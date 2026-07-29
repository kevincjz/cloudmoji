import Foundation
import Testing
import CloudmojiCore
@testable import Cloudmoji

@Suite("Sleepy Cloud")
@MainActor
struct SleepyCloudTests {

    /// Floating-point comparison. The scale is built out of a cosine, so an exact
    /// equality would be asserting something about the FPU rather than about the
    /// breathing.
    private func expect(_ value: Double, _ expected: Double, _ what: String) {
        #expect(abs(value - expected) < 0.0001, "\(what): expected \(expected), got \(value)")
    }

    // MARK: - The breath

    /// The prototype's timings, at the four moments that define them: the bottom
    /// of an exhale, the top of an inhale, the start of the exhale, and the same
    /// place one whole cycle later.
    ///
    /// Mutation: change `hold` from 2 to 1. The t=6 case moves into the exhale
    /// early and reports 1.058 rather than 1.1, and this fails.
    @Test("the breath is 4s in, 2s held, 6s out")
    func breathHitsItsMarks() {
        let duration: TimeInterval = 120

        let start = BreathingSession.state(at: 0, duration: duration)
        expect(start.scale, 0.75, "scale at t=0")
        #expect(start.phase == .inhale)

        let top = BreathingSession.state(at: 4, duration: duration)
        expect(top.scale, 1.10, "scale at the top of the inhale")
        #expect(top.phase == .hold)

        let exhaleStart = BreathingSession.state(at: 6, duration: duration)
        expect(exhaleStart.scale, 1.10, "scale at the start of the exhale")
        #expect(exhaleStart.phase == .exhale)

        let nextCycle = BreathingSession.state(at: 12, duration: duration)
        expect(nextCycle.scale, 0.75, "scale one whole cycle in")
        #expect(nextCycle.phase == .inhale)

        #expect(BreathingSession.cycle == 12)
    }

    /// The easing, not just the endpoints. A linear ramp hits both marks above
    /// and reads as a machine rather than a chest — the halfway point is what
    /// tells the two apart.
    ///
    /// Mutation: replace `eased(k)` with `k`. The midpoint of the inhale moves
    /// from 0.925 to 0.925 — identical, because a cosine ease is symmetric — so
    /// the *quarter* point is checked instead, where linear gives 0.8375 and the
    /// ease gives 0.7999.
    @Test("the breath eases in and out rather than ramping")
    func breathIsEased() {
        let quarter = BreathingSession.state(at: 1, duration: 120).scale
        let linear = 0.75 + 0.25 * (1.10 - 0.75)
        #expect(abs(quarter - linear) > 0.02,
                "a quarter of the way into the inhale the scale is \(quarter), which is the linear answer")

