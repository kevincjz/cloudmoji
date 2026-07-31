package app.cloudmoji.android.data

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fail-fast contract requirement 4 of the Task 1 brief describes: a
 * generated file whose counts or codes don't match what this port expects
 * must throw and name what broke, never decode into a silently incomplete
 * repository.
 */
class EmojiCatalogValidationTest {

    @Test
    fun `the real generated catalogue decodes and validates cleanly`() {
        // Sanity check the fixtures below against reality: if this ever
        // fails, the generator's shape changed and every synthetic fixture
        // in this file needs re-deriving, not just this assertion.
        EmojiRepositoryLoader.fromJson(TestCatalog.json)
    }

    @Test
    fun `a short emoji count throws and names the count that broke`() {
        val exception = assertThrows(EmojiCatalogException::class.java) {
            EmojiRepositoryLoader.fromJson(minimalValidJson)
        }
        assertTrue(exception.message.orEmpty().contains("1 emojis, expected 200"))
    }

    @Test
    fun `an unrecognised top-level key fails to decode instead of being silently ignored`() {
        // Deliberately the real, fully valid catalogue (every count correct)
        // plus one extra key — not a short fixture. A short fixture would
        // still throw via the count-validation gate even if unknown-key
        // strictness were silently disabled, which would make this test pass
        // for the wrong reason. This one only throws if strict decoding is
        // actually wired up.
        val exception = assertThrows(EmojiCatalogException::class.java) {
            EmojiRepositoryLoader.fromJson(realJsonWithUnknownTopLevelKey)
        }
        assertTrue(exception.message.orEmpty().contains("unknownField"))
    }

    @Test
    fun `an unknown category id fails loudly and names the emoji and the category`() {
        val exception = assertThrows(EmojiCatalogException::class.java) {
            EmojiRepositoryLoader.fromJson(jsonWithUnknownCategory)
        }
        assertTrue(exception.message.orEmpty().contains("mythical"))
        assertTrue(exception.message.orEmpty().contains("🦄"))
    }

    @Test
    fun `an unknown language code fails loudly and names the code`() {
        val exception = assertThrows(EmojiCatalogException::class.java) {
            EmojiRepositoryLoader.fromJson(jsonWithUnknownLanguageCode)
        }
        assertTrue(exception.message.orEmpty().contains("xx"))
    }

    @Test
    fun `malformed JSON fails to decode`() {
        assertThrows(EmojiCatalogException::class.java) {
            EmojiRepositoryLoader.fromJson("{ not valid json")
        }
    }
}

/** Structurally complete but far short of every expected count. */
private val minimalValidJson = """
{
  "version": 1,
  "languages": [
    { "id": "en", "short": "EN", "name": "English", "speech": "en-US", "voicePrefixes": ["en"] }
  ],
  "categories": [
    { "id": "all", "icon": "🌟", "labels": { "en": "All" } }
  ],
  "emojis": [
    { "emoji": "🍎", "cat": "fruits", "en": "apple", "zh": "苹果", "ms": "epal", "ja": "りんご", "tl": "mansanas" }
  ],
  "countables": [],
  "numberWords": { "en": ["one"] },
  "animalSounds": {}
}
""".trimIndent()

/**
 * The real generated catalogue — every count correct — with one extra,
 * unrecognised top-level key spliced in right after the opening brace.
 */
private val realJsonWithUnknownTopLevelKey: String by lazy {
    TestCatalog.json.replaceFirst("{", "{\n  \"unknownField\": true,")
}

private val jsonWithUnknownCategory = """
{
  "version": 1,
  "languages": [
    { "id": "en", "short": "EN", "name": "English", "speech": "en-US", "voicePrefixes": ["en"] }
  ],
  "categories": [],
  "emojis": [
    { "emoji": "🦄", "cat": "mythical", "en": "unicorn", "zh": "x", "ms": "x", "ja": "x", "tl": "x" }
  ],
  "countables": [],
  "numberWords": { "en": [] },
  "animalSounds": {}
}
""".trimIndent()

private val jsonWithUnknownLanguageCode = """
{
  "version": 1,
  "languages": [
    { "id": "xx", "short": "XX", "name": "Nowhere", "speech": "xx-XX", "voicePrefixes": ["xx"] }
  ],
  "categories": [],
  "emojis": [],
  "countables": [],
  "numberWords": {},
  "animalSounds": {}
}
""".trimIndent()
