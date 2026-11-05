//
// touchnotes_pdfium.c — thin JNI bridge over the bundled pdfium engine.
//
// Written for TouchNotes' vector PDF import (defect F-10): the canvas needs
// (1) page rendering at arbitrary resolution (sharp zoom, no raster
// upscaling) and (2) text extraction plus selection geometry for
// select/copy/mark/underline. Both are pdfium primitives. This bridge keeps
// only opaque document handles on the Kotlin side; document memory, page and
// text-page caches live here.
//
// Engine: pdfium chromium/8138 (pdfium.googlesource.com/pdfium, BSD-3-Clause /
// Apache-2.0), prebuilt binaries from bblanchon/pdfium-binaries (MIT). See
// app/third_party/PDFIUM-NOTICE.md and PDFIUM-LICENSE.txt.
//
// Threading: every entry point serializes on one process-wide mutex. pdfium
// document objects are not thread-safe; the Kotlin wrapper additionally
// confines calls per document.
//
#include <jni.h>
#include <malloc.h>
#include <math.h>
#include <pthread.h>
#include <string.h>
#include <android/bitmap.h>
#include <android/log.h>

#include "fpdf_text.h"
#include "fpdfview.h"

#define LOG_TAG "TouchnotesPdfium"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* Open documents are few at a time (canvas note + import loop + text cache). */
#define MAX_DOCS 16

typedef struct {
    int inUse;
    FPDF_DOCUMENT doc;
    void *buffer;      /* owned copy of the file bytes (pdfium keeps the pointer) */
    size_t bufferSize;
    FPDF_PAGE page;    /* lazily loaded, single-page cache */
    int pageIndex;     /* index the cached page belongs to, -1 = none */
    FPDF_TEXTPAGE textPage;
} DocEntry;

static DocEntry g_docs[MAX_DOCS];
static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
static int g_libraryReady = 0;

static void ensure_library(void) {
    if (!g_libraryReady) {
        FPDF_InitLibrary();
        g_libraryReady = 1;
    }
}

/* Caller must hold g_lock. Handle is slot index + 1; 0 means "no document". */
static DocEntry *entry_for(jlong handle) {
    if (handle <= 0 || handle > MAX_DOCS) return NULL;
    DocEntry *e = &g_docs[handle - 1];
    return e->inUse ? e : NULL;
}

/* Caller must hold g_lock. Closes the text page whenever the page changes. */
static FPDF_PAGE ensure_page(DocEntry *e, int pageIndex) {
    if (e->page != NULL && e->pageIndex == pageIndex) return e->page;
    if (e->textPage != NULL) {
        FPDFText_ClosePage(e->textPage);
        e->textPage = NULL;
    }
    if (e->page != NULL) {
        FPDF_ClosePage(e->page);
        e->page = NULL;
    }
    e->page = FPDF_LoadPage(e->doc, pageIndex);
    e->pageIndex = e->page != NULL ? pageIndex : -1;
    return e->page;
}

/* Caller must hold g_lock. */
static FPDF_TEXTPAGE ensure_text_page(DocEntry *e, int pageIndex) {
    if (!ensure_page(e, pageIndex)) return NULL;
    if (e->textPage == NULL) e->textPage = FPDFText_LoadPage(e->page);
    return e->textPage;
}

JNIEXPORT void JNICALL
Java_personal_notes_touch_util_PdfiumBridge_initLibrary(JNIEnv *env, jobject thiz) {
    (void) env;
    (void) thiz;
    pthread_mutex_lock(&g_lock);
    ensure_library();
    pthread_mutex_unlock(&g_lock);
}

