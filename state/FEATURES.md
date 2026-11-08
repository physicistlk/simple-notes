# FEATURES — Detailed Spec (Must-Have only)

Status legend: Planned (not implemented), Implemented. Source cites analysis files / reference paths (no ev_ ids available; clean-room static analysis boundary).

## M1 — Note list / notebook browser
- Source: analysis/02-ui-components.md (NoteListView, NoteBookRecycleView, MaxLimitRecyclerView, SwipeRecyclerView; note_item.xml etc.; fragment_pad_doc.xml tile/list toggle); analysis/03-live-capture.md (pathinfo/pathinfo.json tree); reference/files/note/pathinfo/pathinfo.json
- Description: Browse notes in folders; toggle list/grid; multi-select; rename; move to recycle bin; search by title; empty state; folder breadcrumbs.
- Implementation: Compose screens `NoteListScreen`, `FolderTree`; Room entities `NoteEntity`, `FolderEntity`; DAO `NoteDao`, `FolderDao`. Use `LazyVerticalGrid`/`LazyColumn`. Search via SQL LIKE on title.
- Performance Notes: Paginate list (Paging 3) if >500 notes; thumbnails loaded with Coil (local file) capped 128px; avoid loading full bitmaps in list.
- Status: Implemented

## M2 — Rich text note editor
- Source: analysis/02-ui-components.md (NoteTextLayout, NoteRichText, NoteEditText, EditRichTextLayout, change_note_name.xml); analysis/02-resources.md (fonts); user.json textInfo (size, bold, italic, color, align)
- Description: Editable text note with formatting (bold/italic/underline, color, size, alignment), multiple paragraphs; autosave.
- Implementation: Compose `TextEditorScreen` using `BasicTextField` with `AnnotatedString` + span styles; persist as HTML or markdown in Room `NoteEntity.content` (TEXT). Simpler: store plain text + JSON style runs.
- Performance Notes: Debounce autosave 1s; max note text 1MB; avoid recomposition on every keystroke via derived state.
- Templates + daily notes: the left rail offers **Today** (opens the note titled `yyyy-MM-dd`, creating it from the daily template when absent) and **Templates** (list / create / edit / delete, star = use for Today). Template bodies are markdown with variables resolved at creation (`{{date}}`, `{{time}}`, `{{datetime}}`, `{{title}}`, `{{timestamp}}`, `{{date:yyyy/MM}}`); stored in `templates` (schema 9).
- Status: Implemented

## M3 — Drawing canvas
- Source: analysis/02-ui-components.md (PadWhiteBoard full-screen transparent, PanelGlView, PageBackgroundView, ColorPanelView, PenWidth, MeetPenWidth); analysis/02-classes.md (PadWhiteBoard, PanelGlView); analysis/02-data-model.md (native draw libs — not replicated)
- Description: Freehand drawing with pen/eraser, color picker, width presets, pressure (optional), undo/redo, clear page, background (blank/grid/dotted), zoom/pan.
- Touch-to-dismiss: the page drawer (left), tools panel (right), layers panel and shapes menu close themselves on the next touch of the page (`CanvasMenus.dismiss` + `DrawingCanvasView.onCanvasTouchStart`), so changing a setting does not cost an extra close tap before writing again; the pen-case dock and active selections are left alone.
- Implementation: Custom `View` `DrawingCanvasView` using Android Canvas + `Bitmap` backing store (ARGB_8888). Touch events draw `Path` with `Paint`; commit stroke to bitmap. Undo stack of `Bitmap` snapshots, depth derived from heap and page size (3–6). Eraser uses `PorterDuff.Mode.CLEAR`.
- Performance Notes: Allocate bitmap once per page; downscale on low-memory; cap undo to 20; run heavy bitmap ops off main thread via coroutine; `setLayerType(LAYER_TYPE_HARDWARE)` only if beneficial (test).
- Status: Implemented

## M4 — Multi-page notes
- Source: analysis/03-live-capture.md (page_usn.json, note_page*.p per page, catalog.json page titles); analysis/02-ui-components.md (page buttons, addPage toolbar)
- Description: Add/remove/reorder pages; thumbnail sidebar; page navigation; per-page canvas bitmap.
- Implementation: Room `PageEntity(noteId, index, bitmapPath)`; store each page bitmap as PNG in app-private dir (`files/note/<noteId>/page_<i>.png`); load lazily. Catalog (table of contents) optional Should.
- Performance Notes: Keep only current + adjacent pages in memory (3 bitmaps max); recycle on navigation; thumbnails 200px.
- Status: Implemented

