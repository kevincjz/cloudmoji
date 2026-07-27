import SwiftUI

/// One place for colour and type, mirroring `docs/design/DESIGN_SYSTEM.md`.
///
/// Every value here is a direct transcription of a CSS custom property in the
/// design system. Nothing is invented: if a colour is wanted that is not listed
/// there, add it there first. The hex comment on each line is the thing to diff
/// against the stylesheet — the decimals are just 8-bit values over 255.
enum Theme {

    // MARK: - Background

    static let bgPrimary = Color(red: 0.059, green: 0.055, blue: 0.165) // #0F0E2A
    static let bgMid = Color(red: 0.102, green: 0.067, blue: 0.271)     // #1A1145
    static let bgEdge = Color(red: 0.051, green: 0.129, blue: 0.216)    // #0D2137

    /// `linear-gradient(160deg, #0F0E2A 0%, #1A1145 40%, #0D2137 100%)`.
    ///
    /// The mid stop sits at 40%, not the halfway point an evenly spaced
    /// three-colour gradient would give it — the purple band is meant to ride
    /// high so the blue edge has room to deepen behind the emoji grid. The 160°
    /// tilt is dropped: over a nearly black gradient a 20° lean off vertical is
    /// invisible, and top-to-bottom keeps it stable under rotation.
    static let background = LinearGradient(
        stops: [
            .init(color: bgPrimary, location: 0.0),
            .init(color: bgMid, location: 0.4),
            .init(color: bgEdge, location: 1.0),
        ],
        startPoint: .top,
        endPoint: .bottom
    )

    // MARK: - Brand

    static let coral = Color(red: 1.0, green: 0.420, blue: 0.420)   // #FF6B6B
    static let teal = Color(red: 0.306, green: 0.804, blue: 0.769)  // #4ECDC4
    static let gold = Color(red: 1.0, green: 0.902, blue: 0.427)    // #FFE66D

    // MARK: - Surface

    static let surface = Color.white.opacity(0.04)
    static let surfaceBorder = Color.white.opacity(0.06)

    // MARK: - Text

    static let textPrimary = Color.white                     // #FFFFFF
    static let textSecondary = Color.white.opacity(0.4)

    // MARK: - Mascot

    /// `--cloud-body`. Pure white; deliberately a shade brighter than
    /// ``cloudHighlight``, which is the cooler tint on the top bumps. The two
    /// read as one white at a glance — that is the point, and collapsing them
    /// flattens the cloud.
    static let cloudWhite = Color.white                                     // #FFFFFF
    static let cloudHighlight = Color(red: 0.973, green: 0.988, blue: 1.0)  // #F8FCFF
    static let cloudShadow = Color(red: 0.910, green: 0.933, blue: 0.957)   // #E8EEF4

    /// A warm near-black, not `.black`: pure black reads harsh at the size a
    /// toddler sees the face.
    static let eyes = Color(red: 0.176, green: 0.204, blue: 0.212)          // #2D3436

    /// Same value as ``coral``, named separately because the design system does
    /// — the mouth is allowed to drift off brand coral without dragging every
    /// tap highlight with it.
    static let mouth = coral                                                // #FF6B6B
    static let mouthStroke = Color(red: 0.898, green: 0.333, blue: 0.333)   // #E55555

    static let blush = Color(red: 1.0, green: 0.710, blue: 0.710)           // #FFB5B5
    static let blushBeaming = Color(red: 1.0, green: 0.620, blue: 0.620)    // #FF9E9E

    // MARK: - Type

    /// The chunky display face. Wordmark only — see `BundledFonts`.
    ///
    /// Deliberately delegates rather than calling `Font.custom` with a literal:
    /// the PostScript name is `LilitaOne`, not the file name `LilitaOne-Regular`,
    /// and `Font.custom` fails to a system fallback in silence when it is wrong.
    static func display(_ size: CGFloat) -> Font {
        .cloudmojiLogo(size: size)
    }

    /// Everything else. SF Rounded stands in for Nunito on the web.
    static func body(_ size: CGFloat, _ weight: Font.Weight = .heavy) -> Font {
        .cloudmojiRounded(size: size, weight: weight)
    }
}
