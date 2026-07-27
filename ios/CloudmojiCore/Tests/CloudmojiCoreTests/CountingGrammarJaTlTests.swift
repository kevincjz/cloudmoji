import Testing
@testable import CloudmojiCore

@Suite("CountingGrammar: Japanese and Tagalog")
struct CountingGrammarJaTlTests {
    let repo = try! EmojiRepository()
    var grammar: CountingGrammar { CountingGrammar(repository: repo) }

    func item(_ en: String) throws -> Countable {
        try #require(repo.countables.first { $0.en == en })
    }

    // MARK: Japanese

    @Test("Japanese puts the noun first and the counter last")
    func japaneseOrder() throws {
        #expect(grammar.phrase(try item("apple"), count: 3, in: .ja) == "りんご みっつ")
        #expect(grammar.phrase(try item("dog"), count: 1, in: .ja) == "いぬ ひとつ")
    }

    @Test("Japanese inserts no particle between noun and counter")
    func japaneseNoParticle() throws {
        let phrase = grammar.phrase(try item("dog"), count: 2, in: .ja)
        // Exactly two space-separated tokens. Checked as a shape because some
        // nouns legitimately contain の (やしのき = palm tree).
        #expect(phrase.split(separator: " ").count == 2)
        #expect(!phrase.hasPrefix("ふたつ"))
    }

    @Test("Japanese counts one through ten with the ～つ series")
    func japaneseSeries() throws {
        let expected = ["ひとつ", "ふたつ", "みっつ", "よっつ", "いつつ",
                        "むっつ", "ななつ", "やっつ", "ここのつ", "とお"]
        let dog = try item("dog")
        for (index, counter) in expected.enumerated() {
            #expect(grammar.phrase(dog, count: index + 1, in: .ja).hasSuffix(" \(counter)"))
        }
    }

    // MARK: Tagalog

    @Test("vowel-final numerals take the -ng linker")
    func tagalogNg() throws {
        #expect(grammar.phrase(try item("dog"), count: 3, in: .tl) == "tatlong aso")
        #expect(grammar.phrase(try item("dog"), count: 1, in: .tl) == "isang aso")
        #expect(grammar.phrase(try item("dog"), count: 10, in: .tl) == "sampung aso")
    }

    @Test("consonant-final numerals take a separate na")
    func tagalogNa() throws {
        #expect(grammar.phrase(try item("dog"), count: 4, in: .tl) == "apat na aso")
        #expect(grammar.phrase(try item("dog"), count: 6, in: .tl) == "anim na aso")
        #expect(grammar.phrase(try item("dog"), count: 9, in: .tl) == "siyam na aso")
    }

    @Test("n-final numerals take -g")
    func tagalogG() {
        #expect(CountingGrammar.tagalogLinked("roon") == "roong")
    }

    @Test("Tagalog nouns are never pluralised after a numeral")
    func tagalogNoPlural() throws {
        let aso = try item("dog")
        for count in 1...10 {
            #expect(grammar.phrase(aso, count: count, in: .tl).hasSuffix(" aso"))
        }
    }
}
