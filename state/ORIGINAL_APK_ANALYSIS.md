# Original TouchNotes APK Analysis — SiYuan Feature Gap Analysis

## App Identity
- **Package**: com.newskyer.draw (Newskyer Draw / TouchNotes)
- **Version**: 7.0.4
- **Platform**: Android tablet (Xiaomi Pad 5, 10.9")
- **Permissions**: 40+ (network, camera, location, storage, phone state, etc.)
- **Architecture**: Java/Kotlin with obfuscated smali (ProGuard/R8), native libs (arm64-v8a)
- **UI Framework**: Mix of XML layouts and Jetpack Compose (ComposeView inside NoteRichText)

## Original App's Text Editing Capabilities

### Rich Text Editor (NoteRichText + EditRichTextLayout)
The original app uses a hybrid approach:
- **Native Android text rendering** with a custom `NoteRichText` view
- **ComposeView overlay** for modern UI elements (toolbar, dialogs)
- **Text menu** (text_menu_layout_new.xml) with:
  - Font size control (increase/decrease)
  - Bold, italic, underline, strikethrough
  - Font color, font under color
  - Text alignment (left/center/right)
  - Line spacing
  - Handwriting mode toggle
  - Marks/bookmarks
  - Keyboard input / handwriting input toggle
  - Text delete, text return
  - Font family selection

### Inline Formatting Support
From the text menu layout:
- `**bold**` → bold button
- `*italic*` → italic button
- `_underline_` → underline button
- `~~strikethrough~~` → strikethrough button
- Font color picker
- Text alignment
- Line spacing

### Flashcards (SM-2 Spaced Repetition)
- Dedicated `FlashCardActivity`
- Card labels/tags
- Star/favorite cards
- Filter by tag
- "Block Answer" mode (hide answer until tap)
- Mastery tracking ("Master this card and review it in stages")
- Stage test (requires 50+ cards)
- Reset staged test

### Document/Note Management
- Notebook folders (BookshelfActivity)
- Note detail view (NoteDetail)
- Settings (SettingsActivity)
- Cloud sync (Baidu, Huawei, etc.)
- PDF import/export
- Doc scan (DocScanActivity)
- Skills system

### What the Original App Does NOT Have (SiYuan-style)
1. **Block-based editor** — original uses continuous rich text, not discrete blocks
2. **Block references** (`((blockId))`) — not present
3. **Wiki links** (`[[Note Title]]`) — not present
4. **Multi-document workspace** (split panes) — not present
5. **Embedded query blocks** — not present
6. **Knowledge graph view** — not present
7. **Notebook hierarchy** (folders exist but flat) — original has folders but no nested tree
8. **Block-level flashcards** (cards from blocks) — original has cards but not linked to note blocks
9. **Tag index** (content-derived tags) — original has card tags but not note content tags
10. **Block backlinks** — not present

## Our Clone's SiYuan Feature Status

### Implemented (beyond original)
| Feature | Clone Status | Original Status |
|---------|-------------|-----------------|
| Block editor (10 types) | ✅ Full | ❌ Not present |
| WYSIWYG inline formatting | ✅ Full | ⚠️ Rich text only |
| Block references (`((...))`) | ✅ Full | ❌ Not present |
| Block backlinks | ✅ Full | ❌ Not present |
| Wiki links (`[[...]]`) | ✅ Full | ❌ Not present |
| Multi-document split | ✅ Full | ❌ Not present |
| Notebook hierarchy | ✅ Full | ⚠️ Flat folders |
| Tag index + search | ✅ Full | ❌ Not present |
| Flashcard bridge (from blocks) | ✅ Full | ❌ Not present |
| Daily notes + templates | ✅ Full | ❌ Not present |
| Cross-doc block transfer | ✅ Full | ❌ Not present |
| Embedded query blocks | ✅ Full | ❌ Not present |
| Markdown/HTML/PDF export | ✅ Full | ⚠️ Basic export |
| Heading folding | ✅ Full | ❌ Not present |
| Table cell editing | ✅ Full | ❌ Not present |
| Drag-to-reorder | ✅ Full | ❌ Not present |
| Graph view | ⚠️ Pure layer only | ❌ Not present |

### Parity with original (still needed)
| Feature | Clone Status | Original Status |
|---------|-------------|-----------------|
| Drawing canvas + pressure | ✅ Full | ✅ Full |
| PDF import (raster) | ✅ Full | ✅ Full |
| PDF import (vector) | ✅ Full (pdfium) | ✅ Full |
| Flashcards (SM-2) | ✅ Full | ✅ Full |
| Offline-only | ✅ Full | ❌ (has cloud sync) |
| Zero permissions | ✅ Full | ❌ (40+ perms) |
| Tablet-optimized layout | ✅ Full | ✅ Full |

### Key Insight
The original TouchNotes is a **drawing-first** app with rich text as a secondary feature. Our clone is evolving toward a **knowledge-base-first** app (SiYuan-style) that still supports drawing. The SiYuan features we've added go significantly beyond what the original app offers, making our clone a superset in the knowledge management dimension while maintaining parity in the drawing/note-taking dimension.

### Permissions Comparison
| Permission | Original | Clone |
|-----------|----------|-------|
| INTERNET | ✅ | ❌ |
| CAMERA | ✅ | ❌ |
| LOCATION | ✅ | ❌ |
| STORAGE | ✅ | ❌ |
| PHONE_STATE | ✅ | ❌ |
| RECORD_AUDIO | ✅ | ❌ |
| Total | 40+ | 0 (source manifest) |

The clone maintains its clean-room, zero-permission constraint while adding knowledge base features the original doesn't have.