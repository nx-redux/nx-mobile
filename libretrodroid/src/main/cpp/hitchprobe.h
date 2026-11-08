// Diagnostic. Finds the steps that stay on screen too long (a 60 fps game's frame shown for
// 33 ms or more) and splits the late step's time into the core's run, our draw, the pacing sleep and the time spent
// outside the step (the buffer swap and the GL thread's other work), so a hitch can be pinned on one of them.
// GL thread only.
#ifndef LIBRETRODROID_HITCHPROBE_H
#define LIBRETRODROID_HITCHPROBE_H

#include <chrono>
#include <optional>

namespace libretrodroid {

class HitchProbe {
public:
    using Clock = std::chrono::steady_clock;

    struct Report {
        double periodMs;    // from the reported step's start to the next one's
        double runMs;       // retro_run, all frames of the step
        double renderMs;    // our draw of the core's picture (shaders, layout)
        double waitMs;      // the pacing sleep
        double outsideMs;   // after the step returned: eglSwapBuffers and other GL-thread work
        unsigned frames;    // core frames run in the step
    };

    // A step starts. Returns the previous step's report when it ran longer than 1.5 content frames.
    std::optional<Report> begin(Clock::time_point now, double contentFrameMs) {
        std::optional<Report> report;
        if (started && last.ended) {
            const double period = ms(now - last.start);
            if (period > contentFrameMs * 1.5) {
                ++hitchCount;
                report = Report {
                    period, ms(last.ranAt - last.start), ms(last.renderedAt - last.ranAt), ms(last.end - last.renderedAt),
                    ms(now - last.end), last.frames,
                };
            }
        }
        started = true;
        ++stepCount;
        last = Step { now, now, now, now, 0, false };
        return report;
    }

    void ranCore(Clock::time_point now, unsigned frames) {
        last.ranAt = now;
        last.frames = frames;
        if (frames == 0) return;
        const double perFrame = ms(now - last.start) / frames;
        runTotalMs += perFrame * frames;
        runFrames += frames;
        if (perFrame > runWorstMs) runWorstMs = perFrame;
    }

    // The core's run per frame since the last take (the summary's work figures): a faster core shows here at 60 fps,
    // where the pacing figures would not move.
    struct RunStats { unsigned long frames; double averageMs; double worstMs; };
    RunStats takeRunStats() {
        RunStats s { runFrames, runFrames ? runTotalMs / runFrames : 0.0, runWorstMs };
        runTotalMs = 0; runFrames = 0; runWorstMs = 0;
        return s;
    }
    void rendered(Clock::time_point now) { last.renderedAt = now; }
    void waited(Clock::time_point now) { last.end = now; last.ended = true; }

    // A pause or a new game: the gap since the last step is not a hitch.
    void reset() { started = false; }

    unsigned long steps() const { return stepCount; }
    unsigned long hitches() const { return hitchCount; }

private:
    struct Step {
        Clock::time_point start, ranAt, renderedAt, end;
        unsigned frames;
        bool ended;
    };

    static double ms(Clock::duration d) { return std::chrono::duration<double, std::milli>(d).count(); }

    bool started = false;
    Step last {};
    unsigned long stepCount = 0;
    unsigned long hitchCount = 0;
    double runTotalMs = 0;
    unsigned long runFrames = 0;
    double runWorstMs = 0;
};

}

#endif
