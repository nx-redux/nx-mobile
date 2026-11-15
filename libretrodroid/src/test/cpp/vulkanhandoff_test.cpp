// Host check for vulkanhandoff.h (pure). From the repo root:
//   out=$(mktemp -d)/vulkanhandoff_test && clang++ -std=c++17 -Ilibretrodroid/src/main/cpp \
//     libretrodroid/src/test/cpp/vulkanhandoff_test.cpp -o "$out" && "$out"
#include "vulkanhandoff.h"

#include <cstdio>
#include <vector>

using namespace libretrodroid;

static int failures = 0;
static void check(bool ok, const char* what) { if (!ok) { std::printf("FAIL %s\n", what); failures++; } }

int main() {
    check(chooseHandoff(true, true, true) == HandoffMode::Import, "import wins when it works");
    check(chooseHandoff(true, false, false) == HandoffMode::Import, "import without a swapchain");
    check(chooseHandoff(false, true, true) == HandoffMode::Reader, "no import, reader works");
    check(chooseHandoff(false, true, false) == HandoffMode::None, "swapchain but the reader probe failed");
    check(chooseHandoff(false, false, true) == HandoffMode::None, "no swapchain extension");

    BufferIds ids;
    int a = 0, b = 0;
    uint64_t ia = ids.idFor(&a), ib = ids.idFor(&b);
    check(ia != ib && ia != 0 && ids.idFor(&a) == ia, "stable per buffer, never 0");
    ids.clear();
    check(ids.idFor(&a) != ia && ids.idFor(&a) != ib, "never reused after a remake");

    HeldImages<int> held(3);
    std::vector<int> out;
    for (int i = 1; i <= 10; i++) held.push(i, out);   // a fast-forward burst with no draw between
    check(held.size() == 3 && out.size() == 7 && out.front() == 1, "burst never holds more than the budget");
    out.clear();
    held.markDrawn();
    held.push(11, out);
    check(held.size() == 2 && out == std::vector<int>({8, 9}), "after a draw only the drawn one and newer stay");
    out.clear();
    held.drain(out);
    check(held.size() == 0 && out.size() == 2, "drain releases all");

    FailureStreak start;
    bool fired = false;
    for (int i = 0; i < 29; i++) fired |= start.onFrame(false, i * 16);
    check(!fired && start.onFrame(false, 29 * 16) && !start.onFrame(false, 30 * 16), "30 failed first frames: once");
    FailureStreak mid;
    check(!mid.onFrame(true, 0) && !mid.onFrame(false, 100) && !mid.onFrame(false, 2099), "under 2 s: quiet");
    check(mid.onFrame(false, 2100) && !mid.onFrame(false, 2200), "2 s of failures after a success: once");
    FailureStreak blip;
    blip.onFrame(true, 0);
    blip.onFrame(false, 100);
    blip.onFrame(true, 1900);
    check(!blip.onFrame(false, 2500) && !blip.onFrame(false, 3900), "a success restarts the clock");

    if (failures == 0) std::printf("vulkanhandoff_test: OK\n");
    return failures == 0 ? 0 : 1;
}
