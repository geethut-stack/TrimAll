package app.trimall.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette: deep teal ink, mint accent, a single red for failures. No dynamic color on purpose.
val Mint = Color(0xFF7FE3C6)
val Ink = Color(0xFF0F2A2B)
val Fail = Color(0xFFB3261E)
val Warn = Color(0xFF9A6A00)

private val Dark = darkColorScheme(
    primary = Mint, onPrimary = Ink,
    background = Color(0xFF0B1B1C), onBackground = Color(0xFFE2F2EC),
    surface = Color(0xFF0B1B1C), onSurface = Color(0xFFE2F2EC),
    surfaceVariant = Color(0xFF16393A), onSurfaceVariant = Color(0xFFB5D4CA),
    secondaryContainer = Color(0xFF1F4B4B), onSecondaryContainer = Mint,
)
private val Light = lightColorScheme(
    primary = Color(0xFF0B6B58), onPrimary = Color.White,
    background = Color(0xFFF4F8F6), onBackground = Ink,
    surface = Color(0xFFF4F8F6), onSurface = Ink,
    surfaceVariant = Color(0xFFDCEBE5), onSurfaceVariant = Color(0xFF304B47),
    secondaryContainer = Color(0xFFC6E9DE), onSecondaryContainer = Ink,
)

@Composable
fun TrimTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
