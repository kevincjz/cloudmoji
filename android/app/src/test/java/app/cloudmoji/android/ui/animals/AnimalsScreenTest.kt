package app.cloudmoji.android.ui.animals

import androidx.compose.ui.unit.dp
import app.cloudmoji.android.data.EmojiRepository
import app.cloudmoji.android.data.EmojiRepositoryLoader
import app.cloudmoji.android.data.TestCatalog
import app.cloudmoji.android.model.Category
import app.cloudmoji.android.model.EmojiEntry
import app.cloudmoji.android.model.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `AnimalGridMetrics`'s own arithmetic, `animalGrid`/`narrowedAnimals`'s pure
 * filtering, and a handful of onomatopoeia spot checks against the real
 * generated catalogue — mirrors `FlashCardMetricsTest`/`InstrumentPadMetricsTest`:
 * plain `Dp`/list/set logic with no Compose runtime behind it, so this runs on
 * the plain JVM. The Compose half of the touch-target/TalkBack contract lives
 * in `app/src/androidTest/.../AnimalsChildTargetsTest.kt` (not written here,
 * per the Task 12 brief — Compose instrumentation cannot run in this
 * environment).
 */
class AnimalsScreenTest {

    private lateinit var repo: EmojiRepository

    @Before
    fun setUp() {
        repo = EmojiRepositoryLoader.fromJson(TestCatalog.json)
    }

    private fun entry(emoji: String, category: Category = Category.Animals) = EmojiEntry(
        emoji = emoji,
        category = category,
        en = "en-$emoji",
        zh = "zh-$emoji",
        ms = "ms-$emoji",
        ja = "ja-$emoji",
        tl = "tl-$emoji",
    )

    // MARK: - AnimalGridMetrics

    /**
     * `CLAUDE.md` rule 1 for every layout a card is drawn in. A habitat card
     * is a full-width row, not a square tile, so height — not width — is the
     * dimension this project fixes; width is left to the grid column and is
     * comfortably above the floor on every device this app ships to.
     *
     * Mutation proof: temporarily changed `compactHeight` from 106.dp to
     * 48.dp. This test failed, then passed once restored.
     */
    @Test
    fun `every card height clears the child touch-target floor`() {
        val heights = listOf(AnimalGridMetrics.height, AnimalGridMetrics.compactHeight, AnimalGridMetrics.padHeight)
        for (height in heights) {
            assertTrue("$height is under the 64dp floor", height >= 64.dp)
            assertTrue("$height is under the 72dp preferred size", height >= 72.dp)
        }
    }

    /** `CLAUDE.md` rule 2: at least 8dp between two things a child taps. */
    @Test
    fun `cards are spaced at least the required gap apart`() {
        assertTrue(AnimalGridMetrics.spacing >= 8.dp)
    }

    /**
     * Six colours, and the lookup wraps rather than trapping past the end —
     * the grid can hold up to 20 cards, far more than the tint list's own
     * length.
     *
     * Mutation proof: temporarily made `tint` return `tints[0]` always. The
     * distinct-colour assertion failed, then passed once restored.
     */
    @Test
    fun `tints repeat every six cards and never trap past the end`() {
        val first6 = (0 until 6).map { AnimalGridMetrics.tint(it) }
        assertEquals("six cards must not share a colour", 6, first6.toSet().size)
        assertEquals("a seventh card wraps to the first colour", first6[0], AnimalGridMetrics.tint(6))
        AnimalGridMetrics.tint(99) // must not throw
    }

    /**
     * iOS `AnimalSoundsView.columns(compact:expandedPad:landscape:)`, ported
     * literally — asserted case for case so a future re-tune cannot silently
     * collapse two of the four branches into one.
     *
     * Mutation proof: temporarily swapped the `expandedPad` branch's
     * landscape/upright results (4/3 -> 3/4). The two `expandedPad` cases
     * below failed, then passed once restored.
     */
    @Test
    fun `the column count matches iOS's four cases exactly`() {
        assertEquals(2, AnimalGridMetrics.columns(compact = false, expandedPad = false, landscape = false))
        assertEquals(2, AnimalGridMetrics.columns(compact = false, expandedPad = false, landscape = true))
        assertEquals(4, AnimalGridMetrics.columns(compact = true, expandedPad = false, landscape = false))
        assertEquals(3, AnimalGridMetrics.columns(compact = false, expandedPad = true, landscape = false))
        assertEquals(4, AnimalGridMetrics.columns(compact = false, expandedPad = true, landscape = true))
        // `expandedPad` wins over `compact` in iOS's own `if expandedPad { … }
        // return …` ordering — a tablet is never treated as a compact phone.
        assertEquals(4, AnimalGridMetrics.columns(compact = true, expandedPad = true, landscape = true))
    }

    // MARK: - narrowedAnimals

    /** Mirrors iOS `AppModel.emojis(in: .animals)`: the animals category,
     * only when the parent has left it enabled. */
    @Test
    fun `narrowedAnimals returns the animals category when enabled`() {
        val pool = narrowedAnimals(repo, enabledCategories = Category.entries.toSet())
        assertTrue(pool.isNotEmpty())
        assertTrue(pool.all { it.category == Category.Animals })
        assertEquals(repo.entries(Category.Animals).map { it.emoji }, pool.map { it.emoji })
    }

