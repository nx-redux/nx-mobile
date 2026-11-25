// Host check for renderers/pixelconvert.cpp (pure, no GL). From the repo root:
//   out=$(mktemp -d)/pixelconvert_test && clang++ -std=c++17 -Ilibretrodroid/src/main/cpp \
//     libretrodroid/src/test/cpp/pixelconvert_test.cpp libretrodroid/src/main/cpp/renderers/pixelconvert.cpp \
//     -o "$out" && "$out"
#include "renderers/pixelconvert.h"

#include <cstdio>
#include <cstring>
#include <vector>

#include "check.h"

using namespace libretrodroid;

int main() {
    // 0RGB1555 -> RGB565, 2x2 frame with a padded pitch (3 pixels per row in memory).
    // red 0x1F (bits 10-14), green 0x15 (5-9), blue 0x0A (0-4).
    const uint16_t pixel = (uint16_t) ((0x1F << 10) | (0x15 << 5) | 0x0A);
    const uint16_t expected = (uint16_t) ((0x1F << 11) | (0x15 << 6) | 0x0A);
    const uint16_t pad = 0xFFFF;
    const uint16_t source16[] = { pixel, pixel, pad, pixel, 0, pad };
    std::vector<uint16_t> snapshot16(std::begin(source16), std::end(source16));
    std::vector<uint16_t> out16;
    const uint16_t* converted16 = convert0RGB1555ToRGB565(source16, 2, 2, 3 * sizeof(uint16_t), out16);
    check(converted16 == out16.data(), "1555 returns the scratch buffer");
    check(out16.size() == 4, "1555 output is packed");
    check(out16[0] == expected && out16[1] == expected && out16[2] == expected, "1555 converts every pixel");
    check(out16[3] == 0, "1555 last pixel converted (black stays black)");
    check(std::memcmp(snapshot16.data(), source16, sizeof(source16)) == 0, "1555 leaves the core's frame untouched");

    // Converting the same frame twice gives the same result (a duplicate frame is not converted twice).
    convert0RGB1555ToRGB565(source16, 2, 2, 3 * sizeof(uint16_t), out16);
    check(out16[0] == expected, "1555 is idempotent on the source");

    if (failures == 0) std::printf("pixelconvert_test: all passed\n");
    return failures == 0 ? 0 : 1;
}
