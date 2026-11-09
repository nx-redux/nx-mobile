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
 *     Ported from NX Redux workspace/all/netplay/netplay.h @ 19d3a5e1 (GPL-3.0).
 */

/*
 * NXRedux Netplay Module
 * Based on RetroArch netplay architecture
 * Implements frame-synchronized multiplayer over WiFi
 */

#ifndef NP_ENGINE_H
#define NP_ENGINE_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum { NP_ROLE_HOST = 0, NP_ROLE_CLIENT = 1 } np_role;

typedef enum {
	NP_IDLE = 0,	/* not started (or after np_shutdown) */
	NP_CONNECTING,	/* host: listening for the one client; client: connecting (60 x 500 ms retries, ~30 s) */
	NP_SYNCING,		/* host -> client state transfer */
	NP_RUNNING,		/* lockstep (also while only the local player is paused; see np_pause) */
	NP_STALLED,		/* the run frame lacks the peer's input; seconds_left counts down */
	NP_PEER_PAUSED, /* the peer sent PAUSE; seconds_left counts down */
	NP_ENDED,		/* session over: see the reason */
	NP_FAILED,		/* never got going: connect failed, state mismatch, sync timeout */
	NP_RESYNCING	/* pair mode: the hashes differed; the host's state is replacing the client's */
} np_state;

typedef enum {
	NP_REASON_NONE = 0,	  /* local np_leave / np_shutdown */
	NP_REASON_PEER_LEFT,  /* the peer sent DISCONNECT or closed the connection */
	NP_REASON_TIMEOUT,	  /* 30 s stall/pause countdown, or a sync wait (10 s; the client's STATE_HDR wait 45 s), ran out */
	NP_REASON_STATE_MISMATCH,
	NP_REASON_CONNECT,
	NP_REASON_IO,
	NP_REASON_DRIFT,	/* pair mode: the third hash mismatch within 60 s */
	NP_REASON_TOO_SLOW	/* pair mode: a frame thread missed the frame budget for more than 10 s while its peer waited */
} np_reason;

/* Threads: np_pre_frame / np_post_frame / np_input / np_analog only from the frame (GL) thread; np_start / np_pause /
 * np_resume / np_leave / np_get_state / np_active / np_shutdown from any thread (np_pause between a pre_frame that
 * returned 1 and its post_frame lets that frame finish normally; it gates the next pre_frame). */
typedef uint64_t (*np_clock_fn)(void); /* monotonic ms; NULL = CLOCK_MONOTONIC */
typedef size_t (*np_size_fn)(void);
typedef bool (*np_ser_fn)(void*, size_t);
typedef bool (*np_unser_fn)(const void*, size_t);

/* Stick axes per player and frame: LX, LY, RX, RY, raw int16 in libretro's range (protocol 3). */
#define NP_ANALOG_AXES 4

/* 0 ok; spawns the engine thread. peer_ip is the other device's dotted IPv4 for both roles. Host: listens on port
 * and accepts only a connection from peer_ip (any other is closed; it stays CONNECTING and keeps listening). Client:
 * connects to peer_ip:port. Fails (-1) when already started, on a missing or bad address (either role), or when the
 * host cannot bind the port. */
int np_start(np_role role, const char* peer_ip, uint16_t port, np_clock_fn clock);
/* Frames each side runs on its own before the state sync, for a core that takes no state until it ran; set before
 * np_start (0 = none). */
void np_set_presync_frames(int frames);
/* Local pause (menu, background): this device stops sending input and running frames. Nothing goes on the wire: nx-redux
 * main free-runs when it opens and closes its own menu while its peer is paused, so the peer just stalls and counts down.
 * A local pause counts down 30 s itself, then ends the session. */
void np_pause(void);
/* Back from a local pause: input goes out again (no RESUME on the wire); frames run once the peer is not paused. */
void np_resume(void);
void np_leave(void); /* send DISCONNECT, end */
/* Both pointers may be NULL. seconds_left is 0 when no countdown runs. */
np_state np_get_state(int* seconds_left, np_reason* reason);
bool np_active(void); /* started and not yet ENDED/FAILED */
/* GL thread, once per video frame: 1 = run this frame, 0 = skip (connecting, syncing, stalled, paused, over).
 * Never blocks on the network. During SYNCING it calls size/ser (host) or size/unser (client). local_analog is this
 * device's NP_ANALOG_AXES sticks (NULL = centred); they go out with local in one 10-byte INPUT. */
int np_pre_frame(uint16_t local, const int16_t* local_analog, np_size_fn size, np_ser_fn ser, np_unser_fn unser);
void np_post_frame(void);		  /* after a frame that np_pre_frame allowed */
uint16_t np_input(unsigned port); /* port 0 -> P1 word, 1 -> P2 word, >= 2 -> 0, for the current run frame */
/* The synced stick of the current run frame: port 0 -> P1, 1 -> P2; index 0 left, 1 right; id 0 x, 1 y; 0 otherwise. */
int16_t np_analog(unsigned port, unsigned index, unsigned id);
void np_shutdown(void);			  /* join the thread, close sockets, reset to IDLE (safe to call twice) */

/* Pair mode (two emulated consoles on each device, e.g. two linked GBAs): both sides hash the core every 120 frames
 * and compare; on a mismatch the host's state replaces the client's at an agreed frame (NP_RESYNCING meanwhile); the
 * third mismatch within 60 s ends the session (NP_REASON_DRIFT). The input delay adapts (or is pinned), and a frame
 * thread that cannot keep the frame rate ends it (NP_REASON_TOO_SLOW). Both peers must be in pair mode: it adds
 * commands to the wire. Off, the wire is exactly the handheld's protocol 3. */
typedef uint64_t (*np_hash_fn)(void); /* called on the frame thread, after a frame */
/* Before np_start; np_shutdown turns it off again. on with a NULL hash is off. */
void np_set_pair_mode(bool on, np_hash_fn hash);
/* 0 = adaptive; 1..8 = pinned input delay in frames (Developer setting). Any time; kept across sessions. */
void np_set_delay_pin(int frames);
/* This device's input delay in frames (always 2 outside pair mode). */
int np_current_delay(void);
/* The last frame both sides' hashes matched (-1 none) — the save checkpoint. Still readable once the session ended,
 * until np_shutdown. */
int64_t np_last_verified_frame(void);

#ifdef __cplusplus
}
#endif

#endif /* NP_ENGINE_H */
