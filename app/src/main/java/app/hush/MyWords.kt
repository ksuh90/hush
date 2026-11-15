package app.hush

/**
 * The user's words ("My words"): names and terms that the engine boosts, so that it spells them as the user
 * does. This object has no Android code, so JVM tests can check it.
 */
object MyWords {
    /** The most words in the list. With more, the engine writes more normal words with a capital letter. */
    const val MAX_WORDS = 20
    const val MAX_ENTRY_WORDS = 4
    const val MAX_ENTRY_CHARS = 40

    /** The words in [input]. Commas and new lines separate them. Spaces are trimmed, and empty words go. */
    fun parse(input: String): List<String> =
        input.split(',', '\n').map { it.trim().replace(SPACES, " ") }.filter { it.isNotEmpty() }

    /**
     * [words] with the words of [input] at the end, up to [MAX_WORDS]. A word that is already in the list, in any
     * case, does not go in again. A word that is [tooLong] does not go in, and a word after the list is full does not
     * go in.
     */
    fun add(words: List<String>, input: String): Added {
        val list = words.toMutableList()
        val seen = words.mapTo(HashSet()) { it.lowercase() }
        val long = mutableListOf<String>()
        var full = 0
        for (word in parse(input)) {
            if (!seen.add(word.lowercase())) continue
            when {
                tooLong(word) -> long += word
                list.size >= MAX_WORDS -> full++
                else -> list += word
            }
        }
        return Added(list, long, full)
    }

    /** The result of [add]: the new list, the words that are too long, and the number of words that did not fit. */
    data class Added(val words: List<String>, val tooLong: List<String>, val notAdded: Int)

    /**
     * True when [word] has more than [MAX_ENTRY_WORDS] words or [MAX_ENTRY_CHARS] characters. The engine follows a
     * long entry so strongly that it can drop the end of a dictation when the speaker says only a part of it.
     */
    fun tooLong(word: String): Boolean = word.length > MAX_ENTRY_CHARS || word.split(' ').size > MAX_ENTRY_WORDS

    /** [words] from an older Hush, cut to the limits: the words that are [tooLong] go, then the words after [MAX_WORDS]. */
    fun fit(words: List<String>): List<String> = words.filterNot(::tooLong).take(MAX_WORDS)

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
