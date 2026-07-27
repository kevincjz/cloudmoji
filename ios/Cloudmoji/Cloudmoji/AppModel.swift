import Foundation
import Observation
import CloudmojiCore

/// `objc/runtime.h` also declares a `Category` (`typedef struct objc_category *`),
/// which Foundation drags in, so the bare name is ambiguous everywhere in the app
/// target. Declaring it once here fixes it for the whole module — a declaration in
/// the current module wins over an imported one — so the views can keep writing
/// `Category` rather than qualifying every mention.
typealias Category = CloudmojiCore.Category

/// Everything the views read. Settings filtering happens here, so a view never
/// branches on a setting — it consumes an already-narrowed list.
///
/// `@MainActor` because SwiftUI reads it and `SpeechController` is main-actor
/// isolated. Stage 3's WatchConnectivity callbacks arrive off-main and will
/// need to hop here; that is the isolation decision Stage 1 left open.
@MainActor
@Observable
final class AppModel {
    let settings: SettingsStore
    let speech: SpeechController
    let grammar: CountingGrammar

    private let repository: EmojiRepository
    private let allEmojis: [EmojiEntry]
    /// Retained so the voice cache can be dropped on foreground. `SpeechController`
    /// deliberately knows the engine only as a protocol, so it cannot forward this.
    private let engine: SystemSpeechEngine

    init(settings: SettingsStore = SettingsStore()) {
        self.settings = settings
        // A missing or malformed bundled resource is a build error, not a
        // runtime path — but the child must never see a crash, so an empty
        // repository is the degraded case rather than a trap.
        let repo = (try? EmojiRepository()) ?? .empty
        self.repository = repo
        self.allEmojis = repo.emojis
        self.grammar = CountingGrammar(repository: repo)
        let engine = SystemSpeechEngine()
        self.engine = engine
        self.speech = SpeechController(
            resolver: VoiceResolver(languages: repo.languages),
            engine: engine
        )
    }

    var availableLanguages: [LanguageMeta] {
        repository.languages.filter { settings.enabledLanguages.contains($0.id) }
    }

    var categories: [CategoryTab] {
        repository.categories.filter { tab in
            guard let category = tab.category else { return true } // "all"
            return settings.enabledCategories.contains(category)
        }
    }

    /// `nil` means the "all" tab.
    ///
    /// The enabled check comes first and applies even when an explicit category
    /// is named. Skipping it in that branch looks like a free shortcut — the
    /// tab is already gone from `categories` — but a view can still be holding
    /// the old selection for a frame, and the result would be a grid full of
    /// exactly the content a parent just turned off.
    func emojis(in category: Category?) -> [EmojiEntry] {
        allEmojis.filter { entry in
            guard settings.enabledCategories.contains(entry.cat) else { return false }
            guard let category else { return true }
            return entry.cat == category
        }
    }

    func word(for entry: EmojiEntry) -> String {
        entry.word(settings.language)
    }

    /// Drops the cached voice list, so the next utterance re-reads what iOS has
    /// installed. Called when the app returns to the foreground.
    func invalidateVoiceCache() {
        engine.invalidateVoiceCache()
    }

    /// The current-language word for a glyph the child tapped earlier.
    ///
    /// The typing row stores glyphs, not entries, and has to be re-read when the
    /// language changes. Glyphs are unique across the catalogue — the generator's
    /// parity check enforces it — so the first match is the only match.
    func word(forEmoji glyph: String) -> String? {
        guard let entry = allEmojis.first(where: { $0.emoji == glyph }) else { return nil }
        return word(for: entry)
    }

    func label(for tab: CategoryTab) -> String {
        tab.label(settings.language)
    }
}
