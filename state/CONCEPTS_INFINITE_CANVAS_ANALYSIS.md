# Concepts (2026.04): Infinite Canvas Analysis and simple_notes Gap Plan

| | |
|---|---|
| **Analysed** | `Concepts_v2026.04_MOD.apk` · `com.tophatch.concepts` · v2026.04 · minSdk 26 / targetSdk 36 · arm64-v8a |
| **Compared against** | `simple_notes/touchnotes-clone` (package `personal.notes.touch`) |
| **Method** | Static analysis only (craft-anything / craft-anything-cli, deep mode). Nothing was run, decompiled or copied. |
| **Evidence** | `simple_notes/concepts-analysis.cany/` (12 findings, 88 evidence records, `REPORT.md`) |
| **Status** | Analysis complete. Roadmap awaiting decisions (section 7). |

---

## 1. TL;DR

- **What Concepts is:** a C++ engine (`libCore.so`, 84 MB) with a thin Kotlin UI. We cannot port the engine, but we can copy the *information architecture*: object tree, layer model, tool model, precision toggles, status bar.
- **What we have:** a page-based note app with a raster ink engine. "Infinite" is only a page type that hides the paper border. Text, stickers and shapes are baked into bitmaps, so there are no real objects.
- **The main gap is structural, not cosmetic:** no world-space canvas and no object model. Nearly every missing Concepts feature (selection, grouping, snapping, measurement, artboards, SVG export) depends on those two things.
- **Recommended path:** build the world-space transform first, then the object model, then layers v2, then precision (grid and snap), then shape recognition and the status bar.
- **Cost of the shortlist you asked for** (#1, #3, #4, #6, #8): about 11–16 working days. Including the world-space foundation (#9) it is about **16–24 days (3–5 weeks)**. The earlier "about two weeks" figure left #9 out, even though the document itself says #9 should come first.
- **Three decisions are needed** before work starts (section 7): page model vs second mode, objects vs raster, and the Digital Ink dependency.

---

## 2. Scope, method and caveats

**Method.** Findings come from the manifest, DEX class/field/method structure, string resources and native symbol tables. Nothing was decompiled or copied. Everything below is a *feature list* to re-implement in our own words and architecture.

**Confidence.** Class and symbol names are strong evidence that a capability exists. They say little about quality or exact behaviour. Treat "Concepts has X" as "the binary contains X", not "X works well".

**Caveats worth acting on:**

1. **Source APK.** The analysed file is a `_MOD` build. Modified or repackaged APKs are often pirated and can differ from the official release. For a clean-room reference, re-run the analysis against the official Play Store build and confirm the findings match.
2. **Counts need re-checking.** Section 3.1 originally said "27 tool types" but lists 26. Verify against the ledger before quoting either number.
3. **Behavioural reference only.** Concepts is a reference for behaviour, not a source of code. Keep that discipline (section 8).

---

## 3. How Concepts is built

```
MainActivity ── CanvasFragment ── GalleryFragment
                    │
                    ├── core.ui.CanvasElements  (UI model, 13 panels)
                    │     toolBar · layers · statusBar · selectionMenu · contextMenu
                    │     precision(grid/guide/snap/measure/recognition) · quickClear
                    │     settings(grid/artboard/measurements/touchInput/stylus)
                    │     export · textEdit · toolPopUp · pdfPicker · library
                    │
                    └── core.CanvasController  (13 sub-controllers)
                          document · renderer · input · interface · command
                          background · selection · resources · picker · export
                          workspace · undo · test
                                    │
                    ┌───────────────┴────────────────┐
                    ▼                                ▼
        libCore.so  (84 MB, C++)            libimpress_api_jni.so  (vector shapes)
        th::drawing::FluidEngine::Drawing   libdigitalink.so       (ML)
        th::input   (routers + gestures)    libdigitalinksegmentation.so
        th::canvas  (models + editors)      libpdfium.so           (PDF)
        th::document (files, archives)      libcblite.so           (sync DB)
        th::ml      (Digital Ink pipeline)  libandroidx.xr.runtime.openxr.so
        th::gpu / th::tile (renderer)
```

| Area | Finding |
|---|---|
| **Native core** | All document and ink logic is C++. Kotlin (`core.NativeCanvas*`) is a thin bridge. We re-design rather than port. |
| **Item tree** | `Drawing::Tree`: `RootItem → LayerItem / GroupLayerItem → LeafItem`. Leaf kinds: `StrokeItem`, `TextItem`, `ImageItem`, `PDFPageItem`, `MeasurementItem`. Layer kinds also include `GridLayerItem`, `MeasurementLayerItem`. |
| **Per-item state** | `LeafItem` exposes link id, key points, taper, uniform pressure/azimuth, snap-to-stroke-points, accurate bounds, opacity, locked, highlighted, focus, closed. |
| **Drawing domains** | `Document, Tree, Metadata, Workspace, Resources, Tool, ToolDatabase, Grid, Selection, Artboard, Snap, Guide, Picker, Slice, Nudge, Background, Color, Detached` |
| **Input** | One `RouterTool` per mode (Draw/Pan/Zoom/Rotate/Nudge/Slice/Text/Picker/Modify), a `Gesture` library, and `shape_recognition` backed by Digital Ink ML (`th::ml::LiveRecognition`). |
| **Persistence** | `th::document::Archive` container, separate `metadata`, `resourcePack` and `workspacePack` files, autosave priority, crash-recovery candidates. |
| **Brushes** | `BrushKind/BrushType`, `CustomBrush{Database,Archive,Texture,Output}`, ~1,000 shipped brush textures in `assets/brushpacks/`. |

**Implication.** Concepts' *feel* (smooth strokes, snapping, shape recognition) comes from the native engine and ML. We will not match that. We can cheaply match the *structure*.

---

## 4. Concepts feature inventory

### 4.1 Tools
26 tool types: Airbrush, BallpointPen, CustomBrush, DottedBrush, DynamicPen, Eraser, Fill, FountainPen, HardPencil, Image, InverseFountainPen, LegacyPencil, Marker, Nudge, Pan, Redo, Rotate, Selection, Slice, SoftEraser, SoftPencil, Text, Undo, Watercolor, Wire, Zoom.

- `ToolIdentifier` = (type, brush UUID), so custom brushes are first-class.
- `ToolPropertySet` = colour, opacity, smoothness, thickness. `ToolSlotState` keeps these per slot.
- The tool bar has 8 slots. Users can add or replace tools, and each slot keeps its own properties.

### 4.2 Interaction
- 5 modes: `Idle · Drawing · Picking · TransformingCanvas · TransformingSelection`.
- 43 canvas commands and 74 keyboard commands (ZoomToFit, ZoomToSelection, ResetTransform, ToggleSnap, ToggleGrid, ToggleMeasurement, ToggleLayeringMode, tool slots 1–8, and more).
- **Bindable multi-tap gestures** (2/3/4 fingers): Undo, Redo, SelectAll, AdjustLastStroke, ConfigureActiveTool, ToggleCanvasZoom, ToggleCanvasRotation, ToggleInterface, ToggleLayers, ToggleObjects, ToggleColorWheel, TogglePrecisionGuide, ToggleScreenRecording, DoNothing.
- **Shape guides** (5 kinds): Angle, Arc, Ellipse, Line, Rectangle. They are drawn, measured, then converted into a measurement.

### 4.3 Precision subsystem (5 independent toggles)

| Toggle | Behaviour | Options |
|---|---|---|
| **Grid** | 36 presets: dot, isometric, lined, graph, triangle, 1/2/3-point perspective | colour, spacing, custom dimension |
| **Guide** | Shape guide | per-kind toggle |
| **Snap** | While drawing / while editing | drawing: ActiveLayerOnly, AlignToGrid, AllowTraceback, AllowTurns, Autocomplete, SnapToGrid, Wobble. Editing: ActiveLayerOnly, SnapToGrid, SnapToKeyPoints |
| **Measure** | Length / area / angle objects with live labels | units, precision, display format |
| **Recognition** | ML freehand-to-shape conversion | uses `libdigitalink.so` |

### 4.4 Layers
- Layer types: Grid, Group, Measurement. Layering mode: Automatic or Manual.
- Menu: Configure, Delete, Duplicate, Focus, Unfocus, Lock, Unlock, **MergeDown**, Select.
- Per layer: rename, reorder, visibility, opacity scrub, thumbnail, "move selection to layer".
- Appearance states: Faded / Focused / Neutral (focus dims the other layers).

### 4.5 Objects and selection
Object types: Stroke, Text (editable), Image, PDFPage, Measurement, plus Group, Layer, GridLayer and MeasurementLayer containers.

Selection menu: Copy, Cut, Delete, Duplicate, Group, Ungroup, Lock, Unlock, Enable/Disable transparency, Flip H/V, EditText, ToggleAttribution, ReopenPDFDocument, MLSearchStrokes.

### 4.6 Artboards, backgrounds, status bar
- **Artboards:** 60 presets (A2–A5, US paper, inch sizes, iPhone/iPad, Pixel, Surface, social formats, Ponoko laser, and `Infinite`). Each artboard is an editable region with its own editor.
- **Backgrounds (10):** White, Blueprint, BrownPaper, Crumpled, Rippled, Heavyweight, Lightweight, DarkPrint, Transparent, CustomColor.
- **Status bar:** 4 groups (Zoom, Rotation, Offset, Measurements), 9 editable dimensions (Zoom, Rotation, OffsetX, OffsetY, Width, Height, Length, Area, Angle), ratio lock, per-dimension reset, presets. Units: mm, cm, m, km, in, ft, yd, mi, pt, px, %.

### 4.7 Other panels
- **Quick Clear:** strokes, text, images, PDFs, masks, measurements, layers, current layer.
- **Tool popup:** scaling and rotation modes, SetScale, PickerMode, filters, and include-options (all layers, locked items, partial items, eraser strokes, IgnoreAlpha).
- **Export:** PNG and PDF, with region (page or selection) and background/grid inclusion.
- **Colour:** wheel, picker, palette, eyedropper. **Text edit:** in-canvas. **PDF picker:** place a PDF page as an object.
- **Objects library:** brush/object packs. This is a content store, not connectors.

### 4.8 What Concepts does not have
No mind-map, no connectors or arrows between objects, and no object-linking UI. `setLinkIdentifier` exists in the data model but no panel drives it. Text is a real object, not raster.

---

## 5. Gap analysis: simple_notes vs Concepts

The clone is a **page-based note app with a raster ink engine**, not an infinite canvas.

Legend: ❌ missing · ⚠️ partial

### Foundation

| Area | simple_notes today | |
|---|---|---|
| Infinite canvas | `"infinite"` is only a page type that hides the paper border. Ink still lives in a fixed page bitmap (`AUDIT` C27/C33 open). | ❌ |
| Vector strokes | `DrawingCanvasView` rasterises into a `Bitmap`. `ContinuousStroke` is vector but is **not persisted**. | ⚠️ |
| Object model | Text and stickers are rasterised into `ImageLayer`s. Shapes are rasterised into the page. | ❌ |
| Grouping / multi-select | Absent. Lasso is a raster cut/paste. | ❌ |
| Artboards | Absent. | ❌ |
| Undo | Per page, in memory (pixel snapshots in single-page mode, op-list in the reader). **Not persistent.** | ⚠️ |

### Layers

| Area | simple_notes today | |
|---|---|---|
| Layer count | `PageLayerStack`: max 3 drawing layers plus max 3 floating image layers, as two unrelated systems. | ⚠️ |
| Layer features | Has visibility, select, merge, rename. Missing reorder, opacity, lock, focus. | ❌ |

### Precision

| Area | simple_notes today | |
|---|---|---|
| Grid | Decorative paper patterns only (BLANK/GRID/LINES/DOTTED). No snapping grid. | ❌ |
| Snapping, shape guides, measurements | Absent. | ❌ |
| Shape recognition | 3 bbox heuristics (line/circle/rect) in `autoShape` (`DrawingCanvasView.kt:3162`). | ⚠️ |
| Status bar | Zoom % only. | ⚠️ |

### Input and ink

| Area | simple_notes today | |
|---|---|---|
| Brushes | One flat `Paint` per tool. No brush engine or custom brushes. | ❌ |
| Pressure | Single power curve `pow(p, 1/sensitivity)` (`DrawingCanvasView.kt:927`). No tilt/azimuth. | ⚠️ |
| Stylus buttons | `onStylusDetected` only sets a flag. | ❌ |
| Keyboard shortcuts | None (only volume-key page turn). | ❌ |
| Colour picker | Fixed 24-swatch grid plus long-press palette. No HSV picker, no eyedropper. | ⚠️ |

### Other

| Area | simple_notes today | |
|---|---|---|
| Object linking | Absent (also absent from Concepts' UI). | ❌ |
| Export | PNG, raster PDF, MD/HTML. No vector, no SVG. | ⚠️ |

---

## 6. Recommended build order

Items keep their original numbers (#1–#20) so existing references still work. The order of work is set by dependencies, not by number.

### 6.1 Dependency graph

```
#9 World-space transform ─┬─► #1 Object model ─┬─► #2 Selection & object ops
                          │                    ├─► #14 Object linking
                          │                    └─► #7 Shape guides + measurements ─► #8 Status bar v2
                          ├─► #10 Artboards
                          └─► #16 SVG export (needs #1)

#3 Layers v2   (best after #1 so layers hold objects; can start in parallel)
#4 Grid + snap (needs #9 for world-space coordinates, benefits from #1)
#5 HSV picker, #6 Shape recognition, #11-#13, #15, #18, #20: largely independent
```

### 6.2 Phase 0: Foundation (do first)

**#9. World-space canvas transform.** *5–8 days.*
Add `CanvasTransform(scale, offsetX, offsetY)` in world space with no page boundary. Pages become named regions inside the world. Strokes and objects get world coordinates. Doing this *after* #1 and #3 means reworking both, so do it first.

### 6.3 Phase 1: Structure (high impact)

**#1. Object layer (text and shapes as real objects).** *3–5 days.*
Introduce a sealed `CanvasObject` (`InkObject`, `TextObject`, `ShapeObject`, `ImageObject`) with a transform (x, y, w, h, rotation, opacity, z), stored as JSON next to the page PNG. Today `addTextBlock` rasterises text into an `ImageLayer`.
*Unlocks:* selection menu, group/ungroup, flip, duplicate, per-object opacity.
*Needs a decision:* ink strokes should be persisted as vector (`InkObject`) as well, otherwise ink stays raster and cannot be selected or snapped.

```kotlin
// Sketch only; names are illustrative
sealed interface CanvasObject {
    val id: String
    val transform: ObjectTransform   // world-space x, y, w, h, rotation
    val opacity: Float
    val locked: Boolean
    val layerId: String
}
```

**#3. Layers v2.** *3–4 days.*
Replace the two systems (3 drawing + 3 image layers) with one ordered, unlimited list. Each layer has `name, visible, opacity, locked, focused, thumbnail`. Mirrors `LayerType`, `LayerMenuAction` and `LayerAppearance`.

**#2. Selection menu and object operations.** *2–3 days after #1.*
Group, ungroup, duplicate, flip, lock, reorder z. Multi-select by tap and by marquee over objects (not the raster lasso). Modelled on `SelectionMenuAction`.

### 6.4 Phase 2: Precision and feel

**#4. Real grid with snapping.** *2–3 days.* Dot, lined, graph, isometric and triangle presets, `SnapToGrid` while drawing and editing, and `AlignToGrid`.

**#6. Shape recognition.** *1–2 days if the dependency is acceptable, see the warning below.* Feed stroke points to a recogniser and get back a classified shape (line, circle, rectangle, triangle, arrow).

> **Verify before committing.** The earlier draft called Google Digital Ink a free open-source library licensed Apache-2.0. That needs checking. To my knowledge, ML Kit Digital Ink Recognition is distributed under Google's ML Kit terms (not Apache-2.0), and its recognition models are normally **downloaded on demand**, which conflicts with an offline-only, zero-permission app. The Maven coordinate `play-services-mlkit-digitalink` should also be confirmed; the documented artifact is `com.google.mlkit:digital-ink-recognition`. Concepts bundling `libdigitalink.so` shows a native, bundled approach exists, but not that we can reuse it.
> **Fallback:** extend the current heuristics with a permissively licensed stroke-gesture recogniser (the $1/$P family), which is small, offline and dependency-free. Check the licence of whichever implementation you pick.

**#8. Status bar v2.** *2 days.* Zoom, rotation, offset and measurements, with units. Mostly UI over values already computed during pan/zoom.

**#5. HSV colour picker and eyedropper.** *1–2 days.* Replaces the fixed 24-swatch grid.

**#7. Shape guides and measurements.** *3–5 days after #1.* `ShapeGuideKind {Angle, Arc, Ellipse, Line, Rectangle}` with live length/area/angle readouts, convertible into a persisted `MeasurementObject`. Feeds #8.

**#10. Artboards.** *2–3 days after #9.* Regions inside the infinite canvas with a size or preset (including `Infinite`). The 60 presets are only data; ship 10–15.

### 6.5 Phase 3: Nice-to-have

| # | Feature | Notes | Effort |
|---|---|---|---|
| 11 | Configurable multi-tap gestures | 2/3/4-finger to undo/redo/zoom/layers. Cheap once gesture routing exists. | 1–2 d |
| 12 | Canvas keyboard shortcuts | Concepts has 74 commands. We have none. | 1–2 d |
| 13 | Tool slots (8) with per-slot colour/thickness, plus opacity/smoothness sliders | Mirrors `ToolPropertySet`. | 2 d |
| 14 | Object linking | `setLinkIdentifier` exists in Concepts' model but has no UI. Needs #1 and a link layer. | 3 d |
| 15 | Quick Clear panel | Mirrors `QuickClearOption`. | 0.5 d |
| 16 | SVG export | Right format for an infinite canvas. Needs #1. | 2 d |
| 17 | Brush engine and custom brushes | Concepts ships ~1,000 textures and a custom-brush DB. | 1–2 wk |
| 18 | Pressure/tilt/azimuth and stylus buttons | `AuxiliaryButtonKind/Action`, `StylusManager`. | 2–3 d |
| 19 | Perspective grids and vanishing-point gestures | 1/2/3-point grids. | 1 wk |
| 20 | Deterministic ink smoothing | Catmull-Rom, width smoothing, stabiliser. We have quadratic-midpoint plus EMA only. | 1–2 d |

### 6.6 Effort summary

| Scope | Items | Estimate |
|---|---|---|
| Foundation | #9 | 5–8 d |
| Your shortlist | #1, #3, #4, #6, #8 | 11–16 d |
| **Shortlist + foundation** | #9 + shortlist | **16–24 d (about 3–5 weeks)** |
| Full Tier 1 + 2 | #1–#10 | about 24–38 d |

Estimates are single-developer, exclude testing and migration of existing notes, and assume the Compose canvas can host the new model without a rewrite. Treat them as ±30%.

### 6.7 Explicitly not worth pulling
- **Cloud sync** (Couchbase Lite, `accounts`/`googledrive` packages, 700+ store classes). Our app is offline-only by design.
- **Object/brush pack store** (`objects.*`, `store.*`, ~1,400 classes). A monetised content store.
- **XR** (`libandroidx.xr.runtime.openxr.so`, `xr.*`). Out of scope.
- **Trial, billing, re-engagement** (`reversetrial.*`, `reengagement.*`, `interstitial.*`). Irrelevant.
- **The native C++ engine.** Not portable; we re-design in Kotlin.

### 6.8 Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Migrating existing raster pages into a world-space model | Data loss or visual shifts in old notes | Import each old page as an `ImageObject` on a named page region; keep the original PNG until verified |
| Persisting vector ink grows file size and slows load | Large pages feel slow | Tile or cull by viewport; simplify points on save |
| Multi-page notes, PDF pages and TOC assume discrete pages | Wide refactor | Decide section 7, question 1 before starting #9 |
| ML dependency conflicts with offline/zero-permission goal | Policy or size regression | Resolve section 7, question 3 first; fallback recogniser exists |

---

## 7. Decisions needed

1. **Replace the page model, or add the infinite canvas as a second mode?**
   Multi-page notes, PDF pages and the TOC all assume pages. A second mode limits the blast radius but doubles the maintenance. This affects #9.
2. **Should text and shapes become first-class objects, or stay rasterised?**
   This is the biggest behavioural change (#1). Most of the roadmap depends on "first-class".
3. **Is a Google ML dependency acceptable** in an offline-only, zero-permission app?
   Check the licence and the model-download behaviour (section 6.4), plus the size (reported at about 7 MB per ABI), or choose the heuristic fallback.
4. **Confirm the scope of "a few functionalities".**
   Suggested shortlist: **#9 (foundation), #1 (objects), #3 (layers v2), #4 (grid + snap), #6 (shape recognition), #8 (status bar)**, about 3–5 weeks.

---

## 8. Clean-room notes

- Everything here was derived from the manifest, DEX structure, string resources and native symbol tables. No Concepts code was decompiled or copied.
- Concepts' vocabulary (layer types, property sets, precision toggles, artboard presets, units) is reproduced as a **feature list**. Re-implement it in our own words and architecture.
- Re-run the analysis on the **official** build (see section 2) so the reference is unambiguous.
- Open-source or already-in-use components:
  - **pdfium**: already used (`util/PdfiumBridge.kt`).
  - **Google Digital Ink**: licence and offline behaviour must be verified (section 6.4). Do not assume Apache-2.0.
- Our clone is Apache-2.0/MIT clean-room (`README.md`). Keep the attribution discipline.

---

## 9. Reproducing this analysis

```bash
# The ledger lives in the project folder
craft-anything-cli --project simple_notes/concepts-analysis.cany core_investigation_show
craft-anything-cli --project simple_notes/concepts-analysis.cany core_evidence_show --detail summary
craft-anything-cli --project simple_notes/concepts-analysis.cany report --out REPORT.md

# Raw analysis artefacts
ls /tmp/opencode/concepts/     # class lists, string tables, native symbol dumps
```
