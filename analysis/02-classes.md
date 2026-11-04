# Phase 2 — Key Classes (TouchNotes 7.0.4)

**APK:** `/home/lk1/touchnotes-7-0-4.apk`
**Tooling:** resource/layout/manifest inspection + native lib listing. Search boundary: class **names** recovered from XML/manifest only; method signatures not available because the application DEX is iJiami-packed (see 02-data-model.md). No Kotlin/Java source recovered.

## Application entry
- `com.ctZtamt.cym.ilajjtl.MyApplication` — Application subclass (packer wrapper).
- `com.ctZtamt.cym.ilajjtl.Entry` — entry helper.
- `com.ctZtamt.cym.ilajjtl.MyJni` — JNI bridge to packer.
- `a.auu.a` — packer bootstrap class.

## Activities
- `com.newskyer.draw.activity.SplashActivity`
- `com.newskyer.draw.PadActivity` (alias `com.newskyer.draw.MainActivity`)
- `com.newskyer.draw.activity.PayProActivity`, `SettingsActivity`, `CaptureSeviceActivity`, `TNNewsActivity`
- `com.newskyer.draw.file.activity.BookshelfActivity`, `MobileFileSelectActivity`, `DirectionSelectorActivity`
- `com.newskyer.draw.doc_scan.DocScanActivity`
- `com.newskyer.draw.flashCards.FlashCardActivity`
- `com.newskyer.draw.activity.LoginHuaweiDriveActivity`, `baidusync.BaiduLoginActivity`
- `com.newskyer.meetingpad.fileselector.activity.FileSelectActivity`, `PathSelectorActivity`
- `com.cicoe.user.mobile.activity.UserInfoSettingActivity`, `MobileLoginActivity`, `MeetingDetailsActivity`

## Custom views (drawing / notes)
- `com.newskyer.draw.PadWhiteBoard` — main drawing canvas (extends View/SurfaceView; exact superclass unknown).
- `com.newskyer.draw.NoteListView`
- `com.newskyer.draw.views.PageBackgroundView`
- `com.newskyer.draw.views.ColorPanelView`, `ColorView`
- `com.newskyer.draw.views.PanelPageLayout`, `CompomentLayout`
- `com.newskyer.draw.views.PenWidth`, `MeetPenWidth`
- `com.newskyer.draw.views.SvgView` (vector toolbar icons)
- `com.newskyer.draw.file.view.PanelLayout`, `PenBoxLayout`, `NoteTextLayout`, `NoteRichText`, `NoteEditText`, `EditRichTextLayout`
- `com.newskyer.paint.views.PanelGlView` — OpenGL panel (GLSurfaceView likely)
- `com.newskyer.draw.colorpicker.ColorPickerView`
- `com.newskyer.draw.cropper.CropImageView`, `crop.view.CropImageView`
- `com.newskyer.draw.file.view.NoteBookRecycleView`, `MaxLimitRecyclerView`, `SwipeRecyclerView`, `HvRecyclerView`, `AsrScrollView`, `AsrTextView`

## Native libraries (drawing/rendering)
- `libdraw_811.so`, `libdraw_c.so`, `libpanel.so`, `libinkkit_ocr_jni.so`
- `libandroidx.graphics.path.so`, `libglslang.so`, `libSPIRV.so`
- ML/OCR: `libpaddle_lite_jni.so`, `libopencv_java4.so`, `libformula_recognizer.so`, `liblatex_ocr_jni.so`, `libncnn.so`
- PDF: `libpdfCore.so`, `libpdfsdk.so`

## Export / import handlers
- No handler class names or method signatures recovered (packed DEX). Only UI string references and layout surfaces documented in 02-ui-components.md.
- Backup format: `.np` (default `Download/note_backup.np`).

## Unknowns
1. **Database/storage implementation** — no SQLite/Room/DAO classes visible; whether notes use files, SQLite, or a proprietary container is unknown. Search boundary: packed DEX + no runtime.
2. **Stroke data model & serialization** — no stroke/layer/point classes or serialization format recoverable; `.np` internal structure unknown. Need live device or unpacked DEX.
3. **Drawing engine API** — `PadWhiteBoard`/`PanelGlView` method signatures and JNI calls into `libdraw_811.so` unknown; cannot determine stroke rendering, undo stack, or layer model statically.
4. **Export/import handler signatures** — class names not recovered.
5. **On-disk note directory & file naming** in app-private storage unknown.
6. **Whether additional DEX is decrypted to disk at runtime** (e.g., `/data/data/com.newskyer.draw/...`) unknown; requires device.

Top 3 unknowns needing live testing: (1) storage backend & schema, (2) stroke serialization format, (3) PadWhiteBoard/PanelGlView drawing API.