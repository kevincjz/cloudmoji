package app.cloudmoji.android.data

import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Where a child's photographs live until a grown-up explicitly saves a copy.
 * Ported from iOS `Views/Photos/PhotoStore.swift`.
 *
 * Three decisions are the whole of "photos stay on this device", and each one
 * is load-bearing:
 *
 * * **App-private `filesDir`, not MediaStore.** A toddler's shutter presses
 *   stay inside Cloudmoji rather than appearing in the parent's gallery, in
 *   Google Photos, or in any other app's picker. Only
 *   [app.cloudmoji.android.platform.PhotoExport], reached through the
 *   parent-gated Manage Photos screen, copies one out when a grown-up asks.
 * * **Excluded from backup.** Without the exclusion this folder rides along in
 *   Android's Auto Backup, which is a copy of a child's photographs on
 *   Google's servers — true, defensible, and not what the About screen says.
 *   Android has no per-file flag the way iOS does; the exclusion is
 *   declarative, in `res/xml/backup_rules.xml` and
 *   `res/xml/data_extraction_rules.xml`, and both name [DIRECTORY_NAME]. That
 *   is the one coupling in this file that cannot be checked by the compiler,
 *   so `BackupExclusionTest` checks it instead.
 * * **Credential-encrypted storage.** `Context.filesDir` is unreadable until
 *   the phone has been unlocked once after boot, which is this platform's
 *   equivalent of iOS's `.completeFileProtection`. The counterpart rule is
 *   therefore a *negative* one: these files must never be moved to
 *   `createDeviceProtectedStorageContext()`, which is readable before first
 *   unlock.
 *
 * The directory is injected rather than derived, so tests get a temporary one
 * — a test that wrote into a real app container would leak into every run
 * after it.
 */
class PhotoStore(
    val directory: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    /**
     * Newest first, which is the order a child expects: the thing he just
     * photographed is the first thing he sees.
     *
     * Ordered on the millisecond stamp carried in the *file name* rather than
     * on `File.lastModified()`, because filesystem timestamps have a coarser
     * resolution than two shutter presses a second apart — which is exactly
     * why the name carries the stamp. iOS sorts the whole file name as a
     * string; this parses the stamp instead, which is the same answer for
     * every name this app writes and the right answer for the one case a
     * string sort gets wrong (stamps of different digit lengths, which a test
     * with a hand-set clock produces immediately). The name breaks ties, so
     * two captures inside the same millisecond still have a stable order.
     */
    val photos: List<File>
        get() = (directory.listFiles() ?: emptyArray())
            .filter { it.isFile && !it.isHidden && it.extension == FILE_EXTENSION }
            .sortedWith(compareByDescending<File> { stampOf(it.name) }.thenByDescending { it.name })

    val count: Int get() = photos.size

    /**
     * Writes one photograph and returns where it went.
     *
     * Written to a `.tmp` sibling and then renamed, which is this platform's
     * spelling of iOS's `.atomic` write option: [photos] only ever admits
     * files with the [FILE_EXTENSION] extension, so a write interrupted
     * halfway — a full disk, a process killed while a toddler holds the
     * shutter — leaves a `.tmp` file that no gallery will ever show, rather
     * than a half-decoded grey square in a child's scrapbook. The rename is
     * within one directory, so it is atomic on every filesystem Android runs
     * on.
     *
     * Throws rather than failing quietly: the caller shows the child a flash
     * either way, but a parent looking at an empty gallery deserves the
     * failure to have been real somewhere.
     */
    @Throws(IOException::class)
    fun save(bytes: ByteArray): File {
        ensureDirectory()
        val target = File(directory, fileName(now(), newId()))
        val scratch = File(directory, "${target.name}.$SCRATCH_EXTENSION")
        try {
            scratch.writeBytes(bytes)
            if (!scratch.renameTo(target)) {
                throw IOException("could not move ${scratch.name} into place")
            }
        } finally {
            // A failed rename leaves the scratch file behind; it is invisible
            // to `photos` either way, but a gallery folder slowly filling with
            // debris is still a bug.
            if (scratch.exists()) scratch.delete()
        }
        return target
    }

    /** Permanent, and only ever reached from the parent-gated Manage Photos
     * screen — the child's own gallery has no delete affordance at all,
     * because a two-year-old with a delete button is a two-year-old with an
     * empty gallery. */
    fun delete(photo: File) {
        photo.delete()
    }

    /** Safe on an already-empty folder: the confirmation dialog behind this
     * can be answered twice. */
    fun deleteAll() {
        photos.forEach { delete(it) }
    }

    /** Created on first save rather than at launch — most sessions never open
     * Photos at all. */
    @Throws(IOException::class)
    private fun ensureDirectory() {
        if (directory.isDirectory) return
        if (directory.exists() || !directory.mkdirs()) {
            throw IOException("${directory.path} is not a usable photo directory")
        }
    }

    companion object {
        /** The folder inside `filesDir`. **Must** stay identical to the
         * `path` in `res/xml/backup_rules.xml` and
         * `res/xml/data_extraction_rules.xml` — see this class's own doc. */
        const val DIRECTORY_NAME = "photos"

        const val FILE_EXTENSION = "jpg"

        /** The half-written file's extension. Deliberately not
         * [FILE_EXTENSION], so an interrupted write can never be shown. */
        private const val SCRATCH_EXTENSION = "tmp"

        /** `<millis>-<uuid>.jpg`. The stamp is what [photos] orders on; the
         * random half is what keeps two shutter presses inside the same
         * millisecond from overwriting each other. */
        fun fileName(stampMillis: Long, id: String): String = "$stampMillis-$id.$FILE_EXTENSION"

        /** The millisecond stamp a [fileName] carries, or `0` for a name that
         * does not carry one — a file this app did not write, which sorts to
         * the end rather than throwing at a child holding the phone. */
        fun stampOf(name: String): Long = name.substringBefore('-').toLongOrNull() ?: 0L
    }
}
