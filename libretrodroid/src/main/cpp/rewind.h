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

#ifndef LIBRETRODROID_REWIND_H
#define LIBRETRODROID_REWIND_H

#include <atomic>
#include <condition_variable>
#include <cstddef>
#include <cstdint>
#include <deque>
#include <functional>
#include <memory>
#include <mutex>
#include <thread>
#include <vector>

#include "rewindring.h"

namespace libretrodroid {

/**
 * nx-mobile 2026-10-24: rewind, ported from the handheld's ma_rewind.c. The GL thread serializes the core into a free
 * capture-pool slot after retro_run; one worker thread XORs it against the previous stored state, LZ4-compresses it
 * and writes it to the ring, in capture order. Every 60th entry is a keyframe (the whole state), so the ring can drop
 * its oldest entries without losing the chain.
 * - Only the worker writes, FIFO: a capture that finds no free slot within 2 ms is skipped, never written from the GL
 *   thread, and the delta reference moves only after a stored write — a newer state committed ahead of an older one, or
 *   a reference moved past a failed write, breaks every later delta (the handheld's d10c2509).
 * - clear() bumps a generation before draining the queue, so captures still queued are dropped, not stored into the
 *   cleared ring. The worker sleeps on a condition variable; nothing spins.
 * - stepBack pops the newest entry and restores the one before it, which becomes the new delta reference: playing on
 *   after a rewind continues the chain from there (NextUI #577). A state the core refuses is dropped from the newest end,
 *   so the next step goes past it instead of retrying it forever (633c3937).
 * - configure rebuilds only when an effective value changed: the handheld reallocated many MB per config key (1ccc1030).
 * Not thread-safe across callers: LibretroDroid makes every call under its core lock (step(), reset, state load, disc
 * swap), so a clear() never runs beside a capture. The worker is internal.
 */
class Rewind {
public:
    using SerializeFn = std::function<bool(uint8_t*, size_t)>;
    using UnserializeFn = std::function<bool(const uint8_t*, size_t)>;

    enum class Step {
        REWOUND, // the core now holds an earlier state
        EMPTY,   // nothing older to go back to (or the step failed): the core is unchanged
        OFF,     // rewind is off
    };

    /** The ring buffer asked of rewindBudget, before the RAM cap. */
    static constexpr size_t DEFAULT_BUFFER_BYTES = 64 * 1024 * 1024;

    explicit Rewind(size_t wantBytes = DEFAULT_BUFFER_BYTES) : wantBytes(wantBytes) {}
    Rewind(const Rewind&) = delete;
    Rewind& operator=(const Rewind&) = delete;
    ~Rewind();

    /**
     * Builds (or frees) the buffers for states of [stateSize] bytes. A repeat of the last request does nothing, also when
     * that request left rewind off (no RAM for it: the budget is not retried until the size changes). Size 0 is off.
     */
    void configure(bool enabled, size_t stateSize);

    /** After retro_run, core lock held. Serializes on every interval()-th call; a serialize failure turns rewind off. */
    void capture(const SerializeFn& serialize);

    /** Waits for queued captures, then restores the state before the newest entry. */
    Step stepBack(const UnserializeFn& unserialize);

    /** Drops the history (reset, state load, disc swap), including captures still queued. */
    void clear();

    bool enabled() const { return isEnabled.load(); }

    /** Capture every Nth frame: 1 for states up to 1 MB, 2 up to 8 MB, 4 above (snapshotting costs frame time). */
    int interval() const { return captureInterval; }

    /** Blocks until the worker has stored (or dropped) every queued capture. */
    void waitIdle();

    /** How many times the buffers were built; tests check configure doesn't rebuild. */
    int builds() const { return buildCount; }

private:
    void build(size_t stateSize);
    void teardown();
    void workerLoop();
    // ringLock held. Encodes src (a delta unless a keyframe is due) and stores it.
    void storeLocked(const uint8_t* src);
    // ringLock held. Compresses src or its delta into scratch; returns the length, 0 on failure.
    size_t compressLocked(const uint8_t* src, bool keyframe);
    // ringLock held. Decompresses entry k into out; false when it isn't a whole state.
    bool decompressLocked(int k, std::vector<uint8_t>& out);
    // ringLock held. Decodes the state of entry k into stateDec, from the keyframe at or before it.
    bool decodeForwardLocked(int k);
    void recountSinceKeyframeLocked();
    void releaseSlot(int slot);

    const size_t wantBytes;

    // The last configure() request, and what it built.
    bool requestedEnabled = false;
    size_t requestedSize = 0;
    std::atomic<bool> isEnabled { false };
    size_t stateSize = 0;
    int captureInterval = 1;
    unsigned tick = 0;
    int buildCount = 0;

    // Bumped by clear(); a queued capture from an older generation is dropped.
    std::atomic<unsigned> generation { 1 };

    // ringLock: the ring and the worker's encode state; also the GL thread's decode.
    std::mutex ringLock;
    std::unique_ptr<RewindRing> ring;
    std::vector<uint8_t> reference;   // the state the ring's newest entry decodes to: the next delta's base
    bool hasReference = false;
    int sinceKeyframe = 0;            // entries since the newest keyframe, that one included
    std::vector<uint8_t> deltaEnc;
    std::vector<uint8_t> scratch;     // compressed output
    std::vector<uint8_t> stateDec;
    std::vector<uint8_t> deltaDec;

    // queueLock: the capture pool. queueCv wakes the worker; slotCv tells the GL thread a slot is free.
    struct Slot {
        std::vector<uint8_t> data;
        unsigned generation = 0;
    };
    std::mutex queueLock;
    std::condition_variable queueCv;
    std::condition_variable slotCv;
    std::vector<Slot> pool;
    std::vector<int> freeSlots;
    std::deque<int> queue;
    bool stopWorker = false;
    std::thread worker;
};

}

#endif
