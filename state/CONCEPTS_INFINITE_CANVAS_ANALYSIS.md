# Concepts (2026.04) — Infinite Canvas Analysis & simple_notes Gap Plan

**Target analysed:** `simple_notes/Concepts_v2026.04_MOD.apk`
`com.tophatch.concepts` · version 2026.04 · minSdk 26 / targetSdk 36 · arm64-v8a
**Method:** static analysis only (craft-anything / craft-anything-cli, deep mode). No code was run, nothing was copied.
**Ledger + evidence:** `simple_notes/concepts-analysis.cany/` (12 findings, 88 evidence records, `REPORT.md`)
**Our app analysed for comparison:** `simple_notes/touchnotes-clone` (package `personal.notes.touch`)

---

## 1. How Concepts is built (what "Concepts canvas" actually is)

```
MainActivity ── CanvasFragment ── GalleryFragment
                    │
                    ├── core.ui.CanvasElements  (Kotlin-facing UI model, 13 panels)
                    │     toolBar · layers · statusBar · selectionMenu · contextMenu
                    │     precision(grid/guide/snap/measure/recognition) · quickClear
                    │     settings(grid/artboard/measurements/touchInput/stylus)
                    │     export · textEdit · toolPopUp · pdfPicker · library
                    │
                    └── core.CanvasController (13 sub-controllers)
                          document · renderer · input · interface · command
                          background · selection · resources · picker · export
                          workspace · undo · test
                                    │
                    ┌───────────────┴────────────────┐
                    ▼                                ▼
        libCore.so  (84 MB, C++)            libimpress_api_jni.so
        th::drawing::FluidEngine::Drawing    (vector shape rendering)
        th::input (routers + gestures)       libdigitalink.so
        th::canvas (models + editors)         libdigitalinksegmentation.so
        th::document (files, archives)        libpdfium.so
        th::ml (Digital Ink ML pipeline)      libcblite.so (sync db)
        th::gpu / th::tile (renderer)         libandroidx.xr.runtime.openxr.so
```

Key structural facts (all evidence-backed in the ledger):

| Fact | Detail |
|---|---|
| **Native core** | All document/ink logic is C++ (`libCore.so`). Kotlin is a thin bridge (`core.NativeCanvas*`). We cannot port their engine; we must re-design. |
| **Item tree** | `th::drawing::FluidEngine::Drawing::Tree` holds `RootItem → LayerItem / GroupLayerItem → LeafItem`. Leaf kinds: **StrokeItem, TextItem, ImageItem, PDFPageItem, MeasurementItem**. Layer kinds also include **GridLayerItem, MeasurementLayerItem**. |
| **Per-item state** | `LeafItem` exposes `setLinkIdentifier`, `addKeyPoint`/`keyPointWithType`, `setTaper`, `setUniformPressure`, `setUniformAzimuthAngle`, `setSnapToStrokePoints`, `setAccurateBounds`, `setOpacity`, `setLocked`, `setHighlighted`, `setFocus`, `setClosed`. |
| **Drawing domains** | `th::drawing::FluidEngine::Drawing::{Document, Tree, Metadata, Workspace, Resources, Tool, ToolDatabase, Grid, Selection, Artboard, Snap, Guide, Picker, Slice, Nudge, Background, Color, Detached}` |
| **Input** | One `RouterTool` per mode (`Draw/Pan/Zoom/Rotate/Nudge/Slice/Text/Picker/Modify`) + a `Gesture` library + `th::input::shape_recognition` backed by Google **Digital Ink** ML (`th::ml::LiveRecognition`). |
| **Persistence** | `th::document::` — `Archive` (a container format), `metadata` + `resourcePack` + `workspacePack` files, autosave priority, crash-recovery candidates. |
| **Brushes** | `th::drawing::BrushKind/BrushType`, `CustomBrushDatabase/CustomBrushArchive/CustomBrushTexture/CustomBrushOutput`, `assets/brushpacks/*` (~1 000 brush texture files shipped). |
| **Deps** | `libcblite.so` (Couchbase Lite → cloud sync), `libdigitalink.so` (ML), `libpdfium.so` (PDF), `libimpress_api_jni.so` (vector shapes), OpenXR (XR). |

