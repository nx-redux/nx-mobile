// Host check for the per-draw texture filter cache. From the repo root:
//   out=$(mktemp -d)/filtercache_test && clang++ -std=c++17 -fsanitize=address,undefined \
//     -Ilibretrodroid/src/main/cpp libretrodroid/src/test/cpp/filtercache_test.cpp -o "$out" && "$out"
#include "filtercache.h"

#include <cstdio>

using namespace libretrodroid;

static int failures = 0;
static void check(bool ok, const char* what) { if (!ok) { std::printf("FAIL %s\n", what); failures++; } }

static constexpr int NEAREST = 0x2600;
static constexpr int LINEAR = 0x2601;

int main() {
    {
        TextureFilterCache cache;
        check(cache.needsSet(5, LINEAR), "a fresh cache sets");
        check(!cache.needsSet(5, LINEAR), "the same filter again is skipped");
        check(cache.needsSet(5, NEAREST), "another filter is set");
        check(!cache.needsSet(5, NEAREST), "and then remembered");
        check(cache.needsSet(5, LINEAR), "switching back is set");
    }
    {
        TextureFilterCache cache;
        check(cache.needsSet(5, LINEAR), "texture 5");
        check(cache.needsSet(6, LINEAR), "another texture with the same filter is set");
        check(cache.needsSet(5, LINEAR), "the first texture again is set: only the last one is remembered");
    }
    {
        // A draw starts invalidated: a re-created texture can reuse a deleted one's name, with default filters.
        TextureFilterCache cache;
        check(cache.needsSet(5, LINEAR), "first draw");
        cache.invalidate();
        check(cache.needsSet(5, LINEAR), "after invalidate the same filter is set again");
        check(!cache.needsSet(5, LINEAR), "and remembered from there");
    }
    {
        // Texture 0 and filter 0 are ordinary keys, not "unknown".
        TextureFilterCache cache;
        check(cache.needsSet(0, 0), "texture 0, filter 0 on a fresh cache");
        check(!cache.needsSet(0, 0), "texture 0, filter 0 remembered");
    }
    if (failures == 0) std::printf("filtercache_test: all passed\n");
    return failures == 0 ? 0 : 1;
}
