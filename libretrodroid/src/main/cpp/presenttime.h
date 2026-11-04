// nx-mobile 2026-11-03: stamps the frame about to be swapped with the time it should reach the screen
// (EGL_ANDROID_presentation_time). SurfaceFlinger then shows it at the first refresh from that time, instead of the first
// refresh after its buffer happened to be queued: a sleep-timer game's frames stay on a steady slot whatever each step's
// run time. GL thread, between the draw and the swap.
#ifndef LIBRETRODROID_PRESENTTIME_H
#define LIBRETRODROID_PRESENTTIME_H

#include <chrono>

namespace libretrodroid {

class PresentTime {
public:
    // Stamps the current draw surface's next frame; false when the extension is missing, there is no current window
    // surface, or debug.nx.presenttime is "0" (A/B checks: adb shell setprop debug.nx.presenttime 0).
    static bool set(std::chrono::steady_clock::time_point when);
};

}

#endif
