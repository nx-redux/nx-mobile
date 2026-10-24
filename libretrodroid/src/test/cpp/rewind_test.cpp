// Host check for the rewind engine (capture pool, worker, XOR delta + LZ4 over the ring). From the repo root:
//   c=libretrodroid/src/main/cpp; d=$(mktemp -d) && clang -c -fsanitize=address,undefined $c/lz4/lz4.c -o $d/lz4.o && \
//   clang++ -std=c++17 -fsanitize=address,undefined -pthread -Ilibretrodroid/src/test/cpp/shim -I$c \
//     libretrodroid/src/test/cpp/rewind_test.cpp $c/rewind.cpp $c/rewindring.cpp $c/rewindbudget.cpp $c/logflag.cpp \
//     $d/lz4.o -o $d/rewind_test && $d/rewind_test
// The "core" is a byte array: state n is the buffer filled with n (plus a counter byte, so neighbours differ in more
// than one place); serialize copies it out, unserialize copies it back.
#include "rewind.h"

#include <cstdio>
#include <cstring>
#include <vector>

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

static constexpr size_t STATE = 4096;
static constexpr size_t WANT = 4 * 1024 * 1024;

struct FakeCore {
    std::vector<uint8_t> mem = std::vector<uint8_t>(STATE, 0);
    int failUnserialize = 0;   // the next N unserialize calls fail

    void become(int n) {
        std::fill(mem.begin(), mem.end(), static_cast<uint8_t>(n));
        mem[STATE - 1] = static_cast<uint8_t>(n * 7);
    }
    // n when the memory is exactly state n, else -1
    int state() const {
        int n = mem[0];
        for (size_t i = 0; i < STATE - 1; i++)
            if (mem[i] != n) return -1;
        return mem[STATE - 1] == static_cast<uint8_t>(n * 7) ? n : -1;
    }
    Rewind::SerializeFn serialize() {
        return [this](uint8_t* out, size_t len) {
            if (len != mem.size()) return false;
            std::memcpy(out, mem.data(), len);
            return true;
        };
    }
    Rewind::UnserializeFn unserialize() {
        return [this](const uint8_t* in, size_t len) {
            if (failUnserialize > 0) {
                failUnserialize--;
                return false;
            }
            if (len != mem.size()) return false;
            std::memcpy(mem.data(), in, len);
            return true;
        };
    }
};

// Plays states from..to (inclusive), capturing after each like step() does after retro_run.
static void play(Rewind& r, FakeCore& core, int from, int to) {
    for (int n = from; n <= to; n++) {
        core.become(n);
        r.capture(core.serialize());
        // The worker may still be busy: a full pool skips the capture after 2 ms. Waiting here keeps every capture
        // (the test is about the chain, not the pool).
        r.waitIdle();
    }
}

static Rewind::Step back(Rewind& r, FakeCore& core, int times) {
    Rewind::Step last = Rewind::Step::OFF;
    for (int i = 0; i < times; i++) last = r.stepBack(core.unserialize());
    return last;
}

// Incompressible states (a keyframe is about the whole state) that differ in a few bytes, captured 1..1000 into a ring
// of [want] bytes, then rewound until empty: true when every step lands on the state before the last.
static bool smallRing(size_t want, int& steps) {
    Rewind r(want);
    std::vector<uint8_t> mem(STATE);
    uint32_t seed = 12345;
    for (auto& b : mem) {
        seed = seed * 1103515245u + 12345u;
        b = static_cast<uint8_t>(seed >> 16);
    }
    auto become = [&mem](int n) {
        for (int i = 0; i < 4; i++) std::memcpy(mem.data() + 64 * i, &n, sizeof n);
    };
    auto at = [&mem]() {
        int n;
        std::memcpy(&n, mem.data(), sizeof n);
        return n;
    };
    auto ser = [&mem](uint8_t* out, size_t len) { std::memcpy(out, mem.data(), len); return true; };
    auto unser = [&mem](const uint8_t* in, size_t len) { std::memcpy(mem.data(), in, len); return true; };
    r.configure(true, STATE);
    for (int n = 1; n <= 1000; n++) {
        become(n);
        r.capture(ser);
        r.waitIdle();
    }
    int expect = 999;
    bool ok = true;
    steps = 0;
    while (r.stepBack(unser) == Rewind::Step::REWOUND) {
        if (at() != expect) ok = false;
        expect--;
        steps++;
    }
    return ok;
}

