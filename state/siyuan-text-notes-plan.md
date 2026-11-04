# SiYuan → TouchNotes Text Notes upgrade plan

Generated 2026-11-04 · research-only (no code touched) · scope: text notes in
`touchnotes-clone` (`package personal.notes.touch`, minSdk 21, Compose, Room, zero network permissions).

Primary sources (all fetched 2026-11-04):
- **README**: https://github.com/siyuan-note/siyuan (master, 47.1k★, SiYuan 3.8.x era)
- **Product page**: https://b3log.org/siyuan/en/
- **User guide (EN)**: https://github.com/siyuan-note/user-guide-en_US — canonical but **last pushed 2022-05-26**; `.sy` documents under
  `20200923234011-ieuun1p/…` (cited below by short doc name). V3-only features are anchored to changelogs:
  https://github.com/siyuan-note/siyuan/blob/master/app/changelogs/v3.0.x/v3.0.0/v3.0.0.md · `/v3.8.0/v3.8.0.md` · `/v3.8.5/v3.8.5.md` · `/v3.8.9/v3.8.9.md`

---

## 1. CURRENT STATE (verified in code)

| Aspect | Today |
|---|---|
| Route | `Screen.Editor = "editor/{noteId}"` (Routes.kt) → `TextEditorScreen` + `TextEditorViewModel` (TouchNotesNavHost.kt L105-123) |
| Entry points | FAB “Text Note” (NoteListScreen.kt L1012) and canvas toolbar “Text Note” button (CanvasScreen.kt L863) — **any note can carry text** |
| Storage | `notes.content` (String?, free-form markdown-ish text) in `NoteEntity`; canvas pages are PNGs via `NoteFileStore` (files/note/<id>/page_N.png) + `pages` table |
| Note columns | `notes`: id, title, folderId, favorite, deleted, coverPath, createdAt, updatedAt, content, pageCount, `pageType` = "infinite"/"a4"/"pdf"/"image_bg" — **no "text" value; text vs canvas share the table and are only distinguished implicitly** (FAB creates default `pageType="infinite"` and just opens the editor; content stays null for pure canvas notes) |
| Validation | `NoteDao.searchNotes` matches `title LIKE` only — **content is not searched/indexed** |
| Tags | `labels` + `note_label_cross_ref` + `note_label_usage` tables exist (note-level, colored) — separate from any text `#tag#` |
| Editor UI | Single `BasicTextField` (whole doc, `TextFieldValue`) + toolbar buttons; modes EDIT / SPLIT / PREVIEW (TopAppBar tabs) |
| Preview | `RichTextParser.parseMarkdown` → blocks: Heading 1-3 only, Paragraph, Quote, Bullet/Numbered/Checklist (interactive), Divider, CodeBlock; inline B/I/U/S/code/`<u>`/`<font color="#hex">`. **No tables, no images, no nested lists, no highlight** |
| Autosave | Debounce 1 s → `repository.updateNoteContent` |
| Flashcards | exists: `flash_cards` table with `nid` = noteId link (SM-2-ish easeFactor=2.5) + FlashcardScreen |
| Export | `ExportManager`/`ExportDialog`: PNG/PDF/share of page bitmaps; `BackupManager` zips whole data dir |
| DB policy | Room version 3, schema JSON exported; **from v4 onward no destructive fallback — every change needs a registered Migration** (AppDatabase.kt KDoc) |
| Manifest | **zero permissions**; FileProvider only. minSdk 21, targetSdk 35, Compose BOM 2024.04.01, no image-coil/TeX deps |

---

## 2. SIYUAN FEATURE INVENTORY (grouped, cited; 📡 = needs network/server)

