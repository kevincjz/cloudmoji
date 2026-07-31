package app.cloudmoji.android.platform

import android.media.AudioManager

/**
 * What Cloudmoji does when the platform notifies it that audio focus was
 * lost after having been granted — the "ledgered decision" this task's brief
 * hands to whichever task first wires a second [AudioFocusClient] alongside
 * speech. [AndroidAudioFocusSystem]'s own doc explains why that class cannot
 * decide this itself (it is not host-testable), which is why the decision
 * lives here instead, as a plain, host-testable function.
 *
 * iOS's `AudioDirector` never listens for an interruption notification at
 * all: `AVAudioSession`'s interruption already silences the app's own output
 * at the hardware level the instant it happens, so the only work left for
 * the app is *recovery* — `AudioDirector.restartIfStalled`, checked lazily on
 * the next tap, once the interruption has already ended. Android offers no
 * equivalent free lunch: losing focus does not stop an `AudioTrack` or a
 * `TextToSpeech` utterance already in flight, so a well-behaved app has to
 * stop itself, or it keeps talking or tooting straight over the phone call
 * or app that just took focus. That gap is what this policy closes.
 *
 * [AudioManager.AUDIOFOCUS_LOSS] / [AudioManager.AUDIOFOCUS_LOSS_TRANSIENT]
 * ([STOP][AudioFocusLossAction.STOP]): something else now holds focus. Stop
 * everything, and re-sync [AudioFocusOwner]'s bookkeeping with the fact that
 * the platform has already taken focus away.
 *
 * [AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK] is deliberately a
 * [NONE][AudioFocusLossAction.NONE]. Cloudmoji only ever *asks* for
 * `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` (so other apps duck under *it*, per
 * [AndroidAudioFocusSystem]'s own doc); implementing the reciprocal case —
 * ducking Cloudmoji's own tones/speech under someone else's momentary sound —
 * would need a volume-ramp mechanism this app has no other use for, to cover
 * a corner case a full stop already handles safely, if more bluntly.
 *
 * The `AUDIOFOCUS_GAIN*` family, delivered when focus comes back, is
 * [RESUME][AudioFocusLossAction.RESUME].
 *
 * **This used to be [NONE][AudioFocusLossAction.NONE], and the reasoning
 * recorded here for it was true right up until Sleepy Cloud existed.** It
 * said Cloudmoji's own sounds are "~1.2s tone blips ([ToneBuffer.duration])
 * or a few seconds of a spoken word at most — there is nothing worth pausing
 * and resuming later", so a stop plus lazy recovery on the next tap was the
 * whole policy, matching iOS's `AudioDirector.restartIfStalled`.
 *
 * Sleepy Cloud breaks both halves of that. Its ambience
 * ([ToneDirector.playSleepNoise]) is a **continuous loop that runs for up to
 * ten minutes**, and the screen has no next tap: a child lying still and
 * breathing along is the entire interaction. So a notification sound
 * arriving *while the app stays in the foreground* — no `ON_PAUSE`, so none
 * of `SleepyCloudScreen`'s lifecycle handling fires — would take focus, this
 * policy would stop the track, and nothing would ever start it again. The
 * cloud would go on breathing and the room would go on darkening with the
 * advertised sleep sounds silently dead for the rest of the session.
 *
 * Resuming is safe for the other clients precisely because
 * [ToneDirector.resumeAfterFocusGain] does not resume "whatever was
 * playing": it restarts the ambience **only** if some screen still wants it
 * ([ToneDirector.isSleepNoiseWanted]). A pad tone or a spoken word that was
 * cut off has genuinely nothing worth resuming, and still gets none.
 */
enum class AudioFocusLossAction { STOP, RESUME, NONE }

fun audioFocusLossAction(focusChange: Int): AudioFocusLossAction = when (focusChange) {
    AudioManager.AUDIOFOCUS_LOSS,
    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
    -> AudioFocusLossAction.STOP

    AudioManager.AUDIOFOCUS_GAIN,
    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
    -> AudioFocusLossAction.RESUME

    else -> AudioFocusLossAction.NONE
}
