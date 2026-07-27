import SwiftUI

/// The `:active` transform every tappable element in the app is required to
/// have — "one tap = one action = one reward" from `CLAUDE.md`, spelled out as
/// the Active States table in `docs/design/DESIGN_SYSTEM.md`.
///
/// `.buttonStyle(.plain)` — SwiftUI's usual answer for "no chrome" — also
/// removes the press feedback, so it is never the right choice here. This is
/// the one style; the scale differs per control because the design system says
/// it does (emoji 0.85, tabs 0.9, control buttons 0.88), which is why it is a
/// parameter rather than four near-identical styles.
struct PressScale: ButtonStyle {
    /// No default: every call site should have looked its value up in the
    /// design system's Active States table, and a default invites a fifth
    /// number nobody chose.
    var scale: CGFloat

    /// `transition: transform 0.1s` — one duration across the whole app.
    var duration: Double = 0.1

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? scale : 1)
            .animation(
                // CSS `ease` is cubic-bezier(0.25, 0.1, 0.25, 1); SwiftUI has no
                // named equivalent.
                .timingCurve(0.25, 0.1, 0.25, 1, duration: duration),
                value: configuration.isPressed
            )
    }
}
