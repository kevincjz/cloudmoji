package app.cloudmoji.android.model

import app.cloudmoji.android.data.EmojiRepository

/**
 * The countables Count mode may draw from, narrowed to the categories the
 * parent left enabled. Mirrors iOS `AppModel.countables` / `AppModel.narrowed`.
 *
 * Filtering happens here, once, for the same reason it does for Words'
 * [buildSections]: `CountScreen` consumes an already-narrowed catalogue and
 * never branches on a setting itself.
 */
fun narrowedCountables(repository: EmojiRepository, enabledCategories: Set<Category>): List<Countable> =
    narrowed(repository.countables, enabledCategories) { countable -> categoryOf(repository, countable) }

/**
 * The narrowing rule, pure so it can be given cases the shipped data does
 * not contain — mirrors iOS `AppModel.narrowed(_:to:categoryOf:)`.
 *
 * Two rules, both deliberate. A countable that maps to **no** category — 🌟
 * is the only one today, because it is in `countables.ts` and not in
 * `emojis.ts` — is always available: a parent has no switch that could
 * remove it, so removing it would be removing something they never asked to
 * lose. And a narrowing that leaves **nothing** degrades to the whole
 * catalogue rather than to an empty screen, because a blank Count mode is a
 * failure state and `CLAUDE.md` rule 4 says the child never sees one.
 */
fun narrowed(
    countables: List<Countable>,
    categories: Set<Category>,
    categoryOf: (Countable) -> Category?,
): List<Countable> {
    val kept = countables.filter { item ->
        val category = categoryOf(item) ?: return@filter true
        category in categories
    }
    return kept.ifEmpty { countables }
}

/**
 * A countable's category, by matching its emoji against the [EmojiEntry]
 * catalogue — mirrors iOS `AppModel.categoryByGlyph`. `null` when the glyph
 * has no entry there at all (🌟, the one countable that is not also a tap
 * target in Words mode).
 *
 * First match wins where an emoji spans more than one category, the same
 * `uniquingKeysWith: { first, _ in first }` iOS's dictionary literal uses —
 * consistent rather than correct in some deeper sense, since which category
 * "owns" a shared glyph for narrowing purposes is otherwise arbitrary.
 */
private fun categoryOf(repository: EmojiRepository, countable: Countable): Category? =
    repository.emojis.firstOrNull { it.emoji == countable.emoji }?.category
