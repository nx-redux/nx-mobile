// Host check for screenregion.cpp (pure, no GL). From the repo root:
//   out=$(mktemp -d)/screenregion_test && clang++ -std=c++17 -Ilibretrodroid/src/main/cpp \
//     libretrodroid/src/test/cpp/screenregion_test.cpp libretrodroid/src/main/cpp/screenregion.cpp -o "$out" && "$out"
#include "screenregion.h"

#include <cmath>
#include <cstdio>
#include <cstdlib>

using namespace libretrodroid;

static int failures = 0;

static void near(float expected, float actual, const char* what) {
    if (std::fabs(expected - actual) > 1e-5F) {
        std::printf("FAIL %s: expected %f, got %f\n", what, expected, actual);
        failures++;
    }
}

static void check(bool ok, const char* what) {
    if (!ok) { std::printf("FAIL %s\n", what); failures++; }
}

int main() {
    // The DS bottom screen (lower half of the frame) drawn over the top half of a full viewport.
    ScreenRegion bottom { 0.0F, 0.5F, 1.0F, 1.0F,  0.0F, 0.0F, 1.0F, 0.5F,  true, true };
    RegionQuad q = buildRegionQuad(bottom, 0.0F, 0.0F, 1.0F, 1.0F, false);
    // Corner order as VideoLayout: (left, top), (left, bottom), (right, top), (right, top), (left, bottom), (right, bottom).
    near(-1.0F, q.vertices[0], "v0 x");  near(1.0F, q.vertices[1], "v0 y");
    near(0.0F, q.coordinates[0], "c0 u"); near(0.5F, q.coordinates[1], "c0 v");
    near(-1.0F, q.vertices[2], "v1 x");  near(0.0F, q.vertices[3], "v1 y");
    near(0.0F, q.coordinates[2], "c1 u"); near(1.0F, q.coordinates[3], "c1 v");
    near(1.0F, q.vertices[4], "v2 x");   near(1.0F, q.vertices[5], "v2 y");
    near(1.0F, q.vertices[10], "v5 x");  near(0.0F, q.vertices[11], "v5 y");
    near(1.0F, q.coordinates[10], "c5 u"); near(1.0F, q.coordinates[11], "c5 v");
    near(1.0F, q.srcWidth, "src width"); near(0.5F, q.srcHeight, "src height");
    check(q.shaded, "shaded flag");

    // A GL core's framebuffer has its origin at the bottom: the frame's top row is texture v = 1.
    RegionQuad g = buildRegionQuad(bottom, 0.0F, 0.0F, 1.0F, 1.0F, true);
    near(0.5F, g.coordinates[1], "gl c0 v");
    near(0.0F, g.coordinates[3], "gl c1 v");

    // dst is relative to the viewport: a viewport on the right half of the view.
    RegionQuad v = buildRegionQuad(bottom, 0.5F, 0.0F, 0.5F, 1.0F, false);
    near(0.0F, v.vertices[0], "viewport v0 x");
    near(1.0F, v.vertices[10], "viewport v5 x");

    // JNI's packed floats: 11 per region, alpha last (clamped to 0..1, NaN reads as 1); a trailing partial region is
    // dropped.
    float packed[] = {
        0, 0, 1, 0.5F, 0.1F, 0.2F, 0.3F, 0.4F, 0, 1, 0.5F,
        0, 0, 1, 1, 0, 0, 1, 1, 1, 0, 2.0F,
        0, 0, 1, 1, 0, 0, 1, 1, 1, 0, -1.0F,
        0, 0, 1, 1, 0, 0, 1, 1, 1, 0, NAN,
        9, 9, 9,
    };
    auto regions = ScreenRegion::unpack(packed, sizeof(packed) / sizeof(float));
    check(regions.size() == 4, "unpack size");
    if (regions.size() == 4) {
        near(0.5F, regions[0].srcBottom, "unpack srcBottom");
        near(0.4F, regions[0].dstBottom, "unpack dstBottom");
        check(!regions[0].shaded && regions[0].touch, "unpack flags");
        near(0.5F, regions[0].alpha, "unpack alpha");
        near(1.0F, regions[1].alpha, "unpack alpha clamped high");
        near(0.0F, regions[2].alpha, "unpack alpha clamped low");
        near(1.0F, regions[3].alpha, "unpack alpha NaN");
    }

    // The quad carries the region's alpha to the draw.
    ScreenRegion faded { 0.0F, 0.0F, 1.0F, 0.5F,  0.6F, 0.6F, 0.9F, 0.9F,  false, false, 0.75F };
    near(0.75F, buildRegionQuad(faded, 0.0F, 0.0F, 1.0F, 1.0F, false).alpha, "quad alpha");
    check(ScreenRegion::unpack(nullptr, 0).empty(), "unpack null");

    // Downscaling: the region's source in frame pixels vs its destination in view pixels.
    {
        // A 4x DS frame (1024x1536): the top screen (1024x768 source) drawn 593 px wide in a 1248x1900 view.
        ScreenRegion top4x { 0.0F, 0.0F, 1.0F, 0.5F,  0.0F, 0.0F, 593.0F / 1248.0F, 444.0F / 1900.0F,  true, false };
        RegionQuad q4 = buildRegionQuad(top4x, 0.0F, 0.0F, 1.0F, 1.0F, true);
        check(regionDownscales(q4, 1024.0F, 1536.0F, 1248.0F, 1900.0F), "4x screen into 593 px downscales");
        // A 1x frame (256x384): the same region upscales.
        check(!regionDownscales(q4, 256.0F, 384.0F, 1248.0F, 1900.0F), "1x screen into 593 px does not");
        // The PiP inset at 3x: 768 px wide source into a 356 px inset.
        ScreenRegion inset { 0.0F, 0.5F, 1.0F, 1.0F,  0.6F, 0.2F, 0.6F + 356.0F / 1080.0F, 0.2F + 267.0F / 1600.0F,  false, false, 0.5F };
        RegionQuad qi = buildRegionQuad(inset, 0.0F, 0.0F, 1.0F, 1.0F, true);
        check(regionDownscales(qi, 768.0F, 1152.0F, 1080.0F, 1600.0F), "3x inset downscales");
        // Exactly 1:1 is not a downscale.
        ScreenRegion exact { 0.0F, 0.0F, 1.0F, 0.5F,  0.0F, 0.0F, 512.0F / 1024.0F, 384.0F / 1024.0F,  true, false };
        RegionQuad qe = buildRegionQuad(exact, 0.0F, 0.0F, 1.0F, 1.0F, false);
        check(!regionDownscales(qe, 512.0F, 768.0F, 1024.0F, 1024.0F), "1:1 is not a downscale");
        // An empty texture (before the first frame) never downscales.
        check(!regionDownscales(q4, 0.0F, 0.0F, 1248.0F, 1900.0F), "empty texture");
    }

    {
        // A core rotated a quarter turn counterclockwise (SET_ROTATION 1, a vertical arcade game): the picture turns
        // with it, so the view's top-left shows the frame's top-right.
        ScreenRegion whole { 0.0F, 0.0F, 1.0F, 1.0F,  0.0F, 0.0F, 1.0F, 1.0F,  true, false };
        RegionQuad r1 = buildRegionQuad(whole, 0.0F, 0.0F, 1.0F, 1.0F, false, 1);
        near(1.0F, r1.coordinates[0], "r1 top-left u");     near(0.0F, r1.coordinates[1], "r1 top-left v");
        near(0.0F, r1.coordinates[2], "r1 bottom-left u");  near(0.0F, r1.coordinates[3], "r1 bottom-left v");
        near(1.0F, r1.coordinates[4], "r1 top-right u");    near(1.0F, r1.coordinates[5], "r1 top-right v");
        near(0.0F, r1.coordinates[10], "r1 bottom-right u"); near(1.0F, r1.coordinates[11], "r1 bottom-right v");
        near(-1.0F, r1.vertices[0], "r1 vertices stay");
        // Half a turn: top-left shows the bottom-right; four turns are none.
        RegionQuad r2 = buildRegionQuad(whole, 0.0F, 0.0F, 1.0F, 1.0F, false, 2);
        near(1.0F, r2.coordinates[0], "r2 top-left u");     near(1.0F, r2.coordinates[1], "r2 top-left v");
        RegionQuad r4 = buildRegionQuad(whole, 0.0F, 0.0F, 1.0F, 1.0F, false, 4);
        RegionQuad r0 = buildRegionQuad(whole, 0.0F, 0.0F, 1.0F, 1.0F, false);
        for (int i = 0; i < 12; i++) near(r0.coordinates[i], r4.coordinates[i], "four turns are none");
        // Three turns (clockwise once): top-left shows the frame's bottom-left.
        RegionQuad r3 = buildRegionQuad(whole, 0.0F, 0.0F, 1.0F, 1.0F, false, 3);
        near(0.0F, r3.coordinates[0], "r3 top-left u");     near(1.0F, r3.coordinates[1], "r3 top-left v");
    }

    if (failures == 0) std::printf("screenregion: all checks passed\n");
    return failures == 0 ? EXIT_SUCCESS : EXIT_FAILURE;
}
