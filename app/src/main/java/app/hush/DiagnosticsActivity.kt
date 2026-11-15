package app.hush

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView

/** The last microphone, engine and typing results, as text that the user can copy. */
class DiagnosticsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.appBarTitle).setText(R.string.title_diagnostics)
    }

    override fun onResume() {
        super.onResume()
        val prefs = Prefs(this)
        val language = prefs.language
        val ready = ModelStore.of(this).isReady(language.model)
        val lines = listOf(
            "Hush ${packageManager.getPackageInfo(packageName, 0).versionName}",
            "${Build.MANUFACTURER} ${Build.MODEL}, ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}",
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "model: ${language.model.id}, " + if (ready) "on this phone" else "not downloaded",
            "mic: " + (prefs.noteOf(Diagnostics.MIC) ?: getString(R.string.diag_none)),
            "engine: " + (prefs.noteOf(Diagnostics.ENGINE) ?: getString(R.string.diag_none)),
            "insert: " + (prefs.noteOf(Diagnostics.INSERT) ?: getString(R.string.diag_none)),
        )
        findViewById<TextView>(R.id.diagnostics).text = lines.joinToString("\n")
    }
}
