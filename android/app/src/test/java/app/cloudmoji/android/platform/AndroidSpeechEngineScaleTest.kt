package app.cloudmoji.android.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `AndroidSpeechEngine` itself cannot be constructed in a JVM unit test —
 * `android.speech.tts.TextToSpeech` binds to a real system service and
 * throws outside an Android runtime, and this project has no Robolectric.
 * See the Task 4 report for the full list of what that leaves untested.
 *
 * [ANDROID_UTTERANCE_RATE]/[ANDROID_UTTERANCE_PITCH] are file-scope
 * constants specifically so this one claim — the platform-scale conversion
 * `AndroidSpeechEngine` documents — stays host-testable: unlike iOS's
 * `AVSpeechUtterance.rate` (0...1, 0.5 normal), `TextToSpeech`'s rate/pitch
 * scale is already 1.0-is-normal, so `SpeechController.RATE`/`PITCH` carry
 * across unconverted. This test is the assertion of that claim, not just the
 * arithmetic restated — see the fail-then-pass proof in the Task 4 report
 * for how a reintroduced (wrong) conversion factor would be caught here.
 */
class AndroidSpeechEngineScaleTest {
    @Test
    fun `Android's rate and pitch scale match SpeechController's unconverted`() {
        assertEquals(SpeechController.RATE, ANDROID_UTTERANCE_RATE, 0f)
        assertEquals(SpeechController.PITCH, ANDROID_UTTERANCE_PITCH, 0f)
    }

    @Test
    fun `the converted rate is still slower than normal, matching the product intent`() {
        // 1.0 is TextToSpeech's normal rate; the product wants noticeably
        // slower than that (15%), not faster and not a no-op.
        assertTrue(ANDROID_UTTERANCE_RATE < 1.0f)
        assertTrue(ANDROID_UTTERANCE_RATE > 0.7f)
    }
}
