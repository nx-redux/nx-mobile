// Host check for ThreadList: the threads a core started, found from /proc/self/task-style folders. From the repo root:
//   out=$(mktemp -d)/threadlist_test && clang++ -std=c++17 -fsanitize=address,undefined \
//     -Ilibretrodroid/src/main/cpp libretrodroid/src/test/cpp/threadlist_test.cpp \
//     libretrodroid/src/main/cpp/threadlist.cpp -o "$out" && "$out"
#include "threadlist.h"

#include <cstdio>
#include <cstdlib>
#include <cstdio>
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

    // Only emulation threads: audio, Java runtime, binder and networking threads (by their names) are left out.
    {
        char tmpl2[] = "/tmp/threadlist_names.XXXXXX";
        const std::string names = mkdtemp(tmpl2);
        auto thread = [&](int id, const char* name) {
            const std::string d = names + "/" + std::to_string(id);
            mkdir(d.c_str(), 0700);
            FILE* f = std::fopen((d + "/comm").c_str(), "w");
            std::fprintf(f, "%s\n", name);
            std::fclose(f);
        };
        thread(10, "CPU thread");
        thread(11, "AudioTrack");
        thread(12, "binder:123_4");
        thread(13, "DefaultDispatch");
        thread(14, "Shader Compile");
        thread(15, "OkHttp Dispatch");
        // 16 has no comm (it exited): left out
        check(ThreadList::emulation({ 10, 11, 12, 13, 14, 15, 16 }, names.c_str()) == std::vector<int> { 10, 14 },
              "emulation threads only");
        std::system(("rm -rf " + names).c_str());
    }

    // A folder that cannot be read lists nothing.
    check(ThreadList::list("/nonexistent/threadlist").empty(), "missing folder is empty");

    std::system(("rm -rf " + dir).c_str());
    if (failures == 0) std::printf("threadlist_test: all passed\n");
    return failures == 0 ? 0 : 1;
}
