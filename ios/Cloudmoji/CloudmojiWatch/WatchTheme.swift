import SwiftUI

/// The watch's own slice of the design system.
///
/// `Theme` proper stays in the app target — it pulls in the bundled fonts and
/// the full palette, most of which the watch never draws. These are the few
/// values Pocket Cloud actually needs, transcribed from
/// `docs/design/DESIGN_SYSTEM.md` so the wrist matches the phone.
enum WatchTheme {
    static let bgPrimary = Color(red: 0.059, green: 0.055, blue: 0.165) // #0F0E2A
    static let bgMid = Color(red: 0.102, green: 0.067, blue: 0.271)     // #1A1145
    static let bgEdge = Color(red: 0.051, green: 0.129, blue: 0.216)    // #0D2137

    static let teal = Color(red: 0.306, green: 0.804, blue: 0.769)      // #4ECDC4

    /// The app's background gradient, top to bottom (the 160° tilt is dropped
    /// the same way the phone drops it — invisible over a near-black gradient).
    static let background = LinearGradient(
        stops: [
            .init(color: bgPrimary, location: 0.0),
            .init(color: bgMid, location: 0.4),
            .init(color: bgEdge, location: 1.0),
        ],
        startPoint: .top,
        endPoint: .bottom
    )

    /// One emoji fills the face. Large, because it is the whole interface and a
    /// parent glances at it from arm's length.
    static let emojiSize: CGFloat = 72
}
