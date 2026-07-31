package app.cloudmoji.android.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cloudmoji.android.model.Category
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.Settings
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [SettingsRepository] against a real Preferences DataStore backed by a temp
 * file -- `PreferenceDataStoreFactory.create` only needs a [java.io.File], so
 * this needs no Android `Context` and runs as a plain JVM unit test. Ported
 * from the persistence-shaped cases in
 * `ios/CloudmojiCore/Tests/CloudmojiCoreTests/SettingsStoreTests.swift`; the
 * `init`-shaped decode cases live in `SettingsCodecTest`, and the pure
 * clamping/resolution rules live in `SettingsTest`.
 *
 * Every test that checks "does a write survive being read back" does so
 * through *two* [SettingsRepository] instances wrapping the *same*
 * [DataStore] -- never two [DataStore] instances over the same file, which
 * DataStore does not support running concurrently in one process. That is
 * the closest JVM-safe analogue to iOS's "two stores over the same
 * `UserDefaults`" pattern: it still rules out a setter whose effect is only
 * visible to the object that made the call, without trying to simulate a
 * real process relaunch.
 */
class SettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("${UUID.randomUUID()}.preferences_pb") },
        )

    @Test
    fun `fresh install starts in English with everything enabled`() = runBlocking {
        val settings = SettingsRepository(newStore()).settings.first()
        assertEquals(Settings.default(), settings)
    }

    @Test
    fun `dismissing the tour survives being read back by another repository over the same store`() = runBlocking {
        val store = newStore()
        assertEquals(false, SettingsRepository(store).settings.first().seenTutorial)

        SettingsRepository(store).setSeenTutorial(true)
        assertEquals(true, SettingsRepository(store).settings.first().seenTutorial)

        // And it is a real toggle, not a one-way latch.
        SettingsRepository(store).setSeenTutorial(false)
        assertEquals(false, SettingsRepository(store).settings.first().seenTutorial)
    }

    @Test
    fun `the tour flag is stored under cm_seen_tutorial`() = runBlocking {
        val store = newStore()
        SettingsRepository(store).setSeenTutorial(true)

        // A fresh literal key, not SettingsKeys.seenTutorial -- this pins the
        // exact on-disk name as an external contract, the same thing the iOS
        // test pins against UserDefaults.
        val rawKey = booleanPreferencesKey("cm_seen_tutorial")
        assertEquals(true, store.data.first()[rawKey])
    }

    @Test
    fun `a stored language survives being read back`() = runBlocking {
        val store = newStore()
        SettingsRepository(store).setLanguage(Language.Japanese)
        assertEquals(Language.Japanese, SettingsRepository(store).settings.first().language)
    }

    @Test
    fun `the language key is stored under cm_lang`() = runBlocking {
        val store = newStore()
        SettingsRepository(store).setLanguage(Language.Chinese)
        val rawKey = stringPreferencesKey("cm_lang")
        assertEquals("zh", store.data.first()[rawKey])
    }

    @Test
    fun `setting language to a disabled language recovers rather than persisting it`() = runBlocking {
        val store = newStore()
        val repository = SettingsRepository(store)
        repository.setEnabledLanguages(setOf(Language.English, Language.Chinese))

        // .tl is not enabled -- this must recover, not stick.
        repository.setLanguage(Language.Tagalog)

        val settings = SettingsRepository(store).settings.first()
        assertEquals(Language.English, settings.language)
        val rawKey = stringPreferencesKey("cm_lang")
        assertEquals("en", store.data.first()[rawKey])
        assertTrue(store.data.first()[rawKey] != "tl")
    }

    @Test
    fun `setting language to an enabled language still works normally`() = runBlocking {
        val store = newStore()
        val repository = SettingsRepository(store)
        repository.setEnabledLanguages(setOf(Language.English, Language.Chinese))
        repository.setLanguage(Language.Chinese)

        assertEquals(Language.Chinese, SettingsRepository(store).settings.first().language)
    }

    @Test
    fun `with English disabled, the persisted language matches the recovered value`() = runBlocking {
        val store = newStore()
        val repository = SettingsRepository(store)
        repository.setEnabledLanguages(setOf(Language.Japanese, Language.Chinese))
        // .tl is not enabled, and neither is .en -- recovery must land on the
        // alphabetically-first enabled language (ja), and that must be what
        // is persisted, not a hardcoded "en".
        repository.setLanguage(Language.Tagalog)

        val settings = SettingsRepository(store).settings.first()
        assertEquals(Language.Japanese, settings.language)
        assertEquals("ja", store.data.first()[stringPreferencesKey("cm_lang")])
    }

    @Test
    fun `disabling the active language settles it into the enabled set and persists that value`() = runBlocking {
        val store = newStore()
        val repository = SettingsRepository(store)
        repository.setLanguage(Language.Japanese)

        // Disabling the language that is currently active forces
        // setEnabledLanguages to re-resolve and persist `language` itself in
        // the same transaction.
        repository.setEnabledLanguages(setOf(Language.English, Language.Chinese))

        val settings = SettingsRepository(store).settings.first()
        assertTrue(settings.language in settings.enabledLanguages)
        assertEquals(Language.English, settings.language)
        assertEquals("en", store.data.first()[stringPreferencesKey("cm_lang")])
    }

    @Test
    fun `an empty enabled-languages assignment collapses back to every language`() = runBlocking {
        val store = newStore()
        SettingsRepository(store).setEnabledLanguages(emptySet())
        assertEquals(Language.entries.toSet(), SettingsRepository(store).settings.first().enabledLanguages)
    }

    @Test
    fun `an empty enabled-categories assignment collapses back to every category`() = runBlocking {
        val store = newStore()
        SettingsRepository(store).setEnabledCategories(emptySet())
        assertEquals(Category.entries.toSet(), SettingsRepository(store).settings.first().enabledCategories)
    }

    @Test
    fun `assigning an out-of-bounds count range clamps it rather than persisting it verbatim`() = runBlocking {
        val store = newStore()
        // Deliberately outside Settings.countBounds (2..10) on both ends.
        SettingsRepository(store).setCountRange(0..99)

        val settings = SettingsRepository(store).settings.first()
        assertEquals(2..10, settings.countRange)
        assertEquals(2, store.data.first()[intPreferencesKey("cm_count_lower")])
        assertEquals(10, store.data.first()[intPreferencesKey("cm_count_upper")])
    }

    @Test
    fun `assigning a range entirely above countBounds recovers to the default`() = runBlocking {
        val store = newStore()
        SettingsRepository(store).setCountRange(15..20)
        assertEquals(Settings.defaultCountRange, SettingsRepository(store).settings.first().countRange)
    }

    @Test
    fun `a single-value count range is accepted and survives being read back`() = runBlocking {
        val store = newStore()
        SettingsRepository(store).setCountRange(3..3)
        assertEquals(3..3, SettingsRepository(store).settings.first().countRange)
    }

    @Test
    fun `muted is a real toggle that persists`() = runBlocking {
        val store = newStore()
        assertEquals(false, SettingsRepository(store).settings.first().muted)

        SettingsRepository(store).setMuted(true)
        assertEquals(true, SettingsRepository(store).settings.first().muted)

        SettingsRepository(store).setMuted(false)
        assertEquals(false, SettingsRepository(store).settings.first().muted)
    }

    @Test
    fun `every write stamps the current schema version`() = runBlocking {
        val store = newStore()
        SettingsRepository(store).setMuted(true)

        val rawKey = intPreferencesKey("cm_schema_version")
        assertEquals(1, store.data.first()[rawKey])
    }
}
