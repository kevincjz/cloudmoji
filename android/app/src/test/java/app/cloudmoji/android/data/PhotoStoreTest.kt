package app.cloudmoji.android.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The platform-neutral half of iOS `CloudmojiTests/PhotoStoreTests.swift`,
 * ported case for case: ordering, deletion, stray files, on-demand creation,
 * and that the bytes written are the bytes read back.
 *
 * iOS's two remaining cases have no counterpart here and are covered
 * elsewhere on purpose. Its backup-exclusion test reads a per-file URL
 * resource value; Android's exclusion is declarative XML, so
 * `BackupExclusionTest` checks that instead. Its file-protection test asserts
 * on a write option; Android's equivalent is a property of `filesDir` itself
 * (credential-encrypted storage), with no flag to pass or assert — see
 * [PhotoStore]'s own doc.
 *
 * Each test gets a fresh temporary directory. A test that wrote into a real
 * app container would leak into every run after it.
 */
class PhotoStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var tick = 1_000L
    private var serial = 0

    /** The store under test, on a hand-wound clock. Stepped explicitly rather
     * than slept through: two shutter presses a second apart are the case the
     * millisecond stamp exists for, and a real clock cannot reproduce them
     * reliably. */
    private fun makeStore(directory: File = File(folder.root, "photos")): PhotoStore =
        PhotoStore(directory = directory, now = { tick }, newId = { "id${serial++}" })

    private fun bytes(marker: String): ByteArray = "jpeg-$marker".toByteArray()

    /**
     * Newest first — the thing a child just photographed is the first thing
     * he sees.
     *
     * The stamps here (1_000, then 10_000) are deliberately of different
     * digit lengths: that is exactly the case a plain lexicographic sort of
     * the file name gets backwards, and it is why [PhotoStore] parses the
     * stamp rather than comparing whole names the way iOS does.
     *
     * Mutation: flip `sortedWith`'s comparator to ascending. This fails.
     */
    @Test
    fun photosComeBackNewestFirst() {
        val store = makeStore()

        tick = 1_000L
        val first = store.save(bytes("a"))
        tick = 5_000L
        val second = store.save(bytes("b"))
        tick = 10_000L
        val third = store.save(bytes("c"))

        assertEquals(listOf(third, second, first), store.photos)
        assertEquals(3, store.count)
    }

    /** Two presses inside the same millisecond are two photographs, not one
     * overwriting the other — which is what the random half of the name is
     * for.
     *
     * Mutation: drop the id from `fileName`. This fails. */
    @Test
    fun twoCapturesInTheSameMillisecondBothSurvive() {
        val store = makeStore()
        tick = 7_000L

        store.save(bytes("a"))
        store.save(bytes("b"))

        assertEquals(2, store.count)
    }

    /** Mutation: make `delete` a no-op. Every assertion below fails. */
    @Test
    fun deletingRemovesOneAndDeleteAllEmptiesTheFolder() {
        val store = makeStore()
        repeat(3) {
            store.save(bytes("x"))
            tick += 1
        }
        assertEquals(3, store.count)

        val target = store.photos[1]
        store.delete(target)
        assertEquals(2, store.count)
        assertFalse(store.photos.contains(target))

        store.deleteAll()
        assertTrue(store.photos.isEmpty())
        // Twice, because the confirmation dialog can be answered on an
        // already-empty folder.
        store.deleteAll()
        assertTrue(store.photos.isEmpty())
    }

    /** What was written is what comes back. A store that saved zero bytes
     * would pass every ordering assertion above and show a child an empty
     * grey square. */
    @Test
    fun theBytesWrittenAreTheBytesReadBack() {
        val store = makeStore()
        val data = bytes("round-trip")

        val file = store.save(data)

        assertArrayEquals(data, file.readBytes())
    }

    /**
     * Only photographs. A stray file in the folder — a partial write, a
     * `.nomedia`, something another tool dropped in — must never become a
     * grey square in a child's gallery.
     *
     * The `.tmp` case is the load-bearing one: it is the name [PhotoStore]
     * itself writes to before renaming, so a write interrupted halfway leaves
     * exactly this file behind.
     */
    @Test
    fun nonPhotoFilesInTheFolderAreIgnored() {
        val store = makeStore()
        store.save(bytes("real"))
        File(store.directory, "notes.txt").writeText("junk")
        File(store.directory, "1234-abc.jpg.tmp").writeText("half a photo")

        assertEquals(1, store.count)
        assertTrue(store.photos.all { it.extension == PhotoStore.FILE_EXTENSION })
    }

    /** The folder is created on first save rather than at launch — most
     * sessions never open Photos at all. Reading an absent folder must not
     * throw: that read happens with a child holding the phone. */
    @Test
    fun theFolderIsCreatedOnDemand() {
        val store = makeStore()

        assertFalse(store.directory.exists())
        assertTrue(store.photos.isEmpty())

        store.save(bytes("first"))
        assertTrue(store.directory.isDirectory)
    }

    /** A name this app did not write sorts to the end rather than throwing —
     * `stampOf` is called on every entry of the folder listing, on the main
     * thread, while a gallery is being drawn. */
    @Test
    fun aNameWithNoStampIsToleratedRatherThanFatal() {
        assertEquals(0L, PhotoStore.stampOf("not-a-stamp.jpg"))
        assertEquals(1_700_000_000_000L, PhotoStore.stampOf("1700000000000-abc.jpg"))
    }

    /** The one coupling the compiler cannot check is checked by
     * `BackupExclusionTest`; this checks the other end of it, so a rename of
     * the constant shows up as a failure here too. */
    @Test
    fun theDirectoryNameIsTheOneTheBackupRulesExclude() {
        assertEquals("photos", PhotoStore.DIRECTORY_NAME)
    }
}
