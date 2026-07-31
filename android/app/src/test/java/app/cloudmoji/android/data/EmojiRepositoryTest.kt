package app.cloudmoji.android.data

import app.cloudmoji.android.model.Category
import app.cloudmoji.android.model.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Ported from `ios/CloudmojiCore/Tests/CloudmojiCoreTests/EmojiRepositoryTests.swift`
 * plus this port's own fail-fast count gate and localized spot checks.
 */
class EmojiRepositoryTest {

    private lateinit var repo: EmojiRepository

    @Before
    fun setUp() {
        repo = EmojiRepositoryLoader.fromJson(TestCatalog.json)
    }

    @Test
    fun `loads the full content set`() {
        assertEquals(EmojiRepository.EXPECTED_EMOJI_COUNT, repo.emojis.size)
        assertEquals(EmojiRepository.EXPECTED_COUNTABLE_COUNT, repo.countables.size)
        assertEquals(EmojiRepository.EXPECTED_LANGUAGE_COUNT, repo.languages.size)
        assertEquals(EmojiRepository.EXPECTED_CATEGORY_COUNT, repo.categories.size)
        assertEquals(EmojiRepository.EXPECTED_ANIMAL_SOUND_COUNT, repo.animalSoundGlyphs.size)
    }

    @Test
    fun `every emoji has a non-empty word in all five languages`() {
        repo.emojis.forEach { entry ->
            Language.entries.forEach { language ->
                assertTrue("${entry.emoji} missing $language", entry.word(language).isNotEmpty())
            }
        }
    }

    @Test
    fun `tooth keeps its deliberate katakana spelling`() {
        val tooth = repo.entry("🦷") // 🦷
        assertNotNull(tooth)
        // Hiragana は is parsed as the topic particle and voiced "wa".
        assertEquals("ハ", tooth!!.word(Language.Japanese))
    }

    @Test
    fun `dog is localized correctly in every language, matching src slash data slash emojis`() {
        val dog = repo.entry("🐶") // 🐶
        assertNotNull(dog)
        assertEquals("dog", dog!!.word(Language.English))
        assertEquals("狗", dog.word(Language.Chinese))
        assertEquals("anjing", dog.word(Language.Malay))
        assertEquals("いぬ", dog.word(Language.Japanese))
        assertEquals("aso", dog.word(Language.Tagalog))
    }

    @Test
    fun `mango countable keeps its irregular English plural and is localized in every language`() {
        val mango = repo.countables.firstOrNull { it.emoji == "🥭" } // 🥭
        assertNotNull(mango)
        assertEquals("mangoes", mango!!.enPlural)
        assertEquals("mango", mango.noun(Language.English))
        assertEquals("个芒果", mango.noun(Language.Chinese))
        assertEquals("biji mangga", mango.noun(Language.Malay))
        assertEquals("マンゴー", mango.noun(Language.Japanese))
        assertEquals("mangga", mango.noun(Language.Tagalog))
    }

    @Test
    fun `number words run one through ten in every language`() {
        Language.entries.forEach { language ->
            assertNotNull(repo.numberWord(language, count = 1))
            assertNotNull(repo.numberWord(language, count = 10))
            assertNull(repo.numberWord(language, count = 11))
            assertNull(repo.numberWord(language, count = 0))
        }
    }

    @Test
    fun `category labels are translated in every language`() {
        repo.categories.forEach { tab ->
            Language.entries.forEach { language ->
                assertTrue("${tab.id} missing $language", tab.label(language).isNotEmpty())
            }
        }
    }

    @Test
    fun `categories are exposed in the generator's canonical order, all tab first`() {
        assertEquals("all", repo.categories.first().id)
        assertNull("the all tab is not one of the eight Category values", repo.categories.first().category)
        assertEquals(
            Category.entries.map { it.id },
            repo.categories.drop(1).map { it.id },
        )
    }

    @Test
    fun `entries by category only return that category, in catalogue order`() {
        val animals = repo.entries(Category.Animals)
        assertTrue(animals.isNotEmpty())
        assertTrue(animals.all { it.category == Category.Animals })
        assertEquals(
            repo.emojis.filter { it.category == Category.Animals }.map { it.emoji },
            animals.map { it.emoji },
        )
    }

    @Test
    fun `entry lookup finds a real glyph and returns null for one outside the catalogue`() {
        assertNotNull(repo.entry("🍎")) // 🍎
        assertNull(repo.entry("🛸")) // 🛸, not in the catalogue
    }

    @Test
    fun `the empty repository answers rather than traps`() {
        val empty = EmojiRepository.empty
        assertTrue(empty.emojis.isEmpty())
        assertTrue(empty.countables.isEmpty())
        assertTrue(empty.languages.isEmpty())
        assertTrue(empty.categories.isEmpty())
        Language.entries.forEach { language ->
            assertNull(empty.numberWord(language, count = 3))
            assertNull(empty.meta(language))
        }
        assertTrue(empty.animalSoundGlyphs.isEmpty())
        assertNull(empty.animalSound("🐶", Language.English))
        assertNull(empty.entry("🐶"))
        assertTrue(empty.entries(Category.Animals).isEmpty())
    }
}
