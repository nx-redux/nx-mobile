#include "achievementshash.h"

#include <cstdio>
#include <cstring>
#include <mutex>
#include <strings.h>
#include <unistd.h>

#include <dlfcn.h>
#include <string>

#include "chdreader.h"
#include "discfile.h"
#include "../log.h"

namespace libretrodroid {

static std::mutex hashLock;   // one hash at a time: the readers find files through static state
static std::vector<std::pair<std::string, int>> currentFiles;

static const char* baseName(const char* p) { const char* s = strrchr(p, '/'); return s ? s + 1 : p; }

int AchievementsHash::lookup(const char* path) {
    if (!path) return -1;
    for (auto& f : currentFiles) if (f.first == path) return f.second;
    const char* base = baseName(path);
    for (auto& f : currentFiles) if (strcasecmp(baseName(f.first.c_str()), base) == 0) return f.second;
    return -1;
}

struct FdFile { int fd; int64_t pos; int64_t size; };

static void* fdOpen(const char* path) {
    int fd = AchievementsHash::lookup(path);
    if (fd < 0) { LOGW("hash: no fd for %s", path ? path : "(null)"); return nullptr; }
    off_t size = lseek(fd, 0, SEEK_END);
    return new FdFile { fd, 0, size < 0 ? 0 : (int64_t) size };
}

static void fdSeek(void* h, int64_t offset, int origin) {
    auto* f = (FdFile*) h;
    if (origin == SEEK_SET) f->pos = offset;
    else if (origin == SEEK_CUR) f->pos += offset;
    else f->pos = f->size + offset;
    if (f->pos < 0) f->pos = 0;
}

static int64_t fdTell(void* h) { return ((FdFile*) h)->pos; }

static size_t fdRead(void* h, void* buffer, size_t bytes) {
    auto* f = (FdFile*) h;
    ssize_t n = pread(f->fd, buffer, bytes, (off_t) f->pos);
    if (n <= 0) return 0;
    f->pos += n;
    return (size_t) n;
}

static void fdClose(void* h) { delete (FdFile*) h; }   // the fd itself is closed by hash()

// Wii and GameCube discs go through Dolphin's raw-disc reader (any image format). rcheevos asks for the virtual path;
// it is resolved to the app's fd, which the reader opens from the descriptor itself.
static void* discOpen(const char* path) {
    int fd = AchievementsHash::lookup(path);
    if (fd < 0) { LOGW("hash: no fd for %s", path ? path : "(null)"); return nullptr; }
    return discfile::open(fd);
}

static std::string discLibraryPath;

/** Dolphin's disc reader, dlopen'ed for one hash: a running game's own handle keeps the core loaded, otherwise it unloads. */
class DiscLibrary {
public:
    ~DiscLibrary() {
        if (!lib) return;
        discfile::setApi(nullptr);
        dlclose(lib);
    }

