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

#ifndef LIBRETRODROID_VULKANHANDOFF_H
#define LIBRETRODROID_VULKANHANDOFF_H

#include <cstddef>
#include <cstdint>
#include <utility>
#include <vector>

// How the Vulkan bridge gets a frame to the GLES side, and the bookkeeping of the Reader way. Pure: no Vulkan, no Android.
namespace libretrodroid {

// Import: the frame is copied into an AHardwareBuffer the driver imported. Reader: into a swapchain image presented to
// an AImageReader, for a driver that cannot import one. None: the driver can do neither.
enum class HandoffMode : int { None = 0, Import = 1, Reader = 2 };

// What a driver's pre-check found: the import is preferred, the reader needs the swapchain extension and a frame that
// came out of it.
inline HandoffMode chooseHandoff(bool importWorks, bool hasSwapchain, bool readerWorks) {
    if (importWorks) return HandoffMode::Import;
    return hasSwapchain && readerWorks ? HandoffMode::Reader : HandoffMode::None;
}

// An id per live reader buffer, for the GLES side's import cache. Never reused, also across clear(): a swapchain made
// anew may hand out a buffer at an old one's address, and that must be imported again.
class BufferIds {
public:
    uint64_t idFor(const void* buffer) {
        for (const auto& known : ids) {
            if (known.first == buffer) return known.second;
        }
        ids.emplace_back(buffer, next);
        return next++;
    }
    void clear() { ids.clear(); }
    // The lowest id of the buffers now live (0: none yet): an import with a lower id is of a swapchain that is gone.
    uint64_t firstLive() const { return ids.empty() ? 0 : ids.front().second; }

private:
    std::vector<std::pair<const void*, uint64_t>> ids;
    uint64_t next = 1;
};

// The reader images the GLES side may still be sampling, oldest first. An image goes back to the swapchain once a
// newer one has been drawn, and at the latest when more than [budget] are held: several frames can be bridged per draw
// (fast forward), and a reader that has given out all its images refuses the next.
template <typename T>
class HeldImages {
public:
    explicit HeldImages(size_t budget) : budget(budget) {}

    void push(T image, std::vector<T>& release) {
        images.push_back(image);
        size_t drop = drawn > 0 ? drawn - 1 : 0;   // everything before the newest drawn one
        if (images.size() - drop > budget) drop = images.size() - budget;
        for (size_t i = 0; i < drop; i++) release.push_back(images[i]);
        images.erase(images.begin(), images.begin() + (std::ptrdiff_t) drop);
        drawn = drawn > drop ? drawn - drop : 0;
    }

    // The GL draw that samples the newest image has been issued.
    void markDrawn() { drawn = images.size(); }

    void drain(std::vector<T>& release) {
        for (const T& image : images) release.push_back(image);
        images.clear();
        drawn = 0;
    }

    size_t size() const { return images.size(); }

private:
    size_t budget;
    std::vector<T> images;
    size_t drawn = 0;   // how many, from the oldest, a draw has covered
};

// Tells a Reader hand-off that has stopped working from a frame that was merely dropped. True once. At the start: none
// of the first START_FRAMES frames got through, or (a driver whose every failed frame blocks for seconds) at least
// START_MIN_FRAMES failed over START_MS. Later: frames did flow, and then MIDGAME_FRAMES in a row failed over
// MIDGAME_MS or longer. The run of frames matters as much as the time: no frames arrive while the game is paused, so
// two dropped frames either side of a pause are far apart on the clock and still only two.
class FailureStreak {
public:
    static constexpr int START_FRAMES = 30;
    static constexpr int START_MIN_FRAMES = 3;
    static constexpr int64_t START_MS = 5000;
    static constexpr int MIDGAME_FRAMES = 30;
    static constexpr int64_t MIDGAME_MS = 2000;

    bool onFrame(bool ok, int64_t nowMs) {
        if (ok) {
            succeeded = true;
            run = 0;
            return false;
        }
        if (fired) return false;
        if (run++ == 0) runSince = nowMs;
        const int64_t elapsed = nowMs - runSince;
        const bool broken = succeeded
            ? run >= MIDGAME_FRAMES && elapsed >= MIDGAME_MS
            : run >= START_FRAMES || (run >= START_MIN_FRAMES && elapsed >= START_MS);
        if (broken) fired = true;
        return broken;
    }

private:
    bool succeeded = false;
    bool fired = false;
    int run = 0;            // failed frames in a row
    int64_t runSince = 0;   // when that run began
};

} // namespace libretrodroid

#endif // LIBRETRODROID_VULKANHANDOFF_H
