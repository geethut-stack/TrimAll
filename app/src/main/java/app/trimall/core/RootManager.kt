package app.trimall.core

import android.content.Context
import app.trimall.Prefs
import app.trimall.log.AppLog
import app.trimall.widget.WidgetUpdater
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RootState { UNKNOWN, CHECKING, GRANTED, DENIED }

object RootManager {
    private const val TAG = "Root"
    private val _state = MutableStateFlow(RootState.UNKNOWN)
    val state: StateFlow<RootState> = _state.asStateFlow()

    /** Cheap, non-prompting check of an already-open shell. */
    fun refresh() {
        val shell = Shell.getCachedShell() ?: return
        _state.value = if (shell.isRoot) RootState.GRANTED else RootState.DENIED
    }

    /**
     * Triggers the root manager's grant prompt (Magisk / KernelSU / APatch).
     * A previously opened non-root shell is closed first so `su` is attempted again.
     */
    fun request(context: Context) {
        val app = context.applicationContext
        _state.value = RootState.CHECKING
        AppLog.i(TAG, "Requesting root via su")
        Thread {
            Shell.getCachedShell()?.takeIf { !it.isRoot }?.let {
                AppLog.d(TAG, "Closing cached non-root shell to retry su")
                runCatching { it.close() }
            }
            Shell.getShell { shell ->
                val granted = shell.isRoot
                Prefs.rootGranted = granted
                _state.value = if (granted) RootState.GRANTED else RootState.DENIED
                AppLog.i(TAG, "Root request result: ${if (granted) "GRANTED" else "DENIED / unavailable"} (shell status=${shell.status})")
                WidgetUpdater.refresh(app)
            }
        }.start()
    }
}
