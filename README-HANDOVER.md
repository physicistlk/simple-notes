# TouchNotes Clone — Handover (personal.notes.touch)

## Quick Start

**APK:** `/home/lk1/craft_apps/simple_notes/touchnotes-clone/app/build/outputs/apk/debug/app-debug.apk`
**Build date:** 2026-11-03
**Size:** ~21 MB
**Package:** personal.notes.touch
**Install:** `adb install -r app/build/outputs/apk/debug/app-debug.apk`

## Project Root
`/home/lk1/craft_apps/simple_notes/touchnotes-clone/`

## Features Built (14 + 10 Must-Have)

### Must-Have (from approved scope) — all Implemented
- Note list / browser (folders, search, multi-select, recycle)
- Text editor (rich text formatting, autosave)
- Drawing canvas (pen, highlighter, eraser, colors, shapes, undo)
- Multi-page notes + page navigation
- Note metadata (title, favorite, tags/labels, delete)
- Settings (themes, pen config, dark mode, background mode/density, full screen, volume-page-turn, pressure size, auto-shape, keep-screen-on, beyond-paper)
- Export PNG + PDF (SAF, background + strokes + image layers flattened)
- Local persistence: Room DB + JSON files + bitmaps (offline, zero network)
- Backup/restore (zip via SAF)
- Navigation (splash → list → editor/canvas) + multi-note tab bar

### Added TouchNotes-inspired features
- TOC sidebar (catalog.json structure, chapters/pages)
- PDF import as page background (PdfRenderer, non-destructive)
- Image layers (import as inset, resizable/rotate/opacity/visible/max 3/crop)
- Two-finger pan/pinch-zoom (0.25×–4×) + reset-zoom
- Multi-page spread (double-page mode, 2 pages side by side + gutter)
- Pen box (floating palette, 2 quick slots)
- Pressure-sensitive stroke width (stylus)
- Auto-shape (line/rect/circle heuristics)
- Multi-note tab switching
- Background mode (blank/grid/lines/dotted) + density slider

## Performance
- Tested on Xiaomi Pad 5 (nabu), Android 16, serial 230b89f8
- Canvas: 2048×2048 max bitmap; A4 = 1080×1528; infinite = 1080×1920
- RAM budget: <512MB (relaxed per user; 6 GB tablet)
- Storage: 256 GB; no strict cap
- Build: Kotlin 1.9.24 + AGP 8.8.0 + Gradle 8.10.2; Room 2.6.1; Compose BOM

## Verification
- Clone installed successfully (`adb install -r` Success)
- Screenshot captured: `reference/screens/clone_main_installed.png`
- Build: `./gradlew assembleDebug` passes consistently (last verified 2026-11-03)
- Original TouchNotes (com.newskyer.draw) preserved; never uninstalled or cleared

## How to Extend

- **New feature:** Edit `state/SCOPE.md` → build → `./gradlew assembleDebug` → `adb install -r ...` → screenshot → log.
- **New setting:** Add to `SettingsEntity` → `AppDatabase.kt` (bump version, destructive migration OK for personal use) → `SettingsRepository` → `SettingsViewModel` → `SettingsScreen`.
- **New canvas feature:** Modify `DrawingCanvasView` (drawing logic + touch + onDraw); wire through `CanvasScreen`/`CanvasViewModel`.
- **Database change:** Bump `AppDatabase` version; add migration or rely on destructive migration (personal use only).

## Build Commands
```bash
# Debug
./gradlew assembleDebug
# Install
adb -s 230b89f8 install -r app/build/outputs/apk/debug/app-debug.apk
# Screenshot
adb -s 230b89f8 shell screencap -p /sdcard/test.png
adb -s 230b89f8 pull /sdcard/test.png .
# Logs
adb -s 230b89f8 logcat -t 200 -d > logs/current.log
```

## Project Structure
```
app/src/main/java/personal/notes/touch/
├── TouchNotesApp.kt (Application, Room init)
├── MainActivity.kt
├── data/
│   ├── db/ (Room: entities, DAOs, AppDatabase)
│   ├── repo/ (repositories)
│   └── files/ (NoteFileStore)
├── ui/
│   ├── canvas/ (DrawingCanvasView, CanvasScreen/VM, export)
│   ├── editor/ (TextEditorScreen/VM)
│   ├── notes/ (NoteListScreen/VM)
│   ├── settings/ (SettingsScreen/VM)
│   ├── flashcards/ (FlashcardScreen/VM)
│   ├── splash/
│   └── nav/ (Navigation with tabs)
└── util/ (BitmapUtils, ExportManager, BackupManager, ZipUtils)
```

## Known Gaps / Not Implemented
- Handwriting recognition / OCR / formula recognition (deferred; native ML libs excluded)
- Cloud sync / login / billing / ads / analytics (excluded by design)
- Float note / overlay service / screen capture service (excluded)
- Voice/ASR (excluded)
- Multi-account / shared user ID (excluded; clone uses `personal.notes.touch`)
- `.ncc` binary format not reverse-engineered (clone uses Room + JSON + PNG)
- Full ProGuard optimization not verified; release build untested
- Performance under extreme load (1000+ notes, 50+ layers) untested

## Documentation
- `state/SCOPE.md` — scope and decisions
- `state/FEATURES.md` — feature specs with sources
- `state/SPEC.md` — architecture, DB schema, key classes, build config
- `analysis/01-manifest.md`, `01-structure.md`, `02-*.md`, `03-live-capture.md` — original APK analysis
- `reference/files/note/` — pulled data from device
- `reference/screens/` — original screenshots + clone comparison shots
- `logs/` — build logs, install logs, device logs
- `tools/check-upstream.sh` — version audit
- `README-HANDOVER.md` — this file

## Credits / Origin
- Original app: TouchNotes 7.0.4 (com.newskyer.draw) — reference only, clean-room implementation.
- Clone: `personal.notes.touch` — native Kotlin Android, open source, offline-only, no ads, no tracking.
- Project root and all state files: `/home/lk1/craft_apps/simple_notes/touchnotes-clone/`
