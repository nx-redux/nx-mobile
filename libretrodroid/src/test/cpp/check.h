// The pass/fail counting the host checks share. Included by quote from a test in this folder, so it is found whatever
// -I flags that test's build line has. A test uses one of the two forms and returns its counter from main.
#ifndef LIBRETRODROID_TEST_CHECK_H
#define LIBRETRODROID_TEST_CHECK_H

#include <cstdio>

// Quiet form: only a failure prints.
[[maybe_unused]] static int failures = 0;

[[maybe_unused]] static void check(bool ok, const char* what) {
    if (!ok) { std::printf("FAIL %s\n", what); failures++; }
}

// Verbose form: every case prints.
[[maybe_unused]] static int fails = 0;
#define CHECK(cond, msg)                \
    do {                                \
        if (cond) {                     \
            printf("PASS: %s\n", msg);  \
        } else {                        \
            printf("FAIL: %s\n", msg);  \
            fails++;                    \
        }                               \
    } while (0)

#endif
