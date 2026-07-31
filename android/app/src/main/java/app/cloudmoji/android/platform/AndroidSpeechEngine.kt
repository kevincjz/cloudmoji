package app.cloudmoji.android.platform

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * `SpeechController.RATE`/`PITCH` converted into `TextToSpeech`'s scale.
 *
 * Unlike AVFoundation's `AVSpeechUtterance.rate` (0...1, with **0.5** as
 * normal — the trap that shipped a too-fast first iOS build; see the port's
 * `SystemSpeechEngine` doc), `TextToSpeech.setSpeechRate`/`.setPitch` already
 * treat **1.0 as normal**, the same scale the Web Speech API and
 * `SpeechController.RATE`/`PITCH` use. Both constants therefore apply to
 * Android **unchanged** — there is no conversion factor here, unlike the iOS
 * adapter. These live at file scope (not in [AndroidSpeechEngine]'s
 * companion) so a JVM test can assert this scale claim without constructing
 * `android.speech.tts.TextToSpeech`, which throws outside a real Android
 * runtime.
 */
const val ANDROID_UTTERANCE_RATE: Float = SpeechController.RATE
const val ANDROID_UTTERANCE_PITCH: Float = SpeechController.PITCH

/** A `android.speech.tts.Voice`, viewed through [VoiceDescribing]. */
private class AndroidVoice(val voice: android.speech.tts.Voice) : VoiceDescribing {
    override val lang: String = voice.locale.toLanguageTag()
    override val name: String = voice.name
}

/**
 * Binds `android.speech.tts.TextToSpeech` to [SpeechEngine].
 *
 * Not host-testable: `TextToSpeech` binds to an on-device system TTS service
 * and throws when exercised outside a real Android runtime (no Robolectric
 * in this project), so this class has no JVM unit test coverage — see the
 * Task 4 report for what IS covered (the rate/pitch scale constants above,
 * and every queueing/cancellation/mute rule in [SpeechController] and
 * [VoiceResolver], both exercised against fakes).
 */
class AndroidSpeechEngine(
    context: Context,
    /** Optional: routed "around speech" through the shared [AudioFocusOwner],
     * per the plan's single audio-focus owner. `null` (the default) skips
     * focus handling entirely — useful for a preview or a build that has not
     * wired one up yet. */
    private val focusOwner: AudioFocusOwner? = null,
) : SpeechEngine {
    /** Set once `TextToSpeech`'s async init handshake completes. `speak`
     * declines to forward to the engine before this is true and immediately
     * reports the utterance finished instead — the same "no failure state"
     * philosophy as everywhere else in this app: a tap that lands during the
     * sub-second startup race is a quiet miss, not a crash, and not a
     * stranded mascot mood either. */
    private var isReady = false
    private var pendingFinish: (() -> Unit)? = null
    private var cachedVoices: List<VoiceDescribing>? = null

    /** Test/debug seam: how many times the system voice list was actually
     * enumerated. Mirrors iOS's `SystemSpeechEngine.voiceLookupCount`. */
    var voiceLookupCount: Int = 0
        private set

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        isReady = status == TextToSpeech.SUCCESS
    }

    init {
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    focusOwner?.request(AudioFocusClient.SPEECH)
                }

                override fun onDone(utteranceId: String?) = finish()

                @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                override fun onError(utteranceId: String?) = finish()

                override fun onError(utteranceId: String?, errorCode: Int) = finish()
            },
        )
    }

    private fun finish() {
        // Drop the callback before releasing focus or calling out: a late
        // delegate call must not be able to resume a queue that already
        // moved on, and the mascot callback should never observe focus in a
        // half-released state.
        val callback = pendingFinish
        pendingFinish = null
        focusOwner?.release(AudioFocusClient.SPEECH)
        callback?.invoke()
    }

    override fun voices(): List<VoiceDescribing> {
        cachedVoices?.let { return it }
        voiceLookupCount += 1
        val fresh = (tts.voices ?: emptySet()).map { AndroidVoice(it) }
        cachedVoices = fresh
        return fresh
    }

    /** Call when the app returns to the foreground — a parent may have
     * installed a voice in system settings while the app was backgrounded. */
    fun invalidateVoiceCache() {
        cachedVoices = null
    }

    override fun speak(utterance: SpeechUtterance) {
        pendingFinish = utterance.onFinish
        if (!isReady) {
            // Hand the miss straight back rather than stranding a caller's
            // completion callback — a single `SpeechController.speak()` has
            // no watchdog of its own to recover it, unlike a sequence item.
            finish()
            return
        }

        val resolved = (utterance.voice as? AndroidVoice)?.voice
            ?: tts.voices?.firstOrNull { it.locale.toLanguageTag() == utterance.languageTag }
        if (resolved != null) {
            tts.voice = resolved
        } else {
            // No voice resolved anywhere in the language's fallback chain —
            // hand the engine the tag and let it pick for itself, the same
            // fallback iOS's adapter takes.
            tts.language = Locale.forLanguageTag(utterance.languageTag)
        }
        tts.setSpeechRate(ANDROID_UTTERANCE_RATE)
        tts.setPitch(ANDROID_UTTERANCE_PITCH)
        // QUEUE_FLUSH is the cancel-before-speak contract at the engine
        // level: a new utterance always stops whatever the engine was
        // already saying, on top of `SpeechController` already calling
        // `stop()` first.
        tts.speak(utterance.text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    override fun stop() {
        // Drop the callback before stopping: a delegate call can still
        // arrive for the utterance being cancelled, and it must not resume
        // the queue.
        pendingFinish = null
        focusOwner?.release(AudioFocusClient.SPEECH)
        tts.stop()
    }
}
