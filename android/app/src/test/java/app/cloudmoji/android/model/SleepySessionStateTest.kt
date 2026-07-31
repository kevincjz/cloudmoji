package app.cloudmoji.android.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SleepySessionState]'s timing, phase transitions, and end behaviour —
 * driven by a [FakeClock] rather than a real one, so a session can be moved
 * forward in time without a real `Thread.sleep`. Loosely mirrors iOS
 * `SleepyCloudTests`'s "The screen" section, adapted for the state-machine
 * shape this class takes on Android (see that class's own doc for why: iOS
 * keeps this as `@State` on the view itself and steps it from a `Task`;
 * Android needs a pure, injectable-clock class instead, since the caller's
 * own coroutine loop lives in `SleepyCloudScreen.kt`, which cannot run in
 * this environment).
 */
class SleepySessionStateTest {

    private class FakeClock(var millis: Long = 0L) : Clock {
        override fun nowMillis(): Long = millis
    }

    /** Before anything is picked, there is no session — the picker's own
     * resting state. */
    @Test
    fun `before begin, there is no running session`() {
        val session = SleepySessionState(FakeClock())

        assertEquals(null, session.minutes.value)
        assertFalse(session.isRunning)
        assertFalse(session.isAsleep.value)
        assertEquals(0.0, session.progress.value, 0.0001)
        assertEquals(0.0, session.elapsedSeconds(), 0.0001)
    }

    /**
     * Picking a duration starts the clock and puts the session in the
     * running state — mirrors iOS `SleepyCloudView.begin(minutes:)`.
     */
    @Test
    fun `begin starts a running session at the chosen duration`() {
        val clock = FakeClock(millis = 10_000L)
        val session = SleepySessionState(clock)

        session.begin(minutes = 5)

        assertEquals(5, session.minutes.value)
        assertTrue(session.isRunning)
        assertFalse(session.isAsleep.value)
        assertEquals(0.0, session.progress.value, 0.0001)
        assertEquals(300.0, session.totalSeconds, 0.0001)
    }

    /**
     * [SleepySessionState.tick] steps [SleepySessionState.progress] from
     * the wall clock, re-derived from [SleepySessionState.elapsedSeconds]
     * rather than accumulated — a session that is *not* ticked for a while
     * (the caller's loop paused, matching iOS `pause()` cancelling the dim
     * task without touching `startedAt`) still reports the true elapsed
     * time the moment ticking resumes, rather than picking up wherever the
     * last tick left off.
     *
     * Mutation proof: temporarily changed [SleepySessionState.tick] to
     * accumulate a per-call increment instead of re-reading
     * [SleepySessionState.elapsedSeconds]. The "skip ahead without ticking"
     * assertion below (a single [SleepySessionState.tick] after the clock
     * jumps 90s finding `progress` at 0.75, not some smaller accumulated
     * value) failed before the wall-clock read was restored.
     */
    @Test
    fun `progress reflects true elapsed time, even across skipped ticks`() {
        val clock = FakeClock(millis = 0L)
        val session = SleepySessionState(clock)
        session.begin(minutes = 2) // 120 seconds total

        clock.millis = 30_000L
        session.tick()
        assertEquals(0.25, session.progress.value, 0.0001)

        // The caller's loop goes quiet for a while (backgrounded, or simply
        // not ticked) — no `tick()` calls at all — and the clock jumps
        // straight to 90s in.
        clock.millis = 90_000L
        session.tick()
        assertEquals(0.75, session.progress.value, 0.0001)
    }

