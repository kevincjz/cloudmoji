import SwiftUI
import Testing
import UIKit
@testable import Cloudmoji

/// The design system is written in hex; `Theme` is written in decimals. That
/// conversion is done by hand and is exactly the kind of thing that goes wrong
/// silently — a wrong digit yields a colour that still looks like a colour.
/// These tests round-trip each token back to hex and compare against
/// `docs/design/DESIGN_SYSTEM.md`.
///
/// `@MainActor` because the target builds with
/// `SWIFT_DEFAULT_ACTOR_ISOLATION = MainActor`, so `Theme` is main-actor isolated.
@Suite("Theme")
@MainActor
struct ThemeTests {

    // MARK: Colour

    /// Built inside the test body rather than passed through `@Test(arguments:)`:
    /// `Theme` is main-actor isolated, and the macro evaluates its argument
    /// expression outside that isolation.
    @Test("every token round-trips to the hex in the design system")
    func tokenHex() {
        let palette: [(String, Color, String)] = [
            ("bgPrimary", Theme.bgPrimary, "#0F0E2A"),
            ("bgMid", Theme.bgMid, "#1A1145"),
            ("bgEdge", Theme.bgEdge, "#0D2137"),
            ("coral", Theme.coral, "#FF6B6B"),
            ("teal", Theme.teal, "#4ECDC4"),
            ("gold", Theme.gold, "#FFE66D"),
            ("amber", Theme.amber, "#FFB347"),
            ("textPrimary", Theme.textPrimary, "#FFFFFF"),
            ("cloudWhite", Theme.cloudWhite, "#FFFFFF"),
            ("cloudHighlight", Theme.cloudHighlight, "#F8FCFF"),
            ("cloudShadow", Theme.cloudShadow, "#E8EEF4"),
            ("eyes", Theme.eyes, "#2D3436"),
            ("mouth", Theme.mouth, "#FF6B6B"),
            ("mouthStroke", Theme.mouthStroke, "#E55555"),
            ("blush", Theme.blush, "#FFB5B5"),
            ("blushBeaming", Theme.blushBeaming, "#FF9E9E"),
        ]
        // Guards the table itself: dropping a row would quietly shrink coverage.
        #expect(palette.count == 16)

        for (name, color, expected) in palette {
            #expect(hex(color) == expected, "\(name) is off palette")
        }
    }

    /// Pure black would be the natural guess for an eye and is wrong: it reads
    /// harsh next to a white cloud. Called out separately because the round-trip
    /// test above would still pass if someone changed both the value and the
    /// expectation together.
    @Test("the eyes are a warm near-black, not black")
    func eyesAreNotBlack() {
        let (r, g, b, _) = components(Theme.eyes)
        #expect(r > 0.1 && g > 0.1 && b > 0.1)
        // Cool-warm balance: the design system's #2D3436 is bluer than it is red.
        #expect(b > r)
    }

    /// The two whites differ by about 2% and are meant to. Collapsing
    /// `cloudHighlight` into `cloudWhite` flattens the top of the cloud, and
    /// nothing else in the app would notice.
    @Test("the cloud highlight is distinct from the cloud body")
    func cloudWhitesAreDistinct() {
        #expect(hex(Theme.cloudHighlight) != hex(Theme.cloudWhite))
    }

    @Test("the beaming blush is rosier than the resting blush")
    func beamingBlushIsRosier() {
        let resting = components(Theme.blush)
        let beaming = components(Theme.blushBeaming)
        #expect(beaming.g < resting.g)
        #expect(beaming.b < resting.b)
    }

    /// The outline has to read against the fill or the mouth loses its edge.
    @Test("the mouth stroke is darker than the mouth fill")
    func mouthStrokeIsDarker() {
        #expect(luminance(Theme.mouthStroke) < luminance(Theme.mouth))
    }

    @Test("translucent tokens carry the right alpha")
    func alphaTokens() {
        #expect(abs(components(Theme.textSecondary).a - 0.4) < 0.001)
        #expect(abs(components(Theme.textMuted).a - 0.2) < 0.001)
        #expect(abs(components(Theme.surface).a - 0.04) < 0.001)
        #expect(abs(components(Theme.surfaceBorder).a - 0.06) < 0.001)
        // Muted is quieter than secondary, or the placeholder competes with the
        // labels a parent actually needs to read.
        #expect(components(Theme.textMuted).a < components(Theme.textSecondary).a)
    }

    // MARK: Type

    /// `Font.custom` fails to a system fallback in silence, so a literal
    /// `"LilitaOne-Regular"` here (the *file* name, not the PostScript name)
    /// would look merely a bit off rather than broken. Comparing against the
    /// audited helper is the only cheap way to pin it.
    @Test("display uses the bundled logo face")
    func displayUsesLogoFace() {
        #expect(Theme.display(21) == Font.cloudmojiLogo(size: 21))
        #expect(Theme.display(21) != Font.cloudmojiRounded(size: 21))
    }

    @Test("body uses the rounded UI face and honours the weight")
    func bodyUsesRoundedFace() {
        #expect(Theme.body(18, .black) == Font.cloudmojiRounded(size: 18, weight: .black))
        #expect(Theme.body(18, .black) != Font.cloudmojiRounded(size: 18, weight: .bold))
        #expect(Theme.body(18, .black) != Theme.body(12, .black))
    }

    /// The design system's body weights are 700–900; anything lighter looks
    /// wrong next to Lilita One.
    @Test("body defaults to a heavy weight")
    func bodyDefaultWeight() {
        #expect(Theme.body(14) == Font.cloudmojiRounded(size: 14, weight: .heavy))
    }

    // MARK: Helpers

    private func components(_ color: Color) -> (r: Double, g: Double, b: Double, a: Double) {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        UIColor(color).getRed(&r, green: &g, blue: &b, alpha: &a)
        return (Double(r), Double(g), Double(b), Double(a))
    }

    private func hex(_ color: Color) -> String {
        let c = components(color)
        return String(
            format: "#%02X%02X%02X",
            Int((c.r * 255).rounded()),
            Int((c.g * 255).rounded()),
            Int((c.b * 255).rounded())
        )
    }

    private func luminance(_ color: Color) -> Double {
        let c = components(color)
        return 0.2126 * c.r + 0.7152 * c.g + 0.0722 * c.b
    }
}
