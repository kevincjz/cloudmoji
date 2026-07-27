import SwiftUI
import CloudmojiCore

/// Words mode — the whole app, as far as a two-year-old is concerned.
///
/// Ported from `src/components/WordsMode.tsx`. One tap does four things at once:
/// the word is spoken, it floats up in a bubble, the tile bounces, and the emoji
/// joins the typing row. Nothing here can fail: a missing voice, a muted phone
/// and an empty category all still produce a bubble and a bounce, because rule 4
/// in `CLAUDE.md` is that there are no failure states.
///
/// Portrait and landscape share every piece and differ only in arrangement — the
/// web shipped two copies of this list and three edits landed on the dead one.
struct WordsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.cloudmojiIsCompact) private var isCompact

    @State private var category: String = "all"
    @State private var typed: [TypedEmoji] = []
    @State private var bubble: TypedEmoji?
    @State private var bouncingID: String?
    @State private var mood: MascotMood = .happy
    @State private var tapCount = 0

    /// Every delayed effect is held so the *next* tap can cancel it. Fire and
    /// forget looks equivalent and is not: a stale 400ms timer clears the bounce
    /// the child is currently looking at, and a stale 600ms timer opens the
    /// mascot's mouth for a word that is no longer being said.
    @State private var bubbleTask: Task<Void, Never>?
    @State private var bounceTask: Task<Void, Never>?
    @State private var moodTask: Task<Void, Never>?
    @State private var celebrationTask: Task<Void, Never>?
    /// Safety net for a `didFinish` that never arrives — see `speak`.
    @State private var speechFallback: Task<Void, Never>?

    // MARK: - Rules
    //
    // Static and pure, so they can be tested. The behaviour below is timing and
    // SwiftUI state, which a unit test cannot drive; these three are the parts
    // that can actually be got wrong in a way no screenshot shows.

    /// Tap counts that earn a celebration. `CLAUDE.md` rule 10, and the same
    /// list as `src/components/WordsMode.tsx`.
    static let milestones: Set<Int> = [10, 25, 50, 100]

    /// Appends one emoji to the typing row, enforcing the PRD's cap.
    ///
    /// `TypingRow.maxTyped` was published by Task 7 and enforced nowhere; the
    /// row does not own the array, this screen does. A toddler mashing tiles is
    /// exactly the input that finds an unbounded array.
    ///
    /// **Oldest dropped first** — `suffix`, not `prefix`. Dropping the newest
    /// keeps the count at 50 and is the opposite of what is wanted: the row
    /// would freeze on the first fifty emojis of the session and never show
    /// another one.
    static func capped(_ typed: [TypedEmoji], appending item: TypedEmoji) -> [TypedEmoji] {
        Array((typed + [item]).suffix(TypingRow.maxTyped))
    }

    /// The web's `safeMood`: a milestone celebration outranks everything.
    ///
    /// `CLAUDE.md` rule 11. Without it the tap that *earns* the milestone — and
    /// the speech finishing right after it — pull the beaming face straight back
    /// off again, and the reward the whole counter exists for is a flicker.
    /// Every mood change on this screen goes through here; the only assignment
    /// that does not is the celebration ending its own three seconds.
    static func arbitrate(current: MascotMood, requested: MascotMood) -> MascotMood {
        if current == .beaming && requested != .beaming { return current }
        return requested
    }

    // MARK: - Timings
    //
    // From `src/components/WordsMode.tsx` and `CLAUDE.md`.

    /// Star eyes on tap, "for ~600ms" (rule 8). After that the mascot moves to
    /// the speaking face if the word is still being said.
    /// Longest a single word may hold the mouth open. Generously past any real
    /// utterance at rate 0.85 — this is an escape from a stuck state, not a
    /// timing mechanism, so it must never fire while a word is still being said.
    static let speechCeiling = Duration.seconds(8)

    private static let excitedHold = Duration.milliseconds(600)
    /// `setTimeout(() => setBounceIdx(null), 400)`, and the spring the tile
    /// animates the return leg with.
    private static let bounceHold = Duration.milliseconds(400)
    /// The bubble's own `wordFloat` lifetime; the owner drops it on the same
    /// beat, or the fade finishes into a jump cut.
    private static let bubbleHold = Duration.seconds(WordBubbleMetrics.lifetime)
    /// The celebration's own two legs: half a second of anticipation after the
    /// milestone tap, then three seconds of beaming (rule 10).
    private static let celebrationDelay = Duration.milliseconds(500)
    private static let celebrationHold = Duration.seconds(3)

    private var entries: [EmojiEntry] {
        model.emojis(in: Category(rawValue: category))
    }

    var body: some View {
        Group {
            if isCompact { landscape } else { portrait }
        }
        // A language or mute change must silence what is already queued — the
        // same `useEffect(cancelAll, [muted, lang])` the web has. Without it the
        // phone finishes the previous language's word after the switch.
        .onChange(of: model.settings.language) {
            silence()
            // `TypedEmoji` froze its word at tap time, so without this the row
            // keeps speaking whatever language it was typed in: tap 🍎 in
            // English, switch to 中文, tap the apple already in the row, and a
            // zh-CN voice is handed the string "apple" and reads it with Chinese
            // phonetics. Replay does it to the whole row, and the VoiceOver
            // label goes stale too. `WordsMode.tsx` avoids this by looking the
            // entry up again at speak time; re-reading the row on the change is
            // the same result and keeps the child's emojis on screen.
            typed = typed.map { item in
                guard let fresh = model.word(forEmoji: item.emoji) else { return item }
                return TypedEmoji(emoji: item.emoji, word: fresh)
            }
        }
        .onChange(of: model.settings.muted) { silence() }
        .onDisappear {
            model.speech.cancelAll()
            for task in [bubbleTask, bounceTask, moodTask, celebrationTask, speechFallback] { task?.cancel() }
        }
    }

    // MARK: - Layouts

    private var portrait: some View {
        VStack(spacing: 6) {
            header
            typingRow
            bubbleRow
            CategorySource(
                tabs: model.categories, selected: category,
                label: model.label, layout: .horizontal, onSelect: select
            )
            grid
        }
    }

    private var landscape: some View {
        HStack(spacing: 0) {
            CategorySource(
                tabs: model.categories, selected: category,
                label: model.label, layout: .rail, onSelect: select
            )
            // The rail publishes its own width so this screen does not have to
            // repeat the number and drift from it.
            .frame(width: CategorySourceMetrics.railWidth)

            VStack(spacing: 4) {
                header
                typingRow
                grid
            }
            // Sideways there is no height to spend on a reserved row, so the
            // bubble floats over the grid instead of pushing it down.
            .overlay(alignment: .bottom) { bubbleSlot.padding(.bottom, 6) }
        }
    }

    // MARK: - Pieces

    private var header: some View {
        HStack(spacing: 8) {
            CloudMascot(mood: mood, size: isCompact ? 42 : 64)
            VStack(alignment: .leading, spacing: 1) {
                Text("Cloudmoji")
                    .font(Theme.display(isCompact ? 17 : 21))
                    .foregroundStyle(Theme.teal)
                if !isCompact {
                    Text("Tap. Listen. Learn!")
                        .font(Theme.body(10, .heavy))
                        .foregroundStyle(Theme.textSecondary)
                }
            }
            Spacer()
            languagePicker
        }
        .padding(.horizontal, 14)
    }

    private var languagePicker: some View {
        @Bindable var settings = model.settings
        return Picker("Language", selection: $settings.language) {
            ForEach(model.availableLanguages) { meta in
                Text(meta.short).tag(meta.id)
            }
        }
        .pickerStyle(.menu)
        // A menu picker draws its current value as tinted text, which is the
        // system accent blue unless it is said otherwise.
        .tint(Theme.textPrimary)
        // Parent-facing chrome, so the 44pt HIG minimum rather than 64pt.
        // Forcing 64 here swallows the header on a 375pt screen.
        .frame(minWidth: 44, minHeight: 44)
        // The frame alone does NOT grow a menu picker's hit area — it lays out
        // at 62 x 34 and only the text is tappable, which the UI tests measured.
        // `contentShape` is what actually extends the tappable region to the
        // frame we just asked for.
        .contentShape(Rectangle())
        .accessibilityIdentifier("lang-picker")
    }

    private var typingRow: some View {
        TypingRow(
            typed: typed,
            muted: model.settings.muted,
            // The row's placeholder is the one string in it that is not a glyph,
            // and it is the first thing on screen before the first tap.
            language: model.settings.language,
            onReplay: replayAll,
            onDelete: {
                model.speech.cancelAll()
                if !typed.isEmpty { typed.removeLast() }
            },
            onClear: {
                model.speech.cancelAll()
                typed.removeAll()
                bubbleTask?.cancel()
                bubble = nil
            },
            onTapTyped: { speak($0.word, emoji: $0.emoji) }
        )
        .padding(.horizontal, 12)
    }

    /// `.id` per word, the way the web passes `key={id}`: a repeat of the same
    /// word must replay the float from the bottom rather than sit there.
    ///
    /// Nothing at all when there is no bubble — deliberately, because landscape
    /// overlays this on the grid and a placeholder that filled the overlay would
    /// swallow every tap aimed at the emoji underneath it.
    @ViewBuilder private var bubbleSlot: some View {
        if let bubble {
            WordBubble(emoji: bubble.emoji, word: bubble.word).id(bubble.id)
        }
    }

    /// Portrait gives the bubble a row of its own, and the row has to hold its
    /// height when it is empty — which is most of the time.
    ///
    /// `bubbleSlot.frame(height:)` is the obvious spelling and does not work.
    /// An absent `if let` branch is an `EmptyView`, and a stack drops an
    /// `EmptyView` from its layout *along with its frame*: the row measured 0pt
    /// inside the `VStack`, so the category strip and the entire grid jumped
    /// 44pt down the screen on every single tap and back up 2.2 seconds later.
    /// Caught on the simulator, not by a test — measured on its own through
    /// `ImageRenderer` the same view reports a perfectly correct 44pt, which is
    /// why `WordsViewTests` measures it inside a stack.
    ///
    /// Reserving the space with a real, invisible view and hanging the bubble
    /// off it is what actually holds the row open.
    ///
    /// Internal rather than private so `WordsViewTests` can measure it. It reads
    /// no environment, so it renders on its own.
    var bubbleRow: some View {
        Color.clear
            .frame(height: Self.bubbleRowHeight)
            .overlay { bubbleSlot }
    }

    /// Tall enough for the bubble at its 1.06 overshoot plus the 12pt it rises
    /// through, so nothing is clipped mid-float.
    static let bubbleRowHeight: CGFloat = 44

    private var grid: some View {
        EmojiGrid(
            entries: entries,
            bouncingID: bouncingID,
            // The same word that is about to be spoken, so VoiceOver and the
            // speaker agree.
            word: { model.word(for: $0) },
            onTap: tap
        )
    }

    // MARK: - Behaviour

    private func select(_ tab: CategoryTab) {
        category = tab.id
        speak(model.label(for: tab), emoji: tab.icon)
    }

    private func tap(_ entry: EmojiEntry) {
        let word = model.word(for: entry)
        typed = Self.capped(typed, appending: TypedEmoji(emoji: entry.emoji, word: word))
        bounce(entry.id)
        speak(word, emoji: entry.emoji)

        tapCount += 1
        if Self.milestones.contains(tapCount) { celebrate() }
    }

    /// Bubble, mascot, audio — the three parts of one reward.
    ///
    /// The bubble comes first and is not conditional on sound: a muted phone
    /// still answers every tap.
    private func speak(_ word: String, emoji: String) {
        showBubble(TypedEmoji(emoji: emoji, word: word))
        moodTask?.cancel()
        setMood(.excited)

        guard !model.settings.muted else {
            // Nothing will report finishing, so the excited face needs its own
            // way home or the mascot keeps star eyes for the rest of the day.
            moodTask = after(Self.excitedHold) { setMood(.happy) }
            return
        }

        // Star eyes first (rule 8), then the open mouth for as long as the word
        // is actually still being said (rule 9). `SpeechController` has no
        // "started" callback, so the handover is the 600ms the design already
        // specifies for the excited face rather than a guess.
        moodTask = after(Self.excitedHold) { setMood(.speaking) }

        // The mouth must close even if nothing ever reports finishing.
        //
        // `.speaking` was the one mood whose only exit was the engine's
        // `didFinish`, and `SpeechController.speak` — unlike `speakSequence` —
        // arms no watchdog. Pull the headphones out, take a call, get
        // interrupted by Siri, and the callback never arrives: the cloud is left
        // bouncing with a round open mouth, silent, until the next tap. A child
        // who taps once and looks up sees a broken mascot, and there is no mute
        // button to make that silence legible. The web never had this hole — it
        // returns to happy on a timer regardless of TTS.
        speechFallback?.cancel()
        speechFallback = after(Self.speechCeiling) { setMood(.happy) }

        model.speech.speak(word, in: model.settings.language) {
            moodTask?.cancel()
            speechFallback?.cancel()
            setMood(.happy)
        }
    }

    private func replayAll() {
        guard !typed.isEmpty, !model.settings.muted else { return }
        moodTask?.cancel()
        model.speech.speakSequence(
            typed.map { item in
                SpeechItem(text: item.word) {
                    showBubble(item)
                    setMood(.speaking)
                    // A sequence reports no completion, so the mouth needs its
                    // own way to close or it hangs open after the last word.
                    // Each item re-arms this, and it is tied to the bubble's
                    // lifetime: the mouth stays open exactly as long as the word
                    // is on screen. A word that runs past 2.2s closes the mouth
                    // early and the next item opens it again — a blink, not a
                    // failure state.
                    moodTask?.cancel()
                    moodTask = after(Self.bubbleHold) { setMood(.happy) }
                }
            },
            in: model.settings.language
        )
    }

    private func bounce(_ id: String) {
        bounceTask?.cancel()
        bouncingID = id
        bounceTask = after(Self.bounceHold) { bouncingID = nil }
    }

    private func showBubble(_ item: TypedEmoji) {
        bubble = item
        bubbleTask?.cancel()
        bubbleTask = after(Self.bubbleHold) { bubble = nil }
    }

    private func celebrate() {
        celebrationTask?.cancel()
        celebrationTask = Task {
            try? await Task.sleep(for: Self.celebrationDelay)
            guard !Task.isCancelled else { return }
            setMood(.beaming)
            try? await Task.sleep(for: Self.celebrationHold)
            guard !Task.isCancelled else { return }
            // The one assignment that bypasses `setMood`: the celebration is the
            // only thing allowed to lower its own flag, and `arbitrate` would
            // otherwise refuse to let it end.
            mood = .happy
        }
    }

    private func setMood(_ requested: MascotMood) {
        mood = Self.arbitrate(current: mood, requested: requested)
    }

    /// A language or mute change stops the audio and puts the face back.
    private func silence() {
        speechFallback?.cancel()
        model.speech.cancelAll()
        moodTask?.cancel()
        setMood(.happy)
    }

    /// One shape for every delayed effect on this screen, so none of them can
    /// forget the cancellation check.
    private func after(_ delay: Duration, _ work: @escaping @MainActor () -> Void) -> Task<Void, Never> {
        Task {
            try? await Task.sleep(for: delay)
            guard !Task.isCancelled else { return }
            work()
        }
    }
}

#Preview("Words") {
    AdaptiveShell { WordsView() }
        .environment(AppModel())
}
