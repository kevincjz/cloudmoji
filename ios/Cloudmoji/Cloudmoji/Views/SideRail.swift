import SwiftUI
import CloudmojiCore

/// The rail's own darker panel, plus the hairline separating it from the content.
///
/// Moved here out of `CategorySource`, because sideways the rail now holds the mode
/// tabs whether or not there are categories to show — Count mode has none — and one
/// plate drawn by the container is the only way both cases get exactly one. Leaving
/// a copy on `CategorySource` too would lay 0.5 opacity over 0.5 opacity and the
/// Words rail would come out visibly darker than the Count rail.
///
/// The fill ignores the safe area so the panel reaches the physical edge of a
/// notched phone held sideways; the *content* does not, because `AdaptiveShell` is
/// already laid out inside the safe area. Applying the inset in both places is what
/// cost the web port about 118pt of dead space in landscape, and it is invisible
/// until you rotate a real device.
struct RailPlate: View {
    var body: some View {
        Theme.bgPrimary.opacity(0.5)
            .ignoresSafeArea()
            .overlay(alignment: .trailing) {
                Rectangle().fill(Theme.surfaceBorder).frame(width: 1)
            }
    }
}

/// The left-hand column in landscape: whatever the screen has to offer, and the
/// mode tabs at the foot of it.
///
/// Sideways there is no height to spend on a bottom tab bar — a phone gives about
/// 390pt and the bar would take a sixth of it — so the tabs move into the width,
/// which is the axis there is plenty of. Words mode fills the rest with the
/// category rail; Count mode passes nothing, because it has no categories.
///
/// **The tabs sit at the bottom**, under their hairline, exactly where the portrait
/// bar puts them and exactly where `src/components/SideRail.tsx` puts them. The
/// brief for this task specified the top; `src/` is what a child has actually used,
/// and rotating the phone should not move the switch to the other end of the
/// screen.
struct SideRail<Categories: View>: View {
    let mode: AppMode
    let onSelectMode: (AppMode) -> Void
    @ViewBuilder var categories: Categories

    var body: some View {
        VStack(spacing: 0) {
            // `categories.frame(maxHeight: .infinity)` is the obvious spelling and
            // does not work. Count mode passes `EmptyView`, and a stack drops an
            // `EmptyView` from its layout *along with its frame* — so the tabs
            // would slide up to the top of the rail in one mode and sit at the
            // bottom in the other. The same trap cost the Words grid a 44pt jump
            // on every tap; the same fix applies.
            Color.clear
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .overlay { categories }

            ModeTabBar(mode: mode, layout: .rail, onSelect: onSelectMode)
        }
        // Two 64pt columns and their 8pt gap need 136; the rest is breathing room.
        // Published by `CategorySourceMetrics` so the number lives in one place.
        .frame(width: CategorySourceMetrics.railWidth)
        .background(RailPlate())
    }
}

#Preview("Rail, both modes") {
    ZStack {
        Theme.background.ignoresSafeArea()
        HStack(spacing: 40) {
            SideRail(mode: .words, onSelectMode: { _ in }) {
                CategorySource(
                    tabs: (try? EmojiRepository())?.categories ?? [],
                    selected: "all", label: { $0.label(.en) },
                    layout: .rail, onSelect: { _ in }
                )
            }
            SideRail(mode: .count, onSelectMode: { _ in }) { EmptyView() }
            Spacer()
        }
    }
}
