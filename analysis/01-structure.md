# Phase 1 — APK Structure

**APK:** `/home/lk1/craft_apps/simple_notes/touchnotes-7-0-4.apk`
**Size:** 499,256,591 bytes (~476.2 MiB / 477 MB)
**Entries:** 7,084 (per `unzip -l`)
**Tooling:** `unzip -Z1`, `aapt2`, apktool (shell, static). Evidence boundary: shell tools (no Craft Anything ev_ ids).

## Top-level layout
```
AndroidManifest.xml
classes.dex                 (single DEX, ~108 MB)
resources.arsc              (~6.0 MB)
res/                       (6,302 entries)
assets/                    (429 entries)
lib/arm64-v8a/             (60 .so native libs)
META-INF/                  (197 entries: signing + module metadata)
kotlin/, com/, org/, okhttp3/, fabric/, assets/...
*.properties (SDK version markers)
```

## DEX files
- Count: **1** (`classes.dex`, 108,031,028 bytes in extracted tree).
- No `classes2.dex` etc. (multidex version marker present: `androidsupportmultidexversion.txt`, but only one DEX shipped). All code in one DEX → large; decompilation (Phase 2) will be heavy.

## Native libraries
- ABI: `arm64-v8a` only (native-code: arm64-v8a per badging). No x86/x86_64/arm-v7a.
- Count: **60** `.so` files under `lib/arm64-v8a/`.
- Notable groups:
  - Drawing/rendering: `libdraw_811.so`, `libdraw_c.so`, `libpanel.so`, `libinkkit_ocr_jni.so`
  - ML/AI/OCR: `libpaddle_lite_jni.so`, `libpaddle_light_api_shared.so`, `libonnxruntime*.so`, `libncnn.so`, `libopencv_java4.so`, `libsherpa-ncnn-*`, `libsherpa-onnx-jni.so`, `libkaldi-native-fbank-core.so`, `libspokestack-android.so`, `libformula_recognizer.so`, `liblatex_ocr_jni.so`, `libestimate_interface.so`, `libtrack_prediction.so`, `libforecast.so`
  - PDF: `libpdfCore.so`, `libpdfsdk.so`
  - Media/codec: `libavmdl_lite.so`, `libttmplayer_lite.so`, `libods.so`, `libzstd-jni-1.5.2-3.so`
  - Graphics path: `libandroidx.graphics.path.so`, `libglslang.so`, `libSPIRV.so`
  - Security/packer: `libijiami_1011.so`, `libpanglearmor.so`, `libpangleflipped.so`, `libscorpion.so`, `libscorpion_bin.so`, `libsgcore.so`, `libtobEmbedEncrypt.so`, `libxcrash*.so`, `libcrashsdk.so`, `libti-monitor.so`, `libumeng-spy.so`, `libopluslog.so`
  - Huawei AI: `libhiai.so`, `libhiai_ir.so`, `libhiai_ir_build.so`
  - JNI bridges: `libestimate_interface.so`, `libbrushstroke_interface` (referenced as uses-library), `librecognize_interface.touch.so`, `libtflite_interface_wrap.touch.so`

## Resources
- `resources.arsc` ~6.0 MB; `res/` 6,302 entries (layouts, drawables, values, anim, etc.).
- Locales: 80+ (incl. zh-CN, zh-HK, zh-TW, ja, ko, es, etc.).
- Densities: 120/160/240/320/480/640/65534.

## Assets
- 429 entries (fonts, models, templates, etc.) — heavy asset payload contributes to APK size.

## META-INF
- 197 entries: signing block (CICOE.SF/RSA/MANIFEST.MF), plus per-module `*.kotlin_module` and `androidx.*.version` markers.
- Signature scheme: v1 (JAR) + likely v2/v3 (not verified here; unknown). Search boundary: `unzip` listing only.

## ABI / device targeting
- Only `arm64-v8a` → runs on 64-bit ARM devices only. Tablet must be arm64. (Evidence: aapt2 badging `native-code: arm64-v8a`.)

## Multidex / DEX notes
- Single DEX despite ~108 MB; method count unknown without dexdump. R8/ProGuard clearly applied (obfuscated names). Decompilation feasibility for Phase 2 to be assessed with jadx/cfr.

## Unknowns
- Exact signing schemes and cert fingerprints: not extracted (use `apksigner verify` if available; not run).
- Whether additional DEX hidden in assets: not checked (search boundary: top-level listing only).
- Native lib dependencies/SONAME: not inspected (readelf not run).