**Implication for us:** Concepts' *feel* (smooth strokes, snapping, shape recognition, artboards) comes from a native engine + Digital Ink ML. We will not match that. What we **can** cheaply match is the **information architecture**: the panel set, the layer model, the tool model, the precision toggles, the status bar, the object types.

---

## 2. Infinite-canvas feature inventory (Concepts)

### 2.1 Tool model — 27 tool types
`Airbrush · BallpointPen · CustomBrush · DottedBrush · DynamicPen · Eraser · Fill · FountainPen · HardPencil · Image · InverseFountainPen · LegacyPencil · Marker · Nudge · Pan · Redo · Rotate · Selection · Slice · SoftEraser · SoftPencil · Text · Undo · Watercolor · Wire · Zoom`

* Every tool has a **`ToolIdentifier`** = (type, brush UUID) — custom brushes are first-class.
* Every tool has a **`ToolPropertySet`** = colour + opacity + smoothness + thickness (4 sliders), and `ToolSlotState` keeps per-slot colour/thickness.
* The **tool bar has 8 slots**; the user can add/replace tools, and each slot keeps its own properties.

### 2.2 Interaction model
* 5 interaction modes: `Idle · Drawing · Picking · TransformingCanvas · TransformingSelection`.
* 43 canvas commands / 74 keyboard commands (`ZoomToFit`, `ZoomToSelection`, `ResetTransform`, `ShapeGuideToggle*`, `ToggleSnap`, `ToggleGrid`, `ToggleMeasurement`, `ToggleLayeringMode`, `TogglePrecisionVisibility`, tool slots 1–8, …).
* **Multi-tap gestures are user-bindable**: 2/3/4 fingers → Undo, Redo, SelectAll, AdjustLastStroke, ConfigureActiveTool, ToggleCanvasZoom, ToggleCanvasRotation, ToggleInterface, ToggleLayers, ToggleObjects, ToggleColorWheel, TogglePrecisionGuide, ToggleScreenRecording, DoNothing.
* **Shape guides** (5 kinds): Angle, Arc, Ellipse, Line, Rectangle — drawn, measured, then converted into a measurement.

### 2.3 "Precision" subsystem (5 independent toggles)
| Toggle | What it does | Options |
|---|---|---|
| **Grid** | Dot / Isometric / Lined / Lined-graph / Triangle / 1-,2-,3-point perspective grids — **36 presets** | customisable colour, spacing, custom dimension |
| **Guide** | Shape guide: Angle, Arc, Ellipse, Line, Rectangle | per-kind toggle |
| **Snap** | Snapping while drawing / while editing | drawing: ActiveLayerOnly, AlignToGrid, AllowTraceback, AllowTurns, **Autocomplete**, SnapToGrid, **Wobble** · editing: ActiveLayerOnly, SnapToGrid, **SnapToKeyPoints** |
| **Measure** | Length / area / angle measurement objects with live labels | units, precision, display format |
| **Recognition** | **ML shape recognition** — freehand → clean shape | uses `libdigitalink.so` |

### 2.4 Layer model
* Layer types: **Grid / Group / Measurement** (`core.ui.LayerType`).
* Layering mode: **Automatic vs Manual**.
* Layer menu: Configure, Delete, Duplicate, Focus, Unfocus, Lock, Unlock, **MergeDown**, Select.
* Per-layer: rename, reorder, visibility, **opacity scrub**, thumbnail, and *move selection to layer*.
* Layer appearance states: Faded / Focused / Neutral (focus = solo-like dimming of others).

### 2.5 Object types on the canvas
`StrokeItem` (ink) · `TextItem` (editable text) · `ImageItem` · `PDFPageItem` (PDF page as an object) · `MeasurementItem` (length/area/angle) · `GroupLayerItem` (grouping) · `LayerItem` · `GridLayerItem` · `MeasurementLayerItem`.

Selection menu: Copy, Cut, Delete, Duplicate, **Group**, **Ungroup**, Lock, Unlock, Enable/Disable transparency, **Flip H/V**, **EditText**, ToggleAttribution, ReopenPDFDocument, MLSearchStrokes.

