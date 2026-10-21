package app.trimall

import android.app.Application
import app.trimall.core.RootManager
import app.trimall.data.HistoryStore
import app.trimall.log.AppLog
import com.topjohnwu.superuser.Shell

class TrimApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        AppLog.init(this)
        HistoryStore.init(this)

        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER) // see the real, global mount namespace
                .setTimeout(60) // leave time for the root manager prompt
        )
        AppLog.i("App", "TrimAll ${BuildConfig.VERSION_NAME} started (debug=${BuildConfig.DEBUG})")
        RootManager.refresh()
    }
}
