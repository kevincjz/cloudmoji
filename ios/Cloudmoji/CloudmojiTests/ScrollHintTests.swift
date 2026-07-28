import SwiftUI
import Testing
import UIKit
import CloudmojiCore
@testable import Cloudmoji

/// The scroll hint exists because the web's first attempt at it — a dark
/// gradient over a dark background — was invisible, and the tests that covered it
/// asserted **opacity** rather than pixels, so they passed against a thing nobody
/// could see.
///
/// So nothing here asserts that a view exists or that a constant equals itself.
/// Every visual assertion counts pixels that are *teal and bright* inside a named
/// band of a real render, and compares them against the pixels they sit on. The
/// decision logic — when a hint is allowed to appear at all — is asserted
/// separately and arithmetically, because "the strip fits, so show nothing" is
/// not something a screenshot of an overflowing strip can prove.
///
/// `@MainActor` because the target builds with
/// `SWIFT_DEFAULT_ACTOR_ISOLATION = MainActor`.
@Suite("ScrollHint")
@MainActor
struct ScrollHintTests {

    static let tabs = (try? EmojiRepository())?.categories ?? []

    /// No tab has this id, so no chip is washed teal and the only teal pixels in
    /// a render are the hint's own. Without this every assertion below would be
    /// measuring the active chip.
    static let nothingSelected = "—no-such-category—"

    /// Long enough for the two geometry probes to deliver their first reading and
    /// for SwiftUI to re-render with it. At zero the shutter can beat the state
    /// update and photograph a strip that has not measured itself yet.
    static let settle = Duration.milliseconds(200)

    // MARK: - Reading the pixels

    struct Pixel: Hashable {
        var x: Int, y: Int
    }

    /// Pixels that are unmistakably the hint: strongly green-and-blue over red,
    /// and bright. `Theme.teal` is rgb(78, 205, 196) and the badge's border lands
    /// near rgb(64, 163, 163); the background is rgb(15, 14, 42) and a chip's 4%
    /// white plate is a neutral grey, so neither can be mistaken for either.
    ///
    /// This is the whole point of the suite. A hint that is drawn at opacity 1
    /// but tinted like the background it sits on returns zero pixels here.
    func tealPixels(_ bitmap: Bitmap, x: Range<Int>, y: Range<Int>) -> [Pixel] {
        var found: [Pixel] = []
        for py in y where py >= 0 && py < bitmap.height {
            for px in x where px >= 0 && px < bitmap.width {
                let c = bitmap.rgb(x: px, y: py)
                if c.g > c.r + 40, c.b > c.r + 30, c.sum > 150 {
                    found.append(Pixel(x: px, y: py))
                }
            }
        }
        return found
    }

    /// Mean summed brightness of a set of pixels, and of everything else in the
    /// same region — the two numbers whose gap is what "visible" means.
    func brightness(_ bitmap: Bitmap, of pixels: [Pixel]) -> Double {
        guard !pixels.isEmpty else { return 0 }
        return pixels.reduce(0.0) { $0 + Double(bitmap.rgb(x: $1.x, y: $1.y).sum) }
            / Double(pixels.count)
    }

    func meanBrightness(_ bitmap: Bitmap, x: Range<Int>, y: Range<Int>, excluding lit: Set<Pixel>) -> Double {
        var total = 0.0
        var count = 0
        for py in y where py >= 0 && py < bitmap.height {
            for px in x where px >= 0 && px < bitmap.width {
                guard !lit.contains(Pixel(x: px, y: py)) else { continue }
                total += Double(bitmap.rgb(x: px, y: py).sum)
                count += 1
            }
        }
        return count == 0 ? 0 : total / Double(count)
    }

    // MARK: - Rendering

    /// The real component, over the app's own darkest background rather than the
    /// harness's black — the hint has to be visible against what actually sits
    /// behind it, which is the surface the gradient version failed on.
    func strip(
        width: CGFloat,
        height: CGFloat,
        layout: CategoryLayout = .horizontal
    ) async -> Bitmap {
        await Bitmap.of(
            CategorySource(
                tabs: Self.tabs,
                selected: Self.nothingSelected,
                label: { $0.label(.en) },
                layout: layout,
                onSelect: { _ in }
            )
            .frame(height: height)
            .background(Theme.bgPrimary),
            width: width,
            height: height,
            settling: Self.settle
        )
    }

