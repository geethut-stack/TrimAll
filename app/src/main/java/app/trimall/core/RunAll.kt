package app.trimall.core

import android.content.Context
import android.os.Build
import app.trimall.Fmt
import app.trimall.Prefs
import app.trimall.data.GcOutcome
import app.trimall.data.HistoryStore
import app.trimall.data.MountResult
import app.trimall.data.RunRecord
import app.trimall.data.Status
import app.trimall.log.AppLog
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The "Run all" action: root check → (F2FS GC) → trim every mount → record + log everything. */
class RunAll(private val context: Context) {
    private val tag = "RunAll"

    suspend fun execute(trigger: String): RunRecord {
        val startedAt = System.currentTimeMillis()
        val id = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(startedAt))
        AppLog.runId = id
        AppLog.i(tag, "==== RUN START id=$id trigger=$trigger ====")
        AppLog.i(tag, "Settings: trim=${Prefs.trimEnabled} f2fsGc=${Prefs.gcEnabled} gcThreshold=${Prefs.gcThreshold} gcTimeout=${Prefs.gcTimeoutSec}s")

        val mounts = mutableListOf<MountResult>()
        var env = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        var overall = "FAILED"
        var summary = ""

        try {
            RunController.update("Checking root…")
            val exec = RootExecutor()
            if (!exec.isAvailable()) {
                Prefs.rootGranted = false
                RootManager.refresh()
                overall = "FAILED"
                summary = "Root not available. Open the app and tap Request root."
                AppLog.e(tag, summary)
            } else {
                Prefs.rootGranted = true
                RootManager.refresh()
                env = collectEnvironment(exec, env)
                runAll(exec, mounts)
                val failed = mounts.count { it.trimStatus == Status.FAILED || it.gc?.status == Status.FAILED }
                val ok = mounts.count { it.trimStatus == Status.OK || it.gc?.status == Status.OK }
                overall = when {
                    failed == 0 -> "OK"
                    ok > 0 -> "PARTIAL"
                    else -> "FAILED"
                }
                summary = buildSummary(mounts, failed)
            }
        } catch (e: CancellationException) {
            AppLog.w(tag, "Run cancelled")
            AppLog.runId = null
            throw e
        } catch (e: Throwable) {
            AppLog.e(tag, "Run crashed", e)
            overall = "FAILED"
            summary = "Crashed: ${e.message}"
        }

        val record = RunRecord(id, trigger, startedAt, System.currentTimeMillis(), overall, summary, env, mounts)
        HistoryStore.add(record)
        Prefs.lastRunAt = record.endedAt
        Prefs.lastRunStatus = overall
        Prefs.lastRunSummary = summary
        AppLog.i(tag, "==== RUN END $overall in ${Fmt.dur(record.endedAt - startedAt)}: $summary ====")
        AppLog.runId = null
        return record
    }

    private suspend fun collectEnvironment(exec: Executor, base: String): String {
        AppLog.i(tag, "Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), fingerprint=${Build.FINGERPRINT}")
        AppLog.i(tag, "Android ${Build.VERSION.RELEASE} SDK ${Build.VERSION.SDK_INT}, patch ${Build.VERSION.SECURITY_PATCH}, ABIs ${Build.SUPPORTED_ABIS.joinToString()}")
        val uname = exec.exec("uname -a").out.firstOrNull()?.trim().orEmpty()
        exec.exec("id")
        exec.exec("getenforce")
        exec.exec("su -v")
        exec.exec("ls /sys/fs/f2fs")
        return if (uname.isNotEmpty()) "$base, $uname" else base
    }

    private suspend fun runAll(exec: Executor, out: MutableList<MountResult>) {
        RunController.update("Scanning mounts…")
        val scan = exec.exec("cat /proc/mounts")
        val entries = MountScanner.parse(scan.out)
        AppLog.i(tag, "Candidate mounts (${entries.size}): " + entries.joinToString { "${it.mountPoint}[${it.fsType}]" })

        val fstrim = if (Prefs.trimEnabled) FstrimBinary.resolve(context, exec) else null
        val trimmer = fstrim?.let { TrimExecutor(exec, it) }
        val gc = F2fsGc(exec, Prefs.gcThreshold.toLong(), Prefs.gcTimeoutSec)

        entries.forEachIndexed { i, m ->
            RunController.update("${m.mountPoint} (${i + 1}/${entries.size})", i, entries.size)
            var gcOut: GcOutcome? = null
            if (Prefs.gcEnabled && m.fsType == "f2fs") {
                RunController.update("GC on ${m.mountPoint} (${i + 1}/${entries.size})", i, entries.size)
                gcOut = gc.run(m)
            }
            RunController.update("Trimming ${m.mountPoint} (${i + 1}/${entries.size})", i, entries.size)
            val t = when {
                !Prefs.trimEnabled -> TrimOutcome(Status.SKIPPED, null, 0, "trim disabled in settings")
                trimmer == null -> TrimOutcome(Status.FAILED, null, 0, "no fstrim binary available")
                else -> trimmer.trim(m)
            }
            out += MountResult(m.mountPoint, m.device, m.fsType, m.readOnly, gcOut, t.status, t.bytes, t.millis, t.output)
        }
        RunController.update("Finishing…", entries.size, entries.size)
    }

    private fun buildSummary(r: List<MountResult>, failed: Int): String {
        if (r.isEmpty()) return "No trimmable mounts found"
        val trimmed = r.filter { it.trimStatus == Status.OK }
        val bytes = trimmed.sumOf { it.trimBytes ?: 0L }
        val unsupported = r.count { it.trimStatus == Status.UNSUPPORTED }
        val gcCount = r.count { it.gc?.status == Status.OK }
        return buildString {
            append("Trimmed ${trimmed.size}/${r.size} mounts (${Fmt.bytes(bytes)} passed to discard)")
            if (gcCount > 0) append(", GC on $gcCount")
            if (unsupported > 0) append(", $unsupported unsupported")
            if (failed > 0) append(", $failed failed")
        }
    }

    companion object {
        /** Recovery: force gc_urgent back to 0 on every F2FS node. */
        suspend fun resetGcUrgent(): ExecResult {
            val exec = RootExecutor()
            AppLog.i("RunAll", "Manual reset of gc_urgent requested")
            if (!exec.isAvailable()) {
                AppLog.e("RunAll", "Root not available for reset")
                return ExecResult("reset", 1, emptyList(), listOf("Root not available"), 0)
            }
            return exec.exec("for f in /sys/fs/f2fs/*/gc_urgent; do echo \"\$f was \$(cat \$f)\"; echo 0 > \$f; done")
        }
    }
}
