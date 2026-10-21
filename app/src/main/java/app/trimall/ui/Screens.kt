package app.trimall.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trimall.Fmt
import app.trimall.Prefs
import app.trimall.core.RootManager
import app.trimall.core.RootState
import app.trimall.core.RunAll
import app.trimall.core.RunController
import app.trimall.data.HistoryStore
import app.trimall.data.MountResult
import app.trimall.data.RunRecord
import app.trimall.data.Status
import app.trimall.log.AppLog
import app.trimall.service.RunAllService
import app.trimall.widget.RunAllWidgetReceiver
import kotlinx.coroutines.launch

private fun startRun(ctx: Context) {
    ContextCompat.startForegroundService(
        ctx,
        Intent(ctx, RunAllService::class.java).putExtra(RunAllService.EXTRA_TRIGGER, "app"),
    )
}

private fun pinWidget(ctx: Context) {
    val mgr = AppWidgetManager.getInstance(ctx)
    if (mgr.isRequestPinAppWidgetSupported) {
        mgr.requestPinAppWidget(ComponentName(ctx, RunAllWidgetReceiver::class.java), null, null)
    } else {
        Toast.makeText(ctx, "Long-press the home screen, open Widgets, pick TrimAll.", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun statusColor(overall: String): Color = when (overall) {
    "OK" -> MaterialTheme.colorScheme.primary
    "PARTIAL" -> Warn
    else -> Fail
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
    ) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } }
}

// ───────────────────────── Home ─────────────────────────

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val root by RootManager.state.collectAsStateWithLifecycle()
    val run by RunController.state.collectAsStateWithLifecycle()
    val history by HistoryStore.records.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { RootManager.refresh() }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("TrimAll", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)

        Panel {
            Text("Root access", fontWeight = FontWeight.SemiBold)
            Text(
                when (root) {
                    RootState.GRANTED -> "Granted. Commands run as root."
                    RootState.DENIED -> "Not granted. Tap Request root, then approve the prompt in your root manager."
                    RootState.CHECKING -> "Waiting for your root manager…"
                    RootState.UNKNOWN -> "Not checked yet."
                },
                color = if (root == RootState.DENIED) Fail else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { RootManager.request(ctx) }, enabled = root != RootState.CHECKING) {
                Text(if (root == RootState.GRANTED) "Check root again" else "Request root")
            }
        }

        Button(
            onClick = { startRun(ctx) },
            enabled = !run.running,
            modifier = Modifier.fillMaxWidth().height(68.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text(if (run.running) "Running…" else "Run all", fontSize = 22.sp, fontWeight = FontWeight.Bold) }

        if (run.running) {
            Text(run.stage)
            if (run.total > 0) LinearProgressIndicator(progress = { run.done / run.total.toFloat() }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        val last = history.firstOrNull()
        if (last == null) {
            Text("No runs yet. Run all trims every mount and, on F2FS, runs garbage collection first.")
        } else {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(last.overall, color = statusColor(last.overall), fontWeight = FontWeight.Bold)
                    Text(Fmt.time(last.startedAt), style = MaterialTheme.typography.bodySmall)
                }
                Text(last.summary)
            }
        }

        OutlinedButton(onClick = { pinWidget(ctx) }) { Text("Add the one-tap widget") }
    }
}

// ───────────────────────── History ─────────────────────────

@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val history by HistoryStore.records.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { Exporter.share(ctx) }) { Text("Export") }
            OutlinedButton(onClick = { HistoryStore.clear() }, enabled = history.isNotEmpty()) { Text("Clear history") }
        }
        if (history.isEmpty()) {
            Text("Nothing here yet. Finished runs show up with per-mount results.", Modifier.padding(20.dp))
        }
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { items(history, key = { it.id }) { RunCard(it) } }
    }
}

