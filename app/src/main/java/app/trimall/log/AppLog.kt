package app.trimall.log

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Verbose file logger. Everything the app does is written here: every shell command,
 * its exit code, stdout and stderr, GC polling, and results. Rotates at 2 MB, keeps 5 files.
 */
object AppLog {
    private const val MAX_BYTES = 2L * 1024 * 1024
    private const val KEEP_FILES = 5
    private const val TAIL = 1500

    private val lock = Any()
    private lateinit var dir: File
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val tailBuf = ArrayDeque<String>()
    private val _tail = MutableStateFlow<List<String>>(emptyList())

    /** Live tail for the Logs screen. */
    val tail: StateFlow<List<String>> = _tail.asStateFlow()

    /** Tag added to every line while a run is active. */
    @Volatile
    var runId: String? = null

    fun init(context: Context) {
        dir = File(context.filesDir, "logs").apply { mkdirs() }
        synchronized(lock) {
            val f = current()
            if (f.exists()) f.readLines().takeLast(TAIL).forEach { tailBuf.addLast(it) }
            _tail.value = tailBuf.toList()
        }
    }

    private fun current() = File(dir, "trimall.log")

    fun v(tag: String, msg: String) = log('V', tag, msg)
    fun d(tag: String, msg: String) = log('D', tag, msg)
    fun i(tag: String, msg: String) = log('I', tag, msg)
    fun w(tag: String, msg: String) = log('W', tag, msg)
    fun e(tag: String, msg: String, t: Throwable? = null) =
        log('E', tag, if (t == null) msg else msg + "\n" + Log.getStackTraceString(t))

    private fun log(level: Char, tag: String, msg: String) {
        val prio = when (level) {
            'V' -> Log.VERBOSE; 'D' -> Log.DEBUG; 'I' -> Log.INFO; 'W' -> Log.WARN; else -> Log.ERROR
        }
        Log.println(prio, "TrimAll/$tag", msg)
        if (!::dir.isInitialized) return
        val rid = runId?.let { " [$it]" } ?: ""
        synchronized(lock) {
            val stamp = fmt.format(Date())
            val lines = msg.split('\n').map { "$stamp $level/$tag$rid: $it" }
            try {
                val f = current()
                if (f.exists() && f.length() > MAX_BYTES) rotate()
                f.appendText(lines.joinToString("\n", postfix = "\n"))
            } catch (e: Exception) {
                Log.e("TrimAll/AppLog", "write failed", e)
            }
            lines.forEach {
                tailBuf.addLast(it)
                while (tailBuf.size > TAIL) tailBuf.removeFirst()
            }
            _tail.value = tailBuf.toList()
        }
    }

    private fun rotate() {
        File(dir, "trimall.log.$KEEP_FILES").delete()
        for (i in KEEP_FILES - 1 downTo 1) {
            val from = File(dir, "trimall.log.$i")
            if (from.exists()) from.renameTo(File(dir, "trimall.log.${i + 1}"))
        }
        current().renameTo(File(dir, "trimall.log.1"))
    }

    fun files(): List<File> = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()

    fun clear() {
        synchronized(lock) {
            files().forEach { it.delete() }
            tailBuf.clear()
            _tail.value = emptyList()
        }
    }
}
