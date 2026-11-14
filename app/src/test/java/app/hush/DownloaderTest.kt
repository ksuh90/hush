package app.hush

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CancellationException

class DownloaderTest {
    @get:Rule val temp = TemporaryFolder()

    private val content = ByteArray(300_000) { (it % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
    private val model = ModelFile("model.onnx", "https://example.test/model.onnx", sha, content.size.toLong())

    private class FakeSource(val content: ByteArray, val honorRange: Boolean = true) : HttpSource {
        val offsets = mutableListOf<Long>()
        override fun open(url: String, offset: Long): HttpSource.Response {
            offsets += offset
            return if (offset > 0 && honorRange) {
                HttpSource.Response(206, ByteArrayInputStream(content, offset.toInt(), content.size - offset.toInt()))
            } else {
                HttpSource.Response(200, ByteArrayInputStream(content))
            }
        }
    }

    private fun dest() = File(temp.root, "spec/model.onnx")

    @Test
    fun downloadsAndVerifies() {
        Downloader(FakeSource(content)).fetch(model, dest(), {}) { false }
        assertArrayEquals(content, dest().readBytes())
        assertFalse(File(dest().path + ".part").exists())
    }

    @Test
    fun continuesAPartialDownload() {
        dest().parentFile!!.mkdirs()
        File(dest().path + ".part").writeBytes(content.copyOf(100_000))
        val source = FakeSource(content)
        Downloader(source).fetch(model, dest(), {}) { false }
        assertEquals(listOf(100_000L), source.offsets)
        assertArrayEquals(content, dest().readBytes())
    }

    @Test
    fun startsAgainWhenTheServerIgnoresTheRange() {
        dest().parentFile!!.mkdirs()
        File(dest().path + ".part").writeBytes(content.copyOf(100_000))
        Downloader(FakeSource(content, honorRange = false)).fetch(model, dest(), {}) { false }
        assertArrayEquals(content, dest().readBytes())
    }

    @Test
    fun badChecksumDeletesThePart() {
        val bad = content.copyOf().also { it[5] = 99 }
        try {
            Downloader(FakeSource(bad)).fetch(model, dest(), {}) { false }
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("checksum"))
        }
        assertFalse(dest().exists())
        assertFalse(File(dest().path + ".part").exists())
    }

    @Test
    fun httpErrorFails() {
        try {
            Downloader { _, _ -> HttpSource.Response(404, ByteArrayInputStream(ByteArray(0))) }.fetch(model, dest(), {}) { false }
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("404"))
        }
    }

    @Test
    fun cancelKeepsThePart() {
        try {
            Downloader(FakeSource(content)).fetch(model, dest(), {}) { true }
            fail("expected CancellationException")
        } catch (_: CancellationException) {
        }
        assertFalse(dest().exists())
        assertTrue(File(dest().path + ".part").exists())
    }

    @Test
    fun storeIsReadyOnlyWithAllFiles() {
        val store = ModelStore(temp.root)
        val spec = Catalog.moonshineBaseEn
        assertFalse(store.isReady(spec))
        assertEquals(spec.bytes + Catalog.vad.bytes, store.missingBytes(spec))
        for ((file, place) in store.parts(spec)) {
            place.parentFile!!.mkdirs()
            java.io.RandomAccessFile(place, "rw").use { it.setLength(file.bytes) }
        }
        assertTrue(store.isReady(spec))
        store.delete(spec)
        assertFalse(store.isReady(spec))
        assertEquals(spec.bytes, store.missingBytes(spec))
    }
}
