// Host check for the rewind ring buffer (pure). From the repo root:
//   out=$(mktemp -d)/rewindring_test && clang++ -std=c++17 -fsanitize=address,undefined \
//     -Ilibretrodroid/src/main/cpp libretrodroid/src/test/cpp/rewindring_test.cpp \
//     libretrodroid/src/main/cpp/rewindring.cpp -o "$out" && "$out"
// Ported from the handheld's tests/test_rewind_ring.c. The ring has no public view of its slots, so a test reads it
// by popping a copy, newest first.
#include "rewindring.h"

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

// Writes len bytes all set to tag; keyframe unless told otherwise
static RewindRing::Write put(RewindRing& r, size_t len, uint8_t tag, bool keyframe = true) {
    std::vector<uint8_t> data(len, tag);
    return r.write(data.data(), len, keyframe);
}

struct Seen {
    int tag;       // -1 if the entry's bytes are not all one tag
    size_t len;
    bool keyframe;
};

// The live entries from the oldest, read by popping a copy
static std::vector<Seen> contents(const RewindRing& r) {
    RewindRing copy = r;
    std::vector<Seen> out;
    while (!copy.empty()) {
        RewindRing::Entry e = copy.newest();
        int tag = e.data[0];
        for (size_t i = 1; i < e.len; i++)
            if (e.data[i] != e.data[0]) tag = -1;
        out.insert(out.begin(), Seen{tag, e.len, e.keyframe});
        copy.popNewest();
    }
    return out;
}

static size_t usedBytes(const RewindRing& r) {
    size_t total = 0;
    for (const Seen& s : contents(r)) total += s.len;
    return total;
}

