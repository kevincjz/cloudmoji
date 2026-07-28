import Foundation

public struct SpeechUtterance {
    public let text: String
    public let languageTag: String
    public let voice: (any VoiceDescribing)?
    public let onFinish: () -> Void

    public init(
        text: String,
        languageTag: String,
        voice: (any VoiceDescribing)?,
        onFinish: @escaping () -> Void
    ) {
        self.text = text
        self.languageTag = languageTag
        self.voice = voice
        self.onFinish = onFinish
    }
}

/// Seam over AVSpeechSynthesizer so queue behaviour is testable without audio.
@MainActor
public protocol SpeechEngine: AnyObject {
    func voices() -> [any VoiceDescribing]
    func speak(_ utterance: SpeechUtterance)
    func stop()
}

public struct SpeechItem {
    public let text: String
    public let onSpeak: (() -> Void)?

    public init(text: String, onSpeak: (() -> Void)? = nil) {
        self.text = text
        self.onSpeak = onSpeak
    }
}

/// Speaks single words and sequences, and can genuinely cancel either.
///
/// Sequences chain on the engine's finish callback rather than on timers. A
/// timer-based queue keeps firing after mute, after a language change and after
/// the round is replaced, because each callback holds stale state and nothing
/// can call it back — which is exactly what happened on the web.
@MainActor
public final class SpeechController {
    /// Speech rate as a **fraction of the engine's normal speed** — 0.85 is 15%
    /// slower than natural, which is what a toddler needs to catch a new word.
    ///
    /// This is the Web Speech API's scale, where 1.0 is normal, and it is the
    /// number `src/hooks/useTTS.ts` uses. It is deliberately NOT an
    /// `AVSpeechUtterance.rate`: that property runs 0...1 with *0.5* as normal,
    /// so assigning 0.85 to it directly gives near-maximum speed. Converting is
    /// the adapter's job — see `SystemSpeechEngine.speak`.
    public static let rate: Float = 0.85

    /// Pitch multiplier. 1.0 is natural on both platforms, so this one does
    /// carry across unchanged.
    public static let pitch: Float = 1.1

    private let resolver: VoiceResolver
    private let engine: any SpeechEngine
    /// Bumped on every cancel. Queued work compares against it and bails.
    private var generation = 0

    /// How long to wait for the engine to report finishing before advancing
    /// anyway. A real synthesiser can drop `didFinish` on a route change or an
    /// interruption, which would otherwise strand the rest of a sequence.
    public var watchdogInterval: Duration = .seconds(6)

    private var watchdog: Task<Void, Never>?

    public init(resolver: VoiceResolver, engine: any SpeechEngine) {
        self.resolver = resolver
        self.engine = engine
    }

    public func cancelAll() {
        generation += 1
        watchdog?.cancel()
        watchdog = nil
        engine.stop()
    }

    /// Speaks one word. `onFinish` runs when the engine reports completion, and
    /// is dropped if the utterance is cancelled first — the mascot uses it to
    /// return from speaking to happy.
    public func speak(
        _ text: String,
        in language: Language,
        onFinish: (() -> Void)? = nil
    ) {
        // An empty request is itself a cancellation: it means "nothing
        // should be speaking now" (a cleared typing row, a category filter
        // with nothing in it). Cancel unconditionally, before the early
        // return, or whatever was already playing keeps going.
        cancelAll()
        guard !text.isEmpty else { return }
        let token = generation
        emit(text, in: language) { [weak self] in
            // A late callback for an utterance that was already stopped must
            // not tell the mascot this word finished — by then something else
            // is speaking, or nothing is.
            guard let self, token == self.generation else { return }
            onFinish?()
        }
    }

    public func speakSequence(_ items: [SpeechItem], in language: Language) {
        // Same reasoning as `speak`: cancel first, then bail on empty input.
        cancelAll()
        guard !items.isEmpty else { return }
        let token = generation
        var index = 0

        // Both call sites already gate entry on token == generation: the
        // direct call below (true by construction) and the recursive call
        // inside emit's onFinish (checked immediately before calling back
        // in). Re-checking it here is deliberate defense-in-depth rather
        // than redundancy this function can shed: step() is the whole
        // cancellation-correctness surface of this controller, so it must
        // not depend on every future call site continuing to gate correctly
        // forever.
        func step() {
            guard token == generation, index < items.count else { return }
            let item = items[index]
            index += 1
            item.onSpeak?()
            // `onSpeak` can reentrantly call back into this controller (e.g.
            // a milestone celebration firing `speak`), which bumps
            // `generation`. Re-check before emitting, or the item that
            // triggered the cancellation still gets forwarded to the engine
            // and speaks after whatever superseded it.
            guard token == generation else { return }

            // Whichever arrives first — the engine's callback or the watchdog —
            // moves the chain on, and the other becomes a no-op. The sole guard
            // against a cancelled chain resuming is the generation check: a late
            // engine callback for an utterance that was already stopped must not
            // step into the next item.
            var advanced = false
            let advance = { [weak self] in
                guard let self, !advanced, token == self.generation else { return }
                advanced = true
                self.watchdog?.cancel()
                self.watchdog = nil
                step()
            }

            // Armed before the hand-off, not after: an engine that reported
            // finishing synchronously would otherwise recurse into the next
            // item, arm its watchdog, and then have this frame cancel it on the
            // way back out.
            watchdog?.cancel()
            watchdog = Task { @MainActor [weak self] in
                guard let self else { return }
                try? await Task.sleep(for: self.watchdogInterval)
                guard !Task.isCancelled else { return }
                advance()
            }

            emit(item.text, in: language, onFinish: advance)
        }
        step()
    }

    private func emit(_ text: String, in language: Language, onFinish: @escaping () -> Void) {
        let tag = resolver.speechTag(for: language)
        let voice = resolver.pick(from: engine.voices(), for: language)
        engine.speak(
            SpeechUtterance(
                text: text,
                // Keep the tag consistent with the chosen voice, or some engines
                // re-resolve and ignore the explicit voice.
                languageTag: voice?.lang ?? tag,
                voice: voice,
                onFinish: onFinish
            )
        )
    }
}
