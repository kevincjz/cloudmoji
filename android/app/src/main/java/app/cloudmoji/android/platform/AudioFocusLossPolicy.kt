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
 * ([STOP][AudioFocusLossAction.STOP]): something else now holds focus.
 * Cloudmoji's own sounds are ~1.2s tone blips ([ToneBuffer.duration]) or a
 * few seconds of a spoken word at most — there is nothing worth pausing and
 * resuming later, unlike a music player mid-song — so a full stop plus
 * letting the very next tap re-request focus fresh is the whole policy. This
 * is the same "recovery checked at the point of use" shape as iOS, just with
 * an explicit stop bolted on for the half iOS gets for free.
 *
 * [AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK] is deliberately a
 * [NONE][AudioFocusLossAction.NONE]. Cloudmoji only ever *asks* for
 * `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` (so other apps duck under *it*, per
 * [AndroidAudioFocusSystem]'s own doc); implementing the reciprocal case —
 * ducking Cloudmoji's own tones/speech under someone else's momentary sound —
 * would need a volume-ramp mechanism this app has no other use for, to cover
 * a corner case a full stop already handles safely, if more bluntly.
 *
 * Anything else — notably the `AUDIOFOCUS_GAIN*` family, delivered when
 * focus comes back — is also [NONE][AudioFocusLossAction.NONE]: regaining
 * focus does not by itself restart anything, matching iOS's "silent on
 * failure, recovered lazily on the next tap" rule. There is no queued tone
 * or word waiting to resume.
 */
enum class AudioFocusLossAction { STOP, NONE }

fun audioFocusLossAction(focusChange: Int): AudioFocusLossAction = when (focusChange) {
    AudioManager.AUDIOFOCUS_LOSS,
    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
    -> AudioFocusLossAction.STOP
    else -> AudioFocusLossAction.NONE
}
