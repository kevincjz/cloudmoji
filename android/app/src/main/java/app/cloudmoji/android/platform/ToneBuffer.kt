package app.cloudmoji.android.platform

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * One note, rendered to samples. Ported from iOS `ToneBuffer.swift`.
 *
 * Pure arithmetic, deliberately kept away from `android.media.AudioTrack`:
 * what a tone sounds like is a judgement for the ear, but whether it clips,
 * whether it decays, whether it carries a DC offset, and whether it is
 * actually the pitch it claims to be are all facts that can be checked
 * without a speaker — and all four are silently wrong in a way no screenshot
 * shows. [AndroidToneEngine] is the half that actually touches the platform,
 * and is not host-testable for exactly the reason this object is kept
 * separate from it.
 *
 * A triangle wave rather than a sine: a sine at these frequencies reads as a
 * test tone, and a square is harsh at a toddler's listening distance.
 * Triangle is the compromise a toy xylophone actually makes.
 */
object ToneBuffer {

    /** Matches `AudioTrack`'s own output format, so no resampling happens
     * between here and the speaker. */
    const val sampleRate: Double = 44_100.0

    /** Long enough for the tail to be audible under the next tap, short
     * enough that eight of them mashed at once do not turn into a drone. */
    const val duration: Double = 1.2

    /** Headroom. Eight pads can sound together — separate `AudioTrack`s mean
     * a toddler with two hands gets chords for free — and a peak of 1.0 each
     * would clip the moment two of them overlapped. */
    const val peak: Float = 0.55f

    /** A fast attack, but not instant: a step from silence to full amplitude
     * is a click, and the click is the loudest thing in the sound. */
    const val attack: Double = 0.006

    /** The exponential decay constant. Chosen so the tone is at about 1% of
     * peak by the end of [duration] — audibly finished rather than cut off. */
    const val decay: Double = 3.8

    /**
     * The eight pads, a C-major pentatonic run.
     *
     * Pentatonic because there is no wrong note in it: `CLAUDE.md` rule 4
     * says there are no failure states, and a chromatic or full major layout
     * lets a child mash two adjacent pads and hear a semitone clash he will
     * read as a mistake. Any two of these eight sound intentional together.
     */
    val pitches: List<Double> = listOf(
        261.63, // C4
        293.66, // D4
        329.63, // E4
        392.00, // G4
        440.00, // A4
        523.25, // C5
        587.33, // D5
        659.26, // E5
    )

    /**
     * The waveform, as mono float samples in -1...1.
     *
     * Separate from [AndroidToneEngine] so the arithmetic can be tested
     * without constructing an `AudioTrack`, which needs a device's audio
     * stack.
     */
    fun samples(
        frequency: Double,
        sampleRate: Double = ToneBuffer.sampleRate,
        duration: Double = ToneBuffer.duration,
    ): FloatArray {
        // A frequency of zero or less is not a note. It cannot arrive from
        // `pitches`, but it must not produce NaNs if it ever did.
        if (frequency <= 0 || sampleRate <= 0 || duration <= 0) return FloatArray(0)

        val count = (sampleRate * duration).toInt()
        val out = FloatArray(count)
        val cyclesPerSample = frequency / sampleRate

        for (i in 0 until count) {
            val t = i / sampleRate
            // Phase within one cycle, 0...1. `i * cyclesPerSample` is always
            // non-negative, so `%` here is the same as Swift's
            // `truncatingRemainder(dividingBy: 1)`.
            val phase = (i * cyclesPerSample) % 1.0
            // A symmetric triangle: -1 at the start of a cycle, +1 halfway.
            // Symmetric is what makes the mean zero, which is what keeps a DC
            // offset out of the mix — eight offset buffers played together
            // would otherwise sum into a thump on every chord.
            val triangle = 1 - 4 * abs(phase - 0.5)
            out[i] = (triangle * envelope(t) * peak).toFloat()
        }
        return out
    }

