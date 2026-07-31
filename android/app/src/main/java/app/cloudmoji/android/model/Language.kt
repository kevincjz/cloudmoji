package app.cloudmoji.android.model

enum class Language(val code: String) {
    English("en"),
    Chinese("zh"),
    Malay("ms"),
    Japanese("ja"),
    Tagalog("tl"),
    ;

    companion object {
        fun fromCode(code: String): Language? = entries.firstOrNull { it.code == code }

        /**
         * Advances to the next language in [enabled], wrapping at the end.
         * Mirrors iOS `AppModel.nextLanguage(after:in:)` — the one-tap cycle
         * a parent's language button drives, replacing a menu picker a
         * 27-month-old cannot operate.
         *
         * Pure and static so the wrap-around, and the "[after] is not even in
         * [enabled]" case, can be exercised without a view. That case should
         * be impossible — `SettingsRepository` re-resolves the active
         * language whenever either side of the invariant moves — but landing
         * on the first enabled language (or, if [enabled] is itself empty,
         * on [after] unchanged) is the same safe recovery every other
         * "should be impossible" case in this app gets rather than a crash.
         */
        fun next(after: Language, enabled: List<Language>): Language {
            val index = enabled.indexOf(after)
            if (index == -1) return enabled.firstOrNull() ?: after
            return enabled[(index + 1) % enabled.size]
        }
    }
}

