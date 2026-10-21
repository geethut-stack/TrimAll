package app.trimall.core

import android.os.SystemClock
import app.trimall.data.GcOutcome
import app.trimall.data.Status
import app.trimall.log.AppLog
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * AOSP idle-maintenance style GC: gc_urgent on, wait until dirty segments drop to the threshold
 * (or the timeout hits), gc_urgent off. gc_urgent is always switched back off, even on cancel.
 */
class F2fsGc(
    private val exec: Executor,
    private val thresholdSegments: Long,
    private val timeoutSec: Int,
) {
    private val tag = "F2fsGc"

    private suspend fun readLong(path: String): Long? =
        exec.exec("cat ${shellQuote(path)}").out.firstOrNull()?.trim()?.toLongOrNull()

    suspend fun run(m: MountEntry): GcOutcome {
        val t0 = SystemClock.elapsedRealtime()
        fun ms() = SystemClock.elapsedRealtime() - t0
        fun skipped(note: String, s: Status = Status.SKIPPED) =
            GcOutcome(s, note, ms(), null, null, null, null)

        if (m.readOnly) return skipped("mounted read-only")

        val real = exec.exec("readlink -f ${shellQuote(m.device)}").out.firstOrNull()?.trim()
            ?.takeIf { it.isNotEmpty() } ?: m.device
        val dir = "/sys/fs/f2fs/${real.substringAfterLast('/')}"
        if (!exec.exec("[ -d ${shellQuote(dir)} ]").ok) {
            val avail = exec.exec("ls /sys/fs/f2fs").out
            AppLog.w(tag, "No sysfs node $dir for ${m.mountPoint}. Available nodes: $avail")
            return skipped("no sysfs node $dir")
        }

        val urgent = shellQuote("$dir/gc_urgent")
        val dirtyBefore = readLong("$dir/dirty_segments")
        val freeBefore = readLong("$dir/free_segments")
        AppLog.i(tag, "${m.mountPoint}: node=$dir dirty=$dirtyBefore free=$freeBefore threshold=$thresholdSegments timeout=${timeoutSec}s")

        val on = exec.exec("echo 1 > $urgent")
        if (!on.ok) return skipped("cannot write gc_urgent: ${on.text}", Status.FAILED)

        var note = ""
        var resetOk = true
        try {
            AppLog.i(tag, "gc_urgent now reads ${exec.exec("cat $urgent").out.firstOrNull()?.trim()}")
            val deadline = t0 + timeoutSec * 1000L
            while (true) {
                val dirty = readLong("$dir/dirty_segments")
                AppLog.d(tag, "${m.mountPoint}: dirty_segments=$dirty free_segments=${readLong("$dir/free_segments")}")
                if (dirty != null && dirty <= thresholdSegments) {
                    note = "dirty segments reached $dirty (≤ $thresholdSegments)"
                    break
                }
                if (SystemClock.elapsedRealtime() >= deadline) {
                    note = "stopped at the ${timeoutSec}s timeout (dirty=$dirty)"
                    break
                }
                delay(2000)
            }
        } finally {
            withContext(NonCancellable) {
                val off = exec.exec("echo 0 > $urgent")
                val rb = exec.exec("cat $urgent").out.firstOrNull()?.trim()
                resetOk = off.ok && rb == "0"
                if (resetOk) AppLog.i(tag, "${m.mountPoint}: gc_urgent reset to 0")
                else AppLog.e(tag, "${m.mountPoint}: COULD NOT reset gc_urgent (reads $rb). Use Settings → Reset GC urgent.")
            }
        }

        val dirtyAfter = readLong("$dir/dirty_segments")
        val freeAfter = readLong("$dir/free_segments")
        AppLog.i(tag, "${m.mountPoint}: done in ${ms()} ms. $note. dirty $dirtyBefore→$dirtyAfter, free $freeBefore→$freeAfter")
        return GcOutcome(
            if (resetOk) Status.OK else Status.FAILED,
            if (resetOk) note else "$note; gc_urgent was NOT reset",
            ms(), dirtyBefore, dirtyAfter, freeBefore, freeAfter,
        )
    }
}
