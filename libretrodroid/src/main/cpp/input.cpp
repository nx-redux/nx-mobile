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

#include "input.h"

#include <cmath>

#include <android/input.h>
#include <android/keycodes.h>

#include "../../libretro-common/include/libretro.h"

namespace libretrodroid {

int16_t Input::getInputState(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port >= 4) return 0;

    switch (device) {
        case RETRO_DEVICE_JOYPAD: {
            switch (id) {
                case RETRO_DEVICE_ID_JOYPAD_LEFT: {
                    bool axis = pads[port].dpadXAxis == -1;
                    bool buttons = anyPressed(
                        port,
                        RETRO_DEVICE_ID_JOYPAD_LEFT,
                        Input::RETRO_DEVICE_ID_JOYPAD_DOWN_LEFT,
                        Input::RETRO_DEVICE_ID_JOYPAD_UP_LEFT
                    );
                    return axis || buttons;
                }
                case RETRO_DEVICE_ID_JOYPAD_RIGHT: {
                    bool axis = pads[port].dpadXAxis == 1;
                    bool buttons = anyPressed(
                        port,
                        RETRO_DEVICE_ID_JOYPAD_RIGHT,
                        Input::RETRO_DEVICE_ID_JOYPAD_UP_RIGHT,
                        Input::RETRO_DEVICE_ID_JOYPAD_DOWN_RIGHT
                    );
                    return axis || buttons;
                }
                case RETRO_DEVICE_ID_JOYPAD_UP: {
                    bool axis = pads[port].dpadYAxis == -1;
                    bool buttons = anyPressed(
                        port,
                        RETRO_DEVICE_ID_JOYPAD_UP,
                        Input::RETRO_DEVICE_ID_JOYPAD_UP_LEFT,
                        Input::RETRO_DEVICE_ID_JOYPAD_UP_RIGHT
                    );
                    return axis || buttons;
                }
                case RETRO_DEVICE_ID_JOYPAD_DOWN: {
                    bool axis = pads[port].dpadYAxis == 1;
                    bool buttons = anyPressed(
                        port,
                        RETRO_DEVICE_ID_JOYPAD_DOWN,
                        Input::RETRO_DEVICE_ID_JOYPAD_DOWN_LEFT,
                        Input::RETRO_DEVICE_ID_JOYPAD_DOWN_RIGHT
                    );
                    return axis || buttons;
                }
                default:
                    return anyPressed(port, id);
            }
        }

        case RETRO_DEVICE_ANALOG: {
            switch (index) {
                case RETRO_DEVICE_INDEX_ANALOG_LEFT:
                    switch (id) {
                        case RETRO_DEVICE_ID_ANALOG_X:
                            return (int16_t) (pads[port].joypadLeftXAxis * MAX_RANGE_MOTION);
                        case RETRO_DEVICE_ID_ANALOG_Y:
                            return (int16_t) (pads[port].joypadLeftYAxis * MAX_RANGE_MOTION);
                        default:
                            return 0;
                    }
                case RETRO_DEVICE_INDEX_ANALOG_RIGHT:
                    switch (id) {
                        case RETRO_DEVICE_ID_ANALOG_X:
                            return (int16_t) (pads[port].joypadRightXAxis * MAX_RANGE_MOTION);
                        case RETRO_DEVICE_ID_ANALOG_Y:
                            return (int16_t) (pads[port].joypadRightYAxis * MAX_RANGE_MOTION);
                        default:
                            return 0;
                    }
                case RETRO_DEVICE_INDEX_ANALOG_BUTTON:
                    return analogButtonState(port, id);
                default:
                    return 0;
            }
        }

        case RETRO_DEVICE_POINTER: {
            // Single touch only.
            if (index > 0) {
                return 0;
            }

            switch (id) {
                case RETRO_DEVICE_ID_POINTER_PRESSED: {
                    return (int16_t) (pads[port].pointer.pressed() ? 1 : 0);
                }

                case RETRO_DEVICE_ID_POINTER_X:
                    return (int16_t) (2.0 * (pads[port].pointer.x - 0.5f) * MAX_RANGE_MOTION);

                case RETRO_DEVICE_ID_POINTER_Y:
                    return (int16_t) (2.0 * (pads[port].pointer.y - 0.5f) * MAX_RANGE_MOTION);

                default:
                    return 0;
            }
        }

        default:
            return 0;
    }
}

