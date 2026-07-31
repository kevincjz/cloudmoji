package app.cloudmoji.android.model

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The half of Flash Cards that iOS keeps in `FlashCardsView.swift`'s `@State`
 * and cannot test at all: what a tap does to the round, and — the rule this
 * whole mini-app exists to keep — what a **non-matching** tap does *not* do.
 *
 * Every expectation below is traceable to a line of `FlashCardsView.tap(_:)`
 * / `nextRound()` / `silence()`; where the two could plausibly differ, the
 * iOS line is quoted in the test's own doc.
 */
class FlashCardsViewModelTest {

    private fun entry(emoji: String, word: String): EmojiEntry =
        EmojiEntry(emoji = emoji, category = Category.Animals, en = word, zh = word, ms = word, ja = word, tl = word)

    private val pool = listOf(
        entry("🐶", "dog"), entry("🐱", "cat"), entry("🐮", "cow"),
        entry("🐷", "pig"), entry("🐔", "chicken"), entry("🦁", "lion"),
    )

    private fun started(seed: Int = 1): FlashCardsViewModel =
        FlashCardsViewModel().apply { startRound(pool, Language.English, Random(seed)) }

    private fun FlashCardsViewModel.wrongChoice(): EmojiEntry {
        val current = requireNotNull(round.value)
        return current.choices.first { !current.isCorrect(it) }
    }

    // MARK: - Starting a round

    @Test
    fun `starting a round puts a question with an answer on screen`() {
        val model = started()
        val round = requireNotNull(model.round.value)

        assertTrue(round.target in round.choices)
        assertFalse(model.isAdvancing.value)
        assertNull(model.solvedId.value)
        assertNull(model.pendingAction.value)
    }

    /**
     * iOS `nextRound()` passes `avoiding: round?.target`, so consecutive
     * rounds never ask for the same thing.
     *
     * Mutation: pass `avoiding = null` in [FlashCardsViewModel.startRound].
     * Some of the twenty seeds repeat the target and this fails.
     */
    @Test
    fun `a fresh round never repeats the outgoing target`() {
        val model = started(seed = 5)
        for (seed in 1..20) {
            val previous = requireNotNull(model.round.value).target
            model.startRound(pool, Language.English, Random(seed))
            assertNotEquals("seed $seed asked for the same thing twice", previous, model.round.value!!.target)
        }
    }

    // MARK: - Tapping

    /**
     * A correct tap opens the celebration window: the tile is marked solved,
     * everything is disabled, and the screen is told to deal a new round
     * after the delay. iOS: `isAdvancing = true; solvedID = entry.id; ...
     * advanceTask = afterDelay(advanceDelay) { ... nextRound() }`.
     *
     * Mutation: drop `advancingState.value = true`. The disabled window
     * never opens and this fails.
     */
    @Test
    fun `a correct tap solves the tile and schedules the next round`() {
        val model = started()
        val target = requireNotNull(model.round.value).target

        val outcome = model.tap(target)

        assertTrue(outcome is FlashCardsViewModel.TapOutcome.Correct)
        assertEquals(target.id, model.solvedId.value)
        assertTrue(model.isAdvancing.value)
        assertEquals(FlashCardsViewModel.PendingAction.Kind.Advance, model.pendingAction.value?.kind)
        assertEquals(target.id, model.bounce.value?.id)
    }

    /**
     * **The rule this mini-app exists for.** `CLAUDE.md` rule 4: a tap that
     * is not the answer is not a failure. It bounces the tile the child
     * actually touched, schedules the question to come back — iOS's `else`
     * branch is `speak(word(entry)); advanceTask = afterDelay { ask() }` —
     * and, critically, leaves `isAdvancing` alone, so the other choices stay
     * live and the child can keep exploring. Nothing is marked solved, and
     * the round is not replaced.
     *
     * Mutation: set `advancingState.value = true` in the non-matching branch
     * too. The board freezes for 1400ms after every exploratory tap, and the
     * two `assertFalse`/`Repeat` expectations below fail.
     */
    @Test
    fun `a non-matching tap answers the child without locking the board`() {
        val model = started()
        val roundBefore = requireNotNull(model.round.value)
        val other = model.wrongChoice()

        val outcome = model.tap(other)

        assertTrue(outcome is FlashCardsViewModel.TapOutcome.Other)
        assertEquals("the tapped tile must be the one that answers", other, outcome!!.entry)
        assertFalse("a non-matching tap must never lock the other choices", model.isAdvancing.value)
        assertNull("nothing was solved", model.solvedId.value)
        assertEquals(FlashCardsViewModel.PendingAction.Kind.Repeat, model.pendingAction.value?.kind)
        assertEquals("the question was never withdrawn", roundBefore, model.round.value)
        assertEquals(other.id, model.bounce.value?.id)
    }

    /**
     * …and it stays that way for a whole run of them. A child hunting across
     * the row must be able to tap every tile in turn.
     */
    @Test
    fun `a run of non-matching taps keeps every choice live`() {
        val model = started()
        val round = requireNotNull(model.round.value)
        for (choice in round.choices.filter { !round.isCorrect(it) }) {
            assertNotNull("a non-matching tap was refused", model.tap(choice))
            assertFalse(model.isAdvancing.value)
        }
        // …and the answer still works afterwards.
        assertTrue(model.tap(round.target) is FlashCardsViewModel.TapOutcome.Correct)
    }

