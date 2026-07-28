import CoreText
import SwiftUI

/// Registers the fonts we ship inside the app bundle.
///
/// The app target generates its Info.plist from build settings, and `UIAppFonts`
/// is an array — Xcode's `INFOPLIST_KEY_` passthrough writes strings, so declaring
/// it that way yields a malformed entry and the font silently falls back to the
/// system face. Registering through Core Text sidesteps the plist entirely and,
/// unlike the plist route, can be asserted in a test.
enum BundledFonts {
    /// PostScript name — what `UIFont(name:)` matches on. Not the file name, and
    /// not the family name ("Lilita One", with a space).
    static let logoPostScriptName = "LilitaOne"

    /// The family name Nunito reports. Named here so nobody has to write it as a
    /// literal — and so ``BundledFontsTests`` can build the naive, wrong call
    /// deliberately, as the control it measures against.
    ///
    /// **Never pass this to `Font.custom`.** `Nunito[wght].ttf` is a single
    /// variable file whose `wght` axis runs 200…1000 with an `fvar` *default of
    /// 200* — its full name is literally "Nunito ExtraLight". Ask for the family
    /// and you get whatever the platform picks off that: 200 by the file's own
    /// default, 400 as iOS actually resolves it, and never the 700–900 this app
    /// draws in. Either way it is far too light to sit next to Lilita One, and
    /// either way it resolves happily rather than failing. The named instances
    /// below are the only safe way in.
    static let bodyFamilyName = "Nunito"

    /// The file names, without extension, of everything registered at launch.
    /// `Nunito[wght]` really does have brackets in it — that is Google Fonts'
    /// convention for a single-axis variable file, and the bundle keeps it.
    private static let fontFileNames = ["LilitaOne-Regular", "Nunito[wght]"]

    /// The PostScript name of the Nunito *named instance* that pins `wght` to the
    /// weight asked for.
    ///
    /// The file ships eight named instances and Core Text registers a descriptor
    /// for each, so `UIFont(name: "Nunito-Black", …)` arrives with the axis
    /// already at 900. `UIFont(name: "Nunito", …)` arrives at 200 — non-nil, and
    /// wrong. Any test that only checks for `nil` cannot tell those apart.
    ///
    /// Only 700–900 are mapped, because that is the whole of the design system's
    /// body range (`index.html` loads exactly `wght@700;800;900`). Anything else
    /// lands on Bold rather than on a hairline: a caller asking for `.light` has
    /// made a mistake, and 700 is the mistake that still reads.
    static func bodyPostScriptName(for weight: Font.Weight) -> String {
        switch weight {
        case .black: return "Nunito-Black"          // wght 900
        case .heavy: return "Nunito-ExtraBold"      // wght 800
        case .bold: return "Nunito-Bold"            // wght 700
        default: return "Nunito-Bold"               // wght 700, the design floor
        }
    }

    /// The three weights the app actually asks for. One list, so the availability
    /// check and its test cannot drift apart.
    static let bodyWeights: [Font.Weight] = [.bold, .heavy, .black]

    private static var didRegister = false

    /// Idempotent: Core Text errors on a second registration of the same URL.
    static func register() {
        guard !didRegister else { return }
        didRegister = true

        for name in fontFileNames {
            guard let url = Bundle.main.url(forResource: name, withExtension: "ttf") else {
                assertionFailure("\(name).ttf is missing from the app bundle")
                continue
            }
            var error: Unmanaged<CFError>?
            if !CTFontManagerRegisterFontsForURL(url as CFURL, .process, &error) {
                assertionFailure(
                    "Could not register \(name): \(String(describing: error?.takeUnretainedValue()))"
                )
            }
        }
    }

    /// True once the logo face is resolvable by name.
    static var logoFontIsAvailable: Bool {
        UIFont(name: logoPostScriptName, size: 24) != nil
    }

    /// True once every body weight the design system uses is resolvable by name.
    ///
    /// All three rather than one: they are registered by the same call, but a
    /// later edit to ``bodyPostScriptName(for:)`` can misspell a single instance,
    /// and a misspelling only shows up wherever that one weight is used.
    static var bodyFontIsAvailable: Bool {
        bodyWeights.allSatisfy { UIFont(name: bodyPostScriptName(for: $0), size: 24) != nil }
    }

    /// Every bundled face resolves. This is what launch records.
    static var allFacesAreAvailable: Bool {
        logoFontIsAvailable && bodyFontIsAvailable
    }
}

extension Font {
    /// The chunky display face, used only for the wordmark and the big count
    /// numeral.
    static func cloudmojiLogo(size: CGFloat) -> Font {
        .custom(BundledFonts.logoPostScriptName, size: size)
    }

    /// Everything else: Nunito, the same face the web uses, at the same 700/800/900
    /// weights `index.html` loads. It replaced SF Rounded, which stood in for it
    /// before anyone had seen the two side by side.
    ///
    /// `fixedSize:` and not `size:`, deliberately. `Font.custom(_:size:)` scales
    /// with the body text style, so reaching for it would introduce Dynamic Type
    /// scaling this app has never had — `Font.system(size:weight:design:)`, what
    /// this replaced, is fixed too. Every layout budget here is in fixed points
    /// and several are tight: the header fits a 64pt mascot, a wordmark and three
    /// 44pt controls into 375pt, and the grid's 64pt floor is measured off
    /// rendered pixels. Letting an accessibility text size grow the type would
    /// overflow the strip the child's own controls live in. Scaling is worth doing
    /// on purpose, with those layouts re-measured; it is not worth acquiring as a
    /// side effect of a typeface swap.
    static func cloudmojiRounded(size: CGFloat, weight: Font.Weight = .heavy) -> Font {
        .custom(BundledFonts.bodyPostScriptName(for: weight), fixedSize: size)
    }
}