## M5 — Note metadata
- Source: analysis/03-live-capture.md (pathinfo infos: nn title, favor, del, rcl, usn, lmf); user.json (coverMark)
- Description: Title, favorite, tags/labels, created/modified timestamps, soft-delete/recycle bin.
- Implementation: Room `NoteEntity(id, title, folderId, favorite, deleted, createdAt, updatedAt, coverPath)` + `LabelEntity` + `NoteLabelCrossRef`. Recycle bin filter `deleted=true`.
- Performance Notes: Index on (folderId, deleted, updatedAt).
- Status: Implemented

## M6 — Settings
- Source: analysis/03-live-capture.md (user.json extensive settings); analysis/02-resources.md (theme swatches 10 colors, night mode)
- Description: Theme color swatch, dark mode (system/light/dark), pen defaults (color/width), keep screen on, auto-save interval, default background.
- Implementation: DataStore Preferences (or Room `SettingsEntity` single row) + Compose `SettingsScreen`. Apply theme via Material3 dynamic color override.
- Performance Notes: Persist on change (debounced); no heavy work.
- Status: Implemented

## M7 — Export
- Source: analysis/02-ui-components.md (export image, PDF grey/image PDF, audio, stickers); analysis/03-live-capture.md (exported PDF under note/.pdf)
- Description: Export current note/page to PNG image and to PDF (via PdfDocument) via SAF (createDocument). Share via Intent.
- Implementation: `ExportManager` renders canvas bitmap(s) to `PdfDocument` pages; image export copies bitmap to PNG via `MediaStore`/SAF. Run on IO dispatcher with progress.
- Text notes additionally export as a **standalone HTML page** (inline CSS, no external assets, `MarkdownExport.htmlDocument`) and as a **paginated A4 PDF rendered from the markdown** (`TextNotePdfExporter`: headings, lists, tasks, quotes, code, tables, inline bold/italic/strike/code/highlight/links, page numbers). Local image attachments are named, not embedded, because they live outside the export.
- Performance Notes: Render off main thread; downscale images to 2048 max; stream to file (avoid full in-memory PDF for large notes).
- Status: Implemented

## M8 — Local persistence
- Source: analysis/02-data-model.md (no SQLite for notes; file-based .ncc); analysis/03-live-capture.md (pathinfo.json, user.json, flash.db Room for flashcards); STATE.md decision: Room + JSON, not .ncc
- Description: Room database for notes/folders/labels/pages/flashcards; JSON files for note tree index and user settings; bitmaps as files. No .ncc parsing.
- Implementation: Room `AppDatabase` (version 1) with entities above; `NoteRepository` coordinates Room + file I/O. Migrations via Room `Migration`.
- Performance Notes: DB on IO dispatcher; WAL mode; checkpoint periodically; keep transactions short.
- Status: Implemented

## M9 — Backup / restore
- Source: analysis/02-ui-components.md (backup all notes → .np, default Download/note_backup.np, import note); analysis/03-live-capture.md (backup dir with .np/.ncc)
- Description: Zip app note directory to user-chosen location via SAF; restore by unzip with validation.
- Implementation: `BackupManager` uses `ZipOutputStream`/`ZipInputStream` over SAF URIs; include DB + files. Version tag in zip comment.
- Performance Notes: Stream; show progress; run on IO dispatcher; avoid loading whole archive in memory.
- Status: Implemented

## M10 — Navigation shell
- Source: analysis/01-manifest.md (SplashActivity → PadActivity); analysis/02-ui-components.md
- Description: Splash → note list → editor/canvas; back stack; deep link not needed.
- Implementation: Compose Navigation with `NavHost`; single Activity `MainActivity`.
- Performance Notes: Lazy load screens; keep canvas state in ViewModel.
- Status: Implemented

