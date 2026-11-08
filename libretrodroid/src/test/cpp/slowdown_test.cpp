// Host check for the Frame Skip Off speed meter. From the repo root:
//   out=$(mktemp -d)/slowdown_test && clang++ -std=c++17 -fsanitize=address,undefined \
//     -Ilibretrodroid/src/main/cpp libretrodroid/src/test/cpp/slowdown_test.cpp -o "$out" && "$out"
#include "slowdown.h"

#include <cmath>
#include <cstdio>

using namespace libretrodroid;

static int failures = 0;

static void check(bool ok, const char* what) {
    if (!ok) { std::printf("FAIL %s\n", what); failures++; }
}

static SlowdownMeter::Clock::time_point at(double ms) {
    return SlowdownMeter::Clock::time_point(std::chrono::duration_cast<SlowdownMeter::Clock::duration>(
        std::chrono::duration<double, std::milli>(1000.0 + ms)));
}

// One frame per step, a step every stepMs, for seconds: the speed the meter ends on.
static double run(SlowdownMeter& m, double& t, double stepMs, double seconds, double fps = 60.0) {
    double s = m.speed();
    for (double end = t + seconds * 1000.0; t < end; t += stepMs) s = m.add(at(t), 1, fps);
    return s;
}

int main() {
    {
        // Full speed: a frame every 1/60 s stays at 1.
        SlowdownMeter m;
        double t = 0;
        check(std::fabs(run(m, t, 1000.0 / 60.0, 3) - 1.0) < 0.01, "full speed stays 1");
    }
    {
        // 95%: settles at 0.95 within a couple of seconds.
        SlowdownMeter m;
        double t = 0;
        const double s = run(m, t, 1000.0 / 57.0, 3);
        check(std::fabs(s - 0.95) < 0.01, "95% settles at 0.95");
    }
    {
        // Back to full speed after a slow spell: recovers to ~1 again.
        SlowdownMeter m;
        double t = 0;
        run(m, t, 1000.0 / 54.0, 2);
        check(run(m, t, 1000.0 / 60.0, 3) > 0.99, "recovers after slowdown");
    }
    {
        // Faster than real time (the pacer catching up a step) never plays faster than 1.
        SlowdownMeter m;
        double t = 0;
        check(run(m, t, 1000.0 / 70.0, 2) <= 1.0, "never above 1");
    }
    {
        // A crawl is held at the floor.
        SlowdownMeter m;
        double t = 0;
        check(std::fabs(run(m, t, 1000.0 / 20.0, 5) - SlowdownMeter::MIN_SPEED) < 0.01, "held at the floor");
    }
    {
        // Reset: full speed again, and the next step only starts a window.
        SlowdownMeter m;
        double t = 0;
        run(m, t, 1000.0 / 45.0, 2);
        m.reset();
        check(m.speed() == 1.0, "reset is full speed");
        check(m.add(at(t), 1, 60.0) == 1.0, "first step after reset starts a window");
    }
    if (failures == 0) std::printf("slowdown_test: all passed\n");
    return failures == 0 ? 0 : 1;
}
