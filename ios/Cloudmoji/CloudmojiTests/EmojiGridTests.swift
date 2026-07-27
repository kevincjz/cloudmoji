import SwiftUI
import Testing
import UIKit
import CloudmojiCore
@testable import Cloudmoji

/// The touch-target rule is the one thing in this app that a screenshot cannot
/// confirm and the compiler cannot catch: a grid that has squeezed its tiles to
/// 58pt looks completely fine in a screenshot. So these tests lay the real grid
/// out in a real window, photograph it, and measure the tiles off the pixels.
///
/// Two cheaper approaches were tried and thrown away, both of which passed
/// against a grid that drew nothing at all. `ImageRenderer` never runs a layout
/// pass over a lazy container, so a `LazyVGrid` renders blank. And SwiftUI does
/// not build its accessibility tree until an assistive technology asks for it,
/// so walking `accessibilityElements` from a unit test finds zero elements.
///
/// `@MainActor` because the target builds with
/// `SWIFT_DEFAULT_ACTOR_ISOLATION = MainActor`.
@Suite("EmojiGrid")
@MainActor
struct EmojiGridTests {

    static let repository = (try? EmojiRepository()) ?? .empty

    func entries(_ count: Int) -> [EmojiEntry] {
        Array(Self.repository.emojis.prefix(count))
    }

    // MARK: Photographing the real grid

    /// A run of horizontally adjacent lit pixels — one tile, as drawn.
    struct Run {
        var start: Int
        var end: Int // exclusive
        var width: Int { end - start }
    }

