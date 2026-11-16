package app.hush

import org.junit.Assert.assertEquals
import org.junit.Test

class InserterTest {
    private class FakeTarget(
        val before: CharSequence? = null,
        val commitWorks: Boolean = false,
        val setTextWorks: Boolean = false,
    ) : InsertTarget {
        val calls = mutableListOf<String>()
        override fun textBeforeCursor() = before
        override fun commitAtCursor(text: String): Boolean {
            calls += "commit:$text"
            return commitWorks
        }
        override fun setTextAtCursor(text: String): Boolean {
            calls += "set:$text"
            return setTextWorks
        }
        override fun copyToClipboard(text: String) {
            calls += "copy:$text"
        }
    }

    @Test
    fun commitTextIsFirst() {
        val target = FakeTarget(commitWorks = true, setTextWorks = true)
        assertEquals(InsertMethod.COMMIT_TEXT, Inserter.insert("hi", target))
        assertEquals(listOf("commit:hi"), target.calls)
    }

    @Test
    fun setTextIsSecond() {
        val target = FakeTarget(setTextWorks = true)
        assertEquals(InsertMethod.SET_TEXT, Inserter.insert("hi", target))
        assertEquals(listOf("commit:hi", "set:hi"), target.calls)
    }

    @Test
    fun clipboardIsLast() {
        val target = FakeTarget(before = "Hello")
        assertEquals(InsertMethod.CLIPBOARD, Inserter.insert("world", target))
        assertEquals(listOf("commit: world", "set: world", "copy:world"), target.calls)
    }

    @Test
    fun spaceAfterAWord() {
        val target = FakeTarget(before = "Hello", commitWorks = true)
        Inserter.insert("world", target)
        assertEquals(listOf("commit: world"), target.calls)
    }

    @Test
    fun noSpaceRules() {
        assertEquals("world", Inserter.withSpace(null, "world"))
        assertEquals("world", Inserter.withSpace("", "world"))
        assertEquals("world", Inserter.withSpace("Hello ", "world"))
        assertEquals("world", Inserter.withSpace("Hello\n", "world"))
        assertEquals(", ok", Inserter.withSpace("Hello", ", ok"))
        assertEquals(" 안녕하세요", Inserter.withSpace("네", "안녕하세요"))
    }

    @Test
    fun spliceAtCursor() {
        assertEquals("Hello big world" to 10, Inserter.splice("Hello world", 6, 6, "big "))
    }

    @Test
    fun spliceReplacesSelection() {
        assertEquals("Hello there" to 11, Inserter.splice("Hello world", 6, 11, "there"))
        assertEquals("Hello there" to 11, Inserter.splice("Hello world", 11, 6, "there"))
    }

    @Test
    fun spliceWithoutCursorAppends() {
        assertEquals("Hello world" to 11, Inserter.splice("Hello", -1, -1, " world"))
        assertEquals("Hello world" to 11, Inserter.splice("Hello", 99, 99, " world"))
    }

    @Test
    fun spliceIntoEmptyField() {
        assertEquals("hi" to 2, Inserter.splice("", 0, 0, "hi"))
    }
}
