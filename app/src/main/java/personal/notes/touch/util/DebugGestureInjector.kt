package personal.notes.touch.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import personal.notes.touch.ui.canvas.DrawingCanvasView
import java.lang.ref.WeakReference

/**
 * DEBUG-only adb hook that plays real two-pointer gestures straight onto the
 * most recently attached [DrawingCanvasView].
 *
 * On-device multi-touch cannot be scripted otherwise on this project: raw
 * `/dev/input` `sendevent` injection is SELinux-denied for the shell user and
 * two concurrent `input swipe` calls merge into a single pointer. MainActivity
 * registers [receiver] only when BuildConfig.DEBUG (with RECEIVER_EXPORTED so
 * the shell uid can deliver `adb shell am broadcast`); release builds ship the
 * original behaviour.
 *
 * Extras of the broadcast:
 *  - `type`    TYPE_SLOW_IN / TYPE_SLOW_OUT / TYPE_TWO_FU (required)
 *  - `cx`,`cy` gesture center in view px (default 800 / 1280)
 *  - `span`    initial finger separation in px (default 400)
 *  - `steps`   number of interpolated ACTION_MOVE events (default 12)
 *  - `dx`,`dy` total pan translation in px for TWO_FU (default 0 / 300)
 *  - `delayMs` ms between consecutive MotionEvents (default 30)
 *
 * Per gesture the sequence mirrors real hardware: ACTION_DOWN (pointer 0) ->
 * ACTION_POINTER_DOWN (pointers 0 + 1, ids 0/1, TOOL_TYPE_FINGER) -> N x
 * ACTION_MOVE (interpolated coordinates: separation morphs for zooms, both
 * fingers translate equally for the pan) -> ACTION_POINTER_UP (pointer 1) ->
 * ACTION_UP. Events are chained on the view's main [Handler] via
 * [Handler.postDelayed]; there is no Thread.sleep on the UI thread.
 *
 * Ordered-broadcast outcome (visible as `Broadcast completed: result=…` for
 * `adb shell am broadcast` and via `adb logcat -s DebugGestureInjector`):
 * `1/ok` dispatched, `0/noview` when no live canvas view exists (stale
 * WeakReference), `0/badtype` on an unknown `type` extra.
 */
object DebugGestureInjector {

    private const val TAG = "DebugGestureInjector"

    /** Broadcast action handled by [receiver]. */
    const val ACTION_DEBUG_GESTURE = "personal.notes.touch.DEBUG_GESTURE"

    private const val EXTRA_TYPE = "type"
    private const val EXTRA_CX = "cx"
    private const val EXTRA_CY = "cy"
    private const val EXTRA_SPAN = "span"
    private const val EXTRA_STEPS = "steps"
    private const val EXTRA_DX = "dx"
    private const val EXTRA_DY = "dy"
    private const val EXTRA_DELAY_MS = "delayMs"

    private const val TYPE_SLOW_IN = "SLOW_IN"
    private const val TYPE_SLOW_OUT = "SLOW_OUT"
    private const val TYPE_TWO_FU = "TWO_FU"

    private const val DEFAULT_CX = 800f
    private const val DEFAULT_CY = 1280f
    private const val DEFAULT_SPAN = 400f
    private const val DEFAULT_STEPS = 12
    private const val DEFAULT_DX = 0f
    private const val DEFAULT_DY = 300f
    private const val DEFAULT_DELAY_MS = 30L

    private const val MIN_SPAN = 40f
    private const val MAX_SPAN = 4000f
    private const val MIN_STEPS = 2
    private const val MAX_STEPS = 120
    private const val MIN_DELAY_MS = 5L
    private const val MAX_DELAY_MS = 500L

    /** SLOW_IN doubles the finger separation; SLOW_OUT halves it. */
    private const val ZOOM_FACTOR = 2f

    private const val POINTER_ID_0 = 0
    private const val POINTER_ID_1 = 1
    private const val POINTER_INDEX_1 = 1

    private const val RESULT_DISPATCHED = 1
    private const val RESULT_FAILED = 0
    private const val RESULT_DATA_OK = "ok"
    private const val RESULT_DATA_NO_VIEW = "noview"
    private const val RESULT_DATA_BAD_TYPE = "badtype"

