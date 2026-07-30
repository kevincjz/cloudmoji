import SwiftUI
import CloudmojiCore

/// Sleepy Cloud 🌙 — the wind-down.
///
/// A child or grown-up picks two, five or ten minutes; the cloud breathes and the
/// child breathes with it; the room gets darker; at the end the cloud falls
/// asleep and everything stops. Ported from `reference/breathing-cloud.jsx`.
///
/// **It never speaks.** A voice is the opposite of what a bedtime routine needs.
/// Instead, an intentionally quiet synthesized rain/ocean wash begins with the
/// session and stops whenever the session pauses, finishes, or leaves the screen.
/// Nothing here calls `SpeechController`.
struct SleepyCloudView: View {
    @Environment(\.scenePhase) private var scenePhase
    @Environment(AppModel.self) private var model
    @Environment(\.cloudmojiIsCompact) private var isCompact
    @Environment(\.cloudmojiLayout) private var layout

    /// `nil` is the duration picker; a number is a running session.
    @State private var minutes: Int?
    @State private var startedAt: Date?
    /// How far through, 0...1. Stepped by ``dimLoop`` rather than read off the
    /// clock every frame — the breathing is 60fps and the dimming is not, and
    /// writing screen brightness sixty times a second is a real cost for a
    /// change no eye can see.
    @State private var progress: Double = 0
    @State private var isAsleep = false
    @State private var dimTask: Task<Void, Never>?

    @State private var awake = ScreenAwake()
    @State private var dimmer = ScreenDimmer()

    /// Chrome, in the five languages. Copy, not content — `CountView.uiText` is
    /// the precedent and the reason.
    struct UIText {
        let title: [Language: String]
        let subtitle: [Language: String]
        let grownUp: [Language: String]
        let breatheIn: [Language: String]
        let breatheOut: [Language: String]
        let allDone: [Language: String]
        let again: [Language: String]
        let sound: [Language: String]
        /// `%d` is the number of minutes.
        let minutes: [Language: String]
    }

    static let uiText = UIText(
        title: [
            .en: "Sleepy Cloud", .zh: "瞌睡云", .ms: "Awan Mengantuk",
            .ja: "ねむいくも", .tl: "Inaantok na Ulap",
        ],
        subtitle: [
            .en: "Breathe along with the cloud", .zh: "跟着云朵呼吸",
            .ms: "Bernafas bersama awan", .ja: "くもと いっしょに いきをしよう",
            .tl: "Huminga kasabay ng ulap",
        ],
        grownUp: [
            .en: "Pick a sleepy time", .zh: "选择睡眠时间",
            .ms: "Pilih masa tidur", .ja: "ねむる じかんを えらぼう",
            .tl: "Pumili ng oras ng tulog",
        ],
        breatheIn: [
            .en: "breathe in", .zh: "吸气", .ms: "tarik nafas",
            .ja: "すって", .tl: "huminga",
        ],
        breatheOut: [
            .en: "breathe out", .zh: "呼气", .ms: "hembus nafas",
            .ja: "はいて", .tl: "hingahan",
        ],
        allDone: [
            .en: "all done", .zh: "结束了", .ms: "sudah selesai",
            .ja: "おしまい", .tl: "tapos na",
        ],
        again: [
            .en: "again", .zh: "再来", .ms: "sekali lagi",
            .ja: "もういちど", .tl: "ulit",
        ],
        sound: [
            .en: "soft sleep sounds", .zh: "轻柔助眠声", .ms: "bunyi tidur lembut",
            .ja: "やさしい ねむりの おと", .tl: "banayad na tunog sa pagtulog",
        ],
        minutes: [
            .en: "%d min", .zh: "%d 分钟", .ms: "%d min",
            .ja: "%d ふん", .tl: "%d min",
        ]
    )

    /// A missing row is a content bug, not a reason for a child to see a crash.
    private func text(_ table: [Language: String]) -> String {
        table[model.effectiveLanguage] ?? table[.en] ?? ""
    }

    /// The prototype's 56pt buttons lose to `CLAUDE.md` rule 1: either a child or
    /// grown-up may pick the time, and every option has to answer a toddler tap.
    static let choiceHeight: CGFloat = 64
    static let choiceWidth: CGFloat = 96
    static let padChoiceHeight: CGFloat = 78
    static let padChoiceWidth: CGFloat = 126

    /// How dark the overlay gets by the end. The prototype's `progress * 0.55`.
    static let maximumDim: Double = 0.55

    private var dim: Double { progress * Self.maximumDim }

