package app.cloudmoji.android.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * Thin binding of [AudioFocusSystem] to `android.media.AudioManager`.
 *
 * `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` mirrors the intent behind iOS's
 * `AVAudioSession.Category.playback` with `.duckOthers`: Cloudmoji speaks and
 * plays tones over other apps' audio rather than stopping it, and gives
 * focus straight back the moment nothing of ours needs it.
 *
 * Not host-testable — it binds to a real system service — which is exactly
 * why [AudioFocusOwner]'s reference-counting/pairing logic lives in a
 * separate, platform-neutral class that is.
 */
class AndroidAudioFocusSystem(
    context: Context,
    /**
     * Called when the platform notifies this app that focus changed after
     * having been granted — most importantly [AudioManager.AUDIOFOCUS_LOSS]
     * (someone else needs it and is not giving it back) and the transient
     * variants. Defaults to a no-op for callers with nothing to react to
     * (previews, tests); [app.cloudmoji.android.CloudmojiApplication] wires
     * the production policy — see [audioFocusLossAction] for what to do with
     * each focus-change constant, decided as Task 10's own "ledgered
     * decision" this parameter's doc used to defer. `AudioFocusRequest.Builder.build()`
     * requires a listener to be set at all — omitting one throws
     * `IllegalStateException` at construction, which is why this parameter
     * exists rather than being left out entirely.
     */
    private val onFocusChange: (Int) -> Unit = {},
    /**
     * `AudioManager`'s own docs do not guarantee which thread delivers
     * `OnAudioFocusChangeListener` callbacks on every OEM/API level, and
     * everything downstream of [onFocusChange] in production —
     * [AudioFocusOwner]'s unsynchronized active-client set,
     * [SpeechController]'s generation counter, [ToneDirector]'s
     * `isAttached` — assumes single-threaded access, the same contract
     * [AndroidSpeechEngine] upholds for `TextToSpeech`'s own callbacks.
     * Routing through a [CallbackPoster] here reproduces that guarantee
     * rather than trusting undocumented behaviour. Defaults to the real
     * main-thread-confining implementation; a test would supply
     * [InlineCallbackPoster].
     */
    private val poster: CallbackPoster = AndroidMainThreadPoster(),
) : AudioFocusSystem {
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val focusRequest = AudioFocusRequest
        .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setOnAudioFocusChangeListener { focusChange -> poster.post { onFocusChange(focusChange) } }
        .build()

    override fun requestTransientDuckFocus(): Boolean =
        audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    override fun abandonFocus() {
        audioManager.abandonAudioFocusRequest(focusRequest)
    }
}
