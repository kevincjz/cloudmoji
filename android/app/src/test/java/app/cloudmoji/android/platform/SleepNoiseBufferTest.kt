package app.cloudmoji.android.platform

import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ported from `ios/Cloudmoji/CloudmojiTests/AudioTests.swift`'s
 * `SleepNoiseBufferTests` suite. Pure `FloatArray`/`Double` arithmetic — no
 * Compose runtime, no Android device — so unlike [AndroidToneEngine] this
 * actually executes here.
 */
class SleepNoiseBufferTest {

    /**
     * The bedtime ambience is quiet, centred, and fades to silence at both
     * ends of the buffer — the property that makes
     * `AudioTrack.setLoopPoints` wrap without an audible click.
     *
     * Mutation proof: temporarily removed the `edge` factor from the final
     * `return` expression (`clamped * swell * max(0.0, edge)` ->
     * `clamped * swell`). This test failed (the loop's own first/last
     * samples were no longer near zero) before the edge fade was restored.
     */
    @Test
    fun `the bedtime ambience is quiet, centred and fades at the loop seam`() {
        val samples = SleepNoiseBuffer.samples(sampleRate = 4_000.0, duration = 2.0)
        assertTrue("the buffer is empty", samples.isNotEmpty())

        val peak = samples.maxOf { abs(it) }
        assertTrue("the sleep buffer is silent", peak > 0.005f)
        assertTrue(
            "the sleep buffer clips its own ${SleepNoiseBuffer.peak} headroom (peak was $peak)",
            peak <= SleepNoiseBuffer.peak + 0.001f,
        )

        val mean = samples.sum() / samples.size
        assertTrue("the sleep buffer carries a DC offset of $mean", abs(mean) < 0.01f)

        assertTrue("the loop starts with a click (${samples.first()})", abs(samples.first()) < 0.001f)
        assertTrue("the loop ends with a click (${samples.last()})", abs(samples.last()) < 0.001f)
    }

    /** Nonsense in, silence out — never a NaN or a trap in front of a
     * child. */
    @Test
    fun `invalid sleep-buffer inputs degrade to silence`() {
        assertTrue(SleepNoiseBuffer.samples(sampleRate = 0.0).isEmpty())
        assertTrue(SleepNoiseBuffer.samples(duration = 0.0).isEmpty())
        assertTrue(SleepNoiseBuffer.samples(sampleRate = -1.0).isEmpty())
    }

    /** A one- (or zero-) sample buffer cannot carry a swell or an edge
     * fade — [SleepNoiseBuffer.samples]' own `count > 1` guard. */
    @Test
    fun `a degenerate sample count also produces nothing`() {
        assertTrue(SleepNoiseBuffer.samples(sampleRate = 1.0, duration = 0.5).isEmpty())
    }
}
