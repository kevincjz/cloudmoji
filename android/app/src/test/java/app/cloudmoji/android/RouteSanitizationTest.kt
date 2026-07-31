package app.cloudmoji.android

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [sanitizeRestoredRoute] is the fix for a reviewer-caught gate bypass: a
 * `route` restored from saved-instance state (which Compose's
 * `rememberSaveable` populates identically for a config-change recreation
 * and for Android recreating the Activity from a killed-process relaunch)
 * must never resurrect [ParentRoute] without a fresh trip through the
 * arithmetic gate. See `sanitizeRestoredRoute`'s own doc in `CloudmojiApp.kt`
 * for the full reasoning, and `CloudmojiApp.kt`'s `RouteSaver` for where this
 * is actually wired into `rememberSaveable`.
 */
class RouteSanitizationTest {

    @Test
    fun `a restored parent route is coerced back to the launcher`() {
        assertEquals(LauncherRoute, sanitizeRestoredRoute(ParentRoute))
    }

    @Test
    fun `a restored launcher route passes through unchanged`() {
        assertEquals(LauncherRoute, sanitizeRestoredRoute(LauncherRoute))
    }

    @Test
    fun `a restored mini-app route passes through unchanged`() {
        // Mini-app routes carry no parent-only controls, settings, or
        // outbound links, so restoring straight into one is not the bug this
        // guards against — only ParentRoute is.
        assertEquals("words", sanitizeRestoredRoute("words"))
        assertEquals("count", sanitizeRestoredRoute("count"))
        assertEquals("sleepy", sanitizeRestoredRoute("sleepy"))
    }

    @Test
    fun `an unrecognised value passes through unchanged rather than being guessed at`() {
        assertEquals("garbage", sanitizeRestoredRoute("garbage"))
    }
}
