// Host check for the mic feed (source, core state, live fallback, synthetic samples). From the repo root:
//   out=$(mktemp -d)/micfeed_test && clang++ -std=c++17 -fsanitize=address,undefined \
//     -Ilibretrodroid/src/main/cpp libretrodroid/src/test/cpp/micfeed_test.cpp \
//     libretrodroid/src/main/cpp/microphone/micfeed.cpp -o "$out" && "$out"
#include "microphone/micfeed.h"

#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <vector>

using namespace libretrodroid;

static int failures = 0;

static void check(bool ok, const char* what) {
    if (!ok) { std::printf("FAIL %s\n", what); failures++; }
}

static void silenceIsZeros() {
    MicFeed feed;
    feed.setSource(MicSource::SILENCE);
    feed.setCoreRunning(true);
    std::vector<int16_t> out(512, 1234);
    feed.fillSynthetic(out.data(), (int) out.size());
    bool allZero = true;
    for (int16_t s : out) allZero = allZero && s == 0;
    check(allZero, "silenceIsZeros: 512 samples all 0");
}

static void noiseIsLoud() {
    MicFeed feed;
    feed.setSource(MicSource::NOISE);
    feed.setCoreRunning(true);
    std::vector<int16_t> a(4096), b(4096);
    feed.fillSynthetic(a.data(), (int) a.size());
    feed.fillSynthetic(b.data(), (int) b.size());
    double sum = 0;
    bool inPeak = true;
    for (int16_t s : a) {
        sum += (double) s * s;
        inPeak = inPeak && std::abs((int) s) <= MIC_NOISE_PEAK;
    }
    double rms = std::sqrt(sum / a.size());
    std::printf("noise rms %.0f\n", rms);
    check(rms > 3000, "noiseIsLoud: RMS over 3000");
    check(inPeak, "noiseIsLoud: every sample within MIC_NOISE_PEAK");
    check(a != b, "noiseIsLoud: two calls differ");
}

static void wantsLiveNeedsRunning() {
    MicFeed feed;
    feed.setSource(MicSource::LIVE);
    check(!feed.wantsLive(), "wantsLiveNeedsRunning: not while the core has the mic stopped");
    check(feed.effective() == MicSource::SILENCE, "wantsLiveNeedsRunning: stopped is silence");
    feed.setCoreRunning(true);
    check(feed.wantsLive(), "wantsLiveNeedsRunning: live once running");
    check(feed.effective() == MicSource::LIVE, "wantsLiveNeedsRunning: effective live");
}

static void liveFailureFallsBackToNoise() {
    MicFeed feed;
    feed.setSource(MicSource::LIVE);
    feed.setCoreRunning(true);
    feed.onLiveFailed();
    check(!feed.wantsLive(), "liveFailure: no retry while the source stays live");
    check(feed.effective() == MicSource::NOISE, "liveFailure: noise instead");
    feed.setSource(MicSource::LIVE);
    check(!feed.wantsLive(), "liveFailure: same source again keeps the failure");
    feed.setSource(MicSource::NOISE);
    feed.setSource(MicSource::LIVE);
    check(feed.wantsLive(), "liveFailure: a source change clears it");
}

static void leavingLiveClosesWithoutRead() {
    int stops = 0;
    MicFeed feed([&stops] { stops++; });
    feed.setCoreRunning(true);
    feed.setSource(MicSource::LIVE);
    check(stops == 0, "leavingLive: entering live doesn't stop");
    feed.setSource(MicSource::SILENCE);
    check(stops == 1, "leavingLive: leaving live stops at once");
    feed.setSource(MicSource::NOISE);
    check(stops == 1, "leavingLive: non-live changes don't stop");
    feed.setSource(MicSource::LIVE);
    feed.setCoreRunning(false);
    check(stops == 2, "leavingLive: core stopping the mic while live stops");
    feed.setCoreRunning(false);
    check(stops == 2, "leavingLive: a repeated stop doesn't fire again");
    feed.setSource(MicSource::SILENCE);
    check(stops == 2, "leavingLive: leaving live with the core stopped has nothing to close");
}

int main() {
    silenceIsZeros();
    noiseIsLoud();
    wantsLiveNeedsRunning();
    liveFailureFallsBackToNoise();
    leavingLiveClosesWithoutRead();
    if (failures == 0) std::printf("micfeed_test: all checks passed\n");
    return failures == 0 ? 0 : 1;
}
