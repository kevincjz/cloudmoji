package app.cloudmoji.android.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BreathingSession]'s pure timing arithmetic — ported test-for-test from
 * iOS `SleepyCloudTests`'s "The breath" section
 * (`ios/Cloudmoji/CloudmojiTests/SleepyCloudTests.swift`). Pure
 * `Double`/enum arithmetic — no Compose runtime, no clock — so every case
 * here runs on the JVM.
 */
class BreathingSessionTest {

    /** Floating-point comparison. The scale is built out of a cosine, so an
     * exact equality would be asserting something about the FPU rather than
     * about the breathing. Mirrors iOS `SleepyCloudTests.expect`. */
    private fun expect(value: Double, expected: Double, what: String) {
        assertTrue("$what: expected $expected, got $value", Math.abs(value - expected) < 0.0001)
    }

    /**
     * The prototype's timings, at the four moments that define them: the
     * bottom of an exhale, the top of an inhale, the start of the exhale,
     * and the same place one whole cycle later.
     *
     * Mutation proof: temporarily changed [BreathingSession.HOLD_SECONDS]
     * from `2.0` to `1.0`. The `t = 6` case then landed inside the exhale
     * early and reported ~1.058 rather than 1.10. This test failed before
     * the constant was restored.
     */
    @Test
    fun `the breath is 4s in, 2s held, 6s out`() {
        val duration = 120.0

        val start = BreathingSession.state(t = 0.0, duration = duration)
        expect(start.scale, 0.75, "scale at t=0")
        assertEquals(BreathPhase.Inhale, start.phase)

        val top = BreathingSession.state(t = 4.0, duration = duration)
        expect(top.scale, 1.10, "scale at the top of the inhale")
        assertEquals(BreathPhase.Hold, top.phase)

        val exhaleStart = BreathingSession.state(t = 6.0, duration = duration)
        expect(exhaleStart.scale, 1.10, "scale at the start of the exhale")
        assertEquals(BreathPhase.Exhale, exhaleStart.phase)

        val nextCycle = BreathingSession.state(t = 12.0, duration = duration)
        expect(nextCycle.scale, 0.75, "scale one whole cycle in")
        assertEquals(BreathPhase.Inhale, nextCycle.phase)

        assertEquals(12.0, BreathingSession.CYCLE_SECONDS, 0.0001)
    }

    /**
     * The easing, not just the endpoints. A linear ramp hits both marks
     * above and reads as a machine rather than a chest — the halfway point
     * is what tells the two apart.
     *
     * Mutation proof: temporarily replaced `eased(k)`'s body with `return
     * k` (linear). The quarter-point assertion below — which specifically
     * checks the cosine ease differs from the linear answer — failed before
     * the real ease was restored.
     */
    @Test
    fun `the breath eases in and out rather than ramping`() {
        val quarter = BreathingSession.state(t = 1.0, duration = 120.0).scale
        val linear = 0.75 + 0.25 * (1.10 - 0.75)
        assertTrue(
            "a quarter of the way into the inhale the scale is $quarter, which is the linear answer",
            Math.abs(quarter - linear) > 0.02,
        )

        // Symmetric about the middle of the breath: a cosine ease is, and a
        // one-sided ease-in is not.
        val mid = BreathingSession.state(t = 2.0, duration = 120.0).scale
        expect(mid, (0.75 + 1.10) / 2, "the middle of the inhale")
    }

    /**
     * The session ends, the cloud stops moving, and it stays stopped.
     *
     * Mutation proof: temporarily deleted the `t < duration` guard (fell
     * through to the cyclical branch unconditionally). The `t = 120` and
     * `t = 500` cases below came back mid-breath instead of asleep before
     * the guard was restored.
     */
    @Test
    fun `the cloud is asleep once the session is over, and stays asleep`() {
        val duration = 120.0
        for (t in listOf(duration, duration + 0.5, 500.0)) {
            val state = BreathingSession.state(t = t, duration = duration)
            assertEquals("at t=$t the cloud is ${state.phase}", BreathPhase.Asleep, state.phase)
            expect(state.scale, BreathingSession.ASLEEP_SCALE, "asleep scale at t=$t")
        }
    }

    /** The picker draws a still cloud through the same function, with no
     * session picked. "No duration" must not mean "instantly finished". */
    @Test
    fun `no session yet is the resting pose, not sleep`() {
        val state = BreathingSession.state(t = 0.0, duration = 0.0)
        assertEquals(BreathPhase.Inhale, state.phase)
        expect(state.scale, BreathingSession.REST_SCALE, "the picker's cloud")
        assertEquals(0.0, BreathingSession.progress(t = 30.0, duration = 0.0), 0.0001)
    }

    /** A clock that stepped backwards must not produce a NaN scale in front
     * of a child. */
    @Test
    fun `negative time is clamped rather than producing nonsense`() {
        val state = BreathingSession.state(t = -5.0, duration = 120.0)
        assertFalse(state.scale.isNaN())
        assertTrue(state.scale >= BreathingSession.REST_SCALE - 0.0001)
        assertTrue(state.scale <= BreathingSession.PEAK_SCALE + 0.0001)
    }

    /**
     * Mutation proof: temporarily removed the `.coerceIn(0.0, 1.0)` clamp
     * from [BreathingSession.progress]. The over-run case (`t = 150,
     * duration = 120`) then returned 1.25 — which on screen is a progress
     * line wider than the phone — before the clamp was restored.
     */
    @Test
    fun `progress is 0 to 1 and never past it`() {
        expect(BreathingSession.progress(t = 0.0, duration = 120.0), 0.0, "at the start")
        expect(BreathingSession.progress(t = 60.0, duration = 120.0), 0.5, "halfway")
        expect(BreathingSession.progress(t = 150.0, duration = 120.0), 1.0, "past the end")
        expect(BreathingSession.progress(t = -10.0, duration = 120.0), 0.0, "before the start")
    }

    /** The durations a grown-up may pick. */
    @Test
    fun `the offered durations are 2, 5 and 10 minutes`() {
        assertEquals(listOf(2, 5, 10), BreathingSession.CHOICES)
    }
}
