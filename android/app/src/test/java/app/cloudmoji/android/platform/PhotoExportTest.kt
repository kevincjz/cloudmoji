package app.cloudmoji.android.platform

import app.cloudmoji.android.data.PhotoStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two decisions in the export path that can be made without a
 * `ContentResolver`: whether a permission is needed at all, and what the copy
 * is called once it lands in the parent's gallery.
 *
 * The MediaStore insert itself needs a device and is not covered here — see
 * the Task 14 report. Ports the intent of iOS
 * `PhotoLibraryExporterTests.authorizationStatusesAreHandled`, whose subject
 * (a four-valued `PHAuthorizationStatus`) has no Android counterpart: this
 * platform's equivalent question is an SDK-level one.
 */
class PhotoExportTest {

    /**
     * API 29 is the line. Below it, adding to MediaStore is a write to shared
     * storage and needs the runtime permission; at or above it, an app may add
     * its own images with no permission at all — which is the only mode this
     * app ever wants.
     *
     * Mutation: change the comparison to `<=`. The API 29 case fails, and the
     * app would ask a parent for a storage permission it does not need (and
     * which the manifest caps at 28, so the request would resolve as a silent
     * refusal and the export would look broken).
     */
    @Test
    fun onlyPreScopedStorageVersionsNeedAPermission() {
        assertTrue("API 26 is this app's minSdk and needs the permission", PhotoExport.needsLegacyStoragePermission(26))
        assertTrue(PhotoExport.needsLegacyStoragePermission(28))
        assertFalse("scoped storage arrived in API 29", PhotoExport.needsLegacyStoragePermission(29))
        assertFalse(PhotoExport.needsLegacyStoragePermission(34))
    }

    /** The on-disk name is right for a folder ordered by name and wrong for a
     * parent scrolling their own gallery. The stamp survives (it is what makes
     * the name unique); the UUID does not. */
    @Test
    fun theGalleryNameKeepsTheStampAndDropsTheRest() {
        val name = PhotoStore.fileName(1_700_000_000_000L, "6f1c-not-for-humans")

        assertEquals("Cloudmoji-1700000000000.jpg", PhotoExport.displayName(name))
    }

    /** Two photographs are two names — a display name that collapsed them
     * would have MediaStore silently de-duplicate a child's afternoon. */
    @Test
    fun twoPhotographsGetTwoNames() {
        val first = PhotoExport.displayName(PhotoStore.fileName(1_700_000_000_000L, "a"))
        val second = PhotoExport.displayName(PhotoStore.fileName(1_700_000_000_001L, "b"))

        assertFalse(first == second)
    }

    /** A folder of its own, so a parent can find — and delete — the whole lot
     * without hunting through their own photographs. */
    @Test
    fun copiesLandInTheirOwnAlbum() {
        assertEquals("Cloudmoji", PhotoExport.ALBUM)
    }
}
