import SwiftUI
import CloudmojiCore

/// The list's own spacing and its section headers, from
/// `docs/design/DESIGN_SYSTEM.md`. Everything to do with the tiles themselves
/// lives in ``EmojiTileMetrics``.
enum EmojiGridMetrics {
    /// `padding: 2px 10px 24px` on the web wrapper.
    static let horizontalPadding: CGFloat = 10
    static let topPadding: CGFloat = 2
    /// Clears the tab bar so the last row is never half-hidden behind it.
    static let bottomPadding: CGFloat = 24

    /// Between one section's last row of tiles and the next section's header.
    /// Comfortably past the 8pt minimum gap, because the boundary between two
    /// categories is the one place the list should visibly breathe.
    static let sectionSpacing: CGFloat = 10

    /// A **fixed** height, not one that follows the label.
    ///
    /// Two reasons, and the second is the load-bearing one. A header that grows
    /// with a long Malay label would shuffle every row below it; and
    /// `EmojiGridTests` aims a scanline at the first row of tiles by arithmetic,
    /// which is only possible if what sits above that row is a known number.
    static let headerHeight: CGFloat = 34

    /// The icon beside the section name — same size it takes on a labelled
    /// category chip, so the header and the chip that jumps to it read as the
    /// same thing.
    static let headerGlyphSize: CGFloat = 18
    /// `Category label | Nunito | 14px | 800` from the design system's type
    /// scale. The header is that label, in the place the child has arrived at.
    static let headerLabelSize: CGFloat = 14
    static let headerGap: CGFloat = 6
    /// The rule that runs the label out to the edge of the list.
    static let headerRuleHeight: CGFloat = 1

    /// How far past the top edge of the viewport a section header counts as
    /// having been crossed. Absorbs the list's own top padding and the
    /// fractional offsets a fling leaves behind.
    static let sectionCrossed: CGFloat = 8
}

/// A request to scroll the list to a section.
///
/// The token is what makes tapping the same chip twice work: a child who has
/// scrolled away from Animals and taps Animals again must go back there, and an
/// unchanged value would be no change at all as far as `.onChange` is concerned.
struct SectionJump: Equatable {
    let id: String
    let token: Int
}

/// **One long scrollable list of every emoji**, cut into a section per category.
///
/// It used to be a filtered grid: tapping Animals replaced the contents with
/// animals. A 27-month-old tried to scroll past the end of that grid looking for
/// the other categories, most obviously in landscape where three rows fit — his
/// model was that the categories are *places in one list*, and it is better than
/// the one we shipped. So the list is continuous, the headers mark the sections,
/// and the chips scroll rather than filter.
///
/// That deletes a failure state rather than guarding it. When a chip filtered,
/// a parent switching that category off in Settings left the child on a
/// permanently blank grid — `CLAUDE.md` rule 4 — and `WordsView` carried a
/// fallback handler for it. A disabled category simply has no section here; see
/// ``AppModel/sections``.
///
/// Columns stay adaptive at a ``EmojiTileMetrics/side`` minimum, the direct
/// equivalent of the web's `repeat(auto-fill, minmax(72px, 1fr))`: the same
/// list reflows from a 375pt iPhone SE (4 columns) to a 1024pt iPad (12) with no
/// breakpoint. Taking the minimum from ``EmojiTileMetrics`` rather than
/// repeating `72` is what guarantees a column is never narrower than its tile.
struct EmojiGrid: View {
    let sections: [EmojiSection]
    var bouncingID: String?
    /// The word each tile announces to VoiceOver, in the chosen language. The
    /// list has no opinion about language — it forwards whatever the screen
    /// already computed for speech. `nil` leaves the tile on its English
    /// default, so callers written before this parameter existed still compile.
    var word: ((EmojiEntry) -> String)?
    /// What a chip tap asks for. Changing it scrolls the list.
    var jumpTo: SectionJump?
    /// Fires when scrolling moves the list into a different section, so the
    /// chips can follow. A highlight that only followed taps would be a lie the
    /// moment the child scrolled.
    var onActiveSection: ((String) -> Void)?
    /// The name each section header shows, in the chosen language — same source
    /// as the chip's label.
    var label: ((CategoryTab) -> String)?
    let onTap: (EmojiEntry) -> Void

