import CoreText
import SwiftUI
import Testing
import UIKit
@testable import Cloudmoji

/// Main-actor isolated because font registration is: `CTFontManagerRegisterFontsForURL`
/// with `.process` scope and `UIFont` lookups are UIKit work, and the target compiles
/// with `SWIFT_DEFAULT_ACTOR_ISOLATION = MainActor`.
@Suite("BundledFonts")
@MainActor
struct BundledFontsTests {
    /// The failure this guards against is silent: an unregistered custom font
    /// does not throw, it falls back to the system face, and the logo looks
    /// merely "a bit off" rather than broken.
    @Test("the logo face resolves after registration")
    func logoFontResolves() {
        BundledFonts.register()
        #expect(BundledFonts.logoFontIsAvailable)
    }

    @Test("registering twice is harmless")
    func registrationIsIdempotent() {
        BundledFonts.register()
        BundledFonts.register()
        #expect(BundledFonts.logoFontIsAvailable)
    }

    /// Catches a font file being dropped from the target's resources — the
    /// synchronized file group adds them automatically today, but a future
    /// project edit could quietly exclude one.
    ///
    /// Mutation: rename either resource string. The matching one fails.
    @Test("both font files ship in the bundle")
    func fontFilesAreBundled() {
        #expect(Bundle.main.url(forResource: "LilitaOne-Regular", withExtension: "ttf") != nil)
        // Square brackets and all — Google Fonts' name for a single-axis variable
        // file, kept verbatim so the file is diffable against upstream.
        #expect(Bundle.main.url(forResource: "Nunito[wght]", withExtension: "ttf") != nil)
    }

    /// The PostScript name differs from both the file name and the family name
    /// ("Lilita One"). Using either of those with UIFont(name:) returns nil.
    @Test("the resolved face is Lilita One, not a fallback")
    func resolvedFaceIsCorrect() {
        BundledFonts.register()
        let font = UIFont(name: BundledFonts.logoPostScriptName, size: 24)
        #expect(font?.familyName == "Lilita One")
    }

    /// Every test above calls `register()` first, which is what made them blind
    /// to the actual defect: the app never called it. `Font.custom` falls back to
    /// the system face without complaint, so the wordmark shipped in SF Rounded
    /// while all four tests stayed green.
    ///
    /// Asserted through a flag the app records during `init()`. Checking
    /// `logoFontIsAvailable` here instead would prove nothing — the tests above
    /// may have registered it already, and test order is not guaranteed.
    ///
    /// The flag now covers Nunito too, so this is also what catches the body face
    /// never being registered at launch. Mutation: drop `"Nunito[wght]"` from
    /// `fontFileNames`. Run and confirmed failing.
    @Test("the app registers every face at launch, not just under test")
    func registrationHappensAtLaunch() {
        #expect(CloudmojiApp.didRegisterFontsAtLaunch)
    }

    // MARK: - Nunito, the body face

