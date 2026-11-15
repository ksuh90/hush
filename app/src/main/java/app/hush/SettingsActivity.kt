package app.hush

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView

/** The short Settings list: language and model, Delete model, Diagnostics and About. */
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
        findViewById<View>(R.id.diagnosticsRow).setOnClickListener { startActivity(Intent(this, DiagnosticsActivity::class.java)) }
        text(R.id.diagnosticsSummary, getString(R.string.diagnostics_summary))
        text(R.id.aboutTitle, getString(R.string.about_version, packageManager.getPackageInfo(packageName, 0).versionName))
        text(R.id.aboutSummary, getString(R.string.about_summary))
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
        val language = Catalog.language(prefs.languageCode)
        val megabytes = SetupActivity.megabytes(ModelStore.totalBytes(language.model))
        text(R.id.languageSummary, getString(R.string.language_summary, language.label, language.note, megabytes))
        val downloading = Downloads.status?.let { it.specId == language.model.id && it.running } == true
        val canDelete = store.isReady(language.model) && !downloading
        findViewById<View>(R.id.deleteRow).apply {
            isEnabled = canDelete
            alpha = if (canDelete) 1f else DISABLED_ALPHA
        }
        text(R.id.deleteSummary, if (canDelete) getString(R.string.delete_summary, megabytes) else getString(R.string.delete_none))
    }

    private fun confirmDelete() {
        val spec = Catalog.language(prefs.languageCode).model
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
