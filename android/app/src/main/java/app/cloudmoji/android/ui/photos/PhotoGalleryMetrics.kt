package app.cloudmoji.android.ui.photos

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cloudmoji.android.platform.jpegSampleSizeNoSmallerThan

/**
 * Every number the Photos gallery and its camera are drawn from. Mirrors iOS
 * `PhotosView.swift`'s `PhotoGalleryMetrics` and `CameraView.shutterSide`, pt
 * for dp — the same "port the iOS constant literally" convention
 * `AnimalGridMetrics`/`InstrumentPadMetrics`/`FlashCardMetrics` already use.
 *
 * A plain object with no Compose dependency beyond [Dp], so the touch-target
 * contract (`CLAUDE.md` rule 1) is checked by a JVM test that actually runs
 * here, rather than only by an instrumentation test that cannot.
 */
object PhotoGalleryMetrics {

    /** The preferred child target. A thumbnail is tapped to make it big, which
     * is the one gesture in this mini-app. */
    val thumbnailSide: Dp = 92.dp
    val padThumbnailSide: Dp = 156.dp

    val spacing: Dp = 12.dp
    val cornerRadius: Dp = 12.dp

    /** The camera tile is bigger than a thumbnail on purpose: it is the thing
     * the child came here to do, and the photographs are what happened last
     * time. */
    val cameraSide: Dp = 148.dp
    val padCameraSide: Dp = 176.dp

    val padContentMaxWidth: Dp = 1040.dp
    val padCameraMaxWidth: Dp = 760.dp

    /**
     * The shutter. Bigger than the 72dp preferred size and bigger than the
     * 64dp floor: it is the only control on the viewfinder and it is pressed
     * with a thumb while both hands are holding a phone at arm's length.
     * iOS `CameraView.shutterSide`.
     */
    val shutterSide: Dp = 88.dp

    const val PRESSED_SCALE: Float = 0.85f

    /** The polaroid tilt of thumbnail [index] — iOS
     * `[-2.5, 1.8, -1.0, 2.2][index % 4]`. A scrapbook, not a contact sheet:
     * four angles repeating means no two neighbours ever sit at the same one. */
    fun tiltDegrees(index: Int): Float = TILTS[index.mod(TILTS.size)]

    /** iOS `index.isMultiple(of: 3)` — every third picture gets a heart
     * instead of sparkles. */
    fun isHearted(index: Int): Boolean = index.mod(3) == 0

    fun thumbnailSide(isExpandedPad: Boolean): Dp =
        if (isExpandedPad) padThumbnailSide else thumbnailSide

    fun cameraSide(isExpandedPad: Boolean): Dp =
        if (isExpandedPad) padCameraSide else cameraSide

    /**
     * The gallery's decode budget — how far down a stored photograph is
     * decoded for a [maxPixels]-longest-edge draw.
     *
     * Named here, next to the sizes it is called with, but implemented by
     * [jpegSampleSizeNoSmallerThan] — the *floor* rounding, not the cap one
     * the capture path uses: a grid cell drawn from a bitmap smaller than
     * itself is visibly soft, so [minPixels] is a size not to go under. It is
     * *not* defined in [PhotoThumbnails], where it is actually used, because
     * that object holds an `android.util.LruCache` and so cannot be loaded by
     * a JVM unit test at all.
     */
    fun thumbnailSampleSize(sourceWidth: Int, sourceHeight: Int, minPixels: Int): Int =
        jpegSampleSizeNoSmallerThan(sourceWidth, sourceHeight, minPixels)

    private val TILTS = listOf(-2.5f, 1.8f, -1.0f, 2.2f)
}
