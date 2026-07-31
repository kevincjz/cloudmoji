package app.cloudmoji.android.model

import kotlin.math.PI
import kotlin.math.cos

/**
 * Where the cloud is in one breath. Ported from iOS `BreathPhase`
 * (`Views/Sleepy/BreathingCloud.swift`).
 *
 * Its own type rather than a [MascotMood] case, for the same reason iOS's
 * doc gives: a sleeping face is one screen's animation state, not product
 * law about which mood outranks which, and putting it in the shared enum
 * would make every exhaustive `when` on [MascotMood] elsewhere in this app
 * grow a case it can never see.
 */
enum class BreathPhase {
    Inhale, Hold, Exhale,

    /** The session is over. The cloud stops moving and the loop stops
     * running. */
    Asleep,
}

/** The cloud's scale and which phase it is in, at one instant. Ported from
 * iOS `BreathingSession.state(at:duration:)`'s return tuple. */
data class BreathState(val scale: Double, val phase: BreathPhase)

/**
 * The breathing itself: timings, easing, and where the scale is at time *t*.
 * Ported from iOS `BreathingSession` (`Views/Sleepy/BreathingCloud.swift`).
 *
 * Pure and stateless, because it is the part of this screen that can be
 * silently wrong. How the cloud *looks* asleep is a judgement for the eye;
 * whether a four-second inhale actually takes four seconds is arithmetic,
 * and arithmetic that drifted by a beat would still look plausible on a
 * screenshot — see `BreathingSessionTest`.
 *
 * Timings are `reference/breathing-cloud.jsx` verbatim, via iOS: 4s in, 2s
 * hold, 6s out.
 */
object BreathingSession {
    const val INHALE_SECONDS: Double = 4.0
    const val HOLD_SECONDS: Double = 2.0
    const val EXHALE_SECONDS: Double = 6.0
    val CYCLE_SECONDS: Double = INHALE_SECONDS + HOLD_SECONDS + EXHALE_SECONDS

    /** `scale(0.75)` at the bottom of an exhale, `scale(1.1)` at the top of
     * an inhale, and a little smaller than either once asleep. */
    const val REST_SCALE: Double = 0.75
    const val PEAK_SCALE: Double = 1.10
    const val ASLEEP_SCALE: Double = 0.72

    /** The durations a grown-up may pick, in minutes. */
    val CHOICES: List<Int> = listOf(2, 5, 10)

    /**
     * Where the cloud is at [t] seconds into a session of [duration]
     * seconds.
     *
     * `duration <= 0` means "no session picked yet", and returns the
     * resting pose rather than declaring the session instantly over. iOS's
     * picker draws its still cloud through this same function; Android's
     * `SleepyCloudScreen` gives the picker a fixed `Hold` pose directly
     * instead, but the contract is kept because [SleepySessionState]
     * delegates straight to here, and a fresh session (no `begin` yet) must
     * report "resting", not "already finished".
     */
    fun state(t: Double, duration: Double): BreathState {
        if (duration <= 0) return BreathState(REST_SCALE, BreathPhase.Inhale)
        if (t < duration) {
            // Negative time cannot happen in production — the caller
            // measures forward from a start instant — but a clock that
            // stepped backwards must not produce a scale of NaN in front of
            // a child who is trying to fall asleep.
            val p = maxOf(0.0, t) % CYCLE_SECONDS

            if (p < INHALE_SECONDS) {
                val scale = REST_SCALE + eased(p / INHALE_SECONDS) * (PEAK_SCALE - REST_SCALE)
                return BreathState(scale, BreathPhase.Inhale)
            }
            if (p < INHALE_SECONDS + HOLD_SECONDS) {
                return BreathState(PEAK_SCALE, BreathPhase.Hold)
            }
            val k = (p - INHALE_SECONDS - HOLD_SECONDS) / EXHALE_SECONDS
            val scale = PEAK_SCALE - eased(k) * (PEAK_SCALE - REST_SCALE)
            return BreathState(scale, BreathPhase.Exhale)
        }
        return BreathState(ASLEEP_SCALE, BreathPhase.Asleep)
    }

    /** The prototype's `0.5 - 0.5 * cos(pi * k)` — an ease-in-out over
     * 0...1. Linear breathing reads as a machine; this is what makes it
     * read as a chest. */
    private fun eased(k: Double): Double = 0.5 - 0.5 * cos(PI * k)

    /** How far through the session, 0...1. Drives both the dimming and the
     * progress line. */
    fun progress(t: Double, duration: Double): Double {
        if (duration <= 0) return 0.0
        return (t / duration).coerceIn(0.0, 1.0)
    }
}
