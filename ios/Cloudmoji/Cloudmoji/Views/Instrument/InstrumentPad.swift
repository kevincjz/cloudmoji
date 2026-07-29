import SwiftUI

enum InstrumentPadMetrics {
    /// The preferred child-facing size, not the 64pt floor. A pad is struck
    /// rather than aimed at, often with a whole hand.
    static let minimumSide: CGFloat = 72

    /// `CLAUDE.md` rule 2, the floor between adjacent child-facing targets.
    static let spacing: CGFloat = 8

    static let cornerRadius: CGFloat = 20
    static let borderWidth: CGFloat = 2

    /// Design system Active States: emoji tiles `scale(0.85)`. A pad is the most
    /// tile-like thing on this screen and takes the same number.
    static let pressedScale: CGFloat = 0.85

    /// The eight colours, one per pad, so a child can aim at "the red one".
    /// Brand hues first, then the two Sleepy Cloud tints, then round again —
    /// deliberately not eight invented colours.
    static let tints: [Color] = [
        Theme.coral, Theme.teal, Theme.gold, Theme.amber,
        Theme.moonlight, Theme.lavender, Theme.coral, Theme.teal,
    ]

    static func tint(_ index: Int) -> Color {
        tints.isEmpty ? Theme.teal : tints[index % tints.count]
    }
}

/// One key.
///
/// **It sounds on touch-down, not on touch-up**, which is why this is a
/// `DragGesture(minimumDistance: 0)` rather than a `Button`. A `Button` fires on
/// release, and an instrument that waits for the finger to come off does not read
/// as an instrument — it reads as broken, and a child stops trying. The trade is
/// that a finger dragged off the pad still counts, which is exactly what a real
/// keyboard does.
///
/// `hasStruck` is what keeps one press from firing on every gesture update: the
/// gesture reports continuously while the finger is down, and without the latch a
/// held finger machine-guns the note.
struct InstrumentPad: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    let index: Int
    let side: CGFloat
    let onStrike: () -> Void

    @State private var hasStruck = false
    @GestureState private var isDown = false

    private var tint: Color { InstrumentPadMetrics.tint(index) }

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: InstrumentPadMetrics.cornerRadius, style: .continuous)
    }

    var body: some View {
        ZStack {
            shape
                .fill(
                    LinearGradient(
                        colors: [
                            tint.opacity(isDown ? 0.96 : 0.72),
                            tint.opacity(isDown ? 0.62 : 0.34),
                            Theme.bgPrimary.opacity(0.92),
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )

            RoundedRectangle(cornerRadius: 999)
                .fill(Color.white.opacity(isDown ? 0.30 : 0.16))
                .frame(width: side * 0.62, height: max(5, side * 0.07))
                .offset(y: -side * 0.31)

            Circle()
                .fill(Theme.bgPrimary.opacity(0.30))
                .frame(width: side * 0.34)
                .overlay(
                    Text("\(index + 1)")
                        .font(Theme.body(max(15, side * 0.18), .black))
                        .foregroundStyle(Color.white.opacity(0.88))
                )

            Image(systemName: "music.note")
                .font(.system(size: max(18, side * 0.23), weight: .black))
                .foregroundStyle(Color.white)
                .opacity(isDown ? 1 : 0)
                .scaleEffect(isDown ? 1 : 0.55)
                .offset(x: side * 0.28, y: -side * 0.27)
        }
            .clipShape(shape)
            .overlay(shape.stroke(Color.white.opacity(isDown ? 0.50 : 0.20), lineWidth: InstrumentPadMetrics.borderWidth))
            .frame(width: side, height: side)
            .scaleEffect(isDown ? InstrumentPadMetrics.pressedScale : 1)
            .offset(y: isDown && !reduceMotion ? 4 : 0)
            .shadow(
                color: tint.opacity(isDown ? 0.44 : 0.22),
                radius: isDown ? 18 : 12,
                y: isDown ? 4 : 9
            )
            .animation(
                reduceMotion
                    ? .easeOut(duration: 0.06)
                    : .spring(response: 0.18, dampingFraction: 0.72),
                value: isDown
            )
            // Separate views get separate touches, so two fingers on two pads is
            // two gestures and the chord is free.
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .updating($isDown) { _, state, _ in state = true }
                    .onChanged { _ in
                        guard !hasStruck else { return }
                        hasStruck = true
                        Haptics.tap()
                        onStrike()
                    }
                    .onEnded { _ in hasStruck = false }
            )
            // A `Shape` is not an accessibility element on its own, so without
            // this the pad is invisible to VoiceOver *and* to XCUITest — the
            // identifier below would land on nothing and every assertion about
            // it would be unable to fail.
            .accessibilityElement()
            .accessibilityAddTraits(.isButton)
            .accessibilityLabel("Note \(index + 1)")
            .accessibilityIdentifier("pad-\(index)")
    }
}