    // MARK: - When a hint may appear

    /// A strip that fits entirely on screen must show nothing at either end.
    ///
    /// Delete `!atEnd` from ``ScrollEdges/showsEnd`` and this fails — a hint
    /// pointing at content that is not there is worse than no hint, because it
    /// teaches a child to reach for nothing.
    @Test("a scroll view whose content fits shows no hint at either end")
    func contentThatFitsShowsNothing() {
        let edges = ScrollEdges(offset: 0, viewport: 375, content: 300)
        #expect(!edges.overflows)
        #expect(!edges.showsStart)
        #expect(!edges.showsEnd)
    }

    /// Before the first geometry reading arrives every length is zero, and a hint
    /// flashing on at launch and off again a frame later is a worse artefact than
    /// the one being fixed.
    ///
    /// The zero case is the boundary the strict comparison in ``overflows``
    /// exists for: relax it to `content >= viewport` and a scroll view that has
    /// not measured itself yet reports that it overflows.
    @Test("an unmeasured scroll view shows no hint")
    func unmeasuredShowsNothing() {
        let edges = ScrollEdges()
        #expect(!edges.overflows)
        #expect(!edges.showsStart)
        #expect(!edges.showsEnd)
    }

    /// Delete `offset <= Self.slack` from ``ScrollEdges/atStart`` — hard-wire it
    /// to `false` — and the strip points backwards at content the child has not
    /// scrolled past yet, from the moment the app opens.
    @Test("at the start only the forward hint shows")
    func atStartPointsForwardOnly() {
        let edges = ScrollEdges(offset: 0, viewport: 375, content: 800)
        #expect(edges.overflows)
        #expect(!edges.showsStart)
        #expect(edges.showsEnd)
    }

    @Test("mid-scroll both hints show")
    func midScrollPointsBothWays() {
        let edges = ScrollEdges(offset: 200, viewport: 375, content: 800)
        #expect(edges.showsStart)
        #expect(edges.showsEnd)
    }

    /// The half the brief calls out by name: the hint has to *disappear* at the
    /// end of the scroll. Delete `!atEnd` from ``ScrollEdges/showsEnd`` and the
    /// trailing chevron never goes away.
    @Test("at the end only the backward hint shows")
    func atEndPointsBackOnly() {
        let edges = ScrollEdges(offset: 425, viewport: 375, content: 800)
        #expect(edges.showsStart)
        #expect(!edges.showsEnd)
    }

    /// iOS rubber-bands past both ends, so the offset goes negative and past the
    /// content length on every flick. Drop the slack — compare against exactly 0
    /// and exactly `content` — and the hints blink at both extremes of every
    /// bounce.
    @Test("rubber-banding past either end does not resurrect a hint")
    func overscrollDoesNotFlicker() {
        let bouncedBack = ScrollEdges(offset: -30, viewport: 375, content: 800)
        #expect(!bouncedBack.showsStart)

        let bouncedForward = ScrollEdges(offset: 460, viewport: 375, content: 800)
        #expect(!bouncedForward.showsEnd)
    }

    /// Fractional content widths — nine chips sized by nine labels — land the
    /// offset a hair off the true end. Without the slack the hint survives a
    /// scroll that has visibly finished.
    @Test("a fractional offset at the end still counts as the end")
    func fractionalEndCountsAsTheEnd() {
        let edges = ScrollEdges(offset: 424.6, viewport: 375, content: 800)
        #expect(!edges.showsEnd)
    }

    // MARK: - Is it actually visible

