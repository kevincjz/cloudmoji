package app.cloudmoji.android.model

import app.cloudmoji.android.data.EmojiRepository
import app.cloudmoji.android.data.EmojiRepositoryLoader
import app.cloudmoji.android.data.TestCatalog
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Ported from `ios/Cloudmoji/CloudmojiTests/AppModelTests.swift`'s
 * `uncategorisedCountablesAlwaysSurvive` / `narrowingNeverReturnsNothing`.
 *
 * The countables Count mode draws from are narrowed by the parent's enabled
 * categories the same way Words' emoji grid is — see [narrowedCountables].
 */
class CountablesTest {

    private fun item(emoji: String): Countable =
        Countable(emoji = emoji, en = emoji, zh = emoji, ms = emoji, ja = emoji, tl = emoji)

    /**
     * Pure, so it can be given the case the shipped data does not contain: a
     * narrowing that leaves nothing. Five items across two categories plus
     * one uncategorised — the smallest fixture in which "kept the right
     * ones", "dropped the right ones" and "kept the uncategorised one" are
     * three distinguishable outcomes.
     *
     * Mutation: delete the `kept.ifEmpty { countables }` fallback. The last
     * expectation returns an empty list, and Count mode has a blank screen.
     */
    @Test
    fun `narrowing keeps the right categories, drops the rest, and never returns nothing`() {
        val a = item("a")
        val b = item("b")
        val fruit1 = item("f1")
        val fruit2 = item("f2")
        val star = item("star")
        val all = listOf(a, b, fruit1, fruit2, star)

        val categoryOf: (Countable) -> Category? = {
            when (it) {
                a, b -> Category.Animals
                fruit1, fruit2 -> Category.Fruits
                else -> null // uncategorised, like 🌟
            }
        }

        assertEquals(listOf(a, b, star), narrowed(all, setOf(Category.Animals), categoryOf))
        assertEquals(5, narrowed(all, setOf(Category.Animals, Category.Fruits), categoryOf).size)

        // Nothing in this fixture is a vehicle, so only the uncategorised
        // star would survive — which is exactly why the fallback below has
        // to be reached through a fixture that has no uncategorised item at
        // all.
        val noStar = all.take(4)
        assertEquals(
            "an empty narrowing must degrade to the whole catalogue, not to nothing",
            4,
            narrowed(noStar, setOf(Category.Vehicles), categoryOf).size,
        )
    }

    /**
     * Mutation: change the `categoryOf` miss from `true` to `false` in
     * [narrowed]. The star vanishes and narrowing to `Faces` alone leaves
     * nothing to count.
     */
    @Test
    fun `a countable in no category survives every narrowing`() {
        val star = item("star")
        val dog = item("dog")
        val categoryOf: (Countable) -> Category? = { if (it == dog) Category.Animals else null }

        assertEquals(listOf(star), narrowed(listOf(star), setOf(Category.Faces), categoryOf))
        assertEquals(
            listOf(star),
            narrowed(listOf(dog, star), setOf(Category.Faces), categoryOf),
        )
    }

    // MARK: - Against the real catalogue

    private lateinit var repo: EmojiRepository

    @Before
    fun setUp() {
        repo = EmojiRepositoryLoader.fromJson(TestCatalog.json)
    }

    /**
     * 🌟 is the one countable that is not also an emoji-grid entry (it is in
     * `countables.ts`, not `emojis.ts`), so it maps to no [Category] and must
     * survive being narrowed to any single, unrelated category.
     */
    @Test
    fun `the star survives narrowing to a category it has no part in`() {
        val kept = narrowedCountables(repo, setOf(Category.Faces))
        assertEquals(true, kept.any { it.emoji == "🌟" })
    }

    @Test
    fun `narrowing the real catalogue to one category keeps only that category's countables, plus the star`() {
        val kept = narrowedCountables(repo, setOf(Category.Animals))
        val expectedGlyphs = repo.emojis.filter { it.category == Category.Animals }.map { it.emoji }.toSet()
        for (countable in kept) {
            val isStar = countable.emoji == "🌟"
            val isAnimal = countable.emoji in expectedGlyphs
            assert(isStar || isAnimal) {
                "${countable.emoji} survived narrowing to Animals but is neither the star nor an animal"
            }
        }
    }
}
