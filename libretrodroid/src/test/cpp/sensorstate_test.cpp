// Host check for sensorstate.cpp (pure). From the repo root:
//   out=$(mktemp -d)/sensorstate_test && clang++ -std=c++17 -Ilibretrodroid/src/main/cpp \
//     -Ilibretrodroid/src/main/cpp/libretro/libretro-common/include \
//     libretrodroid/src/test/cpp/sensorstate_test.cpp libretrodroid/src/main/cpp/sensorstate.cpp -o "$out" && "$out"
#include "sensorstate.h"
#include "libretro.h"

#include <cstdio>

#include "check.h"

using namespace libretrodroid;

int main() {
    SensorState s;
    check(!s.setState(0, RETRO_SENSOR_ACCELEROMETER_ENABLE), "nothing available: refused");
    s.setAvailable(true, true);
    check(!s.setState(0, RETRO_SENSOR_GYROSCOPE_ENABLE), "available but motion off: refused");
    s.setEnabled(0, true);
    check(s.setState(0, RETRO_SENSOR_ACCELEROMETER_ENABLE), "port 0 accel with motion on");
    check(s.setState(0, RETRO_SENSOR_GYROSCOPE_ENABLE), "port 0 gyro with motion on");
    check(!s.setState(1, RETRO_SENSOR_ACCELEROMETER_ENABLE), "other ports never");
    check(s.setState(0, RETRO_SENSOR_GYROSCOPE_DISABLE), "disable always succeeds");
    s.setAvailable(true, false);
    check(!s.setState(0, RETRO_SENSOR_GYROSCOPE_ENABLE), "no gyro: gyro refused");

    s.setValues(0, 0.f, 1.f, 0.f, 0.5f, 0.f, -0.5f);
    check(s.input(0, RETRO_SENSOR_ACCELEROMETER_Y) == 1.f, "accel y");
    check(s.input(0, RETRO_SENSOR_GYROSCOPE_Z) == -0.5f, "gyro z");
    check(s.input(1, RETRO_SENSOR_ACCELEROMETER_Y) == 0.f, "other ports read 0");
    s.setEnabled(0, false);
    check(s.input(0, RETRO_SENSOR_ACCELEROMETER_Y) == 0.f, "motion off reads 0");
    s.setEnabled(0, true);
    s.reset();
    check(!s.setState(0, RETRO_SENSOR_ACCELEROMETER_ENABLE) && s.input(0, RETRO_SENSOR_ACCELEROMETER_Y) == 0.f, "reset clears all");
    if (failures == 0) std::printf("ok\n");
    return failures == 0 ? 0 : 1;
}
