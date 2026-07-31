package app.cloudmoji.android.data

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.cloudmoji.android.model.Category
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SettingsCodec] against hand-built [androidx.datastore.preferences.core.Preferences]
 * snapshots -- no DataStore file, no `Context`. Ported from the `init`-shaped
 * cases in
 * `ios/CloudmojiCore/Tests/CloudmojiCoreTests/SettingsStoreTests.swift`
 * (iOS's `UserDefaults` read path is this file's direct analogue).
 */
class SettingsCodecTest {

    @Test
    fun `an empty store decodes to fresh-install defaults`() {
        assertEquals(Settings.default(), SettingsCodec.decode(emptyPreferences()))
    }

    @Test
    fun `an unknown stored language recovers to English`() {
        // "es" was never a Cloudmoji language. On the web this exact value
        // reached NUMBER_WORDS[lang][n-1] and crashed Count mode on first tap.
        val prefs = mutablePreferencesOf(SettingsKeys.language to "es")
        assertEquals(Language.English, SettingsCodec.decode(prefs).language)
    }

    @Test
    fun `a stored language is honoured`() {
        val prefs = mutablePreferencesOf(SettingsKeys.language to "ja")
        assertEquals(Language.Japanese, SettingsCodec.decode(prefs).language)
    }

    @Test
    fun `garbage in the enabled-languages set is filtered out`() {
        val prefs = mutablePreferencesOf(SettingsKeys.enabledLanguages to setOf("en", "th", "zh"))
        assertEquals(setOf(Language.English, Language.Chinese), SettingsCodec.decode(prefs).enabledLanguages)
    }

    @Test
    fun `an explicitly empty enabled-languages set decodes to every language`() {
        val prefs = mutablePreferencesOf(SettingsKeys.enabledLanguages to emptySet())
        assertEquals(Language.entries.toSet(), SettingsCodec.decode(prefs).enabledLanguages)
    }

    @Test
    fun `an explicitly empty enabled-categories set decodes to every category`() {
        val prefs = mutablePreferencesOf(SettingsKeys.enabledCategories to emptySet())
        assertEquals(Category.entries.toSet(), SettingsCodec.decode(prefs).enabledCategories)
    }

    @Test
    fun `the active language is forced back into the enabled set`() {
        // Disabling the active language would leave the picker with no valid
        // selection, so it recovers rather than showing an impossible state.
        val prefs = mutablePreferencesOf(
            SettingsKeys.language to "ja",
            SettingsKeys.enabledLanguages to setOf("en", "zh"),
        )
        assertEquals(Language.English, SettingsCodec.decode(prefs).language)
    }

    @Test
    fun `with English disabled, an invalid stored language still recovers into the enabled set`() {
        val prefs = mutablePreferencesOf(
            SettingsKeys.enabledLanguages to setOf("ja", "zh"),
            // "es" was never a Cloudmoji language -- same corrupt value as
            // above, but here English itself is disabled. Recovering to
            // English unconditionally would land outside enabledLanguages,
            // exactly the inconsistent state this invariant prevents.
            SettingsKeys.language to "es",
        )
        val decoded = SettingsCodec.decode(prefs)
        assertTrue(decoded.language in decoded.enabledLanguages)
        assertEquals(Language.Japanese, decoded.language)
    }

    @Test
    fun `with English disabled, a valid stored language is kept`() {
        val prefs = mutablePreferencesOf(
            SettingsKeys.enabledLanguages to setOf("ja", "zh"),
            SettingsKeys.language to "zh",
        )
        assertEquals(Language.Chinese, SettingsCodec.decode(prefs).language)
    }

    @Test
    fun `an inverted or out-of-bounds count range is clamped on read`() {
        val inverted = mutablePreferencesOf(SettingsKeys.countLower to 9, SettingsKeys.countUpper to 2)
        assertEquals(2..9, SettingsCodec.decode(inverted).countRange)

        val outOfBounds = mutablePreferencesOf(SettingsKeys.countLower to 0, SettingsKeys.countUpper to 99)
        assertEquals(2..10, SettingsCodec.decode(outOfBounds).countRange)
    }

    @Test
    fun `a single-value range stored directly is honoured on read`() {
        val prefs = mutablePreferencesOf(SettingsKeys.countLower to 5, SettingsKeys.countUpper to 5)
        assertEquals(5..5, SettingsCodec.decode(prefs).countRange)
    }

    @Test
    fun `a count range missing either bound falls back to the default`() {
        val onlyLower = mutablePreferencesOf(SettingsKeys.countLower to 5)
        assertEquals(Settings.defaultCountRange, SettingsCodec.decode(onlyLower).countRange)
    }

    @Test
    fun `muted and seenTutorial default to false and are read back verbatim`() {
        assertEquals(false, SettingsCodec.decode(emptyPreferences()).muted)
        assertEquals(false, SettingsCodec.decode(emptyPreferences()).seenTutorial)

        val prefs = mutablePreferencesOf(SettingsKeys.muted to true, SettingsKeys.seenTutorial to true)
        val decoded = SettingsCodec.decode(prefs)
        assertEquals(true, decoded.muted)
        assertEquals(true, decoded.seenTutorial)
    }
}
