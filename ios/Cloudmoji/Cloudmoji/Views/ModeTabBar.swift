import SwiftUI

/// The app's two modes.
///
/// The captions stay English in all five languages, as they do on the web. They are
/// read by a parent once; the child navigates by the icons, which are the same
/// glyphs `AboutView` and the App Store listing use.
enum AppMode: String, CaseIterable, Identifiable {
    case words, count

    var id: String { rawValue }

    var icon: String {
        switch self {
        case .words: "🗣️"
        // 🧮 rather than 🔢: the web changed it because 🔢 renders as a grey box on
        // several platforms, and it is worth keeping the two listings identical.
        case .count: "🧮"
        }
    }

    var label: String {
        switch self {
        case .words: "Words"
        case .count: "Count"
        }
    }
}

enum ModeTabLayout {
    /// Upright: a bar across the bottom of the screen.
    case bar
    /// Sideways: two icons at the foot of the side rail, where the scarce axis is
    /// horizontal rather than vertical.
    case rail
}

enum ModeTabBarMetrics {
    /// The floor for anything a **child** taps, and the tab bar is squarely that —
    /// a toddler changes modes far more often than a parent does.
    ///
    /// The web shipped this at 42.5pt on notched phones: `box-sizing: border-box`
    /// meant the home-indicator inset was subtracted from `min-height: 64px`
    /// instead of added below it. Do not let the safe area into this number.
    static let side: CGFloat = 64

    static let spacing: CGFloat = 8
    static let iconSize: CGFloat = 26
    /// Design system type scale, tab captions: 12px / 900.
    static let labelSize: CGFloat = 12
    static let iconLabelSpacing: CGFloat = 3

    /// An unselected icon is dimmed rather than recoloured — a greyed 🧮 beside a
    /// full-colour 🗣️ reads as "not this one", where two full-colour glyphs read
    /// as two equally live buttons.
    static let inactiveGrayscale: Double = 0.5
    static let inactiveOpacity: Double = 0.5

    /// Design system Active States: tabs `scale(0.9)`. Emoji tiles take 0.85 and
    /// control buttons 0.88 — one style, three numbers, all in the table.
    static let pressedScale: CGFloat = 0.9

    /// `rgba(15,14,42,0.95)` with `backdrop-filter: blur(12px)` on the web. The
    /// blur is dropped: over a near-black gradient it is invisible and it costs an
    /// offscreen pass on every scroll frame.
    static let plateOpacity: Double = 0.95

    // MARK: Rail only

    /// `border-radius: 16` on the rail's tabs, matching a rail category chip.
    static let cornerRadius: CGFloat = 16

    /// `background: rgba(78,205,196,0.14)` on the *active* rail tab
    /// (`src/components/SideRail.tsx`). The bar can say "this one" with the
    /// caption's colour; the rail has no captions, so the wash is the only thing
    /// left that can.
    static let railActivePlate: Double = 0.14

    /// `padding-left/right: 4` on the rail's tab section — the same inset the rail
    /// gives its category grid.
    static let railInset: CGFloat = 4
}

/// Words ⇄ Count.
///
/// One component, two layouts — the same shape as `CategorySource`, and for the
/// same reason: the web kept a bar and a rail copy of this list and three separate
/// edits landed on the dead one.
struct ModeTabBar: View {
    let mode: AppMode
    let layout: ModeTabLayout
    let onSelect: (AppMode) -> Void

