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

#ifndef LIBRETRODROID_MICROPHONE_H
#define LIBRETRODROID_MICROPHONE_H

#include <atomic>
#include <memory>
#include <mutex>

#include "libretro.h"
#include "micfeed.h"
#include "oboe/AudioStreamCallback.h"
#include "oboe/FifoBuffer.h"

namespace libretrodroid {

// One core mic. Always answers read() from its MicFeed; the phone mic is open only while the feed wants it live.
class Microphone: public oboe::AudioStreamCallback {
public:
    explicit Microphone(int sampleRate);
    // Closes a stream still open: its callback points at this object.
    ~Microphone() override;
    Microphone(const Microphone& other) = delete;
    Microphone& operator=(const Microphone& other) = delete;

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* audioStream,
        void *audioData,
        int32_t numFrames
    ) override;
    // Oboe's error thread, once it has closed a disconnected stream (rerouted input, another app took the mic).
    void onErrorAfterClose(oboe::AudioStream* audioStream, oboe::Result error) override;

    // Any thread: leaving LIVE closes the stream before returning.
    void setSource(MicSource source);
    // Always fills numSamples: live samples padded with zeros, else silence or noise. Opens the stream when wanted.
    int read(int16_t* samples, int numSamples);

    // The core's set_mic_state: stopping closes the stream at once.
    void setRunning(bool shouldRun);
    bool isRunning() const;

    int sampleRate() const;

private:
    // Both with streamLock held.
    bool openStream();
    void closeStream();

    int mSampleRate = 44100;
    // FIFO only: the Oboe callback takes it, so never stop or close a stream while holding it.
    std::mutex lock;
    std::unique_ptr<oboe::FifoBuffer> fifoBuffer = nullptr;
    // Guards feed and inputStream: the core thread reads and opens, the app's thread (setSource) can close.
    mutable std::mutex streamLock;
    MicFeed feed;
    std::shared_ptr<oboe::AudioStream> inputStream;
    // The stream Oboe closed after an error; compared, never dereferenced. read() drops it and falls back to noise.
    std::atomic<oboe::AudioStream*> lostStream { nullptr };
};

}

#endif //LIBRETRODROID_MICROPHONE_H
