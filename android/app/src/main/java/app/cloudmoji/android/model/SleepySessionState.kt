package app.cloudmoji.android.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sleepy Cloud's own session — which duration was picked, how far through
 * it the clock says the session is, and whether it has finished. Pure and
 * host-testable independent of Compose, Android's `Window`, or a real
 * clock — [clock] is injected so a test can move time without a real
 * `Thread.sleep`, mirroring `MascotMoodMachine`'s own "inject the thing
 * that would otherwise make this untestable" shape.
 *
 * Ported from iOS `SleepyCloudView`'s `@State private var minutes/startedAt/
 * progress/isAsleep` and its `begin`/`reset` functions. What iOS does
 * instead with a `Task`-driven `runDimLoop` — stepping [progress] once a
 * second and detecting the end of the session — is [tick] here: a single
 * pure step a caller (`SleepyCloudScreen`'s own coroutine loop) drives on
 * whatever cadence it likes, rather than this class owning a timer or a
 * coroutine scope itself. Screen lifecycle (keep-awake, dimming, audio) is
 * deliberately not this class's job either — see `platform/ScreenControl.kt`
 * and `ui/sleepy/SleepyCloudScreen.kt`, which read [isRunning]/[progress]/
 * [isAsleep] to decide what the screen and speaker should be doing, exactly
 * as iOS's `resume`/`pause`/`yieldTheScreen` read the `@State` this class
 * now owns.
 *
 * Deliberately does not reset on its own when left — unlike iOS's `@State`,
 * which dies the moment `SleepyCloudView` is dismissed, a Kotlin instance
 * held by `CloudmojiApplication` (Task 6's own rotation-survival pattern)
 * outlives an Activity recreation. [reset] is `CloudmojiApp`'s own call on a
 * *fresh* entry from the launcher — the same "process-scoped state, reset on
 * fresh navigation" trade-off `FlashCardsViewModel`/`CountViewModel` already
 * make, not a divergence invented for this screen.
 *
 * **Threading: not thread-safe, by design** — the same confined-to-one-thread
 * contract as every other pure state class in this app (`MascotMoodMachine`,
 * `FlashCardsViewModel`): every call must arrive on the main/UI thread.
 *
 * State is published as [StateFlow]s rather than plain properties, matching
 * [FlashCardsViewModel]/[CountViewModel] exactly: `SleepyCloudScreen`
 * collects them with `collectAsState()`, and a plain `var` mutated from
 * [tick]'s own loop would change nothing on screen — Compose has no way to
 * observe it.
 */
class SleepySessionState(private val clock: Clock = SystemClock) {

    private val minutesState = MutableStateFlow<Int?>(null)

    /** `null` is the duration picker; a number is a running or finished
     * session. Mirrors iOS `minutes: Int?`. */
    val minutes: StateFlow<Int?> = minutesState.asStateFlow()

    private var startedAtMillis: Long? = null

    private val progressState = MutableStateFlow(0.0)

    /** How far through the session, 0...1 — [BreathingSession.progress]'s
     * own output, cached here rather than recomputed on every read so a
     * caller can compare "did this just change" without doing the division
     * itself. */
    val progress: StateFlow<Double> = progressState.asStateFlow()

    private val asleepState = MutableStateFlow(false)

    /** The session reached its end. Mirrors iOS `isAsleep`: once true, the
     * cloud stops moving and [isRunning] is false — reaching the end
     * deliberately hands the screen back, the same as leaving early does. */
    val isAsleep: StateFlow<Boolean> = asleepState.asStateFlow()

    /** [BreathingSession] takes seconds, not minutes; this is the one place
     * that conversion happens, mirroring iOS `totalSeconds`. */
    val totalSeconds: Double get() = (minutesState.value ?: 0) * 60.0

    /** Whether there is a session that still wants the screen. Asleep does
     * not count — mirrors iOS `isRunning`'s own doc: "reaching the end
     * deliberately hands auto-lock back." */
    val isRunning: Boolean get() = minutesState.value != null && !asleepState.value

    /** Seconds since [begin], or 0 before a session has started. Re-read
     * from [clock] on every call rather than accumulated, so a session
     * paused (the caller simply stops calling [tick]) and resumed later
     * picks up at the *true* elapsed wall-clock time rather than the time
     * [tick] happened to be called — the property iOS's own doc calls out
     * for why `runDimLoop` measures from `startedAt` instead of summing. */
    fun elapsedSeconds(): Double {
        val startedAt = startedAtMillis ?: return 0.0
        return (clock.nowMillis() - startedAt) / 1000.0
    }

    /** The cloud's current scale and breath phase, derived from
     * [elapsedSeconds] — mirrors iOS `SleepyCloudView.session`'s own
     * `BreathingSession.state(at: elapsed, duration: totalSeconds)` call,
     * made once per read rather than cached, since it is cheap arithmetic
     * meant to be called every rendered frame. */
    fun breathState(): BreathState = BreathingSession.state(elapsedSeconds(), totalSeconds)

    /** A grown-up or child picked [minutes]. Mirrors iOS `begin(minutes:)`:
     * starts the clock, and resets [progress]/[isAsleep] in case this
     * follows a finished session (the "again" button). */
    fun begin(minutes: Int) {
        minutesState.value = minutes
        startedAtMillis = clock.nowMillis()
        progressState.value = 0.0
        asleepState.value = false
    }

    /** Back to the picker. Mirrors iOS `reset()`'s own field-clearing half —
     * the screen-lifecycle half (`pause()`) is the caller's job, same as
     * [begin]'s screen-lifecycle half ([isRunning] becoming true) is. */
    fun reset() {
        minutesState.value = null
        startedAtMillis = null
        progressState.value = 0.0
        asleepState.value = false
    }

    /**
     * Steps [progress] from the wall clock, and detects the end of the
     * session. A no-op, returning `false`, when [isRunning] is already
     * false — calling this from a caller's loop that does not itself track
     * "has the session already ended" is what makes the loop safe to leave
     * running one tick past the end.
     *
     * Returns `true` on the one call where the session transitions to
     * [isAsleep] — never again afterward, since a second call finds
     * [isRunning] already false — so a caller can tell "the session just now
     * ended" from "the session has been over for a while" without keeping
     * its own flag. Mirrors iOS `runDimLoop`'s own `guard elapsed <
     * totalSeconds else { break }` / `isAsleep = true` transition, one loop
     * iteration at a time.
     */
    fun tick(): Boolean {
        if (!isRunning) return false
        val elapsed = elapsedSeconds()
        progressState.value = BreathingSession.progress(elapsed, totalSeconds)
        if (elapsed >= totalSeconds) {
            asleepState.value = true
            return true
        }
        return false
    }
}
