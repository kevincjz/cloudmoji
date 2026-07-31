package app.cloudmoji.android.ui.parents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the exact outbound addresses — mirrors iOS `AboutViewTests`' pin on
 * `AboutView.supportEmail`/`supportURL`/`privacyURL`. A silent edit to either
 * string is a silent edit to what a parent's mail app or browser opens. */
class SupportLinksTest {

    @Test
    fun `support email matches the published Cloudmoji support address`() {
        assertEquals("kevin.chan@sproutlearn.co", SupportLinks.SUPPORT_EMAIL)
    }

    @Test
    fun `the mailto link carries the support address and a subject line`() {
        assertTrue(SupportLinks.SUPPORT_MAILTO.startsWith("mailto:${SupportLinks.SUPPORT_EMAIL}"))
        assertTrue(SupportLinks.SUPPORT_MAILTO.contains("subject="))
    }

    @Test
    fun `the privacy URL is the canonical cloudmoji-app policy page`() {
        assertEquals("https://cloudmoji.app/privacy", SupportLinks.PRIVACY_URL)
    }
}
