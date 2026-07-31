package app.cloudmoji.android.model

/**
 * One tappable emoji and its word in every language. Mirrors iOS
 * CloudmojiCore's `EmojiEntry`.
 */
data class EmojiEntry(
    val emoji: String,
    val category: Category,
    val en: String,
    val zh: String,
    val ms: String,
    val ja: String,
    val tl: String,
) {
    /** Stable across categories, since an emoji may appear in more than one. */
    val id: String get() = "$emoji|${category.id}"

    fun word(language: Language): String = when (language) {
        Language.English -> en
        Language.Chinese -> zh
        Language.Malay -> ms
        Language.Japanese -> ja
        Language.Tagalog -> tl
    }
}
