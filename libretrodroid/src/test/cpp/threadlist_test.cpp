// Host check for ThreadList: the threads a core started, found from /proc/self/task-style folders. From the repo root:
//   out=$(mktemp -d)/threadlist_test && clang++ -std=c++17 -fsanitize=address,undefined \
//     -Ilibretrodroid/src/main/cpp libretrodroid/src/test/cpp/threadlist_test.cpp \
//     libretrodroid/src/main/cpp/threadlist.cpp -o "$out" && "$out"
#include "threadlist.h"

#include <cstdio>
#include <cstdlib>
#include <string>
#include <sys/stat.h>
#include <unistd.h>

using namespace libretrodroid;

static int failures = 0;

static void check(bool ok, const char* what) {
    if (!ok) { std::printf("FAIL %s\n", what); failures++; }
}

int main() {
    // A fake task folder: thread ids as folder names, plus a stray non-numeric entry.
    char tmpl[] = "/tmp/threadlist_test.XXXXXX";
    const std::string dir = mkdtemp(tmpl);
    for (const char* name : { "100", "101", "250", "self" }) mkdir((dir + "/" + name).c_str(), 0700);

    auto before = ThreadList::list(dir.c_str());
    check(before == std::vector<int> { 100, 101, 250 }, "lists the numeric entries, sorted");

    // The core starts two threads; one of the old ones exits.
    mkdir((dir + "/300").c_str(), 0700);
    mkdir((dir + "/120").c_str(), 0700);
    rmdir((dir + "/101").c_str());
    auto after = ThreadList::list(dir.c_str());
    check(ThreadList::added(before, after) == std::vector<int> { 120, 300 }, "the threads started since");

    // A folder that cannot be read lists nothing.
    check(ThreadList::list("/nonexistent/threadlist").empty(), "missing folder is empty");

    std::system(("rm -rf " + dir).c_str());
    if (failures == 0) std::printf("threadlist_test: all passed\n");
    return failures == 0 ? 0 : 1;
}
