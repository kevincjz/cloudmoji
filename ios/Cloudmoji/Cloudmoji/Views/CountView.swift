import SwiftUI
import CloudmojiCore

/// Count mode — "Cloudculator".
///
/// Ported from `src/components/CountMode.tsx`. N identical things are on screen and
/// the child taps them one at a time; each tap speaks the running count in the
/// chosen language, lights a dot, and stamps the tile with the number it was.
/// Finishing the round beams the mascot and says the whole phrase again.
///
/// Nothing here can fail. A tile already counted refuses quietly and still presses;
/// a muted phone still shows the number and still beams; a missing voice changes
/// nothing anyone can see.
///
/// The round's rules are in ``CountRound`` and the phrase is `CountingGrammar`'s.
/// What is left here is timing and SwiftUI state, which is what this file is for.
struct CountView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.cloudmojiIsCompact) private var isCompact

    /// Which mode is showing, for the landscape rail's tabs. Defaulted to this
    /// screen's own mode so `CountViewTests` and the previews can build it alone
    /// without the rail claiming Words is selected.
    var mode: AppMode = .count
    var onSelectMode: (AppMode) -> Void = { _ in }

    @State private var round: CountRound?
    /// The tile counted most recently, which bounces once. Separate from the
    /// round because it is presentation, not state the round cares about.
    @State private var lastCounted: Int?
    @State private var phrase: String = ""
    @State private var mood: MascotMood = .happy

    /// Every delayed effect is held so the next event can cancel it.
    @State private var moodTask: Task<Void, Never>?
    @State private var speechFallback: Task<Void, Never>?
    @State private var completionTask: Task<Void, Never>?

    // MARK: - Rules
    //
    // Static and pure, so they can be tested. Both are silent when wrong.

    /// The round's closing line: the same phrase, exclaimed.
    static func completionPhrase(_ phrase: String) -> String { phrase + "!" }

    /// Blank until something has been counted. A readout showing "0" before the
    /// first tap answers a question the child has not asked, with zero.
    static func numeral(for progress: Int) -> String {
        progress == 0 ? "" : String(progress)
    }

    /// The mode's own chrome, in five languages. Copy, not content — it lives
    /// beside the markup on the web too, so there is nothing to generate it from.
    /// `TypingRow.placeholders` is the precedent.
    struct UIText {
        let subtitle: [Language: String]
        let shuffle: [Language: String]
        let next: [Language: String]
    }

    static let uiText = UIText(
        subtitle: [
            .en: "Let's count!", .zh: "数一数!", .ms: "Jom kira!",
            .ja: "かぞえよう!", .tl: "Magbilang tayo!",
        ],
        shuffle: [
            .en: "Shuffle", .zh: "换一换", .ms: "Tukar",
            // Nearly the same word as `next` below. That is what src/ says.
            .ja: "つぎ", .tl: "Palitan",
        ],
        next: [
            .en: "Next!", .zh: "下一个!", .ms: "Seterusnya!",
            .ja: "つぎへ!", .tl: "Susunod!",
        ]
    )

    /// A missing row is a content bug, not a reason for a child to see a crash.
    private func text(_ table: [Language: String]) -> String {
        table[model.settings.language] ?? table[.en] ?? ""
    }

    // MARK: - Timings

    /// Star eyes on tap, "for ~600ms" (`CLAUDE.md` rule 8).
    private static let excitedHold = Duration.milliseconds(600)
    /// The pause between the last tap and the fanfare, so the final number lands
    /// on its own before the celebration talks over it.
    private static let completionDelay = Duration.milliseconds(1200)
    /// Longer than Words mode's three-second milestone hold: this is the end of a
    /// whole round, not a running total passing a marker.
    private static let beamingHold = Duration.milliseconds(3500)
    /// Longest a single word may hold the mouth open. Generously past any real
    /// utterance at rate 0.85 — an escape from a stuck state, not a timing
    /// mechanism, so it must never fire while a word is still being said.
    private static let speechCeiling = Duration.seconds(8)

    var body: some View {
        Group {
            if isCompact { landscape } else { portrait }
        }
        .task {
            // First round of the screen. Guarded, because `.task` runs again on
            // every reappearance — a rotation, or the app coming back to the
            // foreground — and re-rolling the countable under a child mid-round is
            // the one thing this mode must not do.
            //
            // A *mode* switch does not come through here at all: `ContentView`
            // swaps the two screens structurally, so this view's state is
            // discarded and the round starts over. That is what the web does —
            // `App.tsx` renders one mode or the other, never both — and it is the
            // same reason Words mode's typing row comes back empty.
            if round == nil { startRound(target: CountRound.firstTarget(in: model.countRange)) }
        }
        // A parent narrowing the categories or the range mid-session invalidates
        // the round on screen: it may be counting something they just switched off,
        // to a number they just excluded.
        .onChange(of: model.settings.enabledCategories) {
            startRound(target: round?.target ?? CountRound.firstTarget(in: model.countRange))
        }
        .onChange(of: model.settings.countRange) { _, range in
            startRound(target: CountRound.firstTarget(in: range))
        }
        // Same `useEffect(cancelAll, [muted, lang])` Words mode has. Without it the
        // phone finishes the previous language's number after the switch.
        .onChange(of: model.settings.language) {
            silence()
            // The phrase was built at tap time and would otherwise stay in the old
            // language until the next tap — and be handed to the new language's
            // voice on a replay.
            if let round, round.progress > 0 {
                phrase = model.phrase(for: round.item, count: round.progress)
            }
        }
        .onChange(of: model.settings.muted) { silence() }
        .onDisappear {
            model.speech.cancelAll()
            for task in [moodTask, speechFallback, completionTask] { task?.cancel() }
        }
    }

    // MARK: - Layouts

    private var portrait: some View { column }

    /// Sideways the tabs move into the rail so they stop eating the scarce vertical
    /// axis. Count mode has no categories, so the rail holds only the tabs.
    private var landscape: some View {
        HStack(spacing: 0) {
            SideRail(mode: mode, onSelectMode: onSelectMode) { EmptyView() }
            column
        }
    }

    /// One column, referenced by both layouts rather than transcribed into each.
    /// The web keeps two copies of this list and three edits landed on the dead
    /// one; while the two arrangements are identical there is no reason to give
    /// them the chance.
    private var column: some View {
        VStack(spacing: 0) {
            header
            readout
            countingArea
            controls
        }
    }

    // MARK: - Pieces

    private var header: some View {
        ModeHeader(
            mood: mood,
            title: "Cloudculator",
            subtitle: "🧮 " + text(Self.uiText.subtitle)
        )
    }

    private var readout: some View {
        CountReadout(
            target: round?.target ?? 0,
            progress: round?.progress ?? 0,
            numeral: Self.numeral(for: round?.progress ?? 0),
            phrase: phrase
        )
    }

    /// The tiles.
    ///
    /// The scroll view is load-bearing, and so is letting this be the piece with no
    /// height of its own. A stack takes its content height as a floor by default,
    /// and that is what pushed Shuffle and Next off the bottom of a landscape
    /// screen on the web from the second round onward. This area is the one that
    /// gives way.
    ///
    /// The `GeometryReader` is what keeps the round in the *middle* of that room
    /// rather than pinned to the top of it. The web's counting area is
    /// `flex items-center justify-center`; a `ScrollView`'s content is top-aligned,
    /// so a round of three upright sat under the readout with 400pt of empty space
    /// below it. Giving the content a minimum height of exactly the room available
    /// centres it while it fits — and changes nothing once it does not, which is
    /// the case the scroll view is here for.
    private var countingArea: some View {
        GeometryReader { proxy in
            ScrollView(showsIndicators: false) {
                grid
                    .frame(maxWidth: CountTileMetrics.maxGridWidth(compact: isCompact))
                    .frame(maxWidth: .infinity)
                    // A top-row badge hangs 10pt above its tile and a trailing badge
                    // 10pt past it; without this both are clipped by the scroll view.
                    // The trailing 10 also nudges the tiles 5pt left of the frame's
                    // centre, which is what puts the tiles *and their badges* in the
                    // middle rather than the tiles alone.
                    .padding(.top, CountTileMetrics.badgeOverhang)
                    .padding(.trailing, CountTileMetrics.badgeOverhang)
                    .padding(.horizontal, isCompact ? 12 : 20)
                    .padding(.bottom, 6)
                    .frame(minHeight: proxy.size.height)
            }
        }
        .frame(maxHeight: .infinity)
    }

    @ViewBuilder private var grid: some View {
        if let round {
            let columns = CountTileMetrics.columns(count: round.target, compact: isCompact)
            let side = CountTileMetrics.side(count: round.target, compact: isCompact)
            let glyph = CountTileMetrics.glyphSize(count: round.target, compact: isCompact)
            let spacing = CountTileMetrics.gridSpacing(count: round.target, compact: isCompact)

            // Not lazy: a round is at most ten tiles, and a lazy container would
            // realise them out of order and break the badges' paint order.
            Grid(horizontalSpacing: spacing, verticalSpacing: spacing) {
                ForEach(Array(stride(from: 0, to: round.target, by: columns)), id: \.self) { start in
                    GridRow {
                        ForEach(start..<min(start + columns, round.target), id: \.self) { index in
                            CountTile(
                                emoji: round.item.emoji,
                                index: index,
                                badge: round.badge(for: index),
                                isJustCounted: lastCounted == index,
                                side: side,
                                glyphSize: glyph,
                                onTap: { tap(index) }
                            )
                        }
                    }
                }
            }
            .animation(.easeOut(duration: 0.4), value: round.item.emoji)
        }
    }

    private var controls: some View {
        HStack(spacing: 12) {
            CountControl(
                glyph: "🔄",
                caption: text(Self.uiText.shuffle),
                identifier: "count-shuffle",
                tint: Theme.amber,
                action: shuffle
            )
            if round?.isComplete == true {
                CountControl(
                    glyph: "✨",
                    caption: text(Self.uiText.next),
                    identifier: "count-next",
                    tint: Theme.teal,
                    action: nextRound
                )
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeOut(duration: 0.4), value: round?.isComplete)
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .padding(.bottom, 8)
    }

    // MARK: - Behaviour

    private func tap(_ index: Int) {
        guard var current = round else { return }
        // A refused tap must not speak. Saying "three" twice for two taps on the
        // same dog is the one way this mode can actively teach the wrong thing —
        // and it is not a failure state, because the tile still presses.
        guard current.tap(index) else { return }
        round = current
        lastCounted = index

        let spoken = model.phrase(for: current.item, count: current.progress)
        phrase = spoken
        speak(spoken)

        if current.isComplete { celebrate(current) }
    }

    /// Mascot and audio. The number and the phrase are already on screen by the
    /// time this runs, and they are not conditional on sound: a muted phone still
    /// answers every tap.
    private func speak(_ text: String) {
        moodTask?.cancel()
        setMood(.excited)

        guard !model.settings.muted else {
            // Nothing will report finishing, so the excited face needs its own way
            // home or the mascot keeps star eyes for the rest of the round.
            moodTask = afterDelay(Self.excitedHold) { setMood(.happy) }
            return
        }

        moodTask = afterDelay(Self.excitedHold) { setMood(.speaking) }

        // The mouth must close even if nothing ever reports finishing. Pull the
        // headphones out, take a call, get interrupted by Siri, and `didFinish`
        // never arrives — leaving the cloud bouncing with a round open mouth,
        // silent, until the next tap.
        speechFallback?.cancel()
        speechFallback = afterDelay(Self.speechCeiling) { setMood(.happy) }

        model.speech.speak(text, in: model.settings.language) {
            moodTask?.cancel()
            speechFallback?.cancel()
            setMood(.happy)
        }
    }

    private func celebrate(_ finished: CountRound) {
        completionTask?.cancel()
        completionTask = Task {
            try? await Task.sleep(for: Self.completionDelay)
            guard !Task.isCancelled else { return }

            setMood(.beaming)
            let closing = Self.completionPhrase(
                model.phrase(for: finished.item, count: finished.target)
            )
            phrase = closing
            if !model.settings.muted {
                model.speech.speak(closing, in: model.settings.language)
            }

            try? await Task.sleep(for: Self.beamingHold)
            guard !Task.isCancelled else { return }
            // The one assignment that bypasses `setMood`: a celebration is the only
            // thing allowed to lower its own flag, and `arbitrate` would otherwise
            // refuse to let it end.
            mood = .happy
        }
    }

    /// A different thing to count, same number of them.
    private func shuffle() {
        startRound(target: round?.target ?? CountRound.firstTarget(in: model.countRange))
    }

    /// One more than last time, and a different thing to count.
    private func nextRound() {
        let target = CountRound.nextTarget(
            after: round?.target ?? model.countRange.lowerBound,
            in: model.countRange
        )
        startRound(target: target)
    }

    private func startRound(target: Int) {
        // Shuffling mid-celebration must not let the finished round's closing
        // phrase play over the new one, and must not leave a celebration task to
        // fire `.beaming` a beat later over a round that no longer exists — the
        // exact pair of bugs `CountMode.tsx` documents. `silence` cancels both.
        silence()

        guard let item = CountRound.pick(from: model.countables, excluding: round?.item) else {
            // Only reachable with a broken bundle, where `AppModel` fell back to an
            // empty repository. An empty screen beats a crash in front of a child.
            round = nil
            return
        }
        round = CountRound(item: item, target: target)
        lastCounted = nil
        phrase = ""
    }

    private func setMood(_ requested: MascotMood) {
        mood = MascotMood.arbitrate(current: mood, requested: requested)
    }

    /// Stops everything in flight and puts the face back.
    ///
    /// Two departures from Words mode's version, both deliberate.
    ///
    /// It cancels `completionTask` as well as the mood timers. Every caller —
    /// Shuffle, Next, mute, a language change — is an instruction to stop what is
    /// happening, and a celebration left running would fire `.beaming` a beat later
    /// over a round that no longer exists.
    ///
    /// And it assigns `mood` directly rather than going through `setMood`.
    /// `arbitrate` refuses to let anything lower the beaming flag, which is right
    /// for a milestone that fires mid-play and wrong here: a cloud still beaming
    /// over a round that was thrown away is the bug, not the protection.
    private func silence() {
        completionTask?.cancel()
        speechFallback?.cancel()
        moodTask?.cancel()
        model.speech.cancelAll()
        mood = .happy
    }
}

