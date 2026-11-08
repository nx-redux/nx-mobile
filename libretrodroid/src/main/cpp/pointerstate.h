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

#ifndef LIBRETRODROID_POINTERSTATE_H
#define LIBRETRODROID_POINTERSTATE_H

namespace libretrodroid {

/**
 * One port's pointer in 0..1 of the core frame (-1 = released). Wii: [sticky] keeps the last
 * position when the finger lifts or leaves the picture (Dolphin's IR pointer stays where it was aimed), and a velocity
 * from a right stick moves it every frame ([advance]), starting from the centre.
 */
struct PointerState {
    float x = -1.f;
    float y = -1.f;
    float vx = 0.f;   // picture widths per second
    float vy = 0.f;
    bool sticky = false;

    /**
     * Sets [sticky]. With [centreIfUntouched] an untouched pointer starts at the centre, so the core never reads a
     * released -1 (the touch player); without it an untouched pointer stays released until a stick moves it, so a
     * Wiimote nobody holds draws no cursor.
     */
    void setSticky(bool on, bool centreIfUntouched);
    void touch(float nx, float ny);
    void advance(float seconds);
    bool pressed() const { return x >= 0.f && y >= 0.f; }
};

/** The state a rebuilt input starts from: a sticky pointer keeps its flag and position; velocity is dropped on purpose. */
PointerState carryOver(const PointerState& old);

}

#endif