    /// Every realised header's distance below the top of the viewport. Only
    /// realised ones are in here, which is exactly right: a lazy list is not
    /// holding an opinion about a section it has not built.
    @State private var headerOffsets: [String: CGFloat] = [:]
    @State private var viewportHeight: CGFloat = 0
    @State private var active: String?

    private let columns = [
        GridItem(.adaptive(minimum: EmojiTileMetrics.side), spacing: EmojiTileMetrics.spacing)
    ]

    var body: some View {
        ScrollViewReader { proxy in
            // A plain `ScrollView`, deliberately not a ``HintedScrollView``.
            //
            // The chevron affordance exists for the *horizontal* category strip,
            // where a chip is clipped mid-word and nothing says there is more
            // sideways. Scrolling a vertical list of emojis is a gesture a
            // toddler already has — the owner watched his son do it unprompted —
            // and the rows are visibly cut off at the bottom of the screen. A
            // hint here would be decoration over a solved problem.
            ScrollView(.vertical, showsIndicators: false) {
                LazyVStack(spacing: 0) {
                    ForEach(sections) { section in
                        header(section)
                        // `EmojiEntry.id` is "emoji|category", not the emoji
                        // alone: the same glyph may sit in two categories, and
                        // duplicate ids make SwiftUI drop rows.
                        LazyVGrid(columns: columns, spacing: EmojiTileMetrics.spacing) {
                            ForEach(section.entries) { entry in
                                EmojiTile(
                                    entry: entry,
                                    isBouncing: bouncingID == entry.id,
                                    word: word?(entry),
                                    onTap: { onTap(entry) }
                                )
                            }
                        }
                        .padding(.bottom, EmojiGridMetrics.sectionSpacing)
                    }
                    // Reports where the content ends, which is the only way to
                    // know the child has reached the bottom — see
                    // ``recomputeActive``. Zero-height, so it costs no layout.
                    Color.clear
                        .frame(height: 0)
                        .background { offsetProbe(Self.endMarker) }
                }
                .padding(.horizontal, EmojiGridMetrics.horizontalPadding)
                .padding(.top, EmojiGridMetrics.topPadding)
                .padding(.bottom, EmojiGridMetrics.bottomPadding)
            }
            // Declared here because the section probes measure themselves
            // against it — `HintedScrollView` would otherwise have been the only
            // thing declaring it.
            .coordinateSpace(name: CloudmojiScroll.space)
            .accessibilityIdentifier("emoji-grid")
            .background { viewportProbe }
            .onChange(of: jumpTo) { _, new in
                guard let new else { return }
                // A glide rather than a teleport: the thing being taught is that
                // it is the same list, moving. `.top` so the section's header
                // lands under the chip that was just tapped.
                withAnimation(.easeOut(duration: 0.32)) {
                    proxy.scrollTo(new.id, anchor: .top)
                }
            }
        }
    }

    // MARK: - Section headers

