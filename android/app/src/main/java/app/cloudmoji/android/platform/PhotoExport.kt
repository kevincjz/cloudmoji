package app.cloudmoji.android.platform

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import app.cloudmoji.android.data.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Copies app-private photographs into the parent's own gallery, after a
 * grown-up asks. Ported from iOS `Views/Photos/PhotoLibraryExporter.swift`.
 *
 * Cloudmoji only ever *adds*. It never reads, browses or edits the parent's
 * gallery — there is no `READ_MEDIA_IMAGES` permission anywhere in this app —
 * and the originals stay in [PhotoStore] after a successful export, so an
 * export is never also a deletion.
 *
 * Reachable only from the parent-gated Manage Photos screen. Nothing a child
 * can tap leads here.
 */
object PhotoExport {

    /** How exporting ended, in the four ways a parent can be told about.
     * Mirrors iOS `PhotoLibraryExporter.ExportError` plus its success case. */
    enum class Outcome {
        Saved,
        NoPhotos,

        /** Only reachable on API 28 and below — see
         * [needsLegacyStoragePermission]. */
        PermissionDenied,

        /** A photograph vanished between the list being drawn and the export
         * running, or MediaStore refused the insert. Never a partial success
         * that reports as a whole one. */
        Failed,
    }

    /**
     * Where the copies land in the parent's gallery. A folder of its own
     * rather than the top of Pictures, so a parent can find (and delete) the
     * whole lot without hunting through their own photographs.
     */
    const val ALBUM = "Cloudmoji"

    private const val MIME_TYPE = "image/jpeg"

    private const val TAG = "PhotoExport"

    /**
     * Whether this Android version needs `WRITE_EXTERNAL_STORAGE` before an
     * insert into MediaStore will be allowed.
     *
     * API 29 brought scoped storage: an app may add its own images to the
     * shared collection with no permission at all, which is the only mode this
     * app wants. Below that, the same insert is a write to shared external
     * storage and needs the runtime permission — which is why the manifest
     * declares it with `maxSdkVersion="28"` and why it is only ever requested
     * from behind the parental gate.
     *
     * Pure and passed the SDK level, so every branch is host-testable rather
     * than being whatever the machine running the tests happens to be.
     */
    fun needsLegacyStoragePermission(sdkInt: Int): Boolean = sdkInt < Build.VERSION_CODES.Q

    /**
     * What a MediaStore insert failure becomes, for a parent to be told
     * about. Only a version that still needs the legacy storage permission
     * ([needsLegacyStoragePermission]) can genuinely be a permission
     * problem — API 29+ asks for none at all before adding this app's own
     * images, so a `null`/refused insert there is MediaStore declining for
     * some other reason (disk full, a malformed request), and reporting it as
     * [Outcome.PermissionDenied] would show a parent an "allow in Android
     * Settings" recovery with an Open Settings button that switches nothing
     * on. Pure and passed the SDK level for the same host-testability reason
     * as [needsLegacyStoragePermission] itself.
     */
    fun insertFailureOutcome(sdkInt: Int): Outcome =
        if (needsLegacyStoragePermission(sdkInt)) Outcome.PermissionDenied else Outcome.Failed

    /**
     * The name a copy appears under in the gallery.
     *
     * The on-disk name is `<millis>-<uuid>.jpg`, which is right for a folder
     * ordered by name and wrong for a parent scrolling their own gallery.
     * The stamp is kept because it is what makes the name unique; the UUID is
     * dropped because nothing outside this app has any use for it.
     */
    fun displayName(fileName: String): String = "Cloudmoji-${PhotoStore.stampOf(fileName)}.jpg"

    /**
     * Copies [photos] into the gallery.
     *
     * All-or-nothing in what it *reports*, not in what it writes: MediaStore
     * has no transaction, so a failure partway through leaves the copies made
     * so far in place. Reporting [Outcome.Failed] rather than "some of them
     * worked" is the deliberate choice — the originals are all still in
     * Cloudmoji either way, so a parent's next move is to try again, and a
     * second export of an already-copied photograph costs a duplicate rather
     * than a loss.
     */
    suspend fun export(context: Context, photos: List<File>): Outcome = withContext(Dispatchers.IO) {
        if (photos.isEmpty()) return@withContext Outcome.NoPhotos
        if (photos.any { !it.exists() }) return@withContext Outcome.Failed

        val resolver = context.contentResolver
        for (photo in photos) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName(photo.name))
                put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/$ALBUM",
                    )
                    // Hidden from the gallery until the bytes are all there,
                    // so a parent scrolling mid-export never taps a
                    // half-written picture.
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = runCatching {
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            }.getOrNull() ?: return@withContext insertFailureOutcome(Build.VERSION.SDK_INT)

            val wrote = runCatching {
                resolver.openOutputStream(uri)?.use { out -> photo.inputStream().use { it.copyTo(out) } } != null
            }.onFailure { Log.e(TAG, "could not write ${photo.name} into the gallery", it) }.getOrDefault(false)

            if (!wrote) {
                runCatching { resolver.delete(uri, null, null) }
                return@withContext Outcome.Failed
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                runCatching { resolver.update(uri, values, null, null) }
            }
        }
        Outcome.Saved
    }
}
