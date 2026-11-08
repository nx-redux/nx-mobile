/*
 * The game's achieved speed while Frame Skip is Off, for the audio to follow.
 *
 * With Frame Skip Off a game that can't keep up runs every frame and slows down (FPSSync drops the lag instead of
 * catching up), so it makes its sound slower than real time. The audio's rate controller only bends playback by about
 * 2%, so a game at 95% emptied the FIFO (gaps) and, once it caught up with the controller still wound down, overfilled
 * it (dropped samples): crackle. The audio instead plays at the speed the game reaches, measured here from the frames
 * run over wall time, smoothed, never above 1: a heavy moment slows the music with the game, as slowdown on the
 * console did, and the rate controller is left with the small remainder.
 */
#pragma once

#include <algorithm>
#include <chrono>

namespace libretrodroid {

class SlowdownMeter {
public:
    using Clock = std::chrono::steady_clock;

    // The slowest the audio follows: below it the game is barely playable and the sound would only drag further.
    static constexpr double MIN_SPEED = 0.75;
    // Each speed is measured over at least this much wall time, then folded into the smoothed one.
    static constexpr std::chrono::milliseconds WINDOW { 250 };
    static constexpr double SMOOTHING = 0.35;   // weight of a new window

    // [frames] emulated in a step that ended at [now], for a game of [contentFps]. Returns the speed to play at.
    double add(Clock::time_point now, unsigned frames, double contentFps) {
        if (!started) {
            start = now;
            framesInWindow = 0;
            started = true;
            return smoothed;
        }
        framesInWindow += frames;
        const auto elapsed = now - start;
        if (elapsed >= WINDOW && contentFps > 0.0) {
            const double seconds = std::chrono::duration<double>(elapsed).count();
            const double measured = std::clamp(framesInWindow / (seconds * contentFps), MIN_SPEED, 1.0);
            smoothed += SMOOTHING * (measured - smoothed);
            start = now;
            framesInWindow = 0;
        }
        return smoothed;
    }

    // Not measuring (Frame Skip Auto, paused, fast forward, rewind, netplay): full speed, measured afresh next time.
    void reset() {
        started = false;
        smoothed = 1.0;
    }

    double speed() const { return smoothed; }

private:
    bool started = false;
    Clock::time_point start {};
    unsigned framesInWindow = 0;
    double smoothed = 1.0;
};

}  // namespace libretrodroid
