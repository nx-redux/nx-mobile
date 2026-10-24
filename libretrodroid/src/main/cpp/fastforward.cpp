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
#include "fastforward.h"

#include <algorithm>
#include <cmath>

namespace libretrodroid {

namespace {

// A gap this long between steps is a pause or a stall, not the device's pace: leave the measured speed alone.
constexpr auto MEASURE_GAP = std::chrono::milliseconds(250);
// Time constant of the smoothed speed, in seconds: by wall time, so it reacts the same at any display rate, and
// follows a heavy scene in about a second.
constexpr double SPEED_SMOOTHING_SECONDS = 0.3;

}

void FastForwardPacer::start(double contentFps, unsigned maxSpeed, TimePoint now) {
    fps = contentFps > 0.0 ? contentFps : 60.0;
    cap = std::max(maxSpeed, 1u);
    frameInterval = std::chrono::duration_cast<TimePoint::duration>(std::chrono::duration<double>(1.0 / fps));
    interval = std::chrono::duration_cast<TimePoint::duration>(std::chrono::duration<double>(1.0 / (fps * cap)));
    due = now;
    lastStep = now;
    lastFrames = 0;
    speed = cap;
    running = true;
}

void FastForwardPacer::stop() {
    running = false;
    lastFrames = 0;
}

unsigned FastForwardPacer::framesFor(TimePoint now) {
    if (!running) return 0;

    // Frames due within half a frame of now: a display ticking at exactly the capped rate then runs one per step
    // through scheduling jitter instead of alternating 0 and 2. Over time the count is the same.
    unsigned frames = 0;
    const auto horizon = now + interval / 2;
    if (horizon >= due) {
        const auto behind = (horizon - due) / interval + 1;
        if (behind > cap) {
            frames = cap;
            due = now + interval;   // resync: the debt is dropped, not burst through
        } else {
            frames = static_cast<unsigned>(behind);
            due += interval * frames;
        }
    }

    const auto gap = now - lastStep;
    if (gap > TimePoint::duration::zero() && gap < MEASURE_GAP) {
        const double seconds = std::chrono::duration<double>(gap).count();
        const double ran = frames / (seconds * fps);
        speed += (ran - speed) * (1.0 - std::exp(-seconds / SPEED_SMOOTHING_SECONDS));
        speed = std::clamp(speed, 1.0, static_cast<double>(cap));
    }
    lastStep = now;
    lastFrames = frames;
    return frames;
}

Duration FastForwardPacer::sleepFor(TimePoint now) const {
    if (!running || lastFrames > 0 || due <= now) return Duration::zero();
    return std::chrono::duration_cast<Duration>(std::min(due - now, frameInterval));
}

} // namespace libretrodroid
