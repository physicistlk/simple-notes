# Phase 3 — Live Capture (TouchNotes 7.0.4)

**Tablet:** 230b89f8 (Xiaomi 21051182G / Pad 5, Android 16, SDK 36) — verified via `adb devices` and `getprop`.

## Screenshots
- **ADB `screencap` initially blocked** (all-black frames, 21707-byte PNGs) due to HyperOS/MIUI secure-display policy; `screenrecord` failed with `UNASSIGNED_LAYER_STACK`. No valid PNGs retained at that time; `reference/screens/` was left empty to avoid false claims.
- **Manual capture later succeeded**: user captured screenshots and videos directly on the device (saved under `reference/screens/` and `analysis/video_frames/`), including `Screenshot_20261102-184136_TouchNotes.png` … `Screenshot_20261102-184549_TouchNotes.png`, plus `touchnotes_live_record.mp4`, `touchnotes_interactive.mp4`, `touchnotes_buttons_demo.mp4`, `screen-20261102-221046.mp4`. These now provide the visual reference that was originally missing.
- Named clone-side comparison shots later added during Phase 6/7: `clone_drawer_bookmarks.png`, `clone_drawer_thumbnails.png`, `clone_lasso_selected.png`, `clone_lasso_transformed.png`, etc.
- Search boundary: ADB screenshot path blocked at device level initially; manual capture + scrcpy-style user capture resolved it. No root used.

## Data pulled
- Internal app-private storage (`/data/data/com.newskyer.draw/...`): **NO** — `run-as` refused (package not debuggable); direct `adb pull` returned "Permission denied". No root.
- External app storage: **YES** — pulled `/sdcard/Android/data/com.newskyer.draw/files/Documents/note` (114 files, ~30 MB) into `reference/files/note/`.
- Databases: **YES (external)** — `reference/files/note/index/flash.db` is a Room/SQLite DB (flashcards + labels only; no note table). Internal databases not accessible.

## Activity verification
- `dumpsys activity activities | grep newskyer` confirms `com.newskyer.draw/.PadActivity` (resumed) and `BookshelfActivity` (note list) per Phase 2 manifest. Matches `02-classes.md`.

## Storage findings (resolves Phase 2 unknowns)
- Notes are **file-based**, not SQLite: each note is `note_<id>.ncc` (~binary header `20 19 04 29 ...` + JSON) under `.../note/note/`, with a companion `note_<id>__res__/` directory (pages `.p`, `paths`, `catalog.json`, `page_usn.json`, thumbs, exported PDF).
- Index/metadata: `pathinfo/pathinfo.json` (folder/note tree, usn versioning) + `user.json` (settings) + `tabs.json`, `history.json`.
- Room/SQLite (`flash.db`) exists only for **flashcards** (`FlashCardsContext`, `FlashCardsType`, `FlashCardsTags`, `NoteHasUsingLabels`), with `room_master_table`. Search boundary: only this DB inspected; internal DBs not accessible.
- Backup format `.np` confirmed present under `.../note/backup/` (e.g. `note_...-05-14-22-59.np`), alongside `.ncc` backups.
- On-disk note directory resolved: `/sdcard/Android/data/com.newskyer.draw/files/Documents/note/` (external). Internal `/data/data/...` not inspected.

## New unknowns
1. `.ncc` binary container format beyond the leading 12-byte header + JSON payload (compression/encryption of stroke data) — not decoded; needs hex/RE.
2. Stroke serialization inside `...__res__/note_page*.p` and `.../paths` files — format unknown; candidate binary stroke blobs.
3. Whether an internal SQLite DB exists in `/data/data/...` for note index (pathinfo is JSON externally) — inaccessible without root; not determinable.
4. `FlashCardsContext`/labels schema relation to notes — documented; card content (Q/A) stored in SQLite, not .ncc.
5. Screenshot capture remains blocked; need user to enable "USB debugging (Security settings)" or provide manual screenshots if visual reference required.

## Phase 3 status
- Live capture **partially complete**: device verified, external data + Room DB pulled and inspected, activities verified. Screenshot capture blocked by device security policy (0 of 5). No data cleared, no app installed.