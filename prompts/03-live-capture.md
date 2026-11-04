# Phase 3: Live Capture (Tablet Required)

**Agent:** analyst + person
**Prerequisite:** Tablet connected (`adb devices` shows {{SERIAL}})

## Tasks
- Screenshot screens: list, text editor, drawing canvas, settings, export.
- Save to `reference/screens/` as `01-note-list.png`, `02-text-editor.png`, `03-canvas.png`, etc.
- Pull data: `adb pull /data/data/{{ORIGINAL_PACKAGE}}/databases/ reference/databases/`
- Verify activities with `dumpsys`.
- Update unknowns.
