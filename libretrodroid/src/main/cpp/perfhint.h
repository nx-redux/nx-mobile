// nx-mobile 2026-11-03: Android's performance hint session (ADPF, Android 13+): the game's busy threads report each
// step's work against the frame budget, so the system raises clocks before a frame runs late and keeps those threads on
// the big cores. The NDK calls are looked up at runtime (minSdk 26), so older Android just runs without it.
// GL thread only.
#ifndef LIBRETRODROID_PERFHINT_H
#define LIBRETRODROID_PERFHINT_H

#include <cstdint>
#include <vector>

namespace libretrodroid {

class PerfHint {
public:
    ~PerfHint() { close(); }

    // Opens a session for these threads with this per-step budget. False when the system has none (Android 12 or
    // older, or a device without ADPF), or when debug.nx.adpf is "0" (A/B checks: adb shell setprop debug.nx.adpf 0).
    bool open(const std::vector<int>& threads, int64_t targetNanos);
    bool isOpen() const { return session != nullptr; }
    void report(int64_t actualNanos);
    void updateTarget(int64_t targetNanos);
    void close();

private:
    void* session = nullptr;
    int64_t target = 0;
};

}

#endif
