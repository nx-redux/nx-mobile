/*
 *     Copyright (C) 2026  nx-mobile contributors
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

#ifndef LIBRETRODROID_NETPLAYHOOKS_H
#define LIBRETRODROID_NETPLAYHOOKS_H

#include <cstdint>

namespace libretrodroid { class Core; class Input; }

// The run loop's side of the lockstep engine (np_engine.h): P1/P2 joypad words and sticks. preFrame, postFrame and inputState run on the frame (GL)
// thread; active and shutdown from any thread.
namespace libretrodroid::netplay {
    bool active();                                // a session is started and not yet over
    bool preFrame(Core& core, Input& input);      // true: run one frame on the session's inputs; false: redraw only
    void postFrame();                             // after a frame preFrame allowed
    int16_t inputState(unsigned port, unsigned device, unsigned index, unsigned id);
    void shutdown();                              // can block ~0.75 s: never from step()
    // Pair mode for the next np_start, hashing with the core's nx_pair_hash. False (and off) when on and the core is
    // missing or does not export it.
    bool setPairMode(Core* core, bool on);
}

#endif //LIBRETRODROID_NETPLAYHOOKS_H
