import Foundation
import AVFoundation

public struct SpeechUtterance {
    public let text: String
    public let languageTag: String
    public let voice: (any VoiceDescribing)?
    public let onFinish: () -> Void
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
    public static let rate: Float = 0.85
    public static let pitch: Float = 1.1

    private let resolver: VoiceResolver
    private let engine: any SpeechEngine
    /// Bumped on every cancel. Queued work compares against it and bails.
    private var generation = 0

    public init(resolver: VoiceResolver, engine: any SpeechEngine) {
        self.resolver = resolver
        self.engine = engine
    }

    public func cancelAll() {
        generation += 1
        engine.stop()
    }

    public func speak(_ text: String, in language: Language) {
        guard !text.isEmpty else { return }
        cancelAll()
        emit(text, in: language, onFinish: {})
    }

    public func speakSequence(_ items: [SpeechItem], in language: Language) {
        guard !items.isEmpty else { return }
        cancelAll()
        let token = generation
        var index = 0

        func step() {
            guard token == generation, index < items.count else { return }
            let item = items[index]
            index += 1
            item.onSpeak?()
            emit(item.text, in: language) {
                guard token == self.generation else { return }
                step()
            }
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