    /**
     * [SleepySessionState.tick] returns `true` exactly once — the instant
     * the session transitions to asleep — and `false` on every call before
     * or after that instant, so a caller can tell "the session just now
     * ended" apart from "the session has been over for a while" without
     * keeping its own flag.
     *
     * Mutation proof: temporarily removed the `isAsleep = true` assignment
     * from the `elapsed >= totalSeconds` branch of
     * [SleepySessionState.tick] (kept the `return true`). The
     * `session.isAsleep.value`/`session.isRunning` assertions below failed before
     * the assignment was restored.
     */
    @Test
    fun `tick returns true exactly once, on the transition to asleep`() {
        val clock = FakeClock(millis = 0L)
        val session = SleepySessionState(clock)
        session.begin(minutes = 2) // 120 seconds total

        clock.millis = 60_000L
        assertFalse("halfway through is not the end", session.tick())
        assertFalse(session.isAsleep.value)

        clock.millis = 120_000L
        assertTrue("reaching the duration is the end", session.tick())
        assertTrue(session.isAsleep.value)
        assertFalse(session.isRunning)

        // The session is over; ticking again must not repeat the signal.
        clock.millis = 200_000L
        assertFalse("a second tick past the end must not signal again", session.tick())
    }

    /** [SleepySessionState.tick] is a no-op — and, in particular, never
     * signals the end of a session — before [SleepySessionState.begin] has
     * ever been called. */
    @Test
    fun `tick before begin is a no-op`() {
        val session = SleepySessionState(FakeClock())

        val signalledEnd = session.tick()

        assertFalse(signalledEnd)
        assertFalse(session.isAsleep.value)
        assertEquals(0.0, session.progress.value, 0.0001)
    }

    /** [SleepySessionState.reset] mirrors iOS `SleepyCloudView.reset()`'s
     * own field-clearing half: back to the picker, whether reset from a
     * running session or a finished one. */
    @Test
    fun `reset returns to the picker from either a running or finished session`() {
        val clock = FakeClock(millis = 0L)
        val running = SleepySessionState(clock)
        running.begin(minutes = 2)
        clock.millis = 30_000L
        running.tick()
        running.reset()
        assertEquals(null, running.minutes.value)
        assertFalse(running.isRunning)
        assertFalse(running.isAsleep.value)
        assertEquals(0.0, running.progress.value, 0.0001)

        // The clock is shared and already at 30s, so this second session's
        // own start instant is 30s — the end of *its* two minutes is
        // therefore 150s on the same clock, not 120s. (Asserting 120s here
        // was this test's own first bug: it read as an end-of-session case
        // but was really only 90s in, and it failed for that reason.)
        val finished = SleepySessionState(clock)
        finished.begin(minutes = 2)
        clock.millis = 30_000L + 120_000L
        finished.tick()
        assertTrue("the second session did not reach its own end", finished.isAsleep.value)
        finished.reset()
        assertEquals(null, finished.minutes.value)
        assertFalse(finished.isAsleep.value)
    }

    /** [SleepySessionState.breathState] is derived straight from
     * [BreathingSession.state] — this class owns no breathing arithmetic of
     * its own, just the clock anchor [BreathingSession.state] needs. */
    @Test
    fun `breathState mirrors BreathingSession at the current elapsed time`() {
        val clock = FakeClock(millis = 1_000L)
        val session = SleepySessionState(clock)
        session.begin(minutes = 2)

        clock.millis = 1_000L + 4_000L // 4s elapsed: the top of the inhale
        val breath = session.breathState()

        val expected = BreathingSession.state(t = 4.0, duration = 120.0)
        assertEquals(expected.phase, breath.phase)
        assertEquals(expected.scale, breath.scale, 0.0001)
    }

    /** A fresh session (no [SleepySessionState.begin] yet) reports the
     * picker's own resting pose through [SleepySessionState.breathState] —
     * `duration <= 0` is [BreathingSession.state]'s own "no session" case. */
    @Test
    fun `breathState before begin is the resting pose`() {
        val session = SleepySessionState(FakeClock())

        val breath = session.breathState()

        assertEquals(BreathPhase.Inhale, breath.phase)
        assertEquals(BreathingSession.REST_SCALE, breath.scale, 0.0001)
    }
}
