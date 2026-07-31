package app.cloudmoji.android.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ported from the invariant-shaped cases in
 * `ios/CloudmojiCore/Tests/CloudmojiCoreTests/SettingsStoreTests.swift`.
 * Persistence itself (survives a reload, the exact `UserDefaults`/DataStore
 * key names) is covered on Android by `SettingsRepositoryTest`, since that is
 * where iOS's `UserDefaults` role sits here.
 */
class SettingsTest {

    @Test
    fun `fresh install starts in English with everything enabled`() {
        val settings = Settings.default()
        assertEquals(Language.English, settings.language)
        assertEquals(Language.entries.toSet(), settings.enabledLanguages)
        assertEquals(Category.entries.toSet(), settings.enabledCategories)
        assertEquals(2..9, settings.countRange)
        assertEquals(false, settings.muted)
        assertEquals(false, settings.seenTutorial)
    }

    @Test
    fun `an unknown stored language recovers to English`() {
        // "es" was never a Cloudmoji language. On the web this exact value
        // reached NUMBER_WORDS[lang][n-1] and crashed Count mode on first tap.
        val resolved = Settings.resolveLanguage(preferring = null, enabled = Language.entries.toSet())
        assertEquals(Language.English, resolved)
    }

    @Test
    fun `garbage in the enabled-languages set is filtered out`() {
        val stored = setOf("en", "th", "zh").mapNotNull(Language::fromCode).toSet()
        assertEquals(setOf(Language.English, Language.Chinese), Settings.cleanedLanguages(stored))
    }

    @Test
    fun `the enabled set is never empty`() {
        assertEquals(Language.entries.toSet(), Settings.cleanedLanguages(emptySet()))
        assertEquals(Category.entries.toSet(), Settings.cleanedCategories(emptySet()))
    }

    @Test
    fun `the active language is forced back into the enabled set`() {
        // Disabling the active language would leave the picker with no valid
        // selection, so it recovers rather than showing an impossible state.
        val resolved = Settings.resolveLanguage(
            preferring = Language.Japanese,
            enabled = setOf(Language.English, Language.Chinese),
        )
        assertEquals(Language.English, resolved)
    }

    @Test
    fun `setting language to a disabled language recovers rather than persisting it`() {
        // .tl is not enabled -- this must recover, not stick.
        val resolved = Settings.resolveLanguage(
            preferring = Language.Tagalog,
            enabled = setOf(Language.English, Language.Chinese),
        )
        assertEquals(Language.English, resolved)
    }

    @Test
    fun `setting language to an enabled language still works normally`() {
        val resolved = Settings.resolveLanguage(
            preferring = Language.Chinese,
            enabled = setOf(Language.English, Language.Chinese),
        )
        assertEquals(Language.Chinese, resolved)
    }

    @Test
    fun `with English disabled, an invalid stored language still recovers into the enabled set`() {
        // "es" was never a Cloudmoji language -- same corrupt value as the
        // "unknown stored language" case above, but here English itself is
        // disabled. Recovering to English unconditionally (the bug iOS's
        // `init` used to have) would land outside `enabled`, exactly the
        // inconsistent state this invariant exists to prevent.
        val enabled = setOf(Language.Japanese, Language.Chinese)
        val resolved = Settings.resolveLanguage(preferring = null, enabled = enabled)
        assertTrue(resolved in enabled)
        assertEquals(Language.Japanese, resolved)
    }

    @Test
    fun `with English disabled, a valid stored language is kept`() {
        val resolved = Settings.resolveLanguage(
            preferring = Language.Chinese,
            enabled = setOf(Language.Japanese, Language.Chinese),
        )
        assertEquals(Language.Chinese, resolved)
    }

    @Test
    fun `with English disabled, recovery lands on the alphabetically-first enabled language`() {
        // .tl is not enabled, and neither is .en -- recovery must land on
        // the alphabetically-first enabled language (ja), not a hardcoded
        // "en".
        val resolved = Settings.resolveLanguage(
            preferring = Language.Tagalog,
            enabled = setOf(Language.Japanese, Language.Chinese),
        )
        assertEquals(Language.Japanese, resolved)
    }

    @Test
    fun `recovery from an invalid language assignment always lands inside the enabled set`() {
        val enabled = setOf(Language.Japanese, Language.Chinese)
        val resolved = Settings.resolveLanguage(preferring = Language.English, enabled = enabled)
        assertTrue(resolved in enabled)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `resolveLanguage requires a non-empty enabled set`() {
        Settings.resolveLanguage(preferring = Language.English, enabled = emptySet())
    }

    @Test
    fun `an inverted or out-of-bounds count range is clamped`() {
        assertEquals(2..9, Settings.clampedRange(lower = 9, upper = 2))
        assertEquals(2..10, Settings.clampedRange(lower = 0, upper = 99))
    }

    @Test
    fun `assigning an out-of-bounds count range clamps it rather than persisting it verbatim`() {
        // Prior to the iOS fix this mirrors, `countRange`'s didSet persisted
        // whatever it was handed -- unlike language/enabledLanguages, it did
        // not self-heal.
        assertEquals(2..10, Settings.clampedRange(lower = 0, upper = 99))
    }

    @Test
    fun `assigning a range entirely above countBounds recovers to the default`() {
        assertEquals(2..9, Settings.clampedRange(lower = 15, upper = 20))
    }

    @Test
    fun `a single-value count range is accepted`() {
        // A parent choosing "exactly 3" is a real setting, not an error.
        assertEquals(3..3, Settings.clampedRange(lower = 3, upper = 3))
    }
}
