import SwiftUI

enum HomeButtonMetrics {
    /// A **child** taps this — it is how he gets out of a mini-app he did not
    /// mean to open — so it takes the 64pt floor, not the 44pt parent-chrome
    /// one. It is the only navigation control left in the app.
    ///
    /// 84, not 64, and centred rather than tucked in a corner. Tested on a real
    /// phone with the person it is for: 64pt in the bottom-left was hard to
    /// *find*, which is a different failure from hard to hit. The corner is
    /// where adults expect Back; a two-year-old scans the middle. This is the
    /// most important control in the app for a child who has opened the wrong
    /// thing, so it gets the most generous target in the app.
    static let side: CGFloat = 84

    /// The mascot inside the circle. Sized so the cloud's letterboxed art
    /// (`size × size * 0.78`) sits inside the plate with a ring around it.
    static let glyphSize: CGFloat = 58

    /// How far the hosting screen keeps its own content clear of the button:
    /// the button, its inset, and a little air.
    static let reservedHeight: CGFloat = 108

    static let inset: CGFloat = 12

    /// Design system Active States: tabs `scale(0.9)`.
    static let pressedScale: CGFloat = 0.9

    static let borderWidth: CGFloat = 2
}

/// The way out of every mini-app.
///
/// A cloud rather than a chevron, and 64pt rather than a nav bar's 44, because
/// the person who most needs it cannot read "Back" and does not know that the
/// top-left corner of a screen means anything. He does know the cloud.
///
/// Its own type so the 64pt rule can be measured off a real render rather than
/// read back out of the constant that set it.
struct CloudHomeButton: View {
    /// The newer mini-apps tint the navigation ring with their own identity.
    /// `nil` preserves the original Words / Count treatment exactly.
    var accent: Color?
    let action: () -> Void

    init(accent: Color? = nil, action: @escaping () -> Void) {
        self.accent = accent
        self.action = action
    }

    var body: some View {
        Button {
            Haptics.tap()
            action()
        } label: {
            ZStack(alignment: .bottomTrailing) {
                CloudMascot(mood: .happy, size: HomeButtonMetrics.glyphSize)
                    // Hidden, unlike every other mascot in the app.
                    //
                    // `CloudMascot` publishes `mascot-<mood>` as its identifier, and
                    // that is the only way `WordsModeUITests` can observe a
                    // celebration. A second, permanently-happy cloud on the same
                    // screen would make every one of those lookups ambiguous — the
                    // test would measure whichever matched first and could pass with
                    // the celebration deleted. The button's own label is what
                    // VoiceOver reads here.
                    .accessibilityHidden(true)

                Image(systemName: "house.fill")
                    .font(.system(size: 12, weight: .black))
                    .foregroundStyle(Theme.bgPrimary)
                    .frame(width: 26, height: 26)
                    .background(accent ?? Theme.teal, in: Circle())
                    .overlay(Circle().stroke(Color.white.opacity(0.55), lineWidth: 1))
                    .offset(x: 3, y: 3)
                    .accessibilityHidden(true)
            }
                .frame(width: HomeButtonMetrics.side, height: HomeButtonMetrics.side)
                // Two layers, and the opaque one is load-bearing. The button
                // floats over a screen that scrolls underneath it — the animal
                // grid and the emoji grid both do — and `Theme.surface` is white
                // at 4%, so on its own the cloud came out sitting on top of a
                // half-visible emoji. The near-opaque plate is the one the tab
                // bar used to wear, at the same 0.95, so the two read as the
                // same piece of chrome in the same corner.
                .background(Theme.bgPrimary.opacity(0.95), in: Circle())
                .background((accent ?? Theme.textPrimary).opacity(accent == nil ? 0.04 : 0.14), in: Circle())
                .overlay(
                    Circle().stroke(
                        accent?.opacity(0.44) ?? Theme.surfaceBorderStrong,
                        lineWidth: HomeButtonMetrics.borderWidth
                    )
                )
                .shadow(color: accent?.opacity(0.18) ?? .clear, radius: 14, y: 5)
                // Without this the hit area is the mascot's own art box and the
                // ring of plate a toddler aims at is dead. `.contentShape` takes
                // the circle rather than a rectangle so the corners outside the
                // plate do not swallow taps meant for what is underneath.
                .contentShape(Circle())
        }
        .buttonStyle(PressScale(scale: HomeButtonMetrics.pressedScale))
        .accessibilityLabel("Home")
        .accessibilityIdentifier("home-btn")
    }
}

#Preview("Home button") {
    ZStack {
        Theme.background.ignoresSafeArea()
        HStack {
            CloudHomeButton {}
            CloudHomeButton(accent: Theme.coral) {}
        }
    }
}
