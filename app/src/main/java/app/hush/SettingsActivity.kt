package app.hush

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView

/** The short Settings list: language and model, Delete model, My words, Diagnostics and About. */
class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var store: ModelStore

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

    /** The words as chips. When the chosen model cannot use them, the section stays, but it is off and says why. */
    private fun showWords() {
        val model = prefs.language.model
        val on = model.takesWords
        val words = prefs.myWords
        findViewById<View>(R.id.wordsOff).visibility = if (on) View.GONE else View.VISIBLE
        text(R.id.wordsOffText, getString(R.string.words_cannot, model.name))
        text(R.id.wordsExplain, getString(if (words.isEmpty()) R.string.words_explain_empty else R.string.words_explain))
        val chips = findViewById<ViewGroup>(R.id.wordChips)
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
        enable(findViewById(R.id.wordsField), on)
        enable(findViewById(R.id.wordsAdd), on)
    }

    private fun addWords() {
        val field = findViewById<EditText>(R.id.wordsField)
        val words = prefs.myWords
        val added = MyWords.add(words, field.text.toString())
        field.text.clear()
        if (added != words) {
            prefs.myWords = added
            showWords()
        }
    }

    private fun removeWord(word: String) {
        prefs.myWords = prefs.myWords - word
        showWords()
    }

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
    }
}
