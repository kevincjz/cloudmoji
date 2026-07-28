import Testing
import CloudmojiCore
@testable import Cloudmoji

/// The counting state machine, which is the part of Count mode that can be wrong
/// in a way no screenshot shows: a round that lets the same tile be counted twice
/// still looks perfect and still speaks, it just teaches the child to count to
/// four by tapping three things.
///
/// `@MainActor` because the target builds with `SWIFT_DEFAULT_ACTOR_ISOLATION = MainActor`.
@Suite("CountRound")
@MainActor
struct CountRoundTests {

    // MARK: Fixtures

    static func countable(_ emoji: String, _ en: String) -> Countable {
        Countable(emoji: emoji, en: en, zh: "只\(en)", ms: "ekor \(en)", ja: en, tl: en)
    }

    static let dog = countable("🐶", "dog")
    static let cat = countable("🐱", "cat")

    // MARK: - Tapping

    /// Four tiles, tapped out of order, so the badge numbers are the *order of
    /// counting* rather than the tile's position. A three-tile fixture cannot
    /// tell those two apart on more than one tile.
    ///
    /// Mutation: delete `counted.append(index)` in `tap(_:)`. Progress stays 0.
    @Test("tapping records the order counted, not the tile's position")
    func tappingRecordsOrder() {
        var round = CountRound(item: Self.dog, target: 4)
        #expect(round.progress == 0)

        // Hoisted into locals because `#expect` decomposes its expression into a
        // closure that captures the receiver immutably, so a `mutating` method
        // cannot be called inside the macro at all.
        let second = round.tap(2)
        let first = round.tap(0)
        let fourth = round.tap(3)
        #expect(second)
        #expect(first)
        #expect(fourth)

        #expect(round.progress == 3)
        #expect(round.badge(for: 2) == 1)
        #expect(round.badge(for: 0) == 2)
        #expect(round.badge(for: 3) == 3)
        #expect(round.badge(for: 1) == nil, "an uncounted tile must carry no badge")
        #expect(round.isComplete == false)
    }

    /// Mutation: delete `guard !counted.contains(index)` in `tap(_:)`. Progress
    /// reaches 2 off one tile, and the child counts to four by tapping twice.
    @Test("a tile already counted is ignored")
    func doubleTapIsIgnored() {
        var round = CountRound(item: Self.dog, target: 4)
        let accepted = round.tap(1)
        let refused = round.tap(1)
        #expect(accepted)
        #expect(refused == false, "the second tap on the same tile must be refused")
        #expect(round.progress == 1)
        #expect(round.badge(for: 1) == 1)
    }

    /// Mutation: delete `guard (0..<target).contains(index)` in `tap(_:)`.
    /// Progress reaches 1 with no tile on screen to account for it.
    @Test("an index outside the round is ignored")
    func outOfRangeTapIsIgnored() {
        var round = CountRound(item: Self.dog, target: 3)
        let past = round.tap(7)
        let negative = round.tap(-1)
        #expect(past == false)
        #expect(negative == false)
        #expect(round.progress == 0)
    }

    /// Completion is what unlocks Next and the celebration, so it is asserted on
    /// its own rather than inferred from `progress`.
    ///
    /// Mutation: change `isComplete` to `counted.count > target`. Never completes.
    @Test("a round completes on its last tile and not before")
    func completionIsExact() {
        var round = CountRound(item: Self.dog, target: 3)
        for index in 0..<2 {
            _ = round.tap(index)
            #expect(round.isComplete == false, "complete after \(index + 1) of 3")
        }
        _ = round.tap(2)
        #expect(round.isComplete)
        #expect(round.progress == 3)
    }

    // MARK: - Targets

    /// The literal walk, spelled out. Reading the range back out of itself would
    /// pass against an implementation that never moved.
    ///
    /// Mutation: delete the wrap (`range.contains(next) ? next : range.lowerBound`)
    /// and the fifth value is 6, outside the parent's range. Delete the `+ 1` and
    /// every value is 2.
    @Test("the next target walks up the range and wraps to its start")
    func nextTargetWalksAndWraps() {
        let range = 2...5
        var walk: [Int] = []
        var target = 2
        for _ in 0..<5 {
            target = CountRound.nextTarget(after: target, in: range)
            walk.append(target)
        }
        #expect(walk == [3, 4, 5, 2, 3])
    }

    /// A range the parent narrowed to a single value has nowhere to walk to.
    @Test("a single-value range stays where it is")
    func nextTargetOnASingleValueRange() {
        #expect(CountRound.nextTarget(after: 4, in: 4...4) == 4)
    }

    /// Three is where the web starts, and it is the right first round when the
    /// range allows it — two tiles barely reads as counting. Literals, because
    /// `range.lowerBound` would agree with an implementation that always started
    /// at the bottom.
    ///
    /// Mutation: replace the body with `range.lowerBound`. The first case fails.
    @Test("the first target is three when the range allows it, and inside it otherwise")
    func firstTarget() {
        #expect(CountRound.firstTarget(in: 2...9) == 3)
        #expect(CountRound.firstTarget(in: 5...9) == 5, "must not start below the range")
        #expect(CountRound.firstTarget(in: 2...2) == 2, "must not start above the range")
        #expect(CountRound.firstTarget(in: 3...3) == 3)
    }

    // MARK: - Shuffling

    /// Shuffle that can hand back the same animal is a button that sometimes does
    /// nothing, and "one tap = one action = one reward" does not survive that.
    ///
    /// A two-item catalogue is the smallest fixture in which the exclusion is
    /// observable, and fifty draws make a dropped exclusion certain rather than
    /// likely — against the real 84 a dropped exclusion would show up about once
    /// in eighty-four draws and this test would pass all afternoon.
    ///
    /// Mutation: delete the `filter { $0 != excluded }`. Roughly half of the fifty
    /// draws come back as the item we were leaving.
    @Test("shuffling always lands on a different item")
    func shuffleExcludesTheCurrentItem() {
        for _ in 0..<50 {
            let picked = CountRound.pick(from: [Self.dog, Self.cat], excluding: Self.dog)
            #expect(picked == Self.cat, "shuffle returned the item it was replacing")
        }
    }

    /// The degraded case: a parent has narrowed the content so far that one
    /// countable is all there is. Refusing to shuffle is a dead button; handing
    /// back the same item is honest.
    ///
    /// Mutation: return `nil` when the filtered list is empty. The round has
    /// nothing to draw and the screen goes blank in front of a child.
    @Test("shuffling a one-item catalogue returns that item")
    func shuffleWithNothingToSwapTo() {
        #expect(CountRound.pick(from: [Self.dog], excluding: Self.dog) == Self.dog)
        #expect(CountRound.pick(from: [], excluding: nil) == nil)
    }

    /// Every draw is a real member of the catalogue it was given. Without this a
    /// shuffle that returned a fixed placeholder would satisfy everything above.
    @Test("every draw comes from the catalogue")
    func shuffleDrawsFromTheCatalogue() {
        let catalogue = [Self.dog, Self.cat, Self.countable("🐰", "rabbit")]
        for _ in 0..<50 {
            let picked = CountRound.pick(from: catalogue, excluding: nil)
            #expect(picked.map(catalogue.contains) == true)
        }
    }
}