    /**
     * iOS: `guard let round, !isAdvancing else { return }`. A second tap
     * during the celebration must not re-arm the hand-off, or the next round
     * arrives early and the celebration is cut off.
     *
     * Mutation: delete the `if (advancingState.value) return null` guard.
     * The token below advances and this fails.
     */
    @Test
    fun `taps during a celebration are refused`() {
        val model = started()
        val round = requireNotNull(model.round.value)
        model.tap(round.target)
        val armed = model.pendingAction.value

        assertNull(model.tap(model.wrongChoice()))
        assertNull(model.tap(round.target))
        assertEquals("the pending hand-off must not be re-armed", armed, model.pendingAction.value)
    }

    /** No round on screen — the degraded pool case — refuses rather than
     * throwing in front of a child. */
    @Test
    fun `a tap with no round on screen is refused`() {
        val model = FlashCardsViewModel()
        assertNull(model.tap(pool.first()))
    }

    // MARK: - Tokens

    /**
     * iOS re-arms `bounceTask`/`advanceTask` unconditionally on every tap.
     * Here that is a token: a second tap on the *same* tile must produce a
     * new one, or the screen's `LaunchedEffect` would not restart its delay
     * and the bounce would end early.
     *
     * Mutation: make [FlashCardsViewModel.Bounce] carry only the id (drop the
     * token). The two bounces compare equal and this fails.
     */
    @Test
    fun `tapping the same tile twice arms a fresh bounce and a fresh hand-off`() {
        val model = started()
        val other = model.wrongChoice()

        model.tap(other)
        val firstBounce = model.bounce.value
        val firstAction = model.pendingAction.value
        model.tap(other)

        assertNotEquals(firstBounce, model.bounce.value)
        assertNotEquals(firstAction, model.pendingAction.value)
        assertEquals(other.id, model.bounce.value?.id)
    }

    /**
     * A stale timer must not clear the bounce a later tap started.
     *
     * Mutation: drop the token guard in [FlashCardsViewModel.clearBounce].
     * The second tile stops bouncing the instant the first one's timer fires.
     */
    @Test
    fun `a stale bounce timer cannot clear the current bounce`() {
        val model = started()
        val round = requireNotNull(model.round.value)
        // Both non-matching, so neither tap opens the celebration window and
        // refuses the one after it — this test is about the token, not the
        // guard (which `taps during a celebration are refused` covers).
        val others = round.choices.filter { !round.isCorrect(it) }
        val first = others[0]
        val second = others[1]

        model.tap(first)
        val staleToken = requireNotNull(model.bounce.value).token
        model.tap(second)

        model.clearBounce(staleToken)
        assertEquals(second.id, model.bounce.value?.id)

        model.clearBounce(requireNotNull(model.bounce.value).token)
        assertNull(model.bounce.value)
    }

    /**
     * Same rule for the 1400ms hand-off — a late clear for an action already
     * superseded must not drop the new one.
     *
     * Mutation: drop the token guard in
     * [FlashCardsViewModel.clearPendingAction]. The live hand-off is dropped
     * and the question never comes back.
     */
    @Test
    fun `a stale pending-action clear cannot drop the live one`() {
        val model = started()
        model.tap(model.wrongChoice())
        val staleToken = requireNotNull(model.pendingAction.value).token
        model.tap(model.wrongChoice())

        model.clearPendingAction(staleToken)
        assertNotNull("the live hand-off was dropped by a stale clear", model.pendingAction.value)

        model.clearPendingAction(requireNotNull(model.pendingAction.value).token)
        assertNull(model.pendingAction.value)
    }

    // MARK: - Silence and reset

    /**
     * iOS `silence()`: `isAdvancing = false; solvedID = nil;
     * advanceTask?.cancel()` — but the round itself survives, because a
     * language change re-asks the *same* question rather than pulling the
     * emojis out from under a child mid-choice.
     *
     * Mutation: null the round out in [FlashCardsViewModel.clearPendingTap].
     * The last expectation fails and a language change becomes a new
     * question.
     */
    @Test
    fun `clearing a pending tap keeps the question on screen`() {
        val model = started()
        val round = requireNotNull(model.round.value)
        model.tap(round.target)

        model.clearPendingTap()

        assertFalse(model.isAdvancing.value)
        assertNull(model.solvedId.value)
        assertNull(model.pendingAction.value)
        assertEquals("the question must survive a language or mute change", round, model.round.value)
    }

    /**
     * [FlashCardsViewModel.reset] is the other half: opening Flash Cards
     * afresh from the launcher throws the question away so the screen deals a
     * new one, the Android stand-in for iOS's `@State` dying on a mode
     * switch.
     *
     * Mutation: leave `roundState` alone in `reset()`. A re-entry resumes the
     * stale question and this fails.
     */
    @Test
    fun `resetting throws the round away entirely`() {
        val model = started()
        model.tap(requireNotNull(model.round.value).target)

        model.reset()

        assertNull(model.round.value)
        assertFalse(model.isAdvancing.value)
        assertNull(model.solvedId.value)
        assertNull(model.pendingAction.value)
    }

    /**
     * A pool that cannot make a question leaves the screen with no round
     * rather than a one-tile one — [FlashRound.create]'s `null`, carried
     * through. Not reachable in production (`Settings.cleanedCategories`
     * guarantees a non-empty category set) but it must not throw.
     */
    @Test
    fun `a pool that cannot make a question leaves no round`() {
        val model = FlashCardsViewModel()
        model.startRound(listOf(entry("🐶", "dog")), Language.English, Random(1))
        assertNull(model.round.value)
    }
}