int Input::convertAndroidToLibretroKey(int keyCode) const {
    switch (keyCode) {
        case AKEYCODE_BUTTON_START:
            return RETRO_DEVICE_ID_JOYPAD_START;
        case AKEYCODE_BUTTON_SELECT:
            return RETRO_DEVICE_ID_JOYPAD_SELECT;
        case AKEYCODE_BUTTON_A:
            return RETRO_DEVICE_ID_JOYPAD_A;
        case AKEYCODE_BUTTON_X:
            return RETRO_DEVICE_ID_JOYPAD_X;
        case AKEYCODE_BUTTON_Y:
            return RETRO_DEVICE_ID_JOYPAD_Y;
        case AKEYCODE_BUTTON_B:
            return RETRO_DEVICE_ID_JOYPAD_B;
        case AKEYCODE_BUTTON_L1:
            return RETRO_DEVICE_ID_JOYPAD_L;
        case AKEYCODE_BUTTON_L2:
            return RETRO_DEVICE_ID_JOYPAD_L2;
        case AKEYCODE_BUTTON_R1:
            return RETRO_DEVICE_ID_JOYPAD_R;
        case AKEYCODE_BUTTON_R2:
            return RETRO_DEVICE_ID_JOYPAD_R2;
        case AKEYCODE_BUTTON_THUMBL:
            return RETRO_DEVICE_ID_JOYPAD_L3;
        case AKEYCODE_BUTTON_THUMBR:
            return RETRO_DEVICE_ID_JOYPAD_R3;
        case AKEYCODE_DPAD_UP:
            return RETRO_DEVICE_ID_JOYPAD_UP;
        case AKEYCODE_DPAD_DOWN:
            return RETRO_DEVICE_ID_JOYPAD_DOWN;
        case AKEYCODE_DPAD_LEFT:
            return RETRO_DEVICE_ID_JOYPAD_LEFT;
        case AKEYCODE_DPAD_RIGHT:
            return RETRO_DEVICE_ID_JOYPAD_RIGHT;
        case AKEYCODE_DPAD_UP_RIGHT:
            return Input::RETRO_DEVICE_ID_JOYPAD_UP_RIGHT;
        case AKEYCODE_DPAD_UP_LEFT:
            return Input::RETRO_DEVICE_ID_JOYPAD_UP_LEFT;
        case AKEYCODE_DPAD_DOWN_RIGHT:
            return Input::RETRO_DEVICE_ID_JOYPAD_DOWN_RIGHT;
        case AKEYCODE_DPAD_DOWN_LEFT:
            return Input::RETRO_DEVICE_ID_JOYPAD_DOWN_LEFT;
        default:
            return UNKNOWN_KEY;
    }
}

void Input::onKeyEvent(unsigned int port, int action, int keyCode) {
    if (port >= 4) return;
    int retroKeyCode = convertAndroidToLibretroKey(keyCode);
    if (retroKeyCode == UNKNOWN_KEY) {
        return;
    }

    if (action == AKEY_EVENT_ACTION_DOWN) {
        pads[port].pressedKeys.insert(retroKeyCode);
    } else if (action == AKEY_EVENT_ACTION_UP) {
        pads[port].pressedKeys.erase(retroKeyCode);
    }
}

void Input::onMotionEvent(int port, int motionSource, float xAxis, float yAxis) {
    if (port < 0 || port >= 4) return;
    switch (motionSource) {
        case Input::MOTION_SOURCE_DPAD:
            pads[port].dpadXAxis = (int) round(xAxis);
            pads[port].dpadYAxis = (int) round(yAxis);
            break;

        case Input::MOTION_SOURCE_ANALOG_LEFT:
            pads[port].joypadLeftXAxis = xAxis;
            pads[port].joypadLeftYAxis = yAxis;
            break;

        case Input::MOTION_SOURCE_ANALOG_RIGHT:
            pads[port].joypadRightXAxis = xAxis;
            pads[port].joypadRightYAxis = yAxis;
            break;

        case Input::MOTION_SOURCE_POINTER:
            pads[port].pointer.touch(xAxis, yAxis);
            break;
    }
}

void Input::onTriggerEvent(unsigned int port, float l2, float r2) {
    if (port >= 4) return;
    auto toRange = [](float v) {
        if (!(v > 0.0f)) return 0;  // also catches NaN
        if (v >= 1.0f) return MAX_RANGE_MOTION;
        return (int) lroundf(v * MAX_RANGE_MOTION);
    };
    pads[port].l2Analog = toRange(l2);
    pads[port].r2Analog = toRange(r2);
}

// Pressure of an analog button (RETRO_DEVICE_INDEX_ANALOG_BUTTON). Only L2/R2 carry analog values, and a non-zero one
// wins over the key (pads that also send KEYCODE_BUTTON_L2/R2 on a light pull keep their partial values); otherwise the
// digital state answers (pressed = full range), which covers the on-screen pad and digital-only triggers.
int16_t Input::analogButtonState(unsigned int port, unsigned int id) const {
    int analog = -1;
    if (id == RETRO_DEVICE_ID_JOYPAD_L2) analog = pads[port].l2Analog;
    if (id == RETRO_DEVICE_ID_JOYPAD_R2) analog = pads[port].r2Analog;
    if (analog > 0) return (int16_t) analog;
    return (int16_t) (anyPressed(port, id) ? MAX_RANGE_MOTION : 0);
}

template<typename... T>
bool Input::anyPressed(unsigned int port, unsigned int id, T &... args) const {
    return anyPressed(port, id) || anyPressed(port, args...);
}

bool Input::anyPressed(unsigned int port, unsigned int id) const {
    return pads[port].pressedKeys.count(id) > 0;
}

void Input::setStickyPointer(unsigned int port, bool sticky) {
    if (port >= 4) return;
    pads[port].pointer.setSticky(sticky, port == 0);   // only the touch player starts centred
}

void Input::setPointerVelocity(unsigned int port, float vx, float vy) {
    if (port >= 4) return;
    pads[port].pointer.vx = vx;
    pads[port].pointer.vy = vy;
}

PointerState Input::pointerOf(unsigned int port) const {
    return port < 4 ? pads[port].pointer : PointerState();
}

void Input::restorePointer(unsigned int port, const PointerState& state) {
    if (port < 4) pads[port].pointer = state;
}

void Input::advancePointers(float seconds) {
    for (auto& pad : pads) pad.pointer.advance(seconds);
}

} //namespace libretrodroid
