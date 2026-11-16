package app.hush

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URI

/** Guards the About text: Hush sends no audio or text, keeps none, and uses the network only for the model download. */
class PrivacyTest {
    // Gradle runs unit tests in the module folder.
    private val sources = File("src/main/java/app/hush").listFiles { f -> f.extension == "kt" }!!.toList()

    private fun filesUsing(vararg apis: String) =
        sources.filter { file -> file.readText().let { text -> apis.any { it in text } } }.map { it.name }.toSet()

    @Test
    fun onlyTheModelDownloaderUsesTheNetwork() {
        assertTrue(sources.size > 10)
        val network = filesUsing("java.net.", "URLConnection", "Socket", "okhttp", "WebView", "DownloadManager", "Volley")
        assertEquals(setOf("ModelStore.kt"), network)
    }

    @Test
    fun onlyModelFilesAreWritten() {
        val writers = filesUsing(
            "FileOutputStream", "FileWriter", "openFileOutput", "writeText(", "writeBytes(", "createTempFile",
            "MediaStore", "getExternalFilesDir", "SQLiteDatabase", "MediaRecorder(",
        )
        assertEquals(setOf("ModelStore.kt"), writers)
    }

    @Test
    fun downloadsComeOnlyFromTheModelHosts() {
        val files = Catalog.languages.flatMap { it.model.files } + Catalog.vad
        val hosts = files.map { URI(it.url).host }.toSet()
        assertEquals(setOf("huggingface.co", "github.com"), hosts)
    }

    @Test
    fun backupAndDeviceTransferExcludeAllData() {
        val rules = File("src/main/res/xml/data_extraction_rules.xml").readText()
        for (section in listOf("cloud-backup", "device-transfer")) {
            val body = rules.substringAfter("<$section>").substringBefore("</$section>")
            for (domain in listOf("root", "file", "database", "sharedpref", "external")) {
                assertTrue("$section $domain", "<exclude domain=\"$domain\" />" in body)
            }
        }
    }
}
