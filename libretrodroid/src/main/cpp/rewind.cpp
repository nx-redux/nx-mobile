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

#include "rewind.h"

#include <algorithm>
#include <chrono>
#include <cstring>
#include <new>

#include "log.h"
#include "lz4/lz4.h"
#include "rewindbudget.h"

namespace libretrodroid {

namespace {

constexpr size_t MB = 1024 * 1024;
constexpr int KEYFRAME_EVERY = 60;
// Sizes the entry table: an average compressed delta; the ring's bytes run out before its slots for bigger ones.
// 64 MB gives 16384 entries, over four minutes at 60 fps.
constexpr size_t ENTRY_SIZE_HINT = 4096;
// Well over a keyframe run: a full table drops its oldest entry, a keyframe, and the deltas after it go with it.
constexpr int MIN_ENTRIES = 4 * KEYFRAME_EVERY;
// A deeper pool for big states, whose serialize and compress take longer (the handheld's 3 / 4 at 2 MB).
constexpr size_t LARGE_STATE = 2 * MB;
constexpr int POOL_SMALL = 3;
constexpr int POOL_LARGE = 4;
// The handheld's default: a little faster than LZ4's 1, for a slightly bigger entry.
constexpr int LZ4_ACCELERATION = 2;
constexpr auto SLOT_WAIT = std::chrono::milliseconds(2);

void xorInto(uint8_t* out, const uint8_t* a, const uint8_t* b, size_t len) {
    for (size_t i = 0; i < len; i++) out[i] = a[i] ^ b[i];
}

}

Rewind::~Rewind() {
    teardown();
}

void Rewind::configure(bool enabled, size_t size) {
    const bool on = enabled && size > 0;
    if (on == requestedEnabled && (!on || size == requestedSize)) return;
    requestedEnabled = on;
    requestedSize = size;
    teardown();
    if (on) build(size);
}

void Rewind::build(size_t size) {
    const int poolSize = size > LARGE_STATE ? POOL_LARGE : POOL_SMALL;
    // Measured after teardown(): a rebuild counts the old buffers as free.
    const size_t capacity = rewindBudget(wantBytes, size, poolSize, availableMemoryBytes());
    if (capacity == 0) {
        LOGW("Rewind: %zu-byte states leave no room for a rewind buffer; rewind off", size);
        return;
    }
    if (capacity < wantBytes) LOGW("Rewind: buffer cut to %zu MB to fit in RAM", capacity / MB);

    try {
        const int entries = std::max(static_cast<int>(capacity / ENTRY_SIZE_HINT), MIN_ENTRIES);
        ring = std::make_unique<RewindRing>(capacity, entries);
        reference.assign(size, 0);
        deltaEnc.assign(size, 0);
        scratch.assign(static_cast<size_t>(LZ4_compressBound(static_cast<int>(size))), 0);
        stateDec.assign(size, 0);
        deltaDec.assign(size, 0);
        pool.resize(poolSize);
        for (auto& slot : pool) slot.data.assign(size, 0);
    } catch (const std::bad_alloc&) {
        LOGE("Rewind: out of memory for %zu-byte states; rewind off", size);
        teardown();
        return;
    }
    freeSlots.clear();
    for (int i = 0; i < poolSize; i++) freeSlots.push_back(i);
    queue.clear();
    hasReference = false;
    sinceKeyframe = 0;
    tick = 0;
    stateSize = size;
    captureInterval = size <= MB ? 1 : (size <= 8 * MB ? 2 : 4);
    stopWorker = false;
    worker = std::thread([this] { workerLoop(); });
    buildCount++;
    isEnabled = true;
    LOGI("Rewind: %zu-byte states, %zu MB buffer, every %d frame(s)", size, capacity / MB, captureInterval);
}

void Rewind::teardown() {
    if (worker.joinable()) {
        {
            std::lock_guard<std::mutex> lock(queueLock);
            stopWorker = true;
        }
        queueCv.notify_all();
        worker.join();
    }
    isEnabled = false;
    std::lock_guard<std::mutex> lock(ringLock);
    ring.reset();
    // Swapped with empties so the memory is returned, not just the sizes zeroed.
    std::vector<uint8_t>().swap(reference);
    std::vector<uint8_t>().swap(deltaEnc);
    std::vector<uint8_t>().swap(scratch);
    std::vector<uint8_t>().swap(stateDec);
    std::vector<uint8_t>().swap(deltaDec);
    std::vector<Slot>().swap(pool);
    freeSlots.clear();
    queue.clear();
    hasReference = false;
    stateSize = 0;
}

void Rewind::capture(const SerializeFn& serialize) {
    if (!isEnabled) return;
    if (++tick % static_cast<unsigned>(captureInterval) != 0) return;

    int slot;
    {
        std::unique_lock<std::mutex> lock(queueLock);
        // The worker is behind: skip this capture rather than write it out of order from here.
        if (!slotCv.wait_for(lock, SLOT_WAIT, [this] { return !freeSlots.empty(); })) return;
        slot = freeSlots.back();
        freeSlots.pop_back();
    }

    if (!serialize(pool[slot].data.data(), stateSize)) {
        // A refused serialize, or a core whose state grew past the size this was built for.
        LOGW("Rewind: the core refused to serialize; rewind off");
        releaseSlot(slot);
        configure(false, requestedSize);
        return;
    }

    pool[slot].generation = generation.load();
    {
        std::lock_guard<std::mutex> lock(queueLock);
        queue.push_back(slot);
    }
    queueCv.notify_one();
}

void Rewind::releaseSlot(int slot) {
    {
        std::lock_guard<std::mutex> lock(queueLock);
        freeSlots.push_back(slot);
    }
    slotCv.notify_all();
}

void Rewind::waitIdle() {
    if (!isEnabled) return;
    std::unique_lock<std::mutex> lock(queueLock);
    // Captures run on the caller's thread, so no slot can be taken while this waits.
    slotCv.wait(lock, [this] { return queue.empty() && freeSlots.size() == pool.size(); });
}

void Rewind::workerLoop() {
    while (true) {
        int slot;
        {
            std::unique_lock<std::mutex> lock(queueLock);
            queueCv.wait(lock, [this] { return stopWorker || !queue.empty(); });
            if (queue.empty()) return;   // stopping, nothing left
            slot = queue.front();
            queue.pop_front();
        }
        {
            std::lock_guard<std::mutex> lock(ringLock);
            // A capture from before a clear(): dropped, never stored into the cleared ring.
            if (pool[slot].generation == generation.load()) storeLocked(pool[slot].data.data());
        }
        releaseSlot(slot);
    }
}

size_t Rewind::compressLocked(const uint8_t* src, bool keyframe) {
    const uint8_t* in = src;
    if (!keyframe) {
        // Consecutive states differ in few bytes: the XOR is mostly zeros, which LZ4 packs small and fast.
        xorInto(deltaEnc.data(), src, reference.data(), stateSize);
        in = deltaEnc.data();
    }
    int len = LZ4_compress_fast(reinterpret_cast<const char*>(in), reinterpret_cast<char*>(scratch.data()),
                                static_cast<int>(stateSize), static_cast<int>(scratch.size()), LZ4_ACCELERATION);
    return len > 0 ? static_cast<size_t>(len) : 0;
}

void Rewind::storeLocked(const uint8_t* src) {
    bool keyframe = !hasReference || sinceKeyframe >= KEYFRAME_EVERY;
    size_t len = compressLocked(src, keyframe);
    if (len == 0) {
        LOGE("Rewind: compression failed");
        return;
    }
    RewindRing::Write result = ring->write(scratch.data(), len, keyframe);
    if (result == RewindRing::Write::NEED_KEYFRAME) {
        // The trim took the delta's keyframe with it and left the ring empty: this entry starts a new chain.
        keyframe = true;
        len = compressLocked(src, true);
        if (len == 0) {
            LOGE("Rewind: compression failed");
            return;
        }
        result = ring->write(scratch.data(), len, true);
    }
    switch (result) {
        case RewindRing::Write::STORED:
            // Only now: a reference moved past an entry that wasn't stored would break every later delta.
            std::memcpy(reference.data(), src, stateSize);
            hasReference = true;
            sinceKeyframe = keyframe ? 1 : sinceKeyframe + 1;
            break;
        case RewindRing::Write::TOO_BIG:
            LOGW("Rewind: a %zu-byte entry does not fit in the buffer", len);
            break;
        case RewindRing::Write::NO_ROOM:
        case RewindRing::Write::NEED_KEYFRAME:
            LOGE("Rewind: the ring could not store an entry; history dropped");
            ring->clear();
            hasReference = false;
            break;
    }
}

bool Rewind::decompressLocked(int k, std::vector<uint8_t>& out) {
    RewindRing::Entry e = ring->at(k);
    int got = LZ4_decompress_safe(reinterpret_cast<const char*>(e.data), reinterpret_cast<char*>(out.data()),
                                  static_cast<int>(e.len), static_cast<int>(stateSize));
    return got == static_cast<int>(stateSize);
}

bool Rewind::decodeForwardLocked(int k) {
    int key = k;
    while (key > 0 && !ring->at(key).keyframe) key--;
    // The ring keeps a keyframe at its oldest end; anything else is a broken chain.
    if (!ring->at(key).keyframe || !decompressLocked(key, stateDec)) return false;
    for (int i = key + 1; i <= k; i++) {
        if (!decompressLocked(i, deltaDec)) return false;
        xorInto(stateDec.data(), stateDec.data(), deltaDec.data(), stateSize);
    }
    return true;
}

void Rewind::recountSinceKeyframeLocked() {
    const int n = static_cast<int>(ring->count());
    sinceKeyframe = 0;
    for (int k = n - 1; k >= 0; k--) {
        sinceKeyframe++;
        if (ring->at(k).keyframe) return;
    }
}

Rewind::Step Rewind::stepBack(const UnserializeFn& unserialize) {
    if (!isEnabled) return Step::OFF;
    // Every capture so far is in the ring before it is read.
    waitIdle();

    std::lock_guard<std::mutex> lock(ringLock);
    const int n = static_cast<int>(ring->count());
    // The newest entry is (about) where the game is now: there must be one before it.
    if (n < 2 || !hasReference) return Step::EMPTY;

    // The state before the newest: undo the newest delta against the reference, or, when the newest is a keyframe
    // (whose bytes say nothing about the state before it), decode forward from the keyframe before that.
    bool decoded;
    if (!ring->at(n - 1).keyframe) {
        decoded = decompressLocked(n - 1, deltaDec);
        if (decoded) xorInto(stateDec.data(), deltaDec.data(), reference.data(), stateSize);
    } else {
        decoded = decodeForwardLocked(n - 2);
    }
    if (!decoded) {
        LOGE("Rewind: an entry did not decompress; history dropped");
        ring->clear();
        hasReference = false;
        return Step::EMPTY;
    }

    // The restored state is the new reference: playing on from here continues the chain.
    ring->popNewest();
    reference.swap(stateDec);
    recountSinceKeyframeLocked();

    if (!unserialize(reference.data(), stateSize)) {
        // Its entry stays the newest, so the next step goes past it: a bad state is skipped, not retried forever.
        LOGW("Rewind: the core refused a rewound state");
        return Step::EMPTY;
    }
    return Step::REWOUND;
}

void Rewind::clear() {
    if (!isEnabled) return;
    // Before the drain: what is still queued takes the worker's stale path instead of landing in the cleared ring.
    generation.fetch_add(1);
    waitIdle();
    std::lock_guard<std::mutex> lock(ringLock);
    ring->clear();
    hasReference = false;
    sinceKeyframe = 0;
    tick = 0;
}

}
