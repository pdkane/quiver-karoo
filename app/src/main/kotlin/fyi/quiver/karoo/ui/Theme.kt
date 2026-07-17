package fyi.quiver.karoo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val QuiverColors = darkColorScheme(
    primary = Color(0xFFB87333), // copper — Quiver's accent
    onPrimary = Color(0xFF1A1A1A),
    background = Color(0xFF121212),
    onBackground = Color(0xFFF2EFEA),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFF2EFEA),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = QuiverColors, content = content)
}
