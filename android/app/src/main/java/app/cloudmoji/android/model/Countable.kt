package app.cloudmoji.android.model

/**
 * A noun the Count mini-app can pluralise, keyed by emoji. Mirrors iOS
 * CloudmojiCore's `Countable`.
 *
 * `zh` and `ms` bake the classifier into the noun (只狗, ekor anjing). `ja`
 * and `tl` stay bare — their morphology lives on the number (Task 2's
 * counting grammar), not here.
 */
data class Countable(
    val emoji: String,
    val en: String,
    /** Set only where the regular pluraliser is wrong: teeth, mice. */
    val enPlural: String? = null,
    val zh: String,
    val ms: String,
    val ja: String,
    val tl: String,
) {
    val id: String get() = emoji

    fun noun(language: Language): String = when (language) {
        Language.English -> en
        Language.Chinese -> zh
        Language.Malay -> ms
        Language.Japanese -> ja
        Language.Tagalog -> tl
    }
}
