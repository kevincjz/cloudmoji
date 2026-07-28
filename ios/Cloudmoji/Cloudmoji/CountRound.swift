import Foundation
import CloudmojiCore

/// One round of Count mode: a countable, how many of it are on screen, and which
/// of them the child has counted so far.
///
/// A value type on purpose. Count mode's state machine is the part of this app
/// that fails silently — a round that lets a tile be counted twice still looks
/// and sounds perfect — and a struct with pure transitions is the only shape in
/// which it can be tested at all. `CountView` owns one of these and does the
/// timing; everything about *what counting means* lives here.
struct CountRound: Equatable {
    /// What is being counted. The same glyph is repeated `target` times.
    let item: Countable
    /// How many tiles are on screen, and the number the round finishes on.
    let target: Int

    /// Tile indices in the order they were counted. Order matters: the badge on a
    /// tile is its position in *this* array, not its position in the grid, which
    /// is the whole point of counting things that are not in a line.
    private(set) var counted: [Int] = []

    init(item: Countable, target: Int) {
        self.item = item
        self.target = target
    }

    var progress: Int { counted.count }

    var isComplete: Bool { counted.count == target }

    /// The number to draw on a tile, or `nil` if it has not been counted yet.
    func badge(for index: Int) -> Int? {
        guard let position = counted.firstIndex(of: index) else { return nil }
        return position + 1
    }

    /// Counts one tile. Returns `false` when the tap changed nothing — an
    /// already-counted tile, or an index that is not on screen.
    ///
    /// The caller uses the return value to decide whether to speak. Speaking on a
    /// refused tap would say "three" twice and teach the wrong number, which is
    /// the exact failure this guard exists for. It is *not* a failure state: the
    /// tile still presses and still shows its badge, so the tap is still answered.
    @discardableResult
    mutating func tap(_ index: Int) -> Bool {
        guard (0..<target).contains(index) else { return false }
        guard !counted.contains(index) else { return false }
        counted.append(index)
        return true
    }
}

extension CountRound {
    /// Where a session starts. Three, when the parent's range allows it — two
    /// tiles barely read as a group, and three is what the web has always opened
    /// on — otherwise the nearest end of the range.
    static func firstTarget(in range: ClosedRange<Int>) -> Int {
        min(max(3, range.lowerBound), range.upperBound)
    }

    /// One more than last time, wrapping back to the bottom of the range.
    ///
    /// The web randomises on the wrap; walking is better here. The parent may have
    /// narrowed the range to two or three values, and a random draw inside a
    /// two-value range repeats itself half the time — which reads as Next being
    /// broken. The *item* is randomised on every round, so a round is never the
    /// same twice regardless.
    static func nextTarget(after target: Int, in range: ClosedRange<Int>) -> Int {
        let next = target + 1
        return range.contains(next) ? next : range.lowerBound
    }

    /// Draws the next thing to count, never the thing being replaced.
    ///
    /// `nil` only when there is nothing at all to draw from — a state
    /// `AppModel.countables` guarantees cannot happen, checked here anyway because
    /// the alternative is a crash in front of a child.
    static func pick(from catalogue: [Countable], excluding excluded: Countable?) -> Countable? {
        let candidates = catalogue.filter { $0 != excluded }
        // Everything was excluded, which means the catalogue is the one item we
        // were leaving. Handing it back beats a button that does nothing.
        return candidates.randomElement() ?? catalogue.randomElement()
    }
}
