package app.cloudmoji.android.model

/**
 * Parent-facing settings, validated on every read.
 *
 * Mirrors iOS CloudmojiCore's `SettingsStore` field set and defaults exactly.
 * Every instance here is already clean: an empty enabled set has already
 * become "all of them" ([cleanedLanguages]/[cleanedCategories]), the active
 * [language] is already a member of [enabledLanguages] ([resolveLanguage]),
 * and [countRange] already sits inside [countBounds] and is not inverted
 * ([clampedRange]). This type carries no persistence of its own — that is
 * [app.cloudmoji.android.data.SettingsRepository]'s job, and its setters
 * route every mutation through these same companion helpers so a runtime
 * change and a freshly-decoded value can never disagree about what "valid"
 * means, the same single-source-of-truth shape iOS's `SettingsStore` uses
 * its `didSet` observers for.
 */
data class Settings(
    val language: Language,
    val enabledLanguages: Set<Language>,
    val enabledCategories: Set<Category>,
    val countRange: IntRange,
    val muted: Boolean,
    val seenTutorial: Boolean,
) {
    companion object {
        /**
         * Count mode never goes below two (one is not counting) or above ten
         * (Japanese has no ～つ form past とお).
         */
        val countBounds = 2..10

        /** The fresh-install / recovery-from-nonsense count range. */
        val defaultCountRange = 2..9

        /** Fresh-install defaults: English, everything enabled, tour unseen. */
        fun default(): Settings = Settings(
            language = Language.English,
            enabledLanguages = Language.entries.toSet(),
            enabledCategories = Category.entries.toSet(),
            countRange = defaultCountRange,
            muted = false,
            seenTutorial = false,
        )

        /**
         * The single place the "active language must be enabled" invariant is
         * decided. Both a fresh decode (reading a possibly-stale stored
         * value) and a runtime language change (reacting to a parent
         * disabling the language they were just handed) call this rather
         * than each encoding their own recovery.
         *
         * Resolution order: [preferring] if it is enabled, else English if
         * *it* is enabled, else the alphabetically-first (by [Language.code])
         * enabled language. The last tier only matters once English itself
         * has been disabled — there is always some enabled language to fall
         * back to.
         *
         * [enabled] must be non-empty. Every call site already guarantees
         * this: [cleanedLanguages] replaces an empty set with every language
         * before it ever reaches here.
         */
        fun resolveLanguage(preferring: Language?, enabled: Set<Language>): Language {
            require(enabled.isNotEmpty()) {
                "resolveLanguage requires a non-empty enabled set; callers must guarantee this"
            }
            if (preferring != null && preferring in enabled) return preferring
            if (Language.English in enabled) return Language.English
            return enabled.minBy { it.code }
        }

        /** An empty set means "no restriction" — collapses back to every language. */
        fun cleanedLanguages(languages: Set<Language>): Set<Language> =
            languages.ifEmpty { Language.entries.toSet() }

        /** An empty set means "no restriction" — collapses back to every category. */
        fun cleanedCategories(categories: Set<Category>): Set<Category> =
            categories.ifEmpty { Category.entries.toSet() }

        /**
         * The single place the "count range is within [countBounds], and not
         * inverted" invariant is decided. Each bound is clamped independently
         * into [countBounds], then the pair falls back to [defaultCountRange]
         * if that leaves them inverted. A single-value range (lower == upper)
         * is valid — a parent choosing "exactly 3" is a real, intentional
         * setting, not an error.
         */
        fun clampedRange(lower: Int, upper: Int): IntRange {
            val clampedLower = maxOf(countBounds.first, lower)
            val clampedUpper = minOf(countBounds.last, upper)
            return if (clampedLower <= clampedUpper) clampedLower..clampedUpper else defaultCountRange
        }
    }
}
