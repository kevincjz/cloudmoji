import SwiftUI
import Testing
import UIKit
import CloudmojiCore
@testable import Cloudmoji

/// The category chips are the second thing a toddler touches, and the 64pt rule
/// applies to them exactly as it does to the grid. As with `EmojiGridTests`,
/// none of that can be established by reading a constant back out of
/// ``CategorySourceMetrics`` — that passes against any value, including a chip
/// the view never draws.
///
/// So every measurement here comes off a real render in a real window. The
/// technique is a **difference box**: the same layout is photographed twice,
/// once with a chip selected and once with nothing selected, and the rectangle
/// of pixels that changed is that chip's frame. It works for both layouts,
/// including the rail — where an *unselected* chip has no plate behind it and is
/// therefore invisible to a brightness scan, which is precisely the case where a
/// 20pt tap target would sail through a naive pixel test.
///
/// `@MainActor` because the target builds with
/// `SWIFT_DEFAULT_ACTOR_ISOLATION = MainActor`.
@Suite("CategorySource")
@MainActor
struct CategorySourceTests {

    static let tabs = (try? EmojiRepository())?.categories ?? []

    /// No tab has this id, so nothing is selected and every chip is at rest.
    static let nothingSelected = "—no-such-category—"

    /// Wide enough that all nine labelled chips fit without scrolling, so every
    /// one of them can be measured.
    static let wide: CGFloat = 1400

    // MARK: Measuring

    /// A rectangle in the rendered image. `maxX`/`maxY` are exclusive.
    struct Box: Equatable {
        var minX = Int.max, minY = Int.max, maxX = Int.min, maxY = Int.min

        var width: Int { maxX > minX ? maxX - minX : 0 }
        var height: Int { maxY > minY ? maxY - minY : 0 }
        var isEmpty: Bool { width == 0 || height == 0 }

        mutating func add(x: Int, y: Int) {
            minX = min(minX, x); maxX = max(maxX, x + 1)
            minY = min(minY, y); maxY = max(maxY, y + 1)
        }
    }

    /// The 1.5pt border is centred on the chip's edge and antialiases outward,
    /// so a difference box comes back exactly 2px larger than the chip in each
    /// axis — measured, not assumed: a 64pt rail chip photographs as 66×66.
    /// Charged against the measurement, never against the rule; a chip only
    /// passes if it is 64pt after the fringe is taken off.
    static let antialiasFringe = 2

    /// Chip widths in the strip follow their labels and land on fractional
    /// points, so two adjacent difference boxes can each round outward and
    /// swallow a pixel of the gap between them — the nine English chips measure
    /// 8, 8 and 7 for the same declared 8. The rail's geometry is whole numbers
    /// throughout and is asserted with no tolerance at all.
    static let stripGapTolerance = 1

    func snapshot(
        selected: String,
        layout: CategoryLayout,
        label: @escaping (CategoryTab) -> String = { $0.label(.en) },
        width: CGFloat,
        height: CGFloat
    ) async -> Bitmap {
        await Bitmap.of(
            CategorySource(
                tabs: Self.tabs,
                selected: selected,
                label: label,
                layout: layout,
                onSelect: { _ in }
            ),
            width: width,
            height: height
        )
    }

    /// The drawn frame of every chip, in tab order. An off-screen chip comes
    /// back empty rather than clipped-and-plausible.
    func chipBoxes(
        layout: CategoryLayout,
        label: @escaping (CategoryTab) -> String = { $0.label(.en) },
        width: CGFloat,
        height: CGFloat
    ) async -> [Box] {
        let resting = await snapshot(
            selected: Self.nothingSelected, layout: layout, label: label,
            width: width, height: height
        )
        var boxes: [Box] = []
        for tab in Self.tabs {
            let active = await snapshot(
                selected: tab.id, layout: layout, label: label,
                width: width, height: height
            )
            boxes.append(difference(resting, active))
        }
        return boxes
    }