    /**
     * The one branch `narrowedEmojis` does not have: switching Animals off
     * empties the pool rather than falling the whole catalogue back in, which
     * is what lets `AnimalsScreen` show its "switched off" message instead of
     * silently un-hiding content the parent just turned off.
     *
     * Mutation proof: temporarily made this fall back to `repository.entries(Animals)`
     * unconditionally (matching `narrowedEmojis`'s own fallback shape). This
     * test failed (a non-empty pool with Animals excluded from the enabled
     * set), then passed once restored.
     */
    @Test
    fun `narrowedAnimals is empty, not backfilled, when the category is switched off`() {
        val enabled = Category.entries.toSet() - Category.Animals
        assertTrue(narrowedAnimals(repo, enabled).isEmpty())
    }

    // MARK: - animalGrid

    /** The ordinary case: only animals with a noise make the grid. */
    @Test
    fun `the grid keeps only glyphs that have a sound`() {
        val pool = listOf(entry("🐶"), entry("🐱"), entry("🦒"))
        val grid = animalGrid(pool, glyphsWithSound = setOf("🐶", "🐱"))
        assertEquals(setOf("🐶", "🐱"), grid.map { it.emoji }.toSet())
    }

    /**
     * An empty sound table (nothing has shipped, or a broken bundle) falls
     * back to the whole pool rather than an empty grid — `CLAUDE.md` rule 4.
     * Filtering against an empty set can never match anything, so this and
     * the "matches nothing" case below both flow through the same
     * `matched.ifEmpty { pool }` fallback — see `animalGrid`'s own doc.
     *
     * Mutation proof: temporarily changed the fallback from
     * `matched.ifEmpty { pool }` to plain `matched`. This test failed (an
     * empty grid), then passed once restored.
     */
    @Test
    fun `an empty sound table falls back to the whole pool`() {
        val pool = listOf(entry("🐶"), entry("🐱"))
        assertEquals(pool, animalGrid(pool, glyphsWithSound = emptySet()))
    }

    /**
     * A sound table that matches nothing in the pool — a mis-typed glyph, or
     * a sound entry for an animal since removed from the catalogue — is the
     * same degraded case as an empty table, and gets the same recovery.
     *
     * Mutation proof: same mutation as the test above (`matched.ifEmpty { pool }`
     * -> `matched`). This test failed, then passed once restored.
     */
    @Test
    fun `a sound table that matches nothing falls back to the whole pool`() {
        val pool = listOf(entry("🐶"), entry("🐱"))
        assertEquals(pool, animalGrid(pool, glyphsWithSound = setOf("🦒")))
    }

    /** An empty pool (Animals switched off) stays empty regardless of the
     * sound table — there is nothing to fall back *to*. */
    @Test
    fun `an empty pool stays empty`() {
        assertTrue(animalGrid(emptyList(), glyphsWithSound = setOf("🐶")).isEmpty())
    }

    // MARK: - The real catalogue, end to end

    /**
     * Requirement 3: the 20-animal set resolves for every language, run
     * through the actual grid the screen builds (`animalGrid` composed with
     * `EmojiRepository.animalSoundGlyphs`/`animalSound`), not just the
     * repository layer `AnimalSoundDataTest` already covers. This is what
     * `AnimalsScreen` itself depends on.
     *
     * Mutation proof: temporarily made `animalSound` return the entry's own
     * *name* instead of its noise (breaking the "noise is never just the
     * name" invariant `EmojiRepository`'s own tests hold). The last assertion
     * below failed for every glyph, then passed once restored.
     */
    @Test
    fun `every animal the grid actually shows resolves a noise in all five languages`() {
        val pool = repo.entries(Category.Animals)
        val grid = animalGrid(pool, repo.animalSoundGlyphs)

        assertEquals(EmojiRepository.EXPECTED_ANIMAL_SOUND_COUNT, grid.size)
        grid.forEach { animal ->
            Language.entries.forEach { language ->
                val noise = repo.animalSound(animal.emoji, language)
                assertFalse("${animal.emoji} has no $language noise", noise.isNullOrEmpty())
                assertTrue(
                    "${animal.emoji}'s $language noise is just its name",
                    noise != animal.word(language),
                )
            }
        }
    }

    /**
     * A few known onomatopoeia, fixture-checked against `src/data/animalSounds.ts`
     * (the onomatopoeia table `EmojiData.json`'s `animalSounds` is generated
     * from) — the same kind of spot check `EmojiRepositoryTest`'s "dog is
     * localized correctly" already does for words.
     *
     * Mutation proof: temporarily swapped the dog/cat entries in
     * `src/data/animalSounds.ts` and regenerated a local copy of
     * `EmojiData.json` for this run (not committed). Both assertions below
     * failed with the swapped values, then passed once reverted.
     */
    @Test
    fun `known onomatopoeia match src slash data slash animalSounds exactly`() {
        assertEquals("woof woof", repo.animalSound("🐶", Language.English))
        assertEquals("汪汪", repo.animalSound("🐶", Language.Chinese))
        assertEquals("guk guk", repo.animalSound("🐶", Language.Malay))
        assertEquals("ワンワン", repo.animalSound("🐶", Language.Japanese))
        assertEquals("aw aw", repo.animalSound("🐶", Language.Tagalog))

        assertEquals("meow", repo.animalSound("🐱", Language.English))
        assertEquals("喵喵", repo.animalSound("🐱", Language.Chinese))
    }
}
