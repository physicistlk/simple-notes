package personal.notes.touch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import personal.notes.touch.ui.nav.TouchNotesNavHost
import personal.notes.touch.ui.theme.OrangePrimary
import personal.notes.touch.ui.theme.TouchNotesTheme
import personal.notes.touch.util.DebugGestureInjector
import personal.notes.touch.util.ScreenAwakeController

class MainActivity : AppCompatActivity() {

    private lateinit var screenAwake: ScreenAwakeController
    private var screenReceiverRegistered = false

    /**
     * Keeps the app from holding a screen wake lock after the display turns
     * off. Releasing the flag on pause is not enough on its own: some ROMs
     * keep the last activity's window visible behind the keyguard, and a
     * window flag is otherwise only honoured while the window is visible, so
     * clearing it on screen-off guarantees the device can idle.
     */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> screenAwake.onScreenOff()
                Intent.ACTION_SCREEN_ON -> screenAwake.onScreenOn()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TouchNotesApp

        // The window flag is only ever applied through this controller: it
        // holds it while the activity is resumed, the setting is on and the
        // display is on, and releases it otherwise (see ScreenAwakeController).
        screenAwake = ScreenAwakeController { hold ->
            if (hold) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        if (BuildConfig.DEBUG) {
            // DEBUG-only adb hook (see util/DebugGestureInjector.kt): raw
            // /dev/input sendevent is SELinux-denied and concurrent `input
            // swipe` calls merge into one pointer, so the shell broadcasts a
            // synthetic two-pointer gesture that we play on the open canvas.
            // RECEIVER_EXPORTED is required so `adb shell am broadcast` (shell
            // uid) can reach this runtime-registered receiver.
            ContextCompat.registerReceiver(
                this,
                DebugGestureInjector.receiver,
                IntentFilter(DebugGestureInjector.ACTION_DEBUG_GESTURE),
                ContextCompat.RECEIVER_EXPORTED
            )
        }

        setContent {
            val settings by app.settingsRepository.getSettings().collectAsState(initial = null)
            val currentSettings = settings

            // Apply Keep Screen On. This is a LaunchedEffect rather than a
            // composition side effect: composition can be re-run for reasons
            // unrelated to this setting, and the flag belongs to the
            // lifecycle, not to a frame.
            val keepScreenOn = currentSettings?.keepScreenOn ?: false
            LaunchedEffect(keepScreenOn) {
                screenAwake.setRequested(keepScreenOn)
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

    override fun onResume() {
        super.onResume()
        screenAwake.onResume()
        if (!screenReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            ContextCompat.registerReceiver(
                this,
                screenReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            screenReceiverRegistered = true
        }
    }

    override fun onPause() {
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            screenReceiverRegistered = false
        }
        screenAwake.onPause()
        super.onPause()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        StylusTiltTracker.update(ev)
        PenButtonBus.onMotion(ev)
        return super.dispatchTouchEvent(ev)
    }

    /** Hover events carry the pen button state while the pen is in the air. */
    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        PenButtonBus.onMotion(ev)
        return super.dispatchGenericMotionEvent(ev)
    }

    /** Some pens report their buttons as key events (KEYCODE_STYLUS_BUTTON_*). */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (PenButtonBus.onKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        if (BuildConfig.DEBUG) {
            // Mirror of the onCreate debug hook registration; runCatching only
            // guards a never-registered lifecycle edge (registration is
            // unconditional under DEBUG).
            runCatching { unregisterReceiver(DebugGestureInjector.receiver) }
        }
        super.onDestroy()
    }
}

/**
 * Tracks the latest hardware stylus tilt angle (radians, 0 = perpendicular)
 * from [MotionEvent.AXIS_TILT] so Compose canvases can shade with pencil tilt.
 */
object StylusTiltTracker {
    @Volatile
    var currentTiltRad: Float = 0f
        private set

    fun update(ev: MotionEvent) {
        if (ev.pointerCount <= 0) return
        val toolType = ev.getToolType(0)
        if (toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER) {
            val tilt = ev.getAxisValue(MotionEvent.AXIS_TILT, 0)
            currentTiltRad = if (tilt.isFinite() && tilt >= 0f) tilt.coerceIn(0f, 1.45f) else 0f
        } else {
            currentTiltRad = 0f
        }
    }
}


/**
 * Pen barrel buttons, delivered once per press whichever way the pen reports them:
 * as [MotionEvent.getButtonState] bits (touching or hovering) or as stylus key events.
 *
 * Button 1 ([onPrimary]) and button 2 ([onSecondary]) are set by the canvas screen while
 * it is open. Everything the pen reports is logged under the tag `PenButtons`
 * (`adb logcat -s PenButtons`) to see what a given pen actually sends.
 */
object PenButtonBus {
    @Volatile var onPrimary: (() -> Unit)? = null
    @Volatile var onSecondary: (() -> Unit)? = null
    @Volatile private var owner: Any? = null

    fun register(owner: Any, primary: () -> Unit, secondary: () -> Unit) {
        this.owner = owner
        this.onPrimary = primary
        this.onSecondary = secondary
    }

    fun unregister(owner: Any) {
        if (this.owner === owner) {
            this.owner = null
            this.onPrimary = null
            this.onSecondary = null
        }
    }

    private const val TAG = "PenButtons"
    private var lastMask = 0
    private var lastRawButtons = 0

    private const val PRIMARY_BITS = MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_SECONDARY
    private const val SECONDARY_BITS = MotionEvent.BUTTON_STYLUS_SECONDARY or MotionEvent.BUTTON_TERTIARY

    fun onMotion(ev: MotionEvent) {
        if (ev.pointerCount <= 0) return
        val tool = ev.getToolType(0)
        if (tool != MotionEvent.TOOL_TYPE_STYLUS && tool != MotionEvent.TOOL_TYPE_ERASER) return
        val raw = ev.buttonState
        if (raw != lastRawButtons) {
            android.util.Log.d(TAG, "motion action=${ev.actionMasked} tool=$tool buttonState=0x${raw.toString(16)}")
            lastRawButtons = raw
        }
        val primary = (raw and PRIMARY_BITS) != 0
        val secondary = (raw and SECONDARY_BITS) != 0
        val mask = (if (primary) 1 else 0) or (if (secondary) 2 else 0)
        val rising = mask and lastMask.inv()
        lastMask = mask
        if (rising and 1 != 0) onPrimary?.invoke()
        if (rising and 2 != 0) onSecondary?.invoke()
    }

    /**
     * Returns true when the key was a pen button and was handled.
     *
     * Xiaomi Smart Pen (Gen 1 / Gen 2 / Focus Pen) pairs as an external Bluetooth HID
     * keyboard ("Xiaomi Smart Pen", vendor 0x1915, product 0x4d81) and emits
     * [android.view.KeyEvent.KEYCODE_PAGE_DOWN] / [android.view.KeyEvent.KEYCODE_DPAD_DOWN]
     * for the lower (primary) barrel button and [android.view.KeyEvent.KEYCODE_PAGE_UP] /
     * [android.view.KeyEvent.KEYCODE_DPAD_UP] for the upper (secondary) barrel button.
     */
    fun onKey(ev: android.view.KeyEvent): Boolean {
        val code = ev.keyCode
        val devName = ev.device?.name.orEmpty()
        val isXiaomiOrBtPen = devName.contains("Pen", ignoreCase = true) ||
            devName.contains("Stylus", ignoreCase = true)
        val isPageUpDownWhileCanvasActive = (onPrimary != null || onSecondary != null) &&
            (code == android.view.KeyEvent.KEYCODE_PAGE_UP ||
                code == android.view.KeyEvent.KEYCODE_PAGE_DOWN)
        val isStylusKeyCode = code == android.view.KeyEvent.KEYCODE_STYLUS_BUTTON_PRIMARY ||
            code == android.view.KeyEvent.KEYCODE_STYLUS_BUTTON_SECONDARY ||
            code == android.view.KeyEvent.KEYCODE_STYLUS_BUTTON_TERTIARY ||
            code == android.view.KeyEvent.KEYCODE_STYLUS_BUTTON_TAIL ||
            (ev.source and android.view.InputDevice.SOURCE_STYLUS) == android.view.InputDevice.SOURCE_STYLUS
        if (!isXiaomiOrBtPen && !isPageUpDownWhileCanvasActive && !isStylusKeyCode) return false

        android.util.Log.d(
            TAG,
            "key code=$code action=${ev.action} repeat=${ev.repeatCount} dev='$devName' source=0x${ev.source.toString(16)}"
        )
        val isPrimaryKey = code == android.view.KeyEvent.KEYCODE_STYLUS_BUTTON_PRIMARY ||
            code == android.view.KeyEvent.KEYCODE_PAGE_DOWN ||
            code == android.view.KeyEvent.KEYCODE_DPAD_DOWN
        val isSecondaryKey = code == android.view.KeyEvent.KEYCODE_STYLUS_BUTTON_SECONDARY ||
            code == android.view.KeyEvent.KEYCODE_STYLUS_BUTTON_TERTIARY ||
            code == android.view.KeyEvent.KEYCODE_PAGE_UP ||
            code == android.view.KeyEvent.KEYCODE_DPAD_UP
        if (!isPrimaryKey && !isSecondaryKey) return false

        if (ev.action == android.view.KeyEvent.ACTION_DOWN && ev.repeatCount == 0) {
            if (isPrimaryKey) {
                onPrimary?.invoke()
            } else {
                onSecondary?.invoke()
            }
        }
        return onPrimary != null || onSecondary != null
    }
}
