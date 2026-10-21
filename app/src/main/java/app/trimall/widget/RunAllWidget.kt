package app.trimall.widget

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.trimall.Prefs
import app.trimall.core.RunController
import app.trimall.service.RunAllService
import app.trimall.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** One tap = the whole Run all action (root check, F2FS GC, trim every mount). */
class RunAllWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val running = RunController.state.value
        val rootGranted = Prefs.rootGranted
        val lastAt = Prefs.lastRunAt
        val lastStatus = Prefs.lastRunStatus

        val status = when {
            running.running -> running.stage
            !rootGranted -> "Tap to grant root"
            lastAt == 0L -> "Not run yet"
            else -> "$lastStatus · " + DateUtils.getRelativeTimeSpanString(
                lastAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            )
        }

        val action: Action = if (rootGranted) {
            actionStartService(
                Intent(context, RunAllService::class.java)
                    .putExtra(RunAllService.EXTRA_TRIGGER, "widget"),
                isForegroundService = true,
            )
        } else {
            actionStartActivity<MainActivity>()
        }

        provideContent { Content(status, running.running, action) }
    }

    @Composable
    private fun Content(status: String, running: Boolean, action: Action) {
        val bg = ColorProvider(day = Color(0xFF0F2A2B), night = Color(0xFF0F2A2B))
        val fg = ColorProvider(day = Color(0xFFE8FFF7), night = Color(0xFFE8FFF7))
        val dim = ColorProvider(day = Color(0xFF9FD8C6), night = Color(0xFF9FD8C6))
        Column(
            modifier = GlanceModifier.fillMaxSize().background(bg).cornerRadius(20.dp)
                .padding(12.dp).clickable(action),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (running) "Running…" else "Run all",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = fg),
            )
            Text(text = status, style = TextStyle(fontSize = 12.sp, color = dim), maxLines = 1)
        }
    }
}

class RunAllWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RunAllWidget()
}

object WidgetUpdater {
    fun refresh(context: Context) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            runCatching { RunAllWidget().updateAll(app) }
        }
    }
}
