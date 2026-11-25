#include "discfile.h"

#include <cstdio>
#include <string>

namespace libretrodroid {
namespace discfile {

static const DiscApi* api = nullptr;

struct Handle { void* disc; int64_t pos; int64_t size; };

void setApi(const DiscApi* a) { api = a; }

void* open(int fd) {
    if (!api || fd < 0) return nullptr;
    void* disc = api->open_fd ? api->open_fd(fd) : api->open(("/proc/self/fd/" + std::to_string(fd)).c_str());
    if (!disc) return nullptr;
    int64_t size = api->size(disc);
    return new Handle { disc, 0, size < 0 ? 0 : size };
}

void seek(void* h, int64_t offset, int origin) {
    auto* f = (Handle*) h;
    if (!f) return;
    if (origin == SEEK_SET) f->pos = offset;
    else if (origin == SEEK_CUR) f->pos += offset;
    else f->pos = f->size + offset;
    if (f->pos < 0) f->pos = 0;
}

int64_t tell(void* h) { return h ? ((Handle*) h)->pos : 0; }

size_t read(void* h, void* buffer, size_t bytes) {
    auto* f = (Handle*) h;
    if (!f || !api) return 0;
    int64_t n = api->read(f->disc, (uint64_t) f->pos, buffer, (int64_t) bytes);
    if (n <= 0) return 0;
    f->pos += n;
    return (size_t) n;
}

void close(void* h) {
    auto* f = (Handle*) h;
    if (!f) return;
    if (api) api->close(f->disc);
    delete f;
}

static bool magicAt(void* h, int64_t offset, uint32_t magic) {
    uint8_t b[4];
    seek(h, offset, SEEK_SET);
    if (read(h, b, 4) != 4) return false;
    return (((uint32_t) b[0] << 24) | ((uint32_t) b[1] << 16) | ((uint32_t) b[2] << 8) | b[3]) == magic;
}

uint32_t consoleFor(int fd, uint32_t requested, bool* found) {
    if (found) *found = false;
    void* h = open(fd);
    if (!h) return requested;
    uint32_t console = requested;
    bool magicSeen = false;
    if (magicAt(h, 0x18, 0x5D1C9EA3u)) { console = 19; magicSeen = true; }
    else if (magicAt(h, 0x1C, 0xC2339F3Du)) { console = 16; magicSeen = true; }
    close(h);
    if (found) *found = magicSeen;
    return console;
}

}
}
