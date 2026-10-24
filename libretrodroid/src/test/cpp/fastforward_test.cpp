// Host check for the fast-forward pacer, driven by an injected clock. From the repo root:
//   out=$(mktemp -d)/fastforward_test && clang++ -std=c++17 -fsanitize=address,undefined \
//     -Ilibretrodroid/src/main/cpp libretrodroid/src/test/cpp/fastforward_test.cpp \
//     libretrodroid/src/main/cpp/fastforward.cpp -o "$out" && "$out"
#include "fastforward.h"

#include <chrono>
#include <cstdio>

using namespace libretrodroid;
using std::chrono::microseconds;
using std::chrono::milliseconds;

static int failures = 0;

static void check(bool ok, const char* what) {
    if (!ok) { std::printf("FAIL %s\n", what); failures++; }
}

static TimePoint at(double ms) {
    return TimePoint(std::chrono::duration_cast<TimePoint::duration>(std::chrono::duration<double, std::milli>(1000.0 + ms)));
}

// Steps every stepMs for one second of wall time (as the display would call step()); total core frames run.
static unsigned runForOneSecond(double fps, unsigned cap, double stepMs, FastForwardPacer& pacer) {
    pacer.start(fps, cap, at(0));
    unsigned total = 0;
    for (double t = 0; t < 1000.0; t += stepMs) total += pacer.framesFor(at(t));
    return total;
}

int main() {
    {
        FastForwardPacer pacer;
        unsigned total = runForOneSecond(60.0, 4, 1000.0 / 60.0, pacer);
        std::printf("capHolds: %u frames\n", total);
        check(total >= 230 && total <= 250, "capHolds: 60 fps at 4x on 60 Hz runs ~240 frames a second");
        check(pacer.effectiveSpeed() > 3.8 && pacer.effectiveSpeed() <= 4.0, "capHolds: effective speed ~4");
    }
    {
        FastForwardPacer pacer;
        unsigned total = runForOneSecond(60.0, 4, 1000.0 / 120.0, pacer);
        std::printf("capHoldsAt120Hz: %u frames\n", total);
        check(total >= 230 && total <= 250, "capHoldsAt120Hz: a faster display doesn't raise the speed");
    }
    {
        // Each step returns at most the cap, also when the stall leaves 30 frames due.
        FastForwardPacer pacer;
        pacer.start(60.0, 4, at(0));
        unsigned maxStep = 0;
        double t = 0;
        for (int i = 0; i < 10; i++, t += 1000.0 / 60.0) maxStep = std::max(maxStep, pacer.framesFor(at(t)));
        t += 500.0;
        for (int i = 0; i < 60; i++, t += 1000.0 / 60.0) maxStep = std::max(maxStep, pacer.framesFor(at(t)));
        check(maxStep <= 4, "noBurstAfterStall: no step runs more than the cap");
        // And after the stall the clock was resynced: the next second runs at the cap, not above it.
        unsigned after = 0;
        for (double end = t + 1000.0; t < end; t += 1000.0 / 60.0) after += pacer.framesFor(at(t));
        check(after >= 230 && after <= 250, "noBurstAfterStall: back at the cap after the stall");
    }
    {
        FastForwardPacer pacer;
        pacer.start(60.0, 4, at(0));
        bool ok = true;
        for (double t = -50.0; t < 1000.0; t += 0.37) {
            if (static_cast<int>(t * 100) % 3 == 0) pacer.framesFor(at(t));
            if (pacer.sleepFor(at(t)) > microseconds(16700)) ok = false;
            if (pacer.sleepFor(at(t)) < microseconds(0)) ok = false;
        }
        check(ok, "sleepNeverOverAFrame: never sleeps past one content frame");
        // At 1x-like low caps on a slow cadence it still never asks for more than a frame.
        pacer.start(50.0, 2, at(2000));
        check(pacer.sleepFor(at(0)) <= microseconds(20000), "sleepNeverOverAFrame: before start, at most a PAL frame");
        // Ahead of the clock (a step found nothing due): sleep until the next frame; after frames ran, don't.
        pacer.start(60.0, 2, at(0));
        check(pacer.framesFor(at(0)) == 1, "sleep: the first step runs a frame");
        check(pacer.sleepFor(at(0)) == microseconds(0), "sleep: none after frames ran (the swap paces)");
        check(pacer.framesFor(at(1)) == 0, "sleep: 1 ms later nothing is due at 2x");
        auto ahead = pacer.sleepFor(at(1));
        check(ahead > microseconds(7000) && ahead < microseconds(7500), "sleep: ahead sleeps until the next frame");
        FastForwardPacer idle;
        check(idle.sleepFor(at(0)) == microseconds(0), "sleepNeverOverAFrame: inactive pacer doesn't sleep");
    }
    {
        FastForwardPacer pacer;
        unsigned total = runForOneSecond(50.0, 2, 1000.0 / 60.0, pacer);
        std::printf("pal: %u frames\n", total);
        check(total >= 95 && total <= 105, "pal: 50 fps at 2x runs ~100 frames a second");
    }
    {
        // A device too slow for the cap: steps every 50 ms run the cap each, and the speed reported is what ran.
        FastForwardPacer pacer;
        unsigned total = runForOneSecond(60.0, 4, 50.0, pacer);
        check(total <= 4 * 20, "slow: never more than the cap per step");
        double speed = pacer.effectiveSpeed();
        std::printf("slow: effective %.2f\n", speed);
        check(speed > 1.1 && speed < 1.6, "slow: effective speed follows what actually ran (~1.33)");
    }
    {
        FastForwardPacer pacer;
        check(!pacer.active(), "starts inactive");
        check(pacer.framesFor(at(0)) == 0, "inactive: no frames");
        pacer.start(60.0, 3, at(0));
        check(pacer.active(), "active after start");
        pacer.stop();
        check(!pacer.active(), "inactive after stop");
    }

    if (failures == 0) std::printf("fastforward_test: all checks passed\n");
    return failures == 0 ? 0 : 1;
}
