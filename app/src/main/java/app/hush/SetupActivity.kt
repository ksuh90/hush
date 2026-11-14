package app.hush

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView

class SetupActivity : Activity() {
    private lateinit var prefs: Prefs
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }
    private val serviceListener = AccessibilityManager.AccessibilityServicesStateChangeListener { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        prefs = Prefs(this)
        button(R.id.micButton).setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        button(R.id.a11yButton).setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        button(R.id.appInfoButton).setOnClickListener { openAppInfo() }
        button(R.id.batteryButton).setOnClickListener { openAppInfo() }
        val group = findViewById<RadioGroup>(R.id.languageGroup)
        for (language in Catalog.languages) {
            group.addView(RadioButton(this).apply {
                id = View.generateViewId()
                tag = language.code
                text = getString(R.string.language_option, language.label, language.note, megabytes(ModelStore.totalBytes(language.model)))
                isChecked = language.code == prefs.languageCode
            })
        }
        group.setOnCheckedChangeListener { radios, checkedId ->
            prefs.languageCode = radios.findViewById<View>(checkedId).tag as String
            refresh()
        }
        button(R.id.modelButton).setOnClickListener { onModelButton() }
    }

    override fun onResume() {
        super.onResume()
        Downloads.listener = ::refresh
        prefs.listen(prefsListener)
        getSystemService(AccessibilityManager::class.java).addAccessibilityServicesStateChangeListener(mainExecutor, serviceListener)
        refresh()
    }

    override fun onPause() {
        super.onPause()
        Downloads.listener = null
        prefs.unlisten(prefsListener)
        getSystemService(AccessibilityManager::class.java).removeAccessibilityServicesStateChangeListener(serviceListener)
    }

    private fun onModelButton() {
        val spec = Catalog.language(prefs.languageCode).model
        val download = Downloads.status?.takeIf { it.specId == spec.id && it.running }
        when {
            download != null -> Downloads.cancel()
            ModelStore.of(this).isReady(spec) -> ModelStore.of(this).delete(spec)
            else -> Downloads.start(this, spec)
        }
        refresh()
    }

    private fun refreshModel() {
        val spec = Catalog.language(prefs.languageCode).model
        val store = ModelStore.of(this)
        val download = Downloads.status?.takeIf { it.specId == spec.id }
        val progress = findViewById<ProgressBar>(R.id.modelProgress)
        val statusView = findViewById<TextView>(R.id.modelStatus)
        val ready = store.isReady(spec)
        progress.visibility = if (download?.running == true) View.VISIBLE else View.GONE
        when {
            download?.running == true -> {
                val fraction = if (download.total > 0) download.done.toFloat() / download.total else 0f
                progress.progress = (fraction * progress.max).toInt()
                statusView.text = getString(R.string.status_model_downloading, (fraction * 100).toInt(), megabytes(download.total))
                statusView.setTextColor(getColor(R.color.todo))
                button(R.id.modelButton).setText(R.string.action_cancel)
            }
            ready -> {
                status(R.id.modelStatus, true, R.string.status_model_ready, R.string.status_model_ready)
                button(R.id.modelButton).setText(R.string.action_delete_model)
            }
            else -> {
                statusView.text = download?.error?.let { getString(R.string.status_model_failed, it) }
                    ?: getString(R.string.status_model_missing)
                statusView.setTextColor(getColor(R.color.todo))
                button(R.id.modelButton).text = getString(R.string.action_download, megabytes(store.missingBytes(spec)))
            }
        }
    }

    private fun megabytes(bytes: Long) = (bytes + 500_000) / 1_000_000

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        refresh()
    }

    private fun refresh() {
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        status(R.id.micStatus, mic, R.string.status_mic_on, R.string.status_mic_off)
        button(R.id.micButton).visibility = if (mic) View.GONE else View.VISIBLE
        status(R.id.a11yStatus, serviceEnabled(), R.string.status_service_on, R.string.status_service_off)
        val unrestricted = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        status(R.id.batteryStatus, unrestricted, R.string.status_battery_on, R.string.status_battery_off)
        refreshModel()
        findViewById<TextView>(R.id.diagnostics).text = diagnostics()
    }

    private fun serviceEnabled(): Boolean {
        val me = ComponentName(this, HushService::class.java)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun diagnostics(): String {
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        val lines = listOf(
            "Hush $version",
            "${Build.MANUFACTURER} ${Build.MODEL}, ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}",
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "mic: " + (prefs.noteOf(Diagnostics.MIC) ?: getString(R.string.diag_none)),
            "engine: " + (prefs.noteOf(Diagnostics.ENGINE) ?: getString(R.string.diag_none)),
            "insert: " + (prefs.noteOf(Diagnostics.INSERT) ?: getString(R.string.diag_none)),
        )
        return lines.joinToString("\n")
    }

    private fun openAppInfo() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    private fun status(id: Int, ok: Boolean, onText: Int, offText: Int) {
        findViewById<TextView>(id).apply {
            setText(if (ok) onText else offText)
            setTextColor(getColor(if (ok) R.color.ok else R.color.todo))
        }
    }

    private fun button(id: Int): Button = findViewById(id)
}
