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

#ifndef LIBRETRODROID_CHEATMEM_H
#define LIBRETRODROID_CHEATMEM_H

#include <cstddef>
#include <cstdint>

namespace libretrodroid {

// RetroArch's frontend-side ("handler 1") cheats: no code for the core, just a
// RAM patch the frontend applies every frame. The same logic as nx-redux's
// ma_cheat_mem.h; pure, host-tested by src/test/cpp/cheatmem_test.cpp.
// LibretroDroid owns the patches and the memory.

#define CHEAT_MEM_MAX_REGIONS 64

// cheatN_cheat_type values
enum {
	CHEAT_MEM_DISABLED = 0,
	CHEAT_MEM_SET_TO_VALUE,
	CHEAT_MEM_INCREASE_VALUE,
	CHEAT_MEM_DECREASE_VALUE,
	CHEAT_MEM_RUN_NEXT_IF_EQ,
	CHEAT_MEM_RUN_NEXT_IF_NEQ,
	CHEAT_MEM_RUN_NEXT_IF_LT,
	CHEAT_MEM_RUN_NEXT_IF_GT,
};

struct CheatMemPatch {
	int memory;		  // cheatN_handler = 1
	unsigned address; // offset into the concatenated RAM regions
	unsigned value;
	unsigned size;	   // cheatN_memory_search_size: 0-2 = 1/2/4 bits, 3 = 8, 4 = 16, 5 = 32
	unsigned type;	   // CHEAT_MEM_*
	unsigned bit_mask; // cheatN_address_bit_position: bits written for sizes 0-2
	int big_endian;
	unsigned repeat_count; // default 1
	unsigned repeat_add_to_value;
	unsigned repeat_add_to_address; // default 1
};

// The core's system RAM, as RetroArch sees it: the SYSTEM_RAM regions of its
// memory map in order, or else RETRO_MEMORY_SYSTEM_RAM.
typedef struct {
	uint8_t* ptr[CHEAT_MEM_MAX_REGIONS];
	size_t len[CHEAT_MEM_MAX_REGIONS];
	int count;
	size_t total;
} CheatMem;

void CheatMem_reset(CheatMem* mem);
// False when the table is full and the region was left out (an empty or null region is skipped and counts as taken).
bool CheatMem_addRegion(CheatMem* mem, void* ptr, size_t len);

void CheatMemPatch_init(struct CheatMemPatch* p);

// Apply the enabled memory cheats, in file order, once (one frame). A false
// RUN_NEXT_IF_* condition (or one that can't be read) skips the next cheat.
// Returns how many cheats wrote to memory.
int CheatMem_apply(const CheatMem* mem, const struct CheatMemPatch* const* patches, int n);

}  // namespace libretrodroid

#endif  // LIBRETRODROID_CHEATMEM_H