    /// The assertion the web version could not make.
    ///
    /// Nine chips do not fit in 375pt, so the trailing chevron must be there — as
    /// pixels, teal and bright, measurably above the surface behind them — and
    /// the leading one must not, because nothing has been scrolled past yet.
    ///
    /// Delete `.overlay(alignment: endAlignment)` from ``HintedScrollView`` and
    /// the count goes to zero. Change `visible: edges.showsStart` to
    /// `visible: true` and the leading half fails.
    @Test("the overflowing strip draws a visible chevron at the trailing edge and none at the leading")
    func stripHintIsVisibleWhereContentIsHidden() async {
        let width = 375
        let height = 120
        let bitmap = await strip(width: CGFloat(width), height: CGFloat(height))
        #expect(bitmap.width == width, "nothing was captured")

        let band = Int(ScrollHintMetrics.band)
        let trailing = tealPixels(bitmap, x: (width - band)..<width, y: 0..<height)
        let leading = tealPixels(bitmap, x: 0..<band, y: 0..<height)

        // 232 as drawn. Tint the badge like the background it sits on — the
        // gradient version's exact failure — and it drops to nothing.
        #expect(trailing.count >= 150,
                "only \(trailing.count) teal pixels in the trailing band — the hint is invisible")
        #expect(leading.count == 0,
                "\(leading.count) teal pixels at the leading edge, which nothing is hidden behind")

        // Visible *against what it sits on*, not merely present. A hint tinted
        // like the surface behind it scores near zero here — which is exactly
        // what the gradient version would have done.
        let hintBrightness = brightness(bitmap, of: trailing)
        let behind = meanBrightness(
            bitmap, x: (width - band)..<width, y: 0..<height, excluding: Set(trailing)
        )
        #expect(hintBrightness - behind > 200,
                "hint reads \(Int(hintBrightness)) against a background of \(Int(behind))")
    }

    /// The other half of the rule, on a real render rather than in arithmetic:
    /// give the strip room for all nine chips and neither edge may be marked.
    ///
    /// Hard-wire either overlay's `visible` to `true` and this fails.
    @Test("a strip wide enough for every chip draws no hint at all")
    func stripThatFitsDrawsNoHint() async {
        let width = 1400
        let height = 120
        let bitmap = await strip(width: CGFloat(width), height: CGFloat(height))
        let band = Int(ScrollHintMetrics.band)

        let leading = tealPixels(bitmap, x: 0..<band, y: 0..<height)
        let trailing = tealPixels(bitmap, x: (width - band)..<width, y: 0..<height)
        #expect(leading.count == 0, "\(leading.count) teal pixels at the leading edge of a strip that fits")
        #expect(trailing.count == 0, "\(trailing.count) teal pixels at the trailing edge of a strip that fits")
    }

    /// Landscape scrolls the other way, and a hint pointing sideways in a
    /// vertical rail says nothing. Nine icons in two columns need 368pt; 200 is
    /// well short of it.
    ///
    /// Change `axis: .vertical` to `.horizontal` in `CategorySource`'s rail case
    /// and the bottom band comes back empty.
    @Test("the overflowing rail draws a visible chevron at its foot and none at its head")
    func railHintIsVisibleWhereContentIsHidden() async {
        let width = Int(CategorySourceMetrics.railWidth)
        let height = 200
        let bitmap = await strip(width: CGFloat(width), height: CGFloat(height), layout: .rail)
        let band = Int(ScrollHintMetrics.band)

        let bottom = tealPixels(bitmap, x: 0..<width, y: (height - band)..<height)
        let top = tealPixels(bitmap, x: 0..<width, y: 0..<band)

        // 259 as drawn. The floor is not zero here: 🌈 is the sixth category and
        // its cyan bands score about 33 on their own, which is the noise this
        // threshold is set well clear of.
        #expect(bottom.count >= 150,
                "only \(bottom.count) teal pixels at the foot of the rail — the hint is invisible")
        #expect(top.count == 0, "\(top.count) teal pixels at the head of a rail scrolled to the top")

        let hintBrightness = brightness(bitmap, of: bottom)
        let behind = meanBrightness(
            bitmap, x: 0..<width, y: (height - band)..<height, excluding: Set(bottom)
        )
        #expect(hintBrightness - behind > 200,
                "hint reads \(Int(hintBrightness)) against a background of \(Int(behind))")
    }

    /// Nine icons in five rows need 368pt of rail. Give them 500 and nothing is
    /// hidden, so nothing may be marked.
    @Test("a rail tall enough for every icon draws no hint at all")
    func railThatFitsDrawsNoHint() async {
        let width = Int(CategorySourceMetrics.railWidth)
        let height = 500
        let bitmap = await strip(width: CGFloat(width), height: CGFloat(height), layout: .rail)
        let band = Int(ScrollHintMetrics.band)

        let top = tealPixels(bitmap, x: 0..<width, y: 0..<band)
        let bottom = tealPixels(bitmap, x: 0..<width, y: (height - band)..<height)
        #expect(top.count == 0, "\(top.count) teal pixels at the head of a rail that fits")
        #expect(bottom.count == 0, "\(bottom.count) teal pixels at the foot of a rail that fits")
    }

    // MARK: - Which way it points

    /// A chevron pointing away from the hidden content is worse than none.
    ///
    /// Measured off the glyph's own shape rather than its name: a chevron's open
    /// end spans the full height of the glyph — two arm tips, top and bottom —
    /// while its point converges to a single stroke. So the open end is where the
    /// per-column vertical spread is large. The badge's circular border is
    /// symmetric and would drown the signal, so only the interior is read.
    ///
    /// Swap `.trailing`'s symbol for `chevron.left` in ``ScrollHintSide`` and the
    /// two numbers trade places.
    @Test("each chevron points at the content it is marking")
    func chevronsPointAtTheHiddenContent() async throws {
        for side in [ScrollHintSide.leading, .trailing, .top, .bottom] {
            let bitmap = await Bitmap.of(
                ScrollHint(side: side, visible: true).background(Theme.bgPrimary),
                width: 60,
                height: 60
            )
            let all = tealPixels(bitmap, x: 0..<60, y: 0..<60)
            #expect(all.count >= 150, "\(side) drew only \(all.count) teal pixels")

            // The badge's centre, from the ring itself.
            let cx = ((all.map(\.x).min() ?? 0) + (all.map(\.x).max() ?? 0)) / 2
            let cy = ((all.map(\.y).min() ?? 0) + (all.map(\.y).max() ?? 0)) / 2

            // A 16×16 box inside the 28pt ring: its corners sit 11.3pt from the
            // centre, comfortably inside the ring's 12.5pt inner edge, so only
            // the chevron is left.
            let inner = tealPixels(bitmap, x: (cx - 8)..<(cx + 8), y: (cy - 8)..<(cy + 8))
            #expect(inner.count >= 40, "\(side)'s chevron is only \(inner.count) pixels")

            let spread = side.isHorizontal
                ? Self.spreadPerColumn(inner)
                : Self.spreadPerRow(inner)
            let openEnd = side == .leading || side == .top ? spread.high : spread.low
            let point = side == .leading || side == .top ? spread.low : spread.high

            #expect(openEnd > point * 2,
                    "\(side): open end spreads \(openEnd), point spreads \(point)")
        }
    }

    /// Vertical spread of the lit pixels at the lowest and highest x present.
    static func spreadPerColumn(_ pixels: [Pixel]) -> (low: Int, high: Int) {
        extent(pixels, key: \.x, other: \.y)
    }

    /// Horizontal spread of the lit pixels at the lowest and highest y present.
    static func spreadPerRow(_ pixels: [Pixel]) -> (low: Int, high: Int) {
        extent(pixels, key: \.y, other: \.x)
    }

    /// Widest spread along `other` found within two lines of each end of `key` —
    /// two rather than one so a single antialiased fringe column cannot decide it.
    private static func extent(
        _ pixels: [Pixel],
        key: KeyPath<Pixel, Int>,
        other: KeyPath<Pixel, Int>
    ) -> (low: Int, high: Int) {
        guard let minKey = pixels.map({ $0[keyPath: key] }).min(),
              let maxKey = pixels.map({ $0[keyPath: key] }).max() else { return (0, 0) }

        func spread(_ range: ClosedRange<Int>) -> Int {
            let slice = pixels.filter { range.contains($0[keyPath: key]) }
            guard let lo = slice.map({ $0[keyPath: other] }).min(),
                  let hi = slice.map({ $0[keyPath: other] }).max() else { return 0 }
            return hi - lo
        }

        return (spread(minKey...(minKey + 1)), spread((maxKey - 1)...maxKey))
    }
}
