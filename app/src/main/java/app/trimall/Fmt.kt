package app.trimall

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fmt {
    fun bytes(b: Long): String {
        if (b < 1024) return "$b B"
        val units = arrayOf("KiB", "MiB", "GiB", "TiB")
        var v = b.toDouble()
        var i = -1
        while (v >= 1024 && i < units.lastIndex) {
            v /= 1024
            i++
        }
        return String.format(Locale.US, "%.2f %s", v, units[i])
    }

    fun time(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ms))

    fun dur(ms: Long): String =
        if (ms < 1000) "$ms ms" else String.format(Locale.US, "%.1f s", ms / 1000.0)
}
