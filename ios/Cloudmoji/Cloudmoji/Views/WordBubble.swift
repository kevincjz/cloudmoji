import SwiftUI

/// The `wordFloat` keyframes from `src/index.css`, as the four states the
/// animation actually interpolates between.
///
/// Written as a type rather than inline numbers so the curve can be asserted:
/// a bubble whose `entering` opacity is 1 never appears to arrive, and that is
/// invisible to any test that only checks the bubble draws.
enum WordBubblePhase: CaseIterable {
    /// `0% { opacity: 0; translateY(12px) scale(0.7) }`
    case entering
    /// `15% { opacity: 1; translateY(0) scale(1.06) }` — the overshoot.
    case arrived
    /// `25% { scale: 1 }` — settled, and held from here.
    case settled
    /// `100% { opacity: 0; translateY(-10px) scale(0.95) }`
    case leaving

    var opacity: Double {
        switch self {
        case .entering, .leaving: 0
        case .arrived, .settled: 1
        }
    }

    var scale: CGFloat {
        switch self {
        case .entering: 0.7
        case .arrived: 1.06
        case .settled: 1
        case .leaving: 0.95
        }
    }

    var offsetY: CGFloat {
        switch self {
        case .entering: 12
        case .arrived, .settled: 0
        case .leaving: -10
        }
    }
}

/// Every number the bubble is drawn and timed from.
///
/// The durations are the `wordFloat` percentages resolved against its 2.2s: the
/// percentages are kept as the source so a change to ``lifetime`` moves all four
/// legs together instead of leaving the fade-out hanging past the removal.
enum WordBubbleMetrics {
    /// `wordFloat 2.2s`, and the 2200ms timeout in
    /// `src/components/WordsMode.tsx` that removes the bubble. They are the same
    /// number on the web and must stay the same number here — a bubble that
    /// fades slower than its owner removes it disappears with a jump cut.
    static let lifetime: Double = 2.2

    static let arriveAt = 0.15 * lifetime   // 0.330
    static let settleAt = 0.25 * lifetime   // 0.550
    static let fadeFrom = 0.78 * lifetime   // 1.716

    static let enterDuration = arriveAt
    static let settleDuration = settleAt - arriveAt
    static let holdDuration = fadeFrom - settleAt
    static let exitDuration = lifetime - fadeFrom

    /// Design system type scale: word bubble is 18px / 900 / letter-spacing 0.5.
    static let fontSize: CGFloat = 18
    static let tracking: CGFloat = 0.5
    static let emojiSize: CGFloat = 22

    static let cornerRadius: CGFloat = 18
    static let horizontalPadding: CGFloat = 18
    static let verticalPadding: CGFloat = 5
    static let spacing: CGFloat = 6
    static let borderWidth: CGFloat = 1
}

/// The bubble itself, with no animation on it.
///
/// Split out because the animated wrapper starts at opacity 0 and only becomes
/// visible once `.task` has run — which `ImageRenderer` never does. Every pixel
/// assertion about colour and content therefore targets this, and the wrapper is
/// checked separately for actually becoming visible.
struct WordBubbleLabel: View {
    let emoji: String
    let word: String

    var body: some View {
        HStack(spacing: WordBubbleMetrics.spacing) {
            Text(emoji)
                .font(.system(size: WordBubbleMetrics.emojiSize))
            Text(word)
                .font(Theme.body(WordBubbleMetrics.fontSize, .black))
                .tracking(WordBubbleMetrics.tracking)
                .foregroundStyle(Theme.textPrimary)
        }
        .padding(.horizontal, WordBubbleMetrics.horizontalPadding)
        .padding(.vertical, WordBubbleMetrics.verticalPadding)
        .background(
            // `linear-gradient(135deg, rgba(255,107,107,0.2), rgba(78,205,196,0.2))`.
            // Coral into teal — the app's two brand colours meeting. A neutral
            // material here would read as a system alert rather than as the app
            // answering the child.
            LinearGradient(
                colors: [Theme.coral.opacity(0.2), Theme.teal.opacity(0.2)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            ),
            in: shape
        )
        .overlay(shape.stroke(Color.white.opacity(0.1), lineWidth: WordBubbleMetrics.borderWidth))
    }

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: WordBubbleMetrics.cornerRadius, style: .continuous)
    }
}

/// The floating label showing the word being spoken. Rises, holds, and fades
/// over ``WordBubbleMetrics/lifetime``.
///
/// The owner is expected to drop it after the same 2.2s — and to give it
/// `.id(...)` per word, the way the web passes `key={id}`, so a repeat of the
/// same word replays the animation from the top rather than sitting there.
/// `.task(id:)` covers the case where it does not.
struct WordBubble: View {
    let emoji: String
    let word: String

    @State private var phase: WordBubblePhase = .entering

    var body: some View {
        WordBubbleLabel(emoji: emoji, word: word)
            .opacity(phase.opacity)
            .scaleEffect(phase.scale)
            .offset(y: phase.offsetY)
            // It is a report on what just happened, not a control — the child
            // is already being told out loud.
            .allowsHitTesting(false)
            .accessibilityIdentifier("word-bubble")
            .task(id: "\(emoji)\u{1F}\(word)") {
                // Assigned unanimated, so a bubble reused for a new word starts
                // from the bottom again instead of easing out of `leaving`.
                phase = .entering
                withAnimation(.easeOut(duration: WordBubbleMetrics.enterDuration)) {
                    phase = .arrived
                }
                guard await sleep(WordBubbleMetrics.enterDuration) else { return }
                withAnimation(.easeInOut(duration: WordBubbleMetrics.settleDuration)) {
                    phase = .settled
                }
                guard await sleep(
                    WordBubbleMetrics.settleDuration + WordBubbleMetrics.holdDuration
                ) else { return }
                withAnimation(.easeInOut(duration: WordBubbleMetrics.exitDuration)) {
                    phase = .leaving
                }
            }
    }

    /// `false` if the wait was cancelled — the bubble was removed or the word
    /// changed, and the rest of the sequence must not run.
    private func sleep(_ seconds: Double) async -> Bool {
        do {
            try await Task.sleep(for: .seconds(seconds))
            return true
        } catch {
            return false
        }
    }
}

#Preview("Bubble") {
    ZStack {
        Theme.background.ignoresSafeArea()
        VStack(spacing: 24) {
            WordBubbleLabel(emoji: "🍎", word: "apple")
            WordBubbleLabel(emoji: "🐶", word: "いぬ")
            WordBubble(emoji: "🌈", word: "rainbow")
        }
    }
}