JNIEXPORT jlong JNICALL
Java_personal_notes_touch_util_PdfiumBridge_openDocument(JNIEnv *env, jobject thiz,
                                                         jbyteArray data) {
    (void) thiz;
    if (data == NULL) return 0;
    jsize len = (*env)->GetArrayLength(env, data);
    if (len <= 0) return 0;

    /* pdfium keeps the buffer pointer alive for the document's lifetime, so
     * the bytes are copied into native memory owned by the entry. */
    void *buf = malloc((size_t) len);
    if (buf == NULL) return 0;
    (*env)->GetByteArrayRegion(env, data, 0, len, (jbyte *) buf);

    pthread_mutex_lock(&g_lock);
    ensure_library();
    int slot = -1;
    for (int i = 0; i < MAX_DOCS; i++) {
        if (!g_docs[i].inUse) {
            slot = i;
            break;
        }
    }
    if (slot < 0) {
        pthread_mutex_unlock(&g_lock);
        free(buf);
        LOGE("openDocument: document table full");
        return 0;
    }
    FPDF_DOCUMENT doc = FPDF_LoadMemDocument64(buf, (size_t) len, NULL);
    if (doc == NULL) {
        unsigned long err = FPDF_GetLastError();
        pthread_mutex_unlock(&g_lock);
        free(buf);
        LOGE("FPDF_LoadMemDocument64 failed (error %lu)", err);
        return 0;
    }
    DocEntry *e = &g_docs[slot];
    memset(e, 0, sizeof(*e));
    e->inUse = 1;
    e->doc = doc;
    e->buffer = buf;
    e->bufferSize = (size_t) len;
    e->pageIndex = -1;
    pthread_mutex_unlock(&g_lock);
    return (jlong) (slot + 1);
}

JNIEXPORT void JNICALL
Java_personal_notes_touch_util_PdfiumBridge_closeDocument(JNIEnv *env, jobject thiz,
                                                          jlong handle) {
    (void) env;
    (void) thiz;
    pthread_mutex_lock(&g_lock);
    DocEntry *e = entry_for(handle);
    if (e != NULL) {
        if (e->textPage != NULL) FPDFText_ClosePage(e->textPage);
        if (e->page != NULL) FPDF_ClosePage(e->page);
        if (e->doc != NULL) FPDF_CloseDocument(e->doc);
        if (e->buffer != NULL) free(e->buffer);
        memset(e, 0, sizeof(*e));
    }
    pthread_mutex_unlock(&g_lock);
}

JNIEXPORT jint JNICALL
Java_personal_notes_touch_util_PdfiumBridge_getPageCount(JNIEnv *env, jobject thiz,
                                                         jlong handle) {
    (void) env;
    (void) thiz;
    pthread_mutex_lock(&g_lock);
    DocEntry *e = entry_for(handle);
    jint count = e != NULL ? FPDF_GetPageCount(e->doc) : 0;
    pthread_mutex_unlock(&g_lock);
    return count;
}

JNIEXPORT jfloatArray JNICALL
Java_personal_notes_touch_util_PdfiumBridge_getPageSize(JNIEnv *env, jobject thiz,
                                                        jlong handle, jint pageIndex) {
    (void) thiz;
    jfloat values[2] = {0.0f, 0.0f};
    pthread_mutex_lock(&g_lock);
    DocEntry *e = entry_for(handle);
    if (e != NULL) {
        FPDF_PAGE page = ensure_page(e, pageIndex);
        if (page != NULL) {
            values[0] = FPDF_GetPageWidthF(page);
            values[1] = FPDF_GetPageHeightF(page);
        }
    }
    pthread_mutex_unlock(&g_lock);
    jfloatArray out = (*env)->NewFloatArray(env, 2);
    if (out != NULL) (*env)->SetFloatArrayRegion(env, out, 0, 2, values);
    return out;
}

/*
 * Renders [pageIndex] into [bitmap] (ARGB_8888) so that page point
 * (srcX, srcY) lands at the bitmap's top-left corner, at [pxPerPt] pixels per
 * PDF point. Point coordinates are pdfium user space: origin at the page's
 * BOTTOM-left, y growing upward. The whole bitmap is pre-filled white.
 *
 * One function covers both use cases:
 *  - full page:  srcX = srcY = 0, bitmap sized to pageW*pxPerPt × pageH*pxPerPt
 *  - view crop:  a bitmap the size of the visible rect, srcX/srcY = the
 *    visible rect's page point.
 */
