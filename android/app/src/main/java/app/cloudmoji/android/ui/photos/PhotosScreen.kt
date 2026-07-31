package app.cloudmoji.android.ui.photos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import app.cloudmoji.android.data.PhotoStore
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.MiniApp
import app.cloudmoji.android.platform.CameraAvailability
import app.cloudmoji.android.platform.CameraEntryAction
import app.cloudmoji.android.platform.CameraPermissionState
import app.cloudmoji.android.platform.HapticFeedback
import app.cloudmoji.android.platform.cameraEntryAction
import app.cloudmoji.android.platform.findActivity
import app.cloudmoji.android.ui.common.LocalCloudmojiLayout
import app.cloudmoji.android.ui.common.MiniAppScaffold
import app.cloudmoji.android.ui.theme.Coral
import java.io.File

/** Chrome, in the five languages — copy, not content. Ported from iOS
 * `PhotosView.UIText`. The two parent-facing lines stay English-only, exactly
 * as iOS keeps them: like every other explanation in this app they are for the
 * grown-up wondering why a control is missing, not for the child. */
internal object PhotosUiText {
    val empty: Map<Language, String> = mapOf(
        Language.English to "No pictures yet",
        Language.Chinese to "还没有照片",
        Language.Malay to "Belum ada gambar",
        Language.Japanese to "まだ しゃしんが ないよ",
        Language.Tagalog to "Wala pang litrato",
    )

    val takeOne: Map<Language, String> = mapOf(
        Language.English to "Take a picture",
        Language.Chinese to "拍一张",
        Language.Malay to "Ambil gambar",
        Language.Japanese to "しゃしんを とる",
        Language.Tagalog to "Kumuha ng litrato",
    )

    val askGrownUp: Map<Language, String> = mapOf(
        Language.English to "Ask a grown-up",
        Language.Chinese to "请大人帮忙",
        Language.Malay to "Minta orang dewasa",
        Language.Japanese to "おとなに きいてね",
        Language.Tagalog to "Magtanong sa matanda",
    )

    /** Every camera-less emulator lands here, which is why this line exists:
     * without it Photos on such a device is a blank screen with a cloud in the
     * corner and looks exactly like a mini-app that was never built. */
    const val NO_CAMERA =
        "This device has no camera, so there is nothing to photograph with. On a phone or " +
            "tablet with a camera, the camera button appears here."

    const val CAMERA_DENIED = "Camera access is off."

    fun text(table: Map<Language, String>, language: Language): String =
        table[language] ?: table.getValue(Language.English)
}

/**
 * Photos 📷 — the child's own pictures, and the way to take another. Ported
 * from iOS `Views/Photos/PhotosView.swift`.
 *
 * **There is no delete on this screen.** A gallery a two-year-old can empty is
 * a gallery a two-year-old will empty, and the photographs are the point.
 * Deleting lives in
 * [app.cloudmoji.android.ui.parents.ManagePhotosScreen], behind the parental
 * gate, where a grown-up who means it can find it.
 *
 * **A denied camera never becomes a dead or missing feature**: the normal
 * camera tile turns into a parent-facing recovery card that opens this app's
 * page in Android Settings — through the gate, like every other way out of
 * this app. Absent hardware is different again, and handled by *removing* the
 * tile rather than disabling it: a control that answers a tap with nothing is
 * the failure state `CLAUDE.md` rule 4 forbids.
 *
 * This mini-app never speaks and never listens. There is no
 * [app.cloudmoji.android.platform.SpeechController] here and no mascot mood
 * machine — iOS `PhotosView` has neither either — which is also why
 * [MiniApp.Photos]'s `showsSoundRecovery` is `false`: there is no sound to
 * recover.
 *
 * [store] and [cameraPermission] are process-scoped (see
 * [app.cloudmoji.android.CloudmojiApplication]) rather than remembered here,
 * for the same reason every other mini-app's state is: a rotation must not
 * lose them, and the permission result arrives at `CloudmojiApp`'s launcher
 * while this screen is what asked for it.
 */
