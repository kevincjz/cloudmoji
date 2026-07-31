package app.cloudmoji.android.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ports iOS `CameraLifecycleTests.debounceRefusesRapidPresses` /
 * `debounceIsShort`.
 *
 * **The white-out.** iOS's own comment records what this guards: a debounced
 * capture never calls its completion, so a caller that raised a white flash
 * before asking had nothing coming to lower it — the viewfinder went white and
 * stayed white until the mini-app was closed. The debounce answering
 * truthfully is what lets `CameraScreen` ask first and light the flash second.
 * (Android's flash also lowers itself after a fixed beat, so the same bug has
 * two independent guards here — see `CameraScreen`'s own doc.)
 */
class CaptureDebounceTest {

    private val first = 10_000L

    /** Mutation: return `true` unconditionally from `accepts`. The two
     * within-the-window cases fail. */
    @Test
    fun aSecondPressInsideTheWindowIsRefused() {
        assertTrue(
            "the very first press must always be accepted",
            CaptureDebounce.accepts(nowMillis = first, lastCaptureAtMillis = null),
        )
        assertFalse(
            "a press 200ms later was accepted — a toddler drums far faster than that",
            CaptureDebounce.accepts(nowMillis = first + 200, lastCaptureAtMillis = first),
        )
        assertFalse(
            CaptureDebounce.accepts(
                nowMillis = first + CaptureDebounce.WINDOW_MS - 1,
                lastCaptureAtMillis = first,
            ),
        )
    }

    /** Mutation: make the comparison `>` a `<`. This fails — and so would a
     * window that never reopens, which is a shutter that works exactly once. */
    @Test
    fun theWindowReopens() {
        assertTrue(
            CaptureDebounce.accepts(
                nowMillis = first + CaptureDebounce.WINDOW_MS,
                lastCaptureAtMillis = first,
            ),
        )
        assertTrue(
            CaptureDebounce.accepts(
                nowMillis = first + CaptureDebounce.WINDOW_MS * 4,
                lastCaptureAtMillis = first,
            ),
        )
    }

    /** The debounce is about a held finger, not about making the camera feel
     * slow. Anything much past a second and a parent taking two pictures of a
     * moving child loses the second one. iOS asserts the same bounds. */
    @Test
    fun theWindowIsAboutASecond() {
        assertTrue(CaptureDebounce.WINDOW_MS >= 500L)
        assertTrue(CaptureDebounce.WINDOW_MS <= 1_500L)
    }
}
