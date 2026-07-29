import Foundation
import Testing
import CloudmojiCore
@testable import Cloudmoji

/// A seeded generator, so a round can be asked for twice and compared.
///
/// `CountRound.pick` reaches for `randomElement()` directly and is therefore
/// untestable in exactly this respect; `FlashRound` takes an `inout` generator so
/// that it is not.
private nonisolated struct SeededGenerator: RandomNumberGenerator {
    private var state: UInt64

    init(seed: UInt64) { self.state = seed &* 6_364_136_223_846_793_005 &+ 1 }

    mutating func next() -> UInt64 {
        // xorshift64*, which is short, has no dependencies, and is not being
        // asked to be cryptographic.
        state ^= state >> 12
        state ^= state << 25
        state ^= state >> 27
        return state &* 2_685_821_657_736_338_717
    }
}

@Suite("FlashRound")
@MainActor
struct FlashRoundTests {

    private func entry(_ emoji: String, _ word: String, cat: CloudmojiCore.Category = .animals) -> EmojiEntry {
        // Every language gets the same word, so `language:` can be varied in the
        // tests below without the fixtures having to model five vocabularies.
        EmojiEntry(emoji: emoji, cat: cat, en: word, zh: word, ms: word, ja: word, tl: word)
    }

    private var pool: [EmojiEntry] {
        [
            entry("🐶", "dog"), entry("🐱", "cat"), entry("🐮", "cow"),
            entry("🐷", "pig"), entry("🐔", "chicken"), entry("🦁", "lion"),
        ]
    }

    /// Same seed, same round. Without it nothing below can distinguish "the rule
    /// is right" from "this run got lucky".
    ///
    /// Mutation: ignore the `generator` parameter and call `randomElement()`.
    /// The two rounds diverge and this fails.
    @Test("a seeded generator produces the same round twice")
    func roundsAreDeterministic() {
        var a = SeededGenerator(seed: 42)
        var b = SeededGenerator(seed: 42)
        let first = FlashRound(pool: pool, language: .en, using: &a)
        let second = FlashRound(pool: pool, language: .en, using: &b)

        #expect(first != nil)
        #expect(first?.target == second?.target)
        #expect(first?.choices == second?.choices)
    }

    /// The question has an answer. A round whose choices did not include the
    /// target is a screen where every tap is wrong — which is the failure state
    /// `CLAUDE.md` rule 4 forbids, in its purest form.
    ///
    /// Mutation: build `choices` from `distractors` alone. This fails on the
    /// first seed.
    @Test("the target is always among the choices")
    func targetIsAlwaysOffered() {
        for seed in UInt64(1)...40 {
            var generator = SeededGenerator(seed: seed)
            guard let round = FlashRound(pool: pool, language: .en, using: &generator) else {
                Issue.record("seed \(seed) produced no round from a six-entry pool")
                continue
            }
            #expect(round.choices.contains(round.target), "seed \(seed): the answer is not on screen")
            #expect(round.choices.count == 3, "seed \(seed): \(round.choices.count) choices")
            #expect(round.isCorrect(round.target))
        }
    }

    /// **The silent one.** The catalogue has cross-category near-synonyms — the
    /// same word under two different emojis — and a round offering both punishes
    /// a child for being right. It looks perfect on a screenshot.
    ///
    /// Mutation: drop the `seen.insert(...)` filter. The duplicate-word pool
    /// below produces a round with two "cow"s in it and this fails.
    @Test("no two choices share a word in the current language")
    func choicesHaveDistinctWords() {
        let ambiguous = [
            entry("🐮", "cow"), entry("🐄", "cow"), entry("🐶", "dog"),
            entry("🐱", "cat"), entry("🐷", "pig"),
        ]
        for seed in UInt64(1)...40 {
            var generator = SeededGenerator(seed: seed)
            guard let round = FlashRound(pool: ambiguous, language: .en, using: &generator) else {
                Issue.record("seed \(seed) produced no round")
                continue
            }
            let words = round.choices.map { $0.word(.en) }
            #expect(Set(words).count == words.count, "seed \(seed) offered \(words)")
        }
    }

