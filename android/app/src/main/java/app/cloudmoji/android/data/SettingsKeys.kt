package app.cloudmoji.android.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

/**
 * Preferences DataStore keys for [SettingsRepository], and the schema
 * version they belong to.
 *
 * Key names match iOS CloudmojiCore's `SettingsStore.Key` byte-for-byte
 * (`cm_lang`, `cm_enabled_langs`, ...) — nothing here is Android-specific
 * except [schemaVersion], which iOS has no equivalent of.
 *
 * **Migration point:** [SCHEMA_VERSION] has been `1` since this store's
 * first Android release, so [SettingsCodec] has never needed to branch on
 * it. When a future change reshapes what a key means (not just adds a new
 * one — an additive change needs no migration, since every read already
 * treats a missing key as its default), bump [SCHEMA_VERSION] and add a
 * branch at the top of [SettingsCodec.decode] that reads the stored
 * `cm_schema_version` (absent means `1`, the shape described here) and
 * reinterprets the old-shaped raw values into the current shape *before*
 * the invariant helpers in [app.cloudmoji.android.model.Settings] run. Every
 * write path in [SettingsRepository] stamps [SCHEMA_VERSION] via
 * [stampSchemaVersion], so a store written by a future version is
 * self-describing the next time an older build reads it.
 */
internal object SettingsKeys {
    const val SCHEMA_VERSION = 1

    val schemaVersion = intPreferencesKey("cm_schema_version")
    val language = stringPreferencesKey("cm_lang")
    val enabledLanguages = stringSetPreferencesKey("cm_enabled_langs")
    val enabledCategories = stringSetPreferencesKey("cm_enabled_cats")
    val countLower = intPreferencesKey("cm_count_lower")
    val countUpper = intPreferencesKey("cm_count_upper")
    val muted = booleanPreferencesKey("cm_muted")
    val seenTutorial = booleanPreferencesKey("cm_seen_tutorial")
}
