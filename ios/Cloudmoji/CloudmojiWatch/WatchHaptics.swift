import WatchKit

/// The taps a wrist feels.
///
/// The phone's `Haptics` uses `UIImpactFeedbackGenerator`, which does not exist
/// on watchOS; the watch has its own richer haptic vocabulary through
/// `WKInterfaceDevice`. Same three-verb shape, different engine — deliberately a
/// separate type rather than a shared one behind `#if os`, because the two
/// platforms' haptic APIs share nothing.
@MainActor
enum WatchHaptics {
    /// The parent sent an emoji from the wrist.
    static func tap() {
        WKInterfaceDevice.current().play(.click)
    }

    /// An emoji arrived from the child's phone — the wrist announces it even if
    /// the parent is not looking at the screen.
    static func received() {
        WKInterfaceDevice.current().play(.success)
    }
}
