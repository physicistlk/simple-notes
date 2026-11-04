# Phase 2 — UI Components (TouchNotes 7.0.4)

**APK:** `/home/lk1/touchnotes-7-0-4.apk`
**Tooling:** apktool decode + resource/layout inspection. Search boundary: static resources/manifest only; app classes packed (see 02-data-model.md).

## Activities (from manifest, Phase 1)
- Launcher: `com.newskyer.draw.activity.SplashActivity`
- Main: `com.newskyer.draw.PadActivity` (reached via activity-alias `com.newskyer.draw.MainActivity`; exported; action `newskyer.intent.action.DRAW`)
- Other notable: `PayProActivity`, `SettingsActivity`, `BookshelfActivity`, `MobileFileSelectActivity`, `DirectionSelectorActivity`, `CaptureSeviceActivity`, `TNNewsActivity`, `DocScanActivity`, `FlashCardActivity`, `FileSelectActivity`, `PathSelectorActivity`, `UserInfoSettingActivity`, `MobileLoginActivity`, `MeetingDetailsActivity`, `LoginHuaweiDriveActivity`, `BaiduLoginActivity`, plus third-party SDK activities.
Evidence: shell:aapt2 badging/xmltree + decoded AndroidManifest.xml.

## Drawing canvas
- Custom view: `com.newskyer.draw.PadWhiteBoard` (used in `res/layout/activity_main.xml` as `drawboard`/`drawboard2`, full-screen, transparent background).
- OpenGL panel: `com.newskyer.paint.views.PanelGlView` (in layouts; GL-backed rendering).
- Supporting views: `com.newskyer.draw.views.PageBackgroundView`, `ColorPanelView`, `ColorView`, `PenWidth`, `MeetPenWidth`, `PanelLayout`, `PanelPageLayout`, `CompomentLayout`.
- Native rendering: `libdraw_811.so`, `libdraw_c.so`, `libpanel.so` (arm64-v8a).
Evidence: decoded `res/layout/*.xml` grep for `com.newskyer.draw.*Pad`/`PanelGlView`; 01-structure.md native lib list.

## Note list organization
- Custom list: `com.newskyer.draw.NoteListView`, `com.newskyer.draw.file.view.NoteBookRecycleView`, `com.newskyer.draw.views.MaxLimitRecyclerView`, `com.newskyer.draw.views.SwipeRecyclerView`.
- Item layouts: `note_item.xml`, `note_list_item.xml`, `grid_note_item.xml`, `tile_note_item.xml`, `note_page_item.xml`, `note_tree_item_layout.xml`.
- Document browser: `fragment_pad_doc.xml` (tile/list toggle `doc_icon_tile`/`doc_icon_list`, multi-select, path breadcrumbs, empty state `doc_image_empty`).
- Note naming/editing: `change_note_name.xml`, `new_note_layout.xml`, `new_note_list.xml`, `note_search_layout.xml`, `note_scale_layout.xml`.
Evidence: decoded res/layout listing + layout XML.

## Export / import surfaces
- Backup all notes → `.np` file (default `Download/note_backup.np`); import via "Import Note".
- Import sources: system picker, PDF/PPT/Word/CAJ/EPUB/MOBI, images, fonts (ttf).
- Export targets: image, PDF (grey/image PDF), audio, stickers, live motion.
Evidence: decoded `res/values/strings.xml` (export*/import* strings).

## Fragments / other screens
- Many layouts under `res/layout` (1239 files) including `flash_card_*`, `note_history_layout`, `note_merge_layout`, `pad_conflict_dialog`, `pad_loading_dialog`, `pad_room_info`, `window_note`, `float_note_tool*`, `drag_to_open_float_note`.
- Tablet/landscape variants: `layout-land`, `layout-sw600dp`, `layout-sw720dp`, `values-sw*`.

## Themes / styling
- App accent: `app_color` = `#fffb6443` (orange) with `_l` light variant; theme color swatches (beige/blue/dark_blue/dark_green/green/light_blue/light_green/orange/pink/yellow).
- Styles: `res/values/styles.xml` (Material/AppCompat based). Night variants: `values-night`, `color-night`, `drawable-night`.
Evidence: decoded `res/values/colors.xml`, styles.xml.