| Group | Documented features | Source | Net? |
|---|---|---|---|
| Content blocks | Core concept: every block has global ID `202008250000-a1b2c3d`; doc = list of blocks; block types: paragraph, heading 1-6, math block, code, table, HTML block, unordered/ordered/task lists + list items, blockquote, super block (col/row layout), embed block, document block; name/alias/memo per block | guide “What is a Content Block”, “Content Block Type” (user-guide-en_US/…/20200924101225-k254i8g.sy) | no |
| Editor | Markdown WYSIWYG (block-style), block icon menu (Ctrl+/), Enter `/` triggers function menu/slash menu, million-word docs via dynamic loading, read-only mode, zoom focus on any block + breadcrumb | README; guide “Navigate…/Zoom focus”; changelog v3.8.9 “read-only mode” | no |
| Inline elements | strong, em, u, s, mark (highlight), sup, sub, kbd, code; emoji search via `:` + letter; 13 color/background span styles; images via paste/drag with resize | guide “Formatting elements” (…/20200924093441-ft2rhps.sy) | no |
| Links & refs | Internal block ref `((id "text"))` triggered by `((`; forward links & backlinks panel (document-level, lists + mentions), mentions→link conversion, hover preview popups, `siyuan://blocks/ID` deep links; relation graph doc-level + global | guide “Ref Content Block”, “Navigate”, “Hyperlink” (…/20210331201142-4g923es.sy), README | no |
| Virtual references | Mentions auto-detected from block name/alias/existing ref anchor text; convert to real link | guide “Virtual reference” (…/20211010212318-3wx2kqb.sy) | no |
| Embeds & queries | Doc-embed via single-line SQL `{{ SELECT * FROM blocks … }}`; blocks SQLite table + FTS; type/subtype filters (task `t`, ordered `o`, heading `h1-h6`); default 64 results | guide “Embed Content block”, “Database table”, “Type filtering” | no |
| Search | Global search with query syntax AND/OR/NOT/NEAR, case-sensitivity toggle; asset-content search; per-doc grouping + find/replace | guide “Query syntax”, CLI docs (README) | no |
| Tags & bookmarks | Tags `#Tag#`, hierarchical `#A/B/C#`, tag panel (filter in v3.8.0); bookmarks set from block-icon menu, built-in ✨💡⏳, bookmark tab Alt3 | guide “Bookmark and Tag”; changelog v3.8.0 #11039 | no |
| Notebooks & docs | Notebooks in data folder as `.sy` JSON; doc tree; doc↔heading conversion (drag); duplicate doc w/ sub-docs (v3.8.5); automatic heading numbering (v3.8.0), heading folding (v3.8.0) | README FAQ; guide “Conversion of Document and Heading”; changelogs | no |
| Daily notes | Calendar toolbar button; notebook-level storage path + optional template; embed-block tip | guide “Daily notes” | no |
| Templates | `.md` snippets in data/templates; Go text/template `.action{…}` + Sprig functions (now/date/queryBlocks); invoke from `/` menu; default template for new docs (v3.8.5) | guide “Template snippet”; changelog v3.8.5 #18390 | no |
| Databases (attribute views) | Table view (v3.0.0), calendar (v3.8.5) + list (v3.8.5) views; relation/rollup/template columns; field filtering/sorting; automations (v3.8.9); backlinks filterable by DB fields | b3log product page; changelogs | no |
| Mind maps | Markdown list rendered as mind map (guide); *editable* mind map view for list blocks (v3.8.5); themes/selection/clipboard (v3.8.9) | guide assets demo; changelogs v3.8.5/9 | no |
| Rich diagrams/media | Code blocks w/ syntax highlighting; math (KaTeX); mermaid flowchart/sequence/gantt/class/journey/gitgraph/ER; Chart.js; graphviz; PlantUML; staves; iframe/video/audio embeds; audio recording MP3 (v3.8.0); HTML blocks (docs warn on XSS) | guide “Formatting elements”; changelog v3.8.0 | no |
| Assets | Copy-paste/drag upload images into notebook `assets/`; cleanup unreferenced; PDF asset links + page-jump `?page=7` + annotations → doc refs | guide “Assets”, “PDF annotation” | no |
| Export | Import .md; data zip import/export; selection copy = **standard Markdown**; copy as HTML; full export MD / PDF / HTML / Word .docx (w/ block refs resolved) | guide “Import and Export” | no |
| History | Data history folder: auto-versions every 10 min (configurable), on sync-overwrite/delete/restore; browse latest 32; rollback w/ confirm; snapshot compare (v3.8.0); snapshots/data-repo | guide “Data history”; README FAQ | no (local) |
| Sync/E2EE/cloud | 📡 E2EE encrypted incremental multi-device sync (AES GCM), 3rd-party sync *unsupported*, LAN local-area browser access, Docker/multi-user server, publish service (v3.8.5) | README FAQ; guide “Data Sync”, “End-to-end Encryption”, “Use on browser” | **yes** |
| AI | 📡 OpenAI/Azure/Anthropic backends, AI writing Q/A, AI Agent, MCP server, semantic search via embeddings | README; changelogs v3.0.0/v3.8.0/v3.8.5 | **yes** (API keys/network) |
| Flashcards | Spaced repetition w/ FSRS (riff repo); cards from blocks, blockquote (v3.8.0) & callout (v3.8.5); stored under data/storage | README; b3log page; changelogs | no |
| Extras | Outline pane (list outline + doc outline; expand persistence), drag & drop of blocks/docs/tabs, multi-window/tab split screen, Tesseract OCR, themes/icons/plugins marketplaces, JS/CSS snippets, web clipping 📡(browser ext), lock screen, word count UI [task-requested; **not found in sources**] | b3log page; guide “Window and Tab”, “Widgets”; README | OCR/clipping partial |

