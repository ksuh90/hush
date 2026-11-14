package app.hush

interface InsertTarget {
    fun textBeforeCursor(): CharSequence?
    fun commitAtCursor(text: String): Boolean
    fun setTextAtCursor(text: String): Boolean
    fun copyToClipboard(text: String)
}

enum class InsertMethod { COMMIT_TEXT, SET_TEXT, CLIPBOARD }

object Inserter {
    fun insert(text: String, target: InsertTarget): InsertMethod {
        val spaced = withSpace(target.textBeforeCursor(), text)
        return when {
            target.commitAtCursor(spaced) -> InsertMethod.COMMIT_TEXT
            target.setTextAtCursor(spaced) -> InsertMethod.SET_TEXT
            else -> {
                target.copyToClipboard(text)
                InsertMethod.CLIPBOARD
            }
        }
    }

    fun withSpace(before: CharSequence?, text: String): String {
        if (before.isNullOrEmpty() || text.isEmpty()) return text
        if (before.last().isWhitespace() || text.first().isWhitespace()) return text
        if (text.first() in NO_SPACE_BEFORE) return text
        return " $text"
    }

    /** Puts [insert] in place of the selection. Returns the new text and the new cursor position. */
    fun splice(current: CharSequence, selStart: Int, selEnd: Int, insert: String): Pair<String, Int> {
        val length = current.length
        val a = if (selStart in 0..length) selStart else length
        val b = if (selEnd in 0..length) selEnd else a
        val start = minOf(a, b)
        val end = maxOf(a, b)
        val result = buildString {
            append(current, 0, start)
            append(insert)
            append(current, end, length)
        }
        return result to start + insert.length
    }

    private const val NO_SPACE_BEFORE = ".,!?;:)]}'\"…"
}