    /// The trap, stated as an assertion: the *family* resolves, and resolves to
    /// the wrong thing. `UIFont(name: "Nunito", …)` is not nil, so a test written
    /// as `#expect(UIFont(name:) != nil)` passes over a completely broken app.
    ///
    /// The axis floor is 700 rather than a specific number, because the specific
    /// number is not the one the file says it should be. `Nunito[wght].ttf`
    /// declares an `fvar` default of 200 and calls itself "Nunito ExtraLight" —
    /// that is what Core Text reports on macOS — but iOS resolves the bare family
    /// to Regular, 400. Pinning either value would make this test a statement
    /// about an OS version. What matters, and what holds on both, is that the
    /// family lands *below the design system's 700 floor* while every name the app
    /// actually uses lands on or above it.
    ///
    /// Mutation: make `bodyPostScriptName(for:)` return `bodyFamilyName`. The last
    /// expectation here fails, as do the four tests below it.
    @Test("the bare family name resolves below the design system's weight floor")
    func theFamilyNameIsTheTrap() {
        BundledFonts.register()
        let naive = UIFont(name: BundledFonts.bodyFamilyName, size: 44)
        #expect(naive != nil, "the family did not resolve at all — registration is broken")
        let axis = naive.flatMap(Self.weightAxis)
        #expect(
            axis != nil,
            "the family resolved to something with no variation axes — that is a fallback, not Nunito"
        )
        #expect(
            (axis ?? 0) < 700,
            "the family resolved at wght \(String(describing: axis)) — if it now lands in the design range, this whole suite's control is useless"
        )
        // And what the app actually asks for is never that name.
        for weight in BundledFonts.bodyWeights {
            #expect(BundledFonts.bodyPostScriptName(for: weight) != BundledFonts.bodyFamilyName)
        }
    }

    /// Each named instance resolves, is Nunito, and carries the `wght` value the
    /// design system asks for. The axis value is read back out of Core Text's own
    /// resolution of the name — it is not the number that was passed in, because
    /// nothing passes a number in.
    ///
    /// Mutation: swap `.black`'s case to `"Nunito-Bold"`. The 900 expectation
    /// fails. Run and confirmed failing.
    @Test("each body weight resolves to the Nunito instance that pins its axis")
    func bodyWeightsPinTheAxis() {
        BundledFonts.register()
        let expected: [(Font.Weight, Double, String)] = [
            (.bold, 700, "bold"),
            (.heavy, 800, "heavy"),
            (.black, 900, "black"),
        ]
        #expect(expected.count == BundledFonts.bodyWeights.count, "the table has drifted")

        for (weight, axis, name) in expected {
            let font = UIFont(name: BundledFonts.bodyPostScriptName(for: weight), size: 44)
            #expect(font != nil, "\(name) did not resolve")
            #expect(font?.familyName == "Nunito", "\(name) is not Nunito")
            #expect(
                font.flatMap(Self.weightAxis) == axis,
                "\(name) resolved at wght \(String(describing: font.flatMap(Self.weightAxis))), wanted \(axis)"
            )
        }
    }

    /// The one that matters: what SwiftUI actually *draws*.
    ///
    /// `UIFont(name:)` returning a correct font proves nothing about `Theme.body`,
    /// because the fallback this project has already shipped once happens inside
    /// `Font.custom` — which is silent, and which the tests above never touch.
    /// So this renders the same string twice, once through `Theme.body` and once
    /// through a `UIFont` the test above has independently proved is Nunito Black,
    /// and requires the two images to carry identical ink. Two different typefaces
    /// agreeing to the pixel over a 320 × 80 canvas does not happen.
    ///
    /// Mutation: put `.system(size: size, weight: weight, design: .rounded)` back
    /// in `cloudmojiRounded`. Run and confirmed failing.
    @Test("Theme.body draws the same pixels as Nunito Black itself")
    func themeBodyDrawsNunito() async {
        BundledFonts.register()
        let reference = UIFont(name: "Nunito-Black", size: 44)
        #expect(reference != nil, "setup: the reference face did not resolve")
        guard let reference else { return }

        let viaTheme = await ink(Theme.body(44, .black))
        let viaUIKit = await ink(Font(reference))
        #expect(viaTheme > 0, "Theme.body drew nothing at all")
        #expect(
            viaTheme == viaUIKit,
            "Theme.body lit \(viaTheme) pixels, Nunito Black lit \(viaUIKit) — they are not the same face"
        )
    }

    /// The ExtraLight trap, measured rather than reasoned about. A Black instance
    /// has materially more lit pixels than the family default at the same size;
    /// if `Font.custom` were ever handed the bare family name the two would be
    /// equal, and every screen in the app would render in a hairline.
    ///
    /// The 1.8× floor is deliberately far below the ratio Nunito actually gives
    /// (200 → 900 roughly triples the stem width) and far above 1.0, so it fails
    /// hard on the bug and cannot fail on a hinting change.
    ///
    /// Mutation: make `bodyPostScriptName(for:)` return `bodyFamilyName`. The
    /// ratio collapses to 1.0. Run and confirmed failing.
    @Test("body text is much heavier than the family's ExtraLight default")
    func bodyIsHeavierThanTheDefaultInstance() async {
        BundledFonts.register()
        let black = await ink(Theme.body(44, .black))
        let familyDefault = await ink(Font.custom(BundledFonts.bodyFamilyName, fixedSize: 44))
        #expect(familyDefault > 0, "setup: the control drew nothing")
        #expect(
            Double(black) > Double(familyDefault) * 1.8,
            "Nunito Black lit \(black) pixels against the ExtraLight default's \(familyDefault) — the wght axis is not being driven"
        )
    }

    /// The weight argument has to reach the face. Nothing in the app would look
    /// broken if every call collapsed onto one instance — it would just look
    /// uniformly wrong, which is precisely the shipped bug this project keeps
    /// re-finding.
    ///
    /// Strict inequalities with no tolerance, because each 100 units of `wght`
    /// adds ink monotonically and the steps are far larger than any rendering
    /// jitter — the capture is deterministic at scale 1.
    ///
    /// Mutation: drop the `switch` in `bodyPostScriptName(for:)` to a single
    /// return. All three counts become equal. Run and confirmed failing.
    @Test("heavier weights draw more ink than lighter ones")
    func weightsAreDistinguishable() async {
        BundledFonts.register()
        let bold = await ink(Theme.body(44, .bold))
        let heavy = await ink(Theme.body(44, .heavy))
        let black = await ink(Theme.body(44, .black))
        #expect(bold > 0, "the bold weight drew nothing at all")
        #expect(bold < heavy, "bold lit \(bold), heavy lit \(heavy) — 700 and 800 are the same face")
        #expect(heavy < black, "heavy lit \(heavy), black lit \(black) — 800 and 900 are the same face")
    }

    /// The face genuinely changed. SF Rounded is what this replaced and it is the
    /// thing a silent fallback would land back on, so the two must not draw the
    /// same picture.
    ///
    /// Weaker than the equality test above on its own — it would also pass if
    /// `Theme.body` fell back to some *third* face — which is why it sits after
    /// that one rather than instead of it.
    @Test("body text is no longer SF Rounded")
    func bodyIsNotTheSystemFace() async {
        BundledFonts.register()
        let nunito = await ink(Theme.body(44, .black))
        let sfRounded = await ink(.system(size: 44, weight: .black, design: .rounded))
        #expect(nunito != sfRounded, "both faces lit \(nunito) pixels — the swap did not take")
    }

    // MARK: - Helpers

    /// Lit pixels in a fixed 320 × 80 frame, so counts from different fonts are
    /// directly comparable. White on the black `Bitmap.of` draws over, well clear
    /// of the 150 threshold in the middle of a stem and well under it in the
    /// antialiased fringe.
    ///
    /// "Cloudmoji" rather than a lorem string: nine glyphs, four of them with
    /// bowls, which is where a weight axis shows up most.
    private func ink(_ font: Font, text: String = "Cloudmoji") async -> Int {
        let view = Text(text)
            .font(font)
            .foregroundStyle(Color.white)
            .frame(width: 320, height: 80, alignment: .leading)
        let bitmap = await Bitmap.of(view, width: 320, height: 80)
        return bitmap.litPixels(threshold: 150)
    }

    /// The `wght` axis value Core Text resolved this font at, or nil if it has no
    /// variation axes at all — which is what a non-variable fallback looks like.
    ///
    /// `0x77676874` is the four-character code `wght`, which is how Core Text
    /// keys the variation dictionary.
    private static func weightAxis(of font: UIFont) -> Double? {
        guard let variation = CTFontCopyVariation(font as CTFont) as NSDictionary? else { return nil }
        let wght = NSNumber(value: UInt32(0x7767_6874))
        return (variation[wght] as? NSNumber)?.doubleValue
    }
}