@Composable
private fun RunCard(r: RunRecord) {
    var open by rememberSaveable(r.id) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { open = !open },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(r.overall, color = statusColor(r.overall), fontWeight = FontWeight.Bold)
                Text(Fmt.time(r.startedAt), style = MaterialTheme.typography.bodySmall)
            }
            Text(r.summary)
            Text(
                "Started from the ${r.trigger}, took ${Fmt.dur(r.endedAt - r.startedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (open) {
                Text(r.environment, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                r.mounts.forEach { MountBlock(it) }
            } else if (r.mounts.isNotEmpty()) {
                Text("Tap for per-mount details", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MountBlock(m: MountResult) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("${m.mountPoint}  ${m.fsType}${if (m.readOnly) " ro" else ""}  ${m.device}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        m.gc?.let { g ->
            Text(
                "GC ${g.status}: ${g.note} (${Fmt.dur(g.millis)}); dirty ${g.dirtyBefore ?: "?"}→${g.dirtyAfter ?: "?"}, free ${g.freeBefore ?: "?"}→${g.freeAfter ?: "?"}",
                fontSize = 12.sp,
            )
        }
        val bytes = m.trimBytes?.let { " ${Fmt.bytes(it)} passed to discard," } ?: ""
        Text("Trim ${m.trimStatus}:$bytes ${Fmt.dur(m.trimMillis)}", fontSize = 12.sp)
        if (m.trimOutput.isNotBlank()) {
            Text(m.trimOutput, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ───────────────────────── Logs ─────────────────────────

@Composable
fun LogsScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val lines by AppLog.tail.collectAsStateWithLifecycle()
    val state = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) state.scrollToItem(lines.lastIndex) }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { Exporter.share(ctx) }) { Text("Export") }
            OutlinedButton(onClick = { AppLog.clear() }) { Text("Clear log") }
        }
        LazyColumn(state = state, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
            items(lines) { line ->
                val c = when {
                    line.contains(" E/") -> Fail
                    line.contains(" W/") -> Warn
                    line.contains(" V/") -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Text(line, color = c, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp)
            }
        }
    }
}

// ───────────────────────── Settings ─────────────────────────

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var trim by remember { mutableStateOf(Prefs.trimEnabled) }
    var gc by remember { mutableStateOf(Prefs.gcEnabled) }
    var threshold by remember { mutableStateOf(Prefs.gcThreshold.toString()) }
    var timeout by remember { mutableStateOf(Prefs.gcTimeoutSec.toString()) }
    var resetResult by remember { mutableStateOf("") }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("These choose what Run all does, in the app and from the widget.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        SwitchRow("Trim every mount", "Runs fstrim -v on each block-backed filesystem.", trim) { trim = it; Prefs.trimEnabled = it }
        SwitchRow("F2FS garbage collection first", "Forces GC on F2FS mounts before trimming, so the trim reclaims more.", gc) { gc = it; Prefs.gcEnabled = it }

        OutlinedTextField(
            value = threshold,
            onValueChange = { v -> threshold = v.filter(Char::isDigit).take(7); threshold.toIntOrNull()?.let { Prefs.gcThreshold = it } },
            label = { Text("Stop GC at this many dirty segments") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = timeout,
            onValueChange = { v -> timeout = v.filter(Char::isDigit).take(5); timeout.toIntOrNull()?.let { Prefs.gcTimeoutSec = it } },
            label = { Text("GC timeout per mount (seconds)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )

        Panel {
            Text("Recovery", fontWeight = FontWeight.SemiBold)
            Text("If a run was killed mid-GC, this sets gc_urgent back to 0 on every F2FS volume.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                scope.launch {
                    val r = RunAll.resetGcUrgent()
                    resetResult = if (r.ok) r.out.joinToString("\n").ifBlank { "Done." } else "Failed: ${r.text}"
                }
            }) { Text("Reset GC urgent") }
            if (resetResult.isNotBlank()) Text(resetResult, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }

        OutlinedButton(onClick = { Exporter.share(ctx) }) { Text("Export logs and history") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
