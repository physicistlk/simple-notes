# SCOPE — TouchNotes Clone (personal.notes.touch)

Rule #9 enforced: Kotlin only, minSdk 21, Room/SQLite offline, Android Canvas drawing, max canvas 2048×2048, storage not limited (256 GB tablet internal storage; no APK size cap), RAM target <512MB (6 GB tablet RAM), zero network permissions.

## Must-Have (MVP)

| # | Feature | Complexity | Perf impact |
|---|---------|------------|-------------|
| M1 | Note list / notebook browser (folders, create/rename/delete, search) | Medium | Low |
| M2 | Rich text note editor | Medium | Low |
| M3 | Drawing canvas (pen, eraser, colors, widths, undo/redo) | High | High (canvas bitmap) |
| M4 | Multi-page notes + page navigation | Medium | Medium (page bitmaps) |
| M5 | Note metadata (title, cover, favorite, tags via labels) | Low | Low |
| M6 | Settings (theme swatches, pen config, dark mode, keep-screen-on) | Medium | Low |
| M7 | Export note to image (PNG) and PDF | High | Medium (render off-screen) |
| M8 | Local persistence: Room (index/flashcards) + JSON files (note tree, user settings) | Medium | Low |
| M9 | Backup / restore all notes (zip of note dir) | Medium | Medium (I/O) |
| M10 | App navigation: splash → note list → editor/canvas | Low | Low |

## Should-Have (Post-MVP)

| # | Feature | Complexity | Perf impact | Reason deferred |
|---|---------|------------|-------------|-----------------|
| S1 | Flashcards (Room entities + review UI) | Medium | Low | Schema known (flash.db); UI/SM-2 review algorithm not specified; defer |
| S2 | Import image into note page (background) | Medium | Medium | Useful but not core to MVP drawing |
| S3 | Import PDF as note pages (render to bitmaps) | High | High | Heavy; needs PdfRenderer (API 21+) — feasible but large |
| S4 | Lasso select / move / transform strokes | High | Medium | Requires stroke hit-testing; defer |
| S5 | Layers (show/hide/reorder) | Medium | Medium | Nice-to-have; defer |
| S6 | Auto-shape (straighten lines/shapes) | Medium | Low | Defer |
| S7 | Stickers / templates library | Low | Low | Asset payload; defer |
| S8 | Tablet/landscape optimized layouts | Medium | Low | Defer polish |
| S9 | Home-screen widget (quick note) | Medium | Low | Defer |
| S10 | Undo/redo across sessions (history files) | Medium | Medium | Defer (in-memory undo for MVP) |

## Won't-Have (Excluded)

| Feature | Reason |
|---------|--------|
| Cloud sync / login (Huawei, Baidu, Dropbox) | Offline-first; zero network permissions |
| Ads / analytics SDKs (Pangle, Kwai, Umeng, Firebase, SensorsData) | Size/RAM + offline; privacy |
| Screen capture / float ball / overlay services | Requires SYSTEM_ALERT_WINDOW, media projection; out of scope |
| OCR / handwriting recognition / formula recognition | Native ML libs (Paddle/ONNX/ncnn) add size + RAM; deferred (size cap now relaxed but still keep lean) |
| Voice recording / ASR | Native audio libs; offline ASR heavy |
| Document scan (DocScanActivity) | Camera + ML; defer/exclude |
| Billing / Pro unlock | No network; free clone |
| Shared user id (com.newskyer.draw) | Must not reuse; clone uses own package |
| iJiami packer / obfuscation | Not needed for open clone |
| Reverse-engineering .ncc binary format | Use own simple storage (Room + JSON) per Rule #9 |
| Multi-ABI native libs | Arm64 only originally; clone pure Kotlin/Canvas, no .so |

## Estimated footprint (MVP)

- APK: Size not limited (256 GB tablet storage; no artificial cap).
- RAM: Target <512MB (6 GB device RAM); page cache comfortably accommodates multiple full-res pages without constraint.
- Storage: 256 GB internal storage; app-private + SAF.

## Decision recorded

- minSdk kept at **21** (per STATE.md/Rule #9). Compose/Room/Canvas all support API 21; use `ContextCompat`/`ViewCompat` where needed. No API 24+ usage without compat shim. (Original app required 24; clone lowers to 21 as project decision.)
- UI toolkit: **Jetpack Compose** (minSdk 21 supported) for screens; **Android Canvas (View-based CustomView)** for drawing surface (Compose Canvas available but View-based SurfaceView/TextureView gives better perf for large bitmaps). Decision: Compose for chrome, custom View for canvas.
- Storage: Room for structured index (notes, folders, labels, flashcards) + JSON files for note tree/settings + per-note page bitmaps/PNGs. No .ncc parsing.