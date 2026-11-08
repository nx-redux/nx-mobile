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

#ifndef LIBRETRODROID_SHAREDCONTEXTS_H
#define LIBRETRODROID_SHAREDCONTEXTS_H

#include <vector>

#include "nxsharedgl.h"

namespace libretrodroid {

// Which of a fixed number of shared-context slots are taken. Pure, host-tested.
class SharedContextSlots {
public:
    explicit SharedContextSlots(int max) : used(max, false) {}
    // The lowest free slot, or -1 when all are taken.
    int acquire();
    // Frees a taken slot; false for a free or out-of-range one.
    bool release(int slot);
    int live() const;
private:
    std::vector<bool> used;
};

// Contexts for a core's worker threads (Dolphin's shader compilers), answering NX_ENVIRONMENT_GET_SHARED_GL. Each shares
// objects with the context current on the calling thread (the session's, also right after a context loss) and has its own 1x1 pbuffer, or none when
// EGL_KHR_surfaceless_context is there. At most kMaxSharedContexts live.
namespace SharedContexts {
constexpr int kMaxSharedContexts = 8;
void fill(nx_shared_gl_interface* out);
// Session teardown, after the core unloaded: frees any the core left (it should have destroyed its own).
void destroyAll();
}

}

#endif //LIBRETRODROID_SHAREDCONTEXTS_H
