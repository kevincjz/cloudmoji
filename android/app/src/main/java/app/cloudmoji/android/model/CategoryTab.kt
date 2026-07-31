package app.cloudmoji.android.model

/**
 * A tab in the category rail: "all", or one of the eight [Category] values.
 * Mirrors iOS CloudmojiCore's `CategoryTab`.
 */
data class CategoryTab(
    /** "all", or a [Category] id. */
    val id: String,
    val icon: String,
    /** Keyed by [Language.code], to match the generated JSON exactly. */
    val labels: Map<String, String>,
) {
    fun label(language: Language): String = labels[language.code] ?: labels["en"] ?: id

    /** `null` for the "all" tab. */
    val category: Category? get() = Category.fromId(id)
}
