package app.hush

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import kotlin.math.roundToInt

/** Shows one setup step at a time, then the ready screen. With [EXTRA_LANGUAGE_PAGE] it shows only the language choice. */
class SetupActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var store: ModelStore
    private val languagePage by lazy { intent.getBooleanExtra(EXTRA_LANGUAGE_PAGE, false) }
    private var step = SetupStep.MIC
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }
    private val serviceListener = AccessibilityManager.AccessibilityServicesStateChangeListener { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        prefs = Prefs(this)
        store = ModelStore.of(this)
        if (!languagePage) Thread({ store.prune(Catalog.models) }, "hush-prune").start()
        addLanguageOptions()
        view<View>(R.id.back).setOnClickListener { finish() }
        view<TextView>(R.id.appBarTitle).setText(R.string.title_language)
        view<View>(R.id.menuButton).setOnClickListener { showMenu(it) }
        view<View>(R.id.changeButton).setOnClickListener { startActivity(languagePage(this)) }
        view<View>(R.id.mainButton).setOnClickListener { onMainButton() }
        view<View>(R.id.secondButton).setOnClickListener { onSecondButton() }
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        refresh()
    }

    private fun language() = Catalog.language(prefs.languageCode)

    private fun refresh() {
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val unrestricted = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        step = SetupStep.of(mic, store.isReady(language().model), serviceEnabled(this), unrestricted, prefs.batterySkipped)
        if (languagePage) showLanguagePage() else showStep()
    }

    private fun showStep() {
        val ready = step == SetupStep.READY
        show(R.id.appBar, false)
        show(R.id.stepBar, !ready)
        show(R.id.menuBar, ready)
        if (!ready) {
            val segments = view<ViewGroup>(R.id.segments)
            for (i in 0 until segments.childCount) {
                segments.getChildAt(i).setBackgroundResource(if (i < step.number - 1) R.drawable.step_on else R.drawable.step_off)
            }
            view<TextView>(R.id.stepLabel).text = getString(R.string.step_of, step.number)
        }
        when (step) {
            SetupStep.MODEL -> hero(112, 66, 24)
            SetupStep.READY -> hero(124, 74, 4)
            else -> hero(140, 84, 24)
        }
        view<TextView>(R.id.title).setText(
            when (step) {
                SetupStep.MIC -> R.string.title_mic
                SetupStep.MODEL -> R.string.title_language
                SetupStep.SERVICE -> R.string.title_accessibility
                SetupStep.BATTERY -> R.string.title_battery
                SetupStep.READY -> R.string.title_ready
            },
        )
        text(
            R.id.lead,
            when (step) {
                SetupStep.MIC -> R.string.lead_mic
                SetupStep.SERVICE -> R.string.lead_accessibility
                SetupStep.BATTERY -> R.string.lead_battery
                else -> null
            },
        )
        show(R.id.summary, ready)
        show(R.id.languageGroup, step == SetupStep.MODEL)
        text(
            R.id.note,
            when (step) {
                SetupStep.MODEL -> R.string.model_hint
                SetupStep.SERVICE -> R.string.note_accessibility
                SetupStep.BATTERY -> R.string.note_battery
                else -> null
            },
        )
        show(R.id.languageCard, ready)
        show(R.id.testField, ready)
        if (ready) {
            val language = language()
            view<TextView>(R.id.languageName).text = language.label
            view<TextView>(R.id.languageModel).text =
                getString(R.string.model_summary, language.note, megabytes(ModelStore.totalBytes(language.model)))
        }
        show(R.id.actions, !ready)
        when (step) {
            SetupStep.MIC -> actions(R.string.action_allow, null)
            SetupStep.MODEL -> modelActions()
            SetupStep.SERVICE -> actions(R.string.action_open_accessibility, R.string.action_open_apps)
            SetupStep.BATTERY -> actions(R.string.action_app_info, R.string.action_skip)
            SetupStep.READY -> Unit
        }
    }

    private fun showLanguagePage() {
        show(R.id.appBar, true)
        show(R.id.stepBar, false)
        show(R.id.menuBar, false)
        show(R.id.disc, false)
        show(R.id.title, false)
        show(R.id.lead, false)
        show(R.id.summary, false)
        show(R.id.languageGroup, true)
        text(R.id.note, R.string.model_hint)
        show(R.id.languageCard, false)
        show(R.id.testField, false)
        val spec = language().model
        val busy = Downloads.status?.let { it.specId == spec.id && it.running } == true
        show(R.id.actions, busy || !store.isReady(spec))
        modelActions()
    }

    private fun hero(size: Int, ghost: Int, top: Int) {
        val disc = view<View>(R.id.disc)
        disc.visibility = View.VISIBLE
        (disc.layoutParams as ViewGroup.MarginLayoutParams).apply {
            width = dp(size)
            height = dp(size)
            topMargin = dp(top)
        }
        disc.requestLayout()
        view<View>(R.id.ghost).layoutParams.apply {
            width = dp(ghost)
            height = (dp(ghost) * GHOST_RATIO).roundToInt()
        }
        show(R.id.title, true)
    }

    private fun actions(main: Int?, second: Int?) {
        show(R.id.progressText, false)
        show(R.id.progressBar, false)
        button(R.id.mainButton, main?.let(::getString))
        button(R.id.secondButton, second?.let(::getString))
    }

    private fun modelActions() {
        val spec = language().model
        val download = Downloads.status?.takeIf { it.specId == spec.id }
        val anyRunning = Downloads.status?.running == true
        val group = view<RadioGroup>(R.id.languageGroup)
        // The language page is another activity, so the choice can change while this screen is in the back stack.
        group.findViewWithTag<View>(language().code)?.let { if (group.checkedRadioButtonId != it.id) group.check(it.id) }
        for (i in 0 until group.childCount) {
            val option = group.getChildAt(i) as RadioButton
            option.isEnabled = !anyRunning
            option.alpha = if (anyRunning && !option.isChecked) DISABLED_ALPHA else 1f
        }
        val progressText = view<TextView>(R.id.progressText)
        val progress = view<ProgressBar>(R.id.progressBar)
        if (download?.running == true) {
            val fraction = if (download.total > 0) download.done.toFloat() / download.total else 0f
            progressText.text = getString(R.string.status_model_downloading, (fraction * 100).toInt(), megabytes(download.total))
            progressText.visibility = View.VISIBLE
            progress.progress = (fraction * progress.max).toInt()
            progress.visibility = View.VISIBLE
            button(R.id.mainButton, null)
            button(R.id.secondButton, getString(R.string.action_cancel))
        } else {
            progressText.text = download?.error?.let { getString(R.string.status_model_failed, it) }
            progressText.visibility = if (download?.error != null) View.VISIBLE else View.GONE
            progress.visibility = View.GONE
            button(R.id.mainButton, getString(R.string.action_download, megabytes(store.missingBytes(spec))))
            button(R.id.secondButton, null)
        }
    }

    private fun onMainButton() {
        when {
            languagePage || step == SetupStep.MODEL -> Downloads.start(this, language().model)
            step == SetupStep.MIC -> askMic()
            step == SetupStep.SERVICE -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            step == SetupStep.BATTERY -> openAppInfo()
        }
        refresh()
    }

    private fun onSecondButton() {
        when {
            languagePage || step == SetupStep.MODEL -> Downloads.cancel()
            // Android hides "Allow restricted settings" when Hush itself opens its app info.
            step == SetupStep.SERVICE -> startActivity(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
            step == SetupStep.BATTERY -> {
                prefs.batterySkipped = true
                refresh()
            }
        }
    }

    private fun askMic() {
        // After a second "Don't allow", Android does not show the request again. Then only app info can allow it.
        if (prefs.micAsked && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            openAppInfo()
        } else {
            prefs.micAsked = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun openAppInfo() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    private fun addLanguageOptions() {
        val group = view<RadioGroup>(R.id.languageGroup)
        val regular = Typeface.create(resources.getFont(R.font.inter), 400, false)
        val noteSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13.5f, resources.displayMetrics).roundToInt()
        for (language in Catalog.languages) {
            val option = layoutInflater.inflate(R.layout.language_option, group, false) as RadioButton
            option.id = View.generateViewId()
            option.tag = language.code
            val note = getString(R.string.option_note, language.note, megabytes(ModelStore.totalBytes(language.model)))
            option.text = SpannableStringBuilder(language.label).append('\n').apply {
                val start = length
                append(note)
                setSpan(AbsoluteSizeSpan(noteSize), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(getColor(R.color.mute)), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(TypefaceSpan(regular), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            group.addView(option)
            if (language.code == language().code) option.isChecked = true
        }
        group.setOnCheckedChangeListener { radios, checkedId ->
            prefs.languageCode = radios.findViewById<View>(checkedId).tag as String
        }
    }

    // The popup is the root of its own window, so it has no parent.
    @SuppressLint("InflateParams")
    private fun showMenu(anchor: View) {
        val content = layoutInflater.inflate(R.layout.menu_popup, null)
        // A known width lets Gravity.END put the popup 10 dp from the screen edge.
        content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popup = PopupWindow(content, content.measuredWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.setBackgroundDrawable(getDrawable(R.drawable.bg_popup))
        popup.elevation = dp(8).toFloat()
        content.findViewById<View>(R.id.settingsItem).setOnClickListener {
            popup.dismiss()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        val button = anchor as ImageButton
        button.setBackgroundResource(R.drawable.bg_menu_open)
        button.imageTintList = ColorStateList.valueOf(getColor(R.color.fg))
        popup.setOnDismissListener {
            button.setBackgroundResource(R.drawable.bg_round_button)
            button.imageTintList = null
        }
        popup.showAsDropDown(anchor, -dp(6), -dp(6), Gravity.END)
    }

    private fun text(id: Int, text: Int?) {
        val view = view<TextView>(id)
        if (text != null) view.setText(text)
        view.visibility = if (text != null) View.VISIBLE else View.GONE
    }

    private fun button(id: Int, text: String?) {
        val button = view<Button>(id)
        button.text = text
        button.visibility = if (text != null) View.VISIBLE else View.GONE
    }

    private fun show(id: Int, visible: Boolean) {
        view<View>(id).visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private fun <T : View> view(id: Int): T = findViewById(id)

    companion object {
        private const val EXTRA_LANGUAGE_PAGE = "language_page"
        // Height to width of the ghost drawable with its float marks.
        private const val GHOST_RATIO = 62f / 54f
        private const val DISABLED_ALPHA = 0.5f

        fun languagePage(context: Context) = Intent(context, SetupActivity::class.java).putExtra(EXTRA_LANGUAGE_PAGE, true)

        fun megabytes(bytes: Long) = (bytes + 500_000) / 1_000_000

        fun serviceEnabled(context: Context): Boolean {
            val me = ComponentName(context, HushService::class.java)
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
