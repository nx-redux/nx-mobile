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
 * DS local wireless between devices: a room over UDP for melonDS DS's netpacket packets. One phone holds the room and
 * is its member 1; the others register with it and get ids 2..max. Every packet goes through the holder, which relays
 * a broadcast to every other member and a directed one to its member, tagged with the sender's id, so the DS game's
 * host need not be the room's holder. Packets are opaque, unreliable and unordered: the DS protocol retries by itself
 * and a late packet is worse than a lost one, so nothing is retransmitted.
 *
 * Wire (one message per datagram): ['N']['D'][version 1][type u8][sender u16 BE][dest u16 BE] + payload.
 *   ASK 1 (to the search port), ANSWER 2 (room_id u32 BE, settled u8, members u8, room_port u16 BE): the search. Every
 *   holder answers on the search port (a sheet host too, so the automatic route finds it); only AUTO asks.
 *   JOIN 3 (name, up to 16 bytes); ACCEPT 4 (dest = the new id; payload members u8); REFUSE 5 (room full).
 *   DATA 6 (a core packet); LEAVE 7; KEEPALIVE 8 (from the holder: payload members u8, plus the
 *   member's probe stamp u32 BE when echoing; from a member: the probe stamp u32 BE, or empty from older builds).
 * Each side sends a KEEPALIVE every 500 ms; 3 s without hearing a member drops it, 3 s without hearing the holder makes
 * a member leave.
 *
 * Per instance (an ndl_link object), so one process can run a holder and its members.
 */

#ifndef NDL_LINK_H
#define NDL_LINK_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define NDL_MAX_PAYLOAD 2400
#define NDL_HDR_SIZE 8
#define NDL_BROADCAST 0xFFFF
#define NDL_MAX_MEMBERS 8

typedef enum { NDL_OFF, NDL_AUTO, NDL_HOST, NDL_JOIN } ndl_mode;

/* Equal to NX_NDS_RADIO_* (nx_nds_radio.h). */
typedef enum { NDL_RADIO_OFF = 0, NDL_RADIO_ON = 1, NDL_RADIO_HOSTING = 2 } ndl_radio;

typedef struct {
	ndl_mode mode;
	char name[17];		/* this phone's name in the room */
	uint16_t room_port; /* HOST binds it; JOIN sends to host_ip on it */
	uint16_t search_port;
	char broadcast[16];
	char host_ip[16];		/* JOIN only */
	int max_members;		/* 2..NDL_MAX_MEMBERS; anything else means NDL_MAX_MEMBERS */
	uint64_t (*clock)(void); /* milliseconds; NULL = np_net_now_ms */
	/* Called on every socket the link creates, before bind: lets the platform pin it to one network (the default one
	 * can be mobile data, which would carry replies away from the LAN). NULL = nothing. */
	void (*bind_socket)(int fd, void* ctx);
	void* bind_ctx;
} ndl_config;

typedef struct ndl_link ndl_link;

/* A room as a search answer tells of it. Settled: a sheet session's room, or one with another member. */
typedef struct {
	uint32_t room_id;
	bool settled;
} ndl_room_info;

/* True when a is the room to be in rather than b: settled beats unsettled, else the lower id. Every phone that sees
 * the same two rooms picks the same one. */
bool ndl_prefer(ndl_room_info a, ndl_room_info b);

typedef void (*ndl_deliver_fn)(const uint8_t* buf, size_t len, uint16_t sender, void* ctx);

/* Starts the network thread. HOST opens its room before returning (settled); JOIN asks host_ip every 1 s, 30 times.
 * AUTO sends nothing until the radio is on, then searches (300 ms of answers): it joins the preferred room found, or
 * opens one when the radio is HOSTING, else searches again after 700 ms. A lone AUTO holder searches every 2 s and
 * moves to a room it should prefer. Radio off keeps a room for 10 s, then leaves. NULL when the socket, wake pipe or
 * thread cannot be set up. */
ndl_link* ndl_create(const ndl_config* cfg);

/* Stops and joins the thread, then sends LEAVE when in a room. NULL is fine. */
void ndl_destroy(ndl_link* l);

/* Leaves the current room at once (OFF stays out), then starts the new mode. */
void ndl_set_mode(ndl_link* l, ndl_mode m);

void ndl_radio_changed(ndl_link* l, ndl_radio r);

/* Leaves at once, without the radio-off linger. The link then stays out of rooms until ndl_set_mode (or, in AUTO, the
 * next radio ON). */
void ndl_leave_now(ndl_link* l);

bool ndl_in_room(ndl_link* l);
/* This phone's member id, -1 when not in a room. */
int ndl_client_id(ndl_link* l);
/* Members in the room including this phone, 0 when not in a room. */
int ndl_member_count(ndl_link* l);

/* Round trip of the member's keepalive probe to the holder since the last call, in ms; -1 each when there is no sample
 * (the holder, or nothing came back). Resets what it reports. */
void ndl_stats_rtt(ndl_link* l, int* min_ms, int* avg_ms, int* max_ms);

/* Datagrams the link dropped or did not deliver since the last ndl_stats_drops, by reason. */
typedef struct {
	uint32_t queue;		/* inbound queue full */
	uint32_t unknown;	/* holder: from an address that is no member (and not a JOIN) */
	uint32_t wrong_src; /* member: from an address that is not the holder */
	uint32_t bad;		/* bad magic, version or length, or an empty DATA */
	uint32_t echo;		/* a member's own packet relayed back */
	uint32_t dest;		/* addressed to another member, or to one that is gone */
	uint32_t queue_hwm; /* most packets waiting in the inbound queue at once (not a drop) */
} ndl_drop_counts;

/* Fills out with the counts since the last call and resets them. */
void ndl_stats_drops(ndl_link* l, ndl_drop_counts* out);

/* One core packet to dest (a member id, or NDL_BROADCAST for everyone else), sent from the calling thread.
 * 0 sent; -1 when not in a room, len is 0 or above NDL_MAX_PAYLOAD, or dest is this phone or 0. */
int ndl_send(ndl_link* l, const void* buf, size_t len, uint16_t dest);

/* Emulation thread: hands every packet queued so far to fn, in arrival order, and returns how many. The lock is never
 * held while fn runs. */
int ndl_take(ndl_link* l, ndl_deliver_fn fn, void* ctx);

#ifdef NDL_TESTING
/* Freezes the network thread (no receive, no keepalive, no timeout) and makes ndl_destroy skip its LEAVE, as if the
 * phone vanished. */
void ndl_test_freeze(ndl_link* l, bool frozen);
/* Datagrams this link has sent so far, of any type. */
uint32_t ndl_test_sent(ndl_link* l);
#endif

#ifdef __cplusplus
}
#endif

#endif /* NDL_LINK_H */
