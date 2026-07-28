import SwiftUI
import CloudmojiCore

/// Which shape the same list of categories takes.
///
/// `horizontal` is the portrait strip (`src/components/CategoryBar.tsx`);
/// `rail` is the landscape side rail (`src/components/SideRail.tsx`).
enum CategoryLayout {
    case horizontal   // portrait: a scrolling strip
    case rail         // landscape: a vertical rail of icons
}

/// Every number both layouts are drawn from.
///
/// Split out of the view for the same reason as ``EmojiTileMetrics``: the 64pt
/// child-facing floor is a *rule*, and a rule can only be asserted if it is
/// reachable. `CategorySourceTests` measures the drawn chips against these.
///
/// Values come from the two shipped web components rather than from the
/// category-tab rows of `docs/design/DESIGN_SYSTEM.md`, which still describe the
/// pre-touch-target prototype (radius 14, 12px label, 11×5 padding). Where the
/// table and `src/` disagree, `src/` is what a child has actually used.
enum CategorySourceMetrics {
    /// The floor for anything a *child* taps, and a category chip is squarely
    /// that — the toddler changes categories far more often than the parent.
    /// Not the 44pt HIG minimum, which governs the header's parent-only chrome.
    static let side: CGFloat = 64

    /// Minimum gap between two child-facing targets — between chips in the
    /// strip, and both between and below the icons in the rail.
    static let spacing: CGFloat = 8

    static let cornerRadius: CGFloat = 16

    /// `1.5px solid` on the web, kept as-is: unlike the emoji tile's hairline
    /// this border is the *active* indicator, so the extra half point is doing
    /// visible work.
    static let borderWidth: CGFloat = 1.5

    /// The icon reads as a bullet beside the label in the strip, and carries the
    /// whole meaning on its own in the rail — hence the jump.
    static let stripGlyphSize: CGFloat = 18
    static let railGlyphSize: CGFloat = 28

    /// `font-size: 14; font-weight: 800` — `.heavy` is 800.
    static let labelSize: CGFloat = 14

    /// Inside a labelled chip: `padding: 7px 16px` with the 64pt minimum height
    /// doing the vertical work.
    static let chipHorizontalPadding: CGFloat = 16
    /// `gap: 4` between icon and label.
    static let chipGap: CGFloat = 4

    /// `padding: 2px 12px 6px` on the strip's scroll container.
    static let stripInset: CGFloat = 12
    static let stripTopPadding: CGFloat = 2
    static let stripBottomPadding: CGFloat = 6

    /// `padding: 8px 4px` on the rail's scroll container.
    static let railInset: CGFloat = 4

    /// Design system Active States: category tabs `scale(0.9)`. Emoji tiles take
    /// 0.85 and control buttons 0.88 — one style, three numbers, all in the
    /// table.
    static let pressedScale: CGFloat = 0.9

    /// `filter: grayscale(0.4) opacity(0.65)` on an unselected rail icon. The
    /// rail has no plate behind an inactive icon, so this dimming is the only
    /// thing separating "9 categories" from "9 equally shouting emojis".
    static let inactiveGrayscale: Double = 0.4
    static let inactiveOpacity: Double = 0.65

    /// What the rail asks its container for — `RAIL_WIDTH` in `SideRail.tsx`.
    /// Two 64pt columns and their 8pt gap need 136; the rest is breathing room.
    ///
    /// One column left only 2.6 of the 9 categories reachable without
    /// scrolling; two is what made landscape usable. Published here so the
    /// screen that places the rail does not have to repeat the number.
    static let railWidth: CGFloat = 156
}

/// One component, two layouts.
///
/// The web app kept two copies of this list — one for each orientation — and
/// three separate edits landed on the dead copy before anyone noticed. There is
/// deliberately only one here: the layouts differ in arrangement and in how loud
/// an unselected chip is allowed to be, not in what a chip *is*.
struct CategorySource: View {
    let tabs: [CategoryTab]
    let selected: String
    let label: (CategoryTab) -> String
    let layout: CategoryLayout
    let onSelect: (CategoryTab) -> Void

    private static let railColumns = [
        GridItem(.fixed(CategorySourceMetrics.side), spacing: CategorySourceMetrics.spacing),
        GridItem(.fixed(CategorySourceMetrics.side)),
    ]

