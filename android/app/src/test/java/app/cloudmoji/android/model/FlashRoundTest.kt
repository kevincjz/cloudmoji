package app.cloudmoji.android.model

import app.cloudmoji.android.data.EmojiRepositoryLoader
import app.cloudmoji.android.data.TestCatalog
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ported from `ios/Cloudmoji/CloudmojiTests/FlashRoundTests.swift`, test for
 * test, with the same mutation notes attached.
 *
 * Which choices a round offers is the part of Flash Cards that can be wrong
 * in a way no screenshot shows: a round whose three tiles include two emojis
 * with the *same word* has no right answer, and it looks perfect.
 *
 * `Random(seed)` stands in for the Swift suite's hand-rolled xorshift
 * generator — [FlashRound.create] takes it as a parameter for the same reason
 * iOS takes an `inout RandomNumberGenerator`, so a round can be asked for
 * twice and compared.
 */
class FlashRoundTest {

    // Every language gets the same word, so `language` can be varied in the
    // tests below without the fixtures having to model five vocabularies.
    private fun entry(emoji: String, word: String, category: Category = Category.Animals): EmojiEntry =
        EmojiEntry(emoji = emoji, category = category, en = word, zh = word, ms = word, ja = word, tl = word)

    private val pool = listOf(
        entry("🐶", "dog"), entry("🐱", "cat"), entry("🐮", "cow"),
        entry("🐷", "pig"), entry("🐔", "chicken"), entry("🦁", "lion"),
    )

    /**
     * Same seed, same round. Without it nothing below can distinguish "the
     * rule is right" from "this run got lucky".
     *
     * Mutation: ignore the `random` parameter and call `random()`/`shuffled()`
     * with no argument. The two rounds diverge and this fails.
     */
    @Test
    fun `a seeded generator produces the same round twice`() {
        val first = FlashRound.create(pool = pool, language = Language.English, random = Random(42))
        val second = FlashRound.create(pool = pool, language = Language.English, random = Random(42))

        assertNotNull(first)
        assertEquals(first!!.target, second!!.target)
        assertEquals(first.choices, second.choices)
    }

    /**
     * The question has an answer. A round whose choices did not include the
     * target is a screen where every tap is wrong — which is the failure
     * state `CLAUDE.md` rule 4 forbids, in its purest form.
     *
     * Mutation: build `choices` from `distractors` alone. Fails on the first
     * seed.
     */
    @Test
    fun `the target is always among the choices`() {
        for (seed in 1..40) {
            val round = requireNotNull(
                FlashRound.create(pool = pool, language = Language.English, random = Random(seed)),
            ) { "seed $seed produced no round from a six-entry pool" }
            assertTrue("seed $seed: the answer is not on screen", round.target in round.choices)
            assertEquals("seed $seed", 3, round.choices.size)
            assertTrue(round.isCorrect(round.target))
        }
    }

    /**
     * **The silent one.** The catalogue has cross-category near-synonyms —
     * the same word under two different emojis — and a round offering both
     * punishes a child for being right.
     *
     * Mutation: drop the `seen.add(...)` filter. The duplicate-word pool
     * below produces a round with two "cow"s in it and this fails.
     */
    @Test
    fun `no two choices share a word in the current language`() {
        val ambiguous = listOf(
            entry("🐮", "cow"), entry("🐄", "cow"), entry("🐶", "dog"),
            entry("🐱", "cat"), entry("🐷", "pig"),
        )
        for (seed in 1..40) {
            val round = requireNotNull(
                FlashRound.create(pool = ambiguous, language = Language.English, random = Random(seed)),
            ) { "seed $seed produced no round" }
            val words = round.choices.map { it.word(Language.English) }
            assertEquals("seed $seed offered $words", words.size, words.toSet().size)
        }
    }

    /**
     * Distinctness is per language, not per glyph: two emojis can be distinct
     * in English and identical in Chinese.
     *
     * Mutation: hardcode [Language.English] inside [FlashRound.create]. The
     * Chinese case offers two 牛 and this fails.
     */
    @Test
    fun `distinctness follows the language the family chose`() {
        val split = listOf(
            EmojiEntry("🐮", Category.Animals, en = "cow", zh = "牛", ms = "lembu", ja = "うし", tl = "baka"),
            EmojiEntry("🐂", Category.Animals, en = "ox", zh = "牛", ms = "lembu", ja = "うし", tl = "baka"),
            EmojiEntry("🐶", Category.Animals, en = "dog", zh = "狗", ms = "anjing", ja = "いぬ", tl = "aso"),
        )
        for (seed in 1..20) {
            val round = requireNotNull(
                FlashRound.create(pool = split, language = Language.Chinese, random = Random(seed)),
            ) { "seed $seed produced no round" }
            val words = round.choices.map { it.word(Language.Chinese) }
            assertEquals("seed $seed offered $words in Chinese", words.size, words.toSet().size)
            // Two distinct words in Chinese, so a Chinese round is a
            // two-choice round rather than a three-choice one with a repeat.
            assertEquals(2, round.choices.size)
        }
    }

