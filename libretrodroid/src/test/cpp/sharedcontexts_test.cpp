// Host check for the slot table in sharedcontexts.cpp (pure, no EGL). From the repo root:
//   out=$(mktemp -d)/sharedcontexts_test && clang++ -std=c++17 -DNX_SHAREDCONTEXTS_NO_EGL -Ilibretrodroid/src/main/cpp \
//     libretrodroid/src/test/cpp/sharedcontexts_test.cpp libretrodroid/src/main/cpp/sharedcontexts.cpp -o "$out" && "$out"
#include "sharedcontexts.h"

#include <cstdio>

#include "check.h"

using namespace libretrodroid;

int main() {
    SharedContextSlots slots(2);
    int a = slots.acquire(), b = slots.acquire();
    check(a >= 0 && b >= 0 && a != b, "two slots under the limit");
    check(slots.acquire() == -1, "a third past the limit is refused");
    check(slots.live() == 2, "two live");
    check(slots.release(a), "release a live slot");
    check(!slots.release(a), "a second release of the same slot is refused");
    check(!slots.release(7) && !slots.release(-1), "out-of-range slots are refused");
    check(slots.acquire() == a, "a freed slot is reused");
    check(slots.live() == 2, "two live again");
    if (failures == 0) std::printf("ok\n");
    return failures == 0 ? 0 : 1;
}