    /**
     * Attack then exponential decay, 0...1. Strictly decreasing after the
     * attack, which is the property that makes this a note rather than a
     * beep.
     *
     * [duration] is accepted for signature parity with iOS's
     * `envelope(at:duration:)` (and so a caller/test can be explicit about
     * it), but — exactly like the iOS original — it is not read by this
     * function's own math: the decay curve's shape does not depend on how
     * long the caller asked for.
     */
    fun envelope(t: Double, duration: Double = ToneBuffer.duration): Float {
        if (t < 0) return 0f
        if (t < attack) return (t / attack).toFloat()
        return exp(-decay * (t - attack)).toFloat()
    }
}

/**
 * A quiet, deterministic wash for Sleepy Cloud. Ported from iOS
 * `SleepNoiseBuffer` (`ToneBuffer.swift`).
 *
 * This is deliberately synthesized rather than downloaded: there is no
 * recording to license, no network dependency, and no personalisation or
 * tracking. Two low-pass stages turn deterministic white noise into a soft
 * rain/ocean texture. The ends fade to silence, so the looping buffer
 * cannot click at its seam — see [SleepNoiseBufferTest].
 *
 * Kept beside [ToneBuffer] in the same file, mirroring the iOS original's
 * own layout (`ToneBuffer.swift` holds both types): the two are the pure-JVM
 * arithmetic half of this app's synthesized audio, separate from
 * [AndroidToneEngine], which is the half that actually touches
 * `android.media.AudioTrack` and is not host-testable for exactly that
 * reason.
 */
object SleepNoiseBuffer {
    const val duration: Double = 10.0
    const val peak: Float = 0.16f
    const val edgeFade: Double = 0.75

    /**
     * The waveform, as mono float samples in -1...1, meant to be looped.
     *
     * The generator is a fixed-seed linear congruential generator (an `LCG`,
     * the same "deterministic pseudo-random" trick `UInt64`-seeded on iOS),
     * not `kotlin.random.Random`: a fixed seed is what makes the ambience
     * stable between launches and tests, and Kotlin's own `Random` carries
     * no cross-platform seeding guarantee `ToneBuffer`'s determinism
     * requirement could rely on the way this hand-rolled generator's exact
     * bit arithmetic can.
     */
    fun samples(
        sampleRate: Double = ToneBuffer.sampleRate,
        duration: Double = SleepNoiseBuffer.duration,
    ): FloatArray {
        if (sampleRate <= 0 || duration <= 0) return FloatArray(0)

        val count = (sampleRate * duration).toInt()
        if (count <= 1) return FloatArray(0)

        // The high bits of this LCG have the useful distribution. Kotlin's
        // unsigned arithmetic wraps silently on overflow, the same as
        // Swift's `&*`/`&+` this is ported from — no explicit wraparound
        // operator is needed here the way Swift's is.
        var seed: ULong = 0xC10D_5EED_2026uL
        var soft = 0.0
        var deep = 0.0
        val texture = DoubleArray(count)

        for (index in 0 until count) {
            seed = seed * 6_364_136_223_846_793_005uL + 1uL
            val unit = ((seed shr 40) and 0xFF_FFFFuL).toDouble() / 0xFF_FFFF.toDouble()
            val white = unit * 2 - 1

            soft = soft * 0.94 + white * 0.06
            deep = deep * 0.992 + white * 0.008
            texture[index] = soft * 0.82 + deep * 0.48
        }

        val mean = texture.sum() / texture.size
        val out = FloatArray(count)
        for (index in 0 until count) {
            val t = index / sampleRate
            // One slow swell per buffer. Because it is periodic, the
            // ambience breathes without an abrupt volume change when the
            // loop restarts.
            val swell = 0.76 + 0.16 * sin((2 * PI * t / duration) - (PI / 2))
            val edge = minOf(1.0, minOf(t / edgeFade, (duration - t) / edgeFade))
            val centred = (texture[index] - mean) * 2.6
            val clamped = centred.coerceIn(-1.0, 1.0)
            out[index] = (clamped * swell * maxOf(0.0, edge)).toFloat() * peak
        }
        return out
    }
}