    /**
     * A pool that cannot make a question returns `null`, and the screen shows
     * nothing rather than a one-tile round. A question with one answer is not
     * a question.
     *
     * Mutation: change the guard to `distinct.size < 1`. The single-entry
     * case builds a round and this fails.
     */
    @Test
    fun `a pool with fewer than two distinct words makes no round`() {
        assertNull(FlashRound.create(pool = emptyList(), language = Language.English, random = Random(7)))
        assertNull(
            FlashRound.create(pool = listOf(entry("🐶", "dog")), language = Language.English, random = Random(7)),
        )
        // Two entries sharing one word: one distinct word left over, and the
        // same answer under both tiles.
        assertNull(
            FlashRound.create(
                pool = listOf(entry("🐮", "cow"), entry("🐄", "cow")),
                language = Language.English,
                random = Random(7),
            ),
        )
    }

    /**
     * Exactly two distinct words is the smallest real question, and it must
     * not crash, pad itself with a repeat, or be refused.
     *
     * Mutation: change `take(maxOf(1, choiceCount - 1))` to
     * `take(choiceCount - 1)` — a two-entry pool still works, so the
     * assertion that bites is the count being exactly 2 rather than 3.
     */
    @Test
    fun `two distinct words make a valid two-choice round`() {
        for (seed in 1..20) {
            val round = requireNotNull(
                FlashRound.create(
                    pool = listOf(entry("🐶", "dog"), entry("🐱", "cat")),
                    language = Language.English,
                    random = Random(seed),
                ),
            ) { "seed $seed refused a two-word pool" }
            assertEquals(2, round.choices.size)
            assertTrue(round.target in round.choices)
        }
    }

    /**
     * Not the same target twice running. A child who has just been asked for
     * a dog and gets asked for a dog again reads it as the app being stuck.
     *
     * Mutation: delete the `avoiding` filter. Over forty seeds the same
     * target comes back and this fails.
     */
    @Test
    fun `the same target does not come back immediately`() {
        val previous = entry("🐶", "dog")
        for (seed in 1..40) {
            val round = requireNotNull(
                FlashRound.create(
                    pool = pool,
                    language = Language.English,
                    avoiding = previous,
                    random = Random(seed),
                ),
            ) { "seed $seed produced no round" }
            assertTrue("seed $seed asked for the dog again", round.target.emoji != previous.emoji)
        }
    }

    /**
     * …unless there is nothing else to ask for, in which case repeating beats
     * showing nothing.
     *
     * Mutation: drop the `if (candidates.isEmpty()) distinct` fallback and
     * return `null` instead. A two-entry pool with one excluded goes blank.
     */
    @Test
    fun `a pool with nothing else in it repeats rather than giving up`() {
        val dog = entry("🐶", "dog")
        val round = FlashRound.create(
            pool = listOf(dog, entry("🐱", "cat")),
            language = Language.English,
            avoiding = dog,
            random = Random(3),
        )
        assertNotNull("a two-entry pool with one excluded produced no round at all", round)
    }

    /**
     * Every tile on screen came from the pool it was given — without this, an
     * implementation that fabricated a placeholder entry would satisfy
     * everything above.
     */
    @Test
    fun `every choice comes from the pool`() {
        for (seed in 1..40) {
            val round = requireNotNull(
                FlashRound.create(pool = pool, language = Language.English, random = Random(seed)),
            ) { "seed $seed produced no round" }
            assertTrue("seed $seed offered something that is not in the pool", round.choices.all { it in pool })
        }
    }

    // MARK: - The pool itself

    /**
     * The parent's category switches are what Flash Cards draws from, and
     * nothing else — [narrowedEmojis] is the whole of that rule.
     *
     * Mutation: return `repository.emojis` unfiltered. The disabled
     * category's entries come back and this fails.
     */
    @Test
    fun `the pool holds only the categories the parent left enabled`() {
        val repository = EmojiRepositoryLoader.fromJson(TestCatalog.json)
        val enabled = setOf(Category.Animals, Category.Food)

        val narrowed = narrowedEmojis(repository, enabled)

        assertTrue("the narrowed pool is empty", narrowed.isNotEmpty())
        assertEquals(enabled, narrowed.map { it.category }.toSet())
        assertEquals(
            "every enabled entry must survive, in catalogue order",
            repository.emojis.filter { it.category in enabled },
            narrowed,
        )
    }

    /**
     * The real catalogue, narrowed to one category, still makes a proper
     * three-choice round — the thing a parent who has switched off seven of
     * the eight categories actually gets. A fixture cannot prove this;
     * only the shipped content can.
     */
    @Test
    fun `one enabled category still makes a three-choice round in every language`() {
        val repository = EmojiRepositoryLoader.fromJson(TestCatalog.json)
        val narrowed = narrowedEmojis(repository, setOf(Category.Animals))

        for (language in Language.entries) {
            for (seed in 1..10) {
                val round = requireNotNull(
                    FlashRound.create(pool = narrowed, language = language, random = Random(seed)),
                ) { "$language seed $seed produced no round" }
                assertEquals("$language seed $seed", 3, round.choices.size)
                assertTrue(round.target in round.choices)
                val words = round.choices.map { it.word(language) }
                assertEquals("$language seed $seed offered $words", words.size, words.toSet().size)
            }
        }
    }
}
