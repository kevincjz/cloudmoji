package app.cloudmoji.android.platform

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [audioFocusLossAction]'s table — see that file's own doc for the reasoning
 * behind each branch. Reads real `AudioManager` focus-change constants
 * directly: these are plain `public static final int` fields, not method
 * calls into the framework, so they resolve fine under this project's plain
 * JVM unit tests (no Robolectric configured — see `conventions.md`).
 */
class AudioFocusLossPolicyTest {

    /**
     * Mutation proof: temporarily changed the `STOP` branch to match only
     * `AudioManager.AUDIOFOCUS_LOSS` (dropping `AUDIOFOCUS_LOSS_TRANSIENT`).
     * This test failed on the transient case before the branch was restored.
     */
    @Test
    fun `a genuine loss of focus stops playback`() {
        assertEquals(AudioFocusLossAction.STOP, audioFocusLossAction(AudioManager.AUDIOFOCUS_LOSS))
        assertEquals(AudioFocusLossAction.STOP, audioFocusLossAction(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
    }

    @Test
    fun `a duckable transient loss is a no-op`() {
        assertEquals(
            AudioFocusLossAction.NONE,
            audioFocusLossAction(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK),
        )
    }

    /**
     * **Regaining focus resumes.** This case asserted `NONE` until Sleepy
     * Cloud existed, and the reasoning was sound while every sound in this
     * app was a ~1.2s blip recovered on the next tap. A ten-minute ambience
     * playing to a child who is deliberately not tapping anything has no
     * next tap, so a foreground focus blip would otherwise kill it for the
     * rest of the session — see [AudioFocusLossAction]'s own doc.
     *
     * Every member of the gain family, not just the plain one: `AudioManager`
     * documents the transient variants as deliverable to a listener too, and
     * a focus return that this table did not recognise would fall through to
     * `NONE` and lose the ambience just as silently.
     *
     * Mutation proof: temporarily narrowed the `RESUME` branch to
     * `AUDIOFOCUS_GAIN` alone. This test failed on the three transient
     * variants before the branch was restored.
     */
    @Test
    fun `every way focus comes back resumes what still wants to play`() {
        for (
        gain in listOf(
            AudioManager.AUDIOFOCUS_GAIN,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
        )
        ) {
            assertEquals("focus change $gain", AudioFocusLossAction.RESUME, audioFocusLossAction(gain))
        }
    }

    @Test
    fun `an unrecognised code degrades to a no-op rather than stopping playback by accident`() {
        assertEquals(AudioFocusLossAction.NONE, audioFocusLossAction(9999))
    }
}