    /// The bounding box of the pixels that differ between two renders.
    ///
    /// `tolerance` is summed across the three channels; the teal wash lands at
    /// 96 over black, so anything under about 30 is noise.
    func difference(_ a: Bitmap, _ b: Bitmap, tolerance: Int = 12) -> Box {
        var box = Box()
        guard a.width == b.width, a.height == b.height, a.width > 0 else { return box }
        for y in 0..<a.height {
            for x in 0..<a.width {
                let left = a.rgb(x: x, y: y), right = b.rgb(x: x, y: y)
                let delta = abs(left.r - right.r) + abs(left.g - right.g) + abs(left.b - right.b)
                if delta > tolerance { box.add(x: x, y: y) }
            }
        }
        return box
    }

    // MARK: The strip

    /// The cheapest guard against a component that builds and draws nothing —
    /// which is what every discarded version of this suite was silently
    /// measuring.
    ///
    /// Delete `ForEach(tabs)` from the horizontal case and every box comes back
    /// empty.
    @Test("the strip draws a chip for each category")
    func stripDrawsEveryChip() async {
        #expect(Self.tabs.count >= 9)
        let boxes = await chipBoxes(layout: .horizontal, width: Self.wide, height: 120)
        #expect(boxes.count == Self.tabs.count)
        for (tab, box) in zip(Self.tabs, boxes) {
            #expect(!box.isEmpty, "\(tab.id) drew nothing")
        }
    }