Untangling the task brief: **“sudo highlights” does not exist anywhere in the sources** (only Linux `sudo update-desktop-database` in the Hyperlink doc). Interpreted as the documented highlight/`mark` span + 13 colored-style spans from “Formatting elements”. “Emojis paste link”: the `:` emoji search is documented; paste-URL-onto-selection→link behaviour is **[unverified]**. Word count likewise **[unverified]** — include because the task asks for it.

---

## 3. FIT MAPPING (offline Android, zero network, Room + files, Compose-native; canvas/strokes stay separate)

| Group | Verdict | How (Adapt) / Why (Skip/Defer) |
|---|---|---|
| Content blocks | **Adapt** | Keep markdown text in `notes.content` (single doc, not per-block JSON); add a `note_blocks` index table only in Phase 2 when block IDs are needed. No SiYuan-style block DOM — Compose LazyColumn of parsed blocks. |
| Editor (slash menu, block ops, read-only) | **Adopt** | Slash menu bottom-sheet, per-line block quick actions; read-only toggle = fully local. Skip block-icon Ctrl+/ shortcut emulation (touch: long-press block gutter in Phase 2). Million-word dynamic loading: not needed at mobile doc sizes; flag as Skip. |
| Inline elements | **Adopt** | All map to Markdown/HTML spans Compose `AnnotatedString` supports: B/I/U/S/code, `==highlight==` for `mark`, `<font color>` (already stored), sup/sub; 13 SiYuan color styles → theme-based linear-color swatch row. |
| Tags & bookmarks | **Adopt** (Phase 2) | Parse `#tag/child#` from content; eventual merge with existing `labels` tables (see Risks). Bookmarks → block-id rows + existing note-level `favorite`. |
| Links/refs/backlinks | **Adapt** (Phase 2) | `((` autocomplete over titles/headings; store `note_links` table; backlinks pane as bottom sheet (SiYuan does mobile-bottom backlinks per v3.8.9). Graph view → Defer (heavy viz); `siyuan://` → optional Android intent `app scheme/` deep link. |
| Embed blocks/SQL | **Defer/Skip** | Full SQLite blocks table + SQL embed is Phase-3-scale; Phase 2 covers saved-search-like filtered lists via simple queries. Skip live SQL in docs (security + UX). |
| Search | **Adopt** (Phase 2) | Room FTS4 full-text index on content+title; skip query-syntax grammar (offer keyword + type-filtered tabs). |
| Daily notes & templates | **Adapt** (Phase 2) | Single app: per-day text note in a fixed "Journal/Daily" folder; templates as bundled `.md` with `{date}`/`{title}` placeholders in app files. No Go templating. |
| Databases/attribute views | **Defer** (Phase 3, big) | Generic `db_view`+`db_field`+`db_row` Room design; table view first, calendar/list later; relation/rollup last. Large surface, own migration. |
| Mind maps | **Defer** (Phase 3) | Parse list-structure of a text note → mind map render/edit; could reuse the existing canvas/strokes rendering kernel for the tree canvas. Big; needs own investigation. |
| Formulas/diagrams | **Defer/Skip** | TeX/mermaid need a web runtime: bundling KaTeX assets in a WebView is offline-safe but adds a WebView pipeline + APK weight (needs user decision). Skip graphviz/plantuml/echarts/chart.js/staves. |
| Code highlighting | **Adopt-lite** | Monospace card + color for comments/keywords grossly (no dependency); no external highlighter. |
| HTML blocks | **Skip** | XSS vector in SiYuan itself; we render only a safe static subset (ruby/rt unneeded) — no raw HTML rendering. |
| Assets/images | **Adopt** (Phase 1) | Photo picker → copy file into `noteDir/assets/`, insert markdown `![name](local/…)`, preview renders via `Image(Bitmap)`. Resize: pinch later. Audio/video/recording: Defer. PDF links: text-note↔PDF-page deep-move Defer (app imports PDFs to canvas). |
| Export | **Adopt** (Phase 2) | Share `.md` via SAF/FileProvider (exists), copy selection as Markdown, copy-as-HTML `ClipData(text/plain, text/html)`. Export PDF/HTML/Word **Skip** (SiYuan uses server-side Chromium/lute). |
| History | **Adopt** (Phase 2) | On-device revision snapshots per save interval into `files/history/<noteId>/`, list+restore UI; no sync branching. |
| Flashcards | **Adopt** (Phase 3 lean) | Generate `FlashCardEntity(nid=noteId)` from `==cloze==` spans + blockquote Q/A blocks; keep existing SM-2 scheduler (FSRS port = Defer). |
| Outline pane | **Adopt** (Phase 1) | H1-H3 outline drawer from parser; tap → jump. Extensive SiYuan outline panel polish (persist expand states) as stretch. |
| Sync/E2EE/cloud/AI/publish/MCP/web-clipping | **Skip** | Requires network/permissions/accounts/servers the app must not have. Multi-device remains out of scope; reuse `BackupManager` zip for manual move. OCR (Tesseract) **Skip** (native dep, size). Themes/plugins/widgets marketplaces **Skip**; fixed in-app theme already exists. |
| Window/tab split screen | **Skip** | Already single-window; EDIT/SPLIT/PREVIEW covers the useful subset on phone. |