### 2.6 Artboards
60 presets: A2–A5, US paper, inch sizes, iPhone/iPad, Pixel, Surface, Twitter/Instagram/Facebook/LinkedIn, Ponoko laser, and **`Infinite`**. Artboards are editable regions on the canvas with their own editor.

### 2.7 Backgrounds & paper
10 types: White, Blueprint, BrownPaper, Crumpled, Rippled, Heavyweight, Lightweight, DarkPrint, Transparent, **CustomColor**.

### 2.8 Status bar (the numeric HUD)
4 groups — **Zoom, Rotation, Offset, Measurements** — with 9 editable dimensions (Zoom, Rotation, OffsetX, OffsetY, Width, Height, Length, Area, Angle), ratio lock, per-dimension reset, presets, and popup/inline states. Units: mm, cm, m, km, in, ft, yd, mi, pt, px, %.

### 2.9 Other panels
* **Quick Clear**: ClearAllStrokes / Text / Images / PDFs / Masks / Measurements / Layers / CurrentLayer.
* **Tool popup**: Scaling mode, Rotation mode, SetScale, PickerMode, Filters, IncludeAllLayers / IncludeLockedItems / IncludePartialItems / IncludeEraserStrokes, IgnoreAlpha.
* **Export**: PNG, PDF, plus region choice (whole page / selection) and background/grid inclusion.
* **Colour**: colour wheel, colour picker, palette, eyedropper (picker mode).
* **Text edit**: in-canvas text editing (`CanvasTextEditElement`).
* **PDF picker**: choose a PDF page to place into the canvas.
* **Objects library**: brush/object *packs* (a store of pre-made stamped objects) — a content store, not connectors.

### 2.10 What Concepts does **not** have
No mind-map, no connectors/arrows between objects, no object-linking UI (the `setLinkIdentifier` exists in the data model but no panel drives it), no raster-text (text is a real object), no multi-select marquee beyond the selection menu.

---

## 3. Gap analysis: simple_notes (clone) vs Concepts

The clone today is a **page-based note app with a raster ink engine**, not an infinite canvas.

| Concept | simple_notes today | Verdict |
|---|---|---|
| Infinite canvas | `"infinite"` is only a *page type* that hides the paper border; ink still lives in a fixed page bitmap (`AUDIT` C27/C33 still open) | ❌ not a real infinite canvas |
| Vector stroke model | `DrawingCanvasView` rasterises into a `Bitmap`; `ContinuousStroke` is vector but **not persisted** (rasterised on save) | ⚠️ partial |
| Layers | `PageLayerStack` max **3** drawing layers + max **3** floating image layers — two unrelated systems | ⚠️ partial, hard cap |
| Layer features | visibility, select, merge, rename. **No reorder, no opacity, no lock, no focus** | ❌ |
| Object types | Text and stickers are **rasterised into `ImageLayer`s**; shapes are rasterised into the page | ❌ no real text/shape objects |
| Grouping / multi-select | absent (lasso is a **raster** cut/paste) | ❌ |
| Object linking | absent | ❌ |
| Measurements | absent | ❌ |
| Grid | decorative paper patterns only (BLANK/GRID/LINES/DOTTED), not a snapping grid | ❌ |
| Shape guides | absent | ❌ |
| Snapping | absent | ❌ |
| Shape recognition | 3 bbox heuristics (line/circle/rect) in `autoShape` (`DrawingCanvasView.kt:3162`) | ⚠️ minimal |
| Artboards | absent | ❌ |
| Brushes | one flat `Paint` per tool; no brush engine, no custom brushes | ❌ |
| Pressure | one power curve `pow(p, 1/sensitivity)` (`DrawingCanvasView.kt:927`); no tilt/azimuth | ⚠️ partial |
| Stylus buttons | `onStylusDetected` only sets a flag | ❌ |
| Keyboard shortcuts on canvas | none (only volume-key page turn) | ❌ |
| Colour picker | fixed 24-swatch grid + long-press palette; **no HSV picker, no eyedropper** | ⚠️ partial |
| Status bar HUD | zoom % only | ⚠️ partial |
| Undo | per-page, in-memory: pixel snapshots (single-page) / op-list (reader). **Not persistent** | ⚠️ partial |
| Export | PNG + raster PDF + MD/HTML. **No vector, no SVG** | ⚠️ partial |

