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
 * PSP ad hoc over Wi-Fi: how devices agree on which one runs the ad hoc server. A device in automatic mode announces
 * itself over UDP while it is active or serving; the others pick the best serving announcer, or start serving
 * themselves after listening for a while and finding none. Pure logic: the caller owns the socket, the thread and the
 * clock, and feeds packets and time in. Addresses are IPv4 in network byte order and are compared in host order.
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
#define PSL_ANNOUNCE_MS 500     /* while active or serving */
#define PSL_LISTEN_MS   1000    /* an automatic device waits this long for a server before starting one */
#define PSL_EXPIRE_MS   3000    /* a server not heard for this long is gone */
#define PSL_LINGER_MS   10000   /* an automatic server outlives its game's ad hoc mode by this long */
#define PSL_MAX_PEERS   8

enum { PSL_AUTO, PSL_HOST, PSL_JOIN };
enum { PSL_F_SERVING = 1, PSL_F_SESSION = 2, PSL_F_GROUPED = 4 };

typedef struct {
    uint32_t server_ip;
    int serve;
    int settled;
    int announce;
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
    int active, grouped, parked, serving;
    int64_t listen_from_ms;   /* the listen window opens here: activation, or when the last server was lost */
    int64_t left_ms;          /* when the device stopped being active */
    int64_t server_heard_ms;  /* last time any serving peer was heard */
    int heard_server;
    psl_peer peers[PSL_MAX_PEERS];
} psl;

void   psl_init(psl*, int mode, uint32_t self_ip, uint32_t tie, uint32_t host_ip);
void   psl_set_self(psl*, uint32_t self_ip);                 /* the Wi-Fi address changed */
void   psl_active(psl*, int active, int grouped, int64_t now_ms);  /* trigger reached / in a group / left */
void   psl_park(psl*, int parked);                           /* Disconnect: no server, no announce, unsettled */
void   psl_heard(psl*, const uint8_t* pkt, size_t len, uint32_t from_ip, int64_t now_ms);
size_t psl_pack(const psl*, uint8_t out[PSL_PACKET]);        /* 0 when nothing is to be announced */
psl_choice psl_step(psl*, int64_t now_ms);

#endif
