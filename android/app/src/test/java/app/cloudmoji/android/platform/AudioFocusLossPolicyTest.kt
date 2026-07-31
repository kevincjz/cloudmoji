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
    fun `a duckable transient loss and a regained focus are both no-ops`() {
        assertEquals(
            AudioFocusLossAction.NONE,
            audioFocusLossAction(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK),
        )
        assertEquals(AudioFocusLossAction.NONE, audioFocusLossAction(AudioManager.AUDIOFOCUS_GAIN))
    }

    @Test
    fun `an unrecognised code degrades to a no-op rather than stopping playback by accident`() {
        assertEquals(AudioFocusLossAction.NONE, audioFocusLossAction(9999))
    }
}
