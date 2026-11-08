// Host check for renderers/pixelconvert.cpp (pure, no GL). From the repo root:
//   out=$(mktemp -d)/pixelconvert_test && clang++ -std=c++17 -Ilibretrodroid/src/main/cpp \
//     libretrodroid/src/test/cpp/pixelconvert_test.cpp libretrodroid/src/main/cpp/renderers/pixelconvert.cpp \
//     -o "$out" && "$out"
#include "renderers/pixelconvert.h"

#include <cstdio>
#include <cstring>
#include <vector>

using namespace libretrodroid;

static int failures = 0;

static void check(bool ok, const char* what) {
    if (!ok) { std::printf("FAIL %s\n", what); failures++; }
}

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

    // XRGB8888 (B, G, R, X in memory) -> R, G, B, X; 3x1 frame with one pixel of padding.
    // The last pixel is swapped too.
    const uint8_t source32[] = {
        1, 2, 3, 4,   5, 6, 7, 8,   9, 10, 11, 12,   0xEE, 0xEE, 0xEE, 0xEE,
    };
    std::vector<uint8_t> snapshot32(std::begin(source32), std::end(source32));
    std::vector<uint32_t> out32;
    convertXRGB8888ToRGBA(source32, 3, 1, sizeof(source32), out32);
    check(out32.size() == 3, "8888 output is packed");
    auto bytes = reinterpret_cast<const uint8_t*>(out32.data());
    const uint8_t expected32[] = { 3, 2, 1, 4,   7, 6, 5, 8,   11, 10, 9, 12 };
    check(std::memcmp(bytes, expected32, sizeof(expected32)) == 0, "8888 swaps red and blue on every pixel");
    check(std::memcmp(snapshot32.data(), source32, sizeof(source32)) == 0, "8888 leaves the core's frame untouched");

    // Two rows with a pitch wider than the frame: rows are read pitch apart.
    const uint8_t twoRows[] = { 1, 2, 3, 4,  0, 0, 0, 0,   5, 6, 7, 8,  0, 0, 0, 0 };
    convertXRGB8888ToRGBA(twoRows, 1, 2, 8, out32);
    bytes = reinterpret_cast<const uint8_t*>(out32.data());
    check(out32.size() == 2 && bytes[0] == 3 && bytes[4] == 7, "8888 honours the pitch");

    if (failures == 0) std::printf("pixelconvert_test: all passed\n");
    return failures == 0 ? 0 : 1;
}
