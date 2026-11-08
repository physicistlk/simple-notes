package personal.notes.touch.util

/**
 * Owns the "Keep Screen On" setting's effect on the window.
 *
 * The flag is a *screen* wake lock: while it is held the display never times
 * out. That is only ever wanted while the app is actually on screen, so the
 * controller holds it only when all three facts agree:
 *
 *  - the user enabled the setting,
 *  - the activity is resumed (visible and in the foreground),
 *  - the display is on (not locked).
 *
 * Any one of them turning false releases the flag immediately, including on
 * `ACTION_SCREEN_OFF`. Holding it in the background would keep the display
 * (and therefore the device) awake on ROMs that keep showing the app behind a
 * lock-screen preview, which is exactly the "battery drains when locked"
 * symptom this guards against.
 *
 * Pure logic with an injected sink so it can be unit tested.
 */
class ScreenAwakeController(private val applyFlag: (hold: Boolean) -> Unit) {

    private var requested = false
    private var resumed = false
    private var screenOn = true
    private var applied = false

    /** True while the screen wake lock should be held. */
    val isHolding: Boolean
        get() = requested && resumed && screenOn

    /** The "Keep Screen On" setting changed. */
    fun setRequested(enabled: Boolean) {
        if (requested != enabled) {
            requested = enabled
            sync()
        }
    }

    /** The activity became visible and interactive. */
    fun onResume() {
        resumed = true
        // Resuming means the display is on (even if the last event we saw was
        // ACTION_SCREEN_OFF); the next screen-off event clears it again.
        screenOn = true
        sync()
    }

    /** The activity left the foreground (home, another app, screen lock). */
    fun onPause() {
        resumed = false
        sync()
    }

    /** The display turned off: release the flag before the device can sleep. */
    fun onScreenOff() {
        screenOn = false
        sync()
    }

    /** The display turned on again (possibly still behind the keyguard). */
    fun onScreenOn() {
        screenOn = true
        sync()
    }

    private fun sync() {
        val hold = isHolding
        if (hold != applied) {
            applied = hold
            applyFlag(hold)
        }
    }
}
