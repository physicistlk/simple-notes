package personal.notes.touch.ui.canvas

/**
 * The crops of one reader page: the strokes that left the page image and
 * are therefore kept as small transparent bitmaps (the margin notes).
 *
 * Pure bookkeeping - the payload (the bitmap) is opaque here - so the
 * ledger can be unit tested without a device, the same way the ink history
 * is. The controller keeps the bitmaps in the ledger and draws them at
 * their place in the page.
 */
class InkCropLedger<T> {

    /**
     * One crop: where it sits in the pixel space of the page and how big it
     * is. The id is stable: an undo takes the crop out of the ledger and a
     * redo puts the very same crop back.
     */
    data class Crop<T>(
        val id: Int,
        val x: Float,
        val y: Float,
        val width: Int,
        val height: Int,
        val payload: T
    )

    private val crops = LinkedHashMap<Int, Crop<T>>()
    private var nextId = 1

    /**
     * The pixel space the crops live in: the page's own pixels (the size of
     * the ink layer of the page). It is kept here so a crop can be placed
     * again after the session that made it is gone.
     */
    var spaceWidth: Int = 0
        private set

    var spaceHeight: Int = 0
        private set

    /** How many crops the page carries. */
    val count: Int get() = crops.size

    /** The crops, in the order they were added. */
    fun all(): List<Crop<T>> = crops.values.toList()

    /** One crop by its id. */
    fun crop(id: Int): Crop<T>? = crops[id]

    /**
     * Remembers the pixel space of the crops. A space that is already known
     * is kept: the page's ink layer does not change its size.
     */
    fun setSpace(width: Int, height: Int) {
        if (spaceWidth <= 0 || spaceHeight <= 0) {
            spaceWidth = width
            spaceHeight = height
        }
    }

    /** Adds a crop and gives it the next id. */
    fun add(x: Float, y: Float, width: Int, height: Int, payload: T): Crop<T> {
        val crop = Crop(nextId++, x, y, width, height, payload)
        crops[crop.id] = crop
        return crop
    }

    /**
     * Takes a crop out of the ledger (an undo). The crop itself is
     * returned, so it can come back with a redo.
     */
    fun remove(id: Int): Crop<T>? = crops.remove(id)

    /** Puts a crop back into the ledger (a redo). */
    fun restore(crop: Crop<T>) {
        crops[crop.id] = crop
    }

    /** Drops every crop. */
    fun clear() {
        crops.clear()
    }
}
