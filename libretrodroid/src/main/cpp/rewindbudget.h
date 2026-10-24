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

#ifndef LIBRETRODROID_REWINDBUDGET_H
#define LIBRETRODROID_REWINDBUDGET_H

#include <cstddef>
#include <cstdint>
#include <cstdio>

namespace libretrodroid {

/** Smallest ring buffer worth keeping; below this rewind is turned off. */
constexpr size_t REWIND_BUDGET_MIN_BUFFER = 1024 * 1024;

/**
 * RAM budget for the rewind buffer. Besides the ring, rewind holds about (5 + pool) copies of the save state (state,
 * LZ4 scratch, two previous states, delta, capture pool); with a 30-40 MB state that is more than a 1 GB device can
 * spare. Rewind may use half of what is available: returns [want] when it fits, a smaller whole-MB size when only that
 * fits, or 0 when not even REWIND_BUDGET_MIN_BUFFER fits. [availableBytes] 0 means unknown, no limit.
 */
size_t rewindBudget(size_t want, size_t stateSize, int pool, uint64_t availableBytes);

/** Parses MemAvailable from a /proc/meminfo stream. Returns bytes, or 0 if absent. */
uint64_t parseMemAvailable(FILE* meminfo);

/** MemAvailable of this system in bytes, or 0 if it can't be read. */
uint64_t availableMemoryBytes();

}

#endif
