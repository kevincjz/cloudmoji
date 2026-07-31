package app.cloudmoji.android.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAccessPolicyTest {
    @Test
    fun `locked app exposes only Words and Count`() {
        val apps = AppAccessPolicy(hasFullAccess = false).visibleMiniApps()

        assertEquals(listOf(MiniApp.Words, MiniApp.Count), apps)
    }

    @Test
    fun `full app exposes all seven mini apps`() {
        val apps = AppAccessPolicy(hasFullAccess = true).visibleMiniApps()

        assertEquals(7, apps.size)
        assertEquals(MiniApp.entries, apps)
    }

    @Test
    fun `animals setting hides only Animals`() {
        val apps = AppAccessPolicy(hasFullAccess = true)
            .visibleMiniApps(animalsEnabled = false)

        assertFalse(MiniApp.Animals in apps)
        assertTrue(MiniApp.Photos in apps)
        assertEquals(6, apps.size)
    }
}

