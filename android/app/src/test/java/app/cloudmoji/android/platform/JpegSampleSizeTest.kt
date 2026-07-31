package app.cloudmoji.android.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic that keeps both ends of the photograph pipeline inside the
 * heap: how far down a JPEG is decoded, for a capture about to be written and
 * for a thumbnail about to be drawn.
 *
 * **The capture end is the one with teeth.** A twelve-megapixel photograph is
 * about 48MB decoded, and `uprightJpeg` holds two bitmaps while it rotates.
 * Unbounded, that is a peak near 100MB on the control a toddler hammers
 * hardest, on hardware that may have a gigabyte in total.
 *
 * The two functions round *opposite ways* on purpose, and this file exists as
 * much to hold that distinction still as to check the numbers: one must never
 * go below a size, the other must never go above one, and a single shared
 * helper would have left the stored-photo cap silently doing nothing.
 */
class JpegSampleSizeTest {

    /** Thumbnails: never smaller than the cell they are drawn into.
     *
     * Mutation: return a constant 1. The first two assertions fail. */
    @Test
    fun aThumbnailIsDecodedDownToButNotBelowTheDrawSize() {
        assertEquals(16, jpegSampleSizeNoSmallerThan(4_032, 3_024, 252))
        assertEquals(8, jpegSampleSizeNoSmallerThan(4_032, 3_024, 504))
        // Already smaller than the target: decode it as it is.
        assertEquals(1, jpegSampleSizeNoSmallerThan(200, 150, 252))
    }

    /** The invariant holds only where there is something to give up: a
     * photograph *already* smaller than the cell cannot be decoded up to fill
     * it, and asking for that is what a sample size of 1 means. */
    @Test
    fun aThumbnailNeverEndsUpSmallerThanAsked() {
        for (longest in listOf(640, 1_000, 3_000, 4_032, 8_000)) {
            for (target in listOf(56, 92, 156, 1_400)) {
                val sample = jpegSampleSizeNoSmallerThan(longest, longest / 2, target)
                if (longest < target) {
                    assertEquals("nothing to give up at $longest for $target", 1, sample)
                    continue
                }
                assertTrue(
                    "$longest sampled by $sample fell under the $target it is drawn at",
                    longest / sample >= target,
                )
            }
        }
    }

    /**
     * The cap: never larger, which is the whole point of a memory bound.
     *
     * **This is the case the thumbnail rounding gets wrong.** A 4032-pixel
     * capture halved once is 2016 — under the 2048 cap — so a "no smaller
     * than" search stops at a sample size of 1 and decodes the full frame.
     *
     * Mutation: use `jpegSampleSizeNoSmallerThan` here instead. The first
     * assertion fails with 1, and the capture path is unbounded again.
     */
    @Test
    fun aCaptureIsBroughtAtOrUnderTheCap() {
        assertEquals(2, jpegSampleSizeNoLargerThan(4_032, 3_024, MAX_STORED_EDGE_PIXELS))
        assertEquals(1, jpegSampleSizeNoLargerThan(1_600, 1_200, MAX_STORED_EDGE_PIXELS))
        assertEquals(4, jpegSampleSizeNoLargerThan(8_000, 6_000, MAX_STORED_EDGE_PIXELS))
    }

    @Test
    fun aCaptureNeverEndsUpOverTheCap() {
        for (longest in listOf(640, 2_049, 4_032, 8_000, 12_000)) {
            val sample = jpegSampleSizeNoLargerThan(longest, longest / 2, MAX_STORED_EDGE_PIXELS)
            assertTrue(
                "$longest sampled by $sample is still over the $MAX_STORED_EDGE_PIXELS cap",
                longest / sample <= MAX_STORED_EDGE_PIXELS,
            )
        }
    }

    /** Powers of two only — `BitmapFactory` rounds anything else down anyway,
     * so a non-power result would be a silently ignored calculation. */
    @Test
    fun bothSampleSizesAreAlwaysPowersOfTwo() {
        for (longest in listOf(100, 640, 1_000, 2_048, 4_032, 8_000)) {
            for (target in listOf(1, 56, 92, 156, 1_400, MAX_STORED_EDGE_PIXELS)) {
                for (sample in listOf(
                    jpegSampleSizeNoSmallerThan(longest, longest / 2, target),
                    jpegSampleSizeNoLargerThan(longest, longest / 2, target),
                )) {
                    assertTrue("sample $sample is not a power of two", sample > 0 && (sample and (sample - 1)) == 0)
                }
            }
        }
    }

    /** A degenerate target must not spin forever or divide by zero — these run
     * inside a draw pass and inside a capture callback. */
    @Test
    fun aZeroTargetIsTolerated() {
        assertEquals(1, jpegSampleSizeNoSmallerThan(4_032, 3_024, 0))
        assertEquals(1, jpegSampleSizeNoLargerThan(4_032, 3_024, 0))
        assertEquals(1, jpegSampleSizeNoSmallerThan(0, 0, 92))
        assertEquals(1, jpegSampleSizeNoLargerThan(0, 0, 92))
    }

    /**
     * The cap has to be large enough that a photograph still looks like one
     * after a parent exports it, and small enough to bound the rotate. 2048 is
     * above every phone screen's long edge and about 12MB decoded.
     *
     * Mutation: raise it to 8192. The upper bound fails, and a capture is
     * unbounded again in practice.
     */
    @Test
    fun theStoredPhotographIsCappedButStillLargerThanAnyScreen() {
        assertTrue("a stored photo smaller than a screen is a blurry scrapbook", MAX_STORED_EDGE_PIXELS >= 1_600)
        assertTrue("the rotate holds two bitmaps at once", MAX_STORED_EDGE_PIXELS <= 3_000)
    }
}
