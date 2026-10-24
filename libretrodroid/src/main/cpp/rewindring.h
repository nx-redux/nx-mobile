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

#ifndef LIBRETRODROID_REWINDRING_H
#define LIBRETRODROID_REWINDRING_H

#include <cstddef>
#include <cstdint>
#include <vector>

namespace libretrodroid {

/**
 * The rewind ring buffer: compressed entries packed into one byte buffer, indexed by a fixed table. Ported from the
 * handheld's ma_rewind_ring.c. Entries are a delta chain, so they only ever leave from the oldest end, and a delta
 * never outlives the keyframe it builds on: the oldest stored entry is always a keyframe. Not thread-safe; the caller
 * holds its own lock.
 */
class RewindRing {
public:
    enum class Write {
        STORED,
        TOO_BIG, // the entry is not smaller than the whole buffer
        NO_ROOM, // could not make room (a logic error), or a delta had no keyframe left under it: the ring is empty
    };

    struct Entry {
        const uint8_t* data;
        size_t len;
        bool keyframe;
    };

    RewindRing(size_t capacity, int entryCapacity);

    /** Stores an entry, dropping the oldest entries it would overlap or that leave too little room. */
    Write write(const uint8_t* data, size_t len, bool keyframe);

    bool empty() const { return count_ == 0; }
    size_t count() const { return count_; }

    /** The newest entry; the ring must not be empty. */
    Entry newest() const;

    /** Pops the newest entry and reclaims its bytes, so rewind-then-play cycles don't shrink the usable history. */
    void popNewest();

    void clear();

private:
    struct Slot {
        size_t offset = 0;
        size_t size = 0;
        bool keyframe = false;
    };

    // head == tail with entries means the buffer wrapped and is full
    size_t freeSpace() const;
    void dropOldest();
    bool overlaps(const Slot& slot, size_t start, size_t end) const;

    std::vector<uint8_t> buffer_;
    std::vector<Slot> slots_;
    size_t head_ = 0;
    size_t tail_ = 0;
    int slotHead_ = 0;
    int slotTail_ = 0;
    int count_ = 0;
};

}

#endif
