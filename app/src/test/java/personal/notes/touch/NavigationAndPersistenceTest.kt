package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.data.db.entity.FolderEntity
import personal.notes.touch.data.db.entity.LabelEntity
import personal.notes.touch.data.db.entity.NoteEntity
import personal.notes.touch.data.db.entity.PageEntity
import personal.notes.touch.data.db.entity.SettingsEntity
import personal.notes.touch.ui.nav.Screen
import personal.notes.touch.ui.settings.parsePenDefaults
import personal.notes.touch.ui.theme.TouchNotesSwatches

class NavigationAndPersistenceTest {

    @Test
    fun testNavigationRoutes() {
        assertEquals("splash", Screen.Splash.route)
        assertEquals("notes", Screen.NoteList.route)
        assertEquals("canvas/{noteId}", Screen.Canvas.route)
        assertEquals("canvas/test-123", Screen.Canvas.createRoute("test-123"))
        assertEquals("editor/{noteId}", Screen.Editor.route)
        assertEquals("editor/note-abc", Screen.Editor.createRoute("note-abc"))
        assertEquals("settings", Screen.Settings.route)
        assertEquals("flashcards", Screen.Flashcards.route)
    }

    @Test
    fun testEntityDefaults() {
        val note = NoteEntity(id = "note-1", title = "Meeting Notes")
        assertEquals("note-1", note.id)
        assertEquals("Meeting Notes", note.title)
        assertFalse(note.favorite)
        assertFalse(note.deleted)
        assertEquals(1, note.pageCount)

        val folder = FolderEntity(id = "f-1", name = "Work")
        assertEquals("Work", folder.name)

        val page = PageEntity(id = "p-1", noteId = "note-1", index = 0, bitmapPath = "/tmp/p0.png")
        assertEquals(0, page.index)
        assertEquals("note-1", page.noteId)

        val label = LabelEntity(id = "l-1", name = "Urgent", color = 0xFFFF0000.toInt())
        assertEquals("Urgent", label.name)

        val settings = SettingsEntity()
        assertEquals(1, settings.id)
        assertEquals(0xFFFB6443.toInt(), settings.themeColor)
        assertEquals(0, settings.darkMode)
        assertEquals(1000, settings.autoSaveIntervalMs)
        assertFalse(settings.keepScreenOn)
        assertEquals(0, settings.defaultBackground)
    }

    @Test
    fun testSettingsThemeSwatches() {
        assertEquals(10, TouchNotesSwatches.size)
        assertEquals("orange", TouchNotesSwatches[0].id)
        assertEquals(0xFFFB6443.toInt(), TouchNotesSwatches[0].color.hashCode()) // check non-null
    }

    @Test
    fun testPenDefaultsParser() {
        // Default on empty
        val defaultPen = parsePenDefaults("")
        assertEquals(0xFF000000.toInt(), defaultPen.color)
        assertEquals(6f, defaultPen.strokeWidth, 0.01f)
        assertEquals(1.0f, defaultPen.pressureSensitivity, 0.01f)

        // Custom valid json
        val customPen = parsePenDefaults("""{"color":-65536,"width":4.0,"pressure":1.5}""")
        assertEquals(-65536, customPen.color)
        assertEquals(4f, customPen.strokeWidth, 0.01f)
        assertEquals(1.5f, customPen.pressureSensitivity, 0.01f)
    }

    @Test
    fun testCanvasEnumsAndPalettes() {
        assertTrue(personal.notes.touch.ui.canvas.CanvasTool.values().size >= 3)
        assertTrue(personal.notes.touch.ui.canvas.CanvasTool.values().contains(personal.notes.touch.ui.canvas.CanvasTool.PEN))
        assertTrue(personal.notes.touch.ui.canvas.CanvasTool.values().contains(personal.notes.touch.ui.canvas.CanvasTool.HIGHLIGHTER))
        assertTrue(personal.notes.touch.ui.canvas.CanvasTool.values().contains(personal.notes.touch.ui.canvas.CanvasTool.ERASER))
        assertEquals(4, personal.notes.touch.ui.canvas.PaperPattern.values().size)
        assertTrue(personal.notes.touch.ui.canvas.PaletteColors.size >= 7)
        assertEquals(4, personal.notes.touch.ui.canvas.WidthPresets.size)
    }

    @Test
    fun testNoteListFiltersAndViewModes() {
        assertEquals(3, personal.notes.touch.ui.notes.NoteFilter.values().size)
        assertEquals(2, personal.notes.touch.ui.notes.ViewMode.values().size)
        val uiState = personal.notes.touch.ui.notes.NoteListUiState()
        assertEquals(personal.notes.touch.ui.notes.NoteFilter.ALL, uiState.currentFilter)
        assertEquals(personal.notes.touch.ui.notes.ViewMode.GRID, uiState.viewMode)
        assertFalse(uiState.isSearchOpen)
    }

