#ifndef LIBRETRODROID_AVTIMING_H
#define LIBRETRODROID_AVTIMING_H

#include <cmath>

namespace libretrodroid::avtiming {

constexpr double DEFAULT_FPS = 60.0;
constexpr double DEFAULT_SAMPLE_RATE = 48000.0;

// A core's timing.fps is trusted only in (1, 240]: FPSSync turns it into a frame interval, so 0, a negative or NaN
// would be undefined there and stall the pacing.
inline bool isValidFps(double fps) {
    return fps > 1.0 && fps <= 240.0;   // false for NaN too
}

inline double fpsOrDefault(double fps) {
    return isValidFps(fps) ? fps : DEFAULT_FPS;
}

// Audio sizes its FIFO and resampler ratio from the rate: 0 would give an empty buffer and a division by zero.
inline double sampleRateOrDefault(double sampleRate) {
    return (sampleRate > 0.0 && std::isfinite(sampleRate)) ? sampleRate : DEFAULT_SAMPLE_RATE;
}

}

#endif //LIBRETRODROID_AVTIMING_H