JNIEXPORT jboolean JNICALL
Java_personal_notes_touch_util_PdfiumBridge_render(JNIEnv *env, jobject thiz,
                                                   jlong handle, jint pageIndex,
                                                   jobject bitmap,
                                                   jfloat srcX, jfloat srcY,
                                                   jfloat pxPerPt, jboolean renderAnnots) {
    (void) thiz;
    if (bitmap == NULL || pxPerPt <= 0.0f) return JNI_FALSE;
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return JNI_FALSE;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("render: bitmap must be ARGB_8888");
        return JNI_FALSE;
    }

    jboolean ok = JNI_FALSE;
    pthread_mutex_lock(&g_lock);
    DocEntry *e = entry_for(handle);
    FPDF_PAGE page = e != NULL ? ensure_page(e, pageIndex) : NULL;
    if (page != NULL) {
        void *pixels = NULL;
        if (AndroidBitmap_lockPixels(env, bitmap, &pixels) == ANDROID_BITMAP_RESULT_SUCCESS) {
            FPDF_BITMAP target = FPDFBitmap_CreateEx((int) info.width, (int) info.height,
                                                     FPDFBitmap_BGRA, pixels,
                                                     (int) info.stride);
            if (target != NULL) {
                FPDFBitmap_FillRect(target, 0, 0, (int) info.width, (int) info.height,
                                    0xFFFFFFFF);
                int startX = (int) lroundf(-srcX * pxPerPt);
                int startY = (int) lroundf(-srcY * pxPerPt);
                int sizeX = (int) lroundf(FPDF_GetPageWidthF(page) * pxPerPt);
                int sizeY = (int) lroundf(FPDF_GetPageHeightF(page) * pxPerPt);
                FPDF_RenderPageBitmap(target, page, startX, startY, sizeX, sizeY, 0,
                                      renderAnnots ? FPDF_ANNOT : 0);
                FPDFBitmap_Destroy(target);
                ok = JNI_TRUE;
            }
            AndroidBitmap_unlockPixels(env, bitmap);
        }
    }
    pthread_mutex_unlock(&g_lock);
    return ok;
}

JNIEXPORT jint JNICALL
Java_personal_notes_touch_util_PdfiumBridge_textCountChars(JNIEnv *env, jobject thiz,
                                                           jlong handle, jint pageIndex) {
    (void) env;
    (void) thiz;
    pthread_mutex_lock(&g_lock);
    DocEntry *e = entry_for(handle);
    FPDF_TEXTPAGE text = e != NULL ? ensure_text_page(e, pageIndex) : NULL;
    jint count = text != NULL ? FPDFText_CountChars(text) : 0;
    pthread_mutex_unlock(&g_lock);
    return count;
}

/*
 * Extracts [count] UCS-2 characters starting at [start]. Returns null on
 * failure. The trailing utf-16 terminator pdfium writes is dropped.
 */
JNIEXPORT jstring JNICALL
Java_personal_notes_touch_util_PdfiumBridge_textGetChars(JNIEnv *env, jobject thiz,
                                                         jlong handle, jint pageIndex,
                                                         jint start, jint count) {
    (void) thiz;
    if (count <= 0) return NULL;
    jstring result = NULL;

    pthread_mutex_lock(&g_lock);
    DocEntry *e = entry_for(handle);
    FPDF_TEXTPAGE text = e != NULL ? ensure_text_page(e, pageIndex) : NULL;
    if (text != NULL) {
        /* pdfium fills count UCS-2 values plus a terminating zero. */
        unsigned short *buffer = malloc(((size_t) count + 1) * sizeof(unsigned short));
        if (buffer != NULL) {
            int written = FPDFText_GetText(text, start, count, buffer);
            if (written > 0) {
                int chars = written - 1; /* exclude terminator */
                if (chars > 0) {
                    result = (*env)->NewString(env, (const jchar *) buffer, (jsize) chars);
                }
            }
            free(buffer);
        }
    }
    pthread_mutex_unlock(&g_lock);
    return result;
}