    private func header(_ section: EmojiSection) -> some View {
        HStack(spacing: EmojiGridMetrics.headerGap) {
            Text(section.tab.icon)
                .font(.system(size: EmojiGridMetrics.headerGlyphSize))
            Text(label?(section.tab) ?? section.tab.label(.en))
                .font(Theme.body(EmojiGridMetrics.headerLabelSize))
                .foregroundStyle(Theme.teal)
                .lineLimit(1)
            // Runs the name out to the edge, so a section reads as a band across
            // the list rather than as a word floating above some emojis.
            Rectangle()
                .fill(Theme.surfaceBorder)
                .frame(height: EmojiGridMetrics.headerRuleHeight)
        }
        .frame(height: EmojiGridMetrics.headerHeight)
        // The scroll target a chip jumps to.
        .id(section.id)
        .background { offsetProbe(section.id) }
        // One element, not three: VoiceOver announcing "🍎", "Fruits" as two
        // stops in the middle of a list of two hundred tiles is noise.
        .accessibilityElement(children: .combine)
        // A container's identifier propagates down and overwrites its children's
        // — which is how the typing row once ended up with three buttons all
        // called "typing-row". Safe here only because the two `Text`s have no
        // identifiers of their own to lose, and asserted rather than assumed:
        // `WordsModeUITests` looks this up by name.
        .accessibilityIdentifier("section-\(section.id)")
    }

    /// Rides along with a header and reports where it is relative to the
    /// viewport. `.onChange` rather than a `PreferenceKey` so the write stays on
    /// the main actor with no `@Sendable` gymnastics.
    private func offsetProbe(_ id: String) -> some View {
        GeometryReader { proxy in
            let minY = proxy.frame(in: .named(CloudmojiScroll.space)).minY
            Color.clear
                // `initial: true` delivers the first reading. Doing it in the
                // view body instead would be a state write during an update.
                .onChange(of: minY, initial: true) { _, new in
                    headerOffsets[id] = new
                    recomputeActive()
                }
                .onDisappear {
                    headerOffsets[id] = nil
                    recomputeActive()
                }
        }
    }

    /// A background takes the scroll view's own frame, so this measures the
    /// viewport without a `GeometryReader` wrapping — and greedily resizing —
    /// the thing being measured.
    private var viewportProbe: some View {
        GeometryReader { proxy in
            let height = proxy.size.height
            Color.clear.onChange(of: height, initial: true) { _, new in
                viewportHeight = new
                recomputeActive()
            }
        }
    }

    /// Not a section — the sentinel the end-of-content probe writes under.
    /// Prefixed so it can never collide with a category id.
    private static let endMarker = "|end|"

    /// Which section the child is looking at: **the last header to have crossed
    /// the top of the viewport**.
    ///
    /// Deliberately the top edge rather than, say, the middle. A half-viewport
    /// rule reads well on a phone and is wrong on an iPad, where nine columns
    /// fit the whole of Fruits into two rows — the Food header is already past
    /// the middle before anything has been scrolled, so the app would open with
    /// the second chip lit while the child is looking at the first.
    ///
    /// The end of the list is the last section, whatever that rule says. The
    /// final section is short — twelve faces — so on a wide screen its header
    /// can never reach the top, and without this the last chip could be lit
    /// neither by scrolling nor by tapping it.
    ///
    /// Nothing qualifying leaves the answer alone rather than resetting it: that
    /// is a tall section whose header has scrolled out of the lazy list's reach,
    /// and the child is still in it.
    private func recomputeActive() {
        guard viewportHeight > 0 else { return }
        var candidate = headerOffsets
            .filter { $0.key != Self.endMarker && $0.value <= EmojiGridMetrics.sectionCrossed }
            .max { $0.value < $1.value }?
            .key
        if let end = headerOffsets[Self.endMarker], end <= viewportHeight + 1 {
            candidate = sections.last?.id ?? candidate
        }
        guard let candidate, candidate != active else { return }
        active = candidate
        onActiveSection?(candidate)
    }
}

#Preview {
    let repo = (try? EmojiRepository()) ?? .empty
    let tabs = repo.categories.filter { $0.category != nil }
    let sections = tabs.compactMap { tab -> EmojiSection? in
        guard let category = tab.category else { return nil }
        return EmojiSection(tab: tab, entries: repo.emojis.filter { $0.cat == category })
    }
    return ZStack {
        Theme.background.ignoresSafeArea()
        EmojiGrid(sections: sections, bouncingID: sections.first?.entries.first?.id) { _ in }
    }
}
