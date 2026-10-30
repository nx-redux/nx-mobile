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

#include "log.h"

#include "audio.h"
#include <algorithm>
#include <cmath>
#include <memory>

namespace libretrodroid {

Audio::Audio(int32_t sampleRate, double refreshRate, bool preferLowLatencyAudio) {
    LOGI("Audio initialization has been called with input sample rate %d", sampleRate);

    contentRefreshRate = refreshRate;
    inputSampleRate = sampleRate;
    audioLatencySettings = findBestLatencySettings(preferLowLatencyAudio);

    int32_t audioBufferSize = computeAudioBufferSize();
    fifoBuffer = std::make_unique<oboe::FifoBuffer>(2, audioBufferSize);
    audioBufferFrames = audioBufferSize / 2;

    inputShorts = std::unique_ptr<int16_t[]>(new int16_t[INPUT_CHUNK_FRAMES * 2]);
    inputFloats = std::unique_ptr<float[]>(new float[INPUT_CHUNK_FRAMES * 2]);
    outputFloats = std::unique_ptr<float[]>(new float[OUTPUT_CHUNK_FRAMES * 2]);
    // MEDIUM always exists (audible FF's cap), HIGH is the default; the others are made when picked.
    setResamplingQuality(MEDIUM);
    setResamplingQuality(HIGH);

    errorHandler = std::make_shared<StreamErrorHandler>(this);

    std::lock_guard<std::mutex> lock(streamMutex);
    openStreamLocked();
}

Audio::~Audio() {
    // First stop a late error callback from reopening (waits out one already running), then stop and close the
    // stream: close() returns only after the data callback has finished, so no callback outlives the members.
    errorHandler->detach();

    std::lock_guard<std::mutex> lock(streamMutex);
    closed = true;
    if (stream != nullptr) {
        stream->stop();
        stream->close();
    }
    latencyTuner = nullptr;
    stream = nullptr;

    for (auto &converter : converters) {
        if (SRC_STATE *state = converter.load()) src_delete(state);
    }
}

// Call with streamMutex held. Only the stream and its tuner change; the FIFO and buffers stay.
bool Audio::openStreamLocked() {
    LOGI("Using low latency stream: %d", audioLatencySettings->useLowLatencyStream);

    latencyTuner = nullptr;
    stream = nullptr;

    oboe::AudioStreamBuilder builder;
    builder.setChannelCount(2);
    builder.setDirection(oboe::Direction::Output);
    builder.setFormat(oboe::AudioFormat::I16);
    builder.setDataCallback(this);
    builder.setErrorCallback(errorHandler);

    if (audioLatencySettings->useLowLatencyStream) {
        builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    } else {
        builder.setFramesPerCallback(audioBufferFrames * 2 / 10);
    }

    std::shared_ptr<oboe::AudioStream> newStream;
    oboe::Result result = builder.openStream(newStream);
    if (result == oboe::Result::OK) {
        baseConversionFactor.store((double) inputSampleRate / newStream->getSampleRate());
        latencyTuner = std::make_unique<oboe::LatencyTuner>(*newStream);
        stream = newStream;
        return true;
    } else {
        LOGE("Failed to create stream. Error: %s", oboe::convertToText(result));
        return false;
    }
}

void Audio::reopenStream() {
    std::lock_guard<std::mutex> lock(streamMutex);
    if (closed) return;

    // The old stream is already closed by Oboe; its error thread holds its own reference while it finishes.
    if (openStreamLocked() && startRequested) {
        stream->requestStart();
    }
}

std::unique_ptr<Audio::AudioLatencySettings> Audio::findBestLatencySettings(bool preferLowLatencyAudio) {
    if (oboe::AudioStreamBuilder::isAAudioRecommended() && preferLowLatencyAudio) {
        return std::make_unique<AudioLatencySettings>(LOW_LATENCY_SETTINGS);
    } else {
        return std::make_unique<AudioLatencySettings>(DEFAULT_LATENCY_SETTINGS);
    }
}

int32_t Audio::computeAudioBufferSize() {
    double maxLatency = computeMaximumLatency();
    LOGI("Average audio latency set to: %f ms", maxLatency * 0.5);
    double sampleRateDivisor = 500.0 / maxLatency;
    return roundToEven(inputSampleRate / sampleRateDivisor);
}

double Audio::computeMaximumLatency() const {
    double maxLatency = (audioLatencySettings->bufferSizeInVideoFrames / contentRefreshRate) * 1000;
    return std::max(maxLatency, 32.0);
}

void Audio::start() {
    std::lock_guard<std::mutex> lock(streamMutex);
    startRequested = true;
    if (stream != nullptr)
        stream->requestStart();
}

void Audio::stop() {
    std::lock_guard<std::mutex> lock(streamMutex);
    startRequested = false;
    if (stream != nullptr)
        stream->requestStop();
}

void Audio::write(const int16_t *data, size_t frames) {
    fifoBuffer->write(data, frames * 2);
}

void Audio::setPlaybackSpeed(const double newPlaybackSpeed) {
    playbackSpeed.store(newPlaybackSpeed);
}

void Audio::setFastForwardSpeed(double speed) {
    fastForwardSpeed.store(speed);
}

void Audio::setFastForward(bool active, bool audible) {
    bool wasActive = fastForwardActive.exchange(active);
    fastForwardMutes = active && !audible;
    updateMute(wasActive && !active);
}

void Audio::setRewind(bool active, bool audible) {
    rewindMutes = active && !audible;
    updateMute(false);
}

void Audio::updateMute(bool fastForwardEnded) {
    const bool muted = fastForwardMutes || rewindMutes;
    const bool wasMuted = outputMuted.exchange(muted);
    // Unmuting restarts on an empty FIFO, as does the end of audible FF (it drained at FF speed): refill first. Not only
    // when FF ends — muted FF turned audible with FF still on needs it as much.
    if (fastForwardEnded || (wasMuted && !muted)) {
        refillPending.store(true);
    } else if (muted || fastForwardActive.load()) {
        // Muted, or FF starting: nothing to wait for (FF fills the FIFO faster than it plays).
        refillPending.store(false);
    }
}

oboe::DataCallbackResult Audio::onAudioReady(oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames) {
    auto outputArray = reinterpret_cast<int16_t *>(audioData);
    // A pause (0) wins over audible FF's speed, whichever thread wrote last.
    double speed = playbackSpeed.load();
    const double fastForward = fastForwardSpeed.load();
    if (speed > 0.0 && fastForward > 0.0) speed = fastForward;
    const bool muted = outputMuted.load();

    // Muted fast forward (or rewind): what the FIFO still holds is from before; dropped here (the reader's side) so it
    // isn't played when it ends. The core's samples are dropped before they reach the FIFO.
    if (muted) fifoBuffer->setReadCounter(fifoBuffer->getWriteCounter());

    // After FF: silent until the FIFO is refilled, so playback doesn't restart on an empty buffer and crackle.
    bool refilling = false;
    if (!muted && refillPending.load()) {
        double capacity = fifoBuffer->getBufferCapacityInFrames();
        if (fifoBuffer->getFullFramesAvailable() >= capacity * REFILL_FRACTION) {
            refillPending.store(false);
        } else {
            refilling = true;
        }
    }

    // Speed 0 (menu open) and muted FF: the FIFO gets nothing. Play silence and hold the rate controller, which would
    // otherwise wind up against the empty FIFO and skew the rate once the game resumes. Refilling after FF likewise.
    if (speed <= 0.0 || muted || refilling) {
        std::fill_n(outputArray, numFrames * 2, (int16_t) 0);
        converterResetPending = true;
        tuneLatency();
        return oboe::DataCallbackResult::Continue;
    }

    double dynamicBufferFactor = computeDynamicBufferConversionFactor(0.001 * numFrames);
    double finalConversionFactor = baseConversionFactor.load() * dynamicBufferFactor * speed;

    // The converter pulls the input frames this ratio needs from the FIFO (readInput), keeping the fraction of a frame
    // it is between from one callback to the next.
    SRC_STATE *converter = converterFor(fastForward > 0.0);
    constexpr double MAX_RATIO = 256.0;   // libsamplerate's limit either way
    const double ratio = std::clamp(1.0 / finalConversionFactor, 1.0 / MAX_RATIO, MAX_RATIO);
    int32_t framesDone = 0;
    while (converter != nullptr && framesDone < numFrames) {
        const long frames = std::min(numFrames - framesDone, OUTPUT_CHUNK_FRAMES);
        const long produced = src_callback_read(converter, ratio, frames, outputFloats.get());
        if (produced <= 0) break;
        src_float_to_short_array(outputFloats.get(), outputArray + framesDone * 2, (int) produced * 2);
        framesDone += (int32_t) produced;
    }
    std::fill(outputArray + framesDone * 2, outputArray + numFrames * 2, (int16_t) 0);

    tuneLatency();

    return oboe::DataCallbackResult::Continue;
}

void Audio::setResamplingQuality(int newQuality) {
    if (newQuality < LOW || newQuality > MAX) newQuality = HIGH;
    if (converters[newQuality].load() == nullptr) {
        static constexpr int TYPES[QUALITY_COUNT] = {
            SRC_ZERO_ORDER_HOLD, SRC_LINEAR, SRC_SINC_FASTEST, SRC_SINC_BEST_QUALITY
        };
        int error = 0;
        SRC_STATE *state = src_callback_new(readInput, TYPES[newQuality], 2, &error, this);
        if (state == nullptr) {
            LOGE("Resampler %d unavailable: %s", newQuality, src_strerror(error));
            return;   // the level in use stays
        }
        converters[newQuality].store(state);
    }
    quality.store(newQuality);
}

long Audio::readInput(void *self, float **data) {
    auto audio = static_cast<Audio *>(self);
    audio->fifoBuffer->readNow(audio->inputShorts.get(), INPUT_CHUNK_FRAMES * 2);
    src_short_to_float_array(audio->inputShorts.get(), audio->inputFloats.get(), INPUT_CHUNK_FRAMES * 2);
    *data = audio->inputFloats.get();
    return INPUT_CHUNK_FRAMES;
}

SRC_STATE *Audio::converterFor(bool audibleFastForward) {
    int level = quality.load();
    if (audibleFastForward) level = std::min(level, (int) MEDIUM);
    SRC_STATE *converter = converters[level].load();
    if (converter == nullptr) {
        level = MEDIUM;
        converter = converters[MEDIUM].load();
    }
    if (converter != nullptr && (level != playingQuality || converterResetPending)) {
        src_reset(converter);
        playingQuality = level;
        converterResetPending = false;
    }
    return converter;
}

// Realtime thread: never wait for streamMutex. A reopen or start/stop holding it just skips one tune.
void Audio::tuneLatency() {
    std::unique_lock<std::mutex> lock(streamMutex, std::try_to_lock);
    if (lock.owns_lock() && latencyTuner != nullptr) {
        latencyTuner->tune();
    }
}

// To prevent audio buffer overruns or underruns we set up a PI controller. The idea is to run the
// audio slower when the buffer is empty and faster when it's full.
double Audio::computeDynamicBufferConversionFactor(double dt) {
    double framesCapacityInBuffer = fifoBuffer->getBufferCapacityInFrames();
    double framesAvailableInBuffer = fifoBuffer->getFullFramesAvailable();

    // Error is represented by normalized distance to half buffer utilization. Range [-1.0, 1.0]
    double errorMeasure = (framesCapacityInBuffer - 2.0f * framesAvailableInBuffer) / framesCapacityInBuffer;

    // An empty FIFO means nothing is being produced (start-up, audio disabled), not a rate mismatch: don't
    // integrate it. Clamp the integral to the range the adjustment can use, so it can't wind up past it.
    if (framesAvailableInBuffer > 0) {
        errorIntegral += errorMeasure * dt;
    }
    errorIntegral = std::clamp(errorIntegral, -maxi / ki, maxi / ki);

    // Wikipedia states that human ear resolution is around 3.6 Hz within the octave of 1000–2000 Hz.
    // This changes continuously, so we should try to keep it a very low value.
    double proportionalAdjustment = std::clamp(kp * errorMeasure, -maxp, maxp);

    // Ki is a lot lower, so it's safe if it exceeds the ear threshold. Hopefully convergence will
    // be slow enough to be not perceptible. We need to battle test this value.
    double integralAdjustment = std::clamp(ki * errorIntegral, -maxi, maxi);

    double finalAdjustment = proportionalAdjustment + integralAdjustment;

    LOGD("Audio speed adjustments (p: %f) (i: %f)", proportionalAdjustment, integralAdjustment);

    return 1.0 - (finalAdjustment);
}

int32_t Audio::roundToEven(int32_t x) {
    return (x / 2) * 2;
}

void Audio::StreamErrorHandler::onErrorAfterClose(oboe::AudioStream* oldStream, oboe::Result result) {
    AudioStreamErrorCallback::onErrorAfterClose(oldStream, result);
    LOGI("Stream error in oboe::onErrorAfterClose %s", oboe::convertToText(result));

    if (result != oboe::Result::ErrorDisconnected)
        return;

    // Held across the reopen so ~Audio (detach) waits for it to finish.
    std::lock_guard<std::mutex> lock(ownerMutex);
    if (owner != nullptr) {
        owner->reopenStream();
    }
}

void Audio::StreamErrorHandler::detach() {
    std::lock_guard<std::mutex> lock(ownerMutex);
    owner = nullptr;
}

} //namespace libretrodroid