    /// The non-negotiable rule from `CLAUDE.md`, measured on the narrowest phone
    /// we support and on chips that have been through the real layout rather
    /// than one chip rendered alone.
    ///
    /// Drop `minHeight: CategorySourceMetrics.side` from `chip` and the chips
    /// collapse to about 42pt — which is exactly what the web's tab bar shipped
    /// at, and it looked completely fine.
    @Test("every chip drawn in a 375pt strip is at least 64pt tall and 8pt apart")
    func stripChipsObeyTheTouchTargetRule() async {
        let width = 375
        let all = await chipBoxes(layout: .horizontal, width: CGFloat(width), height: 120)
        // A chip half off the right edge is clipped, not undersized; only chips
        // fully inside the viewport can be measured.
        let visible = all.filter { !$0.isEmpty && $0.minX >= 0 && $0.maxX <= width - 1 }
        #expect(visible.count >= 3, "only \(visible.count) chips fully visible at 375pt")

        for box in visible {
            let drawnHeight = box.height - Self.antialiasFringe
            let drawnWidth = box.width - Self.antialiasFringe
            #expect(drawnHeight >= 64, "a chip is only \(drawnHeight)pt tall")
            // Not the 64pt rule — a labelled chip is as wide as its label — but
            // a chip narrower than it is tall means the padding has gone.
            #expect(drawnWidth >= 48, "a chip is only \(drawnWidth)pt wide")
        }

        for (left, right) in zip(visible, visible.dropFirst()) {
            let gap = right.minX - left.maxX + Self.antialiasFringe
            #expect(CGFloat(gap) >= CategorySourceMetrics.spacing - CGFloat(Self.stripGapTolerance),
                    "only \(gap)pt between two chips")
        }
    }

    /// The strip scrolls, which is the only reason nine chips can keep their
    /// padding on a 375pt phone. Swap the `ScrollView` for a plain `HStack` and
    /// SwiftUI squeezes the chips to fit — every one of them still 64pt tall,
    /// and the labels truncated to nothing.
    @Test("the strip keeps its chip width on a narrow screen instead of squeezing")
    func stripScrollsRatherThanShrinking() async throws {
        let narrow = await chipBoxes(layout: .horizontal, width: 375, height: 120)
        let wide = await chipBoxes(layout: .horizontal, width: Self.wide, height: 120)
        let first = try #require(narrow.first)
        let firstWide = try #require(wide.first)
        // Within the same 1px rounding as ``stripGapTolerance``. A squeezed chip
        // is not 1pt narrower — nine chips wanting ~800pt inside 375 would each
        // lose more than half their width.
        #expect(abs(first.width - firstWide.width) <= 1,
                "the first chip is \(first.width)pt at 375 and \(firstWide.width)pt at 1400")
        // And the strip really is wider than the phone, or the above proves
        // nothing.
        let last = try #require(wide.last)
        #expect(last.maxX > 375, "all nine chips fit in 375pt — nothing is being scrolled")
    }

    /// The chip renders `label(tab)`, not the tab's id or its English label.
    /// This is what makes the strip change when a parent switches to 日本語 —
    /// and a chip that ignored the closure would pass every size assertion
    /// above.
    @Test("the strip's chip width follows the label it is handed")
    func stripUsesTheLabelClosure() async throws {
        let short = await chipBoxes(
            layout: .horizontal, label: { _ in "A" }, width: Self.wide, height: 120
        )
        let long = await chipBoxes(
            layout: .horizontal, label: { _ in "AAAAAAAAAAAA" }, width: Self.wide, height: 120
        )
        let shortFirst = try #require(short.first)
        let longFirst = try #require(long.first)
        #expect(longFirst.width > shortFirst.width + 40,
                "\"A\" chip is \(shortFirst.width)pt, twelve-letter chip is \(longFirst.width)pt")
    }

    // MARK: The rail

    /// Landscape was nearly unusable before the rail landed, and the thing that
    /// fixed it was two columns: one left 2.6 of the nine categories reachable
    /// without scrolling. Collapse `railColumns` to a single `GridItem` and the
    /// nine chips fall into nine rows — all still 64pt, all still passing every
    /// other assertion in this file.
    @Test("the rail lays its icons out in two columns")
    func railIsTwoColumns() async {
        let boxes = await chipBoxes(
            layout: .rail, width: CategorySourceMetrics.railWidth, height: 420
        )
        #expect(boxes.count == Self.tabs.count)
        #expect(boxes.allSatisfy { !$0.isEmpty }, "a rail chip drew nothing")

        let columns = Set(boxes.map(\.minX))
        #expect(columns.count == 2, "rail icons sit at \(columns.count) distinct x positions")

        let rows = Set(boxes.map(\.minY))
        #expect(rows.count == 5, "nine chips in two columns should make 5 rows, got \(rows.count)")
    }

    /// Same rule, the other layout. The rail is the one place a chip has no
    /// plate behind it when unselected, so it is also the easiest place to end
    /// up with a target the size of the glyph.
    @Test("every rail chip is at least 64pt square, 8pt apart, and inside the rail")
    func railChipsObeyTheTouchTargetRule() async throws {
        let railWidth = Int(CategorySourceMetrics.railWidth)
        let boxes = await chipBoxes(
            layout: .rail, width: CategorySourceMetrics.railWidth, height: 420
        )
        #expect(boxes.count >= 9)

        for box in boxes {
            let drawnWidth = box.width - Self.antialiasFringe
            let drawnHeight = box.height - Self.antialiasFringe
            #expect(drawnWidth >= 64, "a rail chip is only \(drawnWidth)pt wide")
            #expect(drawnHeight >= 64, "a rail chip is only \(drawnHeight)pt tall")
            #expect(box.minX >= 0)
            #expect(box.maxX <= railWidth,
                    "a rail chip runs \(box.maxX - railWidth)pt past the rail's edge")
        }

        // Horizontal gap, between the two columns of the first row.
        let top = try #require(boxes.map(\.minY).min())
        let firstRow = boxes.filter { $0.minY == top }.sorted { $0.minX < $1.minX }
        #expect(firstRow.count == 2, "\(firstRow.count) chips in the rail's first row")
        if firstRow.count == 2 {
            let gap = firstRow[1].minX - firstRow[0].maxX + Self.antialiasFringe
            #expect(CGFloat(gap) >= CategorySourceMetrics.spacing,
                    "only \(gap)pt between the rail's two columns")
        }

        // Vertical gap, down the left column.
        let leftEdge = try #require(boxes.map(\.minX).min())
        let leftColumn = boxes.filter { $0.minX == leftEdge }.sorted { $0.minY < $1.minY }
        #expect(leftColumn.count >= 4)
        for (upper, lower) in zip(leftColumn, leftColumn.dropFirst()) {
            let gap = lower.minY - upper.maxY + Self.antialiasFringe
            #expect(CGFloat(gap) >= CategorySourceMetrics.spacing,
                    "only \(gap)pt between two rail rows")
        }
    }

    /// The rail is 156pt wide and its two columns are 136 — 20pt of slack that
    /// has to end up split. Nothing in `CategorySource` asks for that; it is
    /// `LazyVGrid`'s own behaviour with fixed columns, which is exactly why it
    /// is pinned here rather than trusted. The mutation this catches is an
    /// asymmetric `.padding` or a leading alignment creeping into the rail:
    /// 20pt off-centre reads as "the rail is broken" and is invisible in a
    /// screenshot until someone looks for it.
    @Test("the rail's columns are centred in the rail")
    func railContentIsCentred() async throws {
        let railWidth = Int(CategorySourceMetrics.railWidth)
        let boxes = await chipBoxes(
            layout: .rail, width: CategorySourceMetrics.railWidth, height: 420
        )
        let leftMargin = try #require(boxes.map(\.minX).min())
        let rightMargin = railWidth - (try #require(boxes.map(\.maxX).max()))
        #expect(abs(leftMargin - rightMargin) <= 2,
                "\(leftMargin)pt of margin on the left, \(rightMargin)pt on the right")
    }

    // MARK: Selection

    /// Selection has to reach the drawn chip, and it has to reach the *right*
    /// one. `isActive` hard-wired to `false` leaves a strip that looks right,
    /// filters correctly, and never shows the child which category they are in;
    /// hard-wired to `true` lights all nine at once. Both are invisible to every
    /// size assertion above.
    @Test("selecting a tab lights that tab's chip and nothing else")
    func selectionLightsOneChip() async throws {
        let resting = await snapshot(
            selected: Self.nothingSelected, layout: .horizontal, width: Self.wide, height: 120
        )
        let all = difference(
            resting,
            await snapshot(selected: "all", layout: .horizontal, width: Self.wide, height: 120)
        )
        let animals = difference(
            resting,
            await snapshot(selected: "animals", layout: .horizontal, width: Self.wide, height: 120)
        )

        #expect(!all.isEmpty, "selecting All changed nothing on screen")
        #expect(!animals.isEmpty, "selecting Animals changed nothing on screen")
        // One chip each, not the whole strip: the widest English label here is
        // "Vehicles", well under 200pt with its padding.
        #expect(all.width < 200, "selecting All repainted \(all.width)pt of strip")
        #expect(animals.width < 200, "selecting Animals repainted \(animals.width)pt of strip")
        #expect(all.height - Self.antialiasFringe >= 64)
        // "All" is the first tab and "Animals" the fourth, so the lit region
        // must move to the right — a chip that lit its neighbour, or always the
        // first one, fails here.
        #expect(all.maxX <= animals.minX,
                "All lights x \(all.minX)–\(all.maxX), Animals x \(animals.minX)–\(animals.maxX)")
    }

    /// The active chip is teal, not merely different. A selection indicator that
    /// changed only the border by 6% white would satisfy the test above and be
    /// invisible across a room, which is where a parent watches this from.
    @Test("the active chip is washed teal")
    func activeChipIsTeal() async throws {
        let boxes = await chipBoxes(layout: .horizontal, width: Self.wide, height: 120)
        let box = try #require(boxes.first)
        #expect(!box.isEmpty)
        let active = await snapshot(
            selected: "all", layout: .horizontal, width: Self.wide, height: 120
        )
        // Just inside the chip's leading edge at mid-height: past the rounded
        // corner, short of the 16pt padding that precedes the icon.
        let sample = active.rgb(x: box.minX + 4, y: box.minY + box.height / 2)
        #expect(sample.g > sample.r, "chip fill is rgb(\(sample.r), \(sample.g), \(sample.b))")
        #expect(sample.b > sample.r, "chip fill is rgb(\(sample.r), \(sample.g), \(sample.b))")
    }

    // MARK: One component, two layouts

    /// The two layouts must actually differ — a `switch` whose arms fell
    /// together would leave landscape drawing a horizontal strip inside a 156pt
    /// rail, which is the state the web app was in before `SideRail` landed.
    @Test("the rail draws bare icons where the strip draws labelled chips")
    func layoutsAreDistinct() async throws {
        let strip = await chipBoxes(layout: .horizontal, width: Self.wide, height: 120)
        let rail = await chipBoxes(
            layout: .rail, width: CategorySourceMetrics.railWidth, height: 420
        )
        let stripFirst = try #require(strip.first)
        let railFirst = try #require(rail.first)
        #expect(stripFirst.width > railFirst.width,
                "strip chip \(stripFirst.width)pt, rail chip \(railFirst.width)pt")
        // The strip is one row; the rail is five.
        #expect(Set(strip.map(\.minY)).count == 1)
        #expect(Set(rail.map(\.minY)).count == 5)
    }
}
