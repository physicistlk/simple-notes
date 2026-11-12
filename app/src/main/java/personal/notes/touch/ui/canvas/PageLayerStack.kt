package personal.notes.touch.ui.canvas

/**
 * The drawing layers of a page.
 *
 * A page has a **stack** of layers, the way the original TouchNotes has one:
 * the base layer (layer 1) is always there, the user can add two more, and
 * every layer can be hidden (masked), merged into the base layer or deleted.
 * The writing always goes on the **current** layer.
 *
 * This file is pure bookkeeping - no Bitmap, no Canvas - so the rules of the
 * stack (the base layer is always there, three at most, a hidden layer is
 * not drawn) can be unit tested without a device. The pixels of a layer live
 * in the ink engine; a layer here is only its identity, its name and its
 * visibility.
 *
 * The identity matters: the committed strokes of a session refer to their
 * layer by object (not by number), so renumbering the stack after a delete
 * cannot move a stroke onto another layer.
 */

/**
 * One layer of a page.
 *
 * The class has **identity** equality on purpose (it is not a data class):
 * the ink engine keeps the pixels of a layer in a map keyed by the layer
 * object, and the strokes refer to the same object.
 */
class PageLayer(
    /** The number of the layer: 1 is the base layer, 2 and 3 are on top. */
    var index: Int,
    /** The name of the layer ("Layer1", "Layer2", "Layer3"). */
    var name: String,
    /** False when the layer is masked: it is not drawn on the page. */
    var visible: Boolean = true
) {
    override fun toString(): String = "PageLayer(#$index $name visible=$visible)"
}

/**
 * The layer stack of one page.
 *
 * The base layer (layer 1) is always the first one and can never be removed
 * - it can only be hidden. The layers above it are numbered in order, so
 * deleting a layer renumbers the ones above it and their names follow.
 */
class PageLayerStack {

    private val items = mutableListOf<PageLayer>()

    /** The layer the writing goes on (null when the stack is empty). */
    var current: PageLayer? = null
        private set

    /** The layers of the page, from the base layer upwards. */
    val layers: List<PageLayer> get() = items

    /** How many layers the page has. */
    val size: Int get() = items.size

    /** True while another layer can be added (three at most). */
    val canAdd: Boolean get() = items.size < MAX_LAYERS

    /** The number of the current layer (0 when there is none). */
    val currentIndex: Int get() = current?.index ?: 0

    /** The layer with the given number (null when there is none). */
    fun layerAt(index: Int): PageLayer? = items.firstOrNull { it.index == index }

    /** The layers that are drawn on the page, in drawing order. */
    fun visibleLayers(): List<PageLayer> = items.filter { it.visible }

    /**
     * Adds a layer at the top of the stack and makes it the current one.
     *
     * Returns null when the page already has three layers.
     */
    fun add(): PageLayer? {
        if (!canAdd) return null
        val layer = insert(items.size)
        current = layer
        return layer
    }

    /**
     * Removes a layer.
     *
     * The base layer is never removed (it can only be hidden), and the layers
     * above the removed one are renumbered. Returns the removed layer, or
     * null when the layer does not exist or is the base layer.
     */
    fun remove(index: Int): PageLayer? {
        val position = positionOf(index)
        if (position <= 0) return null
        val layer = items.removeAt(position)
        if (current === layer) current = items.firstOrNull()
        renumber()
        return layer
    }

    /**
     * Puts a removed layer back at its old place (the undo of a remove).
     *
     * The layers above it move up again, so the stack is the one it was
     * before the layer was removed.
     */
    fun restore(layer: PageLayer, index: Int): Boolean {
        if (items.size >= MAX_LAYERS) return false
        val position = (index - 1).coerceIn(0, items.size)
        items.add(position, layer)
        renumber()
        return true
    }

    /** Hides a layer (it is masked, not deleted) or shows it again. */
    fun setVisible(index: Int, visible: Boolean): Boolean {
        val layer = layerAt(index) ?: return false
        layer.visible = visible
        return true
    }

    /** Makes a layer the current one: the writing goes on it. */
    fun select(index: Int): Boolean {
        val layer = layerAt(index) ?: return false
        current = layer
        return true
    }

    /**
     * Replaces the content of the stack (the loader reads it from the disk).
     *
     * A broken list leaves the stack with the base layer only: a page always
     * has one.
     */
    fun load(layers: List<PageLayer>, currentIndex: Int) {
        items.clear()
        layers.filter { it.index >= BASE_LAYER_INDEX }
            .sortedBy { it.index }
            .take(MAX_LAYERS)
            .forEach { items.add(it) }
        if (items.isEmpty()) {
            insert(0)
        } else {
            renumber()
        }
        current = layerAt(currentIndex) ?: items.first()
    }

    private fun insert(position: Int): PageLayer {
        val layer = PageLayer(position + 1, nameOf(position + 1), true)
        items.add(position, layer)
        renumber()
        return layer
    }

