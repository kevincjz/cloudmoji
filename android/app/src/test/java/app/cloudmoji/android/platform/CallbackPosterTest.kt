package app.cloudmoji.android.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `AndroidMainThreadPoster` (the production [CallbackPoster]) binds to a
 * real `Looper` and is not host-testable, same reasoning as everywhere else
 * in `platform/` that touches Android directly. [InlineCallbackPoster] is
 * the seam that IS host-testable — and the one `SpeechControllerTest`'s
 * fakes rely on being a faithful "runs synchronously, no threading" stand-in
 * whenever a future test wants to exercise `AndroidSpeechEngine`-adjacent
 * code without a real thread hop.
 */
class CallbackPosterTest {
    @Test
    fun `InlineCallbackPoster runs the action synchronously on the calling thread`() {
        var ran = false
        var sameThread = false
        val callingThread = Thread.currentThread()

        InlineCallbackPoster.post {
            ran = true
            sameThread = Thread.currentThread() == callingThread
        }

        assertTrue("the action must have run before post() returns", ran)
        assertTrue("no thread hop for the inline poster", sameThread)
    }

    @Test
    fun `InlineCallbackPoster runs the action exactly once per post`() {
        var count = 0
        InlineCallbackPoster.post { count += 1 }
        InlineCallbackPoster.post { count += 1 }
        assertEquals(2, count)
    }
}
