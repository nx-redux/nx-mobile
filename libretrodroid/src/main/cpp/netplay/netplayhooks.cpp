/*
 *     Copyright (C) 2026  nx-mobile contributors
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

#include "netplayhooks.h"

#include "../core.h"
#include "../input.h"
#include "../../libretro-common/include/libretro.h"

extern "C" {
#include "np_engine.h"
}

namespace libretrodroid::netplay {

// P1 and P2 joypad words and sticks (LX, LY, RX, RY) of the frame being run. Frame thread only.
static uint16_t words[2] = {0, 0};
static int16_t sticks[2][NP_ANALOG_AXES] = {{0, 0, 0, 0}, {0, 0, 0, 0}};

bool active() { return np_active(); }

// The local player is read from port 0, whichever player they are in the session.
static uint16_t localWord(Input& input) {
    uint16_t word = 0;
    for (unsigned id = 0; id < 16; id++) {
        if (input.getInputState(0, RETRO_DEVICE_JOYPAD, 0, id)) word |= (uint16_t) (1u << id);
    }
    return word;
}

// This device's sticks from port 0: raw int16 in libretro's range, as the handheld sends its pad axes.
static void localSticks(Input& input, int16_t out[NP_ANALOG_AXES]) {
    for (unsigned index = 0; index < 2; index++) {
        for (unsigned id = 0; id < 2; id++) {
            out[index * 2 + id] = input.getInputState(0, RETRO_DEVICE_ANALOG, index, id);
        }
    }
}

bool preFrame(Core& core, Input& input) {
    int16_t local[NP_ANALOG_AXES];
    localSticks(input, local);
    if (!np_pre_frame(localWord(input), local, core.retro_serialize_size, core.retro_serialize, core.retro_unserialize)) {
        return false;
    }
    for (unsigned port = 0; port < 2; port++) {
        words[port] = np_input(port);
        for (unsigned index = 0; index < 2; index++) {
            for (unsigned id = 0; id < 2; id++) sticks[port][index * 2 + id] = np_analog(port, index, id);
        }
    }
    return true;
}

void postFrame() { np_post_frame(); }

int16_t inputState(unsigned port, unsigned device, unsigned index, unsigned id) {
    // Ports past P2, pointers and the analog buttons stay neutral: P1's and P2's joypad words and sticks cross the wire.
    if (port > 1) return 0;
    unsigned base = device & RETRO_DEVICE_MASK;
    if (base == RETRO_DEVICE_ANALOG) {
        return (index <= RETRO_DEVICE_INDEX_ANALOG_RIGHT && id <= RETRO_DEVICE_ID_ANALOG_Y) ? sticks[port][index * 2 + id] : 0;
    }
    if (base != RETRO_DEVICE_JOYPAD || index != 0) return 0;
    if (id == RETRO_DEVICE_ID_JOYPAD_MASK) return (int16_t) words[port];
    return id < 16 ? (int16_t) ((words[port] >> id) & 1) : 0;
}

void shutdown() { np_shutdown(); }

bool setPairMode(Core* core, bool on) {
    np_hash_fn hash = (on && core != nullptr) ? reinterpret_cast<np_hash_fn>(core->symbol("nx_pair_hash")) : nullptr;
    np_set_pair_mode(hash != nullptr, hash);
    return !on || hash != nullptr;
}

}
