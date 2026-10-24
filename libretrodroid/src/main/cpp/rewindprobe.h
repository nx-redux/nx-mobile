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

#ifndef LIBRETRODROID_REWINDPROBE_H
#define LIBRETRODROID_REWINDPROBE_H

#include <algorithm>
#include <cstddef>

namespace libretrodroid {

/**
 * When to ask a core that can't rewind yet for its state again, and when to give up on it. Counted in played frames
 * (the frames the core ran: a paused step plays none), so a game left paused at boot (menu, focus) is never given up on.
 * Two causes, counted apart:
 * - A state size of 0 is asked every INTERVAL played frames. Booting cores report 0 for a while (mupen64plus); one that
 *   stays at 0 for [zeroLimit] played frames (melonDS DS in DSi mode: no states at all) is given up on. A size other than
 *   0 ends the streak.
 * - A refused serialize backs off: 2, 4, 8 ... up to 32 intervals. MAX_REFUSALS refusals give up.
 * Pure; GL thread.
 */
class RewindProbe {
public:
    static constexpr unsigned INTERVAL = 60;
    static constexpr unsigned MAX_REFUSALS = 8;

    void reset() { *this = RewindProbe(); }

    /** [played] frames ran since the last call; true when the size should be asked now. */
    bool advance(unsigned played) {
        if (zeroing) zeroPlayed += played;
        if (wait > played) { wait -= played; return false; }
        wait = 0;
        return true;
    }

    /** The size asked was 0; true when it has stayed 0 for [zeroLimit] played frames: give up. */
    bool sizeZero(unsigned zeroLimit) {
        zeroing = true;
        wait = INTERVAL;
        return zeroPlayed >= zeroLimit;
    }

    /** The size asked was not 0: the zero streak ends (a serialize follows, or a build). */
    void sizeKnown() {
        zeroing = false;
        zeroPlayed = 0;
        wait = INTERVAL;
    }

    /** A serialize was refused (a probe, or a capture after the build); true when that makes MAX_REFUSALS: give up. */
    bool refused() {
        refusals++;
        if (refusals >= MAX_REFUSALS) return true;
        wait = INTERVAL << std::min(refusals, 5u);
        return false;
    }

    unsigned refusalCount() const { return refusals; }

private:
    unsigned wait = 0;          // played frames until the next ask
    bool zeroing = false;       // the last size asked was 0
    unsigned zeroPlayed = 0;    // played frames since the zero streak began
    unsigned refusals = 0;
};

}

#endif