## S1 — Flashcards + SM-2 spaced repetition
- Source: state/SCOPE.md (S1 deferred); state/AUDIT-2026-11-04.md §4.2; STATE decision 17 (G7)
- Description: Deck browser (3-column tablet grid) + tap-to-reveal study carousel with Again/Hard/Good/Easy grading; offline SM-2.
- Implementation: Room entities `FlashCardEntity`, `FlashCardTypeEntity`, `FlashCardTagEntity` + `FlashCardDao` + `FlashCardRepository.recordReview` (SM-2) + `FlashcardScreen` (545 LOC) + `FlashcardViewModel`.
- Block bridge (schema 10): the block menu's **Add to deck** turns a block into a card. `text/BlockCards` splits `question :: answer` (first unescaped, unquoted separator; inline markdown stripped), the dialog can edit both sides and the deck (defaults to the note title), and every card records `sourceNoteId`/`sourceBlockId`. Re-adding an edited block refreshes the same card instead of duplicating it, keeping its SM-2 history.
- Performance Notes: All local (Room Flow); no network.
- Status: Implemented

## S2 — Import image into note page (background / image layers)
- Source: state/SCOPE.md (S2 deferred); state/AUDIT-2026-11-04.md §4.2
- Description: Import image(s) as transformable layers over the page background; up to 3 layers per page.
- Implementation: `DrawingCanvasView.ImageLayer` (list up to 3) + `addImageLayer/loadImageLayers/saveImageLayers` via `NoteFileStore.getLayersDir/getLayerInfoFile` + CanvasScreen Layers panel (opacity/crop/delete) + `ExportManager` composites layers.
- Performance Notes: Layer bitmaps cached per page; export renders off main thread.
- Status: Implemented

## S3 — Import PDF as note pages
- Source: state/SCOPE.md (S3 deferred); state/AUDIT-2026-11-04.md §4.2
- Description: Pick a PDF via SAF, render each page to a bitmap, create a note with one page per PDF page.
- Implementation: `PdfImportManager` (123 LOC): `calculateOutputSize` (aspect-ratio fit), `getPageCount`, `renderPage` (PdfRenderer, never-crash guards, cap 2048/axis); `NoteListScreen` + `CanvasScreen` import launchers with per-page `writePageFileAtomically` saves.
- Performance Notes: Renders at computed output size (no post-scale), caps to 2048 per axis to bound memory.
- Status: Implemented

## S4 — Lasso select / move / transform
- Source: state/SCOPE.md (S4 deferred — specified stroke hit-testing; implemented as raster variant); STATE decision 19 (G6); state/AUDIT-2026-11-04.md §4.2
- Description: Freehand closed-polygon selection over the canvas with move/scale/cut; delivered as pixel extraction + cut from backing bitmap (not stroke hit-testing).
- Implementation: `ic_tool_lasso.xml` icon on both docks; translucent orange fill + dashed border; interactive corner/resize handles, drag-to-move, scale handle; floating ✓ Done / ✕ Cut pills.
- Performance Notes: Raster cut/paste; no vector stroke model (see AUDIT CH5 carry-over).
- Status: Implemented

## S5 — Layers (show/hide/reorder)
- Source: state/SCOPE.md (S5 deferred); state/AUDIT-2026-11-04.md §4.2
- Description: Image layers per page can be shown/hidden, reordered up/down, opacity-adjusted, cropped, deleted; export composites them.
- Implementation: Image-layer subsystem above (2 layers visible state in CanvasScreen) + `moveImageLayer` reorder + `setLayerVisible` + `setLayerOpacity` + `deleteImageLayer` + `cropSelectedImageLayer` + persistence via layers dir/info file.
- Performance Notes: At most 3 layers; visibility toggles are in-memory.
- Status: Implemented

## S6 — Auto-shape (straighten lines/shapes)
- Source: state/SCOPE.md (S6 deferred); state/AUDIT-2026-11-04.md §4.2
- Description: Optional snapping of freehand strokes to straight lines/shapes when enabled in Settings.
- Implementation: Settings toggle `autoShapeEnabled` → `SettingsRepository.updateAutoShapeEnabled` → `CanvasViewModel.autoShapeEnabled` → `DrawingCanvasView.autoShape(sampledPoints)`; pressure modulation coexists.
- Performance Notes: Per-stroke sampled points only.
- Status: Implemented

