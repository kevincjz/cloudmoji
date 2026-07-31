package app.cloudmoji.android.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Schedules the delayed, one-shot mood transitions [MascotMoodMachine] arms —
 * the excited-hold handover and the two legs of a celebration. Kept as a seam
 * (rather than the machine calling `kotlinx.coroutines.delay` itself) so
 * tests can fire the scheduled work deterministically instead of waiting on a
 * real clock — the same reasoning, and shape, as `SpeechWatchdogScheduler`.
 */
interface MascotScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): MascotScheduleHandle
}

/** A single scheduled callback. [cancel] is safe to call more than once, and
 * after the action has already fired. */
interface MascotScheduleHandle {
    fun cancel()
}

/** Production [MascotScheduler]: one coroutine per scheduled callback,
 * cancelled via [kotlinx.coroutines.Job.cancel]. [scope] decides the
 * dispatcher and lifetime, the same composition-root choice
 * `CoroutineSpeechWatchdogScheduler` leaves to its caller. */
class CoroutineMascotScheduler(private val scope: CoroutineScope) : MascotScheduler {
    override fun schedule(delayMillis: Long, action: () -> Unit): MascotScheduleHandle {
        val job = scope.launch {
            delay(delayMillis)
            action()
        }
        return object : MascotScheduleHandle {
            override fun cancel() {
                job.cancel()
            }
        }
    }
}

/**
 * The cloud mascot's mood, as a pure-JVM, host-testable state machine.
 *
 * Ported from `WordsView.swift`'s `tap`/`speak`/`celebrate`/`setMood`, which
 * iOS duplicates verbatim on `CountView.swift` — "two implementations of one
 * rule drift apart" is exactly the failure this class exists to rule out on
 * Android: every screen that reacts to taps and speech (Words, Count, and
 * anything later) shares one instance of this machine instead of
 * re-implementing the timings.
 *
 * Three inputs drive every mood change: [onTap] (a child tapped something —
 * an emoji, a category chip, a typed emoji), and [onSpeechStarted] /
 * [onSpeechFinished] (the speech engine's own state, in the shape
 * `SpeechController.isSpeaking` already exposes — this class only consumes
 * plain start/finish signals, so wiring a real `SpeechController` later is a
 * two-line `StateFlow` collector, not a redesign). A fourth input — a
 * milestone reached — is never called from outside: [onTap] counts taps
 * itself (requirement: milestone counting lives with the state machine, not
 * a view) and raises it internally when the running total lands on a
 * milestone.
 *
 * **Threading: not thread-safe, by design**, exactly like `SpeechController`:
 * every public call, and every callback [scheduler] invokes, must arrive on
 * one confined thread (normally the app's main/UI thread). Compose's own
 * snapshot/recomposition model gives that for free when this class is driven
 * from a `ViewModel`/composable on the main thread.
 */
