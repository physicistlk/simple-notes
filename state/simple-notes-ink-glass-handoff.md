# Simple Notes handoff: ink latency + liquid-glass pen dock

Target repo: `https://github.com/physicistlk/simple-notes`  
Reviewed at commit: `9e8b394634213ceac7a3b919e3c8c2322578d55d`  
Package: `personal.notes.touch`  
Constraints to preserve: offline-only, zero network permissions, `minSdk 21`, Compose + Android Canvas, existing undo/redo and page persistence.

This is a patch brief for an agentic coding tool. The current commit already contains several partial fixes: dirty-rect undo in the single-page canvas, dirty-patch undo in the continuous reader, a reduced 9-tap glass shader, and an off-main copy in `ContinuousInkController.persist()`. Do not stop at those. The remaining problems are mostly input ownership, popup multiplicity, stale glass backdrops, full-page redraws, and save/snapshot paths that still touch the UI thread.

---

## P0 — fix the “stuck” and first-stroke failures first

### 1. Continuous reader: the first stylus stroke can cancel itself

**Where**

- `app/src/main/java/personal/notes/touch/ui/canvas/CanvasScreen.kt`
  - `ContinuousPageView`
  - gesture handler keyed by `pointerInput(page.id, inkSettings)`
  - calls `onStylusDetected()` on stylus down
- `app/src/main/java/personal/notes/touch/ui/canvas/CanvasViewModel.kt`
  - `onStylusDetected()`
- `app/src/main/java/personal/notes/touch/ui/canvas/ContinuousInkController.kt`
  - `beginStroke()`, `cancelStroke()`

**Mechanism**

`inkSettings` is rebuilt from `CanvasUiState`. On the first stylus down, `onStylusDetected()` can flip `stylusOnly` to `true`. That changes `inkSettings`, which is a key of the active `pointerInput`. Compose restarts the gesture coroutine, and the `finally` block calls `ink.cancelStroke()`. The stroke that just began is destroyed by the same state change that was supposed to enable stylus-only mode.

This also means any tool/color/width change that alters `inkSettings` during a gesture can abort the gesture.

**Required change**

Do not key the active stroke handler on mutable tool settings. Key it only on stable identity such as `page.id` and the controller instance. Read the latest settings through `rememberUpdatedState`.

```kotlin
val latestInkSettings by rememberUpdatedState(inkSettings)

Box(
    modifier = Modifier
        .matchParentSize()
        .pointerInput(page.id, ink) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val settings = latestInkSettings

                if (settings.readingMode) return@awaitEachGesture

                val isStylus = down.type == PointerType.Stylus ||
                    down.type == PointerType.Eraser
                if (isStylus) onStylusDetected()

                val allowed = isStylus ||
                    (!latestInkSettings.stylusOnly && down.type == PointerType.Touch)
                if (!allowed || !latestInkSettings.isWritingTool) {
                    return@awaitEachGesture
                }

                down.consume()

                // Begin stroke using the settings captured at down.
                // During the loop, read latestInkSettings only where a
                // settings change should intentionally affect the stroke.
            }
        }
)
```

Also make the `finally` cleanup conditional: it should cancel only if this handler still owns the active stroke. Do not let a recomposition or a new gesture clean up a stroke owned by another page/session.

**Acceptance**

- Fresh app open, default settings, first stylus stroke appears and persists.
- Enabling/disabling stylus mode does not erase an in-progress stroke unless the user explicitly changes mode.
- Changing width/color while not drawing does not leave the reader in `isDrawing = true`.

---

### 2. Pen dock: only one popup may exist at a time

**Where**

- `CanvasScreen.kt`
  - horizontal dock color swatches
  - vertical dock color swatches
  - `ColorPalettePopup`
  - `WidthPickerPopup`
  - `BackHandler`
  - `dismissCanvasOverlays()`

**Mechanism**

`ColorPalettePopup` is currently emitted inside each color swatch loop, but all instances share the same `showColorPopupHoriz` / `showColorPopupVert` flag. A long press can therefore create three stacked focusable popups. Dismissing one leaves the others behind, which reads as “the menu is stuck.”

The width popup is non-focusable and has `dismissOnBackPress = false`, but the screen-level `BackHandler` does not check whether a width popup is open. Back can exit the note instead of closing the popup.

**Required change**

Introduce one active-popup state for the dock:

```kotlin
sealed interface DockPopup {
    data class Width(val chipIndex: Int) : DockPopup
    data class Color(val anchorIndex: Int) : DockPopup
}

var activeDockPopup by remember { mutableStateOf<DockPopup?>(null) }

fun closeDockPopup() {
    activeDockPopup = null
}
```

