package app.cashbook

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class Palette(
    val paper: Color, val ink: Color, val muted: Color, val line: Color,
    val accent: Color, val accentSoft: Color, val onAccent: Color,
    val bar: Color, val bar2: Color, val ghost: Color, val warn: Color,
    val del: Color, val undo: Color,
)

private val Light = Palette(
    paper = Color(0xFFFBFCF8), ink = Color(0xFF1F2A26), muted = Color(0xFF66736D), line = Color(0xFFDDE2DA),
    accent = Color(0xFF2F6B57), accentSoft = Color(0xFFDCEBE3), onAccent = Color.White,
    bar = Color(0xFF2F6B57), bar2 = Color(0xFF9CC3B1), ghost = Color(0xFF9AA6A0), warn = Color(0xFFA6542B),
    del = Color(0xFFB4472E), undo = Color(0xFF9FE0C4),
)
private val Dark = Palette(
    paper = Color(0xFF1B221E), ink = Color(0xFFE6ECE8), muted = Color(0xFF97A59E), line = Color(0xFF2C3631),
    accent = Color(0xFF6FBF9F), accentSoft = Color(0xFF24372F), onAccent = Color(0xFF0F1A15),
    bar = Color(0xFF6FBF9F), bar2 = Color(0xFF3F6B5A), ghost = Color(0xFF62706A), warn = Color(0xFFE39468),
    del = Color(0xFFEC8F76), undo = Color(0xFF2F6B57),
)

val LocalPalette = staticCompositionLocalOf { Light }

@Composable
fun CashBookTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) Dark else Light
    val scheme = if (dark) darkColorScheme(
        primary = p.accent, onPrimary = p.onAccent, background = p.paper, surface = p.paper,
        onSurface = p.ink, onBackground = p.ink, surfaceContainerHigh = Color(0xFF242C28),
        primaryContainer = p.accentSoft, onPrimaryContainer = p.ink,
    ) else lightColorScheme(
        primary = p.accent, onPrimary = p.onAccent, background = p.paper, surface = p.paper,
        onSurface = p.ink, onBackground = p.ink, surfaceContainerHigh = Color(0xFFEFF2EC),
        primaryContainer = p.accentSoft, onPrimaryContainer = p.ink,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
