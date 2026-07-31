package app.cloudmoji.android.model

import app.cloudmoji.android.data.EmojiRepository
import app.cloudmoji.android.data.EmojiRepositoryLoader
import app.cloudmoji.android.data.TestCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * Ported from
 * `ios/CloudmojiCore/Tests/CloudmojiCoreTests/CountingGrammarEnglishTests.swift`.
 */
class CountingGrammarEnglishTest {

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
    fun `singular keeps the bare noun`() {
        assertEquals("one dog", grammar.phrase(item("dog"), count = 1, language = Language.English))
    }

    @Test
    fun `regular plurals add s`() {
        assertEquals("two dogs", grammar.phrase(item("dog"), count = 2, language = Language.English))
    }

    @Test
    fun `irregular plurals come from the data, not the rule`() {
        assertEquals("two teeth", grammar.phrase(item("tooth"), count = 2, language = Language.English))
        assertEquals("two mice", grammar.phrase(item("mouse"), count = 2, language = Language.English))
    }

    @Test
    fun `fish does not gain an s`() {
        assertEquals("three fish", grammar.phrase(item("fish"), count = 3, language = Language.English))
    }

    @Test
    fun `sibilant endings take es`() {
        val bus = Countable(emoji = "🚌", en = "bus", enPlural = null, zh = "辆巴士", ms = "buah bas", ja = "バス", tl = "bus")
        assertEquals("two buses", grammar.phrase(bus, count = 2, language = Language.English))
    }

    @Test
    fun `consonant-y becomes ies, vowel-y does not`() {
        val berry = Countable(emoji = "🫐", en = "berry", enPlural = null, zh = "颗蓝莓", ms = "biji beri", ja = "ベリー", tl = "berry")
        val toy = Countable(emoji = "🧸", en = "toy", enPlural = null, zh = "个玩具", ms = "buah mainan", ja = "おもちゃ", tl = "laruan")
        assertEquals("two berries", grammar.phrase(berry, count = 2, language = Language.English))
        assertEquals("two toys", grammar.phrase(toy, count = 2, language = Language.English))
    }

    @Test
    fun `every shipped countable pluralises without a doubled s`() {
        for (countable in repo.countables) {
            val phrase = grammar.phrase(countable, count = 2, language = Language.English)
            assertFalse("${countable.en} -> $phrase", phrase.endsWith("ss"))
        }
    }

    @Test
    fun `the plural of every noun whose plural is not simply plus s is pinned literally`() {
        // `every shipped countable pluralises without a doubled s` above is a
        // weak invariant: it only catches a doubled trailing "s" ("mangoss"),
        // not a wrong-but-plausible plural ("mangos" instead of "mangoes").
        // Pin the exact expected form for every shipped noun whose plural
        // isn't the regular "+s" case.
        val expected = listOf(
            "fish" to "fish",
            "butterfly" to "butterflies",
            "strawberry" to "strawberries",
            "peach" to "peaches",
            "bus" to "buses",
            "tooth" to "teeth",
            "dress" to "dresses",
            "candy" to "candies",
            "mouse" to "mice",
        )
        for ((noun, plural) in expected) {
            assertEquals(
                "$noun -> expected \"two $plural\"",
                "two $plural",
                grammar.phrase(item(noun), count = 2, language = Language.English),
            )
        }
    }
}
