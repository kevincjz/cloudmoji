package app.cloudmoji.android.model

import app.cloudmoji.android.data.EmojiRepository

/**
 * One category's worth of the continuous Words-mode emoji list, with the tab
 * that names it. Mirrors iOS `AppModel.swift`'s `EmojiSection`.
 *
 * The id is the tab's, so it doubles as the scroll target a chip jumps to —
 * see [sectionHeaderIndex].
 */
data class EmojiSection(
    val tab: CategoryTab,
    val entries: List<EmojiEntry>,
) {
    val id: String get() = tab.id
}

/**
 * The emoji list as the child sees it: one continuous run of every enabled
 * emoji, cut into a section per category, in catalogue order. Mirrors iOS
 * `AppModel.sections`.
 *
 * The "all" tab is deliberately not a section — in a continuous list every
 * emoji is already on screen, so "all of them" is not a place to scroll to.
 * A category [enabledCategories] has switched off has no section here, no
 * chip, and no tiles — the same "deletes a failure state rather than
 * guarding it" reasoning iOS documents: a chip can never point at a place
 * that does not exist. An enabled category with zero entries (not possible
 * in the shipped catalogue, but not assumed here) is dropped for the same
 * reason: a header with nothing under it is a small blank screen of its own.
 */
fun buildSections(repository: EmojiRepository, enabledCategories: Set<Category>): List<EmojiSection> =
    repository.categories.mapNotNull { tab ->
        val category = tab.category ?: return@mapNotNull null // the "all" tab
        if (category !in enabledCategories) return@mapNotNull null
        val entries = repository.entries(category)
        if (entries.isEmpty()) return@mapNotNull null
        EmojiSection(tab, entries)
    }

/**
 * Where a category-chip jump should scroll to: the flattened item index of
 * [sectionId]'s header inside a `LazyVerticalGrid` that lays out one header
 * item followed by one item per entry, per section, in order. `null` when
 * [sectionId] has no section (a chip for a category a parent just disabled).
 */
fun sectionHeaderIndex(sections: List<EmojiSection>, sectionId: String): Int? {
    var index = 0
    for (section in sections) {
        if (section.id == sectionId) return index
        index += 1 + section.entries.size
    }
    return null
}

/**
 * The inverse of [sectionHeaderIndex]: which section owns the flattened item
 * at [itemIndex] — its header counts as its own section, and so does every
 * tile after it, up to (not including) the next section's header. This is
 * what tells the category chips which one to highlight from nothing more
 * than a `LazyGridState.firstVisibleItemIndex` — Compose already reports
 * that without the geometry probing iOS's `ScrollView` needed. `null` past
 * the end of the flattened list.
 */
fun sectionAtItemIndex(sections: List<EmojiSection>, itemIndex: Int): String? {
    if (itemIndex < 0) return null
    var cursor = 0
    for (section in sections) {
        val span = 1 + section.entries.size
        if (itemIndex < cursor + span) return section.id
        cursor += span
    }
    return null
}
