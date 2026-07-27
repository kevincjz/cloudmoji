import SwiftUI
import CloudmojiCore

/// The grid's own spacing, from `docs/design/DESIGN_SYSTEM.md`. Everything to
/// do with the tiles themselves lives in ``EmojiTileMetrics``.
enum EmojiGridMetrics {
    /// `padding: 2px 10px 24px` on the web wrapper.
    static let horizontalPadding: CGFloat = 10
    static let topPadding: CGFloat = 2
    /// Clears the tab bar so the last row is never half-hidden behind it.
    static let bottomPadding: CGFloat = 24
}

/// The emoji grid — the visual bulk of the app, and the only thing on screen a
/// child is meant to touch.
///
/// Columns are adaptive at a ``EmojiTileMetrics/side`` minimum, which is the
/// direct equivalent of the web's `repeat(auto-fill, minmax(72px, 1fr))`: the
/// same grid reflows from a 375pt iPhone SE (4 columns) to a 1024pt iPad (12)
/// with no breakpoint and no second set of numbers. Taking the minimum from
/// ``EmojiTileMetrics`` rather than repeating `72` is what guarantees a column
/// is never narrower than the tile inside it.
struct EmojiGrid: View {
    let entries: [EmojiEntry]
    var bouncingID: String?
    let onTap: (EmojiEntry) -> Void

    private let columns = [
        GridItem(.adaptive(minimum: EmojiTileMetrics.side), spacing: EmojiTileMetrics.spacing)
    ]

    var body: some View {
        ScrollView {
            LazyVGrid(columns: columns, spacing: EmojiTileMetrics.spacing) {
                // `EmojiEntry.id` is "emoji|category", not the emoji alone: the
                // same glyph may sit in two categories, and duplicate ids make
                // SwiftUI drop rows.
                ForEach(entries) { entry in
                    EmojiTile(
                        entry: entry,
                        isBouncing: bouncingID == entry.id,
                        onTap: { onTap(entry) }
                    )
                }
            }
            .padding(.horizontal, EmojiGridMetrics.horizontalPadding)
            .padding(.top, EmojiGridMetrics.topPadding)
            .padding(.bottom, EmojiGridMetrics.bottomPadding)
        }
        .accessibilityIdentifier("emoji-grid")
    }
}

#Preview {
    let entries = Array((try? EmojiRepository())?.emojis.prefix(24) ?? [])
    return ZStack {
        Theme.background.ignoresSafeArea()
        EmojiGrid(entries: entries, bouncingID: entries.first?.id) { _ in }
    }
}
