#include "presenttime.h"

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <cstdint>
#include <cstring>

#include "debugprop.h"
#include "log.h"

namespace libretrodroid {

namespace {

using PresentationTime = EGLBoolean (*)(EGLDisplay, EGLSurface, EGLnsecsANDROID);

PresentationTime presentationTime() {
    static const PresentationTime fn = [] {
        auto f = reinterpret_cast<PresentationTime>(eglGetProcAddress("eglPresentationTimeANDROID"));
        LOGI("Presentation time: %s", f ? "available" : "not available");
        return f;
    }();
    return fn;
}

}

bool PresentTime::set(std::chrono::steady_clock::time_point when) {
    static const bool disabled = [] {
        const bool off = debugPropertyOff("debug.nx.presenttime");
        if (off) LOGI("Presentation time: off (debug.nx.presenttime=0)");
        return off;
    }();
    if (disabled) return false;
    PresentationTime fn = presentationTime();
    if (fn == nullptr) return false;
    EGLDisplay display = eglGetCurrentDisplay();
    EGLSurface surface = eglGetCurrentSurface(EGL_DRAW);
    if (display == EGL_NO_DISPLAY || surface == EGL_NO_SURFACE) return false;
    // steady_clock is CLOCK_MONOTONIC on Android, the clock the extension takes.
    const auto ns = std::chrono::duration_cast<std::chrono::nanoseconds>(when.time_since_epoch()).count();
    return fn(display, surface, static_cast<EGLnsecsANDROID>(ns)) == EGL_TRUE;
}

void PresentTime::clear() {
    PresentationTime fn = presentationTime();
    if (fn == nullptr) return;
    EGLDisplay display = eglGetCurrentDisplay();
    EGLSurface surface = eglGetCurrentSurface(EGL_DRAW);
    if (display == EGL_NO_DISPLAY || surface == EGL_NO_SURFACE) return;
    fn(display, surface, static_cast<EGLnsecsANDROID>(INT64_MIN));   // NATIVE_WINDOW_TIMESTAMP_AUTO
}

}
