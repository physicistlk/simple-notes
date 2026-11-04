# Phase 2 — Data Model (TouchNotes 7.0.4)

**APK:** `/home/lk1/touchnotes-7-0-4.apk`
**Tooling:** `apktool 2.12.1` decode of `/tmp/opencode/touchnotes_decoded` + `dexdump` + resource inspection. Search boundary: static only; DEX is iJiami-packed (see "Decompilation depth").

## Decompilation depth achieved
- Outer `classes.dex` decodes to **30 classes** (`a.auu.a` packer stub + `com.ctZtamt.cym.ilajjtl.*`). `dexdump` confirms `class_defs_size: 30`, `string_ids_size: 1751`.
- Real application classes are **not present** in the shipped DEX; they are decrypted at runtime by `libijiami_1011.so` from `assets/ijiami_1011.VData` (113 KB) / in-memory. No Room/SQLite schema, DAO, or entity class could be inspected statically.
- Evidence: shell:dexdump on extracted `classes.dex`; shell:apktool decode (smali/ contains only `com/ctZtamt/...`).

## Storage pattern (observed)
- Manifest/strings state: "All notes are saved in the app internal folder"; "before uninstall app, please 'Backup All Notes', or all notes will be clean." (search boundary: decoded `res/values/strings.xml`).
- Backup/export format: **`.np`** file. Default export path string: `Download/note_backup.np` (`histories_or_export_explain`). Import via "Import Note" (`import_nz`/`note_import`).
- No SQLite database file or Room schema found in `assets/` or `res/` (search boundary: `find assets -name *.db` = none; grep resources for "sqlite"/"RoomDatabase" = none).
- Whether notes are stored as individual files, a proprietary binary container, or a SQLite DB created at runtime in app-private storage is **not determinable statically** (unknown; see 02-unknowns).

## Drawings / strokes serialization
- Rendering surface is the custom view `com.newskyer.draw.PadWhiteBoard` (in `res/layout/activity_main.xml`) plus `com.newskyer.paint.views.PanelGlView` (OpenGL) and native `libdraw_811.so` / `libpanel.so`.
- No stroke data model, serialization format, or file extension for a single note's drawing payload was found in resources. The only documented container is the `.np` backup bundle.
- Native drawing stack: `libdraw_811.so`, `libdraw_c.so`, `libpanel.so`, `libinkkit_ocr_jni.so` (from 01-structure; arm64-v8a only).

## Tables found
- **None recoverable.** No database tables, columns, or DAO signatures are visible because the application DEX is packed. State as unknown; requires live runtime inspection (Phase 3) or unpacked DEX.

## Unknowns (summary)
- Whether storage uses SQLite/Room vs. file-based serialization.
- Stroke/layer data structure and serialization inside `.np` or per-note files.
- Exact on-disk note directory and file naming (internal storage path).

See `analysis/02-classes.md` (Unknowns section) and `analysis/02-unknowns.md` if present.