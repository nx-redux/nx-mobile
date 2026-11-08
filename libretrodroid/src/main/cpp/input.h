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

#ifndef LIBRETRODROID_INPUT_H
#define LIBRETRODROID_INPUT_H

#include <cstdint>
#include <unordered_set>
#include <array>

#include "pointerstate.h"

namespace libretrodroid {

class Input {

private:
    struct GamePadState {
        std::unordered_set<int> pressedKeys;

        int dpadXAxis = 0;
        int dpadYAxis = 0;
        float joypadLeftXAxis = 0;
        float joypadLeftYAxis = 0;
        float joypadRightXAxis = 0;
        float joypadRightYAxis = 0;
        PointerState pointer;
        // Analog trigger values 0..MAX_RANGE_MOTION; -1 until the host sends one (then the digital L2/R2 answers).
        int l2Analog = -1;
        int r2Analog = -1;
    };

public:
    static constexpr int MOTION_SOURCE_DPAD = 0;
    static constexpr int MOTION_SOURCE_ANALOG_LEFT = 1;
    static constexpr int MOTION_SOURCE_ANALOG_RIGHT = 2;
    static constexpr int MOTION_SOURCE_POINTER = 3;
    static constexpr int MAX_RANGE_MOTION = 0x7fff;

    static constexpr int RETRO_DEVICE_ID_JOYPAD_UP_LEFT = 50;
    static constexpr int RETRO_DEVICE_ID_JOYPAD_UP_RIGHT = 51;
    static constexpr int RETRO_DEVICE_ID_JOYPAD_DOWN_LEFT = 52;
    static constexpr int RETRO_DEVICE_ID_JOYPAD_DOWN_RIGHT = 53;

    int16_t getInputState(unsigned port, unsigned device, unsigned index, unsigned id);

    void onKeyEvent(unsigned int port, int action, int keyCode);
    void onMotionEvent(int port, int motionSource, float xAxis, float yAxis);
    void onTriggerEvent(unsigned int port, float l2, float r2);

    // Wii: a pointer that stays where it was on lift, and a stick velocity that moves it each frame.
    void setStickyPointer(unsigned int port, bool sticky);
    void setPointerVelocity(unsigned int port, float vx, float vy);
    void advancePointers(float seconds);
    PointerState pointerOf(unsigned int port) const;
    void restorePointer(unsigned int port, const PointerState& state);

private:
    const int UNKNOWN_KEY = -1;

    template<typename ...T>
    bool anyPressed(unsigned int port, unsigned int id, T&... args) const;
    bool anyPressed(unsigned int port, unsigned int id) const;
    int convertAndroidToLibretroKey(int keyCode) const;
    int16_t analogButtonState(unsigned int port, unsigned int id) const;

    GamePadState pads[4];
};

}

#endif //LIBRETRODROID_INPUT_H
