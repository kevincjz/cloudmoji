package app.cloudmoji.android.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **"A child's photographs never leave this device" is only as true as these
 * three files agreeing.**
 *
 * On iOS the equivalent promise is a per-file flag the store sets and a test
 * reads straight back off disk. Android has no such flag: the exclusion is
 * declarative XML, wired into the manifest by attribute name, and nothing in
 * the toolchain checks that the path inside it matches the folder the app
 * actually writes to. Rename [PhotoStore.DIRECTORY_NAME] and every other test
 * in this module still passes while every photograph a child takes starts
 * being uploaded with the parent's Google backup — silently, and in the one
 * direction that matters.
 *
 * So this asserts the whole chain: the manifest points at both rule files,
 * and both rule files exclude the folder the store writes into.
 *
 * Reads the sources rather than the merged build output because that is what
 * a human edits and what a review diff shows. [moduleFile] walks up from the
 * working directory instead of assuming one, since a Gradle `Test` task's
 * working directory is a convention rather than a guarantee.
 */
class BackupExclusionTest {

    private fun moduleFile(relativePath: String): File {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, relativePath)
            if (candidate.exists()) return candidate
            val nested = File(directory, "app/$relativePath")
            if (nested.exists()) return nested
            directory = directory.parentFile
        }
        throw AssertionError("could not find $relativePath from ${File("").absolutePath}")
    }

    private fun read(relativePath: String): String = moduleFile(relativePath).readText()

    @Test
    fun theManifestPointsAtBothSetsOfRules() {
        val manifest = read("src/main/AndroidManifest.xml")

        assertTrue(
            "android:fullBackupContent is missing — API 30 and below would back the photos up",
            manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""),
        )
        assertTrue(
            "android:dataExtractionRules is missing — API 31+ would back the photos up",
            manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""),
        )
    }

    @Test
    fun autoBackupExcludesTheFolderThePhotosAreWrittenTo() {
        val rules = read("src/main/res/xml/backup_rules.xml")

        assertTrue(
            "backup_rules.xml does not exclude ${PhotoStore.DIRECTORY_NAME}",
            rules.contains("""<exclude domain="file" path="${PhotoStore.DIRECTORY_NAME}" />"""),
        )
    }

    /** Both destinations, not just the cloud one: a device transfer copies the
     * pictures onto hardware they were never taken on, which is the same
     * promise broken a different way. */
    @Test
    fun bothCloudBackupAndDeviceTransferExcludeThePhotos() {
        val rules = read("src/main/res/xml/data_extraction_rules.xml")
        val exclusion = """<exclude domain="file" path="${PhotoStore.DIRECTORY_NAME}" />"""

        val cloud = rules.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>")
        val transfer = rules.substringAfter("<device-transfer>").substringBefore("</device-transfer>")

        assertTrue("cloud-backup does not exclude ${PhotoStore.DIRECTORY_NAME}", cloud.contains(exclusion))
        assertTrue("device-transfer does not exclude ${PhotoStore.DIRECTORY_NAME}", transfer.contains(exclusion))
    }

    /** The camera permission is declared, and the microphone is not — the
     * About screen says Cloudmoji never listens, and on this platform that
     * sentence is enforced by the absence of one line in one file. */
    @Test
    fun theManifestAsksForTheCameraAndNeverForTheMicrophone() {
        val manifest = read("src/main/AndroidManifest.xml")

        assertTrue(manifest.contains("android.permission.CAMERA"))
        assertTrue(
            "a microphone permission appeared — the privacy copy says Cloudmoji never listens",
            !manifest.contains("android.permission.RECORD_AUDIO"),
        )
        assertTrue(
            "a location permission appeared — a child's photograph must carry no place",
            !manifest.contains("android.permission.ACCESS_FINE_LOCATION") &&
                !manifest.contains("android.permission.ACCESS_COARSE_LOCATION"),
        )
    }
}
