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

#include "microphoneinterface.h"

#include <mutex>
#include <unordered_set>

#include "../log.h"
#include "microphone.h"

namespace libretrodroid {

namespace {
// The source and the open mics, so setSource reaches each one. Taken before a mic's own lock, never after.
std::mutex registryLock;
MicSource currentSource = MicSource::SILENCE;
std::unordered_set<Microphone*> openMicrophones;
}

void MicrophoneInterface::setSource(int source) {
    MicSource micSource = MicSource::SILENCE;
    if (source == (int) MicSource::LIVE || source == (int) MicSource::NOISE) micSource = (MicSource) source;

    std::lock_guard<std::mutex> r(registryLock);
    if (micSource == currentSource) return;
    LOGI("Microphone source set to %d", source);
    currentSource = micSource;
    for (Microphone* microphone : openMicrophones) microphone->setSource(micSource);
}

retro_microphone_interface* MicrophoneInterface::getInterface() {
    LOGI("Fetching microphone interface");

    // One static table: the caller copies it, and a new one per request was never freed.
    static retro_microphone_interface interface {
        .interface_version = 1,
        .open_mic = libretroOpenMicrophone,
        .close_mic = libretroCloseMicrophone,
        .get_params = libretroGetMicrophoneParams,
        .set_mic_state = libretroSetMicrophoneState,
        .get_mic_state = libretroGetMicrophoneState,
        .read_mic = libretroReadMicrophone,
    };
    return &interface;
}

retro_microphone_t* MicrophoneInterface::libretroOpenMicrophone(const retro_microphone_params_t* params) {
    // Never fails: no stream opens until the core starts the mic with the source live, and a phone mic that can't
    // open falls back to noise, so the core always has a mic to read.
    auto* result = new Microphone(params != nullptr ? (int) params->rate : 44100);
    std::lock_guard<std::mutex> r(registryLock);
    result->setSource(currentSource);
    openMicrophones.insert(result);
    LOGI("Opened microphone");
    return reinterpret_cast<retro_microphone_t*>(result);
}

void MicrophoneInterface::libretroCloseMicrophone(retro_microphone_t* opaqueMicrophone) {
    if (opaqueMicrophone == nullptr) return;
    LOGI("Closed microphone");
    auto microphone = reinterpret_cast<Microphone*>(opaqueMicrophone);
    {
        std::lock_guard<std::mutex> r(registryLock);
        openMicrophones.erase(microphone);
    }
    delete microphone;
}

bool MicrophoneInterface::libretroGetMicrophoneParams(
    const retro_microphone_t* opaqueMicrophone,
    retro_microphone_params_t* params
) {
    if (opaqueMicrophone == nullptr || params == nullptr) return false;
    auto microphone = reinterpret_cast<const Microphone*>(opaqueMicrophone);
    params->rate = microphone->sampleRate();
    return true;
}

bool MicrophoneInterface::libretroSetMicrophoneState(
    retro_microphone_t* opaqueMicrophone,
    bool state
) {
    if (opaqueMicrophone == nullptr) return false;
    auto microphone = reinterpret_cast<Microphone*>(opaqueMicrophone);
    microphone->setRunning(state);
    return true;
}

bool MicrophoneInterface::libretroGetMicrophoneState(const retro_microphone_t* opaqueMicrophone) {
    if (opaqueMicrophone == nullptr) return false;
    auto microphone = reinterpret_cast<const Microphone*>(opaqueMicrophone);
    return microphone->isRunning();
}

int MicrophoneInterface::libretroReadMicrophone(
    retro_microphone_t* opaqueMicrophone,
    int16_t* samples,
    size_t num_samples
) {
    if (opaqueMicrophone == nullptr || samples == nullptr) return -1;
    auto microphone = reinterpret_cast<Microphone*>(opaqueMicrophone);
    return microphone->read(samples, (int) num_samples);
}

} // libretrodroid