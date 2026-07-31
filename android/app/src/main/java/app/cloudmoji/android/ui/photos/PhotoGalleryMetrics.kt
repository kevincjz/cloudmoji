package app.cloudmoji.android.ui.photos

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
     * The `BitmapFactory` `inSampleSize` for decoding a
     * [sourceWidth]×[sourceHeight] photograph whose longest edge should end up
     * no larger than [maxPixels].
     *
     * Lives here rather than in [PhotoThumbnails] for one blunt reason: that
     * object holds an `android.util.LruCache`, which a JVM unit test cannot
     * even construct, so a decode budget defined next to it could not be
     * tested by anything that runs in this environment. It is arithmetic, it
     * decides how much memory a child's gallery costs, and it belongs
     * somewhere a test can reach.
     *
     * Powers of two only — `BitmapFactory` rounds anything else down anyway —
     * and never below 1, which is what a photograph already smaller than the
     * target needs.
     */
    fun thumbnailSampleSize(sourceWidth: Int, sourceHeight: Int, maxPixels: Int): Int {
        if (maxPixels <= 0) return 1
        val longest = maxOf(sourceWidth, sourceHeight)
        var sample = 1
        while (longest / (sample * 2) >= maxPixels) sample *= 2
        return sample
    }

    private val TILTS = listOf(-2.5f, 1.8f, -1.0f, 2.2f)
}
