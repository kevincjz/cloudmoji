import SwiftUI
import Testing
import UIKit
import CloudmojiCore
@testable import Cloudmoji

/// Whether the grid *looks* right is a judgement for the eye and for the
/// simulator. What can be pinned here is the one property no screenshot proves
/// and no compiler catches: that the thing a toddler aims a whole hand at is
/// actually as big as the rule says.
///
/// So these render the real view through `ImageRenderer` and measure the result,
/// rather than reading the constants back out. Deleting the tile's `.frame(...)`
/// leaves a tile that still builds, still draws an apple, and is 47pt wide —
/// a constant-checking test would pass on it.
///
/// `@MainActor` because the target builds with
/// `SWIFT_DEFAULT_ACTOR_ISOLATION = MainActor`.
@Suite("EmojiTile")
@MainActor
struct EmojiTileTests {

    // MARK: Fixtures

    /// `EmojiEntry`'s memberwise initialiser is internal to the package — Swift
    /// does not synthesise a public one — so fixtures come from the real
    /// bundled content rather than being built by hand.
    static let repository = (try? EmojiRepository()) ?? .empty

    func anEntry() throws -> EmojiEntry {
        try #require(Self.repository.emojis.first { $0.emoji == "🍎" })
    }

    // MARK: Measurement

    func renderedSize(_ view: some View) -> CGSize? {
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        return renderer.uiImage?.size
    }

    func renderedPixels(_ view: some View) -> Data? {
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        return renderer.uiImage?.pngData()
    }

    // MARK: Touch target

    /// The non-negotiable rule from `CLAUDE.md`: 64pt is the floor for anything
    /// a child taps. Measured off the rendered view, so it fails if the frame
    /// is dropped, if the glyph is left to size the tile, or if a future layout
    /// squeezes the tile to fit one more column.
    @Test("a tile renders at least the 64pt minimum a child-facing target may be")
    func tileIsBigEnoughForAToddler() throws {
        let size = try #require(renderedSize(EmojiTile(entry: try anEntry()) {}))
        #expect(size.width >= 64, "tile is \(size.width)pt wide")
        #expect(size.height >= 64, "tile is \(size.height)pt tall")
    }

    /// And specifically 72, spelled out rather than read back from
    /// ``EmojiTileMetrics`` — this is the pin to the design system's
    /// touch-target table and to the shipped web build, and a test that quotes
    /// the constant back at itself would follow the constant anywhere.
    @Test("a tile renders at the 72pt the design system specifies")
    func tileIsTheDesignSystemSize() throws {
        let size = try #require(renderedSize(EmojiTile(entry: try anEntry()) {}))
        #expect(size.width == 72)
        #expect(size.height == 72)
    }

    /// A guardrail on the constants themselves rather than on the view: this is
    /// the assertion `tests/review-fixes.spec.ts` makes on the web. It states
    /// the *rule* (≥ 64, ≥ 8), not the values, so it survives a redesign and
    /// still fails a shrink.
    @Test("the metrics obey the toddler touch-target rule")
    func metricsObeyTheRule() {
        #expect(EmojiTileMetrics.side >= 64)
        #expect(EmojiTileMetrics.spacing >= 8)
    }

    // The tile also has to *stretch* to fill a column wider than 72 — the web's
    // `1fr`. That cannot be tested here: wrapping the tile in a `.frame(width:)`
    // forces the size whether or not the tile asked for it, so the test passes
    // against a tile with no `maxWidth` at all. It is checked in
    // `EmojiGridTests.tilesObeyTheTouchTargetRuleInSitu`, by measuring the gaps
    // the grid actually draws.

    // MARK: Glyph

    /// The emoji must clear the box it sits in. A bump to `glyphSize` that
    /// overflows 72pt clips the top and bottom of every emoji in the app, and
    /// nothing else in this suite would notice.
    @Test("the glyph fits inside the tile")
    func glyphFitsInsideTheTile() throws {
        let glyph = try #require(
            renderedSize(Text("🍎").font(.system(size: EmojiTileMetrics.glyphSize)))
        )
        #expect(glyph.width < EmojiTileMetrics.side, "glyph is \(glyph.width)pt wide")
        #expect(glyph.height < EmojiTileMetrics.side, "glyph is \(glyph.height)pt tall")
    }

    // MARK: Feedback

    /// One tap, one reward. `isBouncing` is the app's only signal that a tap
    /// landed on *this* tile, and it is a pure function of the flag, so the two
    /// renders must differ. Dropping the `.scaleEffect` makes them identical.
    @Test("a bouncing tile is drawn differently from a resting one")
    func bounceIsVisible() throws {
        let entry = try anEntry()
        // Padded so the 1.3× peak has somewhere to grow into rather than being
        // clipped away to nothing by the image bounds.
        let resting = renderedPixels(
            EmojiTile(entry: entry, isBouncing: false) {}
                .frame(width: EmojiTileMetrics.side, height: EmojiTileMetrics.side)
                .padding(24)
        )
        let bouncing = renderedPixels(
            EmojiTile(entry: entry, isBouncing: true) {}
                .frame(width: EmojiTileMetrics.side, height: EmojiTileMetrics.side)
                .padding(24)
        )
        #expect(resting != nil)
        #expect(resting != bouncing)
    }

    /// A bounce that shrinks, or a press that grows, would read as the app
    /// glitching rather than answering. The design system fixes both directions:
    /// `:active` is scale(0.85), `bounceEmoji` peaks at 1.3.
    @Test("the press shrinks and the bounce grows")
    func feedbackPullsInOppositeDirections() {
        #expect(EmojiTileMetrics.pressedScale < 1)
        #expect(EmojiTileMetrics.pressedScale > 0.5) // still recognisably the same tile
        #expect(EmojiTileMetrics.bounceScale > 1)
    }
}