    /// Lays the grid out at `width`, snapshots it on black, and reports the
    /// tiles it finds along a scanline through the middle of the first row.
    ///
    /// The tile fill is white at 4% and its border white at 6%, so on black
    /// every tile pixel is comfortably above zero and every gap is exactly
    /// zero. The scanline is taken at the tiles' vertical mid-point, where
    /// their left and right edges are straight and unantialiased.
    func firstRowTiles(
        _ entries: [EmojiEntry],
        bouncingID: String? = nil,
        width: CGFloat = 375,
        height: CGFloat = 600
    ) -> [Run] {
        let host = UIHostingController(
            rootView: EmojiGrid(entries: entries, bouncingID: bouncingID) { _ in }
                .background(Color.black)
        )
        // The host would otherwise inherit the simulator's safe-area insets and
        // push the first row 60-odd points down the image, under a scanline
        // aimed by arithmetic. The real screen's insets are Task 9's problem;
        // here they only obscure the measurement.
        host.safeAreaRegions = []
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: width, height: height))
        // Without a scene the window never becomes visible, and
        // `drawHierarchy(afterScreenUpdates:)` hands back a black rectangle —
        // which reads here as a grid with no tiles in it.
        if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene {
            window.windowScene = scene
        }
        window.rootViewController = host
        window.makeKeyAndVisible()
        window.layoutIfNeeded()
        host.view.layoutIfNeeded()

        let bounds = CGRect(x: 0, y: 0, width: width, height: height)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        let image = UIGraphicsImageRenderer(bounds: bounds, format: format).image { _ in
            host.view.drawHierarchy(in: bounds, afterScreenUpdates: true)
        }

        guard let cgImage = image.cgImage else { return [] }
        let pixelWidth = cgImage.width
        let pixelHeight = cgImage.height
        var pixels = [UInt8](repeating: 0, count: pixelWidth * pixelHeight * 4)
        pixels.withUnsafeMutableBytes { raw in
            guard let context = CGContext(
                data: raw.baseAddress,
                width: pixelWidth,
                height: pixelHeight,
                bitsPerComponent: 8,
                bytesPerRow: pixelWidth * 4,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return }
            context.draw(cgImage, in: CGRect(x: 0, y: 0, width: pixelWidth, height: pixelHeight))
        }

        let scanline = Int(EmojiGridMetrics.topPadding + EmojiTileMetrics.side / 2)
        guard scanline < pixelHeight else { return [] }

        var runs: [Run] = []
        var current: Run?
        for x in 0..<pixelWidth {
            let offset = (scanline * pixelWidth + x) * 4
            let lit = Int(pixels[offset]) + Int(pixels[offset + 1]) + Int(pixels[offset + 2]) > 8
            switch (lit, current) {
            case (true, nil):
                current = Run(start: x, end: x + 1)
            case (true, .some(var run)):
                run.end = x + 1
                current = run
            case (false, .some(let run)):
                runs.append(run)
                current = nil
            case (false, nil):
                break
            }
        }
        if let run = current { runs.append(run) }
        return runs
    }

    // MARK: Identity

    /// `ForEach` needs unique ids: duplicates make SwiftUI drop rows, so tiles
    /// would silently go missing from the grid. The content is generated from
    /// `src/data/emojis.ts`, so this guards the generator, not the view — and a
    /// missing tile is exactly the kind of thing nobody counts.
    @Test("every grid the app can show has unique row ids")
    func rowIDsAreUnique() {
        var grids: [String: [EmojiEntry]] = ["all": Self.repository.emojis]
        for category in CloudmojiCore.Category.allCases {
            grids[category.rawValue] = Self.repository.emojis.filter { $0.cat == category }
        }
        #expect(grids.count == CloudmojiCore.Category.allCases.count + 1)

        for (name, entries) in grids {
            #expect(!entries.isEmpty, "\(name) grid is empty")
            #expect(Set(entries.map(\.id)).count == entries.count, "\(name) has duplicate ids")
        }
    }

    /// The tile's accessibility identifier is `emoji-<glyph>`, with no category
    /// in it. If one glyph ever appeared in two categories the "all" grid would
    /// carry the same identifier twice and a UI test would match whichever came
    /// first — a flake that only shows up once the content grows.
    @Test("no glyph appears in more than one category")
    func accessibilityIdentifiersAreUnique() {
        let glyphs = Self.repository.emojis.map(\.emoji)
        #expect(Set(glyphs).count == glyphs.count)
    }

    /// The id is `emoji|category`, not the glyph — which is why `bouncingID` is
    /// compared against `id`. Pinned because the two are interchangeable today
    /// (see above) and passing the glyph would work right up until it didn't.
    @Test("a row id is not just the glyph")
    func rowIDCarriesTheCategory() throws {
        let apple = try #require(Self.repository.emojis.first { $0.emoji == "🍎" })
        #expect(apple.id != apple.emoji)
        #expect(apple.id.contains(apple.cat.rawValue))
    }

    // MARK: Layout, measured off the drawn grid

    /// The cheapest guard against a grid that builds and draws nothing — which
    /// is exactly what the first two versions of this suite did while passing.
    @Test("the grid draws a row of tiles, and nothing when it has no entries")
    func gridDrawsItsEntries() {
        #expect(firstRowTiles([]).isEmpty)
        #expect(firstRowTiles(entries(24)).count >= 4)
    }

    /// The tile border sits on a fractional column boundary, so it antialiases
    /// one pixel outside the tile's true edge — a measured run is 2px wider than
    /// the tile, and a measured gap 2px narrower. Charged against the
    /// measurement, never against the rule.
    static let antialiasFringe = 2

    /// The non-negotiable rule from `CLAUDE.md`, measured where it matters: on
    /// tiles that have been through the grid's own column arithmetic on the
    /// narrowest phone we support, not on one tile rendered alone with nothing
    /// competing for the width.
    @Test("every tile drawn in a 375pt grid is at least 64pt wide, 8pt apart, and on screen")
    func tilesObeyTheTouchTargetRuleInSitu() {
        let width = 375
        let tiles = firstRowTiles(entries(24), width: CGFloat(width))
        #expect(tiles.count >= 4, "only \(tiles.count) tiles in the first row")

        for tile in tiles {
            let drawn = tile.width - Self.antialiasFringe
            #expect(drawn >= 64, "a tile is only \(drawn)pt wide")
            #expect(tile.start >= 0)
            #expect(tile.end <= width, "a tile runs \(tile.end - width)pt off the right edge")
        }
        // Exactly the declared spacing, not merely at least it. A tile that has
        // lost its `maxWidth: .infinity` no longer stretches to fill its column,
        // and the leftover width opens up here as an 18pt gap instead of 8 —
        // tiles adrift from the point that was aimed at, and every "at least
        // 8pt" assertion still green.
        for (left, right) in zip(tiles, tiles.dropFirst()) {
            let gap = right.start - left.end + Self.antialiasFringe
            #expect(CGFloat(gap) >= EmojiTileMetrics.spacing, "only \(gap)pt between two tiles")
            #expect(CGFloat(gap) <= EmojiTileMetrics.spacing + 1,
                    "\(gap)pt between two tiles — they are not filling their columns")
        }
    }

    /// The grid's only job beyond layout is routing `bouncingID` to the right
    /// tile. Hard-wiring `isBouncing` to `false`, or comparing the id against
    /// the wrong key, leaves a grid that looks perfect and never acknowledges a
    /// tap. The bounce scales the tile, so the named row draws wider than it
    /// otherwise would and nothing else changes.
    @Test("naming a row in bouncingID grows that row")
    func bouncingIDReachesTheRightTile() throws {
        let rows = entries(24)
        let target = try #require(rows.first)
        let resting = firstRowTiles(rows)
        let bouncing = firstRowTiles(rows, bouncingID: target.id)

        let restingFirst = try #require(resting.first)
        let bouncingFirst = try #require(bouncing.first)
        #expect(bouncingFirst.width > restingFirst.width,
                "bouncing tile is \(bouncingFirst.width)pt, resting is \(restingFirst.width)pt")
        // Roughly the 1.3× peak, allowing for the tile overlapping its neighbour.
        #expect(Double(bouncingFirst.width) >= Double(restingFirst.width) * 1.25)
    }

    /// The same grid has to reflow from an iPhone to an iPad with no breakpoint,
    /// which is the whole reason the columns are adaptive rather than a fixed
    /// count. A hard-coded column count passes every other test in this suite.
    @Test("the grid fits more tiles per row on a wider screen")
    func gridReflowsWithWidth() {
        let phone = firstRowTiles(entries(60), width: 375).count
        let pad = firstRowTiles(entries(60), width: 1024).count
        #expect(phone >= 4, "only \(phone) columns at 375pt")
        #expect(pad > phone, "1024pt fits \(pad) per row, 375pt fits \(phone)")
    }

    /// A model of the `auto-fill` rule the adaptive columns implement, run
    /// against the metrics the grid publishes, across widths there is no
    /// simulator for. It does not prove SwiftUI agrees — the tests above do
    /// that for two widths — but it does catch padding or spacing growing until
    /// the tile a child taps would drop under 64pt.
    @Test("the metrics leave room for full-size tiles at every supported width")
    func metricsSurviveEverySupportedWidth() {
        for screenWidth in [CGFloat(320), 375, 393, 430, 768, 1024] {
            let available = screenWidth - EmojiGridMetrics.horizontalPadding * 2
            let step = EmojiTileMetrics.side + EmojiTileMetrics.spacing
            let columns = max(1, Int((available + EmojiTileMetrics.spacing) / step))
            let tileWidth =
                (available - EmojiTileMetrics.spacing * CGFloat(columns - 1)) / CGFloat(columns)

            #expect(columns >= 3, "only \(columns) columns at \(screenWidth)pt")
            #expect(tileWidth >= 64, "tiles would be \(tileWidth)pt at \(screenWidth)pt")
        }
    }
}
