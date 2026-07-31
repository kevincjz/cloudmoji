package app.cloudmoji.android.model

/**
 * The eight content categories an [EmojiEntry] can belong to. Mirrors iOS
 * CloudmojiCore's `Category` enum. Deliberately excludes "all" — that exists
 * only as a [CategoryTab] id, never as a category an entry carries.
 */
enum class Category(val id: String) {
    Fruits("fruits"),
    Food("food"),
    Animals("animals"),
    Vehicles("vehicles"),
    Nature("nature"),
    Objects("objects"),
    People("people"),
    Faces("faces"),
    ;

    companion object {
        fun fromId(id: String): Category? = entries.firstOrNull { it.id == id }
    }
}
