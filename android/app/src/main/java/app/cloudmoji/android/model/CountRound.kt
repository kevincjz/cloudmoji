package app.cloudmoji.android.model

/**
 * One round of Count mode: a countable, how many of it are on screen, and
 * which of them the child has counted so far. Mirrors iOS `CountRound.swift`.
 *
 * An immutable value type on purpose, exactly like the iOS `struct` it is
 * ported from. Count mode's state machine is the part of this app that fails
 * silently — a round that lets a tile be counted twice still looks and
 * sounds perfect — and a type with pure, testable transitions is the only
 * shape in which that can be caught at all. Kotlin has no `mutating func`, so
 * where iOS's `tap(_:)` mutates in place and returns whether anything
 * changed, [tap] here returns the *new* round, or `null` when the tap is
 * refused — the caller (`CountViewModel`) is the one that decides what "no
 * change" means for its own state.
 */
data class CountRound(
    /** What is being counted. The same glyph is repeated [target] times. */
    val item: Countable,
    /** How many tiles are on screen, and the number the round finishes on. */
    val target: Int,
    /**
     * Tile indices in the order they were counted. Order matters: the badge
     * on a tile is its position in *this* list, not its position in the
     * grid, which is the whole point of counting things that are not in a
     * line.
     */
    val counted: List<Int> = emptyList(),
) {
    val progress: Int get() = counted.size

    val isComplete: Boolean get() = counted.size == target

    /** The number to draw on a tile, or `null` if it has not been counted yet. */
    fun badge(index: Int): Int? {
        val position = counted.indexOf(index)
        return if (position == -1) null else position + 1
    }

    /**
     * Counts one tile. Returns the round with [index] appended to [counted],
     * or `null` when the tap changed nothing — an already-counted tile, or
     * an index that is not on screen.
     *
     * The caller uses `null` to decide whether to speak. Speaking on a
     * refused tap would say "three" twice and teach the wrong number, which
     * is the exact failure this guard exists for. It is *not* a failure
     * state: the tile still presses and still shows its badge (nothing about
     * it changes, badge included, since the round did not change), so the
     * tap is still answered.
     */
    fun tap(index: Int): CountRound? {
        if (index !in 0 until target) return null
        if (index in counted) return null
        return copy(counted = counted + index)
    }

    companion object {
        /**
         * Where a session starts. Three, when the parent's range allows it —
         * two tiles barely read as a group, and three is what the web has
         * always opened on — otherwise the nearest end of the range.
         */
        fun firstTarget(range: IntRange): Int = minOf(maxOf(3, range.first), range.last)

        /**
         * One more than last time, wrapping back to the bottom of the range.
         *
         * The web randomises on the wrap; walking is better here. The parent
         * may have narrowed the range to two or three values, and a random
         * draw inside a two-value range repeats itself half the time — which
         * reads as Next being broken. The *item* is randomised on every
         * round, so a round is never the same twice regardless.
         */
        fun nextTarget(after: Int, range: IntRange): Int {
            val next = after + 1
            return if (next in range) next else range.first
        }

        /**
         * Draws the next thing to count, never the thing being replaced.
         *
         * `null` only when there is nothing at all to draw from — a state
         * [app.cloudmoji.android.data.EmojiRepository]'s countables guarantee
         * cannot happen for the real catalogue, checked here anyway because
         * the alternative is a crash in front of a child.
         */
        fun pick(catalogue: List<Countable>, excluding: Countable?): Countable? {
            val candidates = catalogue.filter { it != excluding }
            // Everything was excluded, which means the catalogue is the one
            // item we were leaving. Handing it back beats a button that does
            // nothing.
            return candidates.randomElement() ?: catalogue.randomElement()
        }

        private fun <T> List<T>.randomElement(): T? = if (isEmpty()) null else random()
    }
}
