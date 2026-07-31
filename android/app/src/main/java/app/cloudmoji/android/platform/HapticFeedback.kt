package app.cloudmoji.android.platform

/**
 * The taps and rewards a child feels. Ported from iOS's `Haptics`,
 * deliberately **not** tied to the mute setting: muting silences the phone,
 * it does not mean "stop responding to me". Two textures, matching the two
 * things the mascot already distinguishes: a firm knock for "you tapped a
 * thing" ([tap]), and a distinct pattern for "you finished a thing"
 * ([reward]) — the same moments the mascot beams for.
 */
interface HapticFeedback {
    /** One emoji, one count tile — the ordinary tap. */
    fun tap()

    /** A milestone in Words mode, a finished round in Count mode. */
    fun reward()
}

/** Default for tests, previews, and any build with no [android.content.Context]
 * to bind a real vibrator to. */
object NoOpHapticFeedback : HapticFeedback {
    override fun tap() = Unit
    override fun reward() = Unit
}
