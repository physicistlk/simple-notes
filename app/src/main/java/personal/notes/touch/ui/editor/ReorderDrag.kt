package personal.notes.touch.ui.editor

/**
 * Turns a vertical finger displacement into whole-block reorder steps.
 *
 * The gutter drag has no idea how tall a block is, so it works in fixed
 * distance buckets: every [stepPx] of accumulated travel (in either direction)
 * produces one `+1` / `-1`, and the remainder is kept, so a long slow drag
 * steps smoothly and a fast flick does not skip rows unpredictably. Pure and
 * Compose-free, so the policy is unit tested; the composable only forwards
 * `positionChange().y` and applies the returned step.
 */
class ReorderDrag(private val stepPx: Float) {

    init {
        require(stepPx > 0f) { "stepPx must be positive" }
    }

    private var excess = 0f

    /** Rows travelled since the last call: -1, 0 or +1 (sign = direction). */
    fun add(dy: Float): Int {
        if (dy.isNaN() || dy.isInfinite()) return 0
        excess += dy
        if (excess >= stepPx) {
            excess -= stepPx
            return 1
        }
        if (excess <= -stepPx) {
            excess += stepPx
            return -1
        }
        return 0
    }

    /** Clears pending travel (drag start/end) so a new gesture starts at zero. */
    fun reset() {
        excess = 0f
    }
}
