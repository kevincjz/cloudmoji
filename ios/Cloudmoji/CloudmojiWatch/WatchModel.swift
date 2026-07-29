import Observation
import SwiftUI
import CloudmojiCore

/// The watch's whole state — the wrist counterpart of the phone's `AppModel`,
/// deliberately tiny.
///
/// It holds the emoji catalogue, one speech controller, and the language/mute
/// the phone last told it about. There is no settings screen on the watch: a
/// parent configures Cloudmoji on the phone, and those choices arrive here as a
/// `RadioContext`. The watch persists only the *language* locally, so it opens
/// speaking the right one before the first sync of a session lands.
@MainActor
@Observable
final class WatchModel {
    let speech: SpeechController

    /// Which language the watch speaks. Persisted under `cmw_lang` so a relaunch
    /// starts where the last session left off; overwritten whenever the phone
    /// pushes a context. Its own key, separate from the phone's `cm_lang` — the
    /// phone's privacy copy enumerates the phone's keys, and this is not one of
    /// them.
    private(set) var language: Language

    /// Whether to stay silent. In memory only: it follows the phone's mute and
    /// there is nothing to remember across launches.
    private(set) var muted = false

    /// The full catalogue, in catalogue order — the watch shows every emoji the
    /// phone can, unfiltered. Narrowing to enabled categories is a Phase 2
    /// nicety once the context carries them.
    let entries: [EmojiEntry]

    /// What the wrist just sent or received, so the view can flash it. Token so a
    /// repeat of the same glyph re-triggers the flash.
    private(set) var flash: Flash?
    struct Flash: Equatable, Identifiable {
        let id: Int
        let emoji: String
        let word: String
    }

    let radio = WatchRadio()

    private let defaults: UserDefaults
    private var flashToken = 0
    private var flashTask: Task<Void, Never>?

    private static let languageKey = "cmw_lang"
    private static let flashLifetime = Duration.seconds(2)

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.language = defaults.string(forKey: Self.languageKey)
            .flatMap(Language.init(rawValue:)) ?? .en

        // A broken bundle is a build error, not something a parent should see as
        // a crash — an empty catalogue is the degraded case, the same choice the
        // phone's `AppModel` makes.
        let repo = (try? EmojiRepository()) ?? .empty
        self.entries = repo.emojis
        self.speech = SpeechController(
            resolver: VoiceResolver(languages: repo.languages),
            engine: SystemSpeechEngine()
        )

        radio.onMessage = { [weak self] message in self?.receive(message) }
        radio.onContext = { [weak self] context in self?.apply(context) }
    }

    func word(for entry: EmojiEntry) -> String {
        entry.word(language)
    }

    /// Starts the connection and applies whatever context iOS already had
    /// waiting. Called once, from the app's `init`.
    func activate() {
        radio.activate()
    }

    /// The parent tapped an emoji on the wrist: buzz, speak it, and send it to
    /// the child's phone so Cloud sees it too.
    func tap(_ entry: EmojiEntry) {
        WatchHaptics.tap()
        showFlash(entry.emoji, word: word(for: entry))
        radio.send(RadioMessage(emoji: entry.emoji, direction: .toPhone, language: language).payload)
        speakUnlessMuted(word(for: entry))
    }

    /// A finished voice clip: send it to the phone, then delete it — nothing is
    /// kept on the watch.
    func sendVoice(_ url: URL) {
        WatchHaptics.tap()
        radio.sendVoice(url)
        // Transfer copies the file into the session's outbox, so removing our
        // temp copy now is safe and leaves no recording behind.
        try? FileManager.default.removeItem(at: url)
    }

    /// An emoji arrived from the child.
    private func receive(_ message: RadioMessage) {
        guard message.direction == .toWatch else { return }
        WatchHaptics.received()
        let word = wordForGlyph(message.emoji, in: message.language)
        showFlash(message.emoji, word: word)
        speakUnlessMuted(word)
    }

    private func apply(_ context: RadioContext) {
        language = context.language
        muted = context.muted
        defaults.set(context.language.rawValue, forKey: Self.languageKey)
    }

    private func speakUnlessMuted(_ text: String) {
        guard !muted, !text.isEmpty else { return }
        speech.speak(text, in: language)
    }

    /// The child sends a bare glyph; the watch owns the same catalogue, so it
    /// looks the word up in the language the child was in. Falls back to the
    /// glyph itself if the catalogue somehow lacks it — a flash with no word,
    /// never a crash.
    private func wordForGlyph(_ glyph: String, in language: Language) -> String {
        entries.first { $0.emoji == glyph }?.word(language) ?? ""
    }

    private func showFlash(_ emoji: String, word: String) {
        flashToken += 1
        flash = Flash(id: flashToken, emoji: emoji, word: word)
        flashTask?.cancel()
        let token = flashToken
        flashTask = Task {
            try? await Task.sleep(for: Self.flashLifetime)
            guard !Task.isCancelled, flashToken == token else { return }
            flash = nil
        }
    }
}
