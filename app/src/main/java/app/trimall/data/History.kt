package app.trimall.data

import android.content.Context
import app.trimall.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class Status { OK, FAILED, SKIPPED, UNSUPPORTED }

data class GcOutcome(
    val status: Status,
    val note: String,
    val millis: Long,
    val dirtyBefore: Long?,
    val dirtyAfter: Long?,
    val freeBefore: Long?,
    val freeAfter: Long?,
)

data class MountResult(
    val mountPoint: String,
    val device: String,
    val fsType: String,
    val readOnly: Boolean,
    val gc: GcOutcome?,
    val trimStatus: Status,
    /** Bytes passed down for discard (what fstrim reports), not bytes the device freed. */
    val trimBytes: Long?,
    val trimMillis: Long,
    val trimOutput: String,
)

data class RunRecord(
    val id: String,
    val trigger: String,
    val startedAt: Long,
    val endedAt: Long,
    val overall: String, // OK | PARTIAL | FAILED
    val summary: String,
    val environment: String,
    val mounts: List<MountResult>,
)

private fun JSONObject.longOrNull(k: String): Long? = if (has(k) && !isNull(k)) getLong(k) else null

private fun GcOutcome.toJson() = JSONObject().apply {
    put("status", status.name); put("note", note); put("millis", millis)
    put("dirtyBefore", dirtyBefore ?: JSONObject.NULL); put("dirtyAfter", dirtyAfter ?: JSONObject.NULL)
    put("freeBefore", freeBefore ?: JSONObject.NULL); put("freeAfter", freeAfter ?: JSONObject.NULL)
}

private fun gcFromJson(o: JSONObject) = GcOutcome(
    Status.valueOf(o.getString("status")), o.getString("note"), o.getLong("millis"),
    o.longOrNull("dirtyBefore"), o.longOrNull("dirtyAfter"),
    o.longOrNull("freeBefore"), o.longOrNull("freeAfter"),
)

private fun MountResult.toJson() = JSONObject().apply {
    put("mountPoint", mountPoint); put("device", device); put("fsType", fsType); put("readOnly", readOnly)
    put("gc", gc?.toJson() ?: JSONObject.NULL)
    put("trimStatus", trimStatus.name); put("trimBytes", trimBytes ?: JSONObject.NULL)
    put("trimMillis", trimMillis); put("trimOutput", trimOutput)
}

private fun mountFromJson(o: JSONObject) = MountResult(
    o.getString("mountPoint"), o.getString("device"), o.getString("fsType"), o.getBoolean("readOnly"),
    if (o.isNull("gc")) null else gcFromJson(o.getJSONObject("gc")),
    Status.valueOf(o.getString("trimStatus")), o.longOrNull("trimBytes"),
    o.getLong("trimMillis"), o.getString("trimOutput"),
)

private fun RunRecord.toJson() = JSONObject().apply {
    put("id", id); put("trigger", trigger); put("startedAt", startedAt); put("endedAt", endedAt)
    put("overall", overall); put("summary", summary); put("environment", environment)
    put("mounts", JSONArray().also { a -> mounts.forEach { a.put(it.toJson()) } })
}

private fun runFromJson(o: JSONObject): RunRecord {
    val arr = o.getJSONArray("mounts")
    return RunRecord(
        o.getString("id"), o.getString("trigger"), o.getLong("startedAt"), o.getLong("endedAt"),
        o.getString("overall"), o.getString("summary"), o.getString("environment"),
        (0 until arr.length()).map { mountFromJson(arr.getJSONObject(it)) },
    )
}

/** Run history, newest first, persisted as JSON (last 200 runs). */
object HistoryStore {
    private const val MAX = 200
    private val lock = Any()
    private lateinit var file: File
    private val _records = MutableStateFlow<List<RunRecord>>(emptyList())
    val records: StateFlow<List<RunRecord>> = _records.asStateFlow()

    fun init(context: Context) {
        file = File(context.filesDir, "history.json")
        synchronized(lock) {
            try {
                if (file.exists()) {
                    val arr = JSONArray(file.readText())
                    _records.value = (0 until arr.length()).map { runFromJson(arr.getJSONObject(it)) }
                }
            } catch (e: Exception) {
                AppLog.e("History", "Could not load history.json", e)
            }
        }
    }

    fun file(): File = file

    fun add(record: RunRecord) = synchronized(lock) {
        val list = (listOf(record) + _records.value).take(MAX)
        _records.value = list
        save(list)
    }

    fun clear() = synchronized(lock) {
        _records.value = emptyList()
        save(emptyList())
    }

    private fun save(list: List<RunRecord>) {
        try {
            file.writeText(JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }.toString())
        } catch (e: Exception) {
            AppLog.e("History", "Could not write history.json", e)
        }
    }
}
