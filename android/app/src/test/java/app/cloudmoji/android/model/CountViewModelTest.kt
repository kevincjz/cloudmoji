package app.cloudmoji.android.model

import app.cloudmoji.android.data.EmojiRepository
import app.cloudmoji.android.data.EmojiRepositoryLoader
import app.cloudmoji.android.data.TestCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Count mode's round/phrase/lastCounted state, host-testable independent of
 * Compose. Ported behaviourally from iOS `CountView.swift`'s `tap`/
 * `startRound`/the language-change handler — the parts of it that are not
 * SwiftUI timing (see `ios/Cloudmoji/CloudmojiTests/CountViewTests.swift`'s
 * own framing: "the behaviour lives in `CountModeUITests`", i.e. not in a
 * unit test at all on iOS either).
 */
class CountViewModelTest {

    private fun countable(emoji: String, en: String): Countable =
        Countable(emoji = emoji, en = en, zh = en, ms = en, ja = en, tl = en)

    private val dog = countable("🐶", "dog")
    private val cat = countable("🐱", "cat")

    /** A trivial, deterministic phrase builder — "$count $noun". Good enough
     * for asserting *that* the right count/item reach the phrase; the real
     * grammar's own rules are [CountingGrammarEnglishTest] and friends'
     * responsibility. */
    private fun phraseFor(item: Countable, count: Int): String = "$count ${item.en}"

    // MARK: - startRound

    @Test
    fun `a fresh round starts at progress zero with a blank phrase`() {
        val model = CountViewModel()
        model.startRound(listOf(dog), target = 4)

        assertEquals(dog, model.round.value?.item)
        assertEquals(4, model.round.value?.target)
        assertEquals(0, model.round.value?.progress)
        assertNull(model.lastCounted.value)
        assertEquals("", model.phrase.value)
    }

    /**
     * Mirrors [CountRoundTest]'s "shuffling always lands on a different
     * item", exercised through the ViewModel entry point Shuffle/Next
     * actually call — [CountViewModel.startRound] is what both are wired to.
     *
     * Mutation: pass `excluding = null` instead of the previous item to
     * [CountRound.pick] inside [CountViewModel.startRound]. With a two-item
     * catalogue roughly half of these twenty draws repeat, and this fails.
     */
    @Test
    fun `starting a new round never repeats the item just retired`() {
        val model = CountViewModel()
        model.startRound(listOf(dog, cat), target = 3)
        repeat(20) {
            val previous = model.round.value!!.item
            model.startRound(listOf(dog, cat), target = 3)
            assertTrue("a fresh round repeated the previous item", model.round.value!!.item != previous)
        }
    }

    /** The degraded case: an empty catalogue must not crash — an absent
     * round beats a crash in front of a child. */
    @Test
    fun `starting a round with nothing to draw from leaves no round`() {
        val model = CountViewModel()
        model.startRound(listOf(dog), target = 3)
        model.startRound(emptyList(), target = 3)
        assertNull(model.round.value)
        assertNull(model.lastCounted.value)
        assertEquals("", model.phrase.value)
    }

    // MARK: - tap

    @Test
    fun `an accepted tap returns the spoken phrase and updates progress, lastCounted and phrase`() {
        val model = CountViewModel()
        model.startRound(listOf(dog), target = 4)

        val spoken = model.tap(2, ::phraseFor)

        assertEquals("1 dog", spoken)
        assertEquals(1, model.round.value?.progress)
        assertEquals(1, model.round.value?.badge(2))
        assertEquals(2, model.lastCounted.value)
        assertEquals("1 dog", model.phrase.value)
    }

    /**
     * A refused tap must not speak — the caller uses `null` to decide that.
     * Mirrors iOS `CountView.tap`'s own "a refused tap must not speak" rule.
     */
    @Test
    fun `a refused tap returns null and leaves progress, lastCounted and phrase untouched`() {
        val model = CountViewModel()
        model.startRound(listOf(dog), target = 4)
        model.tap(1, ::phraseFor)

        val refused = model.tap(1, ::phraseFor) // already counted

        assertNull(refused)
        assertEquals(1, model.round.value?.progress)
        assertEquals(1, model.lastCounted.value) // unchanged: still the first tap
        assertEquals("1 dog", model.phrase.value) // unchanged
    }

    @Test
    fun `a tap outside the round is refused`() {
        val model = CountViewModel()
        model.startRound(listOf(dog), target = 3)
        assertNull(model.tap(7, ::phraseFor))
        assertNull(model.tap(-1, ::phraseFor))
        assertEquals(0, model.round.value?.progress)
    }

