import SwiftUI

/// Every number the readout is drawn from, from `src/components/CountMode.tsx`.
enum CountReadoutMetrics {
    static let dotSide: CGFloat = 10
    static let padDotSide: CGFloat = 14
    static let dotSpacing: CGFloat = 6
    static let padDotSpacing: CGFloat = 8
    /// A counted dot swells slightly, so progress reads at a glance rather than
    /// only by colour — which matters on a phone held at arm's length by a
    /// two-year-old.
    static let dotCountedScale: CGFloat = 1.2
    static let dotBorderWidth: CGFloat = 1.5
    static let dotRowBottomPadding: CGFloat = 6

    /// The running count, in the display face. 64pt upright is deliberately huge:
    /// it is the number being spoken, and it is the only text in the app a
    /// pre-reader is meant to look at.
    static let numeralSize: CGFloat = 64
    static let compactNumeralSize: CGFloat = 34
    static let padNumeralSize: CGFloat = 88
    /// `text-shadow: 0 0 30px rgba(78,205,196,0.5)`. CSS blur radius is roughly
    /// twice SwiftUI's.
    static let numeralGlowRadius: CGFloat = 15

    /// The spoken phrase, under the numeral: "three dogs", "三只狗", "いぬ みっつ".
    static let phraseSize: CGFloat = 18
    static let compactPhraseSize: CGFloat = 13
    static let padPhraseSize: CGFloat = 23
    static let phraseTopPadding: CGFloat = 4

    /// The whole block, reserved whether or not anything has been counted.
    ///
    /// Upright there is height to spend and the numeral is 64pt; sideways there is
    /// not, and the controls at the bottom of the screen are what pays for any
    /// extra taken here.
    static func height(compact: Bool) -> CGFloat {
        compact ? 72 : 112
    }

    static func height(compact: Bool, expandedPad: Bool) -> CGFloat {
        expandedPad ? 154 : height(compact: compact)
    }

    /// What is left for the numeral and phrase once the dot row has had its share.
    ///
    /// `dotSide`, **not** `dotSide * dotCountedScale`: `scaleEffect` is a drawing
    /// transform and does not change the size a view is laid out at, so the dot row
    /// occupies 10pt however swollen a counted dot looks. Subtracting the scaled
    /// figure here would leave the whole readout 2pt short of the height its own
    /// test asserts.
    static func numberBlockHeight(compact: Bool) -> CGFloat {
        height(compact: compact) - dotSide - dotRowBottomPadding
    }

    static func numberBlockHeight(compact: Bool, expandedPad: Bool) -> CGFloat {
        height(compact: compact, expandedPad: expandedPad)
            - (expandedPad ? padDotSide : dotSide)
            - dotRowBottomPadding
    }
}

/// Progress dots, the running count, and the phrase being spoken.
///
/// Takes strings rather than a countable and a language: what to say is
/// `CountingGrammar`'s job and choosing the language is `AppModel`'s, so by the
/// time it reaches here it is text.
struct CountReadout: View {
    /// How many dots. The round's size.
    let target: Int
    /// How many are lit. The running count.
    let progress: Int
    /// The big number. Empty before anything is counted.
    let numeral: String
    /// The spoken phrase. Empty before anything is counted.
    let phrase: String

    @Environment(\.cloudmojiIsCompact) private var isCompact
    @Environment(\.cloudmojiLayout) private var layout

    var body: some View {
        VStack(spacing: 0) {
            dots
                .padding(.bottom, CountReadoutMetrics.dotRowBottomPadding)

            // NOT `if progress > 0 { … }`. A stack elides an `EmptyView` along
            // with any frame on it, so an `if` here reserves nothing and the whole
            // grid below jumps up the screen until the first tap — and back down
            // on every shuffle. A real, invisible view with the block hung off it
            // is what actually holds the space.
            Color.clear
                .frame(
                    height: CountReadoutMetrics.numberBlockHeight(
                        compact: isCompact,
                        expandedPad: layout.isExpandedPad
                    )
                )
                .overlay { numberBlock }
        }
    }

    private var dots: some View {
        HStack(
            spacing: layout.isExpandedPad
                ? CountReadoutMetrics.padDotSpacing
                : CountReadoutMetrics.dotSpacing
        ) {
            ForEach(0..<max(target, 0), id: \.self) { index in
                let isLit = index < progress
                Circle()
                    .fill(isLit ? Theme.teal : Theme.textPrimary.opacity(0.1))
                    .overlay(
                        Circle().stroke(
                            isLit ? Theme.teal.opacity(0.6) : Theme.surfaceBorder,
                            lineWidth: CountReadoutMetrics.dotBorderWidth
                        )
                    )
                    .frame(
                        width: layout.isExpandedPad
                            ? CountReadoutMetrics.padDotSide
                            : CountReadoutMetrics.dotSide,
                        height: layout.isExpandedPad
                            ? CountReadoutMetrics.padDotSide
                            : CountReadoutMetrics.dotSide
                    )
                    .scaleEffect(isLit ? CountReadoutMetrics.dotCountedScale : 1)
                    .animation(.easeOut(duration: 0.3), value: isLit)
            }
        }
        // The dots report progress; the numeral beside them says the same thing in
        // a form VoiceOver can read, so this would only be repetition.
        .accessibilityHidden(true)
    }

    @ViewBuilder private var numberBlock: some View {
        if !numeral.isEmpty {
            VStack(spacing: 0) {
                Text(numeral)
                    .font(
                        Theme.display(
                            layout.isExpandedPad
                                ? CountReadoutMetrics.padNumeralSize
                                : (isCompact
                                   ? CountReadoutMetrics.compactNumeralSize
                                   : CountReadoutMetrics.numeralSize)
                        )
                    )
                    .foregroundStyle(Theme.textPrimary)
                    .shadow(color: Theme.teal.opacity(0.5),
                            radius: CountReadoutMetrics.numeralGlowRadius)
                    .accessibilityIdentifier("count-readout")

                Text(phrase)
                    .font(
                        Theme.body(
                            layout.isExpandedPad
                                ? CountReadoutMetrics.padPhraseSize
                                : (isCompact
                                   ? CountReadoutMetrics.compactPhraseSize
                                   : CountReadoutMetrics.phraseSize),
                            .black
                        )
                    )
                    .foregroundStyle(Theme.textTertiary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                    .padding(.top, CountReadoutMetrics.phraseTopPadding)
                    .accessibilityIdentifier("count-phrase")
            }
            // `countPop 0.3s ease-out`, keyed on the numeral so each new count
            // pops rather than cross-fading into the last one.
            .transition(.scale(scale: 0.7).combined(with: .opacity))
            .id(numeral)
        }
    }
}

#Preview("Readout") {
    ZStack {
        Theme.background.ignoresSafeArea()
        VStack(spacing: 32) {
            CountReadout(target: 6, progress: 0, numeral: "", phrase: "")
            CountReadout(target: 6, progress: 3, numeral: "3", phrase: "three dogs")
            CountReadout(target: 4, progress: 4, numeral: "4", phrase: "よっつ いぬ")
                .environment(\.cloudmojiIsCompact, true)
        }
    }
}