    /** The layers are numbered in order: 1, 2, 3 - and named after them. */
    private fun renumber() {
        items.forEachIndexed { position, layer ->
            layer.index = position + 1
            layer.name = nameOf(position + 1)
        }
    }

    private fun positionOf(index: Int): Int = items.indexOfFirst { it.index == index }

    companion object {
        /** The number of the base layer: it is always there. */
        const val BASE_LAYER_INDEX = ContinuousPageGeometry.BASE_LAYER_INDEX

        /** How many layers a page can have. */
        const val MAX_LAYERS = 3

        /** The name of a layer ("Layer1", "Layer2", "Layer3"). */
        fun nameOf(index: Int): String = "Layer$index"

        /** The stack of a page that has no layers yet: the base layer only. */
        fun base(): PageLayerStack {
            val stack = PageLayerStack()
            val layer = stack.insert(0)
            stack.current = layer
            return stack
        }
    }
}

/**
 * The metadata of the layer stack of a page.
 *
 * It is written next to the layer files
 * (`layers/page_<i>_annot_<n>.png`) as `layers/page_<i>_annot.json` and
 * holds the layers of the page (their number, their name and whether they
 * are visible) and the layer the writing goes on.
 *
 * A layer that is cut into tiles (audit C50) says so as well: its size
 * ("w", "h"), the size of a tile ("tile") and the tiles that exist
 * ("tiles"). A layer without those fields is a **legacy layer**: one
 * full-size PNG, no tile files. The old files keep reading.
 *
 * The format is read and written by hand: `org.json` is not available in
 * the unit tests, and the round trip of the metadata is worth a test.
 */
object PageLayerMeta {

    /** The version of the format (a reader of an older file ignores it). */
    const val VERSION = 1

    /**
     * The tile information of ONE layer (audit C50).
     *
     * A layer that is cut into tiles needs to say how big it is, how big a
     * tile is and which tiles exist - then the reader knows which files to
     * read and where their pixels belong.
     *
     * A layer that has no such information is a **legacy layer**: one
     * full-size PNG (`page_<i>_annot_<n>.png`), no tile files at all.
     */
    data class LayerTilesInfo(
        /** The width of the layer in pixels ("w"). */
        val width: Int,
        /** The height of the layer in pixels ("h"). */
        val height: Int,
        /** The size of a tile in pixels ("tile"). */
        val tileSize: Int,
        /** The names of the tiles that exist ("t0_0", "t1_0", ...). */
        val tiles: List<String>
    ) {
        /** The places of the tiles that exist ("t0_0" becomes (0, 0)). */
        fun positions(): List<Pair<Int, Int>> =
            tiles.mapNotNull { LayerTileGrid.parseTileName(it) }
    }

    /**
     * Writes the stack of a page as JSON.
     *
     * The tile information of the layers is optional: a layer without an
     * entry in [tiles] is written the old way, so a file that is written
     * today does not change.
     */
    fun encode(stack: PageLayerStack, tiles: Map<Int, LayerTilesInfo> = emptyMap()): String {
        val sb = StringBuilder()
        sb.append("{\"version\":").append(VERSION)
        sb.append(",\"current\":").append(stack.currentIndex)
        sb.append(",\"layers\":[")
        stack.layers.forEachIndexed { position, layer ->
            if (position > 0) sb.append(',')
            sb.append("{\"index\":").append(layer.index)
            sb.append(",\"name\":\"").append(escape(layer.name)).append('"')
            sb.append(",\"visible\":").append(if (layer.visible) "true" else "false")
            val info = tiles[layer.index]
            if (info != null) {
                sb.append(",\"w\":").append(info.width)
                sb.append(",\"h\":").append(info.height)
                sb.append(",\"tile\":").append(info.tileSize)
                sb.append(",\"tiles\":[")
                info.tiles.forEachIndexed { position2, name ->
                    if (position2 > 0) sb.append(',')
                    sb.append('"').append(escape(name)).append('"')
                }
                sb.append(']')
            }
            sb.append('}')
        }
        sb.append("]}")
        return sb.toString()
    }

    /**
     * Reads the stack of a page back.
     *
     * Returns null when the text is broken: the caller then falls back to
     * the default stack (the base layer only).
     */
    fun decode(text: String): PageLayerStack? {
        val root = MiniJson.parse(text) as? Map<*, *> ?: return null
        val entries = root["layers"] as? List<*> ?: return null
        val layers = mutableListOf<PageLayer>()
        for (entry in entries) {
            val map = entry as? Map<*, *> ?: continue
            val index = (map["index"] as? Number)?.toInt() ?: continue
            if (index < PageLayerStack.BASE_LAYER_INDEX) continue
            val name = map["name"] as? String ?: PageLayerStack.nameOf(index)
            val visible = map["visible"] as? Boolean ?: true
            layers.add(PageLayer(index, name, visible))
        }
        if (layers.isEmpty()) return null
        val current = (root["current"] as? Number)?.toInt() ?: layers.first().index
        val stack = PageLayerStack()
        stack.load(layers, current)
        return stack
    }