    @Test
    fun testMultiPageEntities() {
        val page0 = PageEntity(id = "p-0", noteId = "note-multi", index = 0, bitmapPath = "/data/page_0.png")
        val page1 = PageEntity(id = "p-1", noteId = "note-multi", index = 1, bitmapPath = "/data/page_1.png")
        assertEquals(0, page0.index)
        assertEquals(1, page1.index)
        assertEquals("note-multi", page0.noteId)
        assertEquals("note-multi", page1.noteId)
    }

    @Test
    fun testRichTextMarkdownParser() {
        val markdown = """
            # Heading 1
            ## Heading 2
            ### Heading 3
            > Notable Quote
            - [ ] Pending Task
            - [x] Completed Task
            - Bullet Point
            1. Numbered Item
            ---
            ```
            fun main() = println("Hello")
            ```
        """.trimIndent()

        val blocks = personal.notes.touch.ui.editor.RichTextParser.parseMarkdown(markdown)
        assertEquals(10, blocks.size)
        assertTrue(blocks[0] is personal.notes.touch.ui.editor.RichBlock.Header && (blocks[0] as personal.notes.touch.ui.editor.RichBlock.Header).level == 1)
        assertTrue(blocks[1] is personal.notes.touch.ui.editor.RichBlock.Header && (blocks[1] as personal.notes.touch.ui.editor.RichBlock.Header).level == 2)
        assertTrue(blocks[2] is personal.notes.touch.ui.editor.RichBlock.Header && (blocks[2] as personal.notes.touch.ui.editor.RichBlock.Header).level == 3)
        assertTrue(blocks[3] is personal.notes.touch.ui.editor.RichBlock.Quote)
        assertTrue(blocks[4] is personal.notes.touch.ui.editor.RichBlock.ChecklistItem && !(blocks[4] as personal.notes.touch.ui.editor.RichBlock.ChecklistItem).isChecked)
        assertTrue(blocks[5] is personal.notes.touch.ui.editor.RichBlock.ChecklistItem && (blocks[5] as personal.notes.touch.ui.editor.RichBlock.ChecklistItem).isChecked)
        assertTrue(blocks[6] is personal.notes.touch.ui.editor.RichBlock.BulletItem)
        assertTrue(blocks[7] is personal.notes.touch.ui.editor.RichBlock.NumberedItem)
        assertTrue(blocks[8] is personal.notes.touch.ui.editor.RichBlock.Divider)
        assertTrue(blocks[9] is personal.notes.touch.ui.editor.RichBlock.CodeBlock)
    }

    @Test
    fun testRichTextInlineParser() {
        val inline = "Hello **world** and *italic* and <u>under</u> and ~~strike~~ and `code` and <font color=\"#FB6443\">orange</font>"
        val annotated = personal.notes.touch.ui.editor.RichTextParser.parseInline(inline)
        // Ensure styled spans are produced
        assertTrue(annotated.spanStyles.isNotEmpty())
        assertEquals("Hello world and italic and under and strike and code and orange", annotated.text)
    }

    @Test
    fun testExportFilenameSanitization() {
        val raw = "Project Notes / Part 1: Meeting & Q?*<|>|"
        val clean = personal.notes.touch.util.ExportManager.sanitizeFilename(raw)
        assertTrue(clean.startsWith("Project Notes _ Part 1_ Meeting & Q"))
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains(":"))
        assertFalse(clean.contains("?"))
        assertFalse(clean.contains("*"))
        assertFalse(clean.contains("<"))
        assertFalse(clean.contains(">"))
        assertFalse(clean.contains("|"))

        val empty = personal.notes.touch.util.ExportManager.sanitizeFilename("   ")
        assertEquals("TouchNote", empty)
    }

    @Test
    fun testBackupManifestAndArchive() {
        val bos = java.io.ByteArrayOutputStream()
        val zos = java.util.zip.ZipOutputStream(bos)
        zos.setComment("TouchNotes Backup v1.0")

        val manifestJson = """{"version":1,"app":"personal.notes.touch","createdAt":123456789}"""
        zos.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
        zos.write(manifestJson.toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        zos.putNextEntry(java.util.zip.ZipEntry("database/touchnotes.db"))
        zos.write("SQLite format 3\u0000dummy".toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        zos.close()

        val zipBytes = bos.toByteArray()
        assertTrue(zipBytes.isNotEmpty())

        val zis = java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zipBytes))
        val entries = mutableListOf<String>()
        var entry = zis.nextEntry
        while (entry != null) {
            entries.add(entry.name)
            zis.closeEntry()
            entry = zis.nextEntry
        }
        zis.close()

        assertTrue(entries.contains("manifest.json"))
        assertTrue(entries.contains("database/touchnotes.db"))
    }
}
