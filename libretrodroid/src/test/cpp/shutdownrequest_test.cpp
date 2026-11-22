// Host check for shutdownrequest.h (pure). From the repo root:
//   out=$(mktemp -d)/shutdownrequest_test && clang++ -std=c++17 -Ilibretrodroid/src/main/cpp \
//     libretrodroid/src/test/cpp/shutdownrequest_test.cpp -o "$out" && "$out"
#include "shutdownrequest.h"

#include <cstdio>
#include <thread>

#include "check.h"

using namespace libretrodroid;

int main() {
    ShutdownRequest r;
    check(!r.take(), "nothing requested");
    r.request();
    check(r.take(), "a request is taken once");
    check(!r.take(), "and then cleared");

    std::thread cpu([&r] { r.request(); });   // the core asks from its CPU thread
    cpu.join();
    check(r.take(), "seen from another thread");

    r.request();
    r.reset();
    check(!r.take(), "a new session starts clear");

    if (failures == 0) std::printf("shutdownrequest_test: OK\n");
    return failures == 0 ? 0 : 1;
}