class MascotMoodMachine(
    private val scheduler: MascotScheduler,
    /** Cumulative session tap counts that earn a celebration. `CLAUDE.md`
     * rule 10, and `WordsView.milestones` / `WordsMode.tsx`'s `[10, 25, 50,
     * 100]`. Count mode's own instance passes `emptySet()` here — see
     * [celebrateNow] — so a tap tally this class still keeps for it never
     * auto-fires a celebration on its own. */
    private val milestones: Set<Int> = DEFAULT_MILESTONES,
    /** The two legs' duration for *this instance*. Defaults match Words'
     * timing (`WordsView.celebrationDelay`/`celebrationHold`); Count mode's
     * own instance overrides both to iOS `CountView`'s 1200ms/3500ms — a
     * whole round finishing is a bigger moment than a running total passing
     * a milestone marker, and gets a longer hold. */
    private val celebrationDelayMillis: Long = CELEBRATION_DELAY_MS,
    private val celebrationHoldMillis: Long = CELEBRATION_HOLD_MS,
) {
    companion object {
        val DEFAULT_MILESTONES: Set<Int> = setOf(10, 25, 50, 100)

        /** Star eyes hold for "~600ms" (rule 8) before the mascot is allowed
         * to move on to whatever speech is actually doing. Matches iOS
         * `WordsView.excitedHold` / the web's `setTimeout(..., 600)`. */
        const val EXCITED_HOLD_MS: Long = 600

        /** The celebration's own two legs: half a second of anticipation
         * after the milestone tap (`WordsView.celebrationDelay`), then three
         * seconds of beaming (rule 10, `WordsView.celebrationHold`). */
        const val CELEBRATION_DELAY_MS: Long = 500
        const val CELEBRATION_HOLD_MS: Long = 3000
    }

    private val moodState = MutableStateFlow(MascotMood.Happy)

    /** The mascot's current mood — what `CloudMascot` renders. */
    val mood: StateFlow<MascotMood> = moodState.asStateFlow()

    /** Cumulative taps this session. Exposed read-only mainly for tests; the
     * milestone celebration is the only thing that actually depends on it. */
    var tapCount: Int = 0
        private set

    /** The speech engine's own state, tracked independently of [mood] so a
     * deferred transition (see [armExcitedHold]) knows what to resolve to. */
    private var isSpeaking = false

    /** True for the [EXCITED_HOLD_MS] window after the most recent tap. While
     * true, [onSpeechStarted] / [onSpeechFinished] must not move the mood on
     * their own — the excited face is a floor, not a cap, on how long a tap's
     * reaction lasts; the hold timer itself is what hands off to whatever
     * [isSpeaking] says once the window closes. */
    private var excitedHoldActive = false

    private var excitedHoldHandle: MascotScheduleHandle? = null
    private var celebrationDelayHandle: MascotScheduleHandle? = null
    private var celebrationHoldHandle: MascotScheduleHandle? = null

    /**
     * Bumped every time [armExcitedHold] / [celebrateNow] supersede their own
     * previous timer. The guard against a stale callback resuming is this
     * counter, not [MascotScheduleHandle.cancel] — mirroring
     * `SpeechController`'s `generation`, which exists for exactly the same
     * reason: a real scheduler's cancellation is not guaranteed to land
     * before an already-in-flight callback runs, so correctness cannot
     * depend on it.
     */
    private var excitedGeneration = 0
    private var celebrationGeneration = 0

    /**
     * A child tapped something. Always counts toward the next milestone —
     * even mid-celebration, exactly like iOS's `tapCount += 1` running ahead
     * of `setMood`'s own beaming guard — and always *requests* the excited
     * face, which [MascotMood.arbitrate] silently drops while beaming.
     */
    fun onTap() {
        tapCount += 1
        request(MascotMood.Excited)
        armExcitedHold()
        if (tapCount in milestones) celebrateNow()
    }

    /** The speech engine started an utterance — real-word or replay, tap or
     * not. Moves to the speaking face immediately unless a tap's excited-hold
     * window is still running, in which case the hold timer applies this once
     * it closes. */
    fun onSpeechStarted() {
        isSpeaking = true
        if (!excitedHoldActive) request(MascotMood.Speaking)
    }

    /** The speech engine finished (or was cancelled). Same deferral as
     * [onSpeechStarted]: an early finish during the excited-hold window must
     * not cut the ~600ms floor short. */
    fun onSpeechFinished() {
        isSpeaking = false
        if (!excitedHoldActive) request(MascotMood.Happy)
    }

    private fun armExcitedHold() {
        excitedHoldActive = true
        excitedGeneration += 1
        val token = excitedGeneration
        excitedHoldHandle?.cancel()
        excitedHoldHandle = scheduler.schedule(EXCITED_HOLD_MS) {
            // A tap that landed after this one was scheduled already
            // superseded it — see the class doc on `excitedGeneration`.
            if (token != excitedGeneration) return@schedule
            excitedHoldActive = false
            request(if (isSpeaking) MascotMood.Speaking else MascotMood.Happy)
        }
    }

    /**
     * `WordsView.celebrate()` / iOS `CountView.celebrate()`: [celebrationDelayMillis]
     * of anticipation, then beaming for [celebrationHoldMillis]. Re-triggering
     * while a celebration is already in flight (two milestones close
     * together; a round finishing again mid-celebration cannot happen today,
     * but the same guard covers it for free) cancels and restarts both legs,
     * extending the hold rather than layering a second one.
     *
     * Public — not just [onTap]'s internal milestone trigger — because a
     * finished Count round is not a cumulative tap-count milestone at all:
     * *every* round ends this way, unconditionally, so Count calls this
     * directly rather than routing through a tap tally. [onBeamingStart]
     * fires at the instant the mood actually flips to beaming, which is
     * exactly when iOS `CountView.celebrate()` sets the closing phrase and
     * speaks it — Count's caller uses it for that; Words' milestone
     * celebration has no such side effect and leaves it at the default no-op.
     */
    fun celebrateNow(onBeamingStart: () -> Unit = {}) {
        celebrationDelayHandle?.cancel()
        celebrationHoldHandle?.cancel()
        celebrationGeneration += 1
        val token = celebrationGeneration
        celebrationDelayHandle = scheduler.schedule(celebrationDelayMillis) {
            if (token != celebrationGeneration) return@schedule
            // Not `request`: a celebration always wins, and always did — this
            // is just the direct route there instead of a no-op detour
            // through arbitrate.
            moodState.value = MascotMood.Beaming
            onBeamingStart()
            celebrationHoldHandle = scheduler.schedule(celebrationHoldMillis) {
                if (token != celebrationGeneration) return@schedule
                // The one assignment that bypasses `request`/`arbitrate`: the
                // celebration is the only thing allowed to lower its own
                // flag, matching iOS's comment on `WordsView.celebrate()`.
                moodState.value = MascotMood.Happy
            }
        }
    }

    /**
     * Cancels every timer in flight — the excited hold and both legs of a
     * celebration — and puts the mood back to Happy directly, bypassing
     * [MascotMood.arbitrate]. Mirrors iOS `CountView.silence()`: a
     * celebration that is about to be thrown away (the round it was for no
     * longer exists) must not be protected by the same rule that keeps a
     * *live* one from being interrupted.
     *
     * Count does, on Shuffle, Next, a language change, a mute toggle, and
     * leaving the screen, all of which can land mid-celebration. Words *does*
     * have a caller now too: `CloudmojiApp`'s `onOpenApp` calls this on every
     * *fresh* entry to Words from the launcher — which is why [tapCount] is
     * zeroed here as well, not just the mood. This instance, unlike Count's/
     * Flash Cards'/Animals' own [MascotMoodMachine]s, keeps [milestones]
     * non-empty; a tally left standing past a reset would still read past 100
     * the next time a child opens Words, and none of 10/25/50/100 could ever
     * be reached again for the rest of the process. iOS has no equivalent bug:
     * `WordsView`'s `tapCount` is `@State`, scoped to the view and reborn at
     * zero on every fresh visit.
     */
    fun reset() {
        excitedHoldHandle?.cancel()
        celebrationDelayHandle?.cancel()
        celebrationHoldHandle?.cancel()
        excitedGeneration += 1
        celebrationGeneration += 1
        excitedHoldActive = false
        isSpeaking = false
        tapCount = 0
        moodState.value = MascotMood.Happy
    }

    private fun request(requested: MascotMood) {
        moodState.value = MascotMood.arbitrate(current = moodState.value, requested = requested)
    }
}
