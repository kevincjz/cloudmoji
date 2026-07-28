import SwiftUI
import Testing
import UIKit
@testable import Cloudmoji

@Suite("CountTile")
@MainActor
struct CountTileTests {

    // MARK: - The rule

    /// The one that matters. Every tile size, at every round size the parent can
    /// select, in both layouts, must clear the 64pt child-facing floor — a count
    /// tile is squarely something a toddler aims at.
    ///
    /// The whole domain rather than a sample: the sizes are a step function and a
    /// sampled test would walk straight past whichever step was wrong.
    ///
    /// Mutation: change any branch of `side(count:compact:)` to 60. This fails and
    /// names the count that broke.
    @Test("every tile size at every round size clears 64pt")
    func everyTileSizeClearsTheChildMinimum() {
        for count in 2...10 {
            for compact in [false, true] {
                let side = CountTileMetrics.side(count: count, compact: compact)
                #expect(
                    side >= 64,
                    "a round of \(count) draws \(side)pt tiles (compact: \(compact)) — 64 is the floor"
                )
            }
        }
    }

    /// The literal table. Reading the values back off the metrics would agree with
    /// a function that returned 64 everywhere — which passes the test above too.
    ///
    /// Mutation: collapse the ternary chain to a single value. Every row but one
    /// fails.
    @Test("the tile shrinks in the steps the design calls for")
    func tileSizeSteps() {
        #expect(CountTileMetrics.side(count: 2, compact: false) == 96)
        #expect(CountTileMetrics.side(count: 3, compact: false) == 96)
        #expect(CountTileMetrics.side(count: 4, compact: false) == 82)
        #expect(CountTileMetrics.side(count: 6, compact: false) == 82)
        #expect(CountTileMetrics.side(count: 7, compact: false) == 72)
        #expect(CountTileMetrics.side(count: 10, compact: false) == 72)

        #expect(CountTileMetrics.side(count: 2, compact: true) == 72)
        #expect(CountTileMetrics.side(count: 5, compact: true) == 72)
        #expect(CountTileMetrics.side(count: 6, compact: true) == 64)
        #expect(CountTileMetrics.side(count: 10, compact: true) == 64)
    }

    /// A glyph larger than its tile is clipped; a glyph much smaller is a tile that
    /// looks empty. Both are step functions and both are transcribed literals.
    ///
    /// Mutation: swap the 4–6 and 7+ branches. Two rows fail.
    @Test("the glyph shrinks with its tile and always fits inside it")
    func glyphSizeSteps() {
        #expect(CountTileMetrics.glyphSize(count: 3, compact: false) == 64)
        #expect(CountTileMetrics.glyphSize(count: 5, compact: false) == 54)
        #expect(CountTileMetrics.glyphSize(count: 8, compact: false) == 46)
        #expect(CountTileMetrics.glyphSize(count: 4, compact: true) == 44)
        #expect(CountTileMetrics.glyphSize(count: 8, compact: true) == 36)

        for count in 2...10 {
            for compact in [false, true] {
                #expect(
                    CountTileMetrics.glyphSize(count: count, compact: compact)
                        < CountTileMetrics.side(count: count, compact: compact),
                    "the glyph is as large as its tile at \(count) (compact: \(compact))"
                )
            }
        }
    }

    // MARK: - The grid it lays out in

    /// Mutation: drop the `min(count, …)`. A round of two claims three columns and
    /// its tiles sit off-centre.
    @Test("columns follow the round size up to the layout's ceiling")
    func columnCounts() {
        #expect(CountTileMetrics.columns(count: 2, compact: false) == 2)
        #expect(CountTileMetrics.columns(count: 3, compact: false) == 3)
        #expect(CountTileMetrics.columns(count: 9, compact: false) == 3)
        #expect(CountTileMetrics.columns(count: 2, compact: true) == 2)
        #expect(CountTileMetrics.columns(count: 5, compact: true) == 5)
        #expect(CountTileMetrics.columns(count: 9, compact: true) == 5)
    }

    /// The sizes and the columns are chosen independently, so nothing stops a
    /// future edit picking a pair that does not fit. This checks the whole domain
    /// against the max width — two independent sets of literals, not a definition
    /// asserted against itself.
    ///
    /// Mutation: raise any tile size by 20pt. The round that no longer fits fails
    /// and says which one.
    @Test("a full row of tiles fits inside the grid's maximum width")
    func aRowFitsItsGrid() {
        for count in 2...10 {
            for compact in [false, true] {
                let columns = CGFloat(CountTileMetrics.columns(count: count, compact: compact))
                let side = CountTileMetrics.side(count: count, compact: compact)
                let spacing = CountTileMetrics.gridSpacing(count: count, compact: compact)
                let used = columns * side + (columns - 1) * spacing
                let available = CountTileMetrics.maxGridWidth(compact: compact)
                #expect(
                    used <= available,
                    "a round of \(count) needs \(used)pt of a \(available)pt grid (compact: \(compact))"
                )
            }
        }
    }

    /// `CLAUDE.md` rule 2, applied to the one grid whose spacing is a variable.
    ///
    /// Mutation: change the 6+ upright spacing to 6. This fails.
    @Test("tiles are never closer together than 8pt")
    func spacingClearsTheFloor() {
        for count in 2...10 {
            for compact in [false, true] {
                #expect(CountTileMetrics.gridSpacing(count: count, compact: compact) >= 8)
            }
        }
    }

    // MARK: - It actually draws

    /// The tile renders at the size it was handed, rather than at whatever its
    /// glyph happens to want. Measured off a real render, because a constant read
    /// back out of itself follows any value.
    ///
    /// Mutation: delete `.frame(width: side, height: side)`. The tile collapses to
    /// the glyph's own box.
    @Test("a tile renders at the size it was given")
    func tileRendersAtItsSize() {
        let bitmap = Bitmap.rendered(
            CountTile(emoji: "🐶", index: 0, badge: nil, isJustCounted: false,
                      side: 96, glyphSize: 64, onTap: {})
        )
        #expect(bitmap.width == 96, "the tile is \(bitmap.width)pt wide")
        #expect(bitmap.height == 96, "the tile is \(bitmap.height)pt tall")
    }

    /// Emoji are the brightest thing on screen by a wide margin — the background
    /// gradient's brightest stop sums to about 114 — so 400 counts glyph and badge
    /// pixels and nothing else. Same threshold `WordsViewTests` uses.
    static let brightThreshold = 400

    /// The badge is how the child sees *which number this one was*, and it is the
    /// single most droppable thing on the tile: without it the round still counts,
    /// still speaks and still completes, and the tiles are indistinguishable.
    ///
    /// The count is taken **only in the strip above the tile**, which the badge
    /// overhangs by 10pt and nothing else can reach.
    ///
    /// Counting the whole padded image instead — the obvious version — is badly
    /// confounded, and it was measured rather than guessed. A counted tile also
    /// draws at full opacity and full scale, so with the badge deleted its *glyph
    /// alone* still lights 752 pixels against the uncounted tile's 573: a 179
    /// margin, where the whole-image test needs 200. It survives its own mutation
    /// by 21 pixels out of 750. Restricting the count to the overhang strip drops
    /// the mutant to a flat zero.
    ///
    /// Mutation: delete the `if let badge` block. `withBadge` drops to zero.
    @Test("a counted tile draws a badge in the space it overhangs and an uncounted one draws nothing there")
    func badgeAppearsOnlyWhenCounted() {
        let inset = CountTileMetrics.badgeOverhang + 4

        /// Lit pixels in the band above the tile — image rows 4 up to but not
        /// including `inset`, which is padding on an uncounted tile and badge on a
        /// counted one.
        func litAboveTile(badge: Int?) -> Int {
            let bitmap = Bitmap.rendered(
                CountTile(emoji: "🐶", index: 0, badge: badge, isJustCounted: false,
                          side: 72, glyphSize: 46, onTap: {})
                    .padding(inset)
            )
            return (4..<Int(inset)).reduce(0) { total, y in
                total + bitmap.runs(y: y, threshold: Self.brightThreshold)
                    .reduce(0) { $0 + $1.width }
            }
        }

        #expect(litAboveTile(badge: nil) == 0, "something is drawing above an uncounted tile")
        #expect(
            litAboveTile(badge: 3) > 100,
            "the badge lit \(litAboveTile(badge: 3)) pixels above the tile — a 34pt disc hung 10pt over the corner covers far more"
        )
    }

    /// A counted tile is washed teal; an uncounted one is the same 4% white plate
    /// as every other surface in the app. That difference is a large part of what
    /// tells the child which of nine identical dogs they have already touched.
    ///
    /// Asserted as *hue*, not brightness. The brightness version — "a counted tile
    /// lights more pixels than an uncounted one" — was run against the mutation
    /// below and **passed**: the ~900 pixels of badge and the full-opacity glyph
    /// carry the difference on their own, so the plate's colour can be deleted
    /// outright without the assertion noticing. Teal is the one thing only the
    /// counted branch can produce: its green beats its red by a mile, where a
    /// white plate has them equal.
    ///
    /// Mutation: delete the `isCounted` branch from the background. The counted
    /// plate goes grey and the green/red margin collapses to zero.
    @Test("a counted tile's plate is washed teal and an uncounted one's is not")
    func countedTileIsWashedTeal() {
        /// A pixel of bare plate. Halfway down the left edge: past the 2.5pt
        /// border, clear of the 46pt glyph's ink, and diagonally opposite the
        /// badge. Not a corner — the 22pt radius curves away there.
        func plate(badge: Int?) -> Bitmap.RGB {
            Bitmap.rendered(
                CountTile(emoji: "🐶", index: 0, badge: badge, isJustCounted: false,
                          side: 72, glyphSize: 46, onTap: {})
            ).rgb(x: 5, y: 36)
        }

        let counted = plate(badge: 1)
        let uncounted = plate(badge: nil)
        #expect(
            counted.g - counted.r >= 10,
            "a counted tile's plate is \(counted) — teal should put green well above red"
        )
        #expect(
            uncounted.g - uncounted.r <= 2,
            "an uncounted tile's plate is \(uncounted) — it should be neutral white"
        )
    }
}
