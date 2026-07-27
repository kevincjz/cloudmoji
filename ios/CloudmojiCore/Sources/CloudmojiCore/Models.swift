import Foundation

public enum Language: String, Codable, CaseIterable, Sendable, Hashable {
    case en, zh, ms, ja, tl
}

public enum Category: String, Codable, CaseIterable, Sendable, Hashable {
    case fruits, food, animals, vehicles, nature, objects, people, faces
}

public struct LanguageMeta: Codable, Sendable, Hashable, Identifiable {
    public let id: Language
    /// The language's own short name, shown on the toggle: EN, 中文, BM, 日本語, TL.
    public let short: String
    /// English name, shown in the picker so a parent can find it.
    public let name: String
    /// BCP-47 code handed to AVSpeechSynthesizer.
    public let speech: String
    /// Ordered voice-language prefixes to try. See VoiceResolver.
    public let voicePrefixes: [String]
}

public struct EmojiEntry: Codable, Sendable, Hashable, Identifiable {
    public let emoji: String
    public let cat: Category
    public let en, zh, ms, ja, tl: String

    /// Stable across categories, since an emoji may appear in more than one.
    public var id: String { "\(emoji)|\(cat.rawValue)" }

    public func word(_ language: Language) -> String {
        switch language {
        case .en: en
        case .zh: zh
        case .ms: ms
        case .ja: ja
        case .tl: tl
        }
    }
}

public struct Countable: Codable, Sendable, Hashable, Identifiable {
    public let emoji: String
    public let en: String
    /// Set only where the regular pluraliser is wrong: teeth, mice.
    public let enPlural: String?
    /// zh and ms bake the classifier into the noun (只狗, ekor anjing).
    /// ja and tl stay bare — their morphology lives on the number.
    public let zh, ms, ja, tl: String

    public var id: String { emoji }

    public func noun(_ language: Language) -> String {
        switch language {
        case .en: en
        case .zh: zh
        case .ms: ms
        case .ja: ja
        case .tl: tl
        }
    }
}

public struct CategoryTab: Codable, Sendable, Hashable, Identifiable {
    /// "all", or a Category raw value.
    public let id: String
    public let icon: String
    /// Keyed by Language raw value. Stored as [String: String] because Swift
    /// encodes dictionaries with non-String keys as arrays, which would not
    /// round-trip against the generated JSON.
    public let labels: [String: String]

    public func label(_ language: Language) -> String {
        labels[language.rawValue] ?? labels["en"] ?? id
    }

    /// nil for the "all" tab.
    public var category: Category? { Category(rawValue: id) }
}
