package app.cloudmoji.android.data

import androidx.datastore.preferences.core.Preferences
import app.cloudmoji.android.model.Category
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.Settings

/**
 * Turns a raw [Preferences] snapshot into a validated [Settings], and back.
 *
 * Split out from [SettingsRepository] so the decoding rules are testable
 * against a [Preferences] built directly with `preferencesOf(...)` — no
 * on-disk DataStore file, no `Context`. This is what keeps [Settings]'s
 * invariants ("garbage in the enabled set is filtered", "an unknown stored
 * language recovers", "an inverted count range is clamped", ...) host-JVM
 * testable, matching iOS `SettingsStoreTests`' coverage of `SettingsStore`'s
 * `init`.
 */
internal object SettingsCodec {

    fun decode(prefs: Preferences): Settings {
        val enabledLanguages = readEnabledLanguages(prefs)
        val enabledCategories = readEnabledCategories(prefs)
        val language = Settings.resolveLanguage(
            preferring = readRawLanguage(prefs),
            enabled = enabledLanguages,
        )
        return Settings(
            language = language,
            enabledLanguages = enabledLanguages,
            enabledCategories = enabledCategories,
            countRange = readCountRange(prefs),
            muted = prefs[SettingsKeys.muted] ?: false,
            seenTutorial = prefs[SettingsKeys.seenTutorial] ?: false,
        )
    }

    /**
     * The stored language exactly as written, unresolved against
     * [enabledLanguages] — callers that need "the active language, forced
     * valid" go through [Settings.resolveLanguage] themselves, the same way
     * [decode] does for a full read.
     */
    fun readRawLanguage(prefs: Preferences): Language? =
        prefs[SettingsKeys.language]?.let(Language::fromCode)

    /**
     * Unknown codes are dropped (a stale "es" or "th" from an older release
     * or a hand-edited file must never reach [Settings.language]), and an
     * empty result — whether from an absent key or a stored empty set —
     * collapses to every language via [Settings.cleanedLanguages].
     */
    fun readEnabledLanguages(prefs: Preferences): Set<Language> {
        val stored = prefs[SettingsKeys.enabledLanguages]
            ?.mapNotNull(Language::fromCode)
            ?.toSet()
            ?: emptySet()
        return Settings.cleanedLanguages(stored)
    }

    fun readEnabledCategories(prefs: Preferences): Set<Category> {
        val stored = prefs[SettingsKeys.enabledCategories]
            ?.mapNotNull(Category::fromId)
            ?.toSet()
            ?: emptySet()
        return Settings.cleanedCategories(stored)
    }

    /**
     * [Settings.defaultCountRange] unless *both* bounds are present — a
     * range with only one bound stored is not a case the writer ever
     * produces, so it is treated the same as neither being stored, exactly
     * as iOS `SettingsStore.readRange` does.
     */
    fun readCountRange(prefs: Preferences): IntRange {
        val lower = prefs[SettingsKeys.countLower]
        val upper = prefs[SettingsKeys.countUpper]
        if (lower == null || upper == null) return Settings.defaultCountRange
        return Settings.clampedRange(lower, upper)
    }
}
