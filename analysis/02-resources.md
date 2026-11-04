# Phase 2 — Resources (TouchNotes 7.0.4)

**APK:** `/home/lk1/touchnotes-7-0-4.apk`
**Tooling:** apktool decode (`res/`), aapt2-compatible resource table. Search boundary: decoded resources only.

## Resource volume
- Layouts: **1239** XML files under `res/layout*`.
- Locales: 80+ (`values-*`, including zh-CN/HK/TW, ja, ko, es, etc.).
- Densities: 120/160/240/320/480/640/65534 plus `drawable-watch`.
- Resource table: `resources.arsc` ~6.0 MB (from 01-structure).
Evidence: decoded `res/` listing.

## Strings
- Primary file: `res/values/strings.xml` (very large; contains app UI strings + third-party SDK strings).
- Notable app strings: backup/import/export, note paths, permission hints, theme names.
- Obfuscated/encrypted strings present: many `agc_*` keys (Huawei AGConnect). Search boundary: decoded XML.

## Colors / themes
- Theme swatches (10): beige, blue, dark_blue, dark_green, green, light_blue, light_green, orange, pink, yellow (`*_theme` in `res/values/colors.xml`).
- Accent: `app_color` = `#fffb6443`; light variant `app_color_l` = `#30fd6643`.
- Night mode: `values-night`, `values-night-v29/v33`, `color-night`, `drawable-night`.
- Material/AppCompat base colors also present.

## Drawables
- Thousands of drawables across densities; icons for note grid/list, multi-select, empty states, pen widths, etc.
- Custom vector assets via `SvgView` (`app:vector="@drawable/..."`) for toolbar icons.
- Drawable resource names include `ic_note_grid`, `ic_note_list`, `ic_multi_selection`, `ic_empty_dirs`.
Evidence: decoded `res/drawable*` + layout references.

## Fonts / assets
- Fonts: `res/font/`, plus `assets/DroidSansChinese.ttf`, Katex (math rendering), jieba (Chinese segmentation).
- Other assets: Lottie JSON animations, ML models (formula OCR), PDF/PPT libs, ad SDK jars.
Evidence: decoded `assets/` listing.

## IDs / attrs
- `res/values/ids.xml`, `attrs.xml`, `dimens.xml`, `bools.xml`, `integers.xml`, `arrays.xml`, `fractions.xml`, `drawables.xml`.
- Custom attributes for `SvgView` (`activeColor`, `vector`), `ColorPickerView`, etc.

## Unknowns
- Exact resource IDs / aapt2-assigned numbers not mapped here (could be dumped with `aapt2 dump resources`); not required for clone skeleton.<|end|><|start|>write to=assistant<|channel|>analysis<|message|>Created file successfully: simple_notes/touchnotes-clone/analysis/02-resources.md