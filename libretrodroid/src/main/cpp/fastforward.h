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
#ifndef LIBRETRODROID_FASTFORWARD_H
#define LIBRETRODROID_FASTFORWARD_H

#include "fpssync.h"   // TimePoint, Duration

namespace libretrodroid {

/**
 * nx-mobile 2026-10-24: paces fast forward against game time, not against steps. The display decides how often step()
 * runs (60, 90 or 120 Hz, or FPSSync's sleep), so "N core frames per step" would make the speed follow the display —
 * the handheld measured 2.5–4x at a 2x cap that way (its PR #188). Instead each core frame has a due time on a clock
 * ticking at maxSpeed × contentFps, and a step runs the frames that are due.
 *
 * - At most maxSpeed frames per step: a step that finds more due (a stall, a slow device, the menu) runs maxSpeed and
 *   resyncs the clock to now, so the debt is dropped rather than burst through. A device that can't keep up simply
 *   runs slower than the cap.
 * - A step may run 0 frames (a display faster than the capped rate); the caller redraws the last frame.
 * - sleepFor only asks for a sleep when the last step ran nothing — the loop is ahead — and never longer than one
 *   content frame (the handheld hung pcsx with a longer one). After frames ran it is 0: the display's swap already
 *   paces the loop, and sleeping there would miss vsyncs and halve the speed.
 *
 * Time comes in as a parameter so the host test drives it. GL thread only.
 */
class FastForwardPacer {
public:
    void start(double contentFps, unsigned maxSpeed, TimePoint now);
    void stop();
    bool active() const { return running; }

    // Core frames this step runs; 0 while inactive.
    unsigned framesFor(TimePoint now);
    // How long the step may sleep after its frames; 0 while inactive or after frames ran.
    Duration sleepFor(TimePoint now) const;

    // Game speed actually achieved (smoothed), in [1, maxSpeed]; what audible FF plays its audio at. 1 while inactive.
    double effectiveSpeed() const { return running ? speed : 1.0; }

private:
    bool running = false;
    unsigned cap = 1;
    double fps = 60.0;
    TimePoint::duration frameInterval {};   // one content frame
    TimePoint::duration interval {};        // one content frame at the cap
    TimePoint due {};                       // when the next core frame is due
    TimePoint lastStep {};
    unsigned lastFrames = 0;
    double speed = 1.0;
};

} // namespace libretrodroid

#endif //LIBRETRODROID_FASTFORWARD_H