int main() {
    {
        Rewind r(WANT);
        FakeCore core;
        r.configure(true, STATE);
        CHECK(r.enabled(), "rewindsToEarlierStates: enabled");
        play(r, core, 1, 100);
        Rewind::Step s = back(r, core, 10);
        CHECK(s == Rewind::Step::REWOUND && core.state() == 90, "rewindsToEarlierStates: 10 back from 100 is 90");
    }
    {
        // Across keyframes (every 60 entries): all the way to the oldest, every state on the way.
        Rewind r(WANT);
        FakeCore core;
        r.configure(true, STATE);
        play(r, core, 1, 150);
        bool ok = true;
        for (int n = 149; n >= 1; n--) {
            if (r.stepBack(core.unserialize()) != Rewind::Step::REWOUND || core.state() != n) ok = false;
        }
        CHECK(ok, "every state back to the oldest, across keyframes");
        CHECK(r.stepBack(core.unserialize()) == Rewind::Step::EMPTY, "then empty");
    }
    {
        Rewind r(WANT);
        FakeCore core;
        r.configure(true, STATE);
        play(r, core, 1, 100);
        back(r, core, 10);
        CHECK(core.state() == 90, "playOnAfterRewind: at 90");
        // Played on from 90 the game is a new branch: 91..95 again but these are the new captures.
        play(r, core, 91, 95);
        Rewind::Step s = back(r, core, 5);
        CHECK(s == Rewind::Step::REWOUND && core.state() == 90, "playOnAfterRewind: 5 back from 95 is 90 again");
        back(r, core, 30);
        CHECK(core.state() == 60, "playOnAfterRewind: the older history still decodes (60)");
    }
    {
        Rewind r(WANT);
        FakeCore core;
        r.configure(true, STATE);
        CHECK(r.stepBack(core.unserialize()) == Rewind::Step::EMPTY, "emptyBufferReportsEmpty: nothing captured");
        play(r, core, 1, 3);
        back(r, core, 3);
        CHECK(core.state() == 1, "emptyBufferReportsEmpty: back to the oldest");
        CHECK(r.stepBack(core.unserialize()) == Rewind::Step::EMPTY, "emptyBufferReportsEmpty: then empty");
    }
    {
        Rewind r(WANT);
        FakeCore core;
        r.configure(true, STATE);
        for (int n = 1; n <= 5; n++) {
            core.become(n);
            r.capture(core.serialize());   // no waiting: some are still queued
        }
        r.clear();
        CHECK(r.stepBack(core.unserialize()) == Rewind::Step::EMPTY, "clearDropsQueued: queued captures dropped");
        play(r, core, 10, 12);
        back(r, core, 1);
        CHECK(core.state() == 11, "clearDropsQueued: a new chain after the clear");
    }
    {
        Rewind r(WANT);
        FakeCore core;
        r.configure(true, 0);
        CHECK(!r.enabled(), "zeroSizeTurnsOff: off");
        bool called = false;
        r.capture([&called](uint8_t*, size_t) { called = true; return true; });
        CHECK(!called, "zeroSizeTurnsOff: capture is a no-op");
        CHECK(r.stepBack(core.unserialize()) == Rewind::Step::OFF, "zeroSizeTurnsOff: stepBack is OFF");
    }
    {
        Rewind r(WANT);
        FakeCore core;
        r.configure(true, STATE);
        play(r, core, 1, 10);
        core.become(50);
        core.failUnserialize = 1;
        r.stepBack(core.unserialize());
        CHECK(core.state() == 50, "failedUnserializeDropsNewest: the failed step left the game alone");
        CHECK(r.stepBack(core.unserialize()) == Rewind::Step::REWOUND && core.state() == 8,
              "failedUnserializeDropsNewest: next step restores the entry before the failed one");
        back(r, core, 7);
        CHECK(core.state() == 1, "failedUnserializeDropsNewest: the oldest is still there");
    }
    {
        Rewind r(WANT);
        r.configure(true, STATE);
        int builds = r.builds();
        r.configure(true, STATE);
        CHECK(r.builds() == builds, "noRebuildWhenUnchanged: same values keep the buffer");
        r.configure(true, STATE * 2);
        CHECK(r.builds() == builds + 1, "noRebuildWhenUnchanged: a new size rebuilds");
        r.configure(false, STATE * 2);
        CHECK(!r.enabled(), "noRebuildWhenUnchanged: off frees it");
        r.configure(false, STATE * 2);
        CHECK(r.builds() == builds + 1, "noRebuildWhenUnchanged: off twice builds nothing");
    }
    {
        Rewind r(WANT);
        FakeCore core;
        r.configure(true, STATE);
        CHECK(r.interval() == 1, "interval: every frame up to 1 MB");
        r.configure(true, 4 * 1024 * 1024);
        CHECK(r.interval() == 2, "interval: every 2nd frame up to 8 MB");
        Rewind big(64 * 1024 * 1024);
        big.configure(true, 9 * 1024 * 1024);
        CHECK(!big.enabled() || big.interval() == 4, "interval: every 4th above 8 MB");
    }
    {
        // A serialize failure turns rewind off.
        Rewind r(WANT);
        r.configure(true, STATE);
        r.capture([](uint8_t*, size_t) { return false; });
        CHECK(!r.enabled(), "serialize failure turns rewind off");
    }
    {
        // Every 2nd frame: only every other call captures.
        Rewind r(WANT);
        FakeCore core;
        const size_t size = 2 * 1024 * 1024;
        core.mem.assign(size, 0);
        r.configure(true, size);
        int calls = 0;
        for (int n = 0; n < 10; n++) {
            r.capture([&calls](uint8_t*, size_t) { calls++; return true; });
            r.waitIdle();
        }
        CHECK(calls == 5, "interval 2 serializes on every other capture call");
    }

    {
        // Wraps and trims many times; whatever is left must still decode, state by state, to the oldest.
        int steps = 0;
        CHECK(smallRing(48 * 1024, steps) && steps >= 60, "small ring: consecutive states after many wraps");
        // Smaller than one keyframe run: deltas push out their own keyframe and the ring restarts on a keyframe
        // (NEED_KEYFRAME).
        CHECK(smallRing(5 * 1024, steps) && steps >= 1, "tiny ring: a delta that lost its keyframe starts a new chain");
    }

    if (fails) {
        printf("%d FAILED\n", fails);
        return 1;
    }
    printf("ALL PASSED\n");
    return 0;
}