    bool load() {
        if (discLibraryPath.empty()) return false;
        lib = dlopen(discLibraryPath.c_str(), RTLD_NOW | RTLD_LOCAL);
        if (!lib) { LOGW("hash: disc library %s: %s", discLibraryPath.c_str(), dlerror()); return false; }
        api.open = (decltype(api.open)) dlsym(lib, "nx_disc_open");
        api.size = (decltype(api.size)) dlsym(lib, "nx_disc_size");
        api.read = (decltype(api.read)) dlsym(lib, "nx_disc_read");
        api.close = (decltype(api.close)) dlsym(lib, "nx_disc_close");
        api.open_fd = (decltype(api.open_fd)) dlsym(lib, "nx_disc_open_fd");   // optional: discfile falls back to the path
        if (!api.open || !api.size || !api.read || !api.close) {
            LOGW("hash: disc library %s lacks the nx_disc_* exports", discLibraryPath.c_str());
            return false;   // the destructor still unloads it
        }
        discfile::setApi(&api);
        return true;
    }

private:
    void* lib = nullptr;
    DiscApi api {};
};

static rc_hash_cdreader_t defaultCd;

#define WRAP_MAGIC 0x43484448u
struct CdHandle { uint32_t magic; bool chd; void* inner; };

static void* wrap(void* inner, bool chd) { return inner ? new CdHandle { WRAP_MAGIC, chd, inner } : nullptr; }
static CdHandle* unwrap(void* h) { auto* w = (CdHandle*) h; return (w && w->magic == WRAP_MAGIC) ? w : nullptr; }

static void* cdOpenTrackIt(const char* path, uint32_t track, const rc_hash_iterator_t* it) {
    void* h = chdreader_open_track(path, track);   // null unless path maps to a CHD
    if (h) return wrap(h, true);
    // rcheevos' default reader reaches its filereader through the iterator, so it needs one.
    if (it && defaultCd.open_track_iterator) {
        h = defaultCd.open_track_iterator(path, track, it);
        if (h) return wrap(h, false);
    }
    if (defaultCd.open_track) {
        h = defaultCd.open_track(path, track);
        if (h) return wrap(h, false);
    }
    return nullptr;
}

static void* cdOpenTrack(const char* path, uint32_t track) { return cdOpenTrackIt(path, track, nullptr); }

static size_t cdRead(void* h, uint32_t sector, void* buf, size_t n) {
    auto* w = unwrap(h);
    if (!w) return 0;
    if (w->chd) return chdreader_read_sector(w->inner, sector, buf, n);
    return defaultCd.read_sector ? defaultCd.read_sector(w->inner, sector, buf, n) : 0;
}

static void cdClose(void* h) {
    auto* w = unwrap(h);
    if (!w) return;
    if (w->chd) chdreader_close_track(w->inner);
    else if (defaultCd.close_track) defaultCd.close_track(w->inner);
    w->magic = 0;
    delete w;
}

static uint32_t cdFirst(void* h) {
    auto* w = unwrap(h);
    if (!w) return 0;
    if (w->chd) return chdreader_first_track_sector(w->inner);
    return defaultCd.first_track_sector ? defaultCd.first_track_sector(w->inner) : 0;
}

static rc_hash_callbacks_t hashCallbacks;
static std::once_flag callbacksOnce;

static void verbose(const char* m, const rc_hash_iterator_t*) { LOGD("rc_hash: %s", m); }
static void error(const char* m, const rc_hash_iterator_t*) { LOGW("rc_hash: %s", m); }

const rc_hash_callbacks_t* AchievementsHash::callbacks() {
    std::call_once(callbacksOnce, [] {
        memset(&hashCallbacks, 0, sizeof(hashCallbacks));
        rc_hash_get_default_cdreader(&defaultCd);
        hashCallbacks.verbose_message = verbose;
        hashCallbacks.error_message = error;
        hashCallbacks.filereader = { fdOpen, fdSeek, fdTell, fdRead, fdClose };
        hashCallbacks.cdreader = { cdOpenTrack, cdRead, cdClose, cdFirst, cdOpenTrackIt };
    });
    return &hashCallbacks;
}

void AchievementsHash::setDiscLibraryPath(const std::string& path) {
    std::lock_guard<std::mutex> lock(hashLock);
    discLibraryPath = path;
}

std::vector<std::vector<uint8_t>> AchievementsHash::chdSectors(int fd, const std::vector<uint32_t>& sectors, bool* ok) {
    std::lock_guard<std::mutex> lock(hashLock);
    std::vector<std::vector<uint8_t>> out;
    *ok = false;
    if (fd < 0) return out;
    currentFiles = {{"probe.chd", fd}};
    void* track = chdreader_open_track("probe.chd", RC_HASH_CDTRACK_FIRST_DATA);
    if (track) {
        *ok = true;
        uint32_t base = chdreader_first_track_sector(track);
        for (uint32_t s : sectors) {
            std::vector<uint8_t> buf(2048);
            if (chdreader_read_sector(track, base + s, buf.data(), buf.size()) != buf.size()) buf.clear();
            out.push_back(std::move(buf));
        }
        chdreader_close_track(track);
    }
    close(fd);
    currentFiles.clear();
    return out;
}

std::string AchievementsHash::hash(uint32_t consoleId, std::vector<std::pair<std::string, int>> files) {
    std::lock_guard<std::mutex> lock(hashLock);
    std::string result;
    if (files.empty()) return result;
    currentFiles = std::move(files);

    rc_hash_iterator_t it;
    rc_hash_initialize_iterator(&it, currentFiles[0].first.c_str(), nullptr, 0);
    it.callbacks = *callbacks();
    DiscLibrary disc;   // loaded for this hash only: a Wii/GameCube console, and unloaded again on return
    if ((consoleId == 16 || consoleId == 19) && currentFiles[0].second >= 0 && disc.load()) {
        // The magic is read through the same reader, so a compressed image still tells GameCube from Wii. Without a
        // disc magic (a WAD, an unreadable file) the plain fd reader hashes it.
        bool found = false;
        uint32_t console = discfile::consoleFor(currentFiles[0].second, consoleId, &found);
        if (found) {
            consoleId = console;
            it.callbacks.filereader = { discOpen, discfile::seek, discfile::tell, discfile::read, discfile::close };
        }
    }
    char out[33] = {0};
    if (rc_hash_generate(out, consoleId, &it)) result = out;
    rc_hash_destroy_iterator(&it);

    for (auto& f : currentFiles) if (f.second >= 0) close(f.second);
    currentFiles.clear();
    return result;
}

}
