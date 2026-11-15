package app.hush

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

/** The short Settings list: language and model, Delete model, My words, Diagnostics and About. */
class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var store: ModelStore

    // The note under the words field: why words did not go in. Null shows the full note when the list is full.
    private var wordsNote: String? = null

    // The words list shows only its first rows until the user taps "Show all". After an add, the list opens when
    // the new words are in the rows that do not show, so the user sees them.
    private var wordsOpen = false
    private var showAdded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)
        store = ModelStore.of(this)
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        text(R.id.appBarTitle, getString(R.string.title_settings))
        findViewById<View>(R.id.languageRow).setOnClickListener { startActivity(SetupActivity.languagePage(this)) }
        findViewById<View>(R.id.deleteRow).setOnClickListener { confirmDelete() }
        findViewById<View>(R.id.changeModel).setOnClickListener { startActivity(SetupActivity.languagePage(this)) }
        findViewById<View>(R.id.wordsAdd).setOnClickListener { addWords() }
        findViewById<FlowLayout>(R.id.wordChips).onFlowChange = ::onWordsFlow
        findViewById<View>(R.id.wordsMore).setOnClickListener { openWords(!wordsOpen) }
        findViewById<EditText>(R.id.wordsField).setOnEditorActionListener { _, action, _ ->
            (action == EditorInfo.IME_ACTION_DONE).also { if (it) addWords() }
        }
        findViewById<View>(R.id.diagnosticsRow).setOnClickListener { startActivity(Intent(this, DiagnosticsActivity::class.java)) }
        text(R.id.diagnosticsSummary, getString(R.string.diagnostics_summary))
        text(R.id.aboutTitle, getString(R.string.about_version, packageManager.getPackageInfo(packageName, 0).versionName))
    }

    override fun onResume() {
        super.onResume()
        Downloads.listener = ::refresh
        refresh()
    }

    override fun onPause() {
        super.onPause()
        Downloads.listener = null
    }

    private fun refresh() {
        val language = prefs.language
        val megabytes = SetupActivity.megabytes(ModelStore.totalBytes(language.model))
        text(R.id.languageSummary, getString(R.string.language_summary, language.label, language.note, megabytes))
        val downloading = Downloads.status?.let { it.specId == language.model.id && it.running } == true
        val canDelete = store.isReady(language.model) && !downloading
        findViewById<View>(R.id.deleteRow).apply {
            isEnabled = canDelete
            alpha = if (canDelete) 1f else DISABLED_ALPHA
        }
        text(R.id.deleteSummary, if (canDelete) getString(R.string.delete_summary, megabytes) else getString(R.string.delete_none))
        showWords()
    }

    /**
     * The words as chips, the count and the note. When the chosen model cannot use them, the section stays, but it is
     * off and says why. When the list is full, the field and + are off until the user removes a word.
     */
    private fun showWords() {
        val model = prefs.language.model
        val on = model.takesWords
        val words = prefs.myWords
        val full = words.size >= MyWords.MAX_WORDS
        prefs.wordsRemoved.takeIf { it > 0 }?.let { removed ->
            wordsNote = resources.getQuantityString(
                R.plurals.words_removed, removed, removed, MyWords.MAX_WORDS, MyWords.MAX_ENTRY_WORDS, MyWords.MAX_ENTRY_CHARS,
            )
            prefs.wordsRemoved = 0
        }
        findViewById<View>(R.id.wordsOff).visibility = if (on) View.GONE else View.VISIBLE
        text(R.id.wordsOffText, getString(R.string.words_cannot, model.name))
        text(R.id.wordsExplain, getString(if (words.isEmpty()) R.string.words_explain_empty else R.string.words_explain))
        val chips = findViewById<FlowLayout>(R.id.wordChips)
        chips.maxRows = if (wordsOpen) 0 else CLOSED_ROWS
        chips.removeAllViews()
        for (word in words) {
            val chip = LayoutInflater.from(this).inflate(R.layout.word_chip, chips, false)
            chip.findViewById<TextView>(R.id.word).text = word
            chip.findViewById<View>(R.id.remove).apply {
                contentDescription = getString(R.string.words_remove, word)
                setOnClickListener { removeWord(word) }
            }
            enable(chip, on)
            chips.addView(chip)
        }
        showMore()
        findViewById<TextView>(R.id.wordsCount).apply {
            text = getString(R.string.words_count, words.size, MyWords.MAX_WORDS)
            contentDescription = resources.getQuantityString(R.plurals.words_count_description, MyWords.MAX_WORDS, words.size, MyWords.MAX_WORDS)
            setTextColor(getColor(if (full && on) R.color.danger else R.color.mute))
        }
        val note = wordsNote ?: if (full) fullNote() else null
        findViewById<TextView>(R.id.wordsNote).apply {
            text = note
            visibility = if (on && note != null) View.VISIBLE else View.GONE
        }
        val field = findViewById<EditText>(R.id.wordsField)
        if (full && field.hasFocus()) {
            field.clearFocus()
            getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(field.windowToken, 0)
        }
        enable(field, on && !full)
        enable(findViewById(R.id.wordsAdd), on && !full)
    }

    /**
     * Adds the words of the field. A word that is too long stays in the field, so the user can make it shorter. When
     * the list gets full, the field empties and the note says how many words did not go in.
     */
    private fun addWords() {
        val field = findViewById<EditText>(R.id.wordsField)
        val words = prefs.myWords
        val result = MyWords.add(words, field.text.toString())
        val full = result.words.size >= MyWords.MAX_WORDS
        val refused = result.notAdded + if (full) result.tooLong.size else 0
        wordsNote = when {
            full && refused > 0 ->
                resources.getQuantityString(R.plurals.words_not_added, refused, refused) + " " + fullNote()
            result.tooLong.size == 1 -> resources.getQuantityString(
                R.plurals.words_too_long, MyWords.MAX_ENTRY_WORDS,
                short(result.tooLong[0]), MyWords.MAX_ENTRY_WORDS, MyWords.MAX_ENTRY_CHARS,
            )
            result.tooLong.isNotEmpty() -> resources.getQuantityString(
                R.plurals.words_too_long_many, result.tooLong.size,
                result.tooLong.size, MyWords.MAX_ENTRY_WORDS, MyWords.MAX_ENTRY_CHARS,
            )
            else -> null
        }
        field.setText(if (full) "" else result.tooLong.joinToString(", "))
        field.setSelection(field.length())
        if (result.words != words) prefs.myWords = result.words
        showAdded = result.words.size > words.size
        showWords()
        // With a large font, the note can be under the keyboard. The user must see why words did not go in.
        if (wordsNote != null) findViewById<TextView>(R.id.wordsNote).let { note ->
            note.post { note.requestRectangleOnScreen(Rect(0, 0, note.width, note.height), false) }
        }
    }

    /** After the chips get their rows: the button can change, and the list opens to show the words just added. */
    private fun onWordsFlow() {
        val chips = findViewById<FlowLayout>(R.id.wordChips)
        if (showAdded && !wordsOpen && chips.shownCount < chips.childCount) openWords(true) else showMore()
        showAdded = false
    }

    private fun openWords(open: Boolean) {
        wordsOpen = open
        findViewById<FlowLayout>(R.id.wordChips).maxRows = if (open) 0 else CLOSED_ROWS
        showMore()
    }

    /** "Show all 20 words" or "Show less", only when the words need more rows than the closed list shows. */
    private fun showMore() {
        val chips = findViewById<FlowLayout>(R.id.wordChips)
        findViewById<Button>(R.id.wordsMore).apply {
            visibility = if (chips.rowCount > CLOSED_ROWS) View.VISIBLE else View.GONE
            text = if (wordsOpen) {
                getString(R.string.words_show_less)
            } else {
                resources.getQuantityString(R.plurals.words_show_all, chips.childCount, chips.childCount)
            }
            setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, if (wordsOpen) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down, 0)
        }
    }

    private fun removeWord(word: String) {
        prefs.myWords = prefs.myWords - word
        wordsNote = null
        showWords()
    }

    private fun fullNote() = resources.getQuantityString(R.plurals.words_full, MyWords.MAX_WORDS, MyWords.MAX_WORDS)

    /** [word], cut with an ellipsis when it is long, for a note. */
    private fun short(word: String) =
        if (word.length <= NOTE_WORD_CHARS) word else word.take(NOTE_WORD_CHARS - 1).trimEnd() + "…"

    private fun enable(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) for (i in 0 until view.childCount) enable(view.getChildAt(i), enabled)
    }

    private fun confirmDelete() {
        val spec = prefs.language.model
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(getString(R.string.delete_confirm_text, SetupActivity.megabytes(ModelStore.totalBytes(spec))))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                store.delete(spec)
                refresh()
            }
            .show()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(getColor(R.color.danger))
    }

    private fun text(id: Int, value: String) {
        findViewById<TextView>(id).text = value
    }

    private companion object {
        const val DISABLED_ALPHA = 0.5f
        const val NOTE_WORD_CHARS = 28
        const val CLOSED_ROWS = 2
    }
}
