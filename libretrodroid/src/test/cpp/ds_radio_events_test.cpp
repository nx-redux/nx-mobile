// Host check for the DS radio events (DS Download Play's received game and its host leaving). From the repo root:
//   out=$(mktemp -d)/ds_radio_events_test && clang++ -std=c++17 -Ilibretrodroid/src/main/cpp/netplay \
//     libretrodroid/src/test/cpp/ds_radio_events_test.cpp -o "$out" && "$out"
#include "ds_radio_events.h"

#include <cstdio>

#include "check.h"

int main() {
    DsRadioEvents e;
    check(e.apply(1), "radio on goes to the room");
    check(e.apply(0) && e.apply(2), "off and hosting go to the room");
    check(!e.apply(4) && !e.apply(4), "host left is the app's");
    check(e.hostLeft == 2, "each host left counts");
    check(!e.apply(3) && e.dlpStarted, "the received game started");
    check(!e.apply(9), "an unknown state goes nowhere");
    e.reset();
    check(e.hostLeft == 0 && !e.dlpStarted, "a new game starts clear");
    if (failures == 0) std::printf("ds_radio_events_test: OK\n");
    return failures == 0 ? 0 : 1;
}
