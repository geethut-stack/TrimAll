package app.trimall.core

import android.os.SystemClock
import app.trimall.log.AppLog
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExecResult(
    val command: String,
    val code: Int,
    val out: List<String>,
    val err: List<String>,
    val millis: Long,
) {
    val ok get() = code == 0
    val text get() = (out + err).joinToString("\n")
}

/** Where privileged commands run. Root is implemented; a Shizuku backend can slot in here later. */
interface Executor {
    val name: String
    suspend fun isAvailable(): Boolean
    suspend fun exec(command: String): ExecResult
}

fun shellQuote(s: String) = "'" + s.replace("'", "'\\''") + "'"

class RootExecutor : Executor {
    override val name = "root"

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        runCatching { Shell.getShell().isRoot }.getOrDefault(false)
    }

    override suspend fun exec(command: String): ExecResult = withContext(Dispatchers.IO) {
        val out = ArrayList<String>()
        val err = ArrayList<String>()
        AppLog.v("Exec", "$ $command")
        val t0 = SystemClock.elapsedRealtime()
        val r = Shell.cmd(command).to(out, err).exec()
        val ms = SystemClock.elapsedRealtime() - t0
        AppLog.v("Exec", "exit=${r.code} in ${ms}ms (stdout ${out.size} lines, stderr ${err.size} lines)")
        out.forEach { AppLog.v("Exec", "stdout| $it") }
        err.forEach { AppLog.v("Exec", "stderr| $it") }
        ExecResult(command, r.code, out, err, ms)
    }
}
