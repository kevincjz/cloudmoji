import Foundation

public enum EmojiRepositoryError: Error, CustomStringConvertible, Sendable {
    case resourceMissing(String)
    /// The resource existed but couldn't be read (e.g. a permissions failure).
    /// Kept distinct from `decodeFailed` so a read failure doesn't misreport
    /// itself as a decode failure.
    case readFailed(String)
    case decodeFailed(String)

    public var description: String {
        switch self {
        case .resourceMissing(let name):
            "EmojiData resource '\(name).json' is missing from the bundle"
        case .readFailed(let message):
            "EmojiData.json could not be read: \(message)"
        case .decodeFailed(let message):
            "EmojiData.json could not be decoded: \(message)"
        }
    }
}

/// Loads the generated content. The only type that knows the file format.
public struct EmojiRepository: Sendable {
    public let data: EmojiData

    public var emojis: [EmojiEntry] { data.emojis }
    public var countables: [Countable] { data.countables }
    public var categories: [CategoryTab] { data.categories }
    public var languages: [LanguageMeta] { data.languages }

    public init(data: EmojiData) {
        self.data = data
    }

    public init(bundle: Bundle? = nil, resource: String = "EmojiData") throws {
        let bundle = bundle ?? .module
        guard let url = bundle.url(forResource: resource, withExtension: "json") else {
            throw EmojiRepositoryError.resourceMissing(resource)
        }
        let raw: Data
        do {
            raw = try Data(contentsOf: url)
        } catch {
            throw EmojiRepositoryError.readFailed(String(describing: error))
        }
        do {
            self.data = try JSONDecoder().decode(EmojiData.self, from: raw)
        } catch {
            throw EmojiRepositoryError.decodeFailed(String(describing: error))
        }
    }

    /// Number word for a count, or nil when the count is out of range.
    /// Japanese has no ～つ form past ten, so callers must handle nil rather
    /// than fabricating a counter.
    public func numberWord(_ language: Language, count: Int) -> String? {
        guard let words = data.numberWords[language.rawValue],
              count >= 1, count <= words.count else { return nil }
        return words[count - 1]
    }

    public func meta(for language: Language) -> LanguageMeta? {
        languages.first { $0.id == language }
    }

    /// Every glyph that has a noise, whatever the language.
    ///
    /// The Animal Sounds grid is built from this, so a tile exists only where
    /// there is something for it to say.
    public var animalSoundGlyphs: Set<String> {
        Set((data.animalSounds ?? [:]).keys)
    }

    /// What this animal says, in this language — "woof woof", 汪汪, ワンワン.
    ///
    /// `nil` when the glyph has no entry, which is how the caller knows there is
    /// no noise rather than being handed the animal's name by mistake. There is
    /// deliberately **no fallback to English**: a Chinese-speaking child hearing
    /// an English voice say "woof" is worse than hearing nothing, and a missing
    /// row is a content bug the tests catch rather than something to paper over
    /// at runtime.
    public func animalSound(for glyph: String, in language: Language) -> String? {
        guard let sound = data.animalSounds?[glyph]?[language.rawValue],
              !sound.isEmpty else { return nil }
        return sound
    }
}

extension EmojiRepository {
    /// A repository with no content. The degraded case when the bundled resource
    /// cannot be loaded — the app shows an empty grid rather than crashing in
    /// front of a child. Reaching this in production means the build is broken.
    ///
    /// Exists as a named value because `EmojiData`'s memberwise initialiser is
    /// internal — Swift does not synthesise a public one — so the app target
    /// cannot build an empty `EmojiData` for itself, and exposing the whole
    /// memberwise surface just for this fallback would be worse.
    public static let empty = EmojiRepository(
        data: EmojiData(
            version: 0, languages: [], categories: [],
            emojis: [], countables: [], numberWords: [:], animalSounds: [:]
        )
    )
}