    @Test
    fun `tapping with no round is refused rather than crashing`() {
        val model = CountViewModel()
        assertNull(model.tap(0, ::phraseFor))
    }

    @Test
    fun `the running phrase reflects progress, not the target`() {
        val model = CountViewModel()
        model.startRound(listOf(dog), target = 4)
        model.tap(0, ::phraseFor)
        assertEquals("1 dog", model.phrase.value)
        model.tap(1, ::phraseFor)
        assertEquals("2 dog", model.phrase.value)
    }

    @Test
    fun `a round completes on its last tile`() {
        val model = CountViewModel()
        model.startRound(listOf(dog), target = 2)
        model.tap(0, ::phraseFor)
        assertTrue(model.round.value?.isComplete == false)
        model.tap(1, ::phraseFor)
        assertTrue(model.round.value?.isComplete == true)
    }

    // MARK: - completionPhrase

    @Test
    fun `the completion phrase is the target count's phrase, exclaimed`() {
        val model = CountViewModel()
        model.startRound(listOf(dog), target = 3)
        model.tap(0, ::phraseFor)

        val closing = model.completionPhrase(::phraseFor)

        assertEquals("3 dog!", closing) // target, not progress (1)
    }

    @Test
    fun `there is no completion phrase without a round`() {
        val model = CountViewModel()
        assertNull(model.completionPhrase(::phraseFor))
    }

    // MARK: - refreshPhrase (language change)

    @Test
    fun `refreshing the phrase before anything is counted is a no-op`() {
        val model = CountViewModel()
        model.startRound(listOf(dog), target = 3)
        model.refreshPhrase(::phraseFor)
        assertEquals("", model.phrase.value)
    }

    @Test
    fun `refreshing the phrase rebuilds it from the current progress`() {
        val model = CountViewModel()
        model.startRound(listOf(dog), target = 3)
        model.tap(0, ::phraseFor)
        assertEquals("1 dog", model.phrase.value)

        // Simulates a language change: the same progress, a different phrase
        // builder (a different language's grammar).
        model.refreshPhrase { item, count -> "${count} ${item.en} (fr)" }

        assertEquals("1 dog (fr)", model.phrase.value)
    }

    // MARK: - setPhrase

    @Test
    fun `setPhrase overwrites the on-screen phrase directly`() {
        val model = CountViewModel()
        model.setPhrase("hello")
        assertEquals("hello", model.phrase.value)
    }

    // MARK: - Against the real CountingGrammar, all five languages
    //
    // Requirement 4 of the Task 7 brief: "readout phrase matches
    // CountingGrammar output for all five languages." `CountingGrammar`
    // itself is Task 2's and is tested exhaustively by
    // `CountingGrammarEnglishTest`/`CountingGrammarClassifierTest`/
    // `CountingGrammarJapaneseTagalogTest`/`CountingGrammarClaudeMdExamplesTest`;
    // what is new here is that `CountViewModel.tap`'s returned phrase, and
    // its published `phrase` state, are *exactly* what the grammar builds —
    // not a copy that could drift.

    private lateinit var repo: EmojiRepository
    private lateinit var grammar: CountingGrammar

    @Before
    fun setUp() {
        repo = EmojiRepositoryLoader.fromJson(TestCatalog.json)
        grammar = CountingGrammar(repo)
    }

    @Test
    fun `the tapped phrase matches CountingGrammar output in every language`() {
        val realDog = repo.countables.first { it.emoji == "🐶" }
        for (language in Language.entries) {
            val model = CountViewModel()
            model.startRound(listOf(realDog), target = 3)
            val spoken = model.tap(0) { item, count -> grammar.phrase(item, count, language) }
            assertEquals(grammar.phrase(realDog, 1, language), spoken)
            assertEquals(grammar.phrase(realDog, 1, language), model.phrase.value)
        }
    }

    @Test
    fun `the completion phrase matches CountingGrammar output, exclaimed, in every language`() {
        val realDog = repo.countables.first { it.emoji == "🐶" }
        for (language in Language.entries) {
            val model = CountViewModel()
            model.startRound(listOf(realDog), target = 3)
            model.tap(0) { item, count -> grammar.phrase(item, count, language) }
            val closing = model.completionPhrase { item, count -> grammar.phrase(item, count, language) }
            assertEquals(grammar.phrase(realDog, 3, language) + "!", closing)
        }
    }
}
