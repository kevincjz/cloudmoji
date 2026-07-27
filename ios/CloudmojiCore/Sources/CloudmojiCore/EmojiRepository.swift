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
            emojis: [], countables: [], numberWords: [:]
        )
    )
}
