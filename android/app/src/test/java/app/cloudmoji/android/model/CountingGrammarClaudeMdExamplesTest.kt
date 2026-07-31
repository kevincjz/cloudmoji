package app.cloudmoji.android.model

import app.cloudmoji.android.data.EmojiRepository
import app.cloudmoji.android.data.EmojiRepositoryLoader
import app.cloudmoji.android.data.TestCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

/**
 * The `CLAUDE.md` language rules quote these exact phrases as the binding
 * examples for Japanese and Tagalog counting grammar. Pinned here verbatim,
 * on top of the ported iOS fixture suites, per Task 2's brief.
 */
class CountingGrammarClaudeMdExamplesTest {

    private lateinit var repo: EmojiRepository
    private lateinit var grammar: CountingGrammar

    @Before
    fun setUp() {
        repo = EmojiRepositoryLoader.fromJson(TestCatalog.json)
        grammar = CountingGrammar(repo)
    }

    private fun item(en: String): Countable =
        repo.countables.first { it.en == en }

    @Test
    fun `Japanese counts noun first, never number-no-noun order - rinngo mittsu`() {
        val phrase = grammar.phrase(item("apple"), count = 3, language = Language.Japanese)
        assertEquals("りんご みっつ", phrase)
        assertNotEquals("みっつのりんご", phrase)
    }

    @Test
    fun `Tagalog vowel-final numeral takes -ng - tatlong aso`() {
        assertEquals("tatlong aso", grammar.phrase(item("dog"), count = 3, language = Language.Tagalog))
    }

    @Test
    fun `Tagalog consonant-final numeral takes a separate na - apat na aso`() {
        assertEquals("apat na aso", grammar.phrase(item("dog"), count = 4, language = Language.Tagalog))
    }
}
