import Foundation
import CloudmojiCore

/// One flash-card question: a word Cloudmoji says, and the emojis to choose from.
///
/// A value type with pure construction, the same shape and for the same reason as
/// `CountRound`: which choices a round offers is the part of this mini-app that
/// can be silently wrong. A round whose three tiles happen to include two emojis
/// with the *same word* in the current language has no right answer, and it looks
/// perfect on a screenshot.
struct FlashRound: Equatable {
    /// The one the child is being asked for.
    let target: EmojiEntry
    /// Target and distractors, already shuffled. The target is always in here.
    let choices: [EmojiEntry]

    /// Builds a round from a pool.
    ///
    /// Returns `nil` when the pool cannot make a question at all — fewer than two
    /// distinct words in the current language, which means there is nothing to
    /// choose *between*. The caller shows nothing rather than a one-tile round;
    /// a question with one answer is not a question.
    ///
    /// Distractors are filtered on the **word**, not the glyph. The catalogue has
    /// cross-category near-synonyms — the same word can arrive under two emojis —
    /// and a round offering both would punish a child for being right.
    ///
    /// The generator is `inout` so a test can seed it and get the same round
    /// twice. `CountRound.pick` reaches for `randomElement()` directly and is
    /// therefore untestable in exactly this respect.
    init?(
        pool: [EmojiEntry],
        choices count: Int = 3,
        language: Language,
        avoiding previous: EmojiEntry? = nil,
        using generator: inout some RandomNumberGenerator
    ) {
        // One entry per distinct word. Keeping the first is arbitrary and fine —
        // what matters is that no two survivors share a word.
        var seen = Set<String>()
        var distinct: [EmojiEntry] = []
        for entry in pool where seen.insert(entry.word(language)).inserted {
            distinct.append(entry)
        }
        guard distinct.count >= 2 else { return nil }

        // Not the same target twice running, unless there is no other choice.
        let targets = distinct.filter { $0.emoji != previous?.emoji }
        guard let target = (targets.isEmpty ? distinct : targets).randomElement(using: &generator)
        else { return nil }

        let distractors = distinct
            .filter { $0.emoji != target.emoji }
            .shuffled(using: &generator)
            // `count - 1` may exceed what is available; `prefix` takes what there
            // is, which is why a pool of two still makes a two-choice round
            // rather than crashing or padding with a repeat.
            .prefix(max(1, count - 1))

        self.target = target
        self.choices = ([target] + distractors).shuffled(using: &generator)
    }

    /// Whether this glyph is the one that was asked for.
    func isCorrect(_ entry: EmojiEntry) -> Bool { entry.emoji == target.emoji }
}
