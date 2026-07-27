import SwiftUI
import CloudmojiCore

/// One emoji the child has typed, and the word that was spoken for it.
///
/// The identity is a fresh `UUID` rather than the glyph: the same emoji can be
/// tapped twice in a row, and `ForEach` drops rows that share an id — the second
/// 🍎 would simply never appear. The synthesised `Equatable` therefore compares
/// ids too, which is what makes `.animation(_:value: typed)` fire on a repeat.
struct TypedEmoji: Identifiable, Equatable {
    let id = UUID()
    let emoji: String
    let word: String
}

/// Every number the typing row is drawn from.
///
/// Sizes and spacing come from `src/components/TypingRow.tsx`; colours and the
/// press scale from `docs/design/DESIGN_SYSTEM.md`. The one place this row
/// deliberately departs from the shipped web is spacing: the web puts 3–4px
/// between typed emojis and between the control buttons, which is under the 8px
/// floor `CLAUDE.md` sets for two things a child taps. The floor wins.
enum TypingRowMetrics {
    /// The floor for anything a *child* taps. The typing row is not the emoji
    /// grid, so 72 is not required here — but 64 is, and the row scrolls rather
    /// than shrinking these to fit. Do not trade this away for a tidier layout.
    static let typedSide: CGFloat = 64
    static let controlSide: CGFloat = 64

    /// Minimum gap between two child-facing targets. Applies both between typed
    /// emojis and between the three controls.
    static let spacing: CGFloat = 8

    /// `font-size: 32` on the web. Comfortably inside the 64pt box.
    static let typedGlyphSize: CGFloat = 32

    /// Per-control glyph sizes from the web. They differ because the glyphs do:
    /// 🔊 is a colour emoji and reads small, ✕ is a text symbol and reads large.
    static let replayGlyphSize: CGFloat = 24
    static let deleteGlyphSize: CGFloat = 22
    static let clearGlyphSize: CGFloat = 20

    /// `12px — control buttons` and `20px — typing row container` from the
    /// design system's radius list.
    static let controlCornerRadius: CGFloat = 12
    static let containerCornerRadius: CGFloat = 20

    static let horizontalPadding: CGFloat = 10
    static let verticalPadding: CGFloat = 7

    /// Tall enough that a 64pt target is never clipped by its own container.
    /// Derived rather than written down: a hand-written 72 here would quietly
    /// squeeze the targets the moment the padding changed.
    static var minHeight: CGFloat { typedSide + verticalPadding * 2 }

    /// The web draws 1.5px. At 6% white on a near-black background the
    /// difference is a hairline either way; matches ``EmojiTileMetrics``.
    static let borderWidth: CGFloat = 1

    /// Design system Active States: control buttons `scale(0.88)`. The typed
    /// emoji is a bare glyph with no plate behind it, so it takes the web's
    /// `active:scale-90` — a bigger squeeze would look like the emoji fell over.
    static let controlPressedScale: CGFloat = 0.88
    static let typedPressedScale: CGFloat = 0.9

    /// `popIn 0.3s ease-out` — a new emoji arriving in the row.
    static let popInDuration: Double = 0.3

    /// Design system type scale: placeholder text is 13px / 800.
    static let placeholderSize: CGFloat = 13
}

/// The strip of emojis the child has tapped, with replay, delete and clear.
///
/// Two behaviours here are easy to lose and both are load-bearing. The row
/// **scrolls** instead of shrinking: when the typed emojis no longer fit, the
/// targets stay 64pt and slide out of view, because a 40pt emoji a toddler
/// cannot hit is worse than an emoji they have to scroll to. And it scrolls to
/// the **newest** emoji on every tap, so what the child just typed is the thing
/// they can see; without that the row silently stops responding after five taps.
struct TypingRow: View {
    /// PRD: at most 50 emojis in the row, oldest dropped first. Enforced by
    /// whoever owns the array — this is the number they enforce it against, and
    /// it matches `MAX_TYPED` in `src/components/WordsMode.tsx`.
    static let maxTyped = 50

    let typed: [TypedEmoji]
    let muted: Bool
    /// Only the placeholder is localised; everything else in the row is a glyph.
    /// Defaults so the interface the plan specifies still compiles unchanged.
    var language: Language = .en
    let onReplay: () -> Void
    let onDelete: () -> Void
    let onClear: () -> Void
    let onTapTyped: (TypedEmoji) -> Void

