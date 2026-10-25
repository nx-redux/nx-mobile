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
#include "micfeed.h"

namespace libretrodroid {

void MicFeed::setSource(MicSource source) {
    if (source == mSource) return;
    bool wasLive = liveWanted();
    mSource = source;
    liveFailed = false;
    if (wasLive && !liveWanted() && onStopLive) onStopLive();
}

void MicFeed::setCoreRunning(bool running) {
    if (running == mCoreRunning) return;
    bool wasLive = liveWanted();
    mCoreRunning = running;
    if (wasLive && !liveWanted() && onStopLive) onStopLive();
}

MicSource MicFeed::effective() const {
    if (!mCoreRunning) return MicSource::SILENCE;
    if (mSource == MicSource::LIVE && liveFailed) return MicSource::NOISE;
    return mSource;
}

void MicFeed::fillSynthetic(int16_t* out, int n) {
    if (effective() != MicSource::NOISE) {
        for (int i = 0; i < n; i++) out[i] = 0;
        return;
    }
    for (int i = 0; i < n; i++) {
        // xorshift32: cheap, and white enough for a core's loudness check.
        noiseState ^= noiseState << 13;
        noiseState ^= noiseState >> 17;
        noiseState ^= noiseState << 5;
        int64_t span = 2 * (int64_t) MIC_NOISE_PEAK + 1;
        out[i] = (int16_t) ((int64_t) (noiseState % (uint32_t) span) - MIC_NOISE_PEAK);
    }
}

}
