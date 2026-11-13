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

#ifndef LIBRETRODROID_PSPLINKHOOKS_H
#define LIBRETRODROID_PSPLINKHOOKS_H

#include <cstdint>
#include <vector>

struct Variable;
struct nx_psp_link_interface;

// The runner's side of PSP ad hoc over Wi-Fi (nx_psp_link.h): a finder thread that runs the election (psl_finder.h)
// on its own UDP socket and tells the core which device runs the ad hoc server. The core's link reaches the app through
// dslink's core-room path, so presence, leave and rejoin work as for a 3DS room.
//
// setCore may come from any thread (the core's), but never from inside one of the core's functions the runner calls.
// configure and forgetCore run on the core's emulation thread, as dslink's do. setNetwork may run on any thread.
namespace libretrodroid::psplink {
    void setCore(struct ::nx_psp_link_interface* link);   // the environment call; nullptr clears
    void configure(const std::vector<Variable>& vars);    // nx_psp_link = auto|host|join, nx_psp_link_ip, nx_psp_link_broadcast, nx_psp_link_self
    void setNetwork(int64_t handle);                      // the Wi-Fi network changed: route the finder's socket again
    void forgetCore();                                    // the core is gone or about to be: never call into it again
}

#endif //LIBRETRODROID_PSPLINKHOOKS_H
