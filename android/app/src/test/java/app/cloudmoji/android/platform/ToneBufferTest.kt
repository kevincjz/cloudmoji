package app.cloudmoji.android.platform

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ported from `ios/Cloudmoji/CloudmojiTests/AudioTests.swift`'s
 * `ToneBufferTests` suite. Pure `FloatArray`/`Double` arithmetic — no
 * Compose runtime, no Android device — so unlike [AndroidToneEngine] this
 * actually executes here.
 */
class ToneBufferTest {

    private val sampleRate: Double = 44_100.0

    /**
     * Clipping is the one way this can be loudly wrong, and eight pads can
     * sound at once — so the headroom is the point, not just the ceiling.
     *
     * Mutation proof: temporarily set [ToneBuffer.peak] to `1.0f`. This test
     * failed (`peak 1.0 exceeds the 0.55 headroom`) before the constant was
     * restored.
     */
    @Test
    fun `the waveform peaks inside the headroom and never clips`() {
        val samples = ToneBuffer.samples(frequency = 440.0, sampleRate = sampleRate)
        assertTrue("the buffer is empty", samples.isNotEmpty())

        val peak = samples.maxOf { abs(it) }
        assertTrue("the buffer is silent", peak > 0f)
        assertTrue("the waveform clips at $peak", peak <= 1.0f)
        assertTrue(
            "peak $peak exceeds the ${ToneBuffer.peak} headroom — two pads at once would clip",
            peak <= ToneBuffer.peak + 0.001f,
        )
    }

    /**
     * A DC offset is inaudible on its own and thumps when eight buffers sum.
     * A symmetric triangle has a mean of zero.
     */
    @Test
    fun `the waveform carries no DC offset`() {
        val samples = ToneBuffer.samples(frequency = 440.0, sampleRate = sampleRate)
        val mean = samples.sum() / samples.size
        assertTrue("mean sample value is $mean", abs(mean) < 0.001f)
    }

    /**
     * A note decays. Without it a pad is a drone, and eight of them mashed
     * together never stop.
     *
     * Mutation proof: temporarily made [ToneBuffer.envelope] `return 1f`
     * after the attack. This test failed (the envelope did not fall between
     * successive steps) before the real decay curve was restored.
     */
    @Test
    fun `the envelope attacks quickly and then decays without stopping`() {
        assertEquals(0f, ToneBuffer.envelope(0.0), 0.0001f)
        assertTrue("the attack never reaches full", ToneBuffer.envelope(ToneBuffer.attack) > 0.99f)

        var previous = ToneBuffer.envelope(ToneBuffer.attack)
        var step = ToneBuffer.attack + 0.05
        while (step <= ToneBuffer.duration) {
            val value = ToneBuffer.envelope(step)
            assertTrue("the envelope did not fall between ${step - 0.05} and $step", value < previous)
            previous = value
            step += 0.05
        }
        assertTrue("the note is still at $previous of full when it ends", previous < 0.05f)
    }

    /**
     * The pitch is actually the pitch. A triangle crosses zero twice a
     * cycle, so A4 over 0.1s gives about 88 crossings.
     */
    @Test
    fun `A4 really is 440Hz`() {
        val window = (sampleRate * 0.1).toInt()
        val samples = ToneBuffer.samples(frequency = 440.0, sampleRate = sampleRate).take(window)

        var crossings = 0
        for (i in 1 until samples.size) {
            if ((samples[i - 1] < 0f) != (samples[i] < 0f)) crossings += 1
        }
        assertTrue("saw $crossings zero crossings, expected about 88", abs(crossings - 88) <= 2)
    }

    /** Every pad has a pitch, and they climb — a pentatonic run with a
     * repeat or an inversion in it is a keyboard where two keys do the same
     * thing. */
    @Test
    fun `the eight pitches are a rising pentatonic run`() {
        assertEquals(8, ToneBuffer.pitches.size)
        for (i in 0 until ToneBuffer.pitches.size - 1) {
            assertTrue(
                "${ToneBuffer.pitches[i + 1]} does not come after ${ToneBuffer.pitches[i]}",
                ToneBuffer.pitches[i + 1] > ToneBuffer.pitches[i],
            )
        }
        assertTrue("the fifth pad is not A4", abs(ToneBuffer.pitches[4] - 440.0) < 0.01)
    }

    /** Nonsense in, silence out — never a NaN or a trap in front of a
     * child. */
    @Test
    fun `a zero or negative frequency produces nothing rather than crashing`() {
        assertTrue(ToneBuffer.samples(frequency = 0.0, sampleRate = sampleRate).isEmpty())
        assertTrue(ToneBuffer.samples(frequency = -1.0, sampleRate = sampleRate).isEmpty())
        assertTrue(ToneBuffer.samples(frequency = 440.0, sampleRate = 0.0).isEmpty())
        assertTrue(ToneBuffer.samples(frequency = 440.0, sampleRate = sampleRate, duration = 0.0).isEmpty())
    }
}
