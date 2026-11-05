# Third-party: pdfium (PDF rendering engine)

Simple Notes bundles **pdfium** to import PDFs as vectors (crisp zoom,
selectable text) — defect F-10.

| | |
|---|---|
| Engine | [pdfium](https://pdfium.googlesource.com/pdfium/) |
| Version | chromium/8138 |
| Engine licence | BSD-3-Clause / Apache-2.0 |
| Binary distribution | [bblanchon/pdfium-binaries](https://github.com/bblanchon/pdfium-binaries) (MIT) |
| Shipped ABIs | `arm64-v8a`, `armeabi-v7a` (`app/src/main/jniLibs/<abi>/libpdfium.so`) |

Only the **unmodified prebuilt engine binaries** are included. All Android
plumbing (`app/src/main/cpp/touchnotes_pdfium.c`) is original code written
for Simple Notes; no code from the original TouchNotes app or from third
parties was copied. Full licence texts:

- Prebuilt distribution licence: `PDFIUM-LICENSE.txt` (bblanchon, MIT).
- Engine: BSD-3-Clause (Chromium) + Apache-2.0 components; see
  `https://pdfium.googlesource.com/pdfium/+/refs/heads/main/LICENSE`.
