// Host check for pointerstate.cpp (pure). From the repo root:
//   out=$(mktemp -d)/pointerstate_test && clang++ -std=c++17 -Ilibretrodroid/src/main/cpp \
//     libretrodroid/src/test/cpp/pointerstate_test.cpp libretrodroid/src/main/cpp/pointerstate.cpp -o "$out" && "$out"
#include "pointerstate.h"

#include <cmath>
#include <cstdio>

#include "check.h"

using namespace libretrodroid;

static bool near(float a, float b) { return std::fabs(a - b) < 1e-4f; }

int main() {
    PointerState p;
    check(!p.pressed(), "starts released");
    p.touch(0.25f, 0.75f);
    check(p.pressed() && near(p.x, 0.25f) && near(p.y, 0.75f), "a touch sets the position");
    p.touch(-10.f, 10.f);
    check(!p.pressed(), "not sticky: a release (outside) clears it");

    PointerState s; s.sticky = true;
    s.touch(0.25f, 0.75f);
    s.touch(-10.f, 10.f);
    check(s.pressed() && near(s.x, 0.25f), "sticky: a release keeps the last position");
    s.touch(-1.f, -1.f);
    check(near(s.y, 0.75f), "sticky: a -1 release keeps it too");

    PointerState v; v.sticky = true;
    v.vx = 1.f; v.vy = 0.f;
    v.advance(0.1f);
    check(near(v.x, 0.6f) && near(v.y, 0.5f), "velocity before any touch starts from the centre");
    v.advance(10.f);
    check(near(v.x, 1.f), "clamped to the picture");
    v.vx = 0.f;
    v.advance(1.f);
    check(near(v.x, 1.f), "no velocity, no move");

    PointerState idle;
    idle.advance(1.f);
    check(!idle.pressed(), "no velocity never presses an untouched pointer");
    PointerState a; a.sticky = true; a.touch(0.3f, 0.6f); a.vx = 1.f; a.vy = 1.f;
    PointerState c = carryOver(a);
    check(c.sticky && near(c.x, 0.3f) && near(c.y, 0.6f), "carry-over keeps a sticky pointer's flag and position");
    check(c.vx == 0.f && c.vy == 0.f, "carry-over drops the velocity");
    PointerState b; b.touch(0.3f, 0.6f);
    check(!carryOver(b).sticky && !carryOver(b).pressed(), "carry-over resets a non-sticky pointer");

    // An untouched pointer made sticky starts at the centre (Dolphin's cursor on screen, not a -1 cast out of range).
    PointerState u; u.setSticky(true, true);
    check(u.sticky && near(u.x, 0.5f) && near(u.y, 0.5f), "sticky with centring on an untouched pointer: the centre");
    PointerState t; t.touch(0.3f, 0.6f); t.setSticky(true, true);
    check(near(t.x, 0.3f) && near(t.y, 0.6f), "sticky on a touched pointer keeps its position");
    PointerState n; n.setSticky(false, true);
    check(!n.sticky && !n.pressed(), "not sticky: an untouched pointer stays released");
    PointerState off; off.setSticky(true, true); off.setSticky(false, true); off.touch(-1.f, -1.f);
    check(!off.pressed(), "sticky turned off: a release clears it again");
    PointerState flag; flag.sticky = true;   // the flag alone (a port marked sticky before the rebuild, currentInput)
    PointerState cu = carryOver(flag);
    check(cu.sticky && !cu.pressed(), "carry-over of an untouched sticky pointer stays released");

    PointerState fo; fo.setSticky(true, false);
    check(fo.sticky && !fo.pressed(), "sticky flag only: an untouched pointer stays released");
    fo.vx = 0.1f;
    fo.advance(1.f);
    check(near(fo.x, 0.6f) && near(fo.y, 0.5f), "flag-only sticky then velocity: starts from the centre");
    PointerState fc = carryOver(fo);
    check(fc.sticky && near(fc.x, 0.6f) && near(fc.y, 0.5f), "carry-over keeps a stick-aimed flag-only pointer");
    if (failures == 0) std::printf("ok\n");
    return failures == 0 ? 0 : 1;
}
