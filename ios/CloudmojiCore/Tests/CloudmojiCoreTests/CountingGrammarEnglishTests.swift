import Testing
@testable import CloudmojiCore

@Suite("CountingGrammar: English")
struct CountingGrammarEnglishTests {
    let repo = try! EmojiRepository()
    var grammar: CountingGrammar { CountingGrammar(repository: repo) }

    func item(_ en: String) throws -> Countable {
        try #require(repo.countables.first { $0.en == en })
    }

    @Test("singular keeps the bare noun")
    func singular() throws {
        #expect(grammar.phrase(try item("dog"), count: 1, in: .en) == "one dog")
    }

    @Test("regular plurals add s")
    func regular() throws {
        #expect(grammar.phrase(try item("dog"), count: 2, in: .en) == "two dogs")
    }

    @Test("irregular plurals come from the data, not the rule")
    func irregular() throws {
        #expect(grammar.phrase(try item("tooth"), count: 2, in: .en) == "two teeth")
        #expect(grammar.phrase(try item("mouse"), count: 2, in: .en) == "two mice")
    }

    @Test("fish does not gain an s")
    func fish() throws {
        #expect(grammar.phrase(try item("fish"), count: 3, in: .en) == "three fish")
    }

    @Test("sibilant endings take es")
    func sibilants() {
        let bus = Countable(emoji: "🚌", en: "bus", enPlural: nil,
                            zh: "辆巴士", ms: "buah bas", ja: "バス", tl: "bus")
        #expect(grammar.phrase(bus, count: 2, in: .en) == "two buses")
    }

    @Test("consonant-y becomes ies, vowel-y does not")
    func yEndings() {
        let berry = Countable(emoji: "🫐", en: "berry", enPlural: nil,
                              zh: "颗蓝莓", ms: "biji beri", ja: "ベリー", tl: "berry")
        let toy = Countable(emoji: "🧸", en: "toy", enPlural: nil,
                            zh: "个玩具", ms: "buah mainan", ja: "おもちゃ", tl: "laruan")
        #expect(grammar.phrase(berry, count: 2, in: .en) == "two berries")
        #expect(grammar.phrase(toy, count: 2, in: .en) == "two toys")
    }

    @Test("every shipped countable pluralises without a doubled s")
    func noDoubleS() {
        for item in repo.countables {
            let phrase = grammar.phrase(item, count: 2, in: .en)
            #expect(!phrase.hasSuffix("ss"), "\(item.en) -> \(phrase)")
            }
    }

    @Test("the plural of every noun whose plural is not simply +s is pinned literally")
    func literalIrregularPlurals() throws {
        // `noDoubleS` above is a weak invariant: it only catches a doubled
        // trailing "s" ("mangoss"), not a wrong-but-plausible plural
        // ("mangos" instead of "mangoes"). Pin the exact expected form for
        // every shipped noun whose plural isn't the regular "+s" case.
        let expected: [(noun: String, plural: String)] = [
            ("fish", "fish"),
            ("butterfly", "butterflies"),
            ("strawberry", "strawberries"),
            ("peach", "peaches"),
            ("bus", "buses"),
            ("tooth", "teeth"),
            ("dress", "dresses"),
            ("candy", "candies"),
            ("mouse", "mice"),
        ]
        for (noun, plural) in expected {
            #expect(
                grammar.phrase(try item(noun), count: 2, in: .en) == "two \(plural)",
                "\(noun) -> expected \"two \(plural)\""
            )
        }
    }
}
