package app.trimall

import android.content.Context
import android.content.SharedPreferences

/** Small settings + last-run snapshot store (also read by the widget). */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences("trimall", Context.MODE_PRIVATE)
    }

    var trimEnabled: Boolean
        get() = sp.getBoolean("trim", true)
        set(v) = sp.edit().putBoolean("trim", v).apply()

    var gcEnabled: Boolean
        get() = sp.getBoolean("gc", true)
        set(v) = sp.edit().putBoolean("gc", v).apply()

    /** F2FS GC stops waiting once dirty segments fall to this value. */
    var gcThreshold: Int
        get() = sp.getInt("gc_threshold", 512)
        set(v) = sp.edit().putInt("gc_threshold", v).apply()

    /** Hard stop for the GC wait loop (so a run always ends). */
    var gcTimeoutSec: Int
        get() = sp.getInt("gc_timeout", 600)
        set(v) = sp.edit().putInt("gc_timeout", v).apply()

    var rootGranted: Boolean
        get() = sp.getBoolean("root_granted", false)
        set(v) = sp.edit().putBoolean("root_granted", v).apply()

    var lastRunAt: Long
        get() = sp.getLong("last_at", 0L)
        set(v) = sp.edit().putLong("last_at", v).apply()

    var lastRunStatus: String
        get() = sp.getString("last_status", "") ?: ""
        set(v) = sp.edit().putString("last_status", v).apply()

    var lastRunSummary: String
        get() = sp.getString("last_summary", "") ?: ""
        set(v) = sp.edit().putString("last_summary", v).apply()
}
