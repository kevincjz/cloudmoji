package app.cloudmoji.android.platform

import android.os.Handler
import android.os.Looper

/**
 * The real implementation of [CallbackPoster]'s contract: confines a
 * callback to the main thread.
 *
 * Runs [post]'s action immediately when already called from the main thread
 * — the common case, since every direct call this app makes into
 * `AndroidSpeechEngine` (`speak`/`stop`/`voices`) already originates on the
 * main/UI thread — and posts to the main [Looper] otherwise, which is the
 * off-thread `TextToSpeech` callback case this class exists for.
 *
 * Not host-testable — it binds to a real `Looper` — which is exactly why
 * [CallbackPoster] is a seam tests can substitute [InlineCallbackPoster] for.
 */
class AndroidMainThreadPoster : CallbackPoster {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun post(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }
}
