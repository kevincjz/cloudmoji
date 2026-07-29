import Foundation

public struct EmojiData: Codable, Sendable {
    public let version: Int
    public let languages: [LanguageMeta]
    public let categories: [CategoryTab]
    public let emojis: [EmojiEntry]
    public let countables: [Countable]
    /// Keyed by Language raw value; ten entries each, for counts 1...10.
    public let numberWords: [String: [String]]
    /// Glyph → what that animal says, per language raw value. Generated from
    /// `src/data/animalSounds.ts`; see ``EmojiRepository/animalSound(for:in:)``.
    ///
    /// Optional in the decoder rather than required, so a `EmojiData.json`
    /// generated before this key existed still loads. The failure it prevents is
    /// a whole app that will not start because one mini-app gained a field.
    public let animalSounds: [String: [String: String]]?
}
