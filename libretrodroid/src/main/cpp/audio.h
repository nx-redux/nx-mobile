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

#include <samplerate.h>

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
    // The pause (0) / run (1) speed. UI thread.
    void setPlaybackSpeed(const double newPlaybackSpeed);
    // nx-mobile 2026-10-24: audible FF's playback speed (the game's achieved speed), 0 when not in audible FF. Separate
    // from setPlaybackSpeed, which another thread writes: the callback combines them with a pause (0) winning, so an FF
    // update landing just after a pause can't play the FIFO's tail at FF pitch in the menu. GL thread.
    void setFastForwardSpeed(double speed);
    // nx-mobile 2026-10-24: fast forward. Muted, the realtime callback plays silence with the rate controller held, as
    // at speed 0, and drops what the FIFO still held; audible, the caller sets the FF speed. Whenever the output was
    // muted and is not anymore (FF ended, or muted FF turned audible) it stays silent until the FIFO is refilled
    // (crackles otherwise). GL thread, like setRewind.
    void setFastForward(bool active, bool audible);
    // nx-mobile 2026-10-24: rewind without its audio mutes the output like muted FF, refilled the same way after.
    void setRewind(bool active, bool audible);
    // nx-mobile 2026-10-30: libsamplerate converter levels, as the handheld's Audio Resampling Quality.
    enum Quality { LOW = 0, MEDIUM = 1, HIGH = 2, MAX = 3, QUALITY_COUNT = 4 };
    // The resampler for what plays from now; audible fast forward plays at MEDIUM at most. An unknown value is HIGH.
    // GL thread: a level's converter is made here the first time, never on the realtime callback.
    void setResamplingQuality(int quality);

private:
    static int32_t roundToEven(int32_t x);
    double computeDynamicBufferConversionFactor(double dt);
    int32_t computeAudioBufferSize();
    bool openStreamLocked();
    void reopenStream();
    void tuneLatency();
    std::unique_ptr<Audio::AudioLatencySettings> findBestLatencySettings(bool preferLowLatencyAudio);
    double computeMaximumLatency() const;
    // libsamplerate's input callback: the next INPUT_CHUNK_FRAMES from the FIFO (zeros past what it holds).
    static long readInput(void *self, float **data);
    // Audio callback thread: the converter for this callback (the level, capped during audible FF), reset when the
    // level changes or output resumes after silence, so no stale history plays.
    SRC_STATE *converterFor(bool audibleFastForward);

private:
    const double kp = 0.006;
    const double ki = 0.00002;
    const double maxp = 0.003;
    const double maxi = 0.02;

    // Live for the whole Audio: the GL thread writes and the audio callback reads them across stream reopens.
    std::unique_ptr<oboe::FifoBuffer> fifoBuffer = nullptr;
    int32_t audioBufferFrames = 0;

    // nx-mobile 2026-10-30: streaming libsamplerate converters, one per level, made on first use (setResamplingQuality)
    // and kept until ~Audio. Each keeps its position and filter history across callbacks, unlike the per-callback
    // linear resampler it replaces, which restarted at every buffer edge.
    static constexpr int32_t INPUT_CHUNK_FRAMES = 64;
    static constexpr int32_t OUTPUT_CHUNK_FRAMES = 1024;
    std::array<std::atomic<SRC_STATE *>, QUALITY_COUNT> converters {};
    std::atomic<int> quality { HIGH };
    std::unique_ptr<int16_t[]> inputShorts;
    std::unique_ptr<float[]> inputFloats;
    std::unique_ptr<float[]> outputFloats;
    // Audio callback thread only.
    int playingQuality = -1;
    bool converterResetPending = true;

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
    double errorIntegral = 0.0;

    std::atomic<double> playbackSpeed { 1.0 };
    std::atomic<double> fastForwardSpeed { 0.0 };

    // Fast forward (setFastForward): written by the GL thread, read by the audio callback.
    // Rewind likewise (setRewind). outputMuted is either one muting.
    std::atomic<bool> fastForwardActive { false };
    bool fastForwardMutes = false;   // GL thread
    bool rewindMutes = false;        // GL thread
    std::atomic<bool> outputMuted { false };
    std::atomic<bool> refillPending { false };
    // GL thread: applies fastForwardMutes / rewindMutes, starting a refill when the output unmutes.
    void updateMute(bool fastForwardEnded);
    // Output resumes after FF once the FIFO is this full, near the PI controller's half-full target.
    static constexpr double REFILL_FRACTION = 0.4;

    std::unique_ptr<AudioLatencySettings> audioLatencySettings;
};

} // namespace libretrodroid

#endif //LIBRETRODROID_AUDIO_H