---

## 4. Recommended build order (the "few functionalities" to pull over)

Ordered by **Concepts-fidelity ÷ effort**. Each item is self-contained and lands in the existing Compose canvas.

### Tier 1 — high impact, low risk (do these first)

**1. A real object layer on the canvas (Text + Shape as objects)**
Concepts' single biggest structural difference. Today `addTextBlock` rasterises text into an `ImageLayer`.
→ Introduce `CanvasObject` (sealed: `InkObject`, `TextObject`, `ShapeObject`, `ImageObject`) with a transform (x, y, w, h, rotation, opacity, z) stored as JSON next to the page PNG. Text stays editable; shapes stay editable. Z-order list, not a bitmap.
*Effort:* 3–5 days. *Unlocks:* real selection menu, group/ungroup, flip, duplicate, per-object opacity.

**2. Selection menu & object operations (group / ungroup / duplicate / flip / lock / reorder z)**
Straight from `core.ui.SelectionMenuAction`. Multi-select via tap + marquee on objects (not raster lasso).
*Effort:* 2–3 days once #1 lands.

**3. Layer model v2 — unlimited, reorderable, opacity, lock, focus, merge-down**
Replace the two separate systems (3 drawing layers + 3 image layers) with one ordered list of layers, each with `name, visible, opacity, locked, focused, thumbnail`. Mirrors `core.ui.LayerType` + `LayerMenuAction` + `LayerAppearance`.
*Effort:* 3–4 days.

**4. Precision: a real grid with snapping**
Concepts' `GridPreset`/`GridCategory` set (dot, lined, graph, isometric, triangle) + `SnapToGrid` while drawing/editing, plus `AlignToGrid`.
*Effort:* 2–3 days (grid render + point snapping in the stroke builder).

**5. HSV colour picker + eyedropper (picker mode)**
Concepts has `PickerMode {ColorPicker, ItemPicker, Lasso}` and a colour wheel. We have a 24-swatch grid only.
*Effort:* 1–2 days.

### Tier 2 — the "Concepts feel" (medium effort, high payoff)

**6. Shape recognition (real, not 3 bbox heuristics)**
Concepts uses Google Digital Ink (`libdigitalink.so`) — a **free open-source library** we can legitimately add as a dependency. `th::input::shape_recognition` + `th::ml::LiveRecognition` is exactly that. Feed the stroke points, get back a classified shape (line, circle, rectangle, triangle, arrow…).
*Effort:* 1–2 days to wire `com.google.android.gms:play-services-mlkit-digitalink` (or the standalone `digitalink` AAR) into our stroke builder.
*This is the single highest-fidelity win for the least code.*

**7. Shape guides + measurements**
Concepts' `ShapeGuideKind {Angle, Arc, Ellipse, Line, Rectangle}` → live length/area/angle readouts, convertible into a persisted `MeasurementObject`. Feeds the status bar.
*Effort:* 3–5 days.

**8. Status bar v2 — zoom, rotation, offset, measurements**
Currently we show zoom %. Concepts shows 4 groups / 9 dimensions with ratio lock and presets. Units mm/cm/m/km/in/ft/yd/mi/pt/px/%.
*Effort:* 2 days (mostly UI over values we already compute during pan/zoom).

**9. Canvas → world transform (the actual infinite canvas)**
Add a `CanvasTransform(scale, offsetX, offsetY)` in **world space** with no page boundary; pages become named regions inside the world. Objects and strokes get world coordinates. This is the real "infinite canvas" and it unlocks artboards naturally.
*Effort:* 5–8 days, but it is the architectural centrepiece — do it **before** #1/#3 if you want to avoid rework. **Recommendation: do it first.**

**10. Artboards**
Regions inside the infinite canvas with their own size/preset (`Infinite` preset included). 60 Concepts presets are just data; ship 10–15.
*Effort:* 2–3 days *after* #9.

### Tier 3 — nice-to-have