Render one popup per dock orientation, outside the swatch loops. Anchor it to the chip that opened it. Close it on:

- canvas touch,
- back press,
- tool selection,
- orientation toggle,
- page change,
- reading-mode toggle,
- outside touch if the popup is non-focusable.

Add a dedicated back handler before the note-exit handler:

```kotlin
BackHandler(enabled = activeDockPopup != null) {
    activeDockPopup = null
}
```

For the width slider, keep local thumb state while dragging. Commit the final value on `onValueChangeFinished`, or update only a lightweight pen-settings holder. Do not rewrite the whole `CanvasUiState` on every slider pixel if that restarts unrelated composition/input work.

**Acceptance**

- Long-press any color swatch opens exactly one palette.
- Selecting a color, tapping outside, pressing back, or touching the canvas closes it completely.
- Long-press width opens exactly one slider.
- Back closes the slider before it closes the note.
- No invisible popup remains after dock orientation changes.

---

### 3. Dock drag handle: own the gesture and clamp using real bounds

**Where**

- `CanvasScreen.kt`
  - horizontal dock drag handle `pointerInput(Unit)`
  - vertical dock drag handle `pointerInput(Unit)`
  - `pencaseOffsetX`, `pencaseOffsetY`
  - `screenWidthPx`, `screenHeightPx`

**Mechanism**

The current handle uses `awaitFirstDown(requireUnconsumed = false)` and does not consume the down immediately. It ignores whether later changes were consumed by another handler. It also has no explicit cancel cleanup. The clamp math uses screen configuration values and hard-coded margins, not the measured dock size, parent size, or insets. The dock can be dragged into a position where the handle is hard or impossible to reach.

**Required change**

Use one explicit drag state machine per handle:

- consume the initial down,
- track the active pointer id,
- consume moves only after drag starts,
- distinguish tap from drag,
- handle up and cancel,
- reset transient drag state in `finally`,
- clamp the whole dock rectangle inside the actual canvas/parent bounds minus system insets.

Sketch:

```kotlin
.pointerInput(dockOrientation, parentBounds, insets) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()

        var dragging = false
        val start = down.position

        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                    ?: break

                if (!dragging) {
                    if ((change.position - start).getDistance() > viewConfiguration.touchSlop) {
                        dragging = true
                    }
                }

                if (dragging) {
                    val delta = change.position - change.previousPosition
                    change.consume()
                    moveDockBy(delta) // clamps using measured dock + parent + insets
                }

                if (change.changedToUpIgnoreConsumed()) {
                    change.consume()
                    if (!dragging) toggleDockOrientation()
                    break
                }
            }
        } finally {
            // Reset any visual pressed/dragging state.
            // Do not leave a half-drag state behind.
        }
    }
}
```

Clamp against measured size:

```kotlin
val minX = insets.left.toFloat()
val minY = insets.top.toFloat()
val maxX = parentWidth - dockWidth - insets.right
val maxY = parentHeight - dockHeight - insets.bottom
```

Re-clamp the stored offset after:

- orientation toggle,
- rotation/configuration change,
- dock size change,
- inset change.

**Acceptance**

- Dock cannot be dragged off-screen.
- Handle remains reachable in both orientations.
- A canceled gesture does not leave the dock stuck in drag mode.
- A tap toggles orientation; a drag does not toggle orientation.

---

### 4. Liquid glass: stop sharing one mutable shader/paint across all glass elements

**Where**

- `app/src/main/java/personal/notes/touch/ui/components/LiquidGlass.kt`
  - `LiquidGlassShader`
  - `Modifier.liquidGlassBackdrop`
- `CanvasScreen.kt`
  - single `glassShader` passed to dock, HUD pills, and width popup

**Mechanism**

One `LiquidGlassShader` owns one mutable `RuntimeShader` and one mutable `Paint`. Every glass element calls `obtain()` and mutates the same uniforms/paint. When multiple glass elements draw in the same frame, the last uniform write can affect the wrong element or produce stale/flickering refraction.

**Required change**

Use one renderer/shader/paint per glass element, or make the draw path create an immutable snapshot per element.

```kotlin
@Composable
fun rememberLiquidGlassRenderer(): LiquidGlassRenderer =
    remember { LiquidGlassRenderer() }
```

Call sites:

```kotlin
val dockGlass = rememberLiquidGlassRenderer()
val stylusHudGlass = rememberLiquidGlassRenderer()
val zoomHudGlass = rememberLiquidGlassRenderer()
val widthPopupGlass = rememberLiquidGlassRenderer()
```

