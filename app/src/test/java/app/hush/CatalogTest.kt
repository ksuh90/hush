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
    fun entriesAreUnique() {
        val keys = Catalog.languages.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun englishHasParakeetAndMoonshine() {
        val english = Catalog.languages.filter { it.code == "en" }.map { it.model }
        assertEquals(listOf(Catalog.parakeetV2, Catalog.moonshineBaseEn), english)
        assertEquals(listOf(Catalog.whisperBase), Catalog.languages.filter { it.code == "ko" }.map { it.model })
    }

    @Test
    fun spanishAndFrenchShareOneModel() {
        assertEquals(Catalog.parakeetV3, Catalog.language("es").model)
        assertEquals(Catalog.parakeetV3, Catalog.language("fr").model)
        assertEquals(1, Catalog.models.count { it == Catalog.parakeetV3 })
    }

    @Test
    fun onlyParakeetV2TakesTheUsersWords() {
        assertEquals(listOf(Catalog.parakeetV2), Catalog.models.filter { it.takesWords })
    }

    @Test
    fun modelChoiceFallsBackToTheDefault() {
        assertEquals(Catalog.parakeetV2, Catalog.language("en").model)
        assertEquals(Catalog.moonshineBaseEn, Catalog.language("en", Catalog.moonshineBaseEn.id).model)
        assertEquals(Catalog.parakeetV2, Catalog.language("en", "removed-model").model)
        assertEquals(Catalog.whisperBase, Catalog.language("ko", Catalog.moonshineBaseEn.id).model)
        assertEquals(Catalog.parakeetV2, Catalog.language("xx", Catalog.whisperBase.id).model)
    }

    @Test
    fun readyChoiceDropsTheOtherModelOfItsLanguage() {
        val moonshine = Catalog.language("en", Catalog.moonshineBaseEn.id)
        val keep = Catalog.keep(moonshine) { true }
        assertEquals(setOf(Catalog.moonshineBaseEn, Catalog.whisperBase, Catalog.parakeetV3), keep)
        val parakeet = Catalog.language("en")
        assertEquals(setOf(Catalog.parakeetV2, Catalog.whisperBase, Catalog.parakeetV3), Catalog.keep(parakeet) { true })
    }

    @Test
    fun oldModelStaysUntilTheChoiceIsDownloaded() {
        val moonshine = Catalog.language("en", Catalog.moonshineBaseEn.id)
        val keep = Catalog.keep(moonshine) { it != Catalog.moonshineBaseEn }
        assertEquals(Catalog.models.toSet(), keep)
    }

    @Test
    fun otherLanguagesKeepTheirModel() {
        val korean = Catalog.language("ko")
        assertEquals(Catalog.models.toSet(), Catalog.keep(korean) { true })
    }

    @Test
    fun sharedModelStaysForBothLanguages() {
        for (code in listOf("es", "fr")) {
            assertEquals(code, Catalog.models.toSet(), Catalog.keep(Catalog.language(code)) { true })
        }
    }

    @Test
    fun otherModelOfALanguageGoesOnlyWhenNoOtherLanguageUsesIt() {
        // A second Spanish model: when it is ready, Parakeet v3 must stay, because French uses it.
        val whisper = Language("es", "Español (Spanish)", "Whisper Base", Catalog.whisperBase, "es")
        val all = Catalog.languages + whisper
        assertEquals(Catalog.models.toSet(), Catalog.keep(whisper, all) { true })
        // Without the French entry, Parakeet v3 goes.
        val noFrench = all.filter { it.code != "fr" }
        assertEquals(Catalog.models.toSet() - Catalog.parakeetV3, Catalog.keep(whisper, noFrench) { true })
    }

    @Test
    fun englishIsTheDefault() {
        assertEquals("en", Catalog.languages.first().code)
        assertEquals("en", Catalog.language("xx").code)
        assertEquals("ko", Catalog.language("ko").code)
        assertEquals("fr", Catalog.language("fr", Catalog.parakeetV2.id).code)
    }

    @Test
    fun transducersHaveAJoiner() {
        for (spec in Catalog.languages.map { it.model }) {
            assertEquals(spec.id, spec.family == Family.TRANSDUCER, spec.joiner != null)
        }
    }
}
