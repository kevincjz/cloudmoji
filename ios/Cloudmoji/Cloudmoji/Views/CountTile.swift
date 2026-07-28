import SwiftUI

/// Every number Count mode's grid is drawn from.
///
/// Transcribed from `src/components/CountMode.tsx`. The sizes are step functions
/// of the round size because a round of nine 96pt tiles does not fit on a phone —
/// and the steps stop at 64, because shrinking a child's target below the floor to
/// make a layout fit is the one trade this project does not make.
enum CountTileMetrics {
    /// The floor. Named rather than inlined so the tests can say what they are
    /// enforcing, and so nobody has to guess whether 64 or 72 applies here — a
    /// count tile is child-facing, and 72 is *preferred*, not required.
    static let childMinimum: CGFloat = 64

    static func side(count: Int, compact: Bool) -> CGFloat {
        if compact { return count <= 5 ? 72 : 64 }
        if count <= 3 { return 96 }
        if count <= 6 { return 82 }
        return 72
    }

    static func glyphSize(count: Int, compact: Bool) -> CGFloat {
        if compact { return count <= 5 ? 44 : 36 }
        if count <= 3 { return 64 }
        if count <= 6 { return 54 }
        return 46
    }

    /// Three across upright, five sideways — and never more columns than there are
    /// tiles. The web hard-codes three upright, which leaves a round of two hugging
    /// the left of a three-column track instead of centred under the readout.
    static func columns(count: Int, compact: Bool) -> Int {
        min(count, compact ? 5 : 3)
    }

    /// Roomier for the small rounds, which have the space for it. Never under the
    /// 8pt floor between two things a child taps.
    static func gridSpacing(count: Int, compact: Bool) -> CGFloat {
        if compact { return 10 }
        return count <= 4 ? 16 : 12
    }

    /// `max-width: 360` upright, `520` sideways. The grid centres inside whatever
    /// it is given, so this stops a round of two spreading across an iPad.
    static func maxGridWidth(compact: Bool) -> CGFloat {
        compact ? 520 : 360
    }

    static let cornerRadius: CGFloat = 22
    /// `2.5px solid`. Heavier than a plate hairline because on a counted tile this
    /// border is the state.
    static let borderWidth: CGFloat = 2.5

    /// The order badge: a 34pt disc hung off the top-right corner.
    static let badgeSide: CGFloat = 34
    /// How far it hangs outside the tile, on both axes. The grid pads by this much
    /// so a top-row badge is not clipped.
    static let badgeOverhang: CGFloat = 10
    static let badgeGlyphSize: CGFloat = 19
    static let badgeBorderWidth: CGFloat = 2.5

    /// Uncounted tiles sit back so the counted ones come forward.
    static let uncountedOpacity: Double = 0.75
    static let uncountedScale: CGFloat = 0.95

    /// Design system Active States: this is a tile, so `scale(0.85)` — the same as
    /// an emoji tile, not the 0.88 of a control button.
    static let pressedScale: CGFloat = 0.85

    /// `bounceEmoji 0.35s ease`, fired on the tile just counted.
    static let bounceScale: CGFloat = 1.15
    static let bounceDuration: Double = 0.35
    /// `transition: all 0.25s ease` between the counted and uncounted looks.
    static let stateDuration: Double = 0.25
}

/// One of the N identical things on screen.
///
/// It knows nothing about the round: it is handed a badge number or `nil`, and a
/// size. That is what lets it be measured on its own, and it is why the round's
/// rules live in `CountRound` where they can be tested.
struct CountTile: View {
    let emoji: String
    /// Position in the grid, for the accessibility identifier only. Counting order
    /// is `badge`, and the two are deliberately different: the child counts the
    /// tiles in whatever order they like.
    let index: Int
    /// The number this tile was counted as, or `nil` if it has not been counted.
    let badge: Int?
    /// True for the tile counted most recently, which bounces once.
    let isJustCounted: Bool
    let side: CGFloat
    let glyphSize: CGFloat
    let onTap: () -> Void

