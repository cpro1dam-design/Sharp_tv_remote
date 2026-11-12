package tvremote.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Background = Color(0xFF101318)
    val Surface = Color(0xFF1B2230)
    val SurfaceHigh = Color(0xFF263044)
    val SurfacePressed = Color(0xFF34415C)
    val OnSurface = Color(0xFFE8ECF4)
    val Muted = Color(0xFF8D97AB)
    val Accent = Color(0xFF4C8DFF)
    val Power = Color(0xFFE5484D)
    val Ok = Color(0xFF3FB950)
    val Red = Color(0xFFE5484D)
    val Green = Color(0xFF3FB950)
    val Yellow = Color(0xFFE3B341)
    val Blue = Color(0xFF4C8DFF)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            onPrimary = Color.White,
            background = Palette.Background,
            onBackground = Palette.OnSurface,
            surface = Palette.Background,
            onSurface = Palette.OnSurface,
            surfaceVariant = Palette.Surface,
            onSurfaceVariant = Palette.Muted,
            surfaceContainer = Palette.Surface,
            surfaceContainerHigh = Palette.SurfaceHigh,
            error = Palette.Power,
        ),
        content = content,
    )
}
