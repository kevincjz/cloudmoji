package app.cloudmoji.android.platform

import app.cloudmoji.android.data.EmojiRepositoryLoader
import app.cloudmoji.android.data.TestCatalog
import app.cloudmoji.android.model.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Ported from `ios/CloudmojiCore/Tests/CloudmojiCoreTests/VoiceResolverTests.swift`.
 * Uses the real generated catalogue's `LanguageMeta` rows (same
 * `voicePrefixes`/`speech` values as iOS — see `src/data/languages.ts`), not
 * a hand-rolled fixture, so a drift in those rows would fail this suite too.
 */
class VoiceResolverTest {
    private lateinit var resolver: VoiceResolver

    @Before
    fun setUp() {
        resolver = VoiceResolver(EmojiRepositoryLoader.fromJson(TestCatalog.json).languages)
    }

    private data class FakeVoice(override val lang: String, override val name: String) : VoiceDescribing

    /** A plausible notched-phone voice set: no Filipino, which is the norm. */
    private val appleish = listOf(
        FakeVoice(lang = "en-US", name = "Samantha"),
        FakeVoice(lang = "en-GB", name = "Daniel"),
        FakeVoice(lang = "zh-CN", name = "Tingting"),
        FakeVoice(lang = "ja-JP", name = "Kyoko"),
        FakeVoice(lang = "ms-MY", name = "Amira"),
        FakeVoice(lang = "id-ID", name = "Damayanti"),
        FakeVoice(lang = "es-ES", name = "Monica"),
    )

    @Test
    fun `with no Filipino voice, Tagalog falls to Malay and never to English`() {
        val picked = requireNotNull(resolver.pick(appleish, Language.Tagalog))
        assertEquals("ms-MY", picked.lang)
        assertFalse(picked.lang.startsWith("en"))
    }

    @Test
    fun `a real Filipino voice wins over the fallback`() {
        val voices = appleish + FakeVoice(lang = "fil-PH", name = "Rosa")
        assertEquals("Rosa", requireNotNull(resolver.pick(voices, Language.Tagalog)).name)
    }

    @Test
    fun `tl-PH tagging is accepted as well as fil-PH`() {
        val voices = appleish + FakeVoice(lang = "tl-PH", name = "Angelo")
        assertEquals("Angelo", requireNotNull(resolver.pick(voices, Language.Tagalog)).name)
    }

    @Test
    fun `Malay falls back to Indonesian`() {
        val voices = appleish.filterNot { it.lang.startsWith("ms") }
        assertEquals("id-ID", requireNotNull(resolver.pick(voices, Language.Malay)).lang)
        // and Tagalog then lands on Indonesian too, still not English
        assertEquals("id-ID", requireNotNull(resolver.pick(voices, Language.Tagalog)).lang)
    }

    @Test
    fun `the other four languages are unaffected`() {
        assertEquals("Samantha", requireNotNull(resolver.pick(appleish, Language.English)).name)
        assertEquals("Tingting", requireNotNull(resolver.pick(appleish, Language.Chinese)).name)
        assertEquals("Kyoko", requireNotNull(resolver.pick(appleish, Language.Japanese)).name)
        assertEquals("Amira", requireNotNull(resolver.pick(appleish, Language.Malay)).name)
    }

    @Test
    fun `an English-only device resolves to nothing rather than mislabelling`() {
        val voices = listOf(FakeVoice(lang = "en-US", name = "Samantha"))
        assertNull(resolver.pick(voices, Language.Tagalog))
        assertNull(resolver.pick(voices, Language.Japanese))
        assertNull(resolver.pick(voices, Language.Chinese))
        assertEquals("Samantha", resolver.pick(voices, Language.English)?.name)
    }

    @Test
    fun `an exact language match beats a looser one in the same tier`() {
        val voices = listOf(
            FakeVoice(lang = "en-GB", name = "Daniel"),
            FakeVoice(lang = "en-US", name = "Alex"),
        )
        assertEquals("en-US", requireNotNull(resolver.pick(voices, Language.English)).lang)
    }

    @Test
    fun `a voice tagged tlh (Klingon) does not steal the tl (Tagalog) tier`() {
        // "tlh" is Klingon's real IANA-registered language subtag. A bare
        // prefix match would match it -- "tlh" starts with "tl" -- and
        // wrongly seat it in Tagalog's tier before Malay/Indonesian ever get
        // a look in. The chain must require an exact tag or a
        // "tl-"-prefixed subtag, so a same-lettered but unrelated language
        // never steals the tier.
        val voices = appleish + FakeVoice(lang = "tlh", name = "Worf")
        val picked = requireNotNull(resolver.pick(voices, Language.Tagalog))
        assertEquals("ms-MY", picked.lang)
    }

    @Test
    fun `speechTag returns the BCP-47 tag for each language`() {
        assertEquals("en-US", resolver.speechTag(Language.English))
        assertEquals("zh-CN", resolver.speechTag(Language.Chinese))
        assertEquals("ms-MY", resolver.speechTag(Language.Malay))
        assertEquals("ja-JP", resolver.speechTag(Language.Japanese))
        assertEquals("fil-PH", resolver.speechTag(Language.Tagalog))
    }

    @Test
    fun `no voices anywhere resolves to null, not a crash`() {
        assertNull(resolver.pick(emptyList(), Language.English))
        assertNotNull(resolver) // sanity: constructing the resolver itself did not throw
    }
}