    var body: some View {
        ZStack {
            moonHalo
            starfield
            content
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        // The deepening dark. Above the stars and the cloud, below nothing —
        // `.allowsHitTesting(false)` so it never swallows a tap meant for a
        // duration button or the home button underneath.
        .overlay {
            Color.black
                .opacity(dim * 0.5)
                .ignoresSafeArea()
                .allowsHitTesting(false)
        }
        .overlay(alignment: .bottom) { progressLine }
        .animation(.linear(duration: 1.2), value: dim)
        // **Pause and resume, explicitly.** Not "give the screen back when
        // inactive", which is what this used to be and which was half a rule:
        // it released the idle timer and the brightness but left the dim loop
        // running, so coming back re-dimmed the screen *without* re-acquiring
        // the idle timer. A session interrupted by a notification and returned
        // to therefore ran to its end with auto-lock switched back on — the
        // phone going dark halfway through a breathe is the single thing this
        // mini-app exists to prevent.
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { resume() } else { pause() }
        }
        .onChange(of: model.settings.muted) {
            guard isRunning else { return }
            if model.settings.muted {
                stopSleepAudio()
            } else {
                startSleepAudio()
            }
        }
        .onDisappear {
            dimTask?.cancel()
            yieldTheScreen()
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("sleepy-panel")
    }

    // MARK: - Pieces

    @ViewBuilder private var content: some View {
        if minutes == nil {
            picker
        } else {
            session
        }
    }

    /// Sideways the cloud is drawn small and the stack tightens: a landscape
    /// phone gives about 400pt of height, and the upright layout — a 130pt cloud,
    /// a title, a caption, three 64pt buttons and a footer, with 26pt between
    /// each — overflowed it and clipped the cloud against the top edge.
    private var cloudWidth: CGFloat {
        if layout.isExpandedPad { return 300 }
        return isCompact
            ? BreathingCloud.compactRenderedWidth
            : BreathingCloud.renderedWidth
    }

    private var picker: some View {
        VStack(spacing: layout.isExpandedPad ? 34 : (isCompact ? 12 : 26)) {
            BreathingCloud(scale: 0.85, phase: .hold, width: cloudWidth)

            VStack(spacing: layout.isExpandedPad ? 5 : 2) {
                Text(text(Self.uiText.title))
                    .font(
                        Theme.display(
                            layout.isExpandedPad ? 36 : (isCompact ? 20 : 26)
                        )
                    )
                    .foregroundStyle(
                        LinearGradient(
                            colors: [Theme.moonlight, Theme.lavender],
                            startPoint: .topLeading, endPoint: .bottomTrailing
                        )
                    )
                Text(text(Self.uiText.subtitle))
                    .font(Theme.body(layout.isExpandedPad ? 16 : 12, .heavy))
                    .foregroundStyle(Theme.textSecondary)
            }
            .multilineTextAlignment(.center)

            HStack(spacing: layout.isExpandedPad ? 16 : 10) {
                ForEach(BreathingSession.choices, id: \.self) { choice in
                    durationButton(choice)
                }
            }

            Text(text(Self.uiText.grownUp))
                .font(Theme.body(layout.isExpandedPad ? 14 : 11, .heavy))
                .foregroundStyle(Theme.textTertiary)

            HStack(spacing: layout.isExpandedPad ? 8 : 6) {
                Image(systemName: model.settings.muted ? "speaker.slash.fill" : "waveform")
                    .font(
                        .system(
                            size: layout.isExpandedPad ? 16 : 12,
                            weight: .black
                        )
                    )
                Text(text(Self.uiText.sound))
                    .font(Theme.body(layout.isExpandedPad ? 14 : 11, .heavy))
            }
            .foregroundStyle(Theme.moonlight.opacity(0.68))
        }
        .padding(.horizontal, layout.isExpandedPad ? 34 : 16)
    }

    private func durationButton(_ choice: Int) -> some View {
        let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
        return Button {
            Haptics.tap()
            begin(minutes: choice)
        } label: {
            VStack(spacing: 3) {
                Image(systemName: "moon.fill")
                    .font(
                        .system(
                            size: layout.isExpandedPad ? 16 : 13,
                            weight: .black
                        )
                    )
                    .foregroundStyle(Theme.lavender.opacity(0.88))
                Text(String(format: text(Self.uiText.minutes), choice))
                    .font(Theme.body(layout.isExpandedPad ? 18 : 15, .black))
                    .foregroundStyle(Theme.moonlight)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
                .frame(
                    minWidth: layout.isExpandedPad
                        ? Self.padChoiceWidth
                        : Self.choiceWidth,
                    minHeight: layout.isExpandedPad
                        ? Self.padChoiceHeight
                        : Self.choiceHeight
                )
                .background(
                    LinearGradient(
                        colors: [Theme.moonlight.opacity(0.14), Theme.lavender.opacity(0.08)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    in: shape
                )
                .overlay(shape.stroke(Theme.moonlight.opacity(0.28), lineWidth: 2))
                .shadow(color: Theme.moonlight.opacity(0.09), radius: 12, y: 6)
                // Without this only the label is tappable and most of the plate
                // a toddler aims at is dead.
                .contentShape(Rectangle())
        }
        .buttonStyle(PressScale(scale: 0.94))
        .accessibilityIdentifier("sleepy-duration-\(choice)")
    }

    /// The running session.
    ///
    /// `TimelineView(.animation)` is the `requestAnimationFrame` of the
    /// prototype: it re-evaluates on the display's own schedule, so the breath is
    /// computed from the wall clock every frame rather than accumulated by a
    /// timer that drifts. The alternative — a `Timer` nudging a `@State` scale —
    /// is what makes a ten-minute session end a visible beat late.
    private var session: some View {
        TimelineView(.animation(paused: isAsleep)) { context in
            let elapsed = startedAt.map { context.date.timeIntervalSince($0) } ?? 0
            let state = BreathingSession.state(at: elapsed, duration: totalSeconds)

            VStack(spacing: layout.isExpandedPad ? 38 : (isCompact ? 14 : 28)) {
                BreathingCloud(
                    scale: isAsleep ? BreathingSession.asleepScale : state.scale,
                    phase: isAsleep ? .asleep : state.phase,
                    dim: dim,
                    width: cloudWidth
                )

                if isAsleep {
                    finished
                } else {
                    Text(label(for: state.phase))
                        .font(Theme.body(layout.isExpandedPad ? 19 : 15, .heavy))
                        .foregroundStyle(Theme.moonlight.opacity(0.5 - dim * 0.4))
                        .tracking(1.4)
                        // A fixed height so the cloud does not step up and down
                        // the screen when the hold's blank label arrives.
                        .frame(height: 20)
                        .accessibilityIdentifier("sleepy-phase")
                }
            }
        }
    }

    private var finished: some View {
        VStack(spacing: 18) {
            Text(text(Self.uiText.allDone))
                .font(Theme.body(15, .heavy))
                .foregroundStyle(Theme.moonlight.opacity(0.45))
                .tracking(1)
                .accessibilityIdentifier("sleepy-done")

            Button {
                Haptics.tap()
                reset()
            } label: {
                Text(text(Self.uiText.again))
                    .font(Theme.body(14, .black))
                    .foregroundStyle(Theme.moonlight.opacity(0.72))
                    .frame(minWidth: 96, minHeight: HomeButtonMetrics.side)
                    .background(
                        Theme.moonlight.opacity(0.09),
                        in: Capsule()
                    )
                    .overlay(
                        Capsule()
                            .stroke(Theme.moonlight.opacity(0.20), lineWidth: 2)
                    )
                    .contentShape(Rectangle())
            }
            .buttonStyle(PressScale(scale: 0.94))
            .accessibilityIdentifier("sleepy-again")
        }
    }

    /// The hold has no label on purpose: a word arriving in the pause between
    /// breathing in and breathing out is an instruction to do something, and
    /// there is nothing to do.
    private func label(for phase: BreathPhase) -> String {
        switch phase {
        case .inhale: text(Self.uiText.breatheIn)
        case .exhale: text(Self.uiText.breatheOut)
        case .hold, .asleep: ""
        }
    }

    /// Fourteen faint stars, placed by the prototype's own arithmetic so the
    /// sky is the same sky. Deliberately not random: a layout that moved every
    /// time the screen was opened would be one more thing changing at bedtime.
    private var starfield: some View {
        GeometryReader { proxy in
            ForEach(0..<14, id: \.self) { i in
                Circle()
                    .fill(Theme.moonlight)
                    .frame(width: 3, height: 3)
                    .position(
                        x: proxy.size.width * CGFloat((i * 37) % 100) / 100,
                        y: proxy.size.height * CGFloat((i * 23) % 90) / 100
                    )
                    .modifier(StarTwinkle(period: 3 + Double(i % 4), delay: Double(i) * 0.4))
            }
        }
        .opacity(1 - dim)
        .allowsHitTesting(false)
    }

    /// A quiet crescent high in the sky gives the picker a full-screen scene,
    /// while the breathing cloud remains the only moving focal point.
    private var moonHalo: some View {
        GeometryReader { proxy in
            ZStack {
                Circle()
                    .fill(Theme.moonlight.opacity(0.08))
                    .frame(
                        width: layout.isExpandedPad ? 236 : 156,
                        height: layout.isExpandedPad ? 236 : 156
                    )
                    .blur(radius: layout.isExpandedPad ? 34 : 22)

                Image(systemName: "moon.fill")
                    .font(
                        .system(
                            size: layout.isExpandedPad ? 122 : 82,
                            weight: .black
                        )
                    )
                    .foregroundStyle(Theme.moonlight.opacity(0.26))
            }
            .position(
                x: proxy.size.width * (
                    layout.isExpandedPad ? 0.82 : (isCompact ? 0.83 : 0.78)
                ),
                y: proxy.size.height * (
                    layout.isExpandedPad ? 0.15 : (isCompact ? 0.24 : 0.16)
                )
            )
        }
        .opacity(1 - dim * 0.8)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    @ViewBuilder private var progressLine: some View {
        if minutes != nil && !isAsleep {
            GeometryReader { proxy in
                Rectangle()
                    .fill(
                        LinearGradient(
                            colors: [Theme.moonlight, Theme.lavender],
                            startPoint: .leading, endPoint: .trailing
                        )
                    )
                    .opacity(0.35)
                    .frame(width: proxy.size.width * progress)
            }
            .frame(height: 2)
            .background(Color.white.opacity(0.04))
            .allowsHitTesting(false)
        }
    }

    // MARK: - Session

    private var totalSeconds: TimeInterval { TimeInterval(minutes ?? 0) * 60 }

    /// Whether there is a session that still wants the screen. Asleep does not
    /// count: reaching the end deliberately hands auto-lock back.
    private var isRunning: Bool { minutes != nil && !isAsleep }

    private func begin(minutes choice: Int) {
        minutes = choice
        startedAt = Date()
        progress = 0
        isAsleep = false
        resume()
    }

    /// Takes the screen and starts the loop. Safe to call on a session that is
    /// already running — `ScreenAwake.hold` is idempotent and the old task is
    /// cancelled first — which is what lets `.onAppear` and a foreground event
    /// both land here.
    private func resume() {
        guard isRunning else { return }
        awake.hold()
        startSleepAudio()
        dimTask?.cancel()
        dimTask = Task { await runDimLoop() }
    }

    /// Gives the screen back and stops the loop, **without ending the session**.
    ///
    /// Stopping the loop is the half that used to be missing. `runDimLoop`
    /// measures from `startedAt` rather than accumulating, so a session paused
    /// for a minute and resumed picks up at the right place rather than at the
    /// place it left off — which is the behaviour a parent expects from a timer.
    private func pause() {
        dimTask?.cancel()
        dimTask = nil
        yieldTheScreen()
    }

    /// Steps the dim and the screen brightness once a second, and ends the
    /// session when the clock runs out.
    ///
    /// A loop rather than a single `Task.sleep(for: totalSeconds)`, because the
    /// dimming has to be progressive; a loop rather than a `Timer`, because a
    /// `Task` is cancelled by `.onDisappear` and a `Timer` on the main run loop
    /// would keep firing into a screen that has gone.
    private func runDimLoop() async {
        guard let startedAt, totalSeconds > 0 else { return }
        while !Task.isCancelled {
            let elapsed = Date().timeIntervalSince(startedAt)
            progress = BreathingSession.progress(at: elapsed, duration: totalSeconds)
            dimmer.dim(progress: progress)

            guard elapsed < totalSeconds else { break }
            try? await Task.sleep(for: .seconds(1))
        }
        guard !Task.isCancelled else { return }

        isAsleep = true
        // Auto-lock comes back on at the end, and that is the *desired*
        // behaviour rather than merely tidy housekeeping: the room is dark, the
        // cloud is asleep, and a phone that locks itself a minute later is the
        // right last thing to happen. Brightness goes back at the same moment,
        // because whoever picks the phone up next did not ask for a dim screen.
        yieldTheScreen()
    }

    private func reset() {
        pause()
        minutes = nil
        startedAt = nil
        progress = 0
        isAsleep = false
    }

    /// The one place the screen is handed back. Called from three exits — the
    /// end of a session, backgrounding, and leaving the mini-app — because each
    /// of them can happen without the other two, and a flag left set is a battery
    /// complaint nobody will ever trace back to a breathing exercise.
    private func yieldTheScreen() {
        stopSleepAudio()
        awake.release()
        dimmer.restore()
    }

    private func startSleepAudio() {
        guard isRunning, !model.settings.muted else { return }
        model.audio.attach(.sleepy)
        model.audio.playSleepNoise()
    }

    private func stopSleepAudio() {
        model.audio.stopSleepNoise()
        if model.audio.client == .sleepy {
            model.audio.detach()
        }
    }
}

/// The `starTwinkle` keyframe: opacity 0.15 → 0.5, on each star's own period.
private struct StarTwinkle: ViewModifier {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    let period: Double
    let delay: Double

    @State private var bright = false

    func body(content: Content) -> some View {
        content
            .opacity(bright ? 0.5 : 0.15)
            .onAppear {
                guard !reduceMotion else {
                    bright = true
                    return
                }
                withAnimation(
                    .easeInOut(duration: period / 2)
                        .repeatForever(autoreverses: true)
                        .delay(delay)
                ) { bright = true }
            }
    }
}

#Preview("Sleepy Cloud") {
    AdaptiveShell { SleepyCloudView() }
        .environment(AppModel())
}
