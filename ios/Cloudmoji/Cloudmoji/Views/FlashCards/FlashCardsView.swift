import SwiftUI
import CloudmojiCore

enum FlashCardMetrics {
    /// Far past the 72pt preferred size. There are only ever three of these on
    /// screen and the child is choosing between them from across a room's worth
    /// of attention span — bigger is unambiguously better here.
    static let choiceSide: CGFloat = 110
    static let compactChoiceSide: CGFloat = 96

    /// `CLAUDE.md` rule 2.
    static let spacing: CGFloat = 12

    static let cornerRadius: CGFloat = 22
    static let glyphSize: CGFloat = 56
    static let compactGlyphSize: CGFloat = 46
    static let borderWidth: CGFloat = 2

    /// Design system Active States: emoji tiles `scale(0.85)`.
    static let pressedScale: CGFloat = 0.85

    /// The replay button, which a child taps to hear the word again.
    static let replaySide: CGFloat = 64
}

/// Flash Cards ⚡ — Cloudmoji says a word, the child finds it.
///
/// The first screen in this app that asks a question, which makes it the first
/// one that could have a wrong answer — and `CLAUDE.md` rule 4 says it must not.
/// So a wrong tap is not wrong: the emoji the child actually touched says **its
/// own** name, bounces, and the question is repeated. He named a thing, out loud,
/// in the language his family chose. That is the same reward Words mode gives,
/// arrived at by a detour.
///
/// Content comes from `EmojiRepository` through `AppModel.emojis(in:)`, already
/// narrowed to the categories the parent left enabled. There is no word list in
/// this file and there must never be one — `src/data/` is the single source.
struct FlashCardsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.cloudmojiIsCompact) private var isCompact
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var round: FlashRound?
    @State private var mood: MascotMood = .happy
    @State private var bouncingID: String?
    @State private var solvedID: String?
    @State private var isAdvancing = false

    @State private var moodTask: Task<Void, Never>?
    @State private var bounceTask: Task<Void, Never>?
    @State private var advanceTask: Task<Void, Never>?
    @State private var speechFallback: Task<Void, Never>?

    /// Star eyes on tap, "for ~600ms" (`CLAUDE.md` rule 8).
    private static let excitedHold = Duration.milliseconds(600)
    /// The pause after a right answer, so the celebration lands before the next
    /// question talks over it.
    private static let advanceDelay = Duration.milliseconds(1400)
    private static let bounceHold = Duration.milliseconds(400)
    /// An escape from a stuck `.speaking` face when `didFinish` never arrives —
    /// a call, Siri, headphones pulled out. Generously past any real utterance.
    private static let speechCeiling = Duration.seconds(8)

    /// Chrome, in the five languages. Copy, not content.
    struct UIText {
        let prompt: [Language: String]
        let replay: [Language: String]
    }

    static let uiText = UIText(
        prompt: [
            .en: "Which one is it?", .zh: "是哪一个?", .ms: "Yang mana satu?",
            .ja: "どれかな?", .tl: "Alin ito?",
        ],
        replay: [
            .en: "Say it again", .zh: "再说一次", .ms: "Sebut lagi",
            .ja: "もういちど", .tl: "Ulitin",
        ]
    )

    private func text(_ table: [Language: String]) -> String {
        table[model.settings.language] ?? table[.en] ?? ""
    }

    private var choiceSide: CGFloat {
        isCompact ? FlashCardMetrics.compactChoiceSide : FlashCardMetrics.choiceSide
    }

    var body: some View {
        Group {
            if isCompact {
                HStack(spacing: 18) {
                    promptCard
                    VStack(spacing: 12) {
                        choices
                        replayButton
                    }
                }
                .padding(.horizontal, 18)
            } else {
                VStack(spacing: 22) {
                    promptCard
                    choices
                    replayButton
                }
                .padding(.horizontal, 12)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .task { if round == nil { nextRound() } }
        // A language change re-asks the same question in the new language rather
        // than throwing the round away: the emoji on screen have not changed, and
        // pulling them out from under a child mid-choice would be the failure.
        .onChange(of: model.settings.language) {
            silence()
            ask()
        }
        .onChange(of: model.settings.muted) {
            if model.settings.muted {
                silence()
            } else {
                ask()
            }
        }
        // A parent narrowing the categories mid-session invalidates the round on
        // screen: it may be asking for something they just switched off.
        .onChange(of: model.settings.enabledCategories) { nextRound() }
        .onDisappear {
            model.speech.cancelAll()
            for task in [moodTask, bounceTask, advanceTask, speechFallback] { task?.cancel() }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("flash-panel")
    }

    // MARK: - Pieces

    /// The spoken prompt is a physical card stack instead of a header followed
    /// by loose labels. A new round feels like a fresh card being dealt.
    private var promptCard: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 30, style: .continuous)
                .fill(Theme.lavender.opacity(0.13))
                .overlay(
                    RoundedRectangle(cornerRadius: 30, style: .continuous)
                        .stroke(Theme.lavender.opacity(0.22), lineWidth: 2)
                )
                .rotationEffect(.degrees(mood == .beaming ? -10 : -6))
                .offset(x: -7, y: 3)

            RoundedRectangle(cornerRadius: 30, style: .continuous)
                .fill(Theme.coral.opacity(0.14))
                .overlay(
                    RoundedRectangle(cornerRadius: 30, style: .continuous)
                        .stroke(Theme.coral.opacity(0.24), lineWidth: 2)
                )
                .rotationEffect(.degrees(mood == .beaming ? 10 : 6))
                .offset(x: 7, y: 3)

            VStack(spacing: isCompact ? 6 : 10) {
                CloudMascot(mood: mood, size: isCompact ? 58 : 78)

                Text(text(Self.uiText.prompt))
                    .font(Theme.body(13, .heavy))
                    .foregroundStyle(Theme.textTertiary)

                Text(round.map { model.word(for: $0.target) } ?? "")
                    .font(Theme.body(isCompact ? 24 : 31, .black))
                    .foregroundStyle(
                        LinearGradient(
                            colors: [Theme.gold, Theme.coral],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                    .padding(.horizontal, 14)
                    .accessibilityIdentifier("flash-word")
            }
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(
                Theme.bgPrimary.opacity(0.88),
                in: RoundedRectangle(cornerRadius: 28, style: .continuous)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .stroke(Theme.gold.opacity(0.34), lineWidth: 2)
            )
        }
        .frame(
            width: isCompact ? 236 : nil,
            height: isCompact ? 224 : 224
        )
        .frame(maxWidth: isCompact ? nil : 310)
        .shadow(color: Theme.coral.opacity(0.16), radius: 20, y: 12)
        .animation(
            reduceMotion ? .easeOut(duration: 0.12) : .spring(response: 0.34, dampingFraction: 0.74),
            value: mood
        )
    }

    @ViewBuilder private var choices: some View {
        if let round {
            HStack(spacing: FlashCardMetrics.spacing) {
                ForEach(Array(round.choices.enumerated()), id: \.element.id) { index, entry in
                    choiceTile(entry, index: index)
                }
            }
        }
    }

    private func choiceTile(_ entry: EmojiEntry, index: Int) -> some View {
        let shape = RoundedRectangle(cornerRadius: FlashCardMetrics.cornerRadius, style: .continuous)
        return Button { tap(entry) } label: {
            ZStack {
                shape.fill(choiceTint(index).opacity(0.20))
                Circle()
                    .fill(choiceTint(index).opacity(0.15))
                    .frame(width: choiceSide * 0.78)
                    .offset(x: choiceSide * 0.24, y: -choiceSide * 0.22)

                Text(entry.emoji)
                    .font(.system(size: isCompact
                                  ? FlashCardMetrics.compactGlyphSize
                                  : FlashCardMetrics.glyphSize))
            }
                .frame(width: choiceSide, height: choiceSide)
                .clipShape(shape)
                .overlay(shape.stroke(choiceTint(index).opacity(0.50), lineWidth: FlashCardMetrics.borderWidth))
                // Without this the target is the glyph's own box and the ring of
                // plate a toddler aims at is dead.
                .contentShape(Rectangle())
        }
        .buttonStyle(PressScale(scale: FlashCardMetrics.pressedScale))
        .disabled(isAdvancing)
        .opacity(isAdvancing && solvedID != entry.id ? 0.34 : 1)
        .scaleEffect(
            solvedID == entry.id
                ? 1.08
                : (bouncingID == entry.id ? EmojiTileMetrics.bounceScale : 1)
        )
        .rotationEffect(.degrees([-3.0, 2.0, -1.5][index % 3]))
        .shadow(color: choiceTint(index).opacity(0.18), radius: 12, y: 7)
        // A tile at 1.3× overlaps its neighbours and the row paints in order, so
        // without this the tile the child just touched is partly covered by the
        // ones after it.
        .zIndex(bouncingID == entry.id || solvedID == entry.id ? 1 : 0)
        .animation(.spring(duration: EmojiTileMetrics.bounceDuration), value: bouncingID)
        .animation(
            reduceMotion ? .easeOut(duration: 0.12) : .spring(response: 0.32, dampingFraction: 0.72),
            value: solvedID
        )
        .accessibilityLabel(model.word(for: entry))
        .accessibilityIdentifier("flash-choice-\(entry.emoji)")
    }

    private func choiceTint(_ index: Int) -> Color {
        [Theme.gold, Theme.lavender, Theme.coral][index % 3]
    }

    private var replayButton: some View {
        let shape = Capsule()
        return Button {
            Haptics.tap()
            ask()
        } label: {
            HStack(spacing: 6) {
                Image(systemName: "speaker.wave.2.fill")
                    .font(.system(size: 17, weight: .bold))
                Text(text(Self.uiText.replay))
                    .font(Theme.body(14, .black))
                    .lineLimit(1)
            }
            .foregroundStyle(Theme.gold)
            .padding(.horizontal, 22)
            .frame(minHeight: FlashCardMetrics.replaySide)
            .background(Theme.gold.opacity(0.13), in: shape)
            .overlay(shape.stroke(Theme.gold.opacity(0.34), lineWidth: 2))
            .contentShape(Rectangle())
        }
        .buttonStyle(PressScale(scale: 0.88))
        .accessibilityLabel(text(Self.uiText.replay))
        .accessibilityIdentifier("flash-replay")
    }

    // MARK: - Behaviour

    private func tap(_ entry: EmojiEntry) {
        // Before anything else, so the buzz lands with the finger.
        Haptics.tap()
        guard let round, !isAdvancing else { return }
        bounce(entry.id)

        if round.isCorrect(entry) {
            isAdvancing = true
            solvedID = entry.id
            Haptics.reward()
            setMood(.beaming)
            speak(model.word(for: entry), thenReturnToHappy: false)
            advanceTask?.cancel()
            advanceTask = afterDelay(Self.advanceDelay) {
                // The one assignment that bypasses `setMood`: a celebration is
                // the only thing allowed to lower its own flag, and `arbitrate`
                // would otherwise refuse to let it end.
                mood = .happy
                isAdvancing = false
                nextRound()
            }
        } else {
            // Not an error — the thing he touched says its own name. Then the
            // question comes back, because it was never withdrawn.
            speak(model.word(for: entry), thenReturnToHappy: true)
            advanceTask?.cancel()
            advanceTask = afterDelay(Self.advanceDelay) { ask() }
        }
    }

    private func nextRound() {
        isAdvancing = false
        solvedID = nil
        silence()
        var generator = SystemRandomNumberGenerator()
        round = FlashRound(
            pool: model.emojis(in: nil),
            language: model.settings.language,
            avoiding: round?.target,
            using: &generator
        )
        ask()
    }

    /// Says the word the round is asking for.
    private func ask() {
        guard let round else { return }
        speak(model.word(for: round.target), thenReturnToHappy: true)
    }

    private func speak(_ word: String, thenReturnToHappy: Bool) {
        moodTask?.cancel()
        if thenReturnToHappy { setMood(.excited) }

        guard !model.settings.muted else {
            // Nothing will report finishing, so the excited face needs its own
            // way home or the mascot keeps star eyes for the rest of the day.
            if thenReturnToHappy {
                moodTask = afterDelay(Self.excitedHold) { setMood(.happy) }
            }
            return
        }

        if thenReturnToHappy {
            moodTask = afterDelay(Self.excitedHold) { setMood(.speaking) }
        }

        speechFallback?.cancel()
        speechFallback = afterDelay(Self.speechCeiling) { setMood(.happy) }

        model.speech.speak(word, in: model.settings.language) {
            moodTask?.cancel()
            speechFallback?.cancel()
            // `arbitrate` refuses to lower a beaming face, so a correct answer's
            // celebration survives its own word finishing. That is rule 11, and
            // it is why this can be unconditional.
            setMood(.happy)
        }
    }

    private func bounce(_ id: String) {
        bounceTask?.cancel()
        bouncingID = id
        bounceTask = afterDelay(Self.bounceHold) { bouncingID = nil }
    }

    private func setMood(_ requested: MascotMood) {
        mood = MascotMood.arbitrate(current: mood, requested: requested)
    }

    private func silence() {
        advanceTask?.cancel()
        speechFallback?.cancel()
        moodTask?.cancel()
        model.speech.cancelAll()
        isAdvancing = false
        solvedID = nil
        // Directly, not through `setMood`: a cloud still beaming over a round
        // that was thrown away is the bug, not the protection. `CountView.silence`
        // makes the same call for the same reason.
        mood = .happy
    }
}

#Preview("Flash Cards") {
    AdaptiveShell { FlashCardsView() }
        .environment(AppModel())
}
