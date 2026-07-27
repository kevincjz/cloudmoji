import Foundation
import AVFoundation

/// Structural view of a voice, so selection can be tested without AVFoundation.
public protocol VoiceDescribing {
    var lang: String { get }
    var name: String { get }
}

extension AVSpeechSynthesisVoice: VoiceDescribing {
    public var lang: String { language }
}

/// Picks the best available voice for a language.
///
/// Walks the language's prefix chain in order and takes the first tier with any
/// voice. That is what stops a device with no Filipino voice falling through to
/// the engine's English default, which mispronounces Tagalog badly — it lands on
/// Malay instead, which shares Tagalog's vowels and its "ng".
public struct VoiceResolver: Sendable {
    private let prefixes: [Language: [String]]
    private let speechTags: [Language: String]

    private static let femaleHints = [
        "female", "samantha", "karen", "tessa",
        "tingting", "sinji", "amira", "kyoko", "o-ren", "rosa",
    ]

    public init(languages: [LanguageMeta]) {
        var prefixes: [Language: [String]] = [:]
        var tags: [Language: String] = [:]
        for meta in languages {
            prefixes[meta.id] = meta.voicePrefixes
            tags[meta.id] = meta.speech
        }
        self.prefixes = prefixes
        self.speechTags = tags
    }

    public func speechTag(for language: Language) -> String {
        speechTags[language] ?? language.rawValue
    }

    // Takes an existential array rather than a generic: SpeechEngine hands back
    // [any VoiceDescribing], and Swift will not satisfy `V: VoiceDescribing`
    // with an existential. A concrete [FakeVoice] still upcasts implicitly.
    public func pick(
        from voices: [any VoiceDescribing],
        for language: Language
    ) -> (any VoiceDescribing)? {
        let chain = prefixes[language] ?? [language.rawValue]

        var tier: [any VoiceDescribing] = []
        for prefix in chain {
            tier = voices.filter { $0.lang == prefix || $0.lang.hasPrefix(prefix + "-") }
            if !tier.isEmpty { break }
        }
        guard !tier.isEmpty else { return nil }

        // Prefer an exact tag match, then a female-sounding name, then the first.
        let exact = tier.filter { $0.lang == speechTag(for: language) }
        let pool = exact.isEmpty ? tier : exact
        return pool.first { voice in
            let name = voice.name.lowercased()
            return Self.femaleHints.contains { name.contains($0) }
        } ?? pool.first
    }
}
