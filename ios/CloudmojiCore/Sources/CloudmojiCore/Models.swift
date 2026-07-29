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

    /// Swift synthesises only an `internal` memberwise initialiser for a `public`
    /// struct, so without this the app target cannot construct an `EmojiEntry` at
    /// all — including in a test fixture. `Countable` carries the same
    /// initialiser for the same reason. Decoding is still how real content
    /// arrives; this exists for fixtures, and for modelling entries the shipped
    /// catalogue does not happen to contain — two emojis sharing one word in one
    /// language, for instance, which `FlashRound` has a rule about and the real
    /// data is too well-behaved to exercise.
    public init(emoji: String, cat: Category, en: String, zh: String, ms: String, ja: String, tl: String) {
        self.emoji = emoji
        self.cat = cat
        self.en = en
        self.zh = zh
        self.ms = ms
        self.ja = ja
        self.tl = tl
    }

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

    /// Swift synthesises only an `internal` memberwise initialiser for a `public`
    /// struct, so without this the app target cannot construct a `Countable` at
    /// all — including in a test fixture. Decoding is still how real content
    /// arrives; this exists for fixtures and for anything that needs to model a
    /// countable the shipped data does not happen to contain.
    public init(
        emoji: String,
        en: String,
        enPlural: String? = nil,
        zh: String,
        ms: String,
        ja: String,
        tl: String
    ) {
        self.emoji = emoji
        self.en = en
        self.enPlural = enPlural
        self.zh = zh
        self.ms = ms
        self.ja = ja
        self.tl = tl
    }

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
