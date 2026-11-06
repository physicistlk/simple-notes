package personal.notes.touch.text

/**
 * Notebook tree over the flat `folders` table.
 *
 * `FolderEntity.parentId` has existed since v3 but the library only ever drew
 * a flat row of chips, so nesting was unreachable in the UI. This builder turns
 * the flat list into a depth-first, pre-order list the library can render with
 * one loop: every node carries its depth and how many notes live in it.
 *
 * Pure Kotlin (no Room, no Android) so the cycle guards and count aggregation
 * are unit tested without a device.
 */
object FolderTree {

    /** Minimal input: what the tree needs from a `folders` row. */
    data class Input(val id: String, val name: String, val parentId: String? = null)

    /**
     * One rendered row. [directCount] is the number of live notes filed exactly
     * here; [totalCount] also includes every descendant, which is the number a
     * collapsed notebook should show.
     */
    data class Node(
        val id: String,
        val name: String,
        val parentId: String?,
        val depth: Int,
        val directCount: Int,
        val totalCount: Int
    )

    private val NAME_ORDER = compareBy<Input> { it.name.lowercase() }

    /**
     * Flattens [input] into pre-order rows. Deliberate tolerances:
     * - a folder whose parent no longer exists (or points at itself) becomes a
     *   root, so deleting a parent never hides its children;
     * - a parent cycle cannot recurse forever and never drops a row — anything
     *   unreachable from the roots is promoted to the end of the list;
     * - siblings are sorted case-insensitively by name.
     */
    fun build(
        input: List<Input>,
        directCounts: Map<String, Int> = emptyMap()
    ): List<Node> {
        if (input.isEmpty()) return emptyList()

        val byId = input.associateBy { it.id }
        fun parentOf(folder: Input): String? =
            folder.parentId?.takeIf { it != folder.id && byId.containsKey(it) }

        val children = LinkedHashMap<String?, MutableList<Input>>()
        input.forEach { folder ->
            children.getOrPut(parentOf(folder)) { mutableListOf() }.add(folder)
        }
        children.values.forEach { siblings -> siblings.sortWith(NAME_ORDER) }

        // totalCount with memoisation. `path` breaks cycles (a → b → a).
        val totals = HashMap<String, Int>()
        fun total(folder: Input, path: MutableSet<String>): Int {
            totals[folder.id]?.let { return it }
            if (!path.add(folder.id)) return directCounts[folder.id] ?: 0
            var sum = directCounts[folder.id] ?: 0
            children[folder.id]?.forEach { child -> sum += total(child, path) }
            path.remove(folder.id)
            totals[folder.id] = sum
            return sum
        }
        input.forEach { total(it, HashSet()) }

        val rows = ArrayList<Node>(input.size)
        val emitted = HashSet<String>()
        fun walk(parentId: String?, depth: Int) {
            children[parentId]?.forEach { folder ->
                if (!emitted.add(folder.id)) return@forEach
                rows += Node(
                    id = folder.id,
                    name = folder.name,
                    parentId = parentOf(folder),
                    depth = depth,
                    directCount = directCounts[folder.id] ?: 0,
                    totalCount = totals[folder.id] ?: 0
                )
                walk(folder.id, depth + 1)
            }
        }
        walk(null, 0)

        // Anything stuck in a cycle is still shown, as a root, after the rest.
        input.filter { it.id !in emitted }.forEach { folder ->
            if (emitted.add(folder.id)) {
                rows += Node(
                    id = folder.id,
                    name = folder.name,
                    parentId = null,
                    depth = 0,
                    directCount = directCounts[folder.id] ?: 0,
                    totalCount = totals[folder.id] ?: 0
                )
                walk(folder.id, 1)
            }
        }
        return rows
    }

    /**
     * Filters a [build] result down to the rows whose whole ancestor chain is
     * expanded. A collapsed ancestor hides its subtree; the collapsed row
     * itself stays visible.
     */
    fun visible(rows: List<Node>, expanded: Set<String>): List<Node> {
        if (rows.isEmpty()) return rows
        val out = ArrayList<Node>(rows.size)
        val ancestorAtDepth = HashMap<Int, String>()
        for (row in rows) {
            // Pre-order guarantees ancestorAtDepth holds the current chain.
            ancestorAtDepth[row.depth] = row.id
            val visible = (0 until row.depth).all { depth ->
                ancestorAtDepth[depth] in expanded
            }
            if (visible) out += row
        }
        return out
    }

    /**
     * [id] plus every folder below it. Selecting a notebook in the library
     * filters by this set, so a parent shows the notes of its subfolders (the
     * same population its count advertises).
     */
    fun subtreeIds(input: List<Input>, id: String): List<String> {
        val children = input.groupBy { it.parentId }
        val out = ArrayList<String>()
        val seen = HashSet<String>()
        val stack = ArrayDeque<String>()
        stack.addLast(id)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (!seen.add(current)) continue
            out += current
            children[current]?.forEach { stack.addLast(it.id) }
        }
        return out
    }

    /** Ids from the top-most ancestor down to (excluding) [id]. */
    fun ancestorsOf(input: List<Input>, id: String): List<String> {
        val byId = input.associateBy { it.id }
        val chain = ArrayList<String>()
        val seen = HashSet<String>()
        var current = byId[id]?.parentId
        while (current != null && seen.add(current)) {
            val folder = byId[current] ?: break
            chain += folder.id
            current = folder.parentId
        }
        return chain.asReversed()
    }

    /** "Physics / Mechanics / Orbits" — one-line path for pickers and headers. */
    fun pathOf(input: List<Input>, id: String): String {
        val byId = input.associateBy { it.id }
        val name = byId[id]?.name ?: return ""
        val parents = ancestorsOf(input, id).mapNotNull { byId[it]?.name }
        return (parents + name).joinToString(" / ")
    }
}
