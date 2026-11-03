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

#ifndef LIBRETRODROID_FPSSYNC_H
#define LIBRETRODROID_FPSSYNC_H

#include <chrono>
#include <optional>
#include <thread>

namespace libretrodroid {

typedef std::chrono::steady_clock::time_point TimePoint;
typedef std::chrono::duration<long, std::micro> Duration;

class FPSSync {
public:
    FPSSync(double contentRefreshRate, double screenRefreshRate);
    ~FPSSync() { }

    void reset();
    unsigned advanceFrames();
    unsigned advanceFrames(TimePoint now);   // nx-mobile: the clock injected (host tests)
    TimePoint deadline() const { return lastFrame; }   // when this step's frame is due; wait() sleeps until it
    // nx-mobile 2026-11-03: when the sleep timer wants this step's frame on screen, so the display shows every frame on a
    // steady slot whatever the step's run time: its deadline + lead (the display's presentation deadline and a margin,
    // the earliest a frame queued at the deadline can show; one frame when lead is 0). None under vsync or before a step.
    std::optional<TimePoint> presentAt(std::chrono::microseconds lead) const;
    void wait();
    double getTimeStretchFactor();
    double getContentRefreshRate() const { return contentRefreshRate; }
private:

    double screenRefreshRate;
    double contentRefreshRate;
    bool useVSync;
    const double FPS_TOLERANCE = 5;

    // nx-mobile 2026-11-03: how many whole frames the sleep timer may fall behind before it runs frames back to back.
    static constexpr long long MAX_LAG_FRAMES = 2;
    // ...and how far before it drops the rest of the lag (a stall), so catching up never runs for seconds.
    static constexpr long long MAX_DROP_FRAMES = 8;

    const TimePoint MIN_TIME = TimePoint::min();
    void start(TimePoint now);

    TimePoint lastFrame = MIN_TIME;
    Duration sampleInterval;
};

}


#endif //LIBRETRODROID_FPSSYNC_H
