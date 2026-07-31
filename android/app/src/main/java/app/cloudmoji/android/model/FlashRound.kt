package app.cloudmoji.android.model

import app.cloudmoji.android.data.EmojiRepository
import kotlin.random.Random

/**
 * One flash-card question: a word Cloudmoji says, and the emojis to choose
 * from. Ported from iOS `Views/FlashCards/FlashRound.swift`.
 *
 * An immutable value type with pure construction, the same shape and for the
 * same reason as [CountRound]: which choices a round offers is the part of
 * this mini-app that can be silently wrong. A round whose three tiles happen
 * to include two emojis with the *same word* in the current language has no
 * right answer, and it looks perfect on a screenshot.
 */
data class FlashRound(
    /** The one the child is being asked for. */
    val target: EmojiEntry,
    /** Target and distractors, already shuffled. The target is always in here. */
    val choices: List<EmojiEntry>,
) {
    /** Whether this glyph is the one that was asked for. */
    fun isCorrect(entry: EmojiEntry): Boolean = entry.emoji == target.emoji

    companion object {
        /** Three tiles, which is what fits across a phone at 96–110dp each
         * without either shrinking under the child-target floor or making the
         * choice feel like a search. Mirrors iOS `FlashRound.init`'s
         * `choices count: Int = 3`. */
        const val DEFAULT_CHOICE_COUNT: Int = 3

        /**
         * Builds a round from [pool].
         *
         * Returns `null` when [pool] cannot make a question at all — fewer
         * than two distinct words in [language], which means there is nothing
         * to choose *between*. The caller shows nothing rather than a one-tile
         * round; a question with one answer is not a question. (Not reachable
         * from the shipped catalogue: `Settings.cleanedCategories` collapses
         * an empty category set back to all of them, so the narrowed pool
         * always has hundreds of words in it.)
         *
         * Distractors are filtered on the **word**, not the glyph. The
         * catalogue has cross-category near-synonyms — the same word can
         * arrive under two emojis — and a round offering both would punish a
         * child for being right.
         *
         * [random] is a parameter, not `Random.Default` reached for inside,
         * so a test can seed it and get the same round twice — iOS's own
         * `inout some RandomNumberGenerator` exists for exactly this reason,
         * and [CountRound.pick], which does reach for `random()` directly, is
         * untestable in precisely this respect.
         */
        fun create(
            pool: List<EmojiEntry>,
            language: Language,
            choiceCount: Int = DEFAULT_CHOICE_COUNT,
            avoiding: EmojiEntry? = null,
            random: Random = Random.Default,
        ): FlashRound? {
            // One entry per distinct word. Keeping the first is arbitrary and
            // fine — what matters is that no two survivors share a word.
            val seen = mutableSetOf<String>()
            val distinct = mutableListOf<EmojiEntry>()
            for (entry in pool) {
                if (seen.add(entry.word(language))) distinct += entry
            }
            if (distinct.size < 2) return null

            // Not the same target twice running, unless there is no other
            // choice — a child who has just been asked for a dog and is asked
            // for a dog again reads it as the app being stuck.
            val candidates = distinct.filter { it.emoji != avoiding?.emoji }
            val target = (if (candidates.isEmpty()) distinct else candidates).randomOrNull(random) ?: return null

            val distractors = distinct
                .filter { it.emoji != target.emoji }
                .shuffled(random)
                // `choiceCount - 1` may exceed what is available; `take` takes
                // what there is, which is why a pool of two still makes a
                // two-choice round rather than crashing or padding with a
                // repeat. The `maxOf(1, ...)` floor keeps a degenerate
                // `choiceCount` of 0 or 1 from producing a one-tile round.
                .take(maxOf(1, choiceCount - 1))

            return FlashRound(
                target = target,
                choices = (listOf(target) + distractors).shuffled(random),
            )
        }
    }
}

/**
 * Every emoji Flash Cards may draw from, narrowed to the categories the
 * parent left enabled. Mirrors iOS `AppModel.emojis(in:)` called with a
 * `nil` category, which is exactly what `FlashCardsView.nextRound()` passes.
 *
 * Filtering happens here, once, for the same reason it does for Words'
 * [buildSections] and Count's [narrowedCountables]: `FlashCardsScreen`
 * consumes an already-narrowed pool and never branches on a setting itself.
 *
 * Deliberately has no "degrade to the whole catalogue when the narrowing
 * leaves nothing" rule, unlike [narrowed] — iOS's `emojis(in:)` has none
 * either, and it cannot arise: `Settings.cleanedCategories` turns an empty
 * enabled-category set back into every category.
 */
fun narrowedEmojis(
    repository: EmojiRepository,
    enabledCategories: Set<Category>,
): List<EmojiEntry> = repository.emojis.filter { it.category in enabledCategories }
