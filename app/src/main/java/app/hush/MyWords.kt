package app.hush

/**
 * The user's words ("My words"): names and terms that the engine boosts, so that it spells them as the user
 * does. This object has no Android code, so JVM tests can check it.
 */
object MyWords {
    /** The words in [input]. Commas and new lines separate them. Spaces are trimmed, and empty words go. */
    fun parse(input: String): List<String> =
        input.split(',', '\n').map { it.trim().replace(SPACES, " ") }.filter { it.isNotEmpty() }

    /** [words] with the words of [input] at the end. A word that is already in the list, in any case, does not go in again. */
    fun add(words: List<String>, input: String): List<String> = distinct(words + parse(input))

    /** [words] without the second and later copies of a word. Upper and lower case are the same. */
    fun distinct(words: List<String>): List<String> {
        val seen = HashSet<String>()
        return words.filter { seen.add(it.lowercase()) }
    }

    /**
     * The hotwords text for sherpa-onnx: the words with "/" between them. A word goes only when the model has a
     * token for each of its characters ([chars]), because sherpa-onnx cannot encode other words. sherpa-onnx reads
     * "/" as a separator and ":", "#" and "@" as a score or a tag, so a word with one of them never goes.
     */
    fun hotwords(words: List<String>, chars: Set<Char>): String =
        words.filter { word -> word.all { it == ' ' || (it in chars && it !in RESERVED) } }.joinToString("/")

    /** The characters that have a token of their own, from the lines of tokens.txt ("piece id"). */
    fun chars(tokens: List<String>): Set<Char> =
        tokens.mapNotNull { it.substringBeforeLast(' ').singleOrNull() }.toSet() - RESERVED - WORD_START

    /**
     * A SentencePiece vocab for sherpa-onnx, from the lines of tokens.txt: a piece and its score on each line.
     * The token id is the BPE merge order, so the score is minus the id. The blank token is not a piece.
     */
    fun bpeVocab(tokens: List<String>): String =
        tokens.filter { it.isNotBlank() }
            .map { it.substringBeforeLast(' ') to it.substringAfterLast(' ').trim().toInt() }
            .filter { (piece, _) -> piece != BLANK }
            .joinToString("") { (piece, id) -> "$piece\t${-id}\n" }

    private val RESERVED = setOf('/', ':', '#', '@')
    private const val WORD_START = '▁'
    private const val BLANK = "<blk>"
    private val SPACES = Regex("\\s+")
}
