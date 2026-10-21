package app.trimall.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.trimall.data.HistoryStore
import app.trimall.log.AppLog
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Exporter {
    /** Zips all log files and history.json, then opens the share sheet. */
    fun share(context: Context) {
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val zip = File(dir, "trimall-export.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            (AppLog.files() + HistoryStore.file()).filter { it.exists() }.forEach { f ->
                z.putNextEntry(ZipEntry(f.name))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", zip)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        AppLog.i("Export", "Sharing ${zip.length()} bytes")
        context.startActivity(Intent.createChooser(send, "Export logs and history").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
