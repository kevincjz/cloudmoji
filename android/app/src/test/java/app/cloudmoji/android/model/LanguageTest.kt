package app.cloudmoji.android.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [Language.next] — the one-tap language cycle behind `ModeHeader`'s language
 * button. Ported from the cases iOS's `AppModel.nextLanguage(after:in:)` is
 * exercised against.
 */
class LanguageTest {

    @Test
    fun `advances to the next language in the enabled list`() {
        assertEquals(
            Language.Chinese,
            Language.next(after = Language.English, enabled = listOf(Language.English, Language.Chinese, Language.Malay)),
        )
    }

    @Test
    fun `wraps from the last enabled language back to the first`() {
        assertEquals(
            Language.English,
            Language.next(after = Language.Malay, enabled = listOf(Language.English, Language.Chinese, Language.Malay)),
        )
    }

    @Test
    fun `a single enabled language cycles to itself`() {
        assertEquals(
            Language.English,
            Language.next(after = Language.English, enabled = listOf(Language.English)),
        )
    }

    @Test
    fun `a current language not present in the enabled list falls back to the first enabled one`() {
        assertEquals(
            Language.Chinese,
            Language.next(after = Language.Japanese, enabled = listOf(Language.Chinese, Language.Malay)),
        )
    }

    @Test
    fun `an empty enabled list leaves the current language unchanged`() {
        assertEquals(
            Language.English,
            Language.next(after = Language.English, enabled = emptyList()),
        )
    }
}
