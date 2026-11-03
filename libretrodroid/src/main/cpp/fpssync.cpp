/*
 *     Copyright (C) 2019  Filippo Scognamiglio
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

#include <cmath>
#include "fpssync.h"
#include "log.h"

namespace libretrodroid {

unsigned FPSSync::advanceFrames() {
    return advanceFrames(std::chrono::steady_clock::now());
}

// nx-mobile 2026-11-03: a step that ends a frame or two late shows its frame late instead of running two frames at
// once (upstream caught up at one frame late, skipping a frame and showing the other for 33 ms: SMG2's stutters on the
// Nova). The clock stays behind, so the next steps skip their sleep until they catch up. Only past MAX_LAG_FRAMES (a
// device that cannot keep up) does it run frames back to back to hold the game's speed.
unsigned FPSSync::advanceFrames(TimePoint now) {
    if (useVSync) return 1;

    if (lastFrame == MIN_TIME) {
        start(now);
    }

    const long long behind = (now - lastFrame) / sampleInterval;
    const long long frames = behind > MAX_LAG_FRAMES ? behind : 1;
    lastFrame = lastFrame + sampleInterval * frames;

    return frames;
}

FPSSync::FPSSync(double contentRefreshRate, double screenRefreshRate) {
    this->contentRefreshRate = contentRefreshRate;
    this->screenRefreshRate = screenRefreshRate;
    this->useVSync = std::abs(contentRefreshRate - screenRefreshRate) < FPS_TOLERANCE;
    this->sampleInterval = std::chrono::microseconds((long) ((1000000L / contentRefreshRate)));
    reset();
}

void FPSSync::start(TimePoint now) {
    LOGI("Starting game with fps %f on a screen with refresh rate %f. Using vsync: %d", contentRefreshRate, screenRefreshRate, useVSync);
    lastFrame = now;
}

std::optional<TimePoint> FPSSync::presentAt() const {
    if (useVSync || lastFrame == MIN_TIME) return std::nullopt;
    return lastFrame + sampleInterval;
}

void FPSSync::reset() {
    lastFrame = MIN_TIME;
}

// nx-mobile 2026-10-31: vsync runs one core frame per screen refresh, so the core's audio comes screen / content times
// faster than its sample rate. Upstream had it inverted (content / screen), which on a 60 Hz screen asked the FIFO for
// about 0.9% less than a 59.73 fps GBA wrote: it overfilled and dropped frames until the PI integral caught up.
double FPSSync::getTimeStretchFactor() {
    return useVSync ? screenRefreshRate / contentRefreshRate : 1.0;
}

void FPSSync::wait() {
    if (useVSync) return;
    std::this_thread::sleep_until(lastFrame);
}

} //namespace libretrodroid
