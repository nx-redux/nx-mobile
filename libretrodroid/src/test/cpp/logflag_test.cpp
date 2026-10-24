// Host check for logflag (Settings → System → Debug log). From the repo root:
//   out=$(mktemp -d)/logflag_test && clang++ -std=c++17 -Ilibretrodroid/src/main/cpp \
//     libretrodroid/src/test/cpp/logflag_test.cpp libretrodroid/src/main/cpp/logflag.cpp -o "$out" && "$out"
#include "logflag.h"

#include <cstdio>

static int failures = 0;
static void check(bool ok, const char* what) { if (!ok) { std::printf("FAIL %s\n", what); failures++; } }

int main() {
    check(!nx_log_enabled(), "off by default");
    nx_log_set_enabled(true);
    check(nx_log_enabled(), "on once set");
    nx_log_set_enabled(false);
    check(!nx_log_enabled(), "off again");
    if (failures == 0) std::printf("logflag_test: OK\n");
    return failures == 0 ? 0 : 1;
}