Do not expose a shared mutable `shaderPaint`. If a paint is needed, create or cache it per renderer.

Also clip the glass content to the rounded shape:

```kotlin
Modifier
    .clip(RoundedCornerShape(26.dp))
    .liquidGlassBackdrop(...)
    .liquidGlassRim(...)
```

Alternatively, make the AGSL return transparent pixels outside the rounded-rect SDF. The current shader draws an opaque rectangle; without clipping, the dock can look like a slab with rounded borders instead of rounded glass.

**Acceptance**

- Dock, HUD pills, and popup can be visible together without one stealing another’s backdrop/size/tint.
- Dock corners are actually rounded and transparent outside the shape.
- No per-frame `printStackTrace()` from recycled-bitmap fallback.

---

## P1 — remove the remaining stroke latency

### 5. Single-page canvas: separate live ink from the committed page

**Where**

- `DrawingCanvasView.kt`
  - `onTouchEvent()`
  - `onDraw()`
  - `drawFountainPenStroke()`
  - `drawTaperedLine()`
  - `drawTaperedQuad()`

**Mechanism**

Every move calls `invalidate()` on the whole view. `onDraw()` redraws the background, paper pattern, committed bitmap, layers, margin ink, and the entire in-progress stroke. The fountain pen tessellates the whole stroke every frame, so cost grows with stroke length. Hardware-accelerated `invalidate(Rect)` is not a reliable dirty-rect optimization on modern Android.

**Required change**

Introduce a live-ink overlay that is independent of the committed page:

- committed page/background/pattern/layers are cached and redraw only when committed content or transform changes;
- live stroke is drawn into a front-buffered or overlay layer;
- on pointer up, commit the stroke once into the backing bitmap and clear the live layer;
- on cancel, clear the live layer without touching committed pixels.

Preferred path on API 29+:

- add `androidx.graphics:graphics-core:1.0.4`;
- use `CanvasFrontBufferedRenderer` or `LowLatencyCanvasView` for the live stroke;
- keep the existing Canvas renderer as fallback for API 21–28.

Fallback path for older devices:

- a transparent overlay `View` or overlay bitmap above the committed canvas;
- invalidate only the overlay during movement;
- do not redraw the full page bitmap per sample.

Also:

```kotlin
override fun onTouchEvent(event: MotionEvent): Boolean {
    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
        requestUnbufferedDispatch(event)
    }
    // existing routing
}
```

Process historical samples for all writing tools, not only fountain pen. Fast strokes currently lose intermediate points for pen/highlighter/eraser.

**Acceptance**

- A 10-second fast scribble does not degrade as the stroke gets longer.
- Live stroke remains visible while committed page layers are unchanged.
- Cancel/palm rejection clears only live ink.
- API 21–28 fallback remains usable.

---

### 6. Single-page canvas: track pointer id, not pointer index 0

**Where**

- `DrawingCanvasView.kt`
  - `onTouchEvent()`
  - stylus-only check
  - multi-touch cancel logic
  - stroke commit logic

**Mechanism**

The code frequently uses `event.x`, `event.y`, and `event.getToolType(0)`. That assumes the active drawing pointer is index 0. If a palm touches first, or the stylus is the second pointer, the canvas can block the stylus, cancel the stroke, or commit at the wrong pointer location. If the stylus lifts while a palm remains down, the event is `ACTION_POINTER_UP`, not `ACTION_UP`, and the current stroke path may not commit correctly.

**Required change**

Maintain an active drawing pointer id:

```kotlin
private var activePointerId: Int? = null
```

On down:

- choose the stylus/eraser pointer if present;
- otherwise choose the allowed touch pointer;
- store `event.getPointerId(actionIndex)`.

On move:

- find the pointer index by id;
- read x/y/pressure from that index;
- process historical samples for that pointer id.

On pointer up:

- if the lifted pointer is the active drawing pointer, commit or cancel based on flags;
- if another pointer lifted, continue the stroke.

Handle palm rejection explicitly:

- `ACTION_CANCEL` cancels the active stroke;
- API 33+ `FLAG_CANCELED` on pointer up cancels that pointer’s stroke;
- in stylus-only mode, ignore non-stylus pointers instead of treating them as drawing input.

**Acceptance**

- Palm resting on screen does not erase or commit the stylus stroke.
- Stylus can draw even if it is not pointer index 0.
- Lifting stylus while palm remains down commits the stylus stroke correctly.
- System cancel does not leave `currentPath`, `currentStrokePoints`, or `isDrawing` stuck.

