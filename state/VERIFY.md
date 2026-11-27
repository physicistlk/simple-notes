# VERIFY — manual test checklist for the tablet

Read this on the tablet and tick each box. Report back the numbers that pass
(e.g. "A1, A2, A5, B all ok") and I'll tick exactly those rows in the
audit. A ticked row becomes **FIXED**; an unticked row stays **IMPLEMENTED**.

Build to install: `app/build/outputs/apk/debug/app-debug.apk`
(703 tests / 0 failures / 2 skipped; lint 0 errors.)

---

## A. Scribble / ink (the core)

### A1. Persistence (the critical one — test first)
- [ ] A4 note: draw 5 strokes → close the tab → reopen → all 5 present
- [ ] Infinite canvas: draw 5 strokes (zoomed out, panned) → close tab → reopen → all present
- [ ] PDF: draw on page 1 → scroll to page 2 → draw on page 2 → scroll back to page 1 → page 1's strokes still there → close tab → reopen → both pages present

### A2. Zoom & pan
- [ ] Pinch zoom in above 100 % on PDF: the PDF page, the white bed, and pre-drawn strokes all grow together
- [ ] Pinch zoom out and back to 100 %: strokes scale down and back up
- [ ] Reset zoom button (in the zoom pill) → 100 %, page stays
- [ ] Centre button → origin, zoom stays
- [ ] A4 note: two fingers drag up/down and left/right — page pans on both axes
- [ ] Infinite canvas: stroke starting outside the visible area (after pan) works
- [ ] Infinite canvas: no bounding frame / card outline around the stroke

### A3. Glass
- [ ] Draw a stroke under the dock → stroke bends/refracts at the glass rim (not just transparency)
- [ ] Scroll the page while the dock is over it → glass updates

### A4. A4 page
- [ ] Zoom out → draw outside the A4 page (on the grey desk) → stroke is clamped to the page
- [ ] Strokes don't appear outside the A4 bounds after reopen

### A5. Thickness
- [ ] Long-press middle width chip → slider → 21 px → draw → tap 3rd chip → tap 2nd chip → thickness stays 21 px
- [ ] Reset thickness button works

### A6. Eraser
- [ ] Eraser clears ink on the reader (not PDF text)
- [ ] Eraser works on strokes drawn after a pinch zoom

### A7. Layers
- [ ] Add layer → base layer always there, eye masks, "…" menu has Merge/Delete (base has no Delete)
- [ ] Layers survive a restart

### A8. Stylus mode
- [ ] PDF note: open right panel → Tools tab → enable stylus mode → finger scrolls, stylus writes

---

## B. Naming & library

- [ ] New infinite canvas note → named "canvas 1", "canvas 2"…
- [ ] New A4 note → named "note 1", "note 2"…
- [ ] Import PDF → note named after the PDF file
- [ ] Library sort order persists across restart
- [ ] Recycle bin: empty it → no orphan page rows or layer files left

---

## C. PDF mode

- [ ] Open a multi-page PDF → pages render centred with grey margin on both sides
- [ ] Stroke in the margin survives a restart
- [ ] PDF text is not overwritten by ink
- [ ] Pinch zoom in works (see A2)
- [ ] PDF search sheet: find a word → navigates to the page
- [ ] PDF outline / table of contents imports correctly
- [ ] Page turning: continuous scroll vs single-page swipe (decoupled from A4)
- [ ] Large PDF (50 MB+) opens without OOM

---

## D. Infinite canvas

- [ ] Draw a big stroke after pinch-zoom-out / pan → no lag
- [ ] Pan 3–4 times, draw a large stroke → no lag
- [ ] Export an infinite canvas note to PDF/image → no distortion, no clipping
- [ ] Page scrubber / HUD (reset, clear) works
- [ ] Arrow selection tool: select, move, resize an object
- [ ] Nested subpages (if applicable)

---

## E. Text / markup notes

- [ ] Block editor: headings, lists, tables, references render correctly
- [ ] Markdown export produces correct output
- [ ] Block operations: add, delete, reorder, fold
- [ ] Wiki links / references resolve
- [ ] Search over markdown works

---

## F. Settings, backup & privacy

- [ ] Settings: colour swatches have content descriptions (TalkBack)
- [ ] Backup export → restore on the same device → notes + backgrounds intact
- [ ] Backup restore on a different device/profile → backgrounds load (paths not absolute)
- [ ] Zero permissions in merged manifest (verify with aapt2)
- [ ] No network access (offline)
- [ ] Backup disabled (`allowBackup=false`)

---

## G. Export

- [ ] Export A4 note to PDF → correct aspect ratio
- [ ] Export infinite canvas → no vertical stretch
- [ ] Export cache pruned (last 20 files)
- [ ] Image import samples (no 12 MP full decode)

---

## H. General UI

- [ ] Top bar: Tools & Settings button visible (no overflow)
- [ ] Tab bar survives rotation
- [ ] Closing the active tab switches to the previous tab (not home)
- [ ] Canvas tap closes open menus (Tools panel, page drawer)
- [ ] Glass elements don't crash on recycled bitmaps
- [ ] App name is "Simple Notes" everywhere (About, manual, splash, exports)

---

## I. Build & code quality (already verified, no device needed)

- [x] `./gradlew testDebugUnitTest` → 0 failures
- [x] `./gradlew lintDebug` → 0 errors
- [x] 0 `!!` in Kotlin sources
- [x] 0 `uses-permission`
- [x] Room schema 14 + migration

---

### Highest priority to test on the tablet

1. **A1** (persistence) — the critical data-loss bug
2. **A2** (zoom/pan) — T6, T13, T32
3. **A3** (glass refraction) — T1
4. **A4** (A4 clamping) — T31
5. **D** (infinite canvas lag + export) — T3, T13
6. **C** (PDF search, outline, multi-page) — new features