import SwiftUI
import CloudmojiCore

/// Every number the tile is drawn from, in one place.
///
/// Pulled out of the view for two reasons. The grid sizes its columns from
/// ``side`` rather than repeating `72`, so a column can never end up narrower
/// than the tile it holds — that failure clips glyphs and is invisible until
/// someone looks at a device. And the touch-target rule in `CLAUDE.md` is a
/// *rule*, not a literal, so it can be asserted here the way
/// `tests/review-fixes.spec.ts` asserts it on the web.
///
/// Values come from `docs/design/DESIGN_SYSTEM.md` (72 × 72, radius 18, 8pt
/// gap, `:active` scale 0.85) and `src/components/EmojiButton.tsx`.
enum EmojiTileMetrics {
    /// 64pt is the floor for anything a *child* taps; 72pt is the preferred
    /// size, and this is the surface a toddler taps more than any other.
    /// Do not shrink this to make a layout fit — add a column instead.
    static let side: CGFloat = 72

    /// Minimum gap between two child-facing targets. Doubles as the grid's
    /// row and column spacing (`gap-2` on the web).
    static let spacing: CGFloat = 8

    static let cornerRadius: CGFloat = 18

    /// The emoji glyph. Two points larger than the web's `font-size: 38`, which
    /// Apple Color Emoji carries comfortably inside 72pt —
    /// `EmojiTileTests.glyphFitsInsideTheTile` holds that line.
    static let glyphSize: CGFloat = 40

    /// The web draws 1.5px. At 6% white on a near-black background the
    /// difference is a hairline either way.
    static let borderWidth: CGFloat = 1

    /// `:active { transform: scale(0.85) }`. The design system requires a
    /// press transform on *every* tappable element: one tap, one reward.
    static let pressedScale: CGFloat = 0.85

    /// `transition: transform 0.1s` on the web.
    static let pressDuration: Double = 0.1

    /// The peak of `@keyframes bounceEmoji`, fired when a word is spoken.
    static let bounceScale: CGFloat = 1.3

    /// A spring rather than the stylesheet's `0.35s ease`. The web keyframe is
    /// a round trip — up and back — driven by one animation; here the caller
    /// raises `isBouncing` and drops it again, so each leg is animated
    /// separately and a spring is what makes the return leg land rather than
    /// glide. Sized to finish inside the ~400ms the caller holds the flag.
    static let bounceDuration: Double = 0.4
}

/// One emoji. 72pt is the project's preferred child-facing target; the rule is
/// 64pt minimum, and this is the surface a toddler taps most.
///
/// The tile is deliberately wider than ``EmojiTileMetrics/side`` when its
/// column is: the web grid is `repeat(auto-fill, minmax(72px, 1fr))`, so tiles
/// stretch to share the row rather than leaving ragged gaps. Pinning the width
/// to 72 instead would spread the leftover space *between* tiles and put the
/// emoji somewhere other than under the finger that aimed at it.
struct EmojiTile: View {
    let entry: EmojiEntry
    var isBouncing: Bool = false
    let onTap: () -> Void

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: EmojiTileMetrics.cornerRadius, style: .continuous)
    }

    var body: some View {
        Button(action: onTap) {
            Text(entry.emoji)
                .font(.system(size: EmojiTileMetrics.glyphSize))
                .frame(
                    minWidth: EmojiTileMetrics.side,
                    maxWidth: .infinity,
                    minHeight: EmojiTileMetrics.side
                )
                .background(Theme.surface, in: shape)
                .overlay(
                    shape.stroke(Theme.surfaceBorder, lineWidth: EmojiTileMetrics.borderWidth)
                )
                // Without this the hit area is the glyph's own box — roughly
                // 47pt — and the surrounding third of the tile looks tappable
                // but is not. A toddler aims at the square, not the apple.
                .contentShape(Rectangle())
        }
        .buttonStyle(
            PressScale(
                scale: EmojiTileMetrics.pressedScale,
                duration: EmojiTileMetrics.pressDuration
            )
        )
        .scaleEffect(isBouncing ? EmojiTileMetrics.bounceScale : 1)
        // A tile at 1.3× overlaps its neighbours, and the grid paints in order,
        // so without this the tile the child just touched is partly covered by
        // the ones after it — the reward arrives half hidden. Visible on the
        // simulator, invisible to every assertion in the suite.
        .zIndex(isBouncing ? 1 : 0)
        .animation(.spring(duration: EmojiTileMetrics.bounceDuration), value: isBouncing)
        // English regardless of the chosen language: VoiceOver is parent-facing
        // chrome here, and the identifier below is what the tests key on.
        .accessibilityLabel(entry.en)
        .accessibilityIdentifier("emoji-\(entry.emoji)")
    }
}

// The `:active` transform now lives in `PressScale.swift` — the typing row's
// controls need the same style at a different scale, and the design system says
// there is one press behaviour, not one per component.

#Preview("Resting and bouncing") {
    let entries = Array((try? EmojiRepository())?.emojis.prefix(4) ?? [])
    return ZStack {
        Theme.background.ignoresSafeArea()
        HStack(spacing: EmojiTileMetrics.spacing) {
            ForEach(Array(entries.enumerated()), id: \.element.id) { index, entry in
                EmojiTile(entry: entry, isBouncing: index == 1) {}
                    .frame(width: EmojiTileMetrics.side)
            }
        }
    }
}
