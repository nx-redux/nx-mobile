// Host check for discfile.cpp (pure). From the repo root:
//   out=$(mktemp -d)/discfile_test && clang++ -std=c++17 -Ilibretrodroid/src/main/cpp -Ilibretrodroid/src/main/cpp/achievements \
//     libretrodroid/src/test/cpp/discfile_test.cpp libretrodroid/src/main/cpp/achievements/discfile.cpp -o "$out" && "$out"
#include "discfile.h"

#include <algorithm>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

#include "check.h"

using namespace libretrodroid;

static std::vector<uint8_t> image;
static int opened = 0;
static std::string openedPath;
static int openedFd = -1;

static void* fakeOpen(const char* path) { opened++; openedPath = path; return &image; }
static void* fakeOpenFd(int fd) { opened++; openedFd = fd; return &image; }
static int64_t fakeSize(void*) { return (int64_t) image.size(); }
static int64_t fakeRead(void*, uint64_t off, void* buf, int64_t len) {
    if (off >= image.size()) return 0;
    int64_t n = std::min<int64_t>(len, (int64_t) (image.size() - off));
    memcpy(buf, image.data() + off, (size_t) n);
    return n;
}
static void fakeClose(void*) { opened--; }
static const DiscApi fake = { fakeOpen, fakeSize, fakeRead, fakeClose, fakeOpenFd };
static const DiscApi pathOnly = { fakeOpen, fakeSize, fakeRead, fakeClose, nullptr };   // an older core: no nx_disc_open_fd

static std::vector<uint8_t> make(int magicOffset, uint32_t magic) {
    std::vector<uint8_t> b(0x40);
    for (size_t i = 0; i < b.size(); i++) b[i] = (uint8_t) i;
    if (magicOffset >= 0) for (int i = 0; i < 4; i++) b[magicOffset + i] = (uint8_t) (magic >> (24 - 8 * i));
    return b;
}

int main() {
    check(discfile::open(7) == nullptr, "no api: open is null");
    check(discfile::consoleFor(7, 19) == 19, "no api: console kept");

    discfile::setApi(&fake);

    image = make(0x1C, 0xC2339F3Du);
    check(discfile::consoleFor(7, 19) == 16, "GC magic gives 16");
    image = make(0x18, 0x5D1C9EA3u);
    check(discfile::consoleFor(7, 16) == 19, "Wii magic gives 19");
    image = make(-1, 0);
    check(discfile::consoleFor(7, 19) == 19, "no magic keeps requested");
    image.resize(8);
    check(discfile::consoleFor(7, 16) == 16, "short image keeps requested");
    check(opened == 0, "consoleFor closes the disc");

    bool found = false;
    image = make(0x18, 0x5D1C9EA3u);
    discfile::consoleFor(7, 19, &found);
    check(found, "a magic is reported found (even when it matches the request)");
    image = make(-1, 0);
    found = true;
    discfile::consoleFor(7, 19, &found);
    check(!found, "no magic: not found");
    static const DiscApi refusing = {
        [](const char*) -> void* { return nullptr; }, fakeSize, fakeRead, fakeClose, [](int) -> void* { return nullptr; } };
    discfile::setApi(&refusing);
    found = true;
    check(discfile::consoleFor(7, 16, &found) == 16 && !found, "open fails: requested kept, not found");
    discfile::setApi(&fake);

    image = make(-1, 0);
    void* h = discfile::open(7);
    check(h != nullptr, "open works");
    uint8_t b[8];
    check(discfile::read(h, b, 4) == 4 && b[0] == 0 && b[3] == 3 && discfile::tell(h) == 4, "read advances");
    discfile::seek(h, 0x10, SEEK_SET);
    check(discfile::read(h, b, 2) == 2 && b[0] == 0x10, "seek set");
    discfile::seek(h, 2, SEEK_CUR);
    check(discfile::tell(h) == 0x14 && discfile::read(h, b, 1) == 1 && b[0] == 0x14, "seek cur");
    discfile::seek(h, -2, SEEK_END);
    check(discfile::read(h, b, 8) == 2 && b[0] == 0x3E, "seek end, short read");
    check(discfile::read(h, b, 8) == 0, "read past end is 0");
    discfile::seek(h, -5, SEEK_SET);
    check(discfile::tell(h) == 0, "negative seek set clamps to 0");
    discfile::close(h);
    check(opened == 0, "close closes the disc");

    // The disc is opened from the app's descriptor; an older core without open_fd gets the /proc path.
    openedFd = -1; openedPath.clear();
    h = discfile::open(7);
    check(openedFd == 7 && openedPath.empty(), "open_fd gets the descriptor");
    discfile::close(h);
    discfile::setApi(&pathOnly);
    openedFd = -1;
    h = discfile::open(7);
    check(h != nullptr && openedFd == -1 && openedPath == "/proc/self/fd/7", "no open_fd: the /proc path");
    discfile::close(h);
    check(discfile::open(-1) == nullptr, "no descriptor: null");
    discfile::setApi(&fake);

    // A null handle (a failed open handed back by rcheevos) is harmless.
    discfile::seek(nullptr, 4, SEEK_SET);
    check(discfile::tell(nullptr) == 0, "null tell is 0");
    check(discfile::read(nullptr, b, 4) == 0, "null read is 0");
    h = discfile::open(7);
    discfile::setApi(nullptr);
    check(discfile::read(h, b, 4) == 0, "read without an api is 0");
    discfile::close(h);
    discfile::setApi(&fake);

    discfile::setApi(nullptr);
    if (!failures) std::printf("ok\n");
    return failures ? 1 : 0;
}
