package app.cloudmoji.android.ui.photos

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Photos' half of the child touch-target contract (`CLAUDE.md` rule 1), plus
 * the decode budget behind the gallery.
 *
 * Lives here, as a JVM test, rather than only in `PhotosChildTargetsTest`:
 * that instrumentation test cannot run in this environment at all (no
 * emulator, see `conventions.md`), and a floor that is only checked by
 * something that never executes is not checked. The androidTest file still
 * earns its place — it measures what is actually *drawn*, which this cannot —
 * but the numbers themselves are asserted here where the assertion runs.
 */
class PhotoGalleryMetricsTest {

    /** 64dp is the floor and 72dp the preference. A thumbnail is what a child
     * taps to make a picture big, so it is a child target. */
    @Test
    fun everyChildTargetClearsTheFloor() {
        assertTrue("thumbnail", PhotoGalleryMetrics.thumbnailSide >= 64.dp)
        assertTrue("tablet thumbnail", PhotoGalleryMetrics.padThumbnailSide >= 64.dp)
        assertTrue("camera tile", PhotoGalleryMetrics.cameraSide >= 64.dp)
        assertTrue("tablet camera tile", PhotoGalleryMetrics.padCameraSide >= 64.dp)
        assertTrue("shutter", PhotoGalleryMetrics.shutterSide >= 64.dp)
    }

    /** Preferred, not merely allowed: none of these three is a control a child
     * should have to aim at. */
    @Test
    fun theChildTargetsClearThePreferredSizeToo() {
        assertTrue(PhotoGalleryMetrics.thumbnailSide >= 72.dp)
        assertTrue(PhotoGalleryMetrics.cameraSide >= 72.dp)
        assertTrue(PhotoGalleryMetrics.shutterSide >= 72.dp)
    }

    /** The shutter is the only control on the viewfinder and is pressed with a
     * thumb while both hands hold the phone at arm's length — iOS gives it
     * 88pt for exactly that, and this is the port of that number. */
    @Test
    fun theShutterMatchesTheIOSSize() {
        assertEquals(88.dp, PhotoGalleryMetrics.shutterSide)
    }

    /** `CLAUDE.md` rule 1's second half: 8dp between anything a child taps. */
    @Test
    fun thumbnailsAreKeptApart() {
        assertTrue(PhotoGalleryMetrics.spacing >= 8.dp)
    }

    /** A tablet gets bigger targets, never smaller ones. */
    @Test
    fun theTabletSizesAreTheLargerOnes() {
        assertEquals(PhotoGalleryMetrics.padThumbnailSide, PhotoGalleryMetrics.thumbnailSide(isExpandedPad = true))
        assertEquals(PhotoGalleryMetrics.thumbnailSide, PhotoGalleryMetrics.thumbnailSide(isExpandedPad = false))
        assertEquals(PhotoGalleryMetrics.padCameraSide, PhotoGalleryMetrics.cameraSide(isExpandedPad = true))
        assertEquals(PhotoGalleryMetrics.cameraSide, PhotoGalleryMetrics.cameraSide(isExpandedPad = false))
        assertTrue(PhotoGalleryMetrics.padThumbnailSide > PhotoGalleryMetrics.thumbnailSide)
        assertTrue(PhotoGalleryMetrics.padCameraSide > PhotoGalleryMetrics.cameraSide)
    }

    /** Four angles repeating, so no two neighbours sit at the same one — a
     * scrapbook rather than a contact sheet. Negative indices are covered
     * because `mod` is not `%`: a `%` here would throw on the first negative
     * index a future caller passed. */
    @Test
    fun theTiltCyclesAndNeverThrows() {
        assertEquals(PhotoGalleryMetrics.tiltDegrees(0), PhotoGalleryMetrics.tiltDegrees(4))
        assertEquals(PhotoGalleryMetrics.tiltDegrees(3), PhotoGalleryMetrics.tiltDegrees(7))
        assertTrue(PhotoGalleryMetrics.tiltDegrees(0) != PhotoGalleryMetrics.tiltDegrees(1))
        PhotoGalleryMetrics.tiltDegrees(-1)
    }

    @Test
    fun everyThirdPictureIsHearted() {
        assertTrue(PhotoGalleryMetrics.isHearted(0))
        assertTrue(PhotoGalleryMetrics.isHearted(3))
        assertTrue(!PhotoGalleryMetrics.isHearted(1))
        assertTrue(!PhotoGalleryMetrics.isHearted(2))
    }

    /**
     * **The decode budget.** A twelve-megapixel JPEG decoded at full size for
     * a 92dp square is about 48MB of bitmap; a gallery a child has filled for
     * a month then scrolls like treacle and eventually takes the process out.
     *
     * Mutation: return a constant 1 from `thumbnailSampleSize`. The first two
     * assertions fail.
     */
    @Test
    fun aLargePhotographIsDecodedDownToTheDrawSize() {
        assertEquals(16, PhotoGalleryMetrics.thumbnailSampleSize(4_032, 3_024, 252))
        assertEquals(8, PhotoGalleryMetrics.thumbnailSampleSize(4_032, 3_024, 504))
        // Already smaller than the target: decode it as it is.
        assertEquals(1, PhotoGalleryMetrics.thumbnailSampleSize(200, 150, 252))
    }

    /** Powers of two only — `BitmapFactory` rounds anything else down anyway,
     * so a non-power result would be a silently ignored calculation. */
    @Test
    fun theSampleSizeIsAlwaysAPowerOfTwo() {
        for (longest in listOf(100, 640, 1_000, 2_048, 4_032, 8_000)) {
            for (target in listOf(1, 56, 92, 156, 1_400)) {
                val sample = PhotoGalleryMetrics.thumbnailSampleSize(longest, longest / 2, target)
                assertTrue("sample $sample is not a power of two", sample > 0 && (sample and (sample - 1)) == 0)
            }
        }
    }

    /** A degenerate target must not spin forever or divide by zero — this is
     * called from a draw pass. */
    @Test
    fun aZeroTargetIsTolerated() {
        assertEquals(1, PhotoGalleryMetrics.thumbnailSampleSize(4_032, 3_024, 0))
        assertEquals(1, PhotoGalleryMetrics.thumbnailSampleSize(0, 0, 92))
    }
}
