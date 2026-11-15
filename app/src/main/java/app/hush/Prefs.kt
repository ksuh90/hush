package app.hush

import android.content.Context
import android.content.SharedPreferences
import java.text.DateFormat
import java.util.Date

class Prefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("hush", Context.MODE_PRIVATE)

    /** The chosen language and model. Hush remembers the model of each language. */
    var language: Language
        get() {
            val code = prefs.getString("language", null) ?: Catalog.languages.first().code
            return Catalog.language(code, prefs.getString("model_$code", null))
        }
        set(value) = prefs.edit().putString("language", value.code).putString("model_${value.code}", value.model.id).apply()

    /** The user's words ("My words"), in the order the user added them. They stay when the model changes. */
    var myWords: List<String>
        get() = prefs.getString("my_words", null)?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()
        set(value) = prefs.edit().putString("my_words", value.joinToString("\n")).apply()

    /** True after Hush asked for the microphone one time. Then Android can stop showing the request. */
    var micAsked: Boolean
        get() = prefs.getBoolean("mic_asked", false)
        set(value) = prefs.edit().putBoolean("mic_asked", value).apply()

    /** True after the user tapped Skip on the battery step. */
    var batterySkipped: Boolean
        get() = prefs.getBoolean("battery_skipped", false)
        set(value) = prefs.edit().putBoolean("battery_skipped", value).apply()

    /** Bubble center in screen pixels, or null when the user did not move it. */
    var bubbleAnchor: Pair<Int, Int>?
        get() {
            val x = prefs.getInt("bubble_x", -1)
            val y = prefs.getInt("bubble_y", -1)
            return if (x < 0 || y < 0) null else x to y
        }
        set(value) = prefs.edit().putInt("bubble_x", value?.first ?: -1).putInt("bubble_y", value?.second ?: -1).apply()

    fun note(key: String, value: String) {
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
        prefs.edit().putString(key, "$time  $value").apply()
    }

    fun noteOf(key: String): String? = prefs.getString(key, null)

    fun listen(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unlisten(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
}

/** Last results that the Diagnostics screen shows, so the user can report them. */
object Diagnostics {
    const val MIC = "diag_mic"
    const val INSERT = "diag_insert"
    const val ENGINE = "diag_engine"

    fun mic(context: Context, value: String) = Prefs(context).note(MIC, value)
    fun insert(context: Context, value: String) = Prefs(context).note(INSERT, value)
    fun engine(context: Context, value: String) = Prefs(context).note(ENGINE, value)
}