## S7 — Stickers / templates library
- Source: state/SCOPE.md (S7 deferred); state/AUDIT-2026-11-04.md §4.2
- Description: Asset-based sticker/template library to insert into notes.
- Implementation: Stub tools-panel item `"Sticker"` (CanvasScreen.kt:2100) with empty handler; no assets.
- Performance Notes: —
- Status: Planned (stub only)

## S8 — Tablet / landscape optimized layouts
- Source: state/SCOPE.md (S8 deferred); state/AUDIT-2026-11-04.md §4.2
- Description: Tablet-first layouts — left NavRail, 3-column note grid, double-page spread, full-screen reading mode.
- Implementation: NoteListScreen rail + grid; CanvasScreen double-page + full-screen modes; A4 `aspectRatio(0.707f)` cards.
- Performance Notes: —
- Status: Implemented

## S9 — Home-screen widget (quick note)
- Source: state/SCOPE.md (S9 deferred); state/AUDIT-2026-11-04.md §4.2
- Description: Home-screen widget for quick note capture.
- Implementation: None (no AppWidgetProvider, no widget XML).
- Performance Notes: —
- Status: Planned

## S10 — Undo/redo across sessions (history files)
- Source: state/SCOPE.md (S10 deferred); state/AUDIT-2026-11-04.md §4.2
- Description: Persist undo/redo history to files so it survives process death / app restart.
- Implementation: In-memory only (`ArrayDeque<Bitmap>` cap 20 in DrawingCanvasView); no history files.
- Performance Notes: See AUDIT CH3 (snapshot memory).
- Status: Planned

## Newly added (post-Phase-7) — all Implemented unless noted

- **PDF import hardening** — PdfImportManager never-crash guards (index bounds, div-by-zero, `coerceAtLeast(1)`, `return null`, try/catch + close in `finally`).
- **Aspect-ratio fit** — `PdfImportManager.calculateOutputSize = min(tW/pW, tH/pH)`, renders directly at computed size.
- **2048 cap** — `PdfImportManager.MAX_DIMENSION = 2048`; `BitmapUtils.createSafeBitmap(max 2048)`; `ExportManager.MAX_BITMAP_DIMENSION 2048`; `DrawingCanvasView` page clamp 2048.
- **Never-crash guards** — import/render return null/0 on any error; call sites Toast on failure.
- **Per-page atomic saves** — `NoteFileStore.writePageFileAtomically` (temp file + rename) used for backing-bitmap pages and PDF pages.
- ~~**Overflow layer for PDF**~~ — removed 2026-11-07. `DrawingCanvasView.overflowBitmap` was allocated (up to 4096×4096 ≈ 67 MB) but never drawn into; see `AUDIT-2026-11-07.md` item 7.
- **Multi-note tabs** — NavHost `openNotes` tab strip (`TouchNotesNavHost.kt:26,77-91`) with close-tab.
- **Notebook tree (hierarchy UI)** — `folders.parentId` existed since v3 but was only drawn as flat chips. The library rail and drawer now render a collapsible tree (`ui/notes/NotebookTree.kt`) built by the pure `text/FolderTree` (pre-order, depth, direct + descendant note counts, orphan/cycle tolerant). Selecting a notebook filters its **whole subtree**; a new "+" creates a root notebook, the row menu renames / creates a subfolder / deletes (deleting re-parents its notes and children instead of dropping them).
- **Split workspace (two documents)** — "Open beside" in the editor top bar (or the pane menu) opens a searchable picker of text notes and a two-pane route (`Screen.Split`, `ui/editor/SplitWorkspaceScreen`). Each pane is the real editor with `embedded = true` and a compact header (title, one-tap mode cycle, ⋮ actions). Swap, close, close-other (maximize) and replace-on-link are available; the two panes are always different notes. Panes are destination state, not routes, so switching notes never destroys an editor ViewModel mid-autosave.
- **WYSIWYG inline formatting in the editor** — the block field styles `**bold**`, `*italic*`, `***both***`, `` `code` ``, `~~strike~~`, `==highlight==` and `<u>underline</u>` as you read, matching the preview. Markers stay visible but dimmed in the block you are editing (so the caret always edits the real markdown) and disappear in every other block and in read-only mode; a real `OffsetMapping` keeps tap-to-caret correct on the rendered text. CODE blocks opt out because their markers are literal. Pure `text/InlineFormat` + `text/InlineRender`, Compose adapter `ui/editor/InlineEditorStyle`.
- **Double-page** — Settings toggle + `DrawingCanvasView.doublePage` (targetWidth ×2+40, coerced 4096) + CanvasViewModel `doublePageMode`.
- **Pen box** — Settings `showPenBox` → floating pen box in CanvasScreen (`penBoxEnabled`).
- **Pressure pen** — Bézier power-curve calibration (`getCalibratedPressure`), `pressureSizeEnabled`, Settings slider "Soft/Standard/Firm".
- **Auto-shape** — see S6 above.
- **Full-screen** — Settings `fullScreen` → MainActivity hides systemBars (transient swipe behavior).
- **Keep-screen-on** — Settings `keepScreenOn` → MainActivity FLAG_KEEP_SCREEN_ON.
- **Auto-center** — Settings `autoCenter` (default true) → `centerTransform` on bitmap init.
- **Beyond-paper** — `beyondPaper = true` default in DrawingCanvasView; frees pan clamp so the user can draw past the sheet.
- **Volume-key** — Settings `volumeForPage` → CanvasScreen `KEYCODE_VOLUME_UP/DOWN` handler (saves + page turn).
- **Background density** — Settings `backgroundDensity` (default 40) → grid/dotted step `coerceIn(20f,80f)`.
- **SM-2 flashcards** — see S1 above.
- **Block references + backlinks (schema 11)** — `((blockId))` / `((blockId|alias))` links to one block (the first 8 chars of its stable UUID). Typing `((` opens an autocomplete over every persisted block (block text + note title); preview renders a teal chip showing the alias or the target's live text, and taps open the target note. `block_refs` indexes one row per (source note, source block, target key); the References sheet gains **Block backlinks** and **Blocks linked from here**, with missing targets shown, not hidden. Fenced code and CODE blocks are not indexed.
- **TOC sidebar** — Drawer OUTLINE tab (hierarchical `TocItem` chapters/sections, `P.N` badges, jump-to-page, add-chapter dialog) persisted to `catalog.json` + `bookmarks.json` via `CanvasViewModel.addTocEntry/deleteTocEntry`.
- **Image layers hide/show/reorder** — see S5 above (`setLayerVisible`, `moveImageLayer`, `setLayerOpacity`, layers panel).

