package app.cloudmoji.android.platform

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Thin binding of [HapticFeedback] to `android.os.Vibrator`, using the
 * one-shot/waveform API available from this app's `minSdk` (26) onward — no
 * `VibratorManager` branch is needed for that reason.
 *
 * Not host-testable — it drives real hardware — which is why it carries no
 * logic beyond picking an amplitude/timing pattern; see [HapticFeedback]'s
 * doc for what each pattern means.
 */
class AndroidHapticFeedback(context: Context) : HapticFeedback {
    private val vibrator = context.applicationContext.getSystemService(Vibrator::class.java)

    override fun tap() {
        vibrate(VibrationEffect.createOneShot(TAP_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun reward() {
        // Two short pulses — an Android analogue of iOS's
        // `UINotificationFeedbackGenerator(.success)`, distinct enough from
        // the single [tap] knock that a child can feel the difference.
        vibrate(VibrationEffect.createWaveform(REWARD_TIMINGS_MS, REWARD_AMPLITUDES, NO_REPEAT))
    }

    private fun vibrate(effect: VibrationEffect) {
        vibrator?.vibrate(effect)
    }

    companion object {
        private const val TAP_DURATION_MS = 20L
        private const val NO_REPEAT = -1
        private val REWARD_TIMINGS_MS = longArrayOf(0, 40, 60, 60)
        private val REWARD_AMPLITUDES = intArrayOf(0, 255, 0, 200)
    }
}