---

### 7. Continuous reader: make live preview incremental

**Where**

- `ContinuousInkController.kt`
  - `drawPreview()`
  - `drawStroke()`
  - `smoothPath()`
  - `previewRevision`
  - `revision`
- `CanvasScreen.kt`
  - `ContinuousPageView` preview Canvas

**Mechanism**

The preview redraws the whole live stroke on every sample. For fountain pen it redraws every segment from the beginning. Path and Paint objects are allocated during drawing. The preview Canvas also reads `ink.revision`, so committed-layer changes invalidate more than the live preview.

**Required change**

Split the signals cleanly:

- `previewRevision`: live stroke only;
- `committedRevision`: committed ink/undo/redo/session changes;
- `sessionRevision`: session ready/loading changes, if needed.

The live preview Canvas should read only the live-preview signal. Committed ink Canvas should read the committed signal. Page composition should not observe every live sample.

For rendering:

- constant-width tools: maintain an incremental `Path` and append only new quadratic segments;
- fountain pen: cache already-rendered opaque segments, or draw new segments into a live bitmap/tile;
- highlighter: avoid naive overlapping alpha segments; use a single path or save-layer strategy;
- eraser preview: keep a cheap preview, commit actual CLEAR on up.

Reuse `Paint` and `Path` objects where possible. Do not allocate them per sample.

**Acceptance**

- Long continuous strokes do not become progressively slower.
- Moving the pen does not recompose every visible page.
- Undo/redo still updates committed ink immediately.
- Cancel clears the preview without leaving a stale tail.

---

### 8. Undo snapshots: do not copy the full page for layer/margin strokes

**Where**

- `DrawingCanvasView.kt`
  - `saveUndoStateForRect()`
  - `strokeInkBounds()`
  - `currentLayerOwnsInk()`
  - stroke commit branch for `onLayer || offPaper`

**Mechanism**

For strokes owned by a layer or written outside the paper, the code still calls `saveUndoStateForRect(marks)`. If the stroke is entirely outside the page rect, the clipped area is empty and the code falls back to `saveUndoState()`, which copies the whole page. That reintroduces the large UI-thread copy the dirty-rect work was meant to remove.

**Required change**

Snapshot only the store that actually changed:

- page ink: dirty-rect page patch;
- layer ink: previous layer ink list/crop state, not page pixels;
- margin ink: previous beyond-page ink list, not page pixels;
- full-page snapshot only for operations that truly rewrite the whole page.

A stroke that lives entirely in a layer or margin crop should not allocate a page bitmap patch.

**Acceptance**

- Writing in PDF margins does not trigger a full-page undo copy.
- Writing on an image layer does not copy unrelated page pixels.
- Undo/redo restores the exact layer/margin/page state.

---

### 9. Save paths: finish moving full-page copies off the UI thread

**Where**

- `ContinuousInkController.kt`
  - `persist()` — partially fixed
  - `flushNow()`
  - `releaseSession()`
  - `dispose()`
- `CanvasViewModel.kt`
  - `persistPageBitmap()`
  - `saveContentLayer()`

**Mechanism**

The latest commit moved the periodic `persist()` copy off the main thread, but several flush/release/dispose paths still call `bitmap.copy(...)` on the calling thread, often the UI thread. Also, `inkLock` currently guards the periodic copy and stroke commit, but not all bitmap mutations such as undo/redo/clear/restore.

**Required change**

Define one snapshot protocol for each ink bitmap:

- all mutations of a given ink bitmap are serialized;
- full-page copies for encoding happen off the UI thread;
- the copy is immutable before PNG encoding;
- no encoder reads a bitmap while a stroke/undo/clear can mutate it.

Either:

1. use a dedicated single-thread ink executor per session and post mutations/snapshots to it; or
2. use the existing lock consistently around every mutation and copy, including undo/redo/clear/restore.

Do not mark a flush as saved before the repository write succeeds. `flushNow()` currently sets `savedWhileActive = true` before the async write and ignores failure; that can lose data.

**Acceptance**

- Page switch, mode switch, back press, and app backgrounding do not block the UI for a full-page copy.
- Failed writes leave the session dirty and retryable.
- No stroke is lost during save/exit races.
- No torn PNGs under rapid stroke + save.

---

## P2 — make the glass feel Apple-like without paying full shader cost every frame

### 10. Cache the frosted backdrop; do not frost the full page per dock repaint

**Where**

- `LiquidGlass.kt`
  - `LIQUID_GLASS_AGSL`
  - `LiquidGlassShader.obtain()`
