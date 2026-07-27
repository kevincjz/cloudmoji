import Foundation

public struct EmojiData: Codable, Sendable {
    public let version: Int
    public let languages: [LanguageMeta]
    public let categories: [CategoryTab]
    public let emojis: [EmojiEntry]
    public let countables: [Countable]
    /// Keyed by Language raw value; ten entries each, for counts 1...10.
    public let numberWords: [String: [String]]
}
