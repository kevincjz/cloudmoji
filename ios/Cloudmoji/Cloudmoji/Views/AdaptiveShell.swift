import SwiftUI
import UIKit

private struct CompactLayoutKey: EnvironmentKey {
    static let defaultValue = false
}

/// The measured canvas shared by every Cloudmoji screen.
///
/// `userInterfaceIdiom` answers a different question from size classes: an iPad
/// in Split View is still an iPad, while a full-screen iPad in landscape can
/// report the same compact vertical size class as a phone. The `isExpandedPad`
/// threshold then decides whether the current window is wide enough to spend
/// that extra room. A narrow Split View slice deliberately falls back to the
/// already-tested phone composition instead of squeezing oversized iPad art
/// into it.
struct CloudmojiLayout: Equatable {
    /// Full-screen iPad mini landscape is 744pt tall before safe-area insets
    /// and can measure below 700pt inside this shell. Keep the threshold below
    /// that usable height so rotating a full-screen iPad never switches the
    /// whole app back to phone sizing. Narrow Split View and short Stage
    /// Manager windows still remain below this floor.
    static let expandedPadMinimumSide: CGFloat = 640

    let size: CGSize
    let isPad: Bool

    init(size: CGSize, isPad: Bool) {
        self.size = size
        self.isPad = isPad
    }

    var isLandscape: Bool { size.width > size.height }

    var isExpandedPad: Bool {
        isPad && min(size.width, size.height) >= Self.expandedPadMinimumSide
    }

    var isCompactPhone: Bool {
        !isPad && size.height <= AdaptiveShell<EmptyView>.compactHeight
            && size.width > size.height
    }
}

private struct CloudmojiLayoutKey: EnvironmentKey {
    static let defaultValue = CloudmojiLayout(size: .zero, isPad: false)
}

extension EnvironmentValues {
    /// True when the screen is short and wide — a phone held sideways. Keyed on
    /// both idiom and height, so an iPad never receives the compressed phone
    /// chrome even when it is in a short Stage Manager window.
    var cloudmojiIsCompact: Bool {
        get { self[CompactLayoutKey.self] }
        set { self[CompactLayoutKey.self] = newValue }
    }

    var cloudmojiLayout: CloudmojiLayout {
        get { self[CloudmojiLayoutKey.self] }
        set { self[CloudmojiLayoutKey.self] = newValue }
    }
}

/// The chrome every screen sits in: the background, and the one decision about
/// how much room there is. It also publishes the actual canvas so larger iPad
/// compositions can stay consistent instead of every screen guessing from its
/// own width.
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

    private let forcedIsPad: Bool?
    private let content: Content

    /// `isPad` is injectable so fixed-size snapshot tests describe the device
    /// they are photographing instead of inheriting whichever simulator happens
    /// to run them. Production callers omit it and use the real device idiom.
    init(isPad: Bool? = nil, @ViewBuilder content: () -> Content) {
        forcedIsPad = isPad
        self.content = content()
    }

    var body: some View {
        GeometryReader { proxy in
            let layout = CloudmojiLayout(
                size: proxy.size,
                isPad: forcedIsPad ?? (UIDevice.current.userInterfaceIdiom == .pad)
            )

            content
                .environment(\.cloudmojiLayout, layout)
                .environment(\.cloudmojiIsCompact, layout.isCompactPhone)
                .frame(width: proxy.size.width, height: proxy.size.height)
        }
        // Only the background reaches under the notch and the home indicator.
        // The content stays inside the safe area, which is what lets the side
        // rail extend its own plate to the edge without insetting anything
        // twice — see `CategorySource.railPlate`.
        .background(Theme.background.ignoresSafeArea())
    }
}