/// Shuffle and Next. Child-facing, so 64pt — a two-year-old presses these far more
/// often than a parent does.
struct CountControl: View {
    let glyph: String
    let caption: String
    let identifier: String
    let tint: Color
    let action: () -> Void

    /// `min-height: 64` on the web, and the floor for anything a child taps.
    static let minHeight: CGFloat = 64
    static let cornerRadius: CGFloat = 18

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: Self.cornerRadius, style: .continuous)
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                // 🔄 and ✨ are colour emoji and keep their own colours; the caption
                // beside them is text and would take the accent blue unaided.
                Text(glyph).font(.system(size: 18))
                Text(caption)
                    .font(Theme.body(14, .black))
                    .foregroundStyle(tint)
                    .lineLimit(1)
            }
            .padding(.horizontal, 22)
            .frame(minHeight: Self.minHeight)
            .background(tint.opacity(0.15), in: shape)
            .overlay(shape.stroke(tint.opacity(0.3), lineWidth: 2))
            .contentShape(Rectangle())
        }
        // Design system Active States: control buttons `scale(0.88)`.
        .buttonStyle(PressScale(scale: 0.88))
        .accessibilityLabel(caption)
        .accessibilityIdentifier(identifier)
    }
}

#Preview("Count") {
    AdaptiveShell { CountView() }
        .environment(AppModel())
}