| # | Feature | Notes | Effort |
|---|---|---|---|
| 11 | Configurable multi-tap gestures | 2/3/4-finger → undo/redo/zoom/layers. Cheap once gesture routing exists. | 1–2 d |
| 12 | Keyboard shortcuts on canvas | Concepts has 74 commands with user-rebindable shortcuts. We have none. | 1–2 d |
| 13 | Tool slots (8 slots, per-slot colour/thickness) + per-tool opacity/smoothness sliders | Concepts' `ToolPropertySet` (colour, opacity, smoothness, thickness) | 2 d |
| 14 | Object linking (`setLinkIdentifier` exists in Concepts' model) | Tap object → link to another object; needs a link layer in the world model | 3 d |
| 15 | Quick Clear panel (clear strokes / text / images / masks / measurements / layers) | Cheap, mirrors `QuickClearOption` | 0.5 d |
| 16 | Vector export (SVG) | Concepts exports PNG/PDF only, but for an infinite canvas SVG is the right format | 2 d |
| 17 | Brush engine + custom brushes | Concepts ships ~1 000 brush textures and a `CustomBrushDatabase`. Big effort. | 1–2 wk |
| 18 | Pressure/tilt/azimuth + stylus button actions | Concepts: `AuxiliaryButtonKind/Action`, `StylusManager`, `setUniformAzimuthAngle` | 2–3 d |
| 19 | Perspective grids + vanishing-point gestures | Concepts: 1/2/3-point perspective grids, `VanishingPointPan/Pinch/Tap` gestures | 1 wk |
| 20 | Deterministic ink smoothing (Catmull-Rom + width smoothing + stabiliser) | We have quadratic-midpoint + EMA only | 1–2 d |

### Explicitly **not** worth pulling
* Concepts' **cloud sync** (Couchbase Lite `libcblite.so`, `accounts`/`googledrive` packages, 700+ class store layer) — our app is offline-only by design.
* **Objects/brush pack store** (`objects.*`, `store.*`, ~1 400 classes) — a monetised content store.
* **XR** (`libandroidx.xr.runtime.openxr.so`, `xr.*`) — out of scope.
* **Trial / billing / re-engagement** (`reversetrial.*`, `reengagement.*`, `interstitial.*`) — irrelevant.
* Native C++ engine — not portable; we re-design in Kotlin.

---

## 5. Clean-room notes

* Everything above was derived from **manifest, DEX class/field/method structure, string resources and native symbol tables** of the APK. No Concepts code was decompiled or copied.
* Concepts' *conceptual* vocabulary (layer types, tool property sets, precision toggles, artboard presets, measurement units) is reproduced here as a **feature list** — re-implement it in our own words and our own architecture.
* Two independent libraries Concepts uses are open source and usable by us directly:
  * **Google Digital Ink** (shape recognition) — Apache-2.0.
  * **pdfium** — we already use it (`util/PdfiumBridge.kt`).
* Our clone is Apache-2.0/MIT-clean-room already (`README.md`); keep the attribution discipline: Concepts is a *behavioural reference*, not a source of code.

---

## 6. Open questions (need a decision from the user)

1. **Do we replace the page model, or add the infinite canvas as a second mode?** The clone's multi-page notes, PDF pages and TOC all assume discrete pages. A world-space canvas is a big refactor (see Tier 2 #9).
2. **Should text/shape objects become first-class, or stay rasterised?** This is the biggest behavioural change (#1).
3. **Digital Ink dependency**: acceptable to add a Google ML dependency to an offline-only, zero-permission app? (`libdigitalink.so` needs no network, but it does add ~7 MB per ABI.)
4. **Scope of "a few functionalities"**: the user asked for a *few*. The recommended shortlist is **#1 (objects), #3 (layers v2), #4 (grid+snap), #6 (shape recognition), #8 (status bar)** — about two weeks of work.

---

## 7. How to reproduce this analysis

```bash
# the ledger lives in the project folder
craft-anything-cli --project simple_notes/concepts-analysis.cany core_investigation_show
craft-anything-cli --project simple_notes/concepts-analysis.cany core_evidence_show --detail summary
craft-anything-cli --project simple_notes/concepts-analysis.cany report --out REPORT.md

# the raw analysis artefacts
ls /tmp/opencode/concepts/     # class lists, string tables, native symbol dumps
```
