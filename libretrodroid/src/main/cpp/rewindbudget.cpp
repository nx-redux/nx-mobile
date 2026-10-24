/*
 *     Copyright (C) 2026  Mohammad Syuhada
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include "rewindbudget.h"

namespace libretrodroid {

size_t rewindBudget(size_t want, size_t stateSize, int pool, uint64_t availableBytes) {
    if (!availableBytes) return want;
    uint64_t budget = availableBytes / 2;
    uint64_t fixed = static_cast<uint64_t>(stateSize) * static_cast<uint64_t>(5 + (pool > 0 ? pool : 0));
    if (fixed + want <= budget) return want;
    if (fixed >= budget) return 0;
    uint64_t room = budget - fixed;
    room -= room % (1024 * 1024);
    if (room < REWIND_BUDGET_MIN_BUFFER) return 0;
    return static_cast<size_t>(room);
}

uint64_t parseMemAvailable(FILE* meminfo) {
    char line[256];
    unsigned long long kb;
    while (fgets(line, sizeof(line), meminfo)) {
        if (sscanf(line, "MemAvailable: %llu kB", &kb) == 1) return static_cast<uint64_t>(kb) * 1024;
    }
    return 0;
}

uint64_t availableMemoryBytes() {
    FILE* f = fopen("/proc/meminfo", "r");
    if (!f) return 0;
    uint64_t bytes = parseMemAvailable(f);
    fclose(f);
    return bytes;
}

}
