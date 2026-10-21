package app.trimall.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.trimall.R
import app.trimall.core.RunAll
import app.trimall.core.RunController
import app.trimall.log.AppLog
import app.trimall.ui.MainActivity
import app.trimall.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Foreground service so a run survives the app being closed (widget taps never open the UI). */
class RunAllService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val nm get() = getSystemService(NotificationManager::class.java)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val trigger = intent?.getStringExtra(EXTRA_TRIGGER) ?: "unknown"
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Trim runs", NotificationManager.IMPORTANCE_LOW)
        )
        startFg(progressNotification("Starting…", 0, 0))

        if (!RunController.tryBegin()) {
            // A run is already going; leave it alone (don't stopSelf, that would kill it).
            AppLog.w("Service", "Run requested by '$trigger' ignored: a run is already in progress")
            return START_NOT_STICKY
        }

        scope.launch {
            val watcher = launch {
                RunController.state.collect { s ->
                    nm.notify(NOTIF_ID, progressNotification(s.stage, s.done, s.total))
                }
            }
            WidgetUpdater.refresh(applicationContext)
            val record = try {
                RunAll(applicationContext).execute(trigger)
            } catch (e: Exception) {
                AppLog.e("Service", "Run failed", e)
                null
            } finally {
                RunController.end()
                watcher.cancel()
            }
            WidgetUpdater.refresh(applicationContext)
            if (record != null) {
                nm.notify(
                    RESULT_ID,
                    NotificationCompat.Builder(this@RunAllService, CHANNEL)
                        .setSmallIcon(R.drawable.ic_stat_trim)
                        .setContentTitle("Run all: ${record.overall}")
                        .setContentText(record.summary)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(record.summary))
                        .setContentIntent(openApp())
                        .setAutoCancel(true)
                        .build()
                )
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startFg(n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun progressNotification(stage: String, done: Int, total: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_trim)
            .setContentTitle("Trimming storage")
            .setContentText(stage)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total, done, total == 0)
            .setContentIntent(openApp())
            .build()

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        const val EXTRA_TRIGGER = "trigger"
        private const val CHANNEL = "trim_runs"
        private const val NOTIF_ID = 1
        private const val RESULT_ID = 2
    }
}