@Composable
fun PhotosScreen(
    store: PhotoStore,
    cameraPermission: CameraPermissionState,
    language: Language,
    hapticFeedback: HapticFeedback,
    onCameraPermissionRequest: () -> Unit,
    onCameraPermissionHelp: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalCloudmojiLayout.current
    val context = LocalContext.current
    val lifecycleOwner = remember(context) { context.findActivity() as? LifecycleOwner }

    val availability by cameraPermission.availability.collectAsState()
    val resolutions by cameraPermission.resolutions.collectAsState()

    var photos by remember(store) { mutableStateOf(store.photos) }
    var enlarged by remember { mutableStateOf<File?>(null) }
    var isCameraShowing by remember { mutableStateOf(false) }

    // What the permission state had already resolved before this screen
    // existed. Without this baseline a child who granted the camera last week
    // would be dropped straight into the viewfinder every time Photos opened.
    var seenResolutions by remember { mutableIntStateOf(resolutions) }

    LaunchedEffect(store) {
        photos = store.photos
        cameraPermission.refresh()
    }

    // Coming back from Android Settings is the one way `Denied` becomes
    // `Ready` without this screen being rebuilt, so the camera tile has to
    // reappear on return rather than on the next cold launch. iOS watches
    // `scenePhase`; this is the same signal, read from the Activity's own
    // lifecycle exactly as `SleepyCloudScreen` does — no
    // `lifecycle-runtime-compose` dependency needed for one observer.
    DisposableEffect(lifecycleOwner) {
        val lifecycle = lifecycleOwner?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) cameraPermission.refresh()
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    // A grown-up who has just answered the gate and allowed the camera lands
    // *in* the viewfinder, rather than back on a tile they must tap a second
    // time — iOS does the same off its `cloudmojiCameraAuthorizationDidChange`
    // notification. A refusal simply leaves the gallery as it was; nothing on
    // this screen ever tells a child that something failed.
    LaunchedEffect(resolutions) {
        if (resolutions == seenResolutions) return@LaunchedEffect
        seenResolutions = resolutions
        if (availability == CameraAvailability.Ready) isCameraShowing = true
    }

    fun leaveCamera() {
        isCameraShowing = false
        photos = store.photos
    }

    // Both composed after `CloudmojiApp`'s own route-level handler, so they
    // are the more-recently-registered callbacks and fire first: back closes
    // the enlarged picture or the viewfinder rather than leaving the mini-app
    // out from under a child who was looking at something.
    BackHandler(enabled = isCameraShowing) { leaveCamera() }
    BackHandler(enabled = enlarged != null) { enlarged = null }

    Box(modifier = modifier.fillMaxSize()) {
        if (isCameraShowing) {
            CameraScreen(
                caption = PhotosUiText.text(PhotosUiText.takeOne, language),
                hapticFeedback = hapticFeedback,
                onCapture = { bytes ->
                    // `null` because a capture can come back empty — a shutter
                    // pressed while the session was restarting, or a
                    // system-level failure. The child still got the flash and
                    // the buzz; there is simply nothing new in the gallery.
                    if (bytes != null) {
                        runCatching { store.save(bytes) }
                        photos = store.photos
                    }
                },
                onDone = ::leaveCamera,
            )
        } else {
            MiniAppScaffold(
                onHome = onHome,
                homeAccent = Coral,
                screenTag = "photos-screen",
                showsSoundRecovery = MiniApp.Photos.showsSoundRecovery,
            ) {
                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Gallery(
                        photos = photos,
                        availability = availability,
                        language = language,
                        isExpandedPad = layout.isExpandedPad,
                        isCompactPhone = layout.isCompactPhone,
                        onCamera = {
                            hapticFeedback.tap()
                            when (cameraEntryAction(availability)) {
                                CameraEntryAction.OpenCamera -> isCameraShowing = true
                                CameraEntryAction.AskParentToRequestPermission -> onCameraPermissionRequest()
                                CameraEntryAction.AskParentToOpenSettings -> onCameraPermissionHelp()
                                CameraEntryAction.Unavailable -> Unit
                            }
                        },
                        onOpenPhoto = { photo ->
                            hapticFeedback.tap()
                            enlarged = photo
                        },
                    )
                }
            }
        }

        // Above everything, including the cloud home button — iOS presents it
        // as a `fullScreenCover`. Tap anywhere to go back: there is no small
        // close target a toddler has to find.
        enlarged?.let { photo ->
            EnlargedPhoto(photo = photo, onClose = { enlarged = null })
        }
    }
}

/**
 * The scrapbook itself: the camera tile, then whatever has been photographed.
 *
 * One [LazyVerticalGrid] with full-width spanned rows for the tile, the empty
 * state and the camera note, rather than a `Column` containing a grid — a lazy
 * grid inside a scrolling column has unbounded height and would defeat its own
 * laziness by measuring every thumbnail at once, which for this screen is the
 * whole point of it being lazy.
 */
@Composable
private fun Gallery(
    photos: List<File>,
    availability: CameraAvailability,
    language: Language,
    isExpandedPad: Boolean,
    isCompactPhone: Boolean,
    onCamera: () -> Unit,
    onOpenPhoto: (File) -> Unit,
) {
    val thumbnailSide = PhotoGalleryMetrics.thumbnailSide(isExpandedPad)

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = thumbnailSide),
        horizontalArrangement = Arrangement.spacedBy(PhotoGalleryMetrics.spacing),
        verticalArrangement = Arrangement.spacedBy(PhotoGalleryMetrics.spacing),
        contentPadding = PaddingValues(
            start = if (isExpandedPad) 30.dp else 14.dp,
            end = if (isExpandedPad) 30.dp else 14.dp,
            top = if (isExpandedPad) 28.dp else if (isCompactPhone) 6.dp else 14.dp,
            bottom = 18.dp,
        ),
        modifier = Modifier
            .fillMaxHeight()
            // iOS caps the scrapbook's width on a big tablet so the thumbnails
            // stay a scrapbook rather than spreading into a contact sheet;
            // the hosting `Box` centres what is left.
            .then(
                if (isExpandedPad) {
                    Modifier.widthIn(max = PhotoGalleryMetrics.padContentMaxWidth)
                } else {
                    Modifier.fillMaxWidth()
                },
            )
            .testTag("photos-panel"),
    ) {
        if (availability != CameraAvailability.Unavailable) {
            item(key = "camera-tile", span = { GridItemSpan(maxLineSpan) }) {
                CameraTile(
                    availability = availability,
                    language = language,
                    isExpandedPad = isExpandedPad,
                    onClick = onCamera,
                )
            }
        }

        if (photos.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                EmptyScrapbook(language = language)
            }
        } else {
            itemsIndexed(photos, key = { _, photo -> photo.name }) { index, photo ->
                PhotoThumbnail(
                    photo = photo,
                    index = index,
                    side = thumbnailSide,
                    onClick = { onOpenPhoto(photo) },
                )
            }
        }

        if (availability == CameraAvailability.Unavailable) {
            item(key = "camera-note", span = { GridItemSpan(maxLineSpan) }) {
                CameraNote()
            }
        }
    }
}
