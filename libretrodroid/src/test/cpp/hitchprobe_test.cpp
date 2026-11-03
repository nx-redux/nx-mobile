// Host check for the frame hitch probe, driven by an injected clock. From the repo root:
//   out=$(mktemp -d)/hitchprobe_test && clang++ -std=c++17 -fsanitize=address,undefined \
//     -Ilibretrodroid/src/main/cpp libretrodroid/src/test/cpp/hitchprobe_test.cpp -o "$out" && "$out"
#include "hitchprobe.h"

#include <cmath>
#include <cstdio>

using namespace libretrodroid;

static int failures = 0;

static void check(bool ok, const char* what) {
    if (!ok) { std::printf("FAIL %s\n", what); failures++; }
}

static bool near(double a, double b) { return std::abs(a - b) < 0.01; }

static HitchProbe::Clock::time_point at(double ms) {
    return HitchProbe::Clock::time_point(
        std::chrono::duration_cast<HitchProbe::Clock::duration>(std::chrono::duration<double, std::milli>(1000.0 + ms)));
}

// One step starting at start: core run, our draw, the pacing sleep; the swap is the time until the next step.
static std::optional<HitchProbe::Report> step(HitchProbe& p, double start, double run, double render, double wait, unsigned frames = 1) {
    auto r = p.begin(at(start), 16.683);
    p.ranCore(at(start + run), frames);
    p.rendered(at(start + run + render));
    p.waited(at(start + run + render + wait));
    return r;
}

int main() {
    HitchProbe p;

    // The first step has no previous one to measure.
    check(!step(p, 0, 5, 1, 9).has_value(), "first step reports nothing");
    // An on-time step (16.7 ms after the last): nothing.
    check(!step(p, 16.7, 5, 1, 9).has_value(), "on-time step reports nothing");

    // The next step starts 33.4 ms later: the previous step is a hitch. That step took 5 + 1 + 9 = 15 ms inside, so
    // 18.4 ms went outside the step (the swap).
    auto r = step(p, 50.1, 20, 1, 0, 2);
    check(r.has_value(), "late step is reported");
    if (r) {
        check(near(r->periodMs, 33.4), "period");
        check(near(r->runMs, 5), "run");
        check(near(r->renderMs, 1), "render");
        check(near(r->waitMs, 9), "wait");
        check(near(r->outsideMs, 18.4), "outside the step");
        check(r->frames == 1, "frames of the reported step");
    }

    // The slow step (a 20 ms core run, 2 frames) is reported when the step after it starts late.
    r = step(p, 90.0, 5, 1, 9);
    check(r.has_value(), "slow core step is reported");
    if (r) {
        check(near(r->runMs, 20), "slow run");
        check(r->frames == 2, "two frames run");
    }

    // A pause (no steps for a while, then reset) never counts as a hitch.
    p.reset();
    check(!step(p, 5000, 5, 1, 9).has_value(), "after reset nothing is reported");
    check(!step(p, 5016.7, 5, 1, 9).has_value(), "and the next on-time step neither");

    // Counts for the summary.
    check(p.steps() == 6, "steps counted");
    check(p.hitches() == 2, "hitches counted");

    {
        // The summary's work figures: each step's core run per frame, averaged and the worst, since the last take.
        HitchProbe w;
        step(w, 0, 10, 1, 5);          // 10 ms, one frame
        step(w, 16.7, 12, 1, 3);       // 12 ms
        step(w, 33.4, 30, 1, 0, 2);    // 30 ms over two frames: 15 ms a frame
        auto s = w.takeRunStats();
        check(s.frames == 4, "frames counted");
        check(near(s.averageMs, (10 + 12 + 30) / 4.0), "average run per frame");
        check(near(s.worstMs, 15), "worst run per frame");
        check(w.takeRunStats().frames == 0, "a take starts over");
    }

    if (failures == 0) std::printf("hitchprobe_test: all passed\n");
    return failures == 0 ? 0 : 1;
}
