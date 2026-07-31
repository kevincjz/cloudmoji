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
 * `ios/CloudmojiCore/Tests/CloudmojiCoreTests/CountingGrammarClassifierTests.swift`.
 */
class CountingGrammarClassifierTest {

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
    fun `Chinese joins with no space, classifier already in the noun`() {
        assertEquals("三只狗", grammar.phrase(item("dog"), count = 3, language = Language.Chinese))
        assertEquals("一个苹果", grammar.phrase(item("apple"), count = 1, language = Language.Chinese))
    }

    @Test
    fun `Chinese uses 两 not 二 for two`() {
        val phrase = grammar.phrase(item("dog"), count = 2, language = Language.Chinese)
        assertTrue(phrase.startsWith("两"))
        assertFalse(phrase.startsWith("二"))
    }

    @Test
    fun `Malay joins with a space, penjodoh already in the noun`() {
        assertEquals("tiga ekor anjing", grammar.phrase(item("dog"), count = 3, language = Language.Malay))
        assertEquals("satu biji epal", grammar.phrase(item("apple"), count = 1, language = Language.Malay))
    }

    @Test
    fun `no classifier language ever emits a double space`() {
        for (countable in repo.countables) {
            for (count in 1..10) {
                for (language in listOf(Language.Chinese, Language.Malay)) {
                    val phrase = grammar.phrase(countable, count = count, language = language)
                    assertFalse(
                        "$language ${countable.en}: $phrase",
                        phrase.contains("  "),
                    )
                    assertEquals(phrase, phrase.trim())
                }
            }
        }
    }
}
