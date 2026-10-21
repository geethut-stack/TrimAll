package app.trimall.core

import android.content.Context
import android.os.Build
import app.trimall.log.AppLog
import java.io.File

/**
 * Finds an fstrim to run:
 *  1. the device's own fstrim (toybox / util-linux) if present;
 *  2. otherwise a binary bundled in assets/fstrim/<abi>/fstrim (e.g. EXALAB/fstrim-static),
 *     copied to app storage and made executable.
 */
object FstrimBinary {
    private const val TAG = "Fstrim"

    suspend fun resolve(context: Context, exec: Executor): String? {
        val sys = exec.exec("command -v fstrim")
        val path = sys.out.firstOrNull()?.trim()
        if (sys.ok && !path.isNullOrEmpty()) {
            AppLog.i(TAG, "Using system fstrim: $path")
            logVersion(exec, path)
            return path
        }
        AppLog.w(TAG, "No system fstrim found, looking for a bundled binary")
        for (abi in Build.SUPPORTED_ABIS) {
            val asset = "fstrim/$abi/fstrim"
            val exists = runCatching { context.assets.open(asset).close(); true }.getOrDefault(false)
            if (!exists) continue
            val dest = File(context.filesDir, "bin/fstrim")
            dest.parentFile?.mkdirs()
            context.assets.open(asset).use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
            val ch = exec.exec("chmod 755 ${shellQuote(dest.absolutePath)}")
            if (ch.ok) {
                AppLog.i(TAG, "Using bundled fstrim ($abi): ${dest.absolutePath}")
                logVersion(exec, dest.absolutePath)
                return dest.absolutePath
            }
        }
        AppLog.e(TAG, "No fstrim available. Put a static build at app/src/main/assets/fstrim/<abi>/fstrim")
        return null
    }

    private suspend fun logVersion(exec: Executor, path: String) {
        exec.exec("${shellQuote(path)} --version")
    }
}