- **Page turning modes (schema 12)** — `settings.pageTurningMode` (`0 = Continuous`, the default; `1 = Single`) and `settings.pageTurningDirection` (`0 = Vertical`, `1 = Horizontal`), edited in Settings and in the canvas right-side panel; continuous mode is disabled for infinite-canvas notes. Single mode keeps the live 18 MPx (heap-capped) pdfium re-render, so text stays crisp at any zoom.
- **Continuous reading** — a read-only overview overlay: a `LazyColumn`/`LazyRow` of pages, `ContinuousPageView` measures its laid-out width and renders each vector page at width × quantised zoom (1 / 1.5 / 2 / 3, area-capped at 12 MPx / 4096 px) via `PdfiumDocument.renderPageInto`, compositing the PDF and the page's stroke layer in one allocation. Pinch zooms with clamped pan; scrolling updates the current page; tapping a page enters single mode on that page. Raster (pre-F-10) pages are decoded once and scaled.
- **Save targeting** — saves are filed under `CanvasViewModel.canvasPageIndex` ("the page the canvas holds"), not the scrolled/current page, and a debounced save captures its page index at schedule time; leaving a note in continuous mode therefore cannot overwrite another page's strokes.
- **Annotated library cards for PDF notes** — a vector-PDF page keeps its clean render in `page_<i>.png` and the ink in `strokes_<i>.png`; the note card now composites the two (`BitmapUtils.loadAlphaThumbnail`, ARGB so transparent ink is not flattened to black) so the library shows the annotated page like the original, while export and raster pages are unchanged.
- **Keep Screen On is lifecycle-scoped** — the window flag is held only while the setting is on, the activity is resumed and the display is on (`util/ScreenAwakeController`), and released on pause and on `ACTION_SCREEN_OFF`; the canvas also drops a pending PDF re-render while its window is hidden, so nothing runs with the screen locked.

