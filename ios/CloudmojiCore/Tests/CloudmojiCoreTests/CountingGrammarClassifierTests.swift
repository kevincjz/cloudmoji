import Testing
@testable import CloudmojiCore

@Suite("CountingGrammar: classifier languages")
struct CountingGrammarClassifierTests {
    let repo = try! EmojiRepository()
    var grammar: CountingGrammar { CountingGrammar(repository: repo) }

    func item(_ en: String) throws -> Countable {
        try #require(repo.countables.first { $0.en == en })
    }

    @Test("Chinese joins with no space, classifier already in the noun")
    func chinese() throws {
        #expect(grammar.phrase(try item("dog"), count: 3, in: .zh) == "三只狗")
        #expect(grammar.phrase(try item("apple"), count: 1, in: .zh) == "一个苹果")
    }

    @Test("Chinese uses 两 not 二 for two")
    func chineseTwo() throws {
        let phrase = grammar.phrase(try item("dog"), count: 2, in: .zh)
        #expect(phrase.hasPrefix("两"))
        #expect(!phrase.hasPrefix("二"))
    }

    @Test("Malay joins with a space, penjodoh already in the noun")
    func malay() throws {
        #expect(grammar.phrase(try item("dog"), count: 3, in: .ms) == "tiga ekor anjing")
        #expect(grammar.phrase(try item("apple"), count: 1, in: .ms) == "satu biji epal")
    }

    @Test("no classifier language ever emits a double space")
    func noDoubleSpace() throws {
        for item in repo.countables {
            for count in 1...10 {
                for language in [Language.zh, .ms] {
                    let phrase = grammar.phrase(item, count: count, in: language)
                    #expect(!phrase.contains("  "), "\(language) \(item.en): \(phrase)")
                    #expect(phrase == phrase.trimmingCharacters(in: .whitespaces))
                }
            }
        }
    }
}
