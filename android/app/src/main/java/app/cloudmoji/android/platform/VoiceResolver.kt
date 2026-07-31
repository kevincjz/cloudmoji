package app.cloudmoji.android.platform

import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.LanguageMeta

/**
 * Picks the best available voice for a language.
 *
 * Ported from iOS CloudmojiCore's `VoiceResolver`. Walks the language's
 * prefix chain (e.g. Tagalog's `fil`, `tl`, `ms`, `id`, `es`) in order and
 * takes the first tier with any voice. That is what stops a device with no
 * Filipino voice falling through to the engine's English default — which
 * mispronounces Tagalog badly — and lands it on Malay instead, which shares
 * Tagalog's vowels and its "ng".
 */
class VoiceResolver(languages: List<LanguageMeta>) {
    private val prefixes: Map<Language, List<String>> = languages.associate { it.id to it.voicePrefixes }
    private val speechTags: Map<Language, String> = languages.associate { it.id to it.speech }

    /** The BCP-47 tag handed to the platform TTS engine for [language]. */
    fun speechTag(language: Language): String = speechTags[language] ?: language.code

    /**
     * Best match for [language] among [voices], or `null` when the device has
     * nothing anywhere in the language's fallback chain — a caller must not
     * mislabel a word by falling back to English or to whatever the engine
     * feels like.
     */
    fun pick(voices: List<VoiceDescribing>, language: Language): VoiceDescribing? {
        val chain = prefixes[language] ?: listOf(language.code)

        var tier: List<VoiceDescribing> = emptyList()
        for (prefix in chain) {
            // An exact tag match or a "prefix-"-subtagged one — never a bare
            // prefix match, or "tlh" (Klingon's real IANA subtag) would steal
            // Tagalog's "tl" tier from Malay/Indonesian.
            tier = voices.filter { it.lang == prefix || it.lang.startsWith("$prefix-") }
            if (tier.isNotEmpty()) break
        }
        if (tier.isEmpty()) return null

        // Prefer an exact tag match, then a female-sounding name, then the first.
        val exact = tier.filter { it.lang == speechTag(language) }
        val pool = exact.ifEmpty { tier }
        return pool.firstOrNull { voice ->
            val name = voice.name.lowercase()
            femaleHints.any { name.contains(it) }
        } ?: pool.firstOrNull()
    }

    companion object {
        private val femaleHints = listOf(
            "female", "samantha", "karen", "tessa",
            "tingting", "sinji", "amira", "kyoko", "o-ren", "rosa",
        )
    }
}