    private var isCounted: Bool { badge != nil }

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: CountTileMetrics.cornerRadius, style: .continuous)
    }

    var body: some View {
        Button(action: onTap) {
            Text(emoji)
                .font(.system(size: glyphSize))
                .frame(width: side, height: side)
                .background(
                    isCounted ? Theme.teal.opacity(0.15) : Theme.surface,
                    in: shape
                )
                .overlay(
                    shape.stroke(
                        isCounted ? Theme.teal.opacity(0.6) : Theme.surfaceBorderStrong,
                        lineWidth: CountTileMetrics.borderWidth
                    )
                )
                // Without an explicit hit shape the tappable area is the glyph's
                // own box and the corners of the square a toddler aims at are dead.
                .contentShape(Rectangle())
                .overlay(alignment: .topTrailing) { badgeView }
        }
        .buttonStyle(PressScale(scale: CountTileMetrics.pressedScale))
        .opacity(isCounted ? 1 : CountTileMetrics.uncountedOpacity)
        .scaleEffect(isJustCounted
                     ? CountTileMetrics.bounceScale
                     : (isCounted ? 1 : CountTileMetrics.uncountedScale))
        // The badge hangs 10pt outside the tile and a bouncing tile overlaps its
        // neighbours, and a grid paints in order — so without this the tile the
        // child just touched has its badge drawn *underneath* the tiles after it.
        // Visible on a device, invisible to every assertion in this suite.
        .zIndex(isCounted || isJustCounted ? 1 : 0)
        .animation(.spring(duration: CountTileMetrics.bounceDuration), value: isJustCounted)
        .animation(.easeInOut(duration: CountTileMetrics.stateDuration), value: isCounted)
        .accessibilityLabel(Text(verbatim: emoji))
        // The badge number, so a UI test can assert *which* number a tile was
        // counted as without reading pixels. Empty when uncounted.
        .accessibilityValue(badge.map(String.init) ?? "")
        .accessibilityIdentifier("count-item-\(index)")
    }

    @ViewBuilder private var badgeView: some View {
        if let badge {
            Text(String(badge))
                // A digit is text, not a colour emoji, so it takes the button's
                // tint — system accent blue — unless this says otherwise.
                .font(Theme.body(CountTileMetrics.badgeGlyphSize, .black))
                .foregroundStyle(Theme.textPrimary)
                .frame(width: CountTileMetrics.badgeSide, height: CountTileMetrics.badgeSide)
                .background(
                    // `linear-gradient(135deg, #4ECDC4, #44B8AC)`.
                    LinearGradient(
                        colors: [Theme.teal, Theme.tealDeep],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    in: Circle()
                )
                .overlay(
                    Circle().stroke(
                        Theme.textPrimary.opacity(0.3),
                        lineWidth: CountTileMetrics.badgeBorderWidth
                    )
                )
                // Flatten before the shadow, or the disc, the ring and the digit
                // each cast their own and the badge reads as three stacked things.
                .compositingGroup()
                .shadow(color: Theme.teal.opacity(0.4), radius: 4, x: 0, y: 2)
                .offset(x: CountTileMetrics.badgeOverhang, y: -CountTileMetrics.badgeOverhang)
                .transition(.scale.combined(with: .opacity))
        }
    }
}

#Preview("Counted and uncounted") {
    ZStack {
        Theme.background.ignoresSafeArea()
        HStack(spacing: 16) {
            CountTile(emoji: "🐶", index: 0, badge: 1, isJustCounted: false,
                      side: 96, glyphSize: 64, onTap: {})
            CountTile(emoji: "🐶", index: 1, badge: nil, isJustCounted: false,
                      side: 96, glyphSize: 64, onTap: {})
            CountTile(emoji: "🐶", index: 2, badge: 12, isJustCounted: true,
                      side: 96, glyphSize: 64, onTap: {})
        }
        .padding(24)
    }
}
