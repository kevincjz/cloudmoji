package app.cloudmoji.android.platform

/**
 * Arbitrates Music mode's tone playback — and, since Task 13, Sleepy Cloud's
 * bedtime ambience — against [AudioFocusOwner] and [ToneEngineDriving]: the
 * Android analogue of iOS `AudioDirector` (`AudioDirector.swift`). Android
 * already centralises audio-focus arbitration in [AudioFocusOwner] (Task 4),
 * shared by [AudioFocusClient.SPEECH] and [AudioFocusClient.TONE] alike, so
 * this class does not need iOS's `AudioClient` enum of every mini-app that
 * can hold the engine — [attach]/[detach] alone (below) already answer "is
 * *some* mini-app currently holding the shared engine", and Music and Sleepy
 * Cloud can never both be that mini-app at once, since this app only ever
 * has one route mounted at a time.
 *
 * **One shared director and engine, not one per audio-producing mini-app** —
 * this class's own doc used to say the opposite ("later mini-apps... would
 * get their own small director"), before Sleepy Cloud actually arrived and
 * needed one. Reusing this exact class instead is the more faithful port:
 * iOS's own real `ToneEngine` (`AudioDirector.swift`) attaches the eight pad
 * players *and* the sleep player to the very same `AVAudioEngine` graph in
 * one `build()`, arbitrated by one `AudioDirector` — see
 * [AndroidToneEngine]'s own doc for how [playSleepNoise] reaches the same
 * shared graph on this platform.
 *
 * [attach]/[detach] bracket a visit to the Music screen — `MusicScreen.kt`'s
 * own `DisposableEffect`, mirroring iOS `InstrumentPadView`'s
 * `.onAppear`/`.onDisappear` — or, for Sleepy Cloud, a *running session*
 * specifically (not the whole screen visit, since the picker holds no
 * engine at all) — see `SleepyCloudScreen.kt`'s own `resume`/`pause`,
 * mirroring iOS `SleepyCloudView.startSleepAudio`/`stopSleepAudio`'s own
 * narrower scope. [detach] both stops the engine and gives
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
     * Starts (or restarts, from the beginning) Sleepy Cloud's bedtime
     * ambience. Gated on [isAttached] exactly like [playTone] — Sleepy Cloud
     * shares this director and the engine underneath it with Music (see this
     * class's own doc for why one shared engine, not a parallel director,
     * mirrors iOS's real `ToneEngine`, which attaches pad players *and* the
     * sleep player to the one graph) — so a stray call from a screen that is
     * not currently attached must not spin the engine up or ask the
     * platform for focus, the same reasoning [playTone]'s own doc gives.
     * Mirrors iOS `AudioDirector.playSleepNoise()`.
     */
    fun playSleepNoise() {
        if (!isAttached) return
        if (!focusOwner.request(AudioFocusClient.TONE)) return
        restartIfStalled()
        engine.playSleepNoise()
    }

    /** Stops the ambience without giving up [isAttached] — a mute toggle
     * mid-session silences the loop, but Sleepy Cloud is still the screen
     * holding the engine, so the very next unmute can bring it straight
     * back via [playSleepNoise] without re-attaching. Mirrors iOS
     * `AudioDirector.stopSleepNoise()`. */
    fun stopSleepNoise() {
        if (!isAttached) return
        engine.stopSleepNoise()
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