---

## 4. RECOMMENDED PHASES

### Phase 1 — markdown block-editor upgrade (hand to worker next; all pure Compose, zero new dependencies)

Goal: most SiYuan user-visible flavor per wall-clock. Keep architecture: `TextFieldValue` markdown source + rebuilt preview; do **not** attempt a true WYSIWYG block-DOM this phase.

| # | Item | Details / acceptance |
|---|---|---|
| P1.1 | Slash menu | Typing `/` at line start opens bottom sheet grouped like SiYuan menu: Text (H1-H3…H6, Bullets, Numbered, Task, Quote, Code block, Divider, Horizontal rule), Expand (Table N×M, Image, Tag, highlight); keyboard-dismiss + cancel pastes literal `/` |
| P1.2 | Parser v2 (`RichTextParser.kt`) | Headings 1-6; nested lists by indent; GFM tables; `==highlight==`; `<sup>/<sub>`; keep existing checked items; tolerant to legacy content (old notes render fine — regression test each old construct) |
| P1.3 | Selection formatting bar | Highlight (yellow default + 6-8 swatches → `mark`-style span), B/I/U/S (toggle-aware), inline code; colored text keeps current `<font color>` storage |
| P1.4 | Tables | Insert 3×3 table; preview renders bordered rows/cols; long text wraps |
| P1.5 | Images | Photo picker → copy to `noteDir/assets/img_<n>.<ext>` (reuse `NoteFileStore` pattern + atomic write), insert `![name](note_assets:<relativePath>)`; preview decodes; `Edit`: tap image → remove/markdown edit |
| P1.6 | Tag chips | Parse `#tag/child#`; preview shows chips (theme color); long-press chip → tag list sheet (full filtering lands in Phase 2); renders existing labels unchanged |
| P1.7 | Outline pane | Drawer listing H1-H3 of current doc; tap → scroll/jump; auto-highlight current heading |
| P1.8 | Emoji sheet | `:` (or toolbar 🙂) opens emoji picker inserting `😄` directly (SiYuan `:`-search adapted) |
| P1.9 | Schema text type | New text notes get `pageType="text"` at creation + route dispatch: `pageType=="text"` → editor; else canvas. Existing notes stay on canvas route; no Room migration (column exists) |
| P1.10 | Touch polish | List continuation on Enter (auto-append `- `/`1. `/`- [ ] `), quote continuation, undo/redo via platform IME only |

Not in Phase 1: block IDs/links/FTS (Phase 2), KaTeX/tables-in-edit-WYSIWYG, document tree changes.

### Phase 2 — links, modularity, search, journal

