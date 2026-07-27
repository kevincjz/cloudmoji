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

    private static var didRegister = false

    /// Idempotent: Core Text errors on a second registration of the same URL.
    static func register() {
        guard !didRegister else { return }
        didRegister = true

        guard let url = Bundle.main.url(forResource: "LilitaOne-Regular", withExtension: "ttf") else {
            assertionFailure("LilitaOne-Regular.ttf is missing from the app bundle")
            return
        }
        var error: Unmanaged<CFError>?
        if !CTFontManagerRegisterFontsForURL(url as CFURL, .process, &error) {
            assertionFailure("Could not register Lilita One: \(String(describing: error?.takeUnretainedValue()))")
        }
    }

    /// True once the logo face is resolvable by name.
    static var logoFontIsAvailable: Bool {
        UIFont(name: logoPostScriptName, size: 24) != nil
    }
}

extension Font {
    /// The chunky display face, used only for the wordmark.
    static func cloudmojiLogo(size: CGFloat) -> Font {
        .custom(BundledFonts.logoPostScriptName, size: size)
    }

    /// Everything else. SF Rounded carries the same friendly, low-contrast feel
    /// as Nunito on the web, and it responds to Dynamic Type for free.
    static func cloudmojiRounded(size: CGFloat, weight: Font.Weight = .heavy) -> Font {
        .system(size: size, weight: weight, design: .rounded)
    }
}
