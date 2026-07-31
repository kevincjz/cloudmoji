package app.cloudmoji.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import app.cloudmoji.android.model.Category
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.Settings
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * The single on-disk settings store, reused across the app via `Context`.
 * Production wiring only — tests construct a [SettingsRepository] directly
 * over a [DataStore] backed by a temp file (or an in-memory one), never
 * this property, so they need no `Context` at all.
 */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cloudmoji_settings",
)

/**
 * Parent-facing settings, persisted with Preferences DataStore.
 *
 * Thin by design: every invariant this class enforces — an empty enabled set
 * means "all of them", the active language must stay inside the enabled set,
 * the count range must stay inside [Settings.countBounds] — is delegated to
 * [SettingsCodec] and [Settings]'s companion, both plain Kotlin with no
 * DataStore dependency. This class's own job is only wiring: turn a
 * [Preferences] snapshot into [settings] on the way out, and run each
 * `setXxx` as one atomic [DataStore.edit] transaction on the way in, mirroring
 * the read-then-recover shape iOS's `SettingsStore` property observers give
 * each field.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    /**
     * The current, always-valid settings. A corrupt or partially-written
     * [Preferences] file surfaces as [IOException] from DataStore; that is
     * treated the same as an empty store rather than propagated, because a
     * settings read must never be a failure state the child's launch can hit
     * — see CLAUDE.md rule 4.
     */
    val settings: Flow<Settings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(SettingsCodec::decode)

    /**
     * Sets the active language, recovering to a valid one rather than
     * persisting a disabled choice — mirrors iOS `SettingsStore.language`'s
     * `didSet`. [Settings.resolveLanguage] is given the *currently stored*
     * enabled set, read inside the same transaction, so this can never race
     * a concurrent [setEnabledLanguages].
     */
    suspend fun setLanguage(language: Language) {
        dataStore.edit { prefs ->
            val enabled = SettingsCodec.readEnabledLanguages(prefs)
            prefs[SettingsKeys.language] = Settings.resolveLanguage(language, enabled).code
            stampSchemaVersion(prefs)
        }
    }

    /**
     * Sets which languages a parent has left on. An empty set collapses back
     * to every language ([Settings.cleanedLanguages]); if that leaves the
     * currently active language disabled, the active language is
     * re-resolved and persisted in the same transaction — mirrors iOS
     * `SettingsStore.enabledLanguages`'s `didSet` re-resolving `language`.
     */
    suspend fun setEnabledLanguages(languages: Set<Language>) {
        dataStore.edit { prefs ->
            val cleaned = Settings.cleanedLanguages(languages)
            val currentLanguage = SettingsCodec.readRawLanguage(prefs)
            prefs[SettingsKeys.enabledLanguages] = cleaned.map(Language::code).toSet()
            if (currentLanguage == null || currentLanguage !in cleaned) {
                prefs[SettingsKeys.language] = Settings.resolveLanguage(currentLanguage, cleaned).code
            }
            stampSchemaVersion(prefs)
        }
    }

    /** An empty set collapses back to every category — mirrors [setEnabledLanguages]. */
    suspend fun setEnabledCategories(categories: Set<Category>) {
        dataStore.edit { prefs ->
            val cleaned = Settings.cleanedCategories(categories)
            prefs[SettingsKeys.enabledCategories] = cleaned.map(Category::id).toSet()
            stampSchemaVersion(prefs)
        }
    }

    /**
     * Sets the count range, clamped into [Settings.countBounds] rather than
     * persisted verbatim — mirrors iOS `SettingsStore.countRange`'s `didSet`.
     */
    suspend fun setCountRange(range: IntRange) {
        dataStore.edit { prefs ->
            val cleaned = Settings.clampedRange(range.first, range.last)
            prefs[SettingsKeys.countLower] = cleaned.first
            prefs[SettingsKeys.countUpper] = cleaned.last
            stampSchemaVersion(prefs)
        }
    }

    suspend fun setMuted(muted: Boolean) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.muted] = muted
            stampSchemaVersion(prefs)
        }
    }

    /**
     * Whether the welcome tour has been dismissed at least once. No
     * validation clause, unlike the setters above, and that is not an
     * oversight: an absent or hand-edited value can only ever be read back
     * as `false` ([SettingsCodec.decode]'s `?: false`), so it can only ever
     * mean "show the tour again" — the harmless direction. Mirrors iOS
     * `SettingsStore.seenTutorial`.
     */
    suspend fun setSeenTutorial(seenTutorial: Boolean) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.seenTutorial] = seenTutorial
            stampSchemaVersion(prefs)
        }
    }

    private fun stampSchemaVersion(prefs: MutablePreferences) {
        prefs[SettingsKeys.schemaVersion] = SettingsKeys.SCHEMA_VERSION
    }
}
