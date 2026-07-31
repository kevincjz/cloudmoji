package app.cloudmoji.android.data

import app.cloudmoji.android.model.Category
import app.cloudmoji.android.model.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Ported from `ios/CloudmojiCore/Tests/CloudmojiCoreTests/EmojiRepositoryTests.swift`'s
 * `AnimalSoundDataTests` suite.
 */
class AnimalSoundDataTest {

    private lateinit var repo: EmojiRepository

    @Before
    fun setUp() {
        repo = EmojiRepositoryLoader.fromJson(TestCatalog.json)
    }

    /**
     * Every glyph with a noise is a real animal in the catalogue. One that is
     * not can never be tapped — the grid is built by intersecting the two —
     * so it is a silent content bug.
     */
    @Test
    fun `every animal with a noise is an animal in the catalogue`() {
        val animals = repo.entries(Category.Animals).map { it.emoji }.toSet()
        assertTrue("no animals at all — nothing below can mean anything", animals.isNotEmpty())

        val glyphs = repo.animalSoundGlyphs
        assertEquals(EmojiRepository.EXPECTED_ANIMAL_SOUND_COUNT, glyphs.size)
        glyphs.forEach { glyph ->
            assertTrue("$glyph has a noise but is not an animal", glyph in animals)
        }
    }

    /**
     * Five languages or none. A missing row is not papered over at runtime —
     * [EmojiRepository.animalSound] deliberately does not fall back to
     * English — so a gap here is a gap a child would actually meet.
     */
    @Test
    fun `every animal noise exists in all five languages`() {
        repo.animalSoundGlyphs.forEach { glyph ->
            Language.entries.forEach { language ->
                val sound = repo.animalSound(glyph, language)
                assertFalse("$glyph has no $language noise", sound.isNullOrEmpty())
            }
        }
    }

    /**
     * The noise is not the name. "dog" and "woof woof" are different strings
     * in every language.
     */
    @Test
    fun `an animal's noise is never just its name`() {
        repo.animalSoundGlyphs.forEach { glyph ->
            val entry = repo.entry(glyph) ?: return@forEach
            Language.entries.forEach { language ->
                val noise = repo.animalSound(glyph, language)
                assertTrue("$glyph's $language noise is just its name", noise != entry.word(language))
            }
        }
    }

    /** An animal with no entry answers `null` rather than another animal's noise. */
    @Test
    fun `an animal with no noise says so`() {
        assertNull(repo.animalSound("🍎", Language.English)) // 🍎
        assertFalse("🍎" in repo.animalSoundGlyphs)
    }
}
