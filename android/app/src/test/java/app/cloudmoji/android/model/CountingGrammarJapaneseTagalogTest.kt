package app.cloudmoji.android.model

import app.cloudmoji.android.data.EmojiRepository
import app.cloudmoji.android.data.EmojiRepositoryLoader
import app.cloudmoji.android.data.TestCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Ported from
 * `ios/CloudmojiCore/Tests/CloudmojiCoreTests/CountingGrammarJaTlTests.swift`.
 */
class CountingGrammarJapaneseTagalogTest {

    private lateinit var repo: EmojiRepository
    private lateinit var grammar: CountingGrammar

    @Before
    fun setUp() {
        repo = EmojiRepositoryLoader.fromJson(TestCatalog.json)
        grammar = CountingGrammar(repo)
    }

    private fun item(en: String): Countable =
        repo.countables.first { it.en == en }

    // Japanese

    @Test
    fun `Japanese puts the noun first and the counter last`() {
        assertEquals("りんご みっつ", grammar.phrase(item("apple"), count = 3, language = Language.Japanese))
        assertEquals("いぬ ひとつ", grammar.phrase(item("dog"), count = 1, language = Language.Japanese))
    }

    @Test
    fun `Japanese inserts no particle between noun and counter`() {
        val phrase = grammar.phrase(item("dog"), count = 2, language = Language.Japanese)
        // Exactly two space-separated tokens. Checked as a shape because some
        // nouns legitimately contain の (やしのき = palm tree).
        assertEquals(2, phrase.split(" ").size)
        assertFalse(phrase.startsWith("ふたつ"))
    }

    @Test
    fun `Japanese counts one through ten with the tsu series`() {
        val expected = listOf(
            "ひとつ", "ふたつ", "みっつ", "よっつ", "いつつ",
            "むっつ", "ななつ", "やっつ", "ここのつ", "とお",
        )
        val dog = item("dog")
        expected.forEachIndexed { index, counter ->
            assertTrue(
                grammar.phrase(dog, count = index + 1, language = Language.Japanese).endsWith(" $counter"),
            )
        }
    }

    // Tagalog

    @Test
    fun `vowel-final numerals take the -ng linker`() {
        assertEquals("tatlong aso", grammar.phrase(item("dog"), count = 3, language = Language.Tagalog))
        assertEquals("isang aso", grammar.phrase(item("dog"), count = 1, language = Language.Tagalog))
        assertEquals("sampung aso", grammar.phrase(item("dog"), count = 10, language = Language.Tagalog))
    }

    @Test
    fun `consonant-final numerals take a separate na`() {
        assertEquals("apat na aso", grammar.phrase(item("dog"), count = 4, language = Language.Tagalog))
        assertEquals("anim na aso", grammar.phrase(item("dog"), count = 6, language = Language.Tagalog))
        assertEquals("siyam na aso", grammar.phrase(item("dog"), count = 9, language = Language.Tagalog))
    }

    @Test
    fun `n-final numerals take -g`() {
        assertEquals("roong", CountingGrammar.tagalogLinked("roon"))
    }

    @Test
    fun `Tagalog nouns are never pluralised after a numeral`() {
        val aso = item("dog")
        for (count in 1..10) {
            assertTrue(grammar.phrase(aso, count = count, language = Language.Tagalog).endsWith(" aso"))
        }
    }

    @Test
    fun `the full Tagalog linker matrix is pinned for one noun across all ten counts`() {
        // The three tests above only name six of the ten number words between
        // them. Pin all ten literally, for one noun, so every number word in
        // the series is covered.
        val expected = listOf(
            "isang aso", "dalawang aso", "tatlong aso", "apat na aso", "limang aso",
            "anim na aso", "pitong aso", "walong aso", "siyam na aso", "sampung aso",
        )
        val aso = item("dog")
        expected.forEachIndexed { index, phrase ->
            assertEquals(phrase, grammar.phrase(aso, count = index + 1, language = Language.Tagalog))
        }
    }
}
