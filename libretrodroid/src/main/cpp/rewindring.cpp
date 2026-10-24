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

#include "rewindring.h"

#include <cstring>

namespace libretrodroid {

RewindRing::RewindRing(size_t capacity, int entryCapacity)
    : buffer_(capacity), slots_(entryCapacity > 0 ? entryCapacity : 1) {}

size_t RewindRing::freeSpace() const {
    if (count_ == 0) return buffer_.size();
    if (head_ == tail_) return 0;
    if (head_ >= tail_) return buffer_.size() - (head_ - tail_);
    return tail_ - head_;
}

void RewindRing::dropOldest() {
    if (count_ == 0) return;
    const Slot& e = slots_[slotTail_];
    tail_ = (e.offset + e.size) % buffer_.size();
    slotTail_ = (slotTail_ + 1) % static_cast<int>(slots_.size());
    count_--;
    if (count_ == 0) head_ = tail_ = 0;
}

bool RewindRing::overlaps(const Slot& slot, size_t start, size_t end) const {
    return slot.offset < end && start < slot.offset + slot.size;
}

RewindRing::Write RewindRing::write(const uint8_t* data, size_t len, bool keyframe) {
    if (len >= buffer_.size()) return Write::TOO_BIG;

    // A full table drops its oldest first, so the write can't overwrite that slot's metadata.
    if (count_ == static_cast<int>(slots_.size())) dropOldest();

    size_t writeOffset = head_;
    if (writeOffset + len > buffer_.size()) {
        writeOffset = 0;
        head_ = 0;
        if (count_ == 0) tail_ = 0;
    }

    // Entries can only leave from the tail (the delta chain stays contiguous), but after a wrap the overlapping ones
    // are the newer entries at low offsets, behind a non-overlapping oldest one: drop up to the newest overlapping.
    int drops = 0;
    for (int k = 0; k < count_; k++) {
        const Slot& s = slots_[(slotTail_ + k) % slots_.size()];
        if (overlaps(s, writeOffset, writeOffset + len)) drops = k + 1;
    }
    while (drops-- > 0) dropOldest();

    while (count_ > 0 && freeSpace() <= len) dropOldest();
    if (count_ > 0 && freeSpace() <= len) return Write::NO_ROOM;

    // A delta needs its keyframe: drop the leading deltas that lost theirs, so the chain starts at a keyframe.
    while (count_ > 0 && !slots_[slotTail_].keyframe) dropOldest();

    // A delta with no keyframe left under it (the trim emptied the ring, or it was the first write) can't be replayed.
    // The ring is then empty, so the caller starts a new chain with a keyframe.
    if (count_ == 0) {
        head_ = tail_ = 0;
        writeOffset = 0;
        if (!keyframe) return Write::NEED_KEYFRAME;
    }

    std::memcpy(buffer_.data() + writeOffset, data, len);

    Slot& slot = slots_[slotHead_];
    slot.offset = writeOffset;
    slot.size = len;
    slot.keyframe = keyframe;

    head_ = writeOffset + len;
    if (head_ >= buffer_.size()) head_ = 0;
    slotHead_ = (slotHead_ + 1) % static_cast<int>(slots_.size());
    count_++;
    return Write::STORED;
}

RewindRing::Entry RewindRing::at(int k) const {
    const Slot& s = slots_[(slotTail_ + k) % slots_.size()];
    return Entry{buffer_.data() + s.offset, s.size, s.keyframe};
}

RewindRing::Entry RewindRing::newest() const {
    int idx = slotHead_ - 1;
    if (idx < 0) idx += static_cast<int>(slots_.size());
    const Slot& s = slots_[idx];
    return Entry{buffer_.data() + s.offset, s.size, s.keyframe};
}

void RewindRing::popNewest() {
    if (count_ == 0) return;
    int idx = slotHead_ - 1;
    if (idx < 0) idx += static_cast<int>(slots_.size());
    slotHead_ = idx;
    count_--;
    if (count_ == 0) head_ = tail_ = 0;
    else head_ = slots_[idx].offset;
}

void RewindRing::clear() {
    head_ = tail_ = 0;
    slotHead_ = slotTail_ = 0;
    count_ = 0;
}

}
