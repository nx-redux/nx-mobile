#include "presenttime.h"

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <cstring>
#include <sys/system_properties.h>

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

bool disabledByProperty() {
    char value[PROP_VALUE_MAX] = {};
    return __system_property_get("debug.nx.presenttime", value) > 0 && std::strcmp(value, "0") == 0;
}

}

bool PresentTime::set(std::chrono::steady_clock::time_point when) {
    static const bool disabled = [] {
        const bool off = disabledByProperty();
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

}
