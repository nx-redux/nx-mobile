// Host check for the rewind RAM budget (pure). From the repo root:
//   out=$(mktemp -d)/rewindbudget_test && clang++ -std=c++17 -fsanitize=address,undefined \
//     -Ilibretrodroid/src/main/cpp libretrodroid/src/test/cpp/rewindbudget_test.cpp \
//     libretrodroid/src/main/cpp/rewindbudget.cpp -o "$out" && "$out"
// Ported from the handheld's tests/test_rewind_budget.c.
#include "rewindbudget.h"

#include <cstdio>
#include <cstring>

using namespace libretrodroid;

static int fails = 0;
#define CHECK(cond, msg)                \
    do {                                \
        if (cond) {                     \
            printf("PASS: %s\n", msg);  \
        } else {                        \
            printf("FAIL: %s\n", msg);  \
            fails++;                    \
        }                               \
    } while (0)

static const uint64_t MB = 1024ULL * 1024;

static uint64_t parse(const char* text) {
    FILE* f = fmemopen((void*)text, strlen(text), "r");
    uint64_t v = parseMemAvailable(f);
    fclose(f);
    return v;
}

int main() {
    CHECK(rewindBudget(64 * MB, 200 * 1024, 3, 600 * MB) == 64 * MB, "small state keeps the asked buffer");
    CHECK(rewindBudget(256 * MB, 40 * MB, 4, 0) == 256 * MB, "unknown RAM keeps the asked buffer");
    // 30 MB state (PPSSPP): 9 copies = 270 MB of the 300 MB budget leaves 30 MB
    CHECK(rewindBudget(256 * MB, 30 * MB, 4, 600 * MB) == 30 * MB, "large state cuts the buffer to what is left");
    size_t cut = rewindBudget(256 * MB, 30 * MB + 12345, 4, 600 * MB);
    CHECK(cut > 0 && cut % MB == 0 && cut < 30 * MB, "a cut buffer is a whole number of MB");
    CHECK(rewindBudget(64 * MB, 40 * MB, 4, 600 * MB) == 0, "no room for the state copies turns rewind off");
    CHECK(rewindBudget(64 * MB, 33 * MB + 300 * 1024, 4, 600 * MB) == 0, "under 1 MB left turns rewind off");
    CHECK(rewindBudget(30 * MB, 30 * MB, 4, 600 * MB) == 30 * MB, "an exact fit keeps the asked buffer");

    CHECK(parse("MemTotal:        1013760 kB\nMemFree:          100000 kB\nMemAvailable:     614400 kB\n") == 600 * MB,
          "MemAvailable parsed in bytes");
    CHECK(parse("MemTotal:        1013760 kB\nMemFree:          100000 kB\n") == 0, "no MemAvailable gives 0");
    CHECK(parse("garbage\n") == 0, "unrelated text gives 0");

    printf(fails ? "\n%d FAILURE(S)\n" : "\nALL PASS\n", fails);
    return fails ? 1 : 0;
}
