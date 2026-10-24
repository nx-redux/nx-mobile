// Host check for the rewind probe's counting (pure). From the repo root:
//   out=$(mktemp -d)/rewindprobe_test && clang++ -std=c++17 -fsanitize=address,undefined \
//     -Ilibretrodroid/src/main/cpp libretrodroid/src/test/cpp/rewindprobe_test.cpp -o "$out" && "$out"
#include "rewindprobe.h"

#include <cstdio>

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

static const unsigned LIMIT = 900;   // 15 s at 60 fps

// Plays [frames] one step at a time (1 frame each); the asks that fall due read size 0. True once given up.
static bool playZero(RewindProbe& p, unsigned frames, unsigned* asks = nullptr) {
    for (unsigned i = 0; i < frames; i++) {
        if (p.advance(1)) {
            if (asks) (*asks)++;
            if (p.sizeZero(LIMIT)) return true;
        }
    }
    return false;
}

int main() {
    {
        RewindProbe p;
        CHECK(p.advance(0), "the first step asks at once, even paused");
        CHECK(!p.sizeZero(LIMIT), "a first 0 is not given up on");
        unsigned asks = 0;
        CHECK(!playZero(p, 60, &asks) && asks == 1, "size 0 is asked again after 60 played frames");
    }
    {
        RewindProbe p;
        p.advance(0);
        p.sizeZero(LIMIT);
        bool asked = false;
        for (int i = 0; i < 100000; i++) asked = p.advance(0) || asked;
        CHECK(!asked, "paused steps (no frames played) never ask nor count toward giving up");
        CHECK(!playZero(p, LIMIT - 60), "under 15 s of play at size 0: still probing");
    }
    {
        RewindProbe p;
        p.advance(0);
        p.sizeZero(LIMIT);
        CHECK(playZero(p, LIMIT), "size 0 for 15 s of play: given up");
    }
    {
        RewindProbe p;
        p.advance(0);
        p.sizeZero(LIMIT);
        playZero(p, LIMIT - 120);
        while (!p.advance(1)) {}
        p.sizeKnown();   // the core booted far enough to report a size, then refused it
        CHECK(!p.refused(), "one refusal backs off, no give-up");
        CHECK(!playZero(p, LIMIT - 1), "a size other than 0 restarts the zero streak");
    }
    {
        RewindProbe p;
        unsigned waits[8] = {};
        unsigned i = 0;
        bool gaveUp = false;
        while (!gaveUp && i < 8) {
            unsigned frames = 0;
            while (!p.advance(1)) frames++;
            waits[i++] = frames + 1;
            p.sizeKnown();
            gaveUp = p.refused();
        }
        CHECK(gaveUp && i == RewindProbe::MAX_REFUSALS, "8 refusals give up");
        CHECK(waits[1] == 120 && waits[2] == 240 && waits[5] == 1920 && waits[7] == 1920, "refusals back off 2, 4 ... 32 intervals");
    }
    {
        RewindProbe p;
        p.advance(0);
        p.sizeZero(LIMIT);
        playZero(p, LIMIT - 1);
        p.reset();
        p.advance(0);
        CHECK(!p.sizeZero(LIMIT), "reset (a new core) starts over");
    }
    printf(fails ? "%d FAILED\n" : "ALL PASSED\n", fails);
    return fails ? 1 : 0;
}
