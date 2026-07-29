import SwiftUI
import CloudmojiCore

/// Animals 🔊 — tap a creature, hear it, then hear its name.
///
/// Content is `AppModel.emojis(in: .animals)`: repository-sourced, already
/// narrowed to what the parent left enabled, and never a word list written in
/// Swift. `src/data/` is the single source for both platforms.
///
/// **The noise is spoken, not recorded.** Tapping a dog says "woof woof", then
/// "dog" — and in 中文 it says 汪汪, then 狗. That is the point rather than a
/// compromise: animal noises are language-specific, so a child learning that the
/// same dog says ワンワン in Japanese and "guk guk" in Malay is learning exactly
/// what this app exists to teach, and no recording can teach it.
///
/// It also removes a dependency that was going nowhere. The alternative was
/// fifteen CC0 recordings, which meant sourcing, licensing and ear-approving
/// third-party audio for a kids app; a survey of Wikimedia Commons turned up
/// about six usable public-domain files, one of which was a prairie dog. Device
/// text-to-speech already ships, already speaks five languages, and is already
/// governed by the mute button.
///
/// A bundled `.caf` still wins where one exists — see `AnimalSoundCatalog` — so
/// a real bark can be dropped in later per animal without touching this file.
struct AnimalSoundsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.cloudmojiIsCompact) private var isCompact
    @Environment(\.cloudmojiLayout) private var layout

    @State private var mood: MascotMood = .happy
    @State private var bouncingID: String?
    @State private var bounceTask: Task<Void, Never>?
    @State private var moodTask: Task<Void, Never>?
    @State private var speechFallback: Task<Void, Never>?
    @State private var wordTask: Task<Void, Never>?

    private static let excitedHold = Duration.milliseconds(600)
    private static let bounceHold = Duration.milliseconds(400)
    private static let speechCeiling = Duration.seconds(8)
    /// How long the creature gets before Cloudmoji names it. Long enough for a
    /// one-second recording to finish, short enough that the two read as one
    /// answer to one tap rather than two separate events.
    private static let wordDelay = Duration.milliseconds(1100)

    /// Every glyph that has something to say — a noise in `src/data`, or a
    /// bundled recording if one is ever added.
    private var withSounds: Set<String> {
        model.animalSoundGlyphs.union(AnimalSoundCatalog.available())
    }

    private var animals: [EmojiEntry] {
        Self.grid(from: model.emojis(in: .animals), withRecordings: withSounds)
    }

    /// **The grid is the sound library**, when there is one.
    ///
    /// Pure and static so the two branches can be tested without a bundle.
    ///
    /// A tile exists only if tapping it makes that animal's noise — which is
    /// what makes this *Animal Sounds* rather than a second copy of Words mode
    /// filtered to animals. Twenty of the catalogue's forty-one animals have a
    /// noise; the giraffe and the octopus do not, and so are not here.
    ///
    /// The no-sounds-at-all branch is the degraded case for a broken bundle: an
    /// **empty** grid is a failure state (`CLAUDE.md` rule 4) and a far worse one
    /// than a tile that says "dog" instead of barking.
    static func grid(from animals: [EmojiEntry], withRecordings recorded: Set<String>) -> [EmojiEntry] {
        guard !recorded.isEmpty else { return animals }
        let matched = animals.filter { recorded.contains($0.emoji) }
        // A library that matches nothing in the pool — a mis-typed glyph, a
        // recording for an animal that was later removed from the catalogue —
        // must not produce a blank screen.
        return matched.isEmpty ? animals : matched
    }

    /// Whether there is anything to show at all.
    ///
    /// `grid` cannot recover from an **empty pool**, and it should not try: an
    /// empty pool means the parent switched the Animals category off, and
    /// inventing content they have asked not to see would be worse than saying
    /// so. `AppModel.visibleMiniApps` hides the tile in that case, so the only
    /// way to arrive here is the `-cm_open animalsounds` debug deep link — and
    /// a blank screen with no explanation is exactly what this guard exists to
    /// prevent.
    private var isUnavailable: Bool { animals.isEmpty }

    static func columns(compact: Bool, expandedPad: Bool, landscape: Bool) -> Int {
        if expandedPad { return landscape ? 4 : 3 }
        return compact ? 4 : 2
    }

    var body: some View {
        VStack(spacing: layout.isExpandedPad ? 18 : (isCompact ? 6 : 10)) {
            animalStageHeader

            if isUnavailable {
                Text("Animals are switched off in the grown-ups screen, so there is nothing to play here. Turn the Animals category back on to use this.")
                    .font(Theme.body(13, .bold))
                    .foregroundStyle(Theme.textSecondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 32)
                    .padding(.top, 24)
                    .accessibilityIdentifier("animals-unavailable")
                Spacer()
            } else {
            ScrollView(showsIndicators: false) {
                LazyVGrid(
                    columns: Array(
                        repeating: GridItem(.flexible(minimum: 96), spacing: AnimalCardMetrics.spacing),
                        count: Self.columns(
                            compact: isCompact,
                            expandedPad: layout.isExpandedPad,
                            landscape: layout.isLandscape
                        )
                    ),
                    spacing: AnimalCardMetrics.spacing
                ) {
                    ForEach(Array(animals.enumerated()), id: \.element.id) { index, entry in
                        AnimalSoundCard(
                            emoji: entry.emoji,
                            word: model.word(for: entry),
                            tint: AnimalCardMetrics.tint(index),
                            isPlaying: bouncingID == entry.id,
                            isCompact: isCompact,
                            isExpandedPad: layout.isExpandedPad,
                            onTap: { tap(entry) }
                        )
                    }
                }
                .frame(maxWidth: layout.isExpandedPad ? 1080 : .infinity)
                .padding(.horizontal, layout.isExpandedPad ? 26 : (isCompact ? 10 : 14))
                .padding(.bottom, 14)
                .frame(maxWidth: .infinity)
            }
            }
        }
        .onAppear { model.audio.attach(.animalSounds) }
        // Belt and braces with `goHome`, which also detaches.
        .onDisappear {
            model.audio.detach()
            model.speech.cancelAll()
            for task in [bounceTask, moodTask, speechFallback, wordTask] { task?.cancel() }
        }
        .onChange(of: model.settings.muted) { silence() }
        .onChange(of: model.settings.language) { silence() }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("animals-panel")
    }

    /// Cloudmoji becomes part of a little sound stage rather than a repeated
    /// page header. The paw and waveform establish the app before any words are
    /// needed.
    private var animalStageHeader: some View {
        HStack(spacing: layout.isExpandedPad ? 22 : 14) {
            Image(systemName: "pawprint.fill")
                .font(
                    .system(
                        size: layout.isExpandedPad ? 31 : (isCompact ? 20 : 24),
                        weight: .black
                    )
                )
                .foregroundStyle(Theme.gold.opacity(0.82))

            CloudMascot(
                mood: mood,
                size: layout.isExpandedPad ? 82 : (isCompact ? 44 : 58)
            )

            Image(systemName: "waveform")
                .font(
                    .system(
                        size: layout.isExpandedPad ? 31 : (isCompact ? 20 : 24),
                        weight: .black
                    )
                )
                .foregroundStyle(Theme.teal.opacity(0.88))
                .scaleEffect(mood == .speaking ? 1.18 : 1)
                .animation(.spring(response: 0.28, dampingFraction: 0.72), value: mood)
        }
        .padding(.horizontal, layout.isExpandedPad ? 34 : 24)
        .frame(
            minHeight: layout.isExpandedPad ? 94 : (isCompact ? 50 : 68)
        )
        .background(Theme.bgPrimary.opacity(0.46), in: Capsule())
        .overlay(Capsule().stroke(Theme.teal.opacity(0.20), lineWidth: 1))
        .padding(.top, layout.isExpandedPad ? 18 : (isCompact ? 2 : 8))
    }

    private func tap(_ entry: EmojiEntry) {
        // Before anything else, so the buzz lands with the finger rather than
        // after the audio stack has decided what to play. Haptics are
        // deliberately not tied to mute — see `Haptics`.
        Haptics.tap()
        bounce(entry.id)
        silenceAudio()

        moodTask?.cancel()
        setMood(.excited)

        guard !model.settings.muted else {
            moodTask = afterDelay(Self.excitedHold) { setMood(.happy) }
            return
        }

        // The noise first, then the name — the order a parent uses. "Woof" and
        // then "dog" teaches which is which; both at once teaches neither.
        //
        // A bundled recording wins if one was ever added, because a real bark
        // beats a synthesised one; otherwise the animal's noise is *spoken*, in
        // the family's own language. That is not a stand-in for audio: 汪汪,
        // ワンワン and "guk guk" are different words for the same dog, and a
        // recording cannot teach that.
        if let url = AnimalSoundCatalog.url(for: entry.emoji) {
            model.audio.playSound(url)
            wordTask = afterDelay(Self.wordDelay) { say(model.word(for: entry)) }
        } else if let noise = model.animalSound(for: entry.emoji) {
            say(noise)
            wordTask = afterDelay(Self.wordDelay) { say(model.word(for: entry)) }
        } else {
            say(model.word(for: entry))
        }
    }

    private func say(_ word: String) {
        moodTask?.cancel()
        moodTask = afterDelay(Self.excitedHold) { setMood(.speaking) }

        // The mouth must close even if nothing ever reports finishing — a call,
        // Siri, headphones pulled out. The same watchdog Words mode carries.
        speechFallback?.cancel()
        speechFallback = afterDelay(Self.speechCeiling) { setMood(.happy) }

        model.speech.speak(word, in: model.settings.language) {
            moodTask?.cancel()
            speechFallback?.cancel()
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

    /// Stops what the previous tap started. A child mashing tiles must hear the
    /// tile under his finger, not a queue of the last six.
    private func silenceAudio() {
        wordTask?.cancel()
        speechFallback?.cancel()
        model.speech.cancelAll()
    }

    private func silence() {
        silenceAudio()
        moodTask?.cancel()
        mood = .happy
    }
}

enum AnimalCardMetrics {
    static let spacing: CGFloat = 10
    static let height: CGFloat = 138
    static let compactHeight: CGFloat = 106
    static let padHeight: CGFloat = 162
    static let cornerRadius: CGFloat = 26

    static let tints: [Color] = [
        Theme.teal, Theme.gold, Theme.coral, Theme.moonlight,
        Theme.lavender, Theme.amber,
    ]

    static func tint(_ index: Int) -> Color {
        tints[index % tints.count]
    }
}

/// A habitat card, not an `EmojiTile`.
///
/// The generic Words tile was technically functional here, but it made Animals
/// look like a category filter. The larger portrait, ground shape, name and
/// waveform make the same content read as a creature that is about to perform.
private struct AnimalSoundCard: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    let emoji: String
    let word: String
    let tint: Color
    let isPlaying: Bool
    let isCompact: Bool
    let isExpandedPad: Bool
    let onTap: () -> Void

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: AnimalCardMetrics.cornerRadius, style: .continuous)
    }

    var body: some View {
        Button(action: onTap) {
            // The decoration is a `.background`, **not** a ZStack sibling.
            //
            // It used to be three shapes stacked behind the content, one of them
            // an `Ellipse` pinned at a fixed 230pt wide. A ZStack takes the
            // largest child's ideal size, so every card wanted to be 230pt wide
            // — wider than the ~180pt cell a two-column grid offers — and
            // `.frame(maxWidth: .infinity)` does not shrink a view below what it
            // asked for. Each card therefore overflowed its cell by about 25pt on
            // each side and the strokes of neighbouring cards crossed, which is
            // what the overlapping outlines were.
            //
            // A background is measured *after* the frame and can never push it
            // out, so the decoration can be any size it likes.
                HStack(spacing: isExpandedPad ? 18 : (isCompact ? 8 : 14)) {
                    Text(emoji)
                        .font(
                            .system(
                                size: isExpandedPad ? 76 : (isCompact ? 45 : 62)
                            )
                        )
                        .scaleEffect(isPlaying && !reduceMotion ? 1.16 : 1)

                    VStack(alignment: .leading, spacing: 6) {
                        Text(word)
                            .font(
                                Theme.body(
                                    isExpandedPad ? 18 : (isCompact ? 12 : 15),
                                    .black
                                )
                            )
                            .foregroundStyle(Theme.textPrimary)
                            .lineLimit(1)
                            .minimumScaleFactor(0.65)

                        Image(systemName: "waveform")
                            .font(
                                .system(
                                    size: isExpandedPad ? 24 : (isCompact ? 17 : 20),
                                    weight: .black
                                )
                            )
                            .foregroundStyle(tint)
                            .scaleEffect(x: isPlaying ? 1.22 : 0.86, y: 1)
                            .opacity(isPlaying ? 1 : 0.60)
                    }
                }
                .padding(.horizontal, 12)
            .frame(maxWidth: .infinity)
            .frame(
                height: isExpandedPad
                    ? AnimalCardMetrics.padHeight
                    : (isCompact ? AnimalCardMetrics.compactHeight : AnimalCardMetrics.height)
            )
            .background {
                ZStack {
                    shape.fill(
                        LinearGradient(
                            colors: [
                                tint.opacity(isPlaying ? 0.42 : 0.24),
                                Theme.bgPrimary.opacity(0.88),
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )

                    Circle()
                        .fill(Theme.gold.opacity(0.10))
                        .frame(width: isExpandedPad ? 132 : (isCompact ? 78 : 108))
                        .offset(
                            x: isExpandedPad ? 82 : (isCompact ? 48 : 68),
                            y: isExpandedPad ? -62 : (isCompact ? -34 : -50)
                        )

                    Ellipse()
                        .fill(tint.opacity(0.13))
                        .frame(
                            width: isExpandedPad ? 300 : 230,
                            height: isExpandedPad ? 74 : (isCompact ? 48 : 62)
                        )
                        .offset(y: isExpandedPad ? 74 : (isCompact ? 48 : 62))
                }
            }
            .clipShape(shape)
            .overlay(shape.stroke(tint.opacity(isPlaying ? 0.58 : 0.30), lineWidth: 2))
            .shadow(color: tint.opacity(isPlaying ? 0.28 : 0.12), radius: 12, y: 7)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressScale(scale: 0.88))
        .animation(
            reduceMotion ? .easeOut(duration: 0.10) : .spring(response: 0.28, dampingFraction: 0.68),
            value: isPlaying
        )
        .accessibilityLabel(word)
        // Kept for the existing navigation UI test and for compatibility with
        // the Words-grid identifier scheme.
        .accessibilityIdentifier("emoji-\(emoji)")
    }
}

#Preview("Animal sounds") {
    AdaptiveShell { AnimalSoundsView() }
        .environment(AppModel())
}