    var body: some View {
        switch layout {
        case .horizontal:
            // Nine categories do not fit on a 375pt phone and the strip clips
            // mid-chip — `どうぶ…` — with nothing saying there is more. Scroll
            // indicators are off here and iOS hides them at rest anyway, so the
            // chevron is the only thing that says so.
            HintedScrollView(axis: .horizontal, identifier: "category-bar") {
                HStack(spacing: CategorySourceMetrics.spacing) {
                    ForEach(tabs) { tab in chip(tab, showsLabel: true) }
                }
                .padding(.horizontal, CategorySourceMetrics.stripInset)
                .padding(.top, CategorySourceMetrics.stripTopPadding)
                .padding(.bottom, CategorySourceMetrics.stripBottomPadding)
            }

        case .rail:
            // No plate here. `SideRail` draws it for the whole rail, because the
            // rail exists in Count mode too — where there are no categories at
            // all — and two plates would lay 0.5 opacity over 0.5 opacity.
            HintedScrollView(axis: .vertical, identifier: "category-rail") {
                // The rail is deliberately wider than its two 64pt columns, and
                // `LazyVGrid` centres fixed columns in the slack on its own —
                // verified by mutation, not assumed, since an explicit content
                // frame here changed nothing. `railContentIsCentred` is what
                // holds it, because 20pt off-centre is invisible until you look.
                LazyVGrid(columns: Self.railColumns, spacing: CategorySourceMetrics.spacing) {
                    ForEach(tabs) { tab in chip(tab, showsLabel: false) }
                }
                .padding(.vertical, CategorySourceMetrics.spacing)
                .padding(.horizontal, CategorySourceMetrics.railInset)
            }
        }
    }

    private func chip(_ tab: CategoryTab, showsLabel: Bool) -> some View {
        let isActive = tab.id == selected
        let shape = RoundedRectangle(
            cornerRadius: CategorySourceMetrics.cornerRadius,
            style: .continuous
        )
        return Button { onSelect(tab) } label: {
            HStack(spacing: CategorySourceMetrics.chipGap) {
                Text(tab.icon)
                    .font(.system(size: showsLabel
                                  ? CategorySourceMetrics.stripGlyphSize
                                  : CategorySourceMetrics.railGlyphSize))
                    // Only the rail dims its icons: in the strip the muted label
                    // already says "not selected", and a greyed-out 🍎 beside a
                    // full-colour one reads as broken rather than unselected.
                    .grayscale(showsLabel || isActive ? 0 : CategorySourceMetrics.inactiveGrayscale)
                    .opacity(showsLabel || isActive ? 1 : CategorySourceMetrics.inactiveOpacity)
                if showsLabel {
                    Text(label(tab))
                        .font(Theme.body(CategorySourceMetrics.labelSize))
                        .lineLimit(1)
                        // `white-space: nowrap`. Without it the chip accepts a
                        // narrower proposal in the scroll view and "Kenderaan"
                        // wraps to two lines.
                        .fixedSize()
                }
            }
            .frame(
                minWidth: showsLabel ? 0 : CategorySourceMetrics.side,
                minHeight: CategorySourceMetrics.side
            )
            .padding(.horizontal, showsLabel ? CategorySourceMetrics.chipHorizontalPadding : 0)
            .background(plate(isActive: isActive, showsLabel: showsLabel), in: shape)
            .overlay(
                shape.stroke(
                    outline(isActive: isActive, showsLabel: showsLabel),
                    lineWidth: CategorySourceMetrics.borderWidth
                )
            )
            .foregroundStyle(isActive ? Theme.teal : Theme.textSecondary)
            // An inactive rail chip has no plate behind it, so without an
            // explicit hit shape the target is the 28pt glyph box and most of
            // the 64pt square a toddler aims at is dead.
            .contentShape(Rectangle())
        }
        .buttonStyle(PressScale(scale: CategorySourceMetrics.pressedScale))
        .accessibilityLabel(label(tab))
        // One identifier for both layouts: only one is ever on screen, and the
        // web's second `rail-` prefix is exactly the kind of duplicate that let
        // three edits land on a dead copy.
        .accessibilityIdentifier("cat-\(tab.id)")
    }

    /// Active is the same teal wash in both layouts. Inactive differs: the strip
    /// keeps its 4% plate so the chips read as a row of buttons, the rail drops
    /// it so nine icons do not become nine boxes down the side of the screen.
    private func plate(isActive: Bool, showsLabel: Bool) -> Color {
        if isActive { return Theme.teal.opacity(0.2) }
        return showsLabel ? Theme.surface : .clear
    }

    private func outline(isActive: Bool, showsLabel: Bool) -> Color {
        if isActive { return Theme.teal.opacity(0.4) }
        return showsLabel ? Theme.surfaceBorder : .clear
    }
}

#Preview("Strip and rail") {
    let tabs = (try? EmojiRepository())?.categories ?? []
    return ZStack {
        Theme.background.ignoresSafeArea()
        VStack(spacing: 24) {
            CategorySource(
                tabs: tabs, selected: "all", label: { $0.label(.en) },
                layout: .horizontal, onSelect: { _ in }
            )
            HStack(spacing: 0) {
                // Bare, without the plate or the mode tabs — see the `SideRail`
                // preview for the rail as a screen actually shows it.
                CategorySource(
                    tabs: tabs, selected: "animals", label: { $0.label(.en) },
                    layout: .rail, onSelect: { _ in }
                )
                .frame(width: CategorySourceMetrics.railWidth)
                Spacer()
            }
        }
    }
}
