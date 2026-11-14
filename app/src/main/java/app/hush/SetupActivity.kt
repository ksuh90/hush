package app.hush

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView

class SetupActivity : Activity() {
    private lateinit var prefs: Prefs

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
        findViewById<View>(R.id.modelSection).visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

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
