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
#ifndef LIBRETRODROID_MICFEED_H
#define LIBRETRODROID_MICFEED_H

#include <cstdint>
#include <functional>
#include <utility>

namespace libretrodroid {

// Same values as the app's MicSource and LibretroDroid.setMicrophoneSource.
enum class MicSource : int { SILENCE = 0, LIVE = 1, NOISE = 2 };

// Peak of the synthetic "blow" noise. Tune on device: Fossil Fighters' and Spirit Tracks' blow prompts must pass.
constexpr int MIC_NOISE_PEAK = 12000;

/**
 * nx-mobile 2026-10-25: what a core's mic hears. Every read_mic gets samples: live ones while the source is LIVE and
 * the core has the mic running, else silence or noise. No Oboe here and no locking: the owner (Microphone) serialises
 * the calls and does the stream work in onStopLive.
 */
class MicFeed {
public:
    // onStopLive: a live stream may be open and is no longer wanted, so close it now (not on the next read, which a
    // paused game never makes).
    explicit MicFeed(std::function<void()> onStopLive = nullptr): onStopLive(std::move(onStopLive)) { }

    void setSource(MicSource source);
    MicSource source() const { return mSource; }
    void setCoreRunning(bool running);
    bool coreRunning() const { return mCoreRunning; }

    bool wantsLive() const { return mSource == MicSource::LIVE && mCoreRunning && !liveFailed; }
    // The stream couldn't open: noise instead, without retrying every frame, until the source next changes.
    void onLiveFailed() { liveFailed = true; }
    MicSource effective() const;

    // Zeros for SILENCE (and LIVE, as padding); white noise for NOISE.
    void fillSynthetic(int16_t* out, int n);

private:
    std::function<void()> onStopLive;
    MicSource mSource = MicSource::SILENCE;
    bool mCoreRunning = false;
    bool liveFailed = false;
    uint32_t noiseState = 0x9E3779B9u;
};

}

#endif //LIBRETRODROID_MICFEED_H
