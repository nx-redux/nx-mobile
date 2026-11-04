#include "threadlist.h"

#include <algorithm>
#include <cstdlib>
#include <dirent.h>
#include <cstdio>
#include <cstring>
#include <iterator>
#include <string>

namespace libretrodroid {

std::vector<int> ThreadList::list(const char* taskDir) {
    std::vector<int> ids;
    DIR* dir = opendir(taskDir);
    if (dir == nullptr) return ids;
    while (dirent* entry = readdir(dir)) {
        char* end = nullptr;
        const long id = std::strtol(entry->d_name, &end, 10);
        if (end != entry->d_name && *end == '\0' && id > 0) ids.push_back(static_cast<int>(id));
    }
    closedir(dir);
    std::sort(ids.begin(), ids.end());
    return ids;
}

std::vector<int> ThreadList::added(const std::vector<int>& before, const std::vector<int>& after) {
    std::vector<int> result;
    std::set_difference(after.begin(), after.end(), before.begin(), before.end(), std::back_inserter(result));
    return result;
}

std::vector<int> ThreadList::emulation(const std::vector<int>& ids, const char* taskDir) {
    // Threads an Android app process starts for itself, not for the game: matched by the start of their name.
    static const char* const notEmulation[] = {
        "AudioTrack", "AAudio", "binder:", "HwBinder", "RenderThread", "hwuiTask", "DefaultDispatch", "OkHttp", "Okio",
        "FinalizerDaemon", "FinalizerWatchd", "ReferenceQueueD", "HeapTaskDaemon", "Jit thread pool", "Signal Catcher",
        "Profile Saver", "queued-work-loo", "AsyncTask", "pool-", "Timer-", "ConnectivityThr", "GmsDynamite",
    };
    std::vector<int> result;
    for (int id : ids) {
        const std::string path = std::string(taskDir) + "/" + std::to_string(id) + "/comm";
        FILE* f = std::fopen(path.c_str(), "r");
        if (f == nullptr) continue;   // exited
        char name[64] = {};
        const bool read = std::fgets(name, sizeof(name), f) != nullptr;
        std::fclose(f);
        if (!read) continue;
        bool keep = true;
        for (const char* prefix : notEmulation) {
            if (std::strncmp(name, prefix, std::strlen(prefix)) == 0) { keep = false; break; }
        }
        if (keep) result.push_back(id);
    }
    return result;
}

}
