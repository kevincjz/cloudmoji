import Foundation

public enum EmojiRepositoryError: Error, CustomStringConvertible {
    case resourceMissing(String)
    case decodeFailed(any Error)

    public var description: String {
        switch self {
        case .resourceMissing(let name):
            "EmojiData resource '\(name).json' is missing from the bundle"
        case .decodeFailed(let error):
            "EmojiData.json could not be decoded: \(error)"
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
        do {
            let raw = try Data(contentsOf: url)
            self.data = try JSONDecoder().decode(EmojiData.self, from: raw)
        } catch {
            throw EmojiRepositoryError.decodeFailed(error)
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
