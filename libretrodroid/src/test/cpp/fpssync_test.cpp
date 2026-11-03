// Host check for FPSSync's sleep-timer pacing (a game whose rate is not the screen's), driven by an injected clock.
// From the repo root:
//   out=$(mktemp -d)/fpssync_test && clang++ -std=c++17 -fsanitize=address,undefined \
//     -Ilibretrodroid/src/main/cpp -Ilibretrodroid/src/test/cpp/shim libretrodroid/src/test/cpp/fpssync_test.cpp \
//     libretrodroid/src/main/cpp/fpssync.cpp libretrodroid/src/main/cpp/logflag.cpp -o "$out" && "$out"
#include "fpssync.h"

#include <cstdio>

using namespace libretrodroid;

static int failures = 0;

static void check(bool ok, const char* what) {
    if (!ok) { std::printf("FAIL %s\n", what); failures++; }
}

static TimePoint at(double ms) {
    return TimePoint(std::chrono::duration_cast<TimePoint::duration>(std::chrono::duration<double, std::milli>(1000.0 + ms)));
}

static double ms(TimePoint t) { return std::chrono::duration<double, std::milli>(t - at(0)).count(); }

int main() {
    // A 60 fps game on a 120 Hz screen: the sleep timer, one frame every 16.667 ms.
    const double frame = 1000.0 / 60.0;

    {
        FPSSync sync(60.0, 120.0);
        check(sync.advanceFrames(at(0)) == 1, "first step runs one frame");
        check(sync.deadline() >= at(frame - 0.01) && sync.deadline() <= at(frame + 0.01), "first deadline one frame on");

        // On time (the step took 15 ms, then slept to its deadline): one frame, the deadline moves a frame.
        check(sync.advanceFrames(at(frame)) == 1, "on-time step runs one frame");

        // The next step ends a whole frame late (a slow 33 ms frame): it still runs one frame, shown late, and the
        // clock stays behind so the following steps skip their sleep. Two frames at once would skip one.
        check(sync.advanceFrames(at(2 * frame + frame + 2)) == 1, "a frame late still runs one frame");
        check(sync.deadline() < at(2 * frame + frame + 2), "the clock stays behind: no sleep");

        // Two frames behind still shows every frame.
        check(sync.advanceFrames(at(5 * frame + 1)) == 1, "two frames late runs one frame");
    }

    {
        // A device that cannot keep up: past two frames of lag it runs frames back to back to hold the game's speed.
        FPSSync sync(60.0, 120.0);
        sync.advanceFrames(at(0));
        check(sync.advanceFrames(at(4 * frame + 1)) >= 2, "three frames late catches up");
        check(ms(sync.deadline()) >= 4 * frame - 0.1, "and the clock catches up with it");
    }

    {
        // Vsync pacing (the game's rate is the screen's) never sleeps or catches up: the swap paces it.
        FPSSync sync(59.94, 60.0);
        check(sync.advanceFrames(at(0)) == 1, "vsync runs one frame");
        check(sync.advanceFrames(at(100)) == 1, "vsync never catches up");
    }

    {
        // The sleep timer stamps each frame to be shown one frame after its deadline, so the display picks a steady slot
        // whatever the step's run time; vsync pacing (the swap paces it) and a step before the first deadline stamp nothing.
        FPSSync sync(60.0, 90.0);
        check(!sync.presentAt().has_value(), "no stamp before the first step");
        sync.advanceFrames(at(0));
        auto stamp = sync.presentAt();
        check(stamp.has_value() && ms(*stamp) > 2 * frame - 0.1 && ms(*stamp) < 2 * frame + 0.1, "deadline + one frame");
        sync.advanceFrames(at(frame));
        auto next = sync.presentAt();
        check(next.has_value() && ms(*next) - ms(*stamp) > frame - 0.1 && ms(*next) - ms(*stamp) < frame + 0.1, "stamps a frame apart");

        FPSSync vsync(59.94, 60.0);
        vsync.advanceFrames(at(0));
        check(!vsync.presentAt().has_value(), "vsync pacing stamps nothing");
    }

    if (failures == 0) std::printf("fpssync_test: all passed\n");
    return failures == 0 ? 0 : 1;
}