    /**
     * Reads the tile information of the layers of a page back.
     *
     * The answer holds one entry per layer that HAS tile information: a
     * layer that is not in it is a **legacy layer** (one full-size PNG, no
     * tile files). A broken file gives an empty map - the caller then keeps
     * the layers it has.
     */
    fun decodeTiles(text: String): Map<Int, LayerTilesInfo> {
        val root = MiniJson.parse(text) as? Map<*, *> ?: return emptyMap()
        val entries = root["layers"] as? List<*> ?: return emptyMap()
        val out = LinkedHashMap<Int, LayerTilesInfo>()
        for (entry in entries) {
            val map = entry as? Map<*, *> ?: continue
            val index = (map["index"] as? Number)?.toInt() ?: continue
            // No "tiles" field: the layer is an old, full-size one.
            val raw = map["tiles"] as? List<*> ?: continue
            val names = raw.mapNotNull { it as? String }
                .filter { LayerTileGrid.parseTileName(it) != null }
            val width = (map["w"] as? Number)?.toInt() ?: 0
            val height = (map["h"] as? Number)?.toInt() ?: 0
            val tileSize = (map["tile"] as? Number)?.toInt() ?: LayerTileGrid.DEFAULT_TILE_SIZE
            out[index] = LayerTilesInfo(width, height, tileSize, names)
        }
        return out
    }

    /**
     * True when the layer has no tile information: it is a legacy layer
     * (one full-size PNG, no tile files).
     */
    fun isLegacyLayer(tiles: Map<Int, LayerTilesInfo>, layerIndex: Int): Boolean =
        !tiles.containsKey(layerIndex)

    private fun escape(text: String): String {
        val sb = StringBuilder()
        for (c in text) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else sb.append(c)
            }
        }
        return sb.toString()
    }
}

/**
 * A small JSON reader.
 *
 * It understands the JSON the app writes with its own code (objects, arrays,
 * strings, numbers, booleans and null) and nothing else - it is not a
 * general purpose parser, it is a small one that a unit test can check.
 */
internal object MiniJson {

    /** Parses a JSON text; null when the text is broken. */
    fun parse(text: String): Any? {
        val reader = Reader(text)
        return try {
            reader.skipSpace()
            val value = reader.value()
            reader.skipSpace()
            if (reader.atEnd()) value else null
        } catch (e: Exception) {
            null
        }
    }

    private class Reader(private val s: String) {
        private var i = 0

        fun atEnd(): Boolean = i >= s.length

        fun skipSpace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            skipSpace()
            if (atEnd()) throw IllegalStateException("unexpected end")
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> { literal("true"); true }
                'f' -> { literal("false"); false }
                'n' -> { literal("null"); null }
                else -> number()
            }
        }

        private fun literal(word: String) {
            if (!s.startsWith(word, i)) throw IllegalStateException("bad literal")
            i += word.length
        }

        private fun obj(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            i++
            skipSpace()
            if (!atEnd() && s[i] == '}') {
                i++
                return map
            }
            while (true) {
                skipSpace()
                val key = str()
                skipSpace()
                if (atEnd() || s[i] != ':') throw IllegalStateException("expected ':'")
                i++
                map[key] = value()
                skipSpace()
                if (atEnd()) throw IllegalStateException("unexpected end")
                when (s[i]) {
                    ',' -> i++
                    '}' -> {
                        i++
                        return map
                    }
                    else -> throw IllegalStateException("expected ',' or '}'")
                }
            }
        }

        private fun arr(): List<Any?> {
            val list = ArrayList<Any?>()
            i++
            skipSpace()
            if (!atEnd() && s[i] == ']') {
                i++
                return list
            }
            while (true) {
                list.add(value())
                skipSpace()
                if (atEnd()) throw IllegalStateException("unexpected end")
                when (s[i]) {
                    ',' -> i++
                    ']' -> {
                        i++
                        return list
                    }
                    else -> throw IllegalStateException("expected ',' or ']'")
                }
            }
        }

        private fun str(): String {
            if (atEnd() || s[i] != '"') throw IllegalStateException("expected a string")
            i++
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw IllegalStateException("unterminated string")
                val c = s[i]
                i++
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) throw IllegalStateException("unterminated escape")
                        val e = s[i]
                        i++
                        when (e) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw IllegalStateException("bad escape")
                                val hex = s.substring(i, i + 4)
                                i += 4
                                sb.append(hex.toInt(16).toChar())
                            }
                            else -> throw IllegalStateException("bad escape")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun number(): Any {
            val start = i
            if (!atEnd() && (s[i] == '-' || s[i] == '+')) i++
            while (!atEnd() && (s[i].isDigit() || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '-' || s[i] == '+')) {
                i++
            }
            val text = s.substring(start, i)
            return text.toDoubleOrNull() ?: throw IllegalStateException("bad number")
        }
    }
}
