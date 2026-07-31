package app.cloudmoji.android.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether there is a camera to use, and whether we may. Ported from iOS
 * `CameraAvailability` (`Views/Photos/CameraController.swift`).
 */
enum class CameraAvailability {
    /** No camera at all — an emulator image built without one, a tablet with
     * none, or a device where an administrator has removed it. */
    Unavailable,

    /** There is one, and the system will still show its permission dialog if
     * asked. */
    NeedsPermission,

    /** A grown-up said no, and Android will no longer show the dialog. Photos
     * replaces the camera tile with a parent-gated recovery card that opens
     * this app's page in Android Settings. */
    Denied,

    Ready,
}

/**
 * What tapping the camera tile does in each state. Ported from iOS
 * `PhotosView.CameraEntryAction`, and the reason it is an enum rather than an
 * `if` inside the tap handler: every authorization state must have an explicit,
 * *useful* answer, because a control that answers a tap with nothing is the
 * failure state `CLAUDE.md` rule 4 forbids.
 */
enum class CameraEntryAction {
    OpenCamera,
    AskParentToRequestPermission,
    AskParentToOpenSettings,

    /** The tile is not drawn at all in this state — an absent control, not a
     * dead one. See `PhotosScreen`'s own camera note. */
    Unavailable,
}

fun cameraEntryAction(availability: CameraAvailability): CameraEntryAction = when (availability) {
    CameraAvailability.Ready -> CameraEntryAction.OpenCamera
    CameraAvailability.NeedsPermission -> CameraEntryAction.AskParentToRequestPermission
    CameraAvailability.Denied -> CameraEntryAction.AskParentToOpenSettings
    CameraAvailability.Unavailable -> CameraEntryAction.Unavailable
}

/**
 * The three inputs Android actually exposes, resolved to one state.
 *
 * iOS reads a single four-valued `AVAuthorizationStatus` in which
 * `.notDetermined` and `.denied` are distinct facts the system remembers.
 * Android has no such value: `checkSelfPermission` answers only granted or
 * not, and "we have asked before" is something the app has to know for itself.
 * [isPermanentlyDenied] is that memory — see [CameraPermissionState] for where
 * it comes from and what it deliberately does *not* try to be.
 */
fun cameraAvailability(
    hasCamera: Boolean,
    isGranted: Boolean,
    isPermanentlyDenied: Boolean,
): CameraAvailability = when {
    !hasCamera -> CameraAvailability.Unavailable
    isGranted -> CameraAvailability.Ready
    isPermanentlyDenied -> CameraAvailability.Denied
    else -> CameraAvailability.NeedsPermission
}

/**
 * Whether a resolved permission request means "the dialog will never appear
 * again".
 *
 * [canAskAgain] is `shouldShowRequestPermissionRationale` read *after* the
 * result: Android sets it true while a further prompt is still possible and
 * false once the user has refused for good. Reading it before a request is the
 * classic mistake — it is also false for a permission that has never been
 * requested at all, which is why this function only ever runs on a result.
 */
fun permanentlyDeniedAfterRequest(granted: Boolean, canAskAgain: Boolean): Boolean =
    !granted && !canAskAgain

/**
 * The app's memory of the camera permission, and the Android stand-in for
 * iOS's `cloudmojiCameraAuthorizationDidChange` notification.
 *
 * Process-scoped (see [app.cloudmoji.android.CloudmojiApplication]) rather
 * than screen-scoped, for two reasons: the permission result arrives at
 * `CloudmojiApp`'s launcher — the only place an `ActivityResultLauncher` can
 * be registered — while the screen that asked for it is `PhotosScreen`, and a
 * rotation must not lose the answer in between.
 *
 * **What [isPermanentlyDenied] is not.** It is in-memory only, and a cold
 * launch starts it false. A parent who refused for good therefore sees the
 * camera tile once more on the next launch; tapping it opens the gate, the
 * request resolves instantly with no dialog, and the tile becomes the
 * "Ask a grown-up" recovery card. That is one wasted tap on a control that
 * still leads somewhere useful, and it was chosen over the alternative:
 * persisting a "we have asked" flag would mean a new key in the settings
 * schema, and a stale `true` there (a parent who granted the permission in
 * Android Settings) would hide the camera from a child who *is* allowed to
 * use it. A wrong-but-recoverable state beats a wrong-and-sticky one.
 *
 * Nothing in this file imports an Android type: [hasCamera] is a fact the
 * caller looks up once, and [isGranted] is an injected read, so the whole
 * state machine is host-testable.
 */
class CameraPermissionState(
    private val hasCamera: Boolean,
    private val isGranted: () -> Boolean,
) {
    private var isPermanentlyDenied = false

    private val _availability = MutableStateFlow(
        cameraAvailability(hasCamera, isGranted(), isPermanentlyDenied = false),
    )

    val availability: StateFlow<CameraAvailability> = _availability.asStateFlow()

    private val _resolutions = MutableStateFlow(0)

    /**
     * Advances once per resolved request. `PhotosScreen` watches it so that a
     * grown-up who has just allowed the camera lands *in* the viewfinder
     * rather than back on a tile they must tap a second time — iOS does the
     * same thing off its notification. A counter rather than a `Boolean`,
     * because two consecutive grants must read as two distinct events.
     */
    val resolutions: StateFlow<Int> = _resolutions.asStateFlow()

    /** Re-reads the live permission. Called when Photos appears and whenever
     * the app returns to the foreground, which is the one way a grant made in
     * Android Settings gets back here. */
    fun refresh() {
        _availability.value = cameraAvailability(hasCamera, isGranted(), isPermanentlyDenied)
    }

    fun onRequestResult(granted: Boolean, canAskAgain: Boolean) {
        isPermanentlyDenied = permanentlyDeniedAfterRequest(granted, canAskAgain)
        refresh()
        _resolutions.value += 1
    }
}
