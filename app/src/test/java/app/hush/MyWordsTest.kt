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
        val added = MyWords.add(listOf("PostgreSQL"), "postgresql, Yosemite, YOSEMITE, Kubernetes")
        assertEquals(MyWords.Added(listOf("PostgreSQL", "Yosemite", "Kubernetes"), emptyList(), 0), added)
    }

    @Test
    fun theOldSpellingStays() {
        assertEquals(listOf("Grafana"), MyWords.add(listOf("Grafana"), "grafana").words)
    }

    @Test
    fun thePasteGoesInUpToTwentyWordsAndCountsTheRest() {
        val added = MyWords.add(numbered(1..17), (18..25).joinToString(", ") { "Word$it" })
        assertEquals(numbered(1..20), added.words)
        assertEquals(5, added.notAdded)
        assertEquals(emptyList<String>(), added.tooLong)
    }

    @Test
    fun aFullListTakesNoWord() {
        assertEquals(MyWords.Added(numbered(1..20), emptyList(), 1), MyWords.add(numbered(1..20), "Brooklyn"))
        // A copy of a word in the list is not a word that did not fit.
        assertEquals(0, MyWords.add(numbered(1..20), "word3").notAdded)
    }

    @Test
    fun anEntryCanHaveFourWordsAndFortyCharacters() {
        assertFalse(MyWords.tooLong("San Francisco Bay Area"))
        assertTrue(MyWords.tooLong("please share the quarterly project"))
        assertFalse(MyWords.tooLong("A".repeat(40)))
        assertTrue(MyWords.tooLong("A".repeat(41)))
        assertFalse(MyWords.tooLong("New York Stock Exchange"))
    }

    @Test
    fun aLongEntryStaysOutAndTheOthersGoIn() {
        val added = MyWords.add(listOf("Hush"), "PostgreSQL, please send the weekly status report, Golden Gate")
        assertEquals(listOf("Hush", "PostgreSQL", "Golden Gate"), added.words)
        assertEquals(listOf("please send the weekly status report"), added.tooLong)
        assertEquals(0, added.notAdded)
    }

    @Test
    fun aLongEntryDoesNotTakeAPlaceInTheList() {
        val added = MyWords.add(numbered(1..19), "one two three four five, Brooklyn, Queens")
        assertEquals(numbered(1..19) + "Brooklyn", added.words)
        assertEquals(listOf("one two three four five"), added.tooLong)
        assertEquals(1, added.notAdded)
    }

    @Test
    fun anOldListKeepsItsFirstTwentyWordsThatAreNotTooLong() {
        val old = listOf("Hush", "a sentence with far too many words in it") + numbered(1..25)
        assertEquals(listOf("Hush") + numbered(1..19), MyWords.fit(old))
        assertEquals(numbered(1..3), MyWords.fit(numbered(1..3)))
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
        fun numbered(range: IntRange) = range.map { "Word$it" }

        // Lines of a tokens.txt like the Parakeet one.
        val TOKENS = listOf("<unk> 0", "▁t 1", "▁ 2", ": 3", "/ 4", "' 5", "<blk> 6") +
            (('a'..'z') + ('A'..'Z')).mapIndexed { i, c -> "$c ${7 + i}" }
    }
}
