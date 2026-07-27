import SwiftUI

private struct CompactLayoutKey: EnvironmentKey {
    static let defaultValue = false
}

extension EnvironmentValues {
    /// True when the screen is short and wide — a phone held sideways. Keyed on
    /// height, not orientation alone, so a tall iPad in landscape keeps the
    /// roomy portrait layout.
    var cloudmojiIsCompact: Bool {
        get { self[CompactLayoutKey.self] }
        set { self[CompactLayoutKey.self] = newValue }
    }
}

/// The chrome every screen sits in: the background, and the one decision about
/// how much room there is.
///
/// The threshold is measured, not derived from `verticalSizeClass`. A phone in
/// landscape and an iPad in landscape are both `.compact` height on some
/// devices and not on others, and the thing that actually matters here is
/// whether a 72pt grid still has rows left after the header — which is a
/// question about points.
struct AdaptiveShell<Content: View>: View {
    /// A phone in landscape gives about 320–420pt of usable height; the
    /// shortest iPad is 768. Anything at or under this has no height to spend on
    /// a horizontal category strip, and moves it into the side rail.
    static var compactHeight: CGFloat { 560 }

    @ViewBuilder var content: Content

    var body: some View {
        GeometryReader { proxy in
            content
                // Both halves are load-bearing. Height alone would call a narrow
                // portrait window compact and hand it the landscape rail; width
                // alone would do the same to an iPad in landscape, which has
                // room to spare.
                .environment(\.cloudmojiIsCompact, proxy.size.height <= Self.compactHeight
                             && proxy.size.width > proxy.size.height)
                .frame(width: proxy.size.width, height: proxy.size.height)
        }
        // Only the background reaches under the notch and the home indicator.
        // The content stays inside the safe area, which is what lets the side
        // rail extend its own plate to the edge without insetting anything
        // twice — see `CategorySource.railPlate`.
        .background(Theme.background.ignoresSafeArea())
    }
}
