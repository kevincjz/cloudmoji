package app.cloudmoji.android.model

/**
 * Metadata describing a supported [Language]: display strings and the voice
 * hints a future TTS adapter will use to pick a system voice. Mirrors iOS
 * CloudmojiCore's `LanguageMeta`.
 */
data class LanguageMeta(
    val id: Language,
    /** The language's own short name, shown on the toggle: EN, 中文, BM, 日本語, TL. */
    val short: String,
    /** English name, shown in the picker so a parent can find it. */
    val name: String,
    /** BCP-47 code handed to the platform TTS engine. */
    val speech: String,
    /** Ordered voice-language prefixes to try when resolving a system voice. */
    val voicePrefixes: List<String>,
)
