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
class AndroidAudioFocusSystem(context: Context) : AudioFocusSystem {
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
        .build()

    override fun requestTransientDuckFocus(): Boolean =
        audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    override fun abandonFocus() {
        audioManager.abandonAudioFocusRequest(focusRequest)
    }
}