    var body: some View {
        switch layout {
        case .bar:
            HStack(spacing: 0) {
                ForEach(AppMode.allCases) { tab in
                    self.tab(tab, showsLabel: true)
                        .frame(maxWidth: .infinity)
                }
            }
            // No `.frame(minHeight:)` on the row. The bar's height is its tabs'
            // own 64pt frames and nothing else — which is deliberate, not an
            // omission: a second 64 here would be redundant with the first, and
            // redundant is the same thing as untestable. Deleting the tab frame
            // would leave the bar at 64pt on the row's say-so while the *target* a
            // toddler aims at collapsed to 50, and `barClearsTheChildMinimum`
            // would have gone on passing. Verified by running exactly that
            // mutation against both versions.
            //
            // What the safe area must not touch is therefore the tabs. The plate
            // below reaches under the home indicator; nothing above it does.
            .background(alignment: .top) {
                // `.ignoresSafeArea` on the *plate only*, never on the row above.
                // That is the whole difference between a 64pt target with a
                // coloured strip beneath it and the web's 42.5pt target.
                Theme.bgPrimary.opacity(ModeTabBarMetrics.plateOpacity)
                    .ignoresSafeArea(edges: .bottom)
                    .overlay(alignment: .top) {
                        Rectangle().fill(Theme.surfaceBorder).frame(height: 1)
                    }
            }
            // `.contain` before the identifier, or it propagates down to the
            // nearest element on each branch and overwrites `tab-words` and
            // `tab-count` with "tab-bar" — the exact defect
            // `WordsModeUITests.testTypingRowControlsKeepTheirOwnIdentifiers`
            // exists for. Invisible to a unit test: SwiftUI builds no
            // accessibility tree outside XCUITest.
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("tab-bar")

        case .rail:
            // Not a `LazyVGrid`: two tabs are not worth a lazy container, and a
            // lazy container realises its children out of order, which is how a
            // scaled-up neighbour ends up painted underneath.
            HStack(spacing: ModeTabBarMetrics.spacing) {
                ForEach(AppMode.allCases) { tab in
                    self.tab(tab, showsLabel: false)
                }
            }
            .padding(.vertical, ModeTabBarMetrics.spacing)
            .padding(.horizontal, ModeTabBarMetrics.railInset)
            // `border-top` on the rail's tab section, the same hairline the
            // portrait bar wears — the tabs read as one control in both layouts.
            .overlay(alignment: .top) {
                Rectangle().fill(Theme.surfaceBorder).frame(height: 1)
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("tab-rail")
        }
    }

    private func tab(_ tab: AppMode, showsLabel: Bool) -> some View {
        let isActive = tab == mode
        let shape = RoundedRectangle(
            cornerRadius: ModeTabBarMetrics.cornerRadius,
            style: .continuous
        )
        return Button { onSelect(tab) } label: {
            VStack(spacing: ModeTabBarMetrics.iconLabelSpacing) {
                Text(tab.icon)
                    .font(.system(size: ModeTabBarMetrics.iconSize))
                    .grayscale(isActive ? 0 : ModeTabBarMetrics.inactiveGrayscale)
                    .opacity(isActive ? 1 : ModeTabBarMetrics.inactiveOpacity)
                if showsLabel {
                    Text(tab.label)
                        .font(Theme.body(ModeTabBarMetrics.labelSize, .black))
                        // The caption is text, so it takes the button's tint —
                        // system accent blue — unless this says otherwise.
                        .foregroundStyle(isActive ? Theme.teal : Theme.textSecondary)
                        .lineLimit(1)
                }
            }
            .frame(minWidth: ModeTabBarMetrics.side, minHeight: ModeTabBarMetrics.side)
            // Only the rail plates its selection; the bar's caption already says
            // which one, and a wash behind a labelled tab reads as a pressed
            // button that never came back up.
            .background(
                showsLabel || !isActive
                    ? Color.clear
                    : Theme.teal.opacity(ModeTabBarMetrics.railActivePlate),
                in: shape
            )
            // Without an explicit hit shape the target is the icon's own box and
            // most of the 64pt a toddler aims at is dead — the bar has no plate at
            // all, and the rail only has one behind the tab already selected.
            .contentShape(Rectangle())
        }
        .buttonStyle(PressScale(scale: ModeTabBarMetrics.pressedScale))
        .accessibilityLabel(tab.label)
        .accessibilityAddTraits(isActive ? [.isSelected] : [])
        .accessibilityIdentifier("tab-\(tab.rawValue)")
    }
}

#Preview("Bar and rail") {
    ZStack {
        Theme.background.ignoresSafeArea()
        VStack(spacing: 40) {
            ModeTabBar(mode: .words, layout: .bar, onSelect: { _ in })
            ModeTabBar(mode: .count, layout: .rail, onSelect: { _ in })
        }
    }
}
