import SwiftUI

/// Which edge a hint marks, and therefore which way its chevron points.
enum ScrollHintSide {
    case leading, trailing, top, bottom

    /// The chevron always points *at the hidden content*.
    var symbol: String {
        switch self {
        case .leading: "chevron.left"
        case .trailing: "chevron.right"
        case .top: "chevron.up"
        case .bottom: "chevron.down"
        }
    }

    var isHorizontal: Bool { self == .leading || self == .trailing }
}

/// Every number the hint is drawn from.
///
/// Deliberately a shade heavier than `src/components/ScrollHint.tsx` (26pt badge,
/// 13pt chevron, 0.25 fill, 0.6 border). The web's first attempt at this
/// affordance was a gradient fade, and the verdict on it was "the gradient is not
/// visible" — a near-black fade over a near-black background reads as nothing.
/// The lesson generalises: err loud. An invisible hint costs pixels and teaches
/// nothing.
enum ScrollHintMetrics {
    /// The band at the edge the badge is centred in — so the badge sits 3pt
    /// clear of the very edge rather than half off it.
    static let band: CGFloat = 34
    static let badge: CGFloat = 28
    static let chevron: CGFloat = 15

    /// Same 1.5pt the active category chip uses. This border is the brightest
    /// part of the badge and is doing the same job: saying "teal, on purpose".
    static let borderWidth: CGFloat = 1.5

    /// Stronger than `--surface-active` / `--border-active` (0.20 / 0.40), which
    /// are tuned for a 64pt chip. This shape is 28pt and has to carry across a
    /// room at a glance.
    static let fillOpacity: Double = 0.28
    static let borderOpacity: Double = 0.70

    /// The badge floats over whatever chip is clipped underneath it, so it needs
    /// a body of its own or the chip reads straight through it.
    static let baseOpacity: Double = 0.90
}

/// Marks a scroll edge that has more content behind it.
///
/// **A shape, not a fade.** The web shipped a gradient scrim first and it was
/// invisible; the teal chevron badge is what replaced it and what is ported here.
/// The scrim is deliberately *not* ported: without it the chip underneath clips
/// mid-glyph, which is itself a cue, and a 92%-opaque band over the trailing
/// third of a chip hides content a child is reaching for.
///
/// Decoration, never a control. A toddler aiming at the chip underneath must hit
/// the chip, and VoiceOver has no business announcing a picture of a chevron.
struct ScrollHint: View {
    let side: ScrollHintSide
    let visible: Bool

    var body: some View {
        Image(systemName: side.symbol)
            // A symbol image is text and takes the accent colour unless it is
            // told otherwise — the same trap the typing row's ✕ fell into.
            .foregroundStyle(Theme.teal)
            .font(.system(size: ScrollHintMetrics.chevron, weight: .bold))
            .frame(width: ScrollHintMetrics.badge, height: ScrollHintMetrics.badge)
            .background {
                Circle().fill(Theme.bgPrimary.opacity(ScrollHintMetrics.baseOpacity))
                Circle().fill(Theme.teal.opacity(ScrollHintMetrics.fillOpacity))
            }
            .overlay {
                Circle().stroke(
                    Theme.teal.opacity(ScrollHintMetrics.borderOpacity),
                    lineWidth: ScrollHintMetrics.borderWidth
                )
            }
            // `.shadow` applies to each drawn primitive separately, so without
            // this the two circles, the stroke and the glyph each cast their own
            // and the badge comes out with a smudged halo.
            .compositingGroup()
            .shadow(color: .black.opacity(0.5), radius: 5, y: 2)
            .frame(
                width: side.isHorizontal ? ScrollHintMetrics.band : nil,
                height: side.isHorizontal ? nil : ScrollHintMetrics.band
            )
            // Opacity, and no implicit animation on it. The web fades over 0.2s;
            // here the hint is captured by bitmap tests that would otherwise
            // photograph it mid-fade at a different phase in each capture — and
            // this project's chronic failure mode is a test that cannot fail.
            // At the extremes of a scroll the difference is imperceptible.
            .opacity(visible ? 1 : 0)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}

/// Whether a scroll view has content hidden past either of its edges.
///
/// Pure arithmetic, kept out of the view so the *when* can be asserted without
/// rendering anything: a strip that fits shows nothing, a strip scrolled to the
/// end stops pointing forward.
struct ScrollEdges: Equatable {
    /// How far the content has been scrolled, in points. Never negative in
    /// theory; rubber-banding makes it so in practice, which ``atStart`` absorbs.
    var offset: CGFloat = 0
    /// The visible length of the scroll view along its axis.
    var viewport: CGFloat = 0
    /// The full length of its content along the same axis.
    var content: CGFloat = 0

    /// A point of slack at both ends. Chip widths follow their labels and land on
    /// fractional points, so an exact comparison leaves the hint flickering on
    /// and off while the content sits still.
    static let slack: CGFloat = 1