        // Symmetric about the middle of the breath: a cosine ease is, and a
        // one-sided ease-in is not.
        let mid = BreathingSession.state(at: 2, duration: 120).scale
        expect(mid, (0.75 + 1.10) / 2, "the middle of the inhale")
    }

    /// The session ends, the cloud stops moving, and it stays stopped.
    ///
    /// Mutation: delete the `t < duration` guard. The 120s and 500s cases come
    /// back mid-breath and this fails.
    @Test("the cloud is asleep once the session is over, and stays asleep")
    func sessionEndsAsleep() {
        let duration: TimeInterval = 120
        for t in [duration, duration + 0.5, 500] {
            let state = BreathingSession.state(at: t, duration: duration)
            #expect(state.phase == .asleep, "at t=\(t) the cloud is \(state.phase.rawValue)")
            expect(state.scale, BreathingSession.asleepScale, "asleep scale at t=\(t)")
        }
    }

    /// The picker draws a still cloud through the same function, with no session
    /// picked. "No duration" must not mean "instantly finished".
    @Test("no session yet is the resting pose, not sleep")
    func noSessionRests() {
        let state = BreathingSession.state(at: 0, duration: 0)
        #expect(state.phase == .inhale)
        expect(state.scale, BreathingSession.restScale, "the picker's cloud")
        #expect(BreathingSession.progress(at: 30, duration: 0) == 0)
    }

    /// A clock that stepped backwards must not produce a NaN scale in front of a
    /// child. `truncatingRemainder` on a negative gives a negative, which lands
    /// in the exhale branch with a negative `k`.
    @Test("negative time is clamped rather than producing nonsense")
    func negativeTimeIsSafe() {
        let state = BreathingSession.state(at: -5, duration: 120)
        #expect(!state.scale.isNaN)
        #expect(state.scale >= BreathingSession.restScale - 0.0001)
        #expect(state.scale <= BreathingSession.peakScale + 0.0001)
    }

    /// Mutation: drop the `min(max(...))` clamp from `progress`. The over-run
    /// case returns 1.25 and this fails — which on screen is a progress line
    /// wider than the phone.
    @Test("progress is 0 to 1 and never past it")
    func progressIsClamped() {
        expect(BreathingSession.progress(at: 0, duration: 120), 0, "at the start")
        expect(BreathingSession.progress(at: 60, duration: 120), 0.5, "halfway")
        expect(BreathingSession.progress(at: 150, duration: 120), 1, "past the end")
        expect(BreathingSession.progress(at: -10, duration: 120), 0, "before the start")
    }

    // MARK: - The screen

    /// Hold and release are balanced and idempotent. `.onAppear` and a
    /// scene-phase change can both arrive for one entry, and three separate
    /// exits all call `release()`.
    ///
    /// Mutation: delete the `guard !isHeld` in `hold()`. The write log becomes
    /// `[true, true, false]` and this fails.
    @Test("the idle timer is held once and given back once")
    func idleTimerIsBalanced() {
        var writes: [Bool] = []
        let awake = ScreenAwake { writes.append($0) }

        awake.hold()
        awake.hold()
        #expect(awake.isHeld)
        awake.release()
        awake.release()
        #expect(!awake.isHeld)

        #expect(writes == [true, false], "saw \(writes)")
    }

    /// **The leak this screen has to be trusted not to have.** A brightness that
    /// survived into another app is a complaint nobody will trace back to a
    /// breathing exercise.
    ///
    /// Mutation: delete `self.original = nil` at the end of `restore()`. The
    /// second restore writes the original a second time, and the "restore before
    /// anything was taken" case below writes when it must not.
    @Test("brightness is restored exactly to where it was found")
    func brightnessIsRestored() {
        var current: CGFloat = 0.8
        var writes: [CGFloat] = []
        let dimmer = ScreenDimmer(read: { current }, write: { value in
            current = value
            writes.append(value)
        })

        // Nothing taken yet: restore must be a no-op, not a write of some
        // default. Three exits call it and only one of them ever dimmed.
        dimmer.restore()
        #expect(writes.isEmpty, "restoring before dimming wrote \(writes)")
        #expect(!dimmer.isDimmed)

        dimmer.dim(progress: 0)
        #expect(dimmer.isDimmed)
        #expect(dimmer.original == 0.8)
        dimmer.dim(progress: 1)
        #expect(current < 0.8, "the screen never got darker")

        dimmer.restore()
        #expect(current == 0.8, "the screen came back at \(current) rather than 0.8")
        #expect(!dimmer.isDimmed)

        let afterRestore = writes.count
        dimmer.restore()
        #expect(writes.count == afterRestore, "a second restore wrote again")
    }

    /// The ramp itself: a fraction of where the screen started, never below the
    /// floor, and never brighter than it was.
    ///
    /// Mutation: drop the clamp on `progress`. A progress of 2 returns a
    /// negative brightness, which iOS reads as a black screen.
    @Test("the dim ramp stays between the floor and where it started")
    func dimRampIsBounded() {
        #expect(ScreenDimmer.level(from: 1.0, progress: 0) == 1.0)
        #expect(abs(ScreenDimmer.level(from: 1.0, progress: 1) - CGFloat(ScreenDimmer.floor)) < 0.0001)
        for progress in [-1.0, 0.0, 0.3, 0.7, 1.0, 2.0] {
            let level = ScreenDimmer.level(from: 0.6, progress: progress)
            #expect(level <= 0.6 + 0.0001, "progress \(progress) brightened the screen to \(level)")
            #expect(level >= 0.6 * CGFloat(ScreenDimmer.floor) - 0.0001,
                    "progress \(progress) took the screen to \(level), under the floor")
        }
    }

    // MARK: - Copy

    /// Sleepy Cloud is the one screen a family may use every single night, so a
    /// missing translation here is not a rare edge — and the fallback is English
    /// text on an otherwise Chinese screen.
    ///
    /// Mutation: delete any `.zh` row. This fails and names the table.
    @Test("every string on the screen exists in all five languages")
    func copyCoversEveryLanguage() {
        let tables: [(String, [Language: String])] = [
            ("title", SleepyCloudView.uiText.title),
            ("subtitle", SleepyCloudView.uiText.subtitle),
            ("grownUp", SleepyCloudView.uiText.grownUp),
            ("breatheIn", SleepyCloudView.uiText.breatheIn),
            ("breatheOut", SleepyCloudView.uiText.breatheOut),
            ("allDone", SleepyCloudView.uiText.allDone),
            ("again", SleepyCloudView.uiText.again),
            ("sound", SleepyCloudView.uiText.sound),
            ("minutes", SleepyCloudView.uiText.minutes),
        ]
        for (name, table) in tables {
            for language in Language.allCases {
                #expect(table[language]?.isEmpty == false,
                        "\(name) has no \(language.rawValue)")
            }
        }
        // The minutes row is a format string and has to keep its placeholder, or
        // every duration button reads the same.
        for language in Language.allCases {
            #expect(SleepyCloudView.uiText.minutes[language]?.contains("%d") == true,
                    "the \(language.rawValue) duration label lost its number")
        }
    }

    /// The prototype's 56pt buttons lose to `CLAUDE.md` rule 1. A child or
    /// grown-up may pick the time and every option must answer.
    ///
    /// Mutation: set `choiceHeight` back to 56. This fails.
    @Test("the duration buttons clear the child minimum")
    func durationButtonsAreChildSized() {
        #expect(SleepyCloudView.choiceHeight >= 64)
        #expect(SleepyCloudView.choiceWidth >= 64)
        #expect(BreathingSession.choices == [2, 5, 10])
    }
}
