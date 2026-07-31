package app.cloudmoji.android.platform

/**
 * Arbitrates Music mode's tone playback against [AudioFocusOwner] and
 * [ToneEngineDriving] — the Android analogue of iOS `AudioDirector`
 * (`AudioDirector.swift`), scoped to instrument tones specifically. Android
 * already centralises audio-focus arbitration in [AudioFocusOwner] (Task 4),
 * shared by [AudioFocusClient.SPEECH] and [AudioFocusClient.TONE] alike, so
 * this class does not need iOS's `AudioClient` enum of every mini-app that
 * can hold the engine — Music is the one caller today, and later mini-apps
 * that make sound of their own kind would get their own small director the
 * same way, still sharing the one [AudioFocusOwner].
 *
 * [attach]/[detach] bracket a visit to the Music screen — `MusicScreen.kt`'s
 * own `DisposableEffect`, mirroring iOS `InstrumentPadView`'s
 * `.onAppear`/`.onDisappear`. [detach] both stops the engine and gives
 * [AudioFocusClient.TONE] back, belt and braces: a tone still ringing over
 * the launcher is the kind of thing a parent notices and cannot explain, the
 * same reasoning iOS's own doc gives for calling `detach()` from two places.
 *
 * [playTone] requests [AudioFocusClient.TONE] fresh on every call — cheap
 * and a no-op once already granted, per [AudioFocusOwner.request]'s own
 * idempotence — rather than only once at [attach] time, so a request denied
 * when the screen first opened (a phone call in progress, say) gets another
 * chance the moment it ends, without the child having to leave and reopen
 * Music. A denial is silent: no crash, no error state, just no sound —
 * matching this project's "no failure states" rule and iOS
 * `ToneEngine.start`'s own "silent on failure" comment. Rapid repeated taps
 * are safe for the same reason: [AudioFocusOwner.request] never re-asks the
 * platform while [AudioFocusClient.TONE] is already held, so mashing pads
 * cannot stack focus requests.
 *
 * **Threading: not thread-safe, by design** — the same contract
 * [AudioFocusOwner] and [SpeechController] both document: every call here
 * must arrive on one confined thread (the app's main/UI thread in
 * production, which is also where Compose delivers touch input).
 */
class ToneDirector(
    private val focusOwner: AudioFocusOwner,
    private val engine: ToneEngineDriving,
) {
    /** Whether Music currently holds the engine. Read-only; mirrors iOS
     * `AudioDirector.client != nil`. */
    var isAttached: Boolean = false
        private set

    fun attach() {
        isAttached = true
        engine.start()
    }

    /** Idempotent — a second call (or a call before [attach]) does nothing,
     * the same guard iOS's `detach()` uses so `.onDisappear` and a later
     * `goHome` can both call it without stopping an already-stopped engine
     * twice. */
    fun detach() {
        if (!isAttached) return
        isAttached = false
        focusOwner.release(AudioFocusClient.TONE)
        engine.stop()
    }

    /** Nothing plays for a screen that is not (or no longer) attached — a
     * stray call from a screen that has already gone must not spin the
     * engine back up or ask the platform for focus. */
    fun playTone(index: Int) {
        if (!isAttached) return
        if (!focusOwner.request(AudioFocusClient.TONE)) return
        restartIfStalled()
        engine.playTone(index)
    }

    /**
     * Recovery, checked at the point of use rather than driven by a platform
     * notification. Mirrors iOS `AudioDirector.restartIfStalled` — see
     * [AndroidToneEngine]'s own doc for how [ToneEngineDriving.isRunning]
     * differs in practice between the two platforms.
     */
    private fun restartIfStalled() {
        if (!engine.isRunning) engine.start()
    }

    /**
     * Silences whatever is currently sounding without giving up the screen's
     * hold on the engine — [isAttached] is untouched, so the very next tap's
     * [restartIfStalled] (via [playTone]) can bring it straight back. The one
     * caller is [CloudmojiApplication]'s audio-focus-loss handling: see
     * [AudioFocusLossAction] for why Android needs this where iOS's
     * `AudioDirector` does not — `AVAudioSession` silences the app's own
     * output for free on an interruption; Android's `AudioManager` does not,
     * and a well-behaved app has to stop itself.
     */
    fun silence() {
        engine.stop()
    }
}
