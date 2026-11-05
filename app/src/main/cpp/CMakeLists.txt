cmake_minimum_required(VERSION 3.22.1)
project(touchnotes_pdfium C)

# pdfium engine is linked as a prebuilt shared library, never rebuilt here:
# chromium/8138 revision of https://pdfium.googlesource.com/pdfium/ packaged by
# https://github.com/bblanchon/pdfium-binaries (MIT distribution license in
# app/third_party/PDFIUM-LICENSE.txt; engine itself is BSD-3-Clause/Apache-2.0).
# One .so per shipped ABI lives in src/main/jniLibs/<abi>/libpdfium.so.
add_library(pdfium SHARED IMPORTED)
set_target_properties(pdfium PROPERTIES
        IMPORTED_LOCATION "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libpdfium.so")

# Thin JNI bridge (rendering to Android bitmaps + text/selection geometry).
add_library(touchnotes_pdfium SHARED touchnotes_pdfium.c)

target_include_directories(touchnotes_pdfium PRIVATE
        "${CMAKE_SOURCE_DIR}/pdfium/include")

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)
target_link_libraries(touchnotes_pdfium
        ${log-lib}
        ${jnigraphics-lib}   # AndroidBitmap_* (rendering into Bitmaps)
        android
        pdfium)