- **Block IDs + `((ref))` links**: Room v3→v4 migration adding `note_blocks` (id, noteId, type, text-snippet hash) + `note_links`; `((` autocomplete anchored text; ref chip long-press → preview popup (SiYuan hover-preview adapted). **Respect the no-destructive-fallback policy: write `MIGRATION_3_4`, keep exported schema JSON, update `DatabaseSchemaPolicyTest`.**
- **Backlinks pane**: doc-level bottom sheet listing incoming links + unconverted mentions (virtual references, cheap keyword match on titles/aliases); one-tap convert mention→like link.
- **Full-text search**: FTS4 virtual table over content+title (Room native, tiny dep footprint); content matches in results, incl. task-state filter (`type`-lite).
- **Daily note**: calendar-icon entry — creates/opens today's note in "Daily" pseudo-folder with `{date}` template; embed-block tip → optional "Carry yesterday's unchecked tasks" (adapted).
- **Templates**: bundled `assets/templates/*.md` (+ user-writable) with `{date}` `{title}` `{time}` placeholders; FAB "New from template" + default template setting (mirrors v3.8.5 default template).
- **Bookmarks**: block-level via `note_blocks.bookmark` + pane; note-level already covered by `favorite`.
- **Word count**: chars/words/rows in editor status row [feature not in sources — per task brief].
- **Export/copy**: share `.md`; copy as Markdown; copy as rich text (HTML ClipData); keep existing PNG/PDF canvas export untouched.
- **Local history**: revision snapshots on save interval (default 10 min, like SiYuan) into `files/history/<noteId>/rev_<ts>.md`; history/restore screen; cap + cleanup.
- **Read-only mode** toggle in editor. Stretch: `notes://block/<id>` deep links (intent filter, no network).

### Phase 3 — flagged big (each needs design + migration, one at a time)

1. **Databases / attribute views** (v3.0.0 table → v3.8.5 calendar/list): generic field/row/view tables; relation & rollup last; own Room migration + heavy UI.
2. **Mind maps** (list blocks editable as mind map, v3.8.5/9): investigate reusing canvas/stroke rendering infra; tree layout in Compose is manageable, editing UX is the cost.
3. **Formulas/math blocks**: dependency decision needed — bundled offline KaTeX in a WebView (works offline, adds WebView+~1MB assets) vs skip TeX entirely. No native TeX option exists.
4. **Flashcards tie-in**: `==cloze==`/blockquote blocks → `FlashCardEntity(nid=noteId)` via a #flashcard-ish marker (SiYuan converts blocks; we keep existing SM-2 engine; FSRS port optional).
5. Optional/deferred extras: transitive backlinks (v3.8.0), heading numbering, siyuan-like collect-viewport, document-overview map, drag-reorder of blocks (touch complexity), note→PDF-page links for existing PDF imports.

---

## 5. RISKS + OPEN ITEMS for orchestrator/user

1. **WYSIWYG vs markdown-source** (biggest architectural fork): true SiYuan flavor is a block-DOM WYSIWYG engine (Electron + lute). Proposal keeps markdown-source + rich preview (cheap, safe). If the user demands inline-rendering-as-you-edit, cost jumps ~5-10×. Decision required before Phase 1 starts if they'll accept preview-only richness.
2. **Existing-note migration**: old text notes are free-form; ensure parser is tolerant + old notes keep rendering. Introducing `pageType="text"` changes list-tap dispatch — one wrong note opens the wrong surface; test matrix: infinite/a4/pdf/image_bg/text × content≠null.
3. **Room policy**: from schema v4 there is NO destructive fallback (AppDatabase.kt); every new table (note_blocks, note_links, FTS4) needs a real `MIGRATION` + schema JSON commit + schema-policy test update. FTS4 triggers on Room are notorious — budget plan B (LIKE scan for small datasets).
4. **Testing/CI**: project ships `DatabaseSchemaPolicyTest`, `NavigationAndPersistenceTest`; worker must extend these, not bypass. No gradle/adb was run for this research plan (task is read-only); Phase 1 worker must build + run tests itself.
5. **Tag systems overlap** (two backends): text `#tag#` chips vs existing `labels` tables — pick one source of truth (open item: merge order), or scoped UI separation.
6. **Verification limits**: user-guide repo is frozen at 2022-05; V3-era behavior was anchored to 2026 changelogs/README instead. "sudo highlights", word count UI, paste-link behavior marked [unverified] above. SiYuan pricing page not checked (b3log items shown as paid members were not individually teased) — no paid-only feature is assumed free here.
