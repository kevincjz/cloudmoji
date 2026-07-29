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
            // **Vertical only.** It used to ignore every edge, which was right
            // when the only thing outside the safe area was the notch. Sideways
            // the home button now reserves a leading gutter, and a plate that
            // ignored *that* spread the rail from the screen edge across the
            // gutter — the rail stopped reading as a rail and the cloud sat on
            // top of it.
            .ignoresSafeArea(edges: .vertical)
            .overlay(alignment: .trailing) {
                Rectangle().fill(Theme.surfaceBorder).frame(width: 1)
            }
    }
}

/// The left-hand column in landscape: whatever the screen has to offer.
///
/// It used to end in the mode tabs, because sideways there was no height to spend
/// on a bottom bar — a phone gives about 390pt and a 64pt bar took a sixth of it.
/// The launcher retired the tabs, so what is left is the rail's original job:
/// hold the category chips where Words mode has them and be a darker plate down
/// the left edge. Count mode no longer builds one at all, having nothing to put
/// in it.
struct SideRail<Categories: View>: View {
    @ViewBuilder var categories: Categories

    var body: some View {
        // `categories.frame(maxHeight: .infinity)` is the obvious spelling and
        // does not work: a stack drops an `EmptyView` from its layout *along with
        // its frame*, which is the same trap that cost the Words grid a 44pt jump
        // on every tap. A real, invisible view holding the space is what works.
        Color.clear
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .overlay { categories }
            // Two 64pt columns and their 8pt gap need 136; the rest is breathing
            // room. Published by `CategorySourceMetrics` so the number lives in
            // one place.
            .frame(width: CategorySourceMetrics.railWidth)
            .background(RailPlate())
    }
}

#Preview("Rail") {
    ZStack {
        Theme.background.ignoresSafeArea()
        HStack(spacing: 40) {
            SideRail {
                CategorySource(
                    tabs: (try? EmojiRepository())?.categories ?? [],
                    selected: "all", label: { $0.label(.en) },
                    layout: .rail, onSelect: { _ in }
                )
            }
            Spacer()
        }
    }
}
