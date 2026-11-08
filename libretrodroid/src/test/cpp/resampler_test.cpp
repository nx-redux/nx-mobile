// Host check for the resamplers' edge cases (pure, no Oboe). From the repo root:
//   out=$(mktemp -d)/resampler_test && clang++ -std=c++17 -fsanitize=address,undefined \
//     -Ilibretrodroid/src/main/cpp/resamplers libretrodroid/src/test/cpp/resampler_test.cpp \
//     libretrodroid/src/main/cpp/resamplers/linearresampler.cpp \
//     libretrodroid/src/main/cpp/resamplers/sincresampler.cpp -o "$out" && "$out"
#include "linearresampler.h"
#include "sincresampler.h"

#include <cstdio>
#include <vector>

using namespace libretrodroid;

static int failures = 0;

static void check(bool ok, const char* what) {
    if (!ok) { std::printf("FAIL %s\n", what); failures++; }
}

static bool allEqual(const std::vector<int16_t>& v, int16_t value) {
    for (auto s : v) if (s != value) return false;
    return true;
}

int main() {
    LinearResampler linear;
    SincResampler sinc(8);

    // Speed 0: no input frames. Must not touch source (nullptr here) and must output silence.
    std::vector<int16_t> sink(200, 77);
    linear.resample(nullptr, 0, sink.data(), 100);
    check(allEqual(sink, 0), "linear: no input gives silence");

    sink.assign(200, 77);
    sinc.resample(nullptr, 0, sink.data(), 100);
    check(allEqual(sink, 0), "sinc: no input gives silence");

    sink.assign(200, 77);
    linear.resample(nullptr, -3, sink.data(), 100);
    check(allEqual(sink, 0), "linear: negative input gives silence");

    // No output requested: nothing written.
    sink.assign(200, 77);
    int16_t one[2] = { 100, -100 };
    linear.resample(one, 1, sink.data(), 0);
    check(allEqual(sink, 77), "linear: zero sink frames writes nothing");

    // A single input frame is held across the output.
    sink.assign(8, 0);
    linear.resample(one, 1, sink.data(), 4);
    for (int i = 0; i < 4; i++) {
        check(sink[2 * i] == 100 && sink[2 * i + 1] == -100, "linear: one frame held");
    }

    // Two frames upsampled to four start on the first frame.
    int16_t two[4] = { 100, -100, 200, -200 };
    sink.assign(8, 0);
    linear.resample(two, 2, sink.data(), 4);
    check(sink[0] == 100 && sink[1] == -100, "linear: first output frame is the first input frame");

    if (failures == 0) std::printf("resampler_test: all checks passed\n");
    return failures == 0 ? 0 : 1;
}
