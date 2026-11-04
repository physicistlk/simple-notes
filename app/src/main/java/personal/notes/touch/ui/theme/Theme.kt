package personal.notes.touch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

@Composable
fun TouchNotesTheme(
    primaryColor: Color = OrangePrimary,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            primaryContainer = primaryColor.copy(alpha = 0.3f),
            onPrimaryContainer = Color.White,
            secondary = primaryColor,
            onSecondary = Color.White,
            background = BackgroundDark,
            onBackground = TextPrimaryDark,
            surface = SurfaceDark,
            onSurface = TextPrimaryDark,
            surfaceVariant = Color(0xFF2C2C2C),
            onSurfaceVariant = TextSecondaryDark
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            primaryContainer = primaryColor.copy(alpha = 0.14f),
            onPrimaryContainer = primaryColor,
            secondary = primaryColor,
            onSecondary = Color.White,
            background = BackgroundLight,
            onBackground = TextPrimaryLight,
            surface = SurfaceLight,
            onSurface = TextPrimaryLight,
            surfaceVariant = Color(0xFFF1F3F5),
            onSurfaceVariant = TextSecondaryLight
        )
    }

    CompositionLocalProvider(LocalThemeColor provides primaryColor) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
