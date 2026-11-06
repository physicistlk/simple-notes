package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.text.FolderTree

/**
 * The notebook tree: ordering, depth, aggregated counts and the tolerances for
 * a broken hierarchy (orphans, cycles) that must never hide a folder.
 */
class FolderTreeTest {

    private fun input(id: String, name: String, parent: String? = null) =
        FolderTree.Input(id = id, name = name, parentId = parent)

    private val physics = input("p", "Physics")
    private val mechanics = input("m", "Mechanics", "p")
    private val orbits = input("o", "Orbits", "m")
    private val maths = input("x", "Maths")
    private val algebra = input("a", "Algebra", "x")

    private val all = listOf(orbits, mechanics, algebra, physics, maths)

    @Test
    fun rootsComeFirstAndChildrenFollowTheirParent() {
        val rows = FolderTree.build(all)
        assertEquals(listOf("x", "a", "p", "m", "o"), rows.map { it.id })
        assertEquals(listOf(0, 1, 0, 1, 2), rows.map { it.depth })
    }

    @Test
    fun siblingsAreSortedCaseInsensitively() {
        val rows = FolderTree.build(
            listOf(input("1", "zebra"), input("2", "Apple"), input("3", "mango"))
        )
        assertEquals(listOf("Apple", "mango", "zebra"), rows.map { it.name })
    }

    @Test
    fun countsAggregateOverDescendants() {
        val rows = FolderTree.build(
            all,
            directCounts = mapOf("o" to 2, "m" to 1, "p" to 3, "a" to 5)
        )
        val byId = rows.associateBy { it.id }
        assertEquals(2, byId.getValue("o").directCount)
        assertEquals(2, byId.getValue("o").totalCount)
        assertEquals(3, byId.getValue("m").totalCount) // 1 own + 2 orbits
        assertEquals(6, byId.getValue("p").totalCount) // 3 own + 3 mechanics
        assertEquals(5, byId.getValue("a").totalCount)
    }

    @Test
    fun anOrphanBecomesARootInsteadOfDisappearing() {
        val rows = FolderTree.build(listOf(input("lost", "Lost", "deleted-parent")))
        assertEquals(1, rows.size)
        assertEquals("lost", rows[0].id)
        assertEquals(0, rows[0].depth)
        assertEquals(null, rows[0].parentId)
    }

    @Test
    fun aSelfParentIsARoot() {
        val rows = FolderTree.build(listOf(input("me", "Loop", "me")))
        assertEquals(1, rows.size)
        assertEquals(0, rows[0].depth)
    }

    @Test
    fun aCycleTerminatesAndEveryFolderIsStillShown() {
        val rows = FolderTree.build(
            listOf(input("a1", "One", "b1"), input("b1", "Two", "a1"), input("c1", "Three"))
        )
        assertEquals(3, rows.size)
        assertEquals(3, rows.map { it.id }.toSet().size)
        assertTrue(rows.all { it.totalCount >= 0 })
    }

    @Test
    fun visibleHidesTheSubtreeOfACollapsedFolder() {
        val rows = FolderTree.build(all)
        val visible = FolderTree.visible(rows, expanded = setOf("p"))
        // Maths is a root (visible) but Algebra is collapsed away.
        assertEquals(listOf("x", "p", "m"), visible.map { it.id })
    }

    @Test
    fun visibleShowsEverythingWhenEveryParentIsExpanded() {
        val rows = FolderTree.build(all)
        assertEquals(rows.size, FolderTree.visible(rows, setOf("p", "m", "x")).size)
    }

    @Test
    fun visibleKeepsRootsEvenWithNothingExpanded() {
        val rows = FolderTree.build(all)
        assertEquals(listOf("x", "p"), FolderTree.visible(rows, emptySet()).map { it.id })
    }

    @Test
    fun subtreeIncludesSelfAndEveryDescendant() {
        assertEquals(setOf("p", "m", "o"), FolderTree.subtreeIds(all, "p").toSet())
        assertEquals(setOf("m", "o"), FolderTree.subtreeIds(all, "m").toSet())
        assertEquals(listOf("x", "a"), FolderTree.subtreeIds(all, "x"))
        assertEquals(listOf("a"), FolderTree.subtreeIds(all, "a"))
    }

    @Test
    fun subtreeOfAnUnknownIdIsJustThatId() {
        assertEquals(listOf("ghost"), FolderTree.subtreeIds(all, "ghost"))
    }

    @Test
    fun subtreeOfACycleTerminates() {
        val cycle = listOf(input("a1", "One", "b1"), input("b1", "Two", "a1"))
        assertEquals(setOf("a1", "b1"), FolderTree.subtreeIds(cycle, "a1").toSet())
    }

    @Test
    fun ancestorsAreOrderedFromTheTop() {
        assertEquals(listOf("p", "m"), FolderTree.ancestorsOf(all, "o"))
        assertTrue(FolderTree.ancestorsOf(all, "p").isEmpty())
    }

    @Test
    fun pathUsesAncestorNames() {
        assertEquals("Physics / Mechanics / Orbits", FolderTree.pathOf(all, "o"))
        assertEquals("Maths", FolderTree.pathOf(all, "x"))
        assertEquals("", FolderTree.pathOf(all, "missing"))
    }

    @Test
    fun emptyInputYieldsNoRows() {
        assertTrue(FolderTree.build(emptyList()).isEmpty())
        assertTrue(FolderTree.visible(emptyList(), emptySet()).isEmpty())
    }
}
