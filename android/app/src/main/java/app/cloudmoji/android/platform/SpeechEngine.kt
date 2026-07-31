package app.cloudmoji.android.platform

/** Structural view of a voice, so selection is testable without the Android
 * TTS stack. Mirrors iOS CloudmojiCore's `VoiceDescribing`. */
interface VoiceDescribing {
    val lang: String
    val name: String
}

/** What [SpeechController] hands the platform engine for one utterance.
 * Mirrors iOS CloudmojiCore's `SpeechUtterance`. */
data class SpeechUtterance(
    val text: String,
    val languageTag: String,
    val voice: VoiceDescribing?,
    val onFinish: () -> Unit,
)

/**
 * Seam over the platform TTS engine so queue behaviour is testable without
 * audio. Mirrors iOS CloudmojiCore's `SpeechEngine` protocol — there it wraps
 * `AVSpeechSynthesizer`; here [AndroidSpeechEngine] wraps
 * `android.speech.tts.TextToSpeech`.
 *
 * Only one utterance is ever in flight — [SpeechController] always stops the
 * engine before it speaks the next one — so an implementation only needs to
 * track a single pending completion callback at a time.
 */
interface SpeechEngine {
    fun voices(): List<VoiceDescribing>
    fun speak(utterance: SpeechUtterance)
    fun stop()

    /**
     * Releases the engine for good — the counterpart to whatever `speak`
     * relies on being bound at construction ([AndroidSpeechEngine] holds a
     * real `android.speech.tts.TextToSpeech` connection). Unlike [stop],
     * which only halts the current utterance and leaves the engine ready for
     * the next `speak`, this ends the engine's life: nothing may call
     * `speak`/`voices`/`stop` on it again afterward.
     *
     * Added alongside [app.cloudmoji.android.CloudmojiApplication] — before
     * that class existed, an [AndroidSpeechEngine] was rebuilt on every
     * screen visit / configuration change with no way to release the
     * previous one at all.
     */
    fun shutdown()
}
