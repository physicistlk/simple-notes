# SPEC — TouchNotes Clone Architecture

## 1. Overview
- Language: Kotlin only.
- Min SDK 21, target SDK 35, compile SDK 35.
- UI: Jetpack Compose (chrome/screens) + custom View canvas.
- Storage: Room (SQLite) for structured data + JSON files + bitmap files. Offline, no network permissions.
- Drawing: Android Canvas API on a custom View with Bitmap backing store. Max canvas 2048×2048.
- Package: `personal.notes.touch`.

## 2. Architecture pattern
MVVM + Repository:
- UI (Compose) → ViewModel (StateFlow) → Repository → Room DAO + File I/O.
- Coroutines + Flow for reactive data; Dispatchers.IO for disk.
- Hilt for DI (optional; if size budget tight, manual DI).

## 3. Package structure
```
personal/notes/touch/
├── TouchNotesApp.kt            (Application)
├── MainActivity.kt
├── data/
│   ├── db/
│   │   ├── AppDatabase.kt
│   │   ├── dao/ (NoteDao, FolderDao, PageDao, LabelDao, FlashCardDao, SettingsDao)
│   │   └── entity/ (NoteEntity, FolderEntity, PageEntity, LabelEntity, NoteLabelCrossRef, FlashCardEntity, FlashCardTypeEntity, FlashCardTagEntity, NoteLabelUsageEntity, SettingsEntity)
│   ├── repo/ (NoteRepository, FolderRepository, PageRepository, SettingsRepository, BackupRepository, ExportRepository)
│   └── files/ (NoteFileStore, BitmapStore)
├── domain/
│   └── model/ (Note, Folder, Page, Label, Stroke? no—bitmap-based)
├── ui/
│   ├── theme/ (Theme.kt, Color.kt, Type.kt)
│   ├── nav/ (TouchNotesNavHost.kt, Routes.kt)
│   ├── notes/ (NoteListScreen.kt, NoteListViewModel.kt)
│   ├── editor/ (TextEditorScreen.kt, TextEditorViewModel.kt)
│   ├── canvas/ (DrawingCanvasView.kt, CanvasScreen.kt, CanvasViewModel.kt, ToolBar.kt)
│   ├── settings/ (SettingsScreen.kt, SettingsViewModel.kt)
│   └── common/ (components)
└── util/ (Dispatchers, BitmapUtils, ZipUtils)
```

## 4. Database schema (Room)

### NoteEntity
- id: String (PK)
- title: String
- folderId: String? (FK FolderEntity.id)
- favorite: Boolean
- deleted: Boolean
- coverPath: String?
- createdAt: Long
- updatedAt: Long
- content: String? (rich text serialized)
- pageCount: Int

### FolderEntity
- id: String (PK)
- name: String
- parentId: String?
- createdAt: Long

### PageEntity
- id: String (PK)
- noteId: String (FK, index)
- index: Int
- bitmapPath: String
- updatedAt: Long

### LabelEntity
- id: String (PK)
- name: String
- color: Int

### NoteLabelCrossRef
- noteId: String (PK part)
- labelId: String (PK part)

### FlashCardEntity (mirrors FlashCardsContext)
- cAutoID: Long (PK auto)
- id: String (unique)
- cName: String
- cSync: Int
- createTime: String
- cQuestion: String?
- cAns: String?
- cTips: String?
- cNum: Int
- cRDate: String
- nid: String
- pid: String?
- isFavor: Int
- reviewStatus: Int
- card_usn: Int
- card_last_usn: Int
- note_usn: Int
- note_last_usn: Int

### FlashCardTypeEntity (mirrors FlashCardsType)
- lAutoID: Long (PK auto)
- tName: String
- sync: Int
- labelId: String
- usn: Int
- last_usn: Int

### FlashCardTagEntity (mirrors FlashCardsTags)
- tAutoID: Long (PK auto)
- id: String
- cardId: String
- labelId: String

### NoteLabelUsageEntity (mirrors NoteHasUsingLabels)
- nAutoID: Long (PK auto)
- id: String
- notePath: String
- labelId: String

### SettingsEntity (single row, id=1)
- id: Int (PK = 1)
- themeColor: Int
- darkMode: Int (0=system,1=light,2=dark)
- penDefaultsJson: String
- keepScreenOn: Boolean
- autoSaveIntervalMs: Int
- defaultBackground: Int

## 5. File storage layout (app-private)
```
files/
├── note/
│   ├── index/
│   │   └── (Room DB lives in databases/)
│   ├── <noteId>/
│   │   ├── page_0.png
│   │   └── page_1.png
│   └── thumbs/
├── backup/
└── temp/
```
Room DB path: `databases/touchnotes.db`. Settings also in Room (SettingsEntity). JSON index file optional; Room is source of truth. We may keep a `pathinfo.json`-equivalent export for debug only.

## 6. Key classes
- `TouchNotesApp`: Application; init Room + WorkManager? no.
- `AppDatabase`: RoomDatabase with entities and DAOs; version 1.
- `NoteRepository`: CRUD notes, folders; coordinates bitmap deletion.
- `PageRepository`: page CRUD, bitmap load/save, page reordering.
- `SettingsRepository`: DataStore/Room settings flow.
- `BackupRepository`: zip/unzip via SAF.
- `ExportRepository`: PNG/PDF export.
- `DrawingCanvasView`: custom View; holds `Bitmap` backing store (max 2048×2048), `Path` current stroke, `Paint`; undo stack `ArrayDeque<Bitmap>` cap 20; touch handling; eraser.
- `CanvasViewModel`: tool state (color, width, mode), page index, undo/redo availability.
- Compose screens + ViewModels.

## 7. Drawing implementation details
- Backing bitmap `Bitmap.createBitmap(w,h,ARGB_8888)` where w/h ≤ min(screen,2048). Use density-scaled default 1080×1920 capped 2048.
- Draw stroke with `canvas.drawPath(path, paint)` on bitmap via `Canvas(bitmap)`.
- Undo: push bitmap copy before each stroke (cap 20); redo stack cleared on new stroke.
- Eraser: `paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)`.
- Zoom/pan: `Matrix` on canvas draw; keep stroke coordinates in bitmap space.
- Memory: recycle bitmaps on page switch; `onLowMemory` clears undo stack.

## 8. Permissions (manifest)
- None network. Storage: none broad; use SAF (Storage Access Framework) for import/export/backup. `READ_EXTERNAL_STORAGE` not needed for SAF. Optionally `POST_NOTIFICATIONS` not needed. Keep manifest minimal.

## 9. Build config
- applicationId = personal.notes.touch
- minSdk = 21, targetSdk = 35, compileSdk = 36
- Kotlin 1.9.24 (kapt-compatible), Compose BOM, Room 2.6.1, Coroutines 1.8.1, Navigation Compose, DataStore.
- ProGuard/R8 for release; keep Room entities.
- No native libs; storage not limited (256 GB tablet storage; no APK size cap); RAM target <512MB (6 GB device RAM).

## 10. Testing strategy
- Unit: repositories, DAOs (Robolectric/Room in-memory).
- UI: Compose tests for note list.
- Manual: canvas perf on device; memory profiling.

## 11. Open risks
- Canvas performance at 2048×2048 on low-end devices; may need tile rendering (deferred).
- Room + file consistency on crash; use transactions + temp files.
- No reference screenshots; UI fidelity approximate.