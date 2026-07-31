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
}