    /// False when everything fits — and therefore when no affordance should be
    /// drawn at all. Also false before the first measurement arrives, when every
    /// length is zero, which is what keeps the hint from flashing on launch.
    var overflows: Bool { content > viewport + Self.slack }

    var atStart: Bool { offset <= Self.slack }
    var atEnd: Bool { offset + viewport >= content - Self.slack }

    /// Hidden content *behind* the current position — up, or to the leading side.
    var showsStart: Bool { overflows && !atStart }
    /// Hidden content *ahead* of it — down, or to the trailing side.
    var showsEnd: Bool { overflows && !atEnd }
}

/// A scroll view that marks the edges it is hiding content behind.
///
/// Scroll indicators are off everywhere in this app and iOS hides them at rest
/// regardless, so without this there is nothing telling a parent that the strip
/// clipping `どうぶ…` at the right edge has five more categories behind it.
///
/// The measurement is deliberately **self-contained**: the two geometry probes
/// read their own frames and write to this view's own state. The web hook this
/// ports took a callback that changed identity every render, which tore down its
/// `ResizeObserver` before the first callback could fire — so the hint never
/// appeared at all, and the tests, which asserted opacity rather than pixels,
/// passed anyway.
struct HintedScrollView<Content: View>: View {
    let axis: Axis
    /// Applied to the `ScrollView` itself, not to the composed view: the UI tests
    /// query `app.scrollViews["category-bar"]`, and an identifier attached
    /// outside the overlays has no scroll view to land on.
    var identifier: String = ""
    @ViewBuilder var content: Content

    @State private var offset: CGFloat = 0
    @State private var viewport: CGFloat = 0
    @State private var contentLength: CGFloat = 0

    /// Resolved to the nearest ancestor that declares it, so one constant name
    /// is safe even with a hinted scroll view inside another.
    private static var space: String { "cloudmoji.scroll-hint" }

    private var edges: ScrollEdges {
        ScrollEdges(offset: offset, viewport: viewport, content: contentLength)
    }

    private var startSide: ScrollHintSide { axis == .horizontal ? .leading : .top }
    private var endSide: ScrollHintSide { axis == .horizontal ? .trailing : .bottom }
    private var startAlignment: Alignment { axis == .horizontal ? .leading : .top }
    private var endAlignment: Alignment { axis == .horizontal ? .trailing : .bottom }

    var body: some View {
        ScrollView(axis == .horizontal ? .horizontal : .vertical, showsIndicators: false) {
            content.background { contentProbe }
        }
        .coordinateSpace(name: Self.space)
        .background { viewportProbe }
        .accessibilityIdentifier(identifier)
        .overlay(alignment: startAlignment) {
            ScrollHint(side: startSide, visible: edges.showsStart)
        }
        .overlay(alignment: endAlignment) {
            ScrollHint(side: endSide, visible: edges.showsEnd)
        }
    }

    /// Rides along with the content, so its frame in the scroll view's own
    /// coordinate space gives both the content's length and how far it has slid.
    private var contentProbe: some View {
        GeometryReader { proxy in
            let frame = proxy.frame(in: .named(Self.space))
            // `initial: true` is what delivers the first reading. Doing it in the
            // view body instead would be a state write during an update pass.
            Color.clear.onChange(of: frame, initial: true) { _, new in
                offset = axis == .horizontal ? -new.minX : -new.minY
                contentLength = axis == .horizontal ? new.width : new.height
            }
        }
    }

    /// A background takes the scroll view's own frame, so this measures the
    /// viewport without a `GeometryReader` wrapping — and greedily resizing —
    /// the thing being measured.
    private var viewportProbe: some View {
        GeometryReader { proxy in
            let size = proxy.size
            Color.clear.onChange(of: size, initial: true) { _, new in
                viewport = axis == .horizontal ? new.width : new.height
            }
        }
    }
}

#Preview("Hints, both axes") {
    ZStack {
        Theme.background.ignoresSafeArea()
        VStack(spacing: 32) {
            HintedScrollView(axis: .horizontal) {
                HStack(spacing: 8) {
                    ForEach(0..<12, id: \.self) { index in
                        Text("Item \(index)")
                            .font(Theme.body(14))
                            .foregroundStyle(Theme.textSecondary)
                            .frame(width: 90, height: 64)
                            .background(Theme.surface, in: RoundedRectangle(cornerRadius: 16))
                    }
                }
                .padding(.horizontal, 12)
            }

            HintedScrollView(axis: .vertical) {
                VStack(spacing: 8) {
                    ForEach(0..<12, id: \.self) { index in
                        Text("Row \(index)")
                            .font(Theme.body(14))
                            .foregroundStyle(Theme.textSecondary)
                            .frame(width: 120, height: 64)
                            .background(Theme.surface, in: RoundedRectangle(cornerRadius: 16))
                    }
                }
            }
            .frame(height: 260)
        }
    }
}