JNIEXPORT jint JNICALL
Java_personal_notes_touch_util_PdfiumBridge_textSelectionRectCount(JNIEnv *env, jobject thiz,
                                                                   jlong handle, jint pageIndex,
                                                                   jint start, jint count) {
    (void) env;
    (void) thiz;
    pthread_mutex_lock(&g_lock);
    DocEntry *e = entry_for(handle);
    FPDF_TEXTPAGE text = e != NULL ? ensure_text_page(e, pageIndex) : NULL;
    jint rects = text != NULL ? FPDFText_CountRects(text, start, count) : 0;
    pthread_mutex_unlock(&g_lock);
    return rects;
}

/*
 * Rect [rectIndex] of the selection group cached by
 * textSelectionRectCount: {left, top, right, bottom} in pdfium user space
 * (bottom-left origin, y up).
 */
JNIEXPORT jfloatArray JNICALL
Java_personal_notes_touch_util_PdfiumBridge_textSelectionRect(JNIEnv *env, jobject thiz,
                                                              jlong handle, jint pageIndex,
                                                              jint rectIndex) {
    (void) thiz;
    (void) pageIndex;
    jfloat values[4] = {0.0f, 0.0f, 0.0f, 0.0f};
    pthread_mutex_lock(&g_lock);
    DocEntry *e = entry_for(handle);
    if (e != NULL && e->textPage != NULL) {
        double left = 0, top = 0, right = 0, bottom = 0;
        if (FPDFText_GetRect(e->textPage, rectIndex, &left, &top, &right, &bottom)) {
            values[0] = (jfloat) left;
            values[1] = (jfloat) top;
            values[2] = (jfloat) right;
            values[3] = (jfloat) bottom;
        }
    }
    pthread_mutex_unlock(&g_lock);
    jfloatArray out = (*env)->NewFloatArray(env, 4);
    if (out != NULL) (*env)->SetFloatArrayRegion(env, out, 0, 4, values);
    return out;
}

/*
 * Character index at page point (x, y) in pdfium user space, -1 if none.
 * Tolerance controls how far off a glyph counts as a hit.
 */
JNIEXPORT jint JNICALL
Java_personal_notes_touch_util_PdfiumBridge_textCharIndexAtPos(JNIEnv *env, jobject thiz,
                                                               jlong handle, jint pageIndex,
                                                               jfloat x, jfloat y,
                                                               jfloat toleranceX,
                                                               jfloat toleranceY) {
    (void) env;
    (void) thiz;
    pthread_mutex_lock(&g_lock);
    DocEntry *e = entry_for(handle);
    FPDF_TEXTPAGE text = e != NULL ? ensure_text_page(e, pageIndex) : NULL;
    jint index = -1;
    if (text != NULL) {
        index = FPDFText_GetCharIndexAtPos(text, (double) x, (double) y,
                                           (double) toleranceX, (double) toleranceY);
    }
    pthread_mutex_unlock(&g_lock);
    return index;
}

/*
 * Character box of [charIndex]: {left, top, right, bottom} in pdfium user
 * space (bottom-left origin, y up).
 */
JNIEXPORT jfloatArray JNICALL
Java_personal_notes_touch_util_PdfiumBridge_textCharBox(JNIEnv *env, jobject thiz,
                                                        jlong handle, jint pageIndex,
                                                        jint charIndex) {
    (void) thiz;
    (void) pageIndex;
    jfloat values[4] = {0.0f, 0.0f, 0.0f, 0.0f};
    pthread_mutex_lock(&g_lock);
    DocEntry *e = entry_for(handle);
    if (e != NULL && e->textPage != NULL) {
        double left = 0, right = 0, bottom = 0, top = 0;
        if (FPDFText_GetCharBox(e->textPage, charIndex, &left, &right, &bottom, &top)) {
            values[0] = (jfloat) left;
            values[1] = (jfloat) top;
            values[2] = (jfloat) right;
            values[3] = (jfloat) bottom;
        }
    }
    pthread_mutex_unlock(&g_lock);
    jfloatArray out = (*env)->NewFloatArray(env, 4);
    if (out != NULL) (*env)->SetFloatArrayRegion(env, out, 0, 4, values);
    return out;
}
