#include "threadlist.h"

#include <algorithm>
#include <cstdlib>
#include <dirent.h>
#include <iterator>

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

}
