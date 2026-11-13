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

/*
 * PSP ad hoc over Wi-Fi or the USB cable: how devices agree on which one runs the ad hoc server. A device in automatic
 * mode announces itself over UDP while it is active or serving; the others pick the best serving announcer, or start
 * serving themselves after listening for a while and finding none. The election runs on one network at a time, since
 * a session's data goes device to device at the address the server saw: the cable while it is up, else the Wi-Fi.
 * Pure logic: the caller owns the sockets, the thread and the clock, and feeds packets and time in. Addresses are IPv4
 * in network byte order and are compared in host order.
 *
 * Wire (16 bytes): "NXPL", version u8, flags u8, 2 reserved zero bytes, announcer IP (4 bytes as given), tie u32 BE.
 */

#ifndef PSL_FINDER_H
#define PSL_FINDER_H

#include <stddef.h>
#include <stdint.h>

#define PSL_PORT        27314   /* UDP; the ad hoc server is 27312, its relay 27313 */
#define PSL_PACKET      16
#define PSL_VERSION     1
#define PSL_ANNOUNCE_MS 500     /* while active */
#define PSL_SERVING_ANNOUNCE_MS 200   /* while serving: a device that starts listening hears a server well inside its window */
#define PSL_LISTEN_MS   1000    /* an automatic device waits this long for a server before starting one */
#define PSL_EXPIRE_MS   3000    /* a server not heard for this long is gone */
#define PSL_LINGER_MS   10000   /* an automatic server outlives its game's ad hoc mode by this long */
#define PSL_MAX_PEERS   8
#define PSL_CABLE_HOLD_MS 6000  /* a cable that is coming is waited for this long before the Wi-Fi is used */

enum { PSL_AUTO, PSL_HOST, PSL_JOIN };
enum { PSL_F_SERVING = 1, PSL_F_SESSION = 2, PSL_F_GROUPED = 4 };
enum { PSL_CABLE_NONE, PSL_CABLE_COMING, PSL_CABLE_UP };   /* as the cable watcher reports it */

typedef struct {
    uint32_t server_ip;
    int serve;
    int settled;
    int announce;
    int cable;        /* the network of this choice, and of its server and announcements: 1 the cable, 0 the Wi-Fi */
    uint32_t self;    /* this device's address on that network */
    int held;         /* unsettled because a cable is coming */
} psl_choice;

typedef struct {
    uint32_t ip;
    uint32_t tie;
    uint8_t flags;
    int64_t heard_ms;
    int used;
} psl_peer;

typedef struct psl {
    int mode;
    uint32_t self_ip, tie, host_ip;
    uint32_t broadcast_ip;    /* with self_ip gives the subnet a packet must come from; 0: any source */
    int active, grouped, parked, serving;
    int64_t listen_from_ms;   /* the listen window opens here: activation, or when the last server was lost */
    int64_t left_ms;          /* when the device stopped being active */
    int64_t server_heard_ms;  /* last time any serving peer was heard */
    int heard_server;
    int cable_state;          /* as last set, PSL_CABLE_* */
    uint32_t cable_self;      /* this device's address on the cable */
    uint32_t cable_broadcast; /* gives the cable's /30; 0 until set: 10.99.0.0/30 */
    int on_cable;             /* automatic mode: the network the election is on */
    int holding;
    int64_t hold_until_ms;
    uint32_t served_ip;       /* the address this device last served from: where its goodbye comes from */
    psl_peer peers[PSL_MAX_PEERS];
} psl;

void   psl_init(psl*, int mode, uint32_t self_ip, uint32_t tie, uint32_t host_ip);
void   psl_set_self(psl*, uint32_t self_ip);                 /* the Wi-Fi address changed */
/* The Wi-Fi network's broadcast address: packets from outside the subnet it and self_ip share are ignored, since the
 * server to follow is a packet's source. When no mask can be had from the two, every source is accepted. */
void   psl_set_net(psl*, uint32_t broadcast_ip);
/* The cable's state, this device's address on it and its broadcast (0 keeps the one known). UP without an address is
 * no cable. An automatic device moves to the cable when it is up and it is not grouped, and back to the Wi-Fi as soon
 * as it is not up, grouped or not: the peers are forgotten, any server stops and the listen window starts again. A
 * transition into COMING while active, the trigger while COMING, or leaving a group in ad hoc while COMING, holds the
 * Wi-Fi election for PSL_CABLE_HOLD_MS (not for a grouped device): a server started or followed there now would keep
 * the game off the cable. A session's host and joiner are on the network self_ip is on, whatever the cable does. */
void   psl_set_cable(psl*, int state, uint32_t cable_self, uint32_t cable_broadcast, int64_t now_ms);
/* The game reached its trigger / left ad hoc. grouped: the game is in a group, or this device's server has another
 * user; either way the server keeps them (it never yields), and out of ad hoc it lingers until they are gone. */
void   psl_active(psl*, int active, int grouped, int64_t now_ms);
/* Disconnect: no server, no announce, unsettled, in every mode (a session's host stops serving, its joiner stops
 * following); unparking gives host and join their fixed choice back. */
void   psl_park(psl*, int parked);
/* A packet that no longer says serving from the server being followed means it stopped: the listen window starts
 * again from now rather than after PSL_EXPIRE_MS. */
void   psl_heard(psl*, const uint8_t* pkt, size_t len, uint32_t from_ip, int64_t now_ms);
/* 0 when nothing is to be announced. For the current network only: sent to the broadcast of choice.cable. */
size_t psl_pack(const psl*, uint8_t out[PSL_PACKET]);
/* The last packet of a server that stops (parked, linger over, yielded, held, moved network, the link gone): not
 * serving, so its followers drop it at once. It belongs on the network the server was on: the cable of the last
 * choice that served. Always PSL_PACKET. */
size_t psl_pack_goodbye(const psl*, uint8_t out[PSL_PACKET]);
psl_choice psl_step(psl*, int64_t now_ms);

#endif
