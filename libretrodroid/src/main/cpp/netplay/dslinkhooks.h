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

#ifndef LIBRETRODROID_DSLINKHOOKS_H
#define LIBRETRODROID_DSLINKHOOKS_H

#include <vector>

#include "nl_netpacket.h"

struct Variable;

// The run loop's side of the DS link (ndl_link.h): melonDS DS's netpacket interface (env 78), the radio state it
// reports, the core's start/stop around a room, and packet delivery.
//
// setNetpacket, forgetCore, configure, radio, preFrame and stopCore run on the frame (GL) thread, the core's emulation
// thread, with coreLock held or no step possible (load, destroy); only that thread creates or destroys the link.
// inRoom, leaveNow and shutdown may run on any thread.
namespace libretrodroid::dslink {
    bool setNetpacket(const nl_netpacket_callback* callbacks);   // SET_NETPACKET_INTERFACE; NULL clears it
    void forgetCore();          // the core is gone or about to be replaced: drop its callbacks and the link
    void configure(const std::vector<Variable>& vars);   // the nx_nds_link* variables: create, replace or end the link
    void radio(unsigned state); // NX_ENVIRONMENT_SET_NDS_RADIO, from inside retro_run
    void preFrame();            // start/stop the core around a room, deliver queued packets; never holds the frame
    void stopCore();            // the core's stop() if its session is open, then the link ends; before retro_unload_game
    void shutdown();            // leaves the room at once; the link itself ends in stopCore
    bool inRoom();
    void leaveNow();            // backgrounded: leave without the radio-off linger
}

#endif //LIBRETRODROID_DSLINKHOOKS_H