    /// Chrome, not content — this string lives in `src/components/TypingRow.tsx`
    /// alongside the markup, not in `src/data/`, so there is nothing to generate
    /// it from. Keep the two in sync by hand when either changes.
    private static let englishPlaceholder = "Tap emojis below! 👇"

    private static let placeholders: [Language: String] = [
        .en: englishPlaceholder,
        .zh: "点击下面的表情 👇",
        .ms: "Ketik emoji di bawah! 👇",
        .ja: "したの えもじを タップしてね 👇",
        .tl: "Pindutin ang emoji sa ibaba! 👇",
    ]

    /// Named separately from the dictionary so the fallback is not a force
    /// unwrap. A missing row is a content bug, not a reason for a child to see
    /// a crash — the same call this app makes everywhere else.
    static func placeholder(_ language: Language) -> String {
        placeholders[language] ?? englishPlaceholder
    }

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: TypingRowMetrics.containerCornerRadius, style: .continuous)
    }

    var body: some View {
        HStack(spacing: TypingRowMetrics.spacing) {
            strip

            if !typed.isEmpty {
                controls
            }
        }
        .padding(.horizontal, TypingRowMetrics.horizontalPadding)
        .padding(.vertical, TypingRowMetrics.verticalPadding)
        .frame(minHeight: TypingRowMetrics.minHeight)
        .background(Theme.surface, in: shape)
        .overlay(shape.stroke(Theme.surfaceBorder, lineWidth: TypingRowMetrics.borderWidth))
        // `.contain` first, then the identifier. Without it this row is not an
        // accessibility element of its own, so the identifier propagates down to
        // the *nearest* element on each branch and overwrites whatever it finds:
        // the three controls came out of the tree as three buttons all called
        // "typing-row", and `replay-btn`, `delete-btn` and `clear-btn` did not
        // exist at all. Not visible to any unit test — SwiftUI builds no
        // accessibility tree outside XCUITest — and caught by
        // `WordsModeUITests.testTypingRowControlsKeepTheirOwnIdentifiers`.
        // Marking the row a container gives the identifier somewhere of its own
        // to land and leaves the children's alone.
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("typing-row")
    }

    // MARK: Typed emojis

    private var strip: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: TypingRowMetrics.spacing) {
                    if typed.isEmpty {
                        Text(Self.placeholder(language))
                            .font(Theme.body(TypingRowMetrics.placeholderSize))
                            .foregroundStyle(Theme.textMuted)
                            .padding(.horizontal, 4)
                            // The row is short and the placeholder is a full
                            // sentence; without this it wraps to two lines in
                            // Malay and Tagalog and the row grows a second row
                            // of height it does not have.
                            .fixedSize()
                    } else {
                        ForEach(typed) { item in
                            TypedEmojiButton(item: item) { onTapTyped(item) }
                                // `popIn`, asymmetric on purpose: an emoji
                                // arriving is a reward and should be seen, an
                                // emoji being deleted should just be gone.
                                .transition(
                                    .asymmetric(
                                        insertion: .scale(scale: 0.2).combined(with: .opacity),
                                        removal: .identity
                                    )
                                )
                        }
                    }
                }
                .animation(.spring(duration: TypingRowMetrics.popInDuration), value: typed)
            }
            // A ScrollView is already greedy along its scroll axis, so this is
            // belt and braces — but it is the thing that keeps the controls
            // pinned to the trailing edge, and it should be said rather than
            // inherited.
            .frame(maxWidth: .infinity, alignment: .leading)
            // Both of these show the *newest* emoji rather than the oldest.
            // Without them the strip freezes on the first thing the child ever
            // tapped: the app still speaks and still bounces, but the row looks
            // like it stopped listening somewhere around the fifth tap.
            //
            // `.defaultScrollAnchor(.trailing)` is the obvious one-liner and is
            // wrong — it also right-aligns content *narrower* than the strip, so
            // three typed emojis huddle against the controls instead of
            // starting where the child's eye is.
            .task {
                guard let last = typed.last else { return }
                proxy.scrollTo(last.id, anchor: .trailing)
            }
            .onChange(of: typed.count) {
                guard let last = typed.last else { return }
                withAnimation(.easeOut(duration: 0.25)) {
                    proxy.scrollTo(last.id, anchor: .trailing)
                }
            }
        }
    }

    // MARK: Controls

    private var controls: some View {
        HStack(spacing: TypingRowMetrics.spacing) {
            // Nothing to replay when the app is muted, and a button that does
            // nothing is a failure state — the one thing this app must not have.
            if !muted {
                TypingRowControl(
                    glyph: "🔊",
                    label: "Replay",
                    identifier: "replay-btn",
                    tint: Theme.teal,
                    glyphSize: TypingRowMetrics.replayGlyphSize,
                    action: onReplay
                )
            }
            TypingRowControl(
                glyph: "⌫",
                label: "Delete last",
                identifier: "delete-btn",
                tint: Theme.amber,
                glyphSize: TypingRowMetrics.deleteGlyphSize,
                action: onDelete
            )
            TypingRowControl(
                glyph: "✕",
                label: "Clear all",
                identifier: "clear-btn",
                tint: Theme.coral,
                glyphSize: TypingRowMetrics.clearGlyphSize,
                action: onClear
            )
        }
    }
}

