package app.cloudmoji.android.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ScreenAwake] and [ScreenDimmer] — ported test-for-test from iOS
 * `SleepyCloudTests`'s "The screen" section
 * (`ios/Cloudmoji/CloudmojiTests/SleepyCloudTests.swift`). Pure classes with
 * injected read/write closures — no `android.view.View`, no `Window` — so
 * every case here runs on the JVM, and is exactly what the Task 13 brief's
 * "keep-screen-on flag set/cleared paths testable via state assertions"
 * requirement points at.
 */
class ScreenControlTest {

    /**
     * Hold and release are balanced and idempotent. A fresh composition and
     * a lifecycle callback can both arrive for one entry, and more than one
     * exit path calls [ScreenAwake.release].
     *
     * Mutation proof: temporarily removed the `if (isHeld) return` guard
     * from [ScreenAwake.hold]. The write log became `[true, true, false]`
     * instead of `[true, false]` before the guard was restored.
     */
    @Test
    fun `the screen is held awake once and given back once`() {
        val writes = mutableListOf<Boolean>()
        val awake = ScreenAwake(write = { writes += it })

        awake.hold()
        awake.hold()
        assertTrue(awake.isHeld)
        awake.release()
        awake.release()
        assertFalse(awake.isHeld)

        assertEquals(listOf(true, false), writes)
    }

    /**
     * **The leak this screen has to be trusted not to have.** A brightness
     * override that survives into the launcher, or another app entirely, is
     * a complaint nobody will trace back to a breathing exercise.
     *
     * Mutation proof: temporarily removed the `original = null` line at the
     * end of [ScreenDimmer.restore]. The second-restore assertion below (no
     * further write once already restored) failed before the clear was
     * restored.
     */
    @Test
    fun `brightness is restored exactly to where it was found`() {
        var current = 0.8f
        val writes = mutableListOf<Float>()
        val dimmer = ScreenDimmer(read = { current }, write = { value -> current = value; writes += value })

        // Nothing taken yet: restore must be a no-op, not a write of some
        // default. More than one exit path calls it and only one of them
        // ever dimmed.
        dimmer.restore()
        assertTrue("restoring before dimming wrote $writes", writes.isEmpty())
        assertFalse(dimmer.isDimmed)

        dimmer.dim(progress = 0.0)
        assertTrue(dimmer.isDimmed)
        assertEquals(0.8f, dimmer.original ?: -1f, 0.0001f)
        dimmer.dim(progress = 1.0)
        assertTrue("the screen never got darker", current < 0.8f)

        dimmer.restore()
        assertEquals("the screen came back at $current rather than 0.8", 0.8f, current, 0.0001f)
        assertFalse(dimmer.isDimmed)

        val writesAfterRestore = writes.size
        dimmer.restore()
        assertEquals("a second restore wrote again", writesAfterRestore, writes.size)
    }

    /**
     * The ramp itself: a fraction of where the screen started, never below
     * the floor, and never brighter than it was.
     *
     * Mutation proof: temporarily removed the `.coerceIn(0.0, 1.0)` clamp
     * from [ScreenDimmer.level]. A progress of 2.0 then returned a negative
     * brightness (which Android reads as an invalid/black override) before
     * the clamp was restored.
     */
    @Test
    fun `the dim ramp stays between the floor and where it started`() {
        assertEquals(1.0f, ScreenDimmer.level(original = 1.0f, progress = 0.0), 0.0001f)
        assertEquals(ScreenDimmer.FLOOR.toFloat(), ScreenDimmer.level(original = 1.0f, progress = 1.0), 0.0001f)

        for (progress in listOf(-1.0, 0.0, 0.3, 0.7, 1.0, 2.0)) {
            val level = ScreenDimmer.level(original = 0.6f, progress = progress)
            assertTrue("progress $progress brightened the screen to $level", level <= 0.6f + 0.0001f)
            assertTrue(
                "progress $progress took the screen to $level, under the floor",
                level >= 0.6f * ScreenDimmer.FLOOR.toFloat() - 0.0001f,
            )
        }
    }
}
