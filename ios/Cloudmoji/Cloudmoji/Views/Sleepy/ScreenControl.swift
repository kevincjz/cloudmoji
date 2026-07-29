import UIKit

/// Keeps the screen alive, and gives it back.
///
/// A wind-down session is the one place in this app where the phone must not
/// auto-lock: a two-minute breathe with a thirty-second lock timer goes dark
/// halfway through and the child is left looking at himself. It is also the one
/// place where *leaking* that is worst — an app that quietly disabled auto-lock
/// and never re-enabled it flattens the battery overnight, and nothing on screen
/// would ever say so.
///
/// So the flag has an owner with a balanced pair of methods, and the writer is
/// injected: a test can prove hold-and-release without touching the real
/// `UIApplication`, which in a unit test is a shared global that would leak into
/// every test after it.
@MainActor
final class ScreenAwake {
    private let write: (Bool) -> Void

    /// Whether this owner is currently holding the screen awake. Idempotent in
    /// both directions — `hold()` twice writes once, which matters because
    /// `.onAppear` and a scene-phase change can both arrive for one entry.
    private(set) var isHeld = false

    init(write: @escaping (Bool) -> Void = { UIApplication.shared.isIdleTimerDisabled = $0 }) {
        self.write = write
    }

    func hold() {
        guard !isHeld else { return }
        isHeld = true
        write(true)
    }

    func release() {
        guard isHeld else { return }
        isHeld = false
        write(false)
    }
}

/// Turns the screen down across a session, and puts it back exactly where it was.
///
/// The brightness a parent had set is theirs, not this app's, so the original is
/// captured on the first dim and restored on the way out — from `.onDisappear`,
/// from backgrounding, and from reaching the end of the session, because a
/// mini-app that hands back a phone at 12% brightness has broken something the
/// person holding it cannot easily explain.
///
/// Reader and writer are injected for the same reason `ScreenAwake`'s are:
/// `UIScreen.brightness` is device-wide state and a test must not move it.
@MainActor
final class ScreenDimmer {
    private let read: () -> CGFloat
    private let write: (CGFloat) -> Void

    /// What the screen was at before the first dim. `nil` means nothing has been
    /// taken and there is nothing to give back.
    private(set) var original: CGFloat?

    var isDimmed: Bool { original != nil }

    init(
        read: @escaping () -> CGFloat = { ScreenDimmer.screen?.brightness ?? 1 },
        write: @escaping (CGFloat) -> Void = { value in ScreenDimmer.screen?.brightness = value }
    ) {
        self.read = read
        self.write = write
    }

    /// The window's own screen rather than `UIScreen.main`, which is the wrong
    /// answer on a device driving more than one and is on its way out of the SDK.
    private static var screen: UIScreen? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first?
            .screen
    }

    /// The floor. Below about a third the screen is unreadably dark on an
    /// already-dim phone, and the point is a room getting quieter, not a screen
    /// that has failed.
    static let floor: Double = 0.35

    /// The brightness for a session `progress` (0...1) of the way through, as a
    /// fraction of where the screen started. Pure, so the ramp can be checked
    /// without a screen.
    static func level(from original: CGFloat, progress: Double) -> CGFloat {
        let clamped = min(max(progress, 0), 1)
        return original * CGFloat(1 - (1 - floor) * clamped)
    }

    /// Takes the screen down to where `progress` says it should be, remembering
    /// where it started the first time it is called.
    func dim(progress: Double) {
        let base = original ?? read()
        original = base
        write(Self.level(from: base, progress: progress))
    }

    /// Puts it back. Safe to call when nothing was ever taken, and safe to call
    /// twice — which it will be, because three separate exits all call it.
    func restore() {
        guard let original else { return }
        write(original)
        self.original = nil
    }
}
