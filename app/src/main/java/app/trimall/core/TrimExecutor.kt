package app.trimall.core

import app.trimall.data.Status
import app.trimall.log.AppLog

data class TrimOutcome(val status: Status, val bytes: Long?, val millis: Long, val output: String)

class TrimExecutor(private val exec: Executor, private val fstrimPath: String) {
    private val bytesParen = Regex("""\((\d+) bytes\)""")
    private val bytesPlain = Regex("""(\d+) bytes""")
    private val unsupported = Regex(
        "not supported|Operation not supported|Read-only file system|Invalid argument",
        RegexOption.IGNORE_CASE,
    )

    /** Full-range trim (no offset, length or minimum extent), verbose. */
    suspend fun trim(m: MountEntry): TrimOutcome {
        AppLog.i("Trim", "Trimming ${m.mountPoint} (${m.fsType}, ${m.device}${if (m.readOnly) ", ro" else ""})")
        val r = exec.exec("${shellQuote(fstrimPath)} -v ${shellQuote(m.mountPoint)}")
        val text = r.text.trim()
        val bytes = bytesParen.find(text)?.groupValues?.get(1)?.toLongOrNull()
            ?: bytesPlain.find(text)?.groupValues?.get(1)?.toLongOrNull()
        val status = when {
            r.ok -> Status.OK
            unsupported.containsMatchIn(text) -> Status.UNSUPPORTED
            else -> Status.FAILED
        }
        AppLog.i("Trim", "${m.mountPoint}: $status, bytes=${bytes ?: "n/a"}, ${r.millis} ms")
        return TrimOutcome(status, bytes, r.millis, text)
    }
}
