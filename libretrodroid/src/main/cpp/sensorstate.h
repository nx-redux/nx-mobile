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

#ifndef LIBRETRODROID_SENSORSTATE_H
#define LIBRETRODROID_SENSORSTATE_H

#include <array>
#include <mutex>

namespace libretrodroid {

/**
 * The phone's accelerometer (g) and gyroscope (rad/s), already in the core's frame, for
 * RETRO_ENVIRONMENT_GET_SENSOR_INTERFACE (Dolphin's Wiimote). Only port 0 (the phone's player) and only while the host
 * turned motion on; values arrive from the host's sensor thread, the core reads them on the GL thread.
 */
class SensorState {
public:
    static SensorState& get();

    void setAvailable(bool accel, bool gyro);
    void setEnabled(unsigned port, bool on);
    bool setState(unsigned port, unsigned action);
    void setValues(unsigned port, float ax, float ay, float az, float gx, float gy, float gz);
    float input(unsigned port, unsigned id) const;
    void reset();

private:
    mutable std::mutex lock;
    bool hasAccel = false;
    bool hasGyro = false;
    bool enabled = false;          // port 0
    std::array<float, 6> values{}; // ax ay az gx gy gz
};

}

#endif
