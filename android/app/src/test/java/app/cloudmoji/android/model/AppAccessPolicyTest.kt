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

    @Test
    fun `locked access resolves the effective language to English regardless of the preferred language`() {
        val policy = AppAccessPolicy(hasFullAccess = false)
        assertEquals(Language.English, policy.effectiveLanguage(preferred = Language.Japanese))
        assertEquals(Language.English, policy.effectiveLanguage(preferred = Language.English))
    }

    @Test
    fun `unlocked access keeps the preferred language`() {
        val policy = AppAccessPolicy(hasFullAccess = true)
        assertEquals(Language.Japanese, policy.effectiveLanguage(preferred = Language.Japanese))
    }

    @Test
    fun `locked access narrows allowed languages to English regardless of the enabled set`() {
        val policy = AppAccessPolicy(hasFullAccess = false)
        assertEquals(
            setOf(Language.English),
            policy.allowedLanguages(enabled = Language.entries.toSet()),
        )
    }

    @Test
    fun `unlocked access returns the enabled set unchanged`() {
        val policy = AppAccessPolicy(hasFullAccess = true)
        val enabled = setOf(Language.English, Language.Chinese)
        assertEquals(enabled, policy.allowedLanguages(enabled = enabled))
    }
}

