package app.hush

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyWordsTest {
    @Test
    fun commasAndNewLinesSeparateWords() {
        assertEquals(listOf("PostgreSQL", "Kubernetes", "Golden Gate"), MyWords.parse(" PostgreSQL,Kubernetes ,\n  Golden   Gate  "))
    }

    @Test
    fun emptyWordsGo() {
        assertEquals(emptyList<String>(), MyWords.parse(" , ,,\n "))
        assertEquals(listOf("Hush"), MyWords.parse(",Hush,"))
    }

    @Test
    fun aWordGoesInOnlyOnceInAnyCase() {
        val words = MyWords.add(listOf("PostgreSQL"), "postgresql, Yosemite, YOSEMITE, Kubernetes")
        assertEquals(listOf("PostgreSQL", "Yosemite", "Kubernetes"), words)
    }

    @Test
    fun theOldSpellingStays() {
        assertEquals(listOf("Grafana"), MyWords.add(listOf("Grafana"), "grafana"))
    }

    @Test
    fun hotwordsHaveSlashesBetween() {
        val chars = ('a'..'z').toSet() + ('A'..'Z').toSet()
        assertEquals("PostgreSQL/Golden Gate", MyWords.hotwords(listOf("PostgreSQL", "Golden Gate"), chars))
        assertEquals("", MyWords.hotwords(emptyList(), chars))
    }

    @Test
    fun wordsThatTheModelCannotEncodeDoNotGo() {
        val chars = MyWords.chars(TOKENS)
        // sherpa-onnx reads ":" as a score, "#" as a threshold, "@" as a tag and "/" as a separator.
        val words = listOf("Hush", "C#", ":score", "AC/DC", "me@home", "Zoë", "Kubernetes")
        assertEquals("Hush/Kubernetes", MyWords.hotwords(words, chars))
    }

    @Test
    fun charsAreTheSingleCharacterTokens() {
        val chars = MyWords.chars(TOKENS)
        assertTrue('a' in chars && 'K' in chars && '\'' in chars)
        // ":" and "/" are tokens, but they never go into the hotwords. The word start mark is not a letter.
        assertFalse(':' in chars || '/' in chars || '▁' in chars)
        assertFalse('<' in chars)
    }

    @Test
    fun theVocabHasEveryPieceButTheBlankWithMinusTheIdAsScore() {
        assertEquals("<unk>\t0\n▁t\t-1\nin\t-2\na\t-3\n", MyWords.bpeVocab(listOf("<unk> 0", "▁t 1", "in 2", "a 3", "<blk> 4", "")))
    }

    private companion object {
        // Lines of a tokens.txt like the Parakeet one.
        val TOKENS = listOf("<unk> 0", "▁t 1", "▁ 2", ": 3", "/ 4", "' 5", "<blk> 6") +
            (('a'..'z') + ('A'..'Z')).mapIndexed { i, c -> "$c ${7 + i}" }
    }
}
