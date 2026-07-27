import Testing
import UIKit
@testable import Cloudmoji

@Suite("BundledFonts")
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

    /// Catches the font file being dropped from the target's resources — the
    /// synchronized file group adds it automatically today, but a future
    /// project edit could quietly exclude it.
    @Test("the font file ships in the bundle")
    func fontFileIsBundled() {
        #expect(Bundle.main.url(forResource: "LilitaOne-Regular", withExtension: "ttf") != nil)
    }

    /// The PostScript name differs from both the file name and the family name
    /// ("Lilita One"). Using either of those with UIFont(name:) returns nil.
    @Test("the resolved face is Lilita One, not a fallback")
    func resolvedFaceIsCorrect() {
        BundledFonts.register()
        let font = UIFont(name: BundledFonts.logoPostScriptName, size: 24)
        #expect(font?.familyName == "Lilita One")
    }
}
