package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.util.ScreenAwakeController

class ScreenAwakeControllerTest {

    private class Recorder {
        val calls = mutableListOf<Boolean>()
        var held = false
        val controller = ScreenAwakeController { hold ->
            calls += hold
            held = hold
        }
    }

    @Test
    fun disabledSettingNeverHolds() {
        val r = Recorder()
        r.controller.onResume()
        r.controller.setRequested(false)
        assertFalse(r.held)
        assertEquals(listOf<Boolean>(), r.calls)
    }

    @Test
    fun enabledAndResumedHolds() {
        val r = Recorder()
        r.controller.setRequested(true)
        // Setting applied before onResume (composition runs first) must not
        // take the lock until the activity is actually visible.
        assertFalse(r.held)
        r.controller.onResume()
        assertTrue(r.held)
    }

    @Test
    fun pauseReleases() {
        val r = Recorder()
        r.controller.setRequested(true)
        r.controller.onResume()
        assertTrue(r.held)
        r.controller.onPause()
        assertFalse(r.held)
        assertEquals(listOf(true, false), r.calls)
    }

    @Test
    fun screenOffReleasesEvenWhileResumed() {
        val r = Recorder()
        r.controller.setRequested(true)
        r.controller.onResume()
        r.controller.onScreenOff()
        assertFalse(r.held)
        // A resumed-but-locked window must not keep the display awake on ROMs
        // that show the app behind the lock screen.
        assertEquals(listOf(true, false), r.calls)
    }

    @Test
    fun screenOnWhileResumedReapplies() {
        val r = Recorder()
        r.controller.setRequested(true)
        r.controller.onResume()
        r.controller.onScreenOff()
        r.controller.onScreenOn()
        assertTrue(r.held)
        assertEquals(listOf(true, false, true), r.calls)
    }

    @Test
    fun screenOnWhilePausedStaysReleased() {
        val r = Recorder()
        r.controller.setRequested(true)
        r.controller.onResume()
        r.controller.onPause()
        r.controller.onScreenOn()
        assertFalse(r.held)
        assertEquals(listOf(true, false), r.calls)
    }

    @Test
    fun turningSettingOffReleases() {
        val r = Recorder()
        r.controller.setRequested(true)
        r.controller.onResume()
        r.controller.setRequested(false)
        assertFalse(r.held)
        assertEquals(listOf(true, false), r.calls)
    }

    @Test
    fun repeatedEventsDoNotChurnTheWindow() {
        val r = Recorder()
        r.controller.setRequested(true)
        r.controller.onResume()
        r.controller.onScreenOn()
        r.controller.onResume()
        assertEquals(listOf(true), r.calls)
    }
}
