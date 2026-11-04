package personal.notes.touch

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import personal.notes.touch.ui.nav.TouchNotesNavHost
import personal.notes.touch.ui.theme.OrangePrimary
import personal.notes.touch.ui.theme.TouchNotesTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TouchNotesApp

        setContent {
            val settings by app.settingsRepository.getSettings().collectAsState(initial = null)
            val currentSettings = settings

            // Apply Keep Screen On
            val keepScreenOn = currentSettings?.keepScreenOn ?: false
            if (keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            // Apply Full-Screen
            val fullScreen = currentSettings?.fullScreen ?: false
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (fullScreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }

            // Dark mode resolution: 0 = System, 1 = Light, 2 = Dark
            val darkTheme = when (currentSettings?.darkMode ?: 0) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }

            val themeColor = currentSettings?.themeColor?.let { Color(it) } ?: OrangePrimary

            TouchNotesTheme(
                primaryColor = themeColor,
                darkTheme = darkTheme
            ) {
                TouchNotesNavHost(app = app)
            }
        }
    }
}
