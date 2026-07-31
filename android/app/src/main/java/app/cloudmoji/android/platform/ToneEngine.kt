package app.cloudmoji.android.platform

/**
 * What [ToneDirector] needs a tone engine to do — the Android analogue of
 * iOS's `ToneEngineDriving` protocol (`AudioDirector.swift`). A separate
 * interface, rather than [AndroidToneEngine] directly, so [ToneDirector]'s
 * attach/detach/restart-after-interruption arbitration is testable with a
 * fake instead of a real `android.media.AudioTrack`, which needs a device's
 * audio stack and takes real time to build eight tracks.
 */
interface ToneEngineDriving {
    /** Whether the engine has been built and is ready to play. */
    val isRunning: Boolean

    /** Builds the pads on first use if needed, and makes them ready to play.
     * Idempotent, and silent on failure — see [AndroidToneEngine]. */
    fun start()

    /** Silences whatever is currently sounding. Does not tear the pads down
     * — see [AndroidToneEngine]'s own doc for why Android keeps them built. */
    fun stop()

    /** Plays pad [index] from the pre-built set. Out-of-range, and any call
     * before [start] has actually built anything, does nothing. */
    fun playTone(index: Int)
}