/// One typed emoji. Tapping it speaks the word again.
///
/// Its own type so the 64pt rule can be measured off a real render rather than
/// read back out of a constant — see `TypingRowTests`.
struct TypedEmojiButton: View {
    let item: TypedEmoji
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            Text(item.emoji)
                .font(.system(size: TypingRowMetrics.typedGlyphSize))
                .frame(
                    minWidth: TypingRowMetrics.typedSide,
                    minHeight: TypingRowMetrics.typedSide
                )
                // There is no plate behind this glyph, so without an explicit
                // hit shape the tappable area is the 32pt glyph box and half
                // the target a child aims at is dead.
                .contentShape(Rectangle())
        }
        .buttonStyle(PressScale(scale: TypingRowMetrics.typedPressedScale))
        .accessibilityLabel(item.word)
        .accessibilityIdentifier("typed-emoji")
    }
}

/// Replay, delete or clear. Child-facing, so 64pt — not the 44pt HIG minimum,
/// which governs the parent-only chrome in the header.
struct TypingRowControl: View {
    let glyph: String
    /// English, like `EmojiTile`'s: VoiceOver here is for a parent.
    let label: String
    let identifier: String
    let tint: Color
    var glyphSize: CGFloat = 22
    let action: () -> Void

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: TypingRowMetrics.controlCornerRadius, style: .continuous)
    }

    var body: some View {
        Button(action: action) {
            Text(glyph)
                // ⌫ and ✕ are text, not colour emoji, so they take the button's
                // tint — which is the system accent blue unless it is said
                // otherwise. The web draws them white.
                .foregroundStyle(Theme.textPrimary)
                .font(.system(size: glyphSize))
                .frame(width: TypingRowMetrics.controlSide, height: TypingRowMetrics.controlSide)
                .background(tint.opacity(0.2), in: shape)
                .overlay(shape.stroke(tint.opacity(0.3), lineWidth: TypingRowMetrics.borderWidth))
                .contentShape(Rectangle())
        }
        .buttonStyle(PressScale(scale: TypingRowMetrics.controlPressedScale))
        .accessibilityLabel(label)
        .accessibilityIdentifier(identifier)
    }
}

#Preview("Typed, empty and muted") {
    let words = [("🍎", "apple"), ("🐶", "dog"), ("🚗", "car"), ("🌈", "rainbow")]
    let typed = words.map { TypedEmoji(emoji: $0.0, word: $0.1) }
    return ZStack {
        Theme.background.ignoresSafeArea()
        VStack(spacing: 12) {
            TypingRow(typed: typed, muted: false, onReplay: {}, onDelete: {}, onClear: {}) { _ in }
            TypingRow(typed: typed, muted: true, onReplay: {}, onDelete: {}, onClear: {}) { _ in }
            TypingRow(typed: [], muted: false, onReplay: {}, onDelete: {}, onClear: {}) { _ in }
            TypingRow(
                typed: [], muted: false, language: .ja,
                onReplay: {}, onDelete: {}, onClear: {}
            ) { _ in }
        }
        .padding(.horizontal, 12)
    }
}