int main() {
    {
        RewindRing r(100, 8);
        CHECK(r.empty() && r.count() == 0, "empty ring");
        CHECK(put(r, 100, 1) == RewindRing::Write::TOO_BIG && r.count() == 0, "entry as big as the buffer refused");
        CHECK(put(r, 150, 1) == RewindRing::Write::TOO_BIG, "entry bigger than the buffer refused");

        CHECK(put(r, 30, 1) == RewindRing::Write::STORED && put(r, 30, 2) == RewindRing::Write::STORED &&
              put(r, 30, 3) == RewindRing::Write::STORED, "three writes");
        CHECK(r.count() == 3 && usedBytes(r) == 90, "three entries, 90 bytes");

        // wrap-around: the next write goes to 0 and drops only the entry it overlaps
        CHECK(put(r, 20, 4) == RewindRing::Write::STORED, "write that wraps");
        std::vector<Seen> c = contents(r);
        CHECK(c.size() == 3 && c[0].tag == 2 && c[1].tag == 3 && c[2].tag == 4,
              "wrap dropped only the oldest (overlapping) entry, bytes intact");

        // a write needing exactly the free space would make head == tail: the oldest goes first
        CHECK(put(r, 10, 5) == RewindRing::Write::STORED, "write needing the exact free space");
        c = contents(r);
        CHECK(c.size() == 3 && c[0].tag == 3 && c[1].tag == 4 && c[2].tag == 5, "exact fit dropped the oldest first");
    }
    {
        RewindRing r(100, 8);
        put(r, 50, 1);
        put(r, 50, 2);
        std::vector<Seen> c = contents(r);
        CHECK(c.size() == 1 && c[0].tag == 2, "two halves: the first is dropped to keep head != tail");
    }
    {
        // entry table full: the oldest is dropped before the write
        RewindRing r(1000, 4);
        for (int i = 1; i <= 4; i++) put(r, 10, (uint8_t)i);
        CHECK(r.count() == 4, "entry table full");
        CHECK(put(r, 10, 5) == RewindRing::Write::STORED, "write into a full table");
        std::vector<Seen> c = contents(r);
        CHECK(c.size() == 4 && c[0].tag == 2 && c[3].tag == 5, "table full: oldest dropped, newest stored");
    }
    {
        // pop newest returns entries newest-first and a write after popping all starts clean
        RewindRing r(1000, 8);
        put(r, 10, 1);
        put(r, 20, 2);
        put(r, 30, 3);
        CHECK(r.newest().len == 30 && r.newest().data[0] == 3, "newest is the last write");
        r.popNewest();
        CHECK(r.count() == 2 && r.newest().len == 20 && r.newest().data[0] == 2, "pop newest: previous becomes newest");
        r.popNewest();
        r.popNewest();
        CHECK(r.empty(), "popping the last entry empties the ring");
        r.popNewest();
        CHECK(r.empty(), "pop on empty is a no-op");
        CHECK(put(r, 10, 7) == RewindRing::Write::STORED && r.count() == 1 && r.newest().data[0] == 7,
              "write after popping all works");
        r.clear();
        CHECK(r.empty(), "clear empties the ring");
        CHECK(put(r, 10, 8) == RewindRing::Write::STORED && r.newest().data[0] == 8, "write after clear works");
    }
    {
        // newest index wraps at the table start
        RewindRing r(1000, 4);
        for (int i = 1; i <= 5; i++) put(r, 10, (uint8_t)i);
        CHECK(r.newest().data[0] == 5 && contents(r).size() == 4, "newest at slot 0 after the table wrapped");
    }
    {
        // popNewestReclaimsBytes: popping frees the bytes, so as many writes fit again
        RewindRing r(100, 64);
        while (r.count() < 64) {
            size_t before = r.count();
            put(r, 10, (uint8_t)(1 + before));
            if (r.count() <= before) break; // the buffer is full and dropping
        }
        size_t full = r.count();
        for (int i = 0; i < 3; i++) r.popNewest();
        for (int i = 0; i < 3; i++) put(r, 10, (uint8_t)(100 + i));
        CHECK(full > 3 && r.count() == full, "pop 3, write 3 of the same size: count unchanged");
    }
    {
        // variable sizes, writes and pops: entries never lose their bytes or overlap
        RewindRing r(997, 16);
        unsigned seed = 12345;
        bool ok = true, content = true;
        std::vector<uint8_t> shadow;  // tag of each live entry from the oldest
        for (int i = 0; i < 20000 && ok; i++) {
            seed = seed * 1103515245u + 12345u;
            size_t len = 1 + (seed >> 8) % 400;
            if ((seed >> 20) % 7 == 0 && !r.empty()) {
                r.popNewest();
                shadow.pop_back();
            } else {
                uint8_t tag = (uint8_t)(1 + i % 250);
                if (put(r, len, tag) != RewindRing::Write::STORED) ok = false;
                shadow.push_back(tag);
            }
            std::vector<Seen> c = contents(r);
            // the ring may have dropped the oldest entries; the rest must match the newest tags
            if (c.size() > shadow.size()) ok = false;
            shadow.erase(shadow.begin(), shadow.end() - (long)c.size());
            size_t total = 0;
            for (size_t k = 0; k < c.size(); k++) {
                content = content && c[k].tag == shadow[k];
                total += c[k].len;
            }
            ok = ok && total < 997;
        }
        CHECK(ok, "20,000 random writes/pops: all stored, never over capacity");
        CHECK(content, "20,000 random writes/pops: live entries keep their bytes");
    }
    {
        // chainStartsAtKeyframe: after wraps the oldest stored entry is always a keyframe
        RewindRing r(300, 12);
        unsigned seed = 777;
        bool ok = true, sawDelta = false;
        for (int i = 0; i < 5000 && ok; i++) {
            seed = seed * 1103515245u + 12345u;
            bool keyframe = r.empty() || (i % 9 == 0);
            size_t len = 1 + (seed >> 8) % 60;
            RewindRing::Write w = put(r, len, (uint8_t)(1 + i % 250), keyframe);
            if (w == RewindRing::Write::NO_ROOM) {
                // only a delta that lost its keyframe is refused, and it leaves the ring empty
                if (keyframe || !r.empty()) ok = false;
                keyframe = true;
                if (put(r, len, 1, true) != RewindRing::Write::STORED) ok = false;
            } else if (w != RewindRing::Write::STORED) {
                ok = false;
            }
            std::vector<Seen> c = contents(r);
            if (c.empty() || !c[0].keyframe) ok = false;
            for (const Seen& s : c) sawDelta = sawDelta || !s.keyframe;
        }
        CHECK(ok, "5,000 mixed writes: the oldest stored entry is always a keyframe");
        CHECK(sawDelta, "the chain test did hold deltas");
        RewindRing e(100, 4);
        CHECK(put(e, 10, 1, false) == RewindRing::Write::NO_ROOM && e.empty(), "a delta into an empty ring is refused");
    }

    printf(fails ? "\n%d FAILURE(S)\n" : "\nALL PASS\n", fails);
    return fails ? 1 : 0;
}
