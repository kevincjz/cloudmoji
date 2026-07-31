package app.cloudmoji.android.platform

/** Which internal audio producer is asking [AudioFocusOwner] to hold focus. */
enum class AudioFocusClient { SPEECH, TONE }

/**
 * Seam over the platform's actual audio-focus API (`AudioManager` on
 * Android), so [AudioFocusOwner]'s request/abandon-pairing logic is
 * host-testable without it. [AndroidAudioFocusSystem] is the real one.
 */
interface AudioFocusSystem {
    /** Requests transient-may-duck focus from the platform. Returns whether
     * it was granted. */
    fun requestTransientDuckFocus(): Boolean

    /** Releases focus back to the platform. Safe to call when not held. */
    fun abandonFocus()
}

/**
 * The one place in the app that asks the platform for transient-may-duck
 * audio focus — "transient" because a spoken word or a pad tone is over in a
 * second or two, "may-duck" so a parent's podcast or nursery-rhyme playlist
 * dips under Cloudmoji rather than stopping outright.
 *
 * Shared by every internal audio producer: speech now
 * ([AudioFocusClient.SPEECH], wired through [AndroidSpeechEngine]), and Task
 * 10's instrument/tone playback later ([AudioFocusClient.TONE]). Multiple
 * producers can be "active" from the app's point of view at once — TTS and
 * instrument tones are independent subsystems, and nothing stops both
 * playing together — but the platform only ever sees one request. This class
 * reference-counts active clients and calls [system] only on the 0→1
 * transition (request) and the 1→0 transition (abandon); that pairing
 * invariant holds whatever order clients start and stop in, and however many
 * are active at once.
 *
 * **Threading: not thread-safe** — [activeClients] is a plain, unsynchronized
 * `MutableSet`. Every caller must arrive on one confined thread, the same
 * contract `SpeechController`'s doc describes: [AndroidSpeechEngine] is the
 * only caller today, and it upholds this by routing its TTS callbacks
 * through a [CallbackPoster] before ever touching this class.
 */
class AudioFocusOwner(private val system: AudioFocusSystem) {
    private val activeClients = mutableSetOf<AudioFocusClient>()

    /** Whether the app currently holds platform audio focus. */
    val isHeld: Boolean get() = activeClients.isNotEmpty()

    /**
     * [client] wants to play. Idempotent per client — calling it twice in a
     * row for the same client without an intervening [release] does not
     * re-request focus from the platform. Returns whether focus is held once
     * this call returns; `false` only when [client] was the sole reason
     * focus would newly be requested and the platform denied it.
     */
    fun request(client: AudioFocusClient): Boolean {
        if (client in activeClients) return true
        val wasIdle = activeClients.isEmpty()
        if (wasIdle && !system.requestTransientDuckFocus()) return false
        activeClients += client
        return true
    }

    /** [client] is done. A no-op when [client] is not currently held. */
    fun release(client: AudioFocusClient) {
        if (client !in activeClients) return
        activeClients -= client
        if (activeClients.isEmpty()) system.abandonFocus()
    }

    /**
     * Forcibly clears every active client's hold on focus, without telling
     * [system] to abandon it. The one caller is
     * [app.cloudmoji.android.CloudmojiApplication]'s
     * [app.cloudmoji.android.platform.audioFocusLossAction] handling
     * ([AudioFocusLossAction.STOP]): by the time that notification arrives,
     * the platform has *already* taken focus away, so calling
     * [AudioFocusSystem.abandonFocus] again would be asking it to give back
     * something it took for itself, not something Cloudmoji is giving up.
     * This just re-syncs this class's own bookkeeping with that reality, so
     * the next [request] asks the platform fresh instead of believing focus
     * is still held when it is not.
     */
    fun releaseAll() {
        activeClients.clear()
    }
}
