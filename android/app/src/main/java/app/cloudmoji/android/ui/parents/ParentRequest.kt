package app.cloudmoji.android.ui.parents

/**
 * What a grown-up is about to be allowed to do, and the one sentence the gate
 * shows them while they answer the arithmetic. Ported from iOS
 * `ContentView.swift`'s `RootContent.ParentRequest`.
 *
 * The gate is one overlay with one pass/cancel pair, so the *reason* it was
 * opened has to travel with it — otherwise every door would need its own gate,
 * and a second gate is a second thing to get wrong. `CloudmojiApp`'s
 * `completeParentRequest` is the single place this is acted on, which is what
 * keeps `route = ParentRoute` at exactly one write site (see
 * `sanitizeRestoredRoute`'s own doc for why that matters).
 *
 * iOS carries a fourth case, `.fullCloudmoji`, for the paid-version discovery
 * tile on its launcher. Android has no such tile — Full Cloudmoji is reached
 * from inside the Grown-ups panel, already behind this gate — so porting it
 * would mean inventing a doorway that does not exist.
 */
enum class ParentRequest(val explanation: String) {
    Settings(
        "Settings let you choose Cloudmoji's sound, languages, categories and learning range.",
    ),

    /** Photos' camera tile, before Android's own permission dialog. The system
     * prompt is itself shown only after the grown-up has answered
     * Cloudmoji's gate. */
    CameraPermission(
        "Photos needs camera access. A grown-up must continue before Android asks for permission.",
    ),

    /** Photos' recovery card, after a refusal Android will not re-prompt for.
     * The only outbound link anything a child taps can lead to, and it is on
     * the far side of this gate. */
    CameraSettings(
        "Camera access was turned off. A grown-up can open Android Settings and allow it again.",
    ),

    ;

    companion object {
        /**
         * The request behind a saved [name], defaulting to [Settings] for
         * anything unrecognised.
         *
         * `rememberSaveable` stores this as a plain `String` so it keeps using
         * the ordinary default `Saver` (the same reason `CloudmojiApp` keeps
         * its gate index a plain `Int`), which means a value can come back out
         * of a `Bundle` written by an older build with a case this one no
         * longer has. Falling back to [Settings] rather than throwing is the
         * safe direction: the gate is still answered either way, and the worst
         * outcome is a grown-up landing in the settings panel instead of a
         * permission dialog.
         */
        fun fromName(name: String): ParentRequest =
            entries.firstOrNull { it.name == name } ?: Settings
    }
}
