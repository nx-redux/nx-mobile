/*
 *     Copyright (C) 2025  Filippo Scognamiglio
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

#include "microphone.h"
#include "oboe/AudioStream.h"
#include "../log.h"
#include "libretro.h"

namespace libretrodroid {

oboe::DataCallbackResult Microphone::onAudioReady(
    oboe::AudioStream* audioStream,
    void* audioData,
    int32_t numFrames
) {
    std::lock_guard<std::mutex> l(lock);
    fifoBuffer->write(audioData, numFrames);
    return oboe::DataCallbackResult::Continue;
}

Microphone::Microphone(int sampleRate):
    mSampleRate(sampleRate),
    fifoBuffer(std::make_unique<oboe::FifoBuffer>(2, sampleRate / 2)),
    feed([this] { closeStream(); }) { }

Microphone::~Microphone() {
    std::lock_guard<std::mutex> s(streamLock);
    closeStream();
}

bool Microphone::openStream() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input);
    builder.setInputPreset(oboe::InputPreset::Generic);
    builder.setPerformanceMode(oboe::PerformanceMode::PowerSaving);
    builder.setSharingMode(oboe::SharingMode::Exclusive);
    builder.setSampleRate(mSampleRate);
    builder.setChannelCount(oboe::ChannelCount::Mono);
    builder.setFormat(oboe::AudioFormat::I16);
    builder.setCallback(this);

    // Not under the lock: the stream's callback takes it, and starting or closing waits on the callback thread.
    std::shared_ptr<oboe::AudioStream> stream;
    oboe::Result result = builder.openStream(stream);
    if (result != oboe::Result::OK || !stream) {
        LOGE("Failed to open microphone stream");
        return false;
    }

    result = stream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start stream");
        stream->close();
        return false;
    }

    inputStream = std::move(stream);
    LOGI("Microphone stream opened");
    return true;
}

void Microphone::closeStream() {
    std::shared_ptr<oboe::AudioStream> stream = std::move(inputStream);
    inputStream = nullptr;
    if (!stream) return;

    // Without the lock: stopping and closing wait for a running onAudioReady, which needs it.
    stream->stop();
    if (stream->close() != oboe::Result::OK) LOGE("Failed to close stream");
    LOGI("Microphone stream closed");

    // Drop what's left, so going live again doesn't replay old audio.
    std::lock_guard<std::mutex> l(lock);
    fifoBuffer->setReadCounter(fifoBuffer->getWriteCounter());
}

void Microphone::setSource(MicSource source) {
    std::lock_guard<std::mutex> s(streamLock);
    if (source == feed.source()) return;
    LOGI("Microphone source %d", (int) source);
    feed.setSource(source);
}

void Microphone::setRunning(bool shouldRun) {
    std::lock_guard<std::mutex> s(streamLock);
    if (shouldRun == feed.coreRunning()) return;
    LOGI("Microphone state %s", shouldRun ? "running" : "stopped");
    feed.setCoreRunning(shouldRun);
}

bool Microphone::isRunning() const {
    std::lock_guard<std::mutex> s(streamLock);
    return feed.coreRunning();
}

int Microphone::read(int16_t* samples, int numSamples) {
    std::lock_guard<std::mutex> s(streamLock);
    // Opened here, on the core's thread, as libretro mics always were; a failure falls back to noise.
    if (feed.wantsLive() && !inputStream && !openStream()) feed.onLiveFailed();

    if (feed.wantsLive() && inputStream) {
        int got;
        {
            std::lock_guard<std::mutex> l(lock);
            got = fifoBuffer->readNow(samples, numSamples);
        }
        if (got < 0) got = 0;
        for (int i = got; i < numSamples; i++) samples[i] = 0;
    } else {
        feed.fillSynthetic(samples, numSamples);
    }
    return numSamples;
}

int Microphone::sampleRate() const {
    return mSampleRate;
}

} // libretrodroid