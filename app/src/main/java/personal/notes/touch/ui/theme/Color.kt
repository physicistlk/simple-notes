package personal.notes.touch.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val OrangePrimary = Color(0xFFFB6443)
val OrangePrimaryDark = Color(0xFFE05232)
val OrangeLight = Color(0xFFFFECE6)

val BackgroundLight = Color(0xFFF8F9FA)
val SurfaceLight = Color(0xFFFFFFFF)
val TextPrimaryLight = Color(0xFF212529)
val TextSecondaryLight = Color(0xFF6C757D)
val DividerLight = Color(0xFFE9ECEF)

val BackgroundDark = Color(0xFF121212)
val SurfaceDark = Color(0xFF1E1E1E)
val TextPrimaryDark = Color(0xFFF8F9FA)
val TextSecondaryDark = Color(0xFFADB5BD)
val DividerDark = Color(0xFF2C2C2C)

data class ThemeSwatch(
    val id: String,
    val name: String,
    val color: Color
)

val TouchNotesSwatches = listOf(
    ThemeSwatch("orange", "Orange (Default)", Color(0xFFFB6443)),
    ThemeSwatch("blue", "Ocean Blue", Color(0xFF2196F3)),
    ThemeSwatch("dark_blue", "Deep Navy", Color(0xFF1565C0)),
    ThemeSwatch("green", "Forest Green", Color(0xFF4CAF50)),
    ThemeSwatch("dark_green", "Emerald", Color(0xFF2E7D32)),
    ThemeSwatch("light_blue", "Sky Blue", Color(0xFF03A9F4)),
    ThemeSwatch("light_green", "Mint", Color(0xFF8BC34A)),
    ThemeSwatch("pink", "Rose Pink", Color(0xFFE91E63)),
    ThemeSwatch("yellow", "Warm Amber", Color(0xFFFFB300)),
    ThemeSwatch("beige", "Warm Sand", Color(0xFF8D6E63))
)

val LocalThemeColor = staticCompositionLocalOf { OrangePrimary }
