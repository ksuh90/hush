package app.hush

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/** Puts text in the focused field of another app. */
class FieldTarget(
    private val service: AccessibilityService,
    private val node: AccessibilityNodeInfo?,
) : InsertTarget {

    private fun connection(): android.accessibilityservice.InputMethod.AccessibilityInputConnection? {
        val inputMethod = service.inputMethod ?: return null
        if (!inputMethod.currentInputStarted) return null
        val editor = inputMethod.currentInputEditorInfo
        // Do not type into an editor of a different app than the focused field.
        if (node != null && editor?.packageName != null && editor.packageName != node.packageName?.toString()) return null
        return inputMethod.currentInputConnection
    }

    private fun nodeText(): CharSequence =
        if (node == null || node.isShowingHintText) "" else node.text ?: ""

    override fun textBeforeCursor(): CharSequence? {
        try {
            connection()?.getSurroundingText(BEFORE, 0, 0)?.let { surrounding ->
                return surrounding.text.subSequence(0, surrounding.selectionStart.coerceIn(0, surrounding.text.length))
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "getSurroundingText failed", e)
        }
        if (node == null) return null
        val text = nodeText()
        val start = node.textSelectionStart
        return if (start in 0..text.length) text.subSequence(0, start) else text
    }

    override fun commitAtCursor(text: String): Boolean {
        val connection = connection() ?: return false
        return try {
            connection.commitText(text, 1, null)
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "commitText failed", e)
            false
        }
    }

    override fun setTextAtCursor(text: String): Boolean {
        val field = node ?: return false
        if (!field.isEditable) return false
        val (newText, cursor) = Inserter.splice(nodeText(), field.textSelectionStart, field.textSelectionEnd, text)
        val setArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        }
        if (!field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)) return false
        val selectionArgs = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
        }
        field.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
        return true
    }

    override fun copyToClipboard(text: String) {
        service.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(service.getString(R.string.app_name), text))
    }

    private companion object {
        const val TAG = "Hush"
        const val BEFORE = 64
    }
}