- `CanvasScreen.kt`
  - `glassBackdropAt()`
  - `glassReaderBitmap`
  - `continuousPageMap`
  - `GlassOrigin`

**Mechanism**

The shader still samples the page bitmap with multiple taps per pixel. The backdrop source is also not always the actual composited page: continuous mode may use the pre-ink page bitmap while the visible page is the active ink session; single-page mode may use only the ink bitmap and ignore PDF background/pattern/layers.

`GlassOrigin` and page-map holders are plain mutable fields. Updating them from `onGloballyPositioned` does not necessarily invalidate the glass draw. The dock can move while the refraction stays visually stuck at an old origin.

**Required change**

Create a small cached backdrop crop for each glass element:

1. composite the actual visible source: paper + PDF/background + committed ink + visible layers;
2. crop only the region under the glass, with a small bezel margin;
3. downsample if needed;
4. blur once into a cached frost texture/bitmap;
5. run the cheap lens shader against the cached frost plus optional original crop for rim refraction.

Update the cached backdrop only when:

- committed ink changes,
- page/background/layers change,
- dock/page geometry changes,
- zoom bucket changes.

During active inking or dock dragging, freeze the expensive backdrop update and keep only cheap rim/specular/position feedback. Refresh once on stroke end or drag end.

Make origin/page-map changes observable to the draw phase:

```kotlin
class GlassOrigin {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
}
```

or explicitly invalidate the glass draw when the holder changes. Read these values inside the draw lambda, not in composition.

**Acceptance**

- Glass follows the dock while dragging.
- Glass refracts the actual visible PDF/ink, not a stale blank bitmap.
- Dock movement and stroke drawing do not trigger a full-page blur every frame.
- The material still reads as glass, not a white plastic card.

---

### 11. Visual polish and fallback

Keep the current material language, but tune it:

- rounded-rectangle SDF bezel;
- clear rim, frosted core;
- subtle chromatic dispersion only at the rim;
- top-left specular glint and weaker bottom-right bounce;
- soft ambient shadow;
- light tint alpha around `0.18–0.32`;
- dark tint alpha around `0.40–0.55`;
- icon contrast protected by local lens/brightness, not by making the whole dock opaque.

Interaction polish:

- selected tool bubble springs lightly;
- hover/stylus spotlight follows the pointer but resets on exit/up/cancel;
- dock lift/press feedback is transform-only, not layout-heavy;
- disable expensive spotlight/shader updates while active ink is moving.

Fallback:

- API 33+: AGSL lens;
- API 31–32: cached blur/RenderEffect if available, otherwise translucent rim material;
- API 21–30: translucent tint + rim + shadow, no RuntimeShader.

Do not use software rendering as a “fix.” Do not recycle bitmaps that Compose or the shader may still draw.

---

## Verification plan for the agent

Run on the physical tablet if available, not only emulator:

1. **First-stroke test**: fresh note, default settings, stylus down immediately. Stroke must appear and persist.
2. **Palm test**: rest palm, draw with stylus, lift stylus while palm remains. Stroke must commit correctly.
3. **Long-stroke test**: 10-second fast scribble in single-page and continuous modes. Frame time should not degrade with stroke length.
4. **Popup test**: long-press each color/width chip in both dock orientations. Exactly one popup opens; back/outside/canvas touch closes it.
5. **Dock drag test**: drag to all edges, rotate, change orientation, toggle fullscreen/insets. Handle remains reachable.
6. **Glass test**: place dock over PDF text, grid paper, committed ink, and margin ink. Refraction matches visible content and follows dock movement.
7. **Save/exit test**: draw, immediately back out, switch page, switch mode, background app. No lost strokes.
8. **Fallback test**: API 21–28 and API 31–32 paths remain usable without AGSL.

Use Perfetto / `dumpsys gfxinfo personal.notes.touch framestats` to compare before/after. The goal is not just “less CPU”; it is stable input-to-display latency during real stylus movement.

---

## Do not do these

- Do not merely increase the save debounce and call the lag fixed.
- Do not disable the glass globally as the primary fix.
- Do not key an active stroke `pointerInput` on mutable tool settings.
- Do not emit multiple popups from a loop while sharing one expanded flag.
- Do not share one mutable `RuntimeShader`/`Paint` across multiple glass elements.
- Do not rely on `invalidate(Rect)` to clip hardware-accelerated redraws.
- Do not recycle bitmaps still referenced by Compose, shaders, or pending saves.
- Do not mark a page saved before the repository write succeeds.