    private enum class Mode { ZOOM_IN, ZOOM_OUT, PAN }

    /** Most recently attached canvas view; a stale reference means no view. */
    private var canvasRef: WeakReference<DrawingCanvasView>? = null

    /** Bumped on every broadcast so superseded postDelayed chains abort. */
    private var gestureSeq = 0

    /** Called from [DrawingCanvasView.onAttachedToWindow]. */
    fun register(view: DrawingCanvasView) {
        canvasRef = WeakReference(view)
    }

    /** Called from [DrawingCanvasView.onDetachedFromWindow]; clears only own ref. */
    fun unregister(view: DrawingCanvasView) {
        val current = canvasRef?.get()
        if (current == null || current === view) canvasRef = null
    }

    /** Handles [ACTION_DEBUG_GESTURE]; MainActivity registers this in DEBUG builds. */
    val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_DEBUG_GESTURE) return
            val view = canvasRef?.get()
            val handler = view?.handler
            if (view == null || handler == null || !view.isAttachedToWindow) {
                Log.i(TAG, "no live DrawingCanvasView attached; debug gesture skipped")
                setResult(RESULT_FAILED, RESULT_DATA_NO_VIEW, null)
                return
            }
            dispatchGesture(view, handler, intent)
        }

        private fun dispatchGesture(view: DrawingCanvasView, handler: Handler, intent: Intent) {
            val mode = resolveMode(intent.getStringExtra(EXTRA_TYPE))
            if (mode == null) {
                Log.w(TAG, "unknown 'type'; expected $TYPE_SLOW_IN|$TYPE_SLOW_OUT|$TYPE_TWO_FU")
                setResult(RESULT_FAILED, RESULT_DATA_BAD_TYPE, null)
                return
            }
            val cx = intent.getFloatExtra(EXTRA_CX, DEFAULT_CX)
            val cy = intent.getFloatExtra(EXTRA_CY, DEFAULT_CY)
            val span = intent.getFloatExtra(EXTRA_SPAN, DEFAULT_SPAN).coerceIn(MIN_SPAN, MAX_SPAN)
            val steps = intent.getIntExtra(EXTRA_STEPS, DEFAULT_STEPS).coerceIn(MIN_STEPS, MAX_STEPS)
            val dx = intent.getFloatExtra(EXTRA_DX, DEFAULT_DX)
            val dy = intent.getFloatExtra(EXTRA_DY, DEFAULT_DY)
            val delayMs = intent.getLongExtra(EXTRA_DELAY_MS, DEFAULT_DELAY_MS)
                .coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)

            val frames = buildFrames(mode, cx, cy, span, steps, dx, dy)
            gestureSeq += 1
            val seq = gestureSeq
            val startUptime = SystemClock.uptimeMillis()
            Log.i(TAG, "dispatching $mode steps=$steps span=$span dx=$dx dy=$dy delayMs=$delayMs")
            setResult(RESULT_DISPATCHED, RESULT_DATA_OK, null)

            val chain = object : Runnable {
                private var index = 0
                override fun run() {
                    if (seq != gestureSeq) return // superseded by a newer broadcast
                    if (canvasRef?.get() !== view) return // canvas closed mid-gesture
                    val currentHandler = view.handler ?: return
                    val frame = frames.getOrNull(index) ?: return
                    val event = MotionEvent.obtain(
                        startUptime,
                        startUptime + index * delayMs,
                        frame.action,
                        frame.properties.size,
                        frame.properties,
                        frame.coordinates,
                        0,
                        0,
                        1f,
                        1f,
                        0,
                        0,
                        InputDevice.SOURCE_TOUCHSCREEN,
                        0
                    )
                    try {
                        view.dispatchTouchEvent(event)
                    } finally {
                        event.recycle()
                    }
                    index += 1
                    if (index >= frames.size) {
                        Log.i(TAG, "gesture $mode delivered (${frames.size} MotionEvents)")
                    } else {
                        currentHandler.postDelayed(this, delayMs)
                    }
                }
            }
            handler.postDelayed(chain, delayMs)
        }
    }

    /** One injected MotionEvent: action + per-pointer properties and coords. */
    private class GestureFrame(
        val action: Int,
        val properties: Array<MotionEvent.PointerProperties>,
        val coordinates: Array<MotionEvent.PointerCoords>
    )

    private fun buildFrames(
        mode: Mode,
        cx: Float,
        cy: Float,
        span: Float,
        steps: Int,
        dx: Float,
        dy: Float
    ): List<GestureFrame> {
        fun fingerProperties(vararg pointerIds: Int): Array<MotionEvent.PointerProperties> =
            pointerIds.map { id ->
                MotionEvent.PointerProperties().apply {
                    this.id = id
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }.toTypedArray()

        fun fingerCoords(vararg points: Pair<Float, Float>): Array<MotionEvent.PointerCoords> =
            points.map { point ->
                MotionEvent.PointerCoords().apply {
                    x = point.first
                    y = point.second
                    pressure = 1f
                    size = 1f
                }
            }.toTypedArray()

        val halfSpan = span / 2f
        val endSpan = when (mode) {
            Mode.ZOOM_IN -> span * ZOOM_FACTOR
            Mode.ZOOM_OUT -> span / ZOOM_FACTOR
            Mode.PAN -> span
        }

        // Zoom: fingers stay symmetric vertically around (cx, cy).
        // Pan: fingers keep a fixed separation and both translate by (dx, dy) * t.
        fun positionsAt(fraction: Float): Pair<Pair<Float, Float>, Pair<Float, Float>> =
            when (mode) {
                Mode.ZOOM_IN, Mode.ZOOM_OUT -> {
                    val currentHalf = (span + (endSpan - span) * fraction) / 2f
                    (cx to (cy - currentHalf)) to (cx to (cy + currentHalf))
                }
                Mode.PAN -> {
                    val offsetX = dx * fraction
                    val offsetY = dy * fraction
                    ((cx - halfSpan + offsetX) to (cy + offsetY)) to
                        ((cx + halfSpan + offsetX) to (cy + offsetY))
                }
            }

        val startPositions = positionsAt(0f)
        val endPositions = positionsAt(1f)
        val frames = mutableListOf<GestureFrame>()

        // 1) ACTION_DOWN: pointer 0 lands alone (as real hardware would).
        frames.add(
            GestureFrame(
                MotionEvent.ACTION_DOWN,
                fingerProperties(POINTER_ID_0),
                fingerCoords(startPositions.first)
            )
        )

        // 2) ACTION_POINTER_DOWN: pointer 1 joins (pointer index 1 encoded).
        frames.add(
            GestureFrame(
                MotionEvent.ACTION_POINTER_DOWN or
                    (POINTER_INDEX_1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                fingerProperties(POINTER_ID_0, POINTER_ID_1),
                fingerCoords(startPositions.first, startPositions.second)
            )
        )

        // 3) N x ACTION_MOVE: interpolated separation (zoom) / translation (pan).
        for (step in 1..steps) {
            val (position0, position1) = positionsAt(step.toFloat() / steps.toFloat())
            frames.add(
                GestureFrame(
                    MotionEvent.ACTION_MOVE,
                    fingerProperties(POINTER_ID_0, POINTER_ID_1),
                    fingerCoords(position0, position1)
                )
            )
        }

        // 4) ACTION_POINTER_UP: pointer 1 lifts (event still carries both).
        frames.add(
            GestureFrame(
                MotionEvent.ACTION_POINTER_UP or
                    (POINTER_INDEX_1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                fingerProperties(POINTER_ID_0, POINTER_ID_1),
                fingerCoords(endPositions.first, endPositions.second)
            )
        )

        // 5) ACTION_UP: the last finger lifts; pointer 0 remains alone.
        frames.add(
            GestureFrame(
                MotionEvent.ACTION_UP,
                fingerProperties(POINTER_ID_0),
                fingerCoords(endPositions.first)
            )
        )

        return frames
    }

    private fun resolveMode(type: String?): Mode? = when (type?.trim()?.uppercase()) {
        TYPE_SLOW_IN -> Mode.ZOOM_IN
        TYPE_SLOW_OUT -> Mode.ZOOM_OUT
        TYPE_TWO_FU -> Mode.PAN
        else -> null
    }
}