    /// Distinctness is per language, not per glyph: two emojis can be distinct
    /// in English and identical in Chinese.
    ///
    /// Mutation: hardcode `.en` inside the initialiser. The Chinese case offers
    /// two 牛 and this fails.
    @Test("distinctness follows the language the family chose")
    func distinctnessIsPerLanguage() {
        let split = [
            EmojiEntry(emoji: "🐮", cat: .animals, en: "cow", zh: "牛", ms: "lembu", ja: "うし", tl: "baka"),
            EmojiEntry(emoji: "🐂", cat: .animals, en: "ox", zh: "牛", ms: "lembu", ja: "うし", tl: "baka"),
            EmojiEntry(emoji: "🐶", cat: .animals, en: "dog", zh: "狗", ms: "anjing", ja: "いぬ", tl: "aso"),
        ]
        for seed in UInt64(1)...20 {
            var generator = SeededGenerator(seed: seed)
            guard let round = FlashRound(pool: split, language: .zh, using: &generator) else { continue }
            let words = round.choices.map { $0.word(.zh) }
            #expect(Set(words).count == words.count, "seed \(seed) offered \(words) in Chinese")
            // Two distinct words in Chinese, so a Chinese round is a two-choice
            // round rather than a three-choice one with a repeat in it.
            #expect(round.choices.count == 2)
        }
    }

    /// A pool that cannot make a question returns `nil`, and the screen shows
    /// nothing rather than a one-tile round. A question with one answer is not a
    /// question.
    ///
    /// Mutation: change the guard to `distinct.count >= 1`. The single-entry
    /// case builds a round and this fails.
    @Test("a pool with fewer than two distinct words makes no round")
    func tooSmallAPoolMakesNoRound() {
        var generator = SeededGenerator(seed: 7)
        #expect(FlashRound(pool: [], language: .en, using: &generator) == nil)
        #expect(FlashRound(pool: [entry("🐶", "dog")], language: .en, using: &generator) == nil)
        // Three entries, two of which share a word: one distinct word left over
        // and the same answer.
        #expect(FlashRound(
            pool: [entry("🐮", "cow"), entry("🐄", "cow")],
            language: .en, using: &generator
        ) == nil)
    }

    /// Exactly two distinct words is the smallest real question, and it must not
    /// crash, pad itself with a repeat, or be refused.
    ///
    /// Mutation: change `prefix(max(1, count - 1))` to `prefix(count - 1)` and
    /// remove the `max` — a two-entry pool still works, so the assertion that
    /// bites is the count being exactly 2 rather than 3.
    @Test("two distinct words make a valid two-choice round")
    func twoWordsMakeATwoChoiceRound() {
        for seed in UInt64(1)...20 {
            var generator = SeededGenerator(seed: seed)
            guard let round = FlashRound(
                pool: [entry("🐶", "dog"), entry("🐱", "cat")],
                language: .en, using: &generator
            ) else {
                Issue.record("seed \(seed) refused a two-word pool")
                continue
            }
            #expect(round.choices.count == 2)
            #expect(round.choices.contains(round.target))
        }
    }

    /// Not the same target twice running. A child who has just been asked for a
    /// dog and gets asked for a dog again reads it as the app being stuck.
    ///
    /// Mutation: delete the `avoiding:` filter. Over forty seeds the same target
    /// comes back and this fails.
    @Test("the same target does not come back immediately")
    func avoidsRepeatingTheTarget() {
        let previous = entry("🐶", "dog")
        for seed in UInt64(1)...40 {
            var generator = SeededGenerator(seed: seed)
            guard let round = FlashRound(
                pool: pool, language: .en, avoiding: previous, using: &generator
            ) else { continue }
            #expect(round.target.emoji != previous.emoji, "seed \(seed) asked for the dog again")
        }
    }

    /// …unless there is nothing else to ask for, in which case repeating beats
    /// showing nothing.
    @Test("a pool with nothing else in it repeats rather than giving up")
    func repeatsWhenThereIsNoAlternative() {
        let dog = entry("🐶", "dog")
        var generator = SeededGenerator(seed: 3)
        let round = FlashRound(
            pool: [dog, entry("🐱", "cat")], language: .en, avoiding: dog, using: &generator
        )
        #expect(round != nil, "a two-entry pool with one excluded produced no round at all")
    }
}
