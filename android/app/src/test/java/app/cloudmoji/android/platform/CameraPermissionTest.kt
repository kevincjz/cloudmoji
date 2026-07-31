package app.cloudmoji.android.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The permission state machine and what each of its states does when a child
 * taps the camera. Ports iOS `CloudmojiTests/PhotoStoreTests.swift`'s
 * `CameraLifecycleTests.permissionStatesNeverBecomeADeadCameraTile`, plus the
 * Android-only half iOS has no equivalent for (there is no `.notDetermined`
 * on this platform — the app has to remember).
 */
class CameraPermissionTest {

    /**
     * **Every authorization state has an explicit recovery action.** A state
     * that fell through to "nothing happens" would be the failure state
     * `CLAUDE.md` rule 4 forbids, on the one control a child came to this
     * mini-app to press.
     *
     * Mutation: make `cameraEntryAction` return `Unavailable` for `Denied`.
     * This fails.
     */
    @Test
    fun everyAvailabilityHasAnExplicitAction() {
        assertEquals(CameraEntryAction.OpenCamera, cameraEntryAction(CameraAvailability.Ready))
        assertEquals(
            CameraEntryAction.AskParentToRequestPermission,
            cameraEntryAction(CameraAvailability.NeedsPermission),
        )
        assertEquals(
            CameraEntryAction.AskParentToOpenSettings,
            cameraEntryAction(CameraAvailability.Denied),
        )
        assertEquals(CameraEntryAction.Unavailable, cameraEntryAction(CameraAvailability.Unavailable))
    }

    /** No camera beats everything else — a granted permission on a device with
     * no lens is still nothing to photograph with.
     *
     * Mutation: reorder the `when` so `isGranted` is tested first. This fails. */
    @Test
    fun absentHardwareOutranksEveryPermissionState() {
        assertEquals(
            CameraAvailability.Unavailable,
            cameraAvailability(hasCamera = false, isGranted = true, isPermanentlyDenied = false),
        )
        assertEquals(
            CameraAvailability.Unavailable,
            cameraAvailability(hasCamera = false, isGranted = false, isPermanentlyDenied = true),
        )
    }

    @Test
    fun theThreePermissionStatesResolveAsTheyDoOnIOS() {
        assertEquals(
            CameraAvailability.Ready,
            cameraAvailability(hasCamera = true, isGranted = true, isPermanentlyDenied = false),
        )
        assertEquals(
            CameraAvailability.NeedsPermission,
            cameraAvailability(hasCamera = true, isGranted = false, isPermanentlyDenied = false),
        )
        assertEquals(
            CameraAvailability.Denied,
            cameraAvailability(hasCamera = true, isGranted = false, isPermanentlyDenied = true),
        )
    }

    /** Only a refusal Android will not re-prompt for is permanent. A refusal
     * it *will* re-prompt for must stay `NeedsPermission`, or a parent who
     * meant to allow it on the second try never gets a second try.
     *
     * Mutation: return `!granted`. The middle case fails. */
    @Test
    fun onlyAnUnrepeatableRefusalIsPermanent() {
        assertTrue(permanentlyDeniedAfterRequest(granted = false, canAskAgain = false))
        assertFalse(permanentlyDeniedAfterRequest(granted = false, canAskAgain = true))
        assertFalse(permanentlyDeniedAfterRequest(granted = true, canAskAgain = false))
        assertFalse(permanentlyDeniedAfterRequest(granted = true, canAskAgain = true))
    }

    @Test
    fun aGrantMovesTheStateToReadyAndAnnouncesItself() {
        var granted = false
        val state = CameraPermissionState(hasCamera = true, isGranted = { granted })

        assertEquals(CameraAvailability.NeedsPermission, state.availability.value)
        val before = state.resolutions.value

        granted = true
        state.onRequestResult(granted = true, canAskAgain = false)

        assertEquals(CameraAvailability.Ready, state.availability.value)
        assertEquals(
            "PhotosScreen opens the viewfinder off this counter; a grant that does not " +
                "advance it leaves the grown-up on a tile they must press again",
            before + 1,
            state.resolutions.value,
        )
    }

    /**
     * The state a parent's final refusal leaves behind, and — the part that
     * matters — the way back out of it. A camera re-enabled in Android
     * Settings must show up on the next [CameraPermissionState.refresh],
     * which is what the screen's `ON_RESUME` observer calls.
     *
     * Mutation: leave `isPermanentlyDenied` true forever (drop the `granted`
     * branch of `permanentlyDeniedAfterRequest`). The last assertion fails.
     */
    @Test
    fun aFinalRefusalIsRecoverableFromAndroidSettings() {
        var granted = false
        val state = CameraPermissionState(hasCamera = true, isGranted = { granted })

        state.onRequestResult(granted = false, canAskAgain = false)
        assertEquals(CameraAvailability.Denied, state.availability.value)

        // The parent walks to Android Settings and switches it on; the screen
        // comes back to the foreground and refreshes.
        granted = true
        state.refresh()

        assertEquals(CameraAvailability.Ready, state.availability.value)
    }

    /** A refusal that can be re-prompted keeps the camera tile a camera tile,
     * so a second gate pass produces a second dialog. */
    @Test
    fun aRepeatableRefusalLeavesTheTileAskable() {
        val state = CameraPermissionState(hasCamera = true, isGranted = { false })

        state.onRequestResult(granted = false, canAskAgain = true)

        assertEquals(CameraAvailability.NeedsPermission, state.availability.value)
        assertEquals(
            CameraEntryAction.AskParentToRequestPermission,
            cameraEntryAction(state.availability.value),
        )
    }

    /** A device with no camera never leaves `Unavailable`, whatever the
     * permission does — the tile is absent rather than dead. */
    @Test
    fun aCameralessDeviceStaysUnavailable() {
        val state = CameraPermissionState(hasCamera = false, isGranted = { true })

        state.refresh()
        state.onRequestResult(granted = true, canAskAgain = true)

        assertEquals(CameraAvailability.Unavailable, state.availability.value)
    }
}
