package app.cloudmoji.android.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ported from `ios/Cloudmoji/CloudmojiTests/CountRoundTests.swift`.
 *
 * The counting state machine is the part of Count mode that can be wrong in
 * a way no screenshot shows: a round that lets the same tile be counted
 * twice still looks perfect and still speaks, it just teaches the child to
 * count to four by tapping three things.
 */
class CountRoundTest {

    private fun countable(emoji: String, en: String): Countable =
        Countable(emoji = emoji, en = en, zh = "只$en", ms = "ekor $en", ja = en, tl = en)

    private val dog = countable("🐶", "dog")
    private val cat = countable("🐱", "cat")

    // MARK: - Tapping

    /**
     * Four tiles, tapped out of order, so the badge numbers are the *order
     * of counting* rather than the tile's position. A three-tile fixture
     * cannot tell those two apart on more than one tile.
     *
     * Mutation: drop `counted + index` in [CountRound.tap] (return `this`
     * unchanged instead). Progress stays 0.
     */
    @Test
    fun `tapping records the order counted, not the tile's position`() {
        var round = CountRound(item = dog, target = 4)
        assertEquals(0, round.progress)

        val second = round.tap(2)
        assertNotNull(second)
        round = second!!
        val first = round.tap(0)
        assertNotNull(first)
        round = first!!
        val fourth = round.tap(3)
        assertNotNull(fourth)
        round = fourth!!

        assertEquals(3, round.progress)
        assertEquals(1, round.badge(2))
        assertEquals(2, round.badge(0))
        assertEquals(3, round.badge(3))
        assertNull("an uncounted tile must carry no badge", round.badge(1))
        assertFalse(round.isComplete)
    }

    /**
     * Mutation: delete the `index in counted` guard in [CountRound.tap].
     * Progress reaches 2 off one tile, and the child counts to four by
     * tapping twice.
     */
    @Test
    fun `a tile already counted is ignored`() {
        val round = CountRound(item = dog, target = 4)
        val accepted = round.tap(1)
        assertNotNull(accepted)
        val refused = accepted!!.tap(1)
        assertNull("the second tap on the same tile must be refused", refused)
        assertEquals(1, accepted.progress)
        assertEquals(1, accepted.badge(1))
    }

    /**
     * Mutation: delete the `index !in 0 until target` guard in
     * [CountRound.tap]. Progress reaches 1 with no tile on screen to
     * account for it.
     */
    @Test
    fun `an index outside the round is ignored`() {
        val round = CountRound(item = dog, target = 3)
        assertNull(round.tap(7))
        assertNull(round.tap(-1))
        assertEquals(0, round.progress)
    }

    /**
     * Completion is what unlocks Next and the celebration, so it is
     * asserted on its own rather than inferred from `progress`.
     *
     * Mutation: change `isComplete` to `counted.size > target`. Never
     * completes.
     */
    @Test
    fun `a round completes on its last tile and not before`() {
        var round = CountRound(item = dog, target = 3)
        for (index in 0 until 2) {
            round = round.tap(index)!!
            assertFalse("complete after ${index + 1} of 3", round.isComplete)
        }
        round = round.tap(2)!!
        assertTrue(round.isComplete)
        assertEquals(3, round.progress)
    }

    // MARK: - Targets

    /**
     * The literal walk, spelled out. Reading the range back out of itself
     * would pass against an implementation that never moved.
     *
     * Mutation: delete the wrap (`next in range` check) and the fifth value
     * is 6, outside the parent's range. Delete the `+ 1` and every value is 2.
     */
    @Test
    fun `the next target walks up the range and wraps to its start`() {
        val range = 2..5
        val walk = mutableListOf<Int>()
        var target = 2
        repeat(5) {
            target = CountRound.nextTarget(target, range)
            walk += target
        }
        assertEquals(listOf(3, 4, 5, 2, 3), walk)
    }

    /** A range the parent narrowed to a single value has nowhere to walk to. */
    @Test
    fun `a single-value range stays where it is`() {
        assertEquals(4, CountRound.nextTarget(4, 4..4))
    }

    /**
     * Three is where the web starts, and it is the right first round when
     * the range allows it — two tiles barely reads as counting. Literals,
     * because `range.first` would agree with an implementation that always
     * started at the bottom.
     *
     * Mutation: replace the body with `range.first`. The first case fails.
     */
    @Test
    fun `the first target is three when the range allows it, and inside it otherwise`() {
        assertEquals(3, CountRound.firstTarget(2..9))
        assertEquals("must not start below the range", 5, CountRound.firstTarget(5..9))
        assertEquals("must not start above the range", 2, CountRound.firstTarget(2..2))
        assertEquals(3, CountRound.firstTarget(3..3))
    }

    // MARK: - Shuffling

    /**
     * Shuffle that can hand back the same animal is a button that
     * sometimes does nothing, and "one tap = one action = one reward" does
     * not survive that.
     *
     * A two-item catalogue is the smallest fixture in which the exclusion
     * is observable, and fifty draws make a dropped exclusion certain
     * rather than likely.
     *
     * Mutation: delete the `it != excluding` filter. Roughly half of the
     * fifty draws come back as the item we were leaving.
     */
    @Test
    fun `shuffling always lands on a different item`() {
        repeat(50) {
            val picked = CountRound.pick(listOf(dog, cat), excluding = dog)
            assertEquals("shuffle returned the item it was replacing", cat, picked)
        }
    }

    /**
     * The degraded case: a parent has narrowed the content so far that one
     * countable is all there is. Refusing to shuffle is a dead button;
     * handing back the same item is honest.
     *
     * Mutation: return `null` when the filtered list is empty. The round
     * has nothing to draw and the screen goes blank in front of a child.
     */
    @Test
    fun `shuffling a one-item catalogue returns that item`() {
        assertEquals(dog, CountRound.pick(listOf(dog), excluding = dog))
        assertNull(CountRound.pick(emptyList(), excluding = null))
    }

    /**
     * Every draw is a real member of the catalogue it was given. Without
     * this a shuffle that returned a fixed placeholder would satisfy
     * everything above.
     */
    @Test
    fun `every draw comes from the catalogue`() {
        val catalogue = listOf(dog, cat, countable("🐰", "rabbit"))
        repeat(50) {
            val picked = CountRound.pick(catalogue, excluding = null)
            assertTrue(picked in catalogue)
        }
    }
}
