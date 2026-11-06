package personal.notes.touch.text

/**
 * One note as the graph sees it. Only what a node needs: identity, label and
 * the kind used to open the right screen on tap.
 */
data class GraphNoteInput(
    val id: String,
    val title: String,
    val pageType: String = "text"
)

/** An undirected link between two notes, with how many references it stands for. */
data class KnowledgeEdge(
    val sourceId: String,
    val targetId: String,
    val weight: Int = 1
)

data class KnowledgeNode(
    val id: String,
    val title: String,
    val pageType: String,
    val degree: Int
)

data class KnowledgeGraph(
    val nodes: List<KnowledgeNode> = emptyList(),
    val edges: List<KnowledgeEdge> = emptyList(),
    val components: Int = 0
) {
    val isEmpty: Boolean get() = nodes.isEmpty()
    val edgeCount: Int get() = edges.size
}

/** A node with a position inside the graph canvas. */
data class PositionedNode(
    val node: KnowledgeNode,
    val x: Float,
    val y: Float
)

/**
 * Builds and lays out the note graph.
 *
 * Edges come from `[[wiki links]]` and `((block refs))`; both directions of a
 * pair are one undirected edge with a weight, self-links are dropped, and an
 * edge whose endpoint is missing (deleted note) is ignored. Unlinked notes stay
 * visible as isolated nodes, because an orphan is information too.
 *
 * Layout is deterministic and does not simulate physics: each connected
 * component sits on its own circle, and the component circles are packed into
 * a grid. That makes the view stable across recompositions (no nodes jumping
 * when a note is touched) and cheap enough for hundreds of notes. Nodes are
 * clamped inside the canvas so nothing is drawn off-screen.
 */
object KnowledgeGraphBuilder {

    fun build(notes: List<GraphNoteInput>, links: List<Pair<String, String>>): KnowledgeGraph {
        if (notes.isEmpty()) return KnowledgeGraph()
        val byId = notes.associateBy { it.id }
        val merged = LinkedHashMap<Pair<String, String>, Int>()
        for ((from, to) in links) {
            if (from == to || from !in byId || to !in byId) continue
            val key = if (from < to) from to to else to to from
            merged[key] = (merged[key] ?: 0) + 1
        }
        val edges = merged.map { (key, weight) ->
            KnowledgeEdge(key.first, key.second, weight)
        }.sortedWith(compareBy({ it.sourceId }, { it.targetId }))

        val degrees = HashMap<String, Int>()
        for (edge in edges) {
            degrees[edge.sourceId] = (degrees[edge.sourceId] ?: 0) + 1
            degrees[edge.targetId] = (degrees[edge.targetId] ?: 0) + 1
        }
        val nodes = notes.map {
            KnowledgeNode(
                id = it.id,
                title = it.title.ifBlank { "Untitled Note" },
                pageType = it.pageType,
                degree = degrees[it.id] ?: 0
            )
        }

        val adjacency = HashMap<String, MutableList<String>>()
        for (node in nodes) adjacency[node.id] = mutableListOf()
        for (edge in edges) {
            adjacency[edge.sourceId]?.add(edge.targetId)
            adjacency[edge.targetId]?.add(edge.sourceId)
        }
        val seen = HashSet<String>()
        var components = 0
        for (node in nodes) {
            if (!seen.add(node.id)) continue
            components++
            val stack = ArrayDeque<String>()
            stack.addLast(node.id)
            while (stack.isNotEmpty()) {
                val id = stack.removeLast()
                adjacency[id]?.forEach { next -> if (seen.add(next)) stack.addLast(next) }
            }
        }
        return KnowledgeGraph(nodes, edges, components)
    }

    fun layout(graph: KnowledgeGraph, width: Float, height: Float): List<PositionedNode> {
        if (graph.nodes.isEmpty() || width <= 0f || height <= 0f) return emptyList()
        val byId = graph.nodes.associateBy { it.id }
        val adjacency = HashMap<String, MutableList<String>>()
        for (node in graph.nodes) adjacency[node.id] = mutableListOf()
        for (edge in graph.edges) {
            adjacency[edge.sourceId]?.add(edge.targetId)
            adjacency[edge.targetId]?.add(edge.sourceId)
        }

        val components = ArrayList<List<KnowledgeNode>>()
        val seen = HashSet<String>()
        for (node in graph.nodes.sortedBy { it.id }) {
            if (node.id in seen) continue
            val members = ArrayList<KnowledgeNode>()
            val stack = ArrayDeque<String>()
            stack.addLast(node.id)
            seen += node.id
            while (stack.isNotEmpty()) {
                val id = stack.removeLast()
                byId[id]?.let { members += it }
                adjacency[id]?.forEach { next -> if (seen.add(next)) stack.addLast(next) }
            }
            components += members.sortedWith(
                compareByDescending<KnowledgeNode> { it.degree }.thenBy { it.title.lowercase() }
            )
        }

        components.sortWith(
            compareByDescending<List<KnowledgeNode>> { it.size }
                .thenBy { it.firstOrNull()?.id.orEmpty() }
        )

        val cols = kotlin.math.ceil(kotlin.math.sqrt(components.size.toDouble())).toInt()
            .coerceAtLeast(1)
        val rows = kotlin.math.ceil(components.size.toDouble() / cols).toInt().coerceAtLeast(1)
        val cellW = width / cols
        val cellH = height / rows
        val pad = minOf(width, height) * 0.05f
        val radius = minOf(cellW, cellH) * 0.32f

        val out = ArrayList<PositionedNode>(graph.nodes.size)
        components.forEachIndexed { index, members ->
            val col = index % cols
            val row = index / cols
            val cx = (col + 0.5f) * cellW
            val cy = (row + 0.5f) * cellH
            if (members.size == 1) {
                out += PositionedNode(members[0], cx.coerceIn(pad, width - pad), cy.coerceIn(pad, height - pad))
            } else {
                members.forEachIndexed { i, node ->
                    val angle = (2.0 * Math.PI * i / members.size) - Math.PI / 2.0
                    val x = (cx + radius * kotlin.math.cos(angle).toFloat()).coerceIn(pad, width - pad)
                    val y = (cy + radius * kotlin.math.sin(angle).toFloat()).coerceIn(pad, height - pad)
                    out += PositionedNode(node, x, y)
                }
            }
        }
        return out
    }
}
