package app.hush

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {
    private val sha256 = Regex("[0-9a-f]{64}")

    @Test
    fun everyFileIsPinned() {
        val files = Catalog.languages.flatMap { it.model.files } + Catalog.vad
        for (file in files) {
            assertTrue(file.url, file.url.startsWith("https://"))
            assertTrue(file.name, file.url.endsWith("/${file.name}"))
            assertTrue(file.name, sha256.matches(file.sha256))
            assertTrue(file.name, file.bytes > 0)
        }
    }

    @Test
    fun languageCodesAreUnique() {
        val codes = Catalog.languages.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun englishIsTheDefault() {
        assertEquals("en", Catalog.languages.first().code)
        assertEquals("en", Catalog.language("xx").code)
        assertEquals("ko", Catalog.language("ko").code)
    }
}
