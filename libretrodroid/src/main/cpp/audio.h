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

#ifndef LIBRETRODROID_AUDIO_H
#define LIBRETRODROID_AUDIO_H

#include <array>
#include <atomic>
#include <memory>
#include <mutex>
#include <unistd.h>
#include <oboe/Oboe.h>
#include <oboe/FifoBuffer.h>

#include "resamplers/linearresampler.h"

namespace libretrodroid {

class Audio: public oboe::AudioStreamDataCallback {
private:
    struct AudioLatencySettings {
        unsigned bufferSizeInVideoFrames;
        bool useLowLatencyStream;
    };

    const AudioLatencySettings DEFAULT_LATENCY_SETTINGS { 8, false };
    const AudioLatencySettings LOW_LATENCY_SETTINGS { 4, true };

    // Oboe calls the error callback on its own thread, which can still be running when Audio is destroyed. The
    // stream holds this handler by shared_ptr, and ~Audio detaches it, so a late disconnect never reaches a dead Audio.
    class StreamErrorHandler : public oboe::AudioStreamErrorCallback {
    public:
        explicit StreamErrorHandler(Audio* owner) : owner(owner) { }
        void onErrorAfterClose(oboe::AudioStream* oldStream, oboe::Result result) override;
        void detach();

    private:
        std::mutex ownerMutex;
        Audio* owner;
    };

public:
    Audio(int32_t sampleRate, double refreshRate, bool preferLowLatencyAudio);
    ~Audio() override;

    void start();
    void stop();

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *oboeStream,
        void *audioData,
        int32_t numFrames
    ) override;

public:
    void write(const int16_t *data, size_t frames);
    void setPlaybackSpeed(const double newPlaybackSpeed);
    // nx-mobile 2026-10-24: fast forward. Muted, the realtime callback plays silence with the rate controller held, as
    // at speed 0, and drops what the FIFO still held; audible, the caller sets the playback speed to the game's. When
    // FF ends the output stays silent until the FIFO is refilled (crackles otherwise). Any thread.
    void setFastForward(bool active, bool audible);

private:
    static int32_t roundToEven(int32_t x);
    double computeDynamicBufferConversionFactor(double dt);
    int32_t computeAudioBufferSize();
    bool openStreamLocked();
    void reopenStream();
    void tuneLatency();
    std::unique_ptr<Audio::AudioLatencySettings> findBestLatencySettings(bool preferLowLatencyAudio);
    double computeMaximumLatency() const;

private:
    const double kp = 0.006;
    const double ki = 0.00002;
    const double maxp = 0.003;
    const double maxi = 0.02;

    LinearResampler resampler;

    // Live for the whole Audio: the GL thread writes and the audio callback reads them across stream reopens.
    std::unique_ptr<oboe::FifoBuffer> fifoBuffer = nullptr;
    std::unique_ptr<int16_t[]> temporaryAudioBuffer = nullptr;
    int32_t temporaryAudioBufferFrames = 0;

    // Guards stream, latencyTuner, startRequested and closed. start/stop, the error thread's reopen and ~Audio
    // take it; the realtime callback only try_locks it.
    std::mutex streamMutex;
    std::shared_ptr<oboe::AudioStream> stream = nullptr;
    std::unique_ptr<oboe::LatencyTuner> latencyTuner = nullptr;
    std::shared_ptr<StreamErrorHandler> errorHandler;
    bool startRequested = false;
    bool closed = false;

    int32_t inputSampleRate;
    double contentRefreshRate = 60.0;

    std::atomic<double> baseConversionFactor { 1.0 };

    // Audio callback thread only.
    double framesToSubmit = 0.0;
    double errorIntegral = 0.0;

    std::atomic<double> playbackSpeed { 1.0 };

    // Fast forward (setFastForward): written by the GL thread, read by the audio callback.
    std::atomic<bool> fastForwardActive { false };
    std::atomic<bool> fastForwardMuted { false };
    std::atomic<bool> refillPending { false };
    // Output resumes after FF once the FIFO is this full, near the PI controller's half-full target.
    static constexpr double REFILL_FRACTION = 0.4;

    std::unique_ptr<AudioLatencySettings> audioLatencySettings;
};

} // namespace libretrodroid

#endif //LIBRETRODROID_AUDIO_H
