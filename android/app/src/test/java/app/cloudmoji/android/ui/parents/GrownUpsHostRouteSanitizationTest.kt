package app.cloudmoji.android.ui.parents

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [sanitizeRestoredDestination] — the belt-and-braces half of the same
 * reviewer-caught gate-bypass fix as `app.cloudmoji.android.RouteSanitizationTest`.
 * See this function's own doc for why it exists independently of
 * `sanitizeRestoredRoute` rather than relying on that fix alone.
 */
class GrownUpsHostRouteSanitizationTest {

    @Test
    fun `a restored panel destination passes through unchanged`() {
        assertEquals(DestinationPanel, sanitizeRestoredDestination(DestinationPanel))
    }

    @Test
    fun `a restored About destination is coerced back to the panel`() {
        assertEquals(DestinationPanel, sanitizeRestoredDestination(DestinationAbout))
    }

    @Test
    fun `a restored Full Cloudmoji destination is coerced back to the panel`() {
        assertEquals(DestinationPanel, sanitizeRestoredDestination(DestinationFullCloudmoji))
    }

    @Test
    fun `a restored Tutorial destination is coerced back to the panel`() {
        assertEquals(DestinationPanel, sanitizeRestoredDestination(DestinationTutorial))
    }

    @Test
    fun `an unrecognised destination is also coerced back to the panel`() {
        // Unlike sanitizeRestoredRoute (which only special-cases the one
        // dangerous value and passes everything else through), this
        // function's safe default is "panel" for anything that is not
        // already exactly that — there is no legitimate sub-screen this
        // composable should ever restore straight into.
        assertEquals(DestinationPanel, sanitizeRestoredDestination("garbage"))
    }
}
