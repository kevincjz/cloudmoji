import Testing
@testable import CloudmojiCore

@Suite("EmojiRepository")
struct EmojiRepositoryTests {
    let repo = try! EmojiRepository()

    @Test("loads the full content set")
    func counts() {
        #expect(repo.emojis.count == 200)
        #expect(repo.countables.count == 84)
        #expect(repo.languages.count == 5)
        #expect(repo.categories.count == 9)
    }

    @Test("every emoji has a non-empty word in all five languages")
    func allLanguagesPopulated() {
        for entry in repo.emojis {
            for language in Language.allCases {
                #expect(!entry.word(language).isEmpty, "\(entry.emoji) missing \(language)")
            }
        }
    }

    @Test("tooth keeps its deliberate katakana spelling")
    func toothIsKatakana() throws {
        let tooth = try #require(repo.emojis.first { $0.emoji == "🦷" })
        // Hiragana は is parsed as the topic particle and voiced "wa".
        #expect(tooth.word(.ja) == "ハ")
    }

    @Test("number words run one through ten in every language")
    func numberWords() {
        for language in Language.allCases {
            #expect(repo.numberWord(language, count: 1) != nil)
            #expect(repo.numberWord(language, count: 10) != nil)
            #expect(repo.numberWord(language, count: 11) == nil)
            #expect(repo.numberWord(language, count: 0) == nil)
        }
    }

    @Test("category labels are translated in every language")
    func categoryLabels() {
        for tab in repo.categories {
            for language in Language.allCases {
                #expect(!tab.label(language).isEmpty, "\(tab.id) missing \(language)")
            }
        }
    }

    /// The degraded case the app falls back to when the bundled resource cannot
    /// be loaded. It must be genuinely inert: every accessor still answers, so
    /// the child sees an empty grid instead of a crash.
    @Test("the empty repository answers rather than traps")
    func emptyRepositoryIsInert() {
        let empty = EmojiRepository.empty
        #expect(empty.emojis.isEmpty)
        #expect(empty.countables.isEmpty)
        #expect(empty.languages.isEmpty)
        #expect(empty.categories.isEmpty)
        for language in Language.allCases {
            #expect(empty.numberWord(language, count: 3) == nil)
            #expect(empty.meta(for: language) == nil)
        }
    }
}
