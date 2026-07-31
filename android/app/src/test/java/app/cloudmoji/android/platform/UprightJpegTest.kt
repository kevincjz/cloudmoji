package app.cloudmoji.android.platform

import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [uprightJpeg]'s one decision a JVM test can actually reach: what it returns
 * when the capture will not decode into a `Bitmap` at all.
 *
 * `BitmapFactory` cannot be exercised here — every call into it from a plain
 * JUnit test throws `RuntimeException: Method ... not mocked` rather than
 * ever returning `null` (see `task-14-report.md`'s own table: "`uprightJpeg`'s
 * Bitmap calls" were already documented as untestable at this layer). That is
 * exactly why [uprightJpeg] takes its `decode` step as a seam — the same
 * pattern `WordsScheduler`/`MascotScheduler`/`CallbackPoster` already use
 * elsewhere in this app for an Android dependency no JVM test can drive
 * directly. Supplying a fake that returns `null` reproduces the one real
 * failure mode this function has to handle (a genuinely undecodable sensor
 * JPEG) without needing a real or mocked `Bitmap` at all.
 *
 * Whole-branch review Finding 2: before this fix, an undecodable capture fell
 * back to the *original* bytes — the sensor's own JPEG, EXIF block intact —
 * which contradicted `AboutScreen`'s unconditional "removes the camera's own
 * hidden information" claim. The fix drops the capture instead; the child
 * still got the flash and the buzz, and `PhotosScreen`'s `onCapture` already
 * treats a `null` result as nothing new in the gallery, not a failure state.
 */
class UprightJpegTest {

    /**
     * Mutation: change `uprightJpeg`'s `decode(bytes, maxEdgePixels) ?: return null`
     * back to `?: return bytes`. This test fails immediately — the result
     * stops being `null` and becomes the three-byte input instead.
     */
    @Test
    fun `an undecodable capture is dropped, never falling back to the original EXIF-bearing bytes`() {
        val sensorBytes = byteArrayOf(1, 2, 3) // stands in for a JPEG BitmapFactory cannot read

        val result = uprightJpeg(
            bytes = sensorBytes,
            rotationDegrees = 90,
            decode = { _, _ -> null }, // the real BitmapFactory dance, on a device, returning null
        )

        assertNull("an undecodable capture must be dropped, not published with its sensor EXIF intact", result)
    }

    /** The rotation and quality arguments must not matter to the drop —
     * nothing about them is even read once [decode] itself returns nothing to
     * work with. */
    @Test
    fun `the drop happens regardless of rotation or quality`() {
        for (rotation in listOf(0, 90, 180, 270, -90)) {
            val result = uprightJpeg(
                bytes = byteArrayOf(9),
                rotationDegrees = rotation,
                quality = 50,
                decode = { _, _ -> null },
            )
            assertNull("rotation $rotation must still drop an undecodable capture", result)
        }
    }
}
