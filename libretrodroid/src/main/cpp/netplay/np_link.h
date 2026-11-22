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
 *
 *     Ported from NX Redux workspace/all/netplay/gbalink.c @ 2f25643b (GPL-3.0): the transport only (no discovery,
 *     hotspot, siolink or UI).
 */

/*
 * GBA link over TCP (the handheld's GBALink), for cores with the libretro netpacket interface: gpSP (wireless adapter,
 * cable fakes, real cable) and mGBA (real cable). No input exchange and no state sync: each device runs its own game
 * on its own save, and the core's packets cross the wire as they are.
 *
 * Wire: 5-byte header [cmd u8][size u16 BE][client_id u16 BE], payload <= 2048. The client sends an empty READY; the
 * host answers READY with its link mode ("auto\0", or no payload). The host sends HEARTBEAT after 500 ms with nothing
 * sent, the client echoes each one; 60 s without a packet ends the session. PAUSE carries 1 (menu open) or 0.
 */

#ifndef NP_LINK_H
#define NP_LINK_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define NL_HDR_SIZE 5
#define NL_MAX_PAYLOAD 2048
#define NL_MODE_MAX 31
#define NL_NXLS_SIZE 24 /* a real-cable message: "NXLS" + 20 bytes (sio_lockstep.h SIOLS_MSG_SIZE) */
#define NL_NXLS_HOLD 4	/* its type byte for HOLD (word byte 12: 1 hold, 0 release) */

#define NL_CMD_SIO_DATA 0x01
#define NL_CMD_DISCONNECT 0x04
#define NL_CMD_READY 0x05
#define NL_CMD_HEARTBEAT 0x06
#define NL_CMD_PAUSE 0x07

typedef enum { NL_ROLE_HOST = 0, NL_ROLE_CLIENT = 1 } nl_role;

typedef enum {
	NL_IDLE = 0,		/* not started (or after nl_shutdown) */
	NL_CONNECTING = 1,	/* host: waiting for the peer and its READY (again when the joiner leaves within 10 s of READY with
						 * no data sent: the handheld reloading for the host's mode); client: connecting, then waiting for
						 * the host's READY */
	NL_LINKED = 2,		/* READY exchanged: packets flow */
	NL_PEER_PAUSED = 3, /* the peer sent PAUSE 1; seconds_left counts down from 30 */
	NL_ENDED = 4,		/* over after linking (a host's joiner: once it sent data or stayed 10 s): see the reason */
	NL_FAILED = 5		/* never linked: connect failed, refused, or no READY */
} nl_state;

typedef enum {
	NL_REASON_NONE = 0,		 /* local nl_leave / nl_shutdown */
	NL_REASON_PEER_LEFT = 1, /* DISCONNECT or the connection closed */
	NL_REASON_TIMEOUT = 2,	 /* 60 s silence, or a pause (either side) past 30 s */
	NL_REASON_CONNECT = 4,	 /* no connection, the host turned us away, or no READY in 5 s */
	NL_REASON_IO = 5		 /* socket error, or the send queue stuck for 2 s */
} nl_reason;

typedef uint64_t (*nl_clock_fn)(void); /* monotonic ms; NULL = CLOCK_MONOTONIC */
typedef void (*nl_deliver_fn)(const uint8_t* buf, size_t len, void* ctx);

/* Threads: nl_frame, nl_take_lockstep and nl_send from the frame (GL) thread; everything else from any thread. */

/* 0 ok; spawns the network thread. peer_ip: the wizard's peer, dotted IPv4, both roles (the host accepts only it).
 * mode: the host's READY payload (gpSP's raw gpsp_serial, "" for none; at most NL_MODE_MAX chars); ignored for a client.
 * -1: already started, bad address, mode too long, or the host cannot bind. */
int nl_start(nl_role role, const char* peer_ip, uint16_t port, const char* mode, nl_clock_fn clock);
/* Client: 1 once the host's READY came, with its mode copied into out ("" when it sent none); 0 before that, and always
 * for a host. */
int nl_host_mode(char* out, size_t cap);
/* 0 host, 1 client, -1 not started. The core's client id. */
int nl_client_id(void);
/* Menu open or in the background: frames stop here and PAUSE 1 goes out (plus HOLD twice once real-cable traffic was
 * seen). 30 s of it ends the session. Idempotent. */
void nl_pause(void);
void nl_resume(void); /* PAUSE 0 (and HOLD release); idempotent */
void nl_leave(void);  /* DISCONNECT, then ENDED (reason NONE) */
/* Both pointers may be NULL; seconds_left counts the peer's pause only (0 otherwise). */
nl_state nl_get_state(int* seconds_left, nl_reason* reason);
bool nl_active(void); /* started and not ENDED/FAILED */
/* GL thread, once per video frame: hands over the queued packets (real-cable ones first, then at most 64 others in
 * arrival order) through deliver, then says whether the frame runs: 0 while this device or the peer is paused, else 1
 * (also while the host waits for its peer, and after the session is over). Never blocks on the network. */
int nl_frame(nl_deliver_fn deliver, void* ctx);
/* GL thread, the core's mid-frame poll: hands over queued real-cable messages only (none when the lock is busy: never
 * blocks), then an owed HOLD release (nl_release_hold) once: NXLS type NL_NXLS_HOLD, byte 12 = 0. The release is
 * delivered even after the session ended or nl_shutdown reset it, but only when real-cable traffic was seen since the
 * last nl_start (a wireless-adapter or cable-fake core would misread it). Returns how many. */
int nl_take_lockstep(nl_deliver_fn deliver, void* ctx);
/* GL thread, the core's send: one SIO_DATA with client_id in its header. 0 queued, -1 when not linked or 0/oversize. */
int nl_send(const void* buf, size_t len, uint16_t client_id);
void nl_shutdown(void); /* DISCONNECT if linked, join the thread, close, reset to IDLE (safe to call twice) */
/* Any thread, never blocks (sets a flag): this device must not stay held by the peer's real-cable HOLD (the app pauses
 * or closes the game). The core's next mid-frame poll (nl_take_lockstep) gets one synthetic HOLD release and falls back
 * to its own 100 ms / 500 ms timeouts, so retro_run returns within about 0.5 s. nl_leave, nl_shutdown and every session
 * end owe the same release. */
void nl_release_hold(void);

/* Pure helpers. */
void nl_encode_header(uint8_t out[NL_HDR_SIZE], uint8_t cmd, uint16_t size, uint16_t client_id);
bool nl_is_lockstep(const void* buf, size_t len); /* "NXLS" + exactly NL_NXLS_SIZE bytes */

#ifdef __cplusplus
}
#endif

#endif /* NP_LINK_H */
