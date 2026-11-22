// Host check for vulkanframe.h (pure). From the repo root:
//   out=$(mktemp -d)/vulkanframe_test && clang++ -std=c++17 -Ilibretrodroid/src/main/cpp \
//     libretrodroid/src/test/cpp/vulkanframe_test.cpp -o "$out" && "$out"
#include "vulkanframe.h"

#include <cstdio>

#include "check.h"

using namespace libretrodroid;

int main() {
    FrameExtent e = boundFrame(1280, 960, 2560, 1920);
    check(e.width == 1280 && e.height == 960, "inside the limit: as reported (N64 2x)");
    e = boundFrame(1280, 1056, 640, 528);
    check(e.width == 640 && e.height == 528, "beyond the limit: clamped (Dolphin's EFB past its surface)");
    e = boundFrame(640, 480, 640, 480);
    check(e.width == 640 && e.height == 480, "equal to the limit");
    e = boundFrame(0, 0, 640, 480);
    check(e.width == 1 && e.height == 1, "never empty");
    e = boundFrame(800, 600, 0, 0);
    check(e.width == 800 && e.height == 600, "no limit known: as reported");
    if (failures == 0) std::printf("vulkanframe_test: OK\n");
    return failures == 0 ? 0 : 1;
}
