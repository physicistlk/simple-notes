# Simple Notes

Open-source Android notes app (clean-room clone of TouchNotes) built with **native Kotlin**, **Jetpack Compose**, **Room**, and the **Android Canvas API**. Offline-only, no ads, no tracking, no cloud sync.

## Features

- **Notes & folders** — create, rename, move, recycle bin, search, favorites, tags/labels
- **Rich text editor** — bold/italic/underline, color, size, alignment, autosave
- **Drawing canvas** — pen, highlighter, eraser, shapes, lasso selection, undo/redo, pan/zoom, pressure-sensitive width, auto-shape
- **Multi-page notes** — page navigation, continuous mode with centroid pinch zoom & multi-page overview, double-page spread, background modes (blank/grid/lines/dotted)
- **Image & text layers** — OneNote-style live text preview & inline insertion, procedural vector stickers, interactive transform bounding box (resize, rotate, delete handles), opacity, hide/show, reorder, merge to base
- **PDF import & annotation** — page backgrounds via PdfRenderer, beyond-paper margin annotation persistence, blank sheet page insertion, TOC & bookmark shifting
- **Export** — PNG/PDF (flattened) via SAF
- **Backup/restore** — atomic temp-file writes, WAL checkpoint TRUNCATE, zip of note directory via SAF
- **Flashcards** — SM-2 spaced repetition
- **Table of contents** — hierarchical chapters/pages
- **Settings** — themes, pen config, full screen, keep-screen-on, volume-key page turn, auto-center, beyond-paper
- **Drag & drop / paste** — split-screen drag from any app + clipboard paste
- **Multi-note tabs** — switch between open notes with switch-to-previous on close
- **Offline** — zero network permissions; all data in app-private storage

## Build

- **minSdk 21** / targetSdk 35 / compileSdk 35
- JDK 21, Android SDK 35, Gradle 8.10.2, AGP 8.8.0, Kotlin 1.9.24

```bash
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or download the prebuilt APK from [Releases](https://github.com/physicistlk/simple-notes/releases/tag/v0.1.0-debug).

## Project Structure

```
app/src/main/java/personal/notes/touch/
├── TouchNotesApp.kt          # Application, Room init
├── MainActivity.kt
├── data/                     # Room entities, DAOs, repositories, file store
├── ui/                       # Compose screens (notes, editor, canvas, settings, flashcards)
├── util/                     # BitmapUtils, ExportManager, BackupManager, PdfImportManager, ClipboardHelper
└── ...
```

## Documentation

- `state/SCOPE.md` — scope & decisions
- `state/FEATURES.md` — feature specs with sources
- `state/SPEC.md` — architecture, DB schema, key classes
- `state/STATE.md` — blackboard / progress decisions (#1–#63)
- `state/AUDIT.md` — live audit document covering markup text, database, scribble/ink engine, and cross-cutting findings (M1–M24, B1–B4, C1–C17, X1–X10)
- `state/archive/` — archived historical audits (`AUDIT-2026-11-03.md`, `AUDIT-2026-11-04.md`, `AUDIT-2026-11-04-v2.md`, `AUDIT-2026-11-07-history.md`) and `README-HANDOVER.md`

## License

Clean-room implementation. Original app (TouchNotes 7.0.4, `com.newskyer.draw`) used only as feature reference — no code copied. PDF import ideas attributed to [storytold/pdfcraft](https://github.com/storytold/pdfcraft) (MIT/Apache-2.0).

## Contributing

Open an issue or PR. Please keep the offline-only, no-ads, no-tracking constraints.
