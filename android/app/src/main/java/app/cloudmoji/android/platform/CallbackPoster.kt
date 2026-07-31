package app.cloudmoji.android.platform

/**
 * Confines a callback onto one thread.
 *
 * Exists because `android.speech.tts.TextToSpeech`'s async init handshake
 * and its `UtteranceProgressListener` (`onStart`/`onDone`/`onError`) are not
 * guaranteed to arrive on the caller's thread — a well-documented TTS
 * gotcha; they commonly land on a binder thread instead. Everything
 * downstream of those callbacks assumes single-threaded access: `AndroidSpeechEngine`'s
 * own `isReady`/`pendingFinish`/voice cache, [AudioFocusOwner]'s
 * unsynchronized active-client set, and — via the completion callback chain
 * those TTS callbacks ultimately invoke — [SpeechController]'s
 * `generation`/`index`/`watchdogHandle`. That is the same single-threaded
 * assumption iOS gets for free from `SpeechController`/`SystemSpeechEngine`
 * being `@MainActor`-isolated at compile time (`SystemSpeechEngine.speechSynthesizer(_:didFinish:)`
 * explicitly hops back with `Task { @MainActor in ... }` for exactly this
 * reason). Posting every one of those TTS callbacks through one
 * [CallbackPoster] reproduces that guarantee on Android.
 *
 * Kept as an injectable seam — rather than `AndroidSpeechEngine` calling
 * `Handler(Looper.getMainLooper())` directly — so a test can supply
 * [InlineCallbackPoster] instead of a real `Looper`. `AndroidMainThreadPoster`
 * is the production implementation.
 */
interface CallbackPoster {
    fun post(action: () -> Unit)
}

/** Runs [action] synchronously, on the calling thread — no hopping. For
 * tests, and for any composition root that already guarantees
 * single-threaded delivery some other way. */
object InlineCallbackPoster : CallbackPoster {
    override fun post(action: () -> Unit) = action()
}
