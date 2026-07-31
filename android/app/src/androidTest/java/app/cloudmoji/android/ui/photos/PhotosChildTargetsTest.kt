package app.cloudmoji.android.ui.photos

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.cloudmoji.android.data.PhotoStore
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.platform.CameraPermissionState
import app.cloudmoji.android.platform.NoOpHapticFeedback
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Photos' Compose surface: the child-facing touch-target contract
 * (`CLAUDE.md` rule 1 / `conventions.md`), the `testTag`s the gallery
 * publishes, and that a denied camera still leads a child somewhere.
 *
 * **Not runnable in this environment** — no emulator or device is available
 * here, so `connectedAndroidTest` cannot run (see `conventions.md`). This file
 * was only compiled (`./gradlew :app:compileDebugAndroidTestKotlin`), which is
 * the whole of what Task 14 could verify about it. Run it once a device is
 * available, before trusting it.
 *
 * The numbers themselves — every target size, the gap, the decode budget —
 * are asserted by `PhotoGalleryMetricsTest`, which *does* execute here. This
 * file's own job is the part arithmetic cannot cover: that the values reach
 * what is actually drawn.
 */
class PhotosChildTargetsTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** A store in the instrumentation context's own cache directory, not the
     * app's real photo folder: a test must never write into, or delete from,
     * the pictures on the device it is running on. */
    private fun makeStore(): PhotoStore {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "photos-test-${System.nanoTime()}")
        return PhotoStore(directory)
    }

    private fun setScreen(
        store: PhotoStore = makeStore(),
        hasCamera: Boolean = true,
        isGranted: Boolean = true,
        onPermissionRequest: () -> Unit = {},
        onPermissionHelp: () -> Unit = {},
    ) {
        val permission = CameraPermissionState(hasCamera = hasCamera, isGranted = { isGranted })
        composeRule.setContent {
            PhotosScreen(
                store = store,
                cameraPermission = permission,
                language = Language.English,
                hapticFeedback = NoOpHapticFeedback,
                onCameraPermissionRequest = onPermissionRequest,
                onCameraPermissionHelp = onPermissionHelp,
                onHome = {},
            )
        }
    }

    @Test
    fun theCameraTileMeetsTheChildTargetFloor() {
        setScreen()

        composeRule.onNodeWithTag("photos-camera-btn")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(64.dp)
            .assertHeightIsAtLeast(64.dp)
            .assertHasClickAction()
    }

    /** An empty gallery is a composed scrapbook, not a blank screen — the
     * state a child sees the very first time Photos is opened. */
    @Test
    fun anEmptyGalleryShowsTheScrapbookRatherThanNothing() {
        setScreen()

        composeRule.onNodeWithTag("photos-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("photos-empty").assertIsDisplayed()
    }

    /** The way back out — `MiniAppScaffold`'s cloud home button, the only
     * navigation control any mini-app has. */
    @Test
    fun theCloudHomeButtonIsPresent() {
        setScreen()

        composeRule.onNodeWithTag("cloud-home")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(64.dp)
            .assertHeightIsAtLeast(64.dp)
    }

    /**
     * **A denied camera is not a dead control.** The tile becomes the
     * recovery card, and tapping it asks for a grown-up rather than doing
     * nothing — `CLAUDE.md` rule 4.
     */
    @Test
    fun aDeniedCameraStillLeadsSomewhere() {
        var askedForHelp = false
        val permission = CameraPermissionState(hasCamera = true, isGranted = { false })
        permission.onRequestResult(granted = false, canAskAgain = false)

        composeRule.setContent {
            PhotosScreen(
                store = makeStore(),
                cameraPermission = permission,
                language = Language.English,
                hapticFeedback = NoOpHapticFeedback,
                onCameraPermissionRequest = {},
                onCameraPermissionHelp = { askedForHelp = true },
                onHome = {},
            )
        }

        composeRule.onNodeWithTag("photos-camera-permission-btn")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(64.dp)
            .assertHeightIsAtLeast(64.dp)
            .performClick()

        composeRule.waitForIdle()
        assert(askedForHelp) { "the recovery card did not ask for a grown-up" }
    }

    /** No camera at all: the tile is *absent* rather than disabled, and the
     * only explanation a parent gets is on screen in its place. */
    @Test
    fun aCameralessDeviceExplainsItselfInsteadOfShowingADeadTile() {
        setScreen(hasCamera = false)

        composeRule.onNodeWithTag("photos-camera-note").assertIsDisplayed()
        composeRule.onNodeWithTag("photos-camera-btn").assertDoesNotExist()
        composeRule.onNodeWithTag("photos-camera-permission-btn").assertDoesNotExist()
    }

    /** A permission that has never been asked for routes the tap to the
     * parental gate rather than straight to Android's dialog. */
    @Test
    fun anUnaskedPermissionRoutesThroughTheGate() {
        var askedForPermission = false
        setScreen(isGranted = false, onPermissionRequest = { askedForPermission = true })

        composeRule.onNodeWithTag("photos-camera-btn").performClick()

        composeRule.waitForIdle()
        assert(askedForPermission) { "the camera tile did not open the parental gate" }
    }
}
