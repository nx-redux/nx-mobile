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
 *     Ported from NX Redux workspace/all/netplay/netplay.c @ 19d3a5e1 (GPL-3.0).
 */

/*
 * NXRedux Netplay Module
 * Simplified implementation based on RetroArch netplay concepts
 *
 * Key design:
 * - Lockstep synchronization: both devices must have same inputs before advancing
 * - Frame buffer: circular buffer storing input history
 * - Host = Player 1, Client = Player 2 (always)
 * - Both devices run identical emulation with identical inputs
 */

/*
 * Port notes:
 *
 * Wire: byte-for-byte the handheld's engine protocol. Packet = 7-byte header (u8 cmd, u32 frame BE, u16 size BE)
 * + size payload bytes. INPUT carries 10 bytes (protocol 3): u16 buttons, i16 LX, LY, RX, RY, all
 * big-endian; a 2-byte INPUT (an older peer) counts with centred sticks, any other size is dropped. STATE_HDR carries
 * a u32 BE state size and is followed by that many raw bytes with no framing. PAUSE / RESUME / DISCONNECT / READY /
 * STATE_ACK carry frame 0, size 0.
 *
 * Threads. One engine thread owns the socket: it accepts (host) or connects (client), reads with the accumulating
 * reader (np_net.c) and stores what it reads into the ring under `np_mutex`, and it is the only writer of the
 * socket. The frame path (np_pre_frame / np_post_frame / np_input, called on Android's GL thread under the core
 * lock) never touches the socket: it reads the ring under the mutex and appends outgoing packets to an in-memory
 * queue, then pokes the thread through a self-pipe. The thread drains the queue with non-blocking sends (looping
 * until every byte is written, POLLOUT in between), so neither side ever blocks while holding the mutex and the
 * mutex is never held across a blocking call.
 *
 * State sync (host -> client, once). The host's first np_pre_frame after the accept serialises the core (GL
 * thread, outside the mutex) and queues STATE_HDR + raw bytes; the thread waits for STATE_ACK (10 s), queues READY
 * and switches to RUNNING. The client's thread receives STATE_HDR; a size that differs from the client's own
 * size() fails with STATE_MISMATCH (DISCONNECT sent). Once the raw bytes are in, the client's np_pre_frame
 * unserialises them (GL thread), the thread sends STATE_ACK and waits for READY (10 s). Both sides then prefill
 * frames 0 and 1 with neutral input: run_frame 0, self_frame 2.
 *
 * Where it differs from the handheld's engine:
 * - preFrame never waits on a blocking receive (the thread receives);
 * - ring: remote INPUT for frame f re-initialises slot(f) when it still holds an older frame, and pre-frame only
 *   initialises its own slot when slot->frame != self_frame, so an early remote half is never wiped;
 * - framing: the accumulating reader handles split headers/payloads; size > 4096 ends the session (IO);
 * - pause: local_paused and remote_paused are tracked separately and frames never run while either is set, while
 *   the thread keeps reading, so RESUME and DISCONNECT are always seen. A local pause sends PAUSE and stops our
 *   input at the same time, as minarch does: the handheld's double-pause free-run needs a peer that pauses and keeps
 *   sending input, so old and new handhelds stay in step, and a paused peer waits without the
 *   handheld's stall timeout. RESUME goes out before our next INPUT;
 * - countdown: stall, peer pause and local pause run a 30 s wall-clock countdown on the injected clock; input for
 *   the run frame or a RESUME clears it, KEEPALIVE does not; KEEPALIVE goes out every 500 ms while stalled;
 * - np_input(port >= 2) is 0 (the handheld mirrored P2 on every port > 0);
 * - every send writes all bytes with MSG_NOSIGNAL.
 * Dropped: hotspot, Wi-Fi, NXDR re-broadcast, discovery, checkCoreSupport (Kotlin decides). The phone ends the
 * session when the peer leaves; it does not go back to WAITING.
 *
 * Phone-only (no wire change): the host is told its peer's IPv4 (the control channel's client) and accepts only a
 * connection from it, closing any other and listening on; the client retries its connect for ~30 s (60 x 500 ms)
 * and waits up to 45 s for STATE_HDR, since a phone host starts listening early in its game start.
 *
 * The phone starts np_start only after its core has loaded (GameActivity's first frame), so a
 * handheld's 10 s state waits start late enough.
 */

#define _POSIX_C_SOURCE 200809L
#if defined(__APPLE__)
#define _DARWIN_C_SOURCE
#endif

#include "np_engine.h"
#include "np_net.h"
#include "np_tcp.h"

#include <arpa/inet.h>
#include <errno.h>
#include <netinet/in.h>
#include <poll.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

// Constants kept from netplay.h (wire-relevant ones must not change)
#define NETPLAY_DEFAULT_PORT 55435
#define NETPLAY_PROTOCOL_VERSION 3
#define NP_INPUT_SIZE 10		// u16 buttons + i16 LX, LY, RX, RY (protocol 3)
#define NP_INPUT_SIZE_LEGACY 2	// u16 buttons only (protocol 2 peers): sticks centred

// Input ring indexed by frame (power of 2 for efficient wraparound)
#define NETPLAY_FRAME_BUFFER_SIZE 64
#define NETPLAY_FRAME_MASK (NETPLAY_FRAME_BUFFER_SIZE - 1)

// Input latency frames (to hide network jitter)
#define NETPLAY_INPUT_LATENCY_FRAMES 2

// Network commands
enum {
	CMD_INPUT = 0x01,	  // Input data for a frame
	CMD_STATE_HDR = 0x03, // State header (size)
	CMD_STATE_ACK = 0x05, // State received OK
	CMD_DISCONNECT = 0x08,
	CMD_READY = 0x09,	  // Ready to play
	CMD_PAUSE = 0x0A,	  // Player paused (menu opened)
	CMD_RESUME = 0x0B,	  // Player resumed (menu closed)
	CMD_KEEPALIVE = 0x0C, // Keepalive during stall to prevent timeout
};

// Timers (milliseconds)
#define NP_COUNTDOWN_MS 30000	  // stall / pause countdown (v2)
#define NP_KEEPALIVE_MS 500		  // KEEPALIVE while stalled
#define NP_SYNC_TIMEOUT_MS 10000  // each network wait during the state sync
// The client's wait for STATE_HDR: a phone host listens early in its game start and serialises only once its core
// runs (after its save import / disc copy), so the joiner's first wait is longer. Local timer only, wire unchanged.
#define NP_SYNC_HDR_TIMEOUT_MS 45000
#define NP_CONNECT_ATTEMPTS 60	  // client: 60 x 500 ms retries, ~30 s (the handheld's 20 is too short for a phone host)
#define NP_CONNECT_RETRY_MS 500
#define NP_CONNECT_TIMEOUT_MS 5000 // per attempt (handheld SO_SNDTIMEO 5 s)
#define NP_POLL_MS 20			  // engine thread poll slice (re-checks timers)
#define NP_FINAL_FLUSH_MS 500	  // real time spent flushing the queue (DISCONNECT) on the way out
#define NP_LINGER_MS 250		  // real time spent waiting for the peer's FIN before close

// Caps the client's STATE_HDR allocation, made before it may know its own size (a hostile host could ask for anything).
// The largest bundled netplay core's state (pcsx_rearmed) is a few MB.
#define NP_MAX_STATE_SIZE (64u * 1024u * 1024u)

typedef struct {
	uint32_t frame;
	uint16_t p1_input;					  // Host input (always Player 1)
	uint16_t p2_input;					  // Client input (always Player 2)
	int16_t p1_analog[NP_ANALOG_AXES];	  // LX, LY, RX, RY
	int16_t p2_analog[NP_ANALOG_AXES];
	bool have_p1;
	bool have_p2;
} FrameInput;

typedef enum {
	SYNC_NONE = 0,
	SYNC_HOST_SERIALIZE,	  // host: waiting for the GL thread to serialise
	SYNC_HOST_SERIALIZING,	  // host: GL thread is in ser()
	SYNC_HOST_WAIT_ACK,		  // host: state queued, waiting for STATE_ACK
	SYNC_CLIENT_WAIT_HDR,	  // client: waiting for STATE_HDR
	SYNC_CLIENT_RECV_STATE,	  // client: receiving the raw state bytes
	SYNC_CLIENT_UNSERIALIZE,  // client: waiting for the GL thread to unserialise
	SYNC_CLIENT_UNSERIALIZING, // client: GL thread is in unser()
	SYNC_CLIENT_WAIT_READY	  // client: STATE_ACK queued, waiting for READY
} SyncPhase;

static pthread_mutex_t np_mutex = PTHREAD_MUTEX_INITIALIZER;

// Main netplay state (everything guarded by np_mutex)
static struct {
	bool started;
	bool stop;
	bool thread_started;
	pthread_t thread;

	np_role role;
	np_state phase; // IDLE, CONNECTING, SYNCING, RUNNING, ENDED, FAILED (STALLED/PEER_PAUSED are derived)
	np_reason reason;
	np_clock_fn clock;
	char peer_ip[64];
	struct in_addr peer_addr; // client: the host it connects to; host: the one client address it accepts
	uint16_t port;

	// Sockets (sock and listen_fd are used only by the engine thread; closed by it or by np_shutdown)
	int sock;
	int listen_fd;
	np_wake wake;

	// Outgoing queue, drained by the engine thread
	np_outq out;

	// Incoming
	np_reader rd;

	// State sync
	SyncPhase sync;
	bool sync_timer;
	uint64_t sync_t0;
	uint8_t* state_buf; // client: received state
	size_t state_len;
	bool have_local_size;
	size_t local_size; // client: its own size(), recorded by np_pre_frame
	int presync_frames; // frames run on our own before the state sync (mupen64plus-next takes no state until it ran)
	int presync_done;

	// Frame synchronization
	uint32_t self_frame; // Our current frame
	uint32_t run_frame;	 // Frame we're executing
	FrameInput frame_buffer[NETPLAY_FRAME_BUFFER_SIZE];
	bool frame_armed; // np_pre_frame returned 1 and np_post_frame has not run yet
	uint16_t cur_p1;
	uint16_t cur_p2;
	int16_t cur_a1[NP_ANALOG_AXES];
	int16_t cur_a2[NP_ANALOG_AXES];

	// Stall / pause (for menu)
	bool stalled;
	bool local_paused;	// We have paused (menu open)
	bool remote_paused; // Remote player has paused
	bool cd_active;
	uint64_t cd_t0;
	uint64_t last_keepalive;
} np;

static uint8_t rx_scratch[16384]; // engine thread only

//////////////////////////////////////////////////////////////////////////////
// Helpers (call with np_mutex held unless stated otherwise)
//////////////////////////////////////////////////////////////////////////////

static uint64_t now_ms(void) {
	return np.clock ? np.clock() : np_net_now_ms();
}

static void reset_fields(void) {
	memset(&np, 0, sizeof(np));
	np.phase = NP_IDLE;
	np.reason = NP_REASON_NONE;
	np.sock = -1;
	np.listen_fd = -1;
	np_wake_init(&np.wake);
	np_reader_reset(&np.rd);
}

static int16_t get_be16(const uint8_t* b) {
	return (int16_t)(uint16_t)(((uint16_t)b[0] << 8) | b[1]);
}

static void put_be16(uint8_t* b, uint16_t v) {
	b[0] = (uint8_t)(v >> 8);
	b[1] = (uint8_t)v;
}

static void wake_thread(void) {
	np_wake_poke(&np.wake);
}

// No cap: the state transfer queues the whole serialised core at once.
static bool queue_bytes(const void* data, size_t n) {
	return np_outq_push(&np.out, data, n, 0);
}

static bool send_packet(uint8_t cmd, uint32_t frame, const void* data, uint16_t size) {
	if (np.sock < 0)
		return false;
	uint8_t hdr[NP_HDR_SIZE];
	np_encode_header(hdr, cmd, frame, size);
	bool ok = queue_bytes(hdr, sizeof(hdr)) && (size == 0 || queue_bytes(data, size));
	wake_thread();
	return ok;
}

static const char* reason_name(np_reason r) {
	switch (r) {
	case NP_REASON_NONE: return "none";
	case NP_REASON_PEER_LEFT: return "peer left";
	case NP_REASON_TIMEOUT: return "timeout";
	case NP_REASON_STATE_MISMATCH: return "state mismatch";
	case NP_REASON_CONNECT: return "connect failed";
	case NP_REASON_IO: return "i/o";
	}
	return "?";
}

static bool is_live(void) {
	return np.phase == NP_CONNECTING || np.phase == NP_SYNCING || np.phase == NP_RUNNING;
}

// End the session once; the first reason wins.
static void end_session(np_state st, np_reason why, bool send_disconnect) {
	if (!is_live())
		return;
	if (send_disconnect)
		send_packet(CMD_DISCONNECT, 0, NULL, 0);
	np.phase = st;
	np.reason = why;
	np.frame_armed = false;
	np.cd_active = false;
	free(np.state_buf);
	np.state_buf = NULL;
	NP_LOG("session %s: %s", st == NP_FAILED ? "failed" : "ended", reason_name(why));
	wake_thread();
}

static FrameInput* get_frame_slot(uint32_t frame) {
	return &np.frame_buffer[frame & NETPLAY_FRAME_MASK];
}

static void init_frame_slot(uint32_t frame) {
	FrameInput* slot = get_frame_slot(frame);
	memset(slot, 0, sizeof(*slot));
	slot->frame = frame;
}

static void init_frame_buffer(void) {
	for (uint32_t i = 0; i < NETPLAY_FRAME_BUFFER_SIZE; i++)
		init_frame_slot(i);
}

static void update_countdown(uint64_t now) {
	bool want = np.local_paused || np.remote_paused || np.stalled;
	if (!want) {
		np.cd_active = false;
	} else if (!np.cd_active) {
		np.cd_active = true;
		np.cd_t0 = now;
	}
}

static uint64_t elapsed(uint64_t now, uint64_t since) {
	return now > since ? now - since : 0;
}

static void check_timers(uint64_t now) {
	if (np.phase == NP_RUNNING) {
		if (np.cd_active && elapsed(now, np.cd_t0) >= NP_COUNTDOWN_MS) {
			end_session(NP_ENDED, NP_REASON_TIMEOUT, true);
			return;
		}
		if (np.stalled && !np.local_paused && !np.remote_paused &&
			elapsed(now, np.last_keepalive) >= NP_KEEPALIVE_MS) {
			send_packet(CMD_KEEPALIVE, np.self_frame, NULL, 0);
			np.last_keepalive = now;
		}
	} else if (np.phase == NP_SYNCING) {
		uint64_t limit = np.sync == SYNC_CLIENT_WAIT_HDR ? NP_SYNC_HDR_TIMEOUT_MS : NP_SYNC_TIMEOUT_MS;
		if (np.sync_timer && elapsed(now, np.sync_t0) >= limit)
			end_session(NP_FAILED, NP_REASON_TIMEOUT, true);
	}
}

static void sync_wait(SyncPhase phase, uint64_t now) {
	np.sync = phase;
	np.sync_timer = true;
	np.sync_t0 = now;
}

static void enter_sync(uint64_t now) {
	np.phase = NP_SYNCING;
	np.self_frame = 0;
	np.run_frame = 0;
	init_frame_buffer();
	np_reader_reset(&np.rd);
	if (np.role == NP_ROLE_HOST) {
		np.sync = SYNC_HOST_SERIALIZE; // no network timer: the GL thread is next
		np.sync_timer = false;
	} else {
		sync_wait(SYNC_CLIENT_WAIT_HDR, now);
	}
	NP_LOG("connected, syncing state");
}

// Netplay_completeStateSync
static void complete_state_sync(void) {
	init_frame_buffer();

	// Pre-fill latency buffer frames with neutral input
	for (uint32_t i = 0; i < NETPLAY_INPUT_LATENCY_FRAMES; i++) {
		FrameInput* slot = get_frame_slot(i);
		slot->frame = i;
		slot->p1_input = 0;
		slot->p2_input = 0;
		memset(slot->p1_analog, 0, sizeof(slot->p1_analog)); // the latency prefill frames are centred
		memset(slot->p2_analog, 0, sizeof(slot->p2_analog));
		slot->have_p1 = true;
		slot->have_p2 = true;
	}

	np.run_frame = 0;
	np.self_frame = NETPLAY_INPUT_LATENCY_FRAMES;
	np.phase = NP_RUNNING;
	np.sync = SYNC_NONE;
	np.sync_timer = false;
	np.frame_armed = false;
	np.stalled = false;
	np.local_paused = false;
	np.remote_paused = false;
	np.cd_active = false;
	free(np.state_buf);
	np.state_buf = NULL;
	NP_LOG("Netplay active");
}

static bool run_slot_complete(void) {
	FrameInput* slot = get_frame_slot(np.run_frame);
	return slot->frame == np.run_frame && slot->have_p1 && slot->have_p2;
}

//////////////////////////////////////////////////////////////////////////////
// Receive (engine thread, np_mutex held)
//////////////////////////////////////////////////////////////////////////////

static void handle_packet(const np_packet* p, uint64_t now) {
	switch (p->cmd) {
	case CMD_INPUT: {
		// Protocol 3: 10 bytes. A 2-byte INPUT (an older peer) counts with centred sticks; any other size is dropped,
		// as the handheld drains and discards it.
		if (np.phase != NP_RUNNING || (p->size != NP_INPUT_SIZE && p->size != NP_INPUT_SIZE_LEGACY))
			return;
		// A legitimate peer is never behind our run frame and at most 2 ahead of our self frame.
		int32_t ahead = (int32_t)(p->frame - np.run_frame);
		if (ahead < 0 || ahead >= NETPLAY_FRAME_BUFFER_SIZE) {
			NP_LOG("dropping input for frame %u (run %u)", p->frame, np.run_frame);
			return;
		}
		FrameInput* slot = get_frame_slot(p->frame);
		if (slot->frame != p->frame) // ring fix: the slot still holds frame - 64
			init_frame_slot(p->frame);
		uint16_t remote_input = (uint16_t)get_be16(p->payload);
		int16_t axes[NP_ANALOG_AXES] = {0, 0, 0, 0};
		if (p->size == NP_INPUT_SIZE)
			for (int i = 0; i < NP_ANALOG_AXES; i++)
				axes[i] = get_be16(p->payload + 2 + 2 * i);
		if (np.role == NP_ROLE_HOST) {
			slot->p2_input = remote_input;
			memcpy(slot->p2_analog, axes, sizeof(axes));
			slot->have_p2 = true;
		} else {
			slot->p1_input = remote_input;
			memcpy(slot->p1_analog, axes, sizeof(axes));
			slot->have_p1 = true;
		}
		if (p->frame == np.run_frame && run_slot_complete()) {
			np.stalled = false; // input for the run frame clears the stall countdown
			update_countdown(now);
		}
		return;
	}
	case CMD_STATE_HDR: {
		if (np.role != NP_ROLE_CLIENT || np.phase != NP_SYNCING || np.sync != SYNC_CLIENT_WAIT_HDR ||
			p->size != 4) {
			NP_LOG("unexpected STATE_HDR");
			end_session(np.phase == NP_SYNCING ? NP_FAILED : NP_ENDED, NP_REASON_IO, true);
			return;
		}
		uint32_t size = ((uint32_t)p->payload[0] << 24) | ((uint32_t)p->payload[1] << 16) |
						((uint32_t)p->payload[2] << 8) | (uint32_t)p->payload[3];
		if (np.have_local_size && size != np.local_size) {
			NP_LOG("State size mismatch: %u vs %zu", size, np.local_size);
			end_session(NP_FAILED, NP_REASON_STATE_MISMATCH, true);
			return;
		}
		if (size == 0 || size > NP_MAX_STATE_SIZE) {
			NP_LOG("bad state size %u", size);
			end_session(NP_FAILED, NP_REASON_STATE_MISMATCH, true);
			return;
		}
		np.state_buf = (uint8_t*)malloc(size);
		if (!np.state_buf) {
			end_session(NP_FAILED, NP_REASON_IO, true);
			return;
		}
		np.state_len = size;
		np_reader_expect_raw(&np.rd, np.state_buf, size);
		sync_wait(SYNC_CLIENT_RECV_STATE, now);
		return;
	}
	case CMD_STATE_ACK:
		if (np.role == NP_ROLE_HOST && np.phase == NP_SYNCING && np.sync == SYNC_HOST_WAIT_ACK) {
			send_packet(CMD_READY, 0, NULL, 0);
			complete_state_sync();
		}
		return;
	case CMD_READY:
		if (np.role == NP_ROLE_CLIENT && np.phase == NP_SYNCING && np.sync == SYNC_CLIENT_WAIT_READY)
			complete_state_sync();
		return;
	case CMD_DISCONNECT:
		end_session(NP_ENDED, NP_REASON_PEER_LEFT, false);
		return;
	case CMD_PAUSE:
		if (np.phase == NP_RUNNING) {
			np.remote_paused = true;
			update_countdown(now);
			NP_LOG("Remote player paused");
		}
		return;
	case CMD_RESUME:
		if (np.phase == NP_RUNNING) {
			np.remote_paused = false;
			np.cd_active = false; // RESUME clears the countdown (a local pause or stall restarts it)
			update_countdown(now);
		}
		return;
	case CMD_KEEPALIVE:
		// Keepalive: the peer is alive. Deliberately does not clear the countdown (v2).
		return;
	default:
		return;
	}
}

static void process_bytes(const uint8_t* buf, size_t n, uint64_t now) {
	if (np.phase == NP_SYNCING && np.sync_timer)
		np.sync_t0 = now; // progress
	while (n > 0 && (np.phase == NP_SYNCING || np.phase == NP_RUNNING)) {
		size_t used = 0;
		np_packet pkt;
		np_rd_result res = np_reader_feed(&np.rd, buf, n, &used, &pkt);
		buf += used;
		n -= used;
		if (res == NP_RD_PACKET) {
			handle_packet(&pkt, now);
		} else if (res == NP_RD_RAW_DONE) {
			np.sync = SYNC_CLIENT_UNSERIALIZE; // the GL thread is next: no network timer
			np.sync_timer = false;
		} else if (res == NP_RD_OVERSIZE) {
			NP_LOG("oversized packet header, closing");
			end_session(NP_ENDED, NP_REASON_IO, true);
		}
	}
}

static np_reason socket_error_reason(int err) {
	return (err == ECONNRESET || err == EPIPE || err == ENOTCONN) ? NP_REASON_PEER_LEFT : NP_REASON_IO;
}

// Write as much of the queue as the socket takes right now (never blocks).
static void flush_queue(int fd, uint64_t now) {
	bool wrote;
	np_outq_result res = np_outq_flush(&np.out, fd, &wrote);
	if (wrote && np.phase == NP_SYNCING && np.sync_timer)
		np.sync_t0 = now; // progress
	if (res == NP_OUTQ_ERROR) {
		int err = errno;
		np_outq_clear(&np.out);
		end_session(NP_ENDED, socket_error_reason(err), false);
	}
	if (!np_outq_pending(&np.out)) {
		np_outq_clear(&np.out);
		if (np.out.cap > (1u << 20)) // drop the state-transfer sized buffer
			np_outq_free(&np.out);
	}
}

//////////////////////////////////////////////////////////////////////////////
// Engine thread
//////////////////////////////////////////////////////////////////////////////

static bool should_quit(void) {
	return np.stop || !is_live();
}

// np_tcp's loops ask between waits, taking the mutex themselves.
static bool quit_locked(void) {
	pthread_mutex_lock(&np_mutex);
	bool quit = should_quit();
	pthread_mutex_unlock(&np_mutex);
	return quit;
}

static int connect_peer(const char* ip, uint16_t port, int wake_fd) {
	int fd = np_tcp_connect_retry(ip, port, wake_fd, NP_CONNECT_ATTEMPTS, NP_CONNECT_TIMEOUT_MS, NP_CONNECT_RETRY_MS,
								  quit_locked);
	if (fd != NP_TCP_CONNECT_GAVE_UP)
		return fd;
	pthread_mutex_lock(&np_mutex);
	NP_LOG("Connection failed (%s:%u)", ip, port);
	end_session(NP_FAILED, NP_REASON_CONNECT, false);
	pthread_mutex_unlock(&np_mutex);
	return -1;
}

static void run_session(int fd, int wake_fd) {
	for (;;) {
		pthread_mutex_lock(&np_mutex);
		check_timers(now_ms());
		bool quit = should_quit();
		bool want_out = np_outq_pending(&np.out);
		pthread_mutex_unlock(&np_mutex);
		if (quit)
			return;

		struct pollfd p[2] = {{fd, (short)(POLLIN | (want_out ? POLLOUT : 0)), 0}, {wake_fd, POLLIN, 0}};
		int r = poll(p, 2, NP_POLL_MS);
		if (r < 0 && errno != EINTR) {
			pthread_mutex_lock(&np_mutex);
			end_session(NP_ENDED, NP_REASON_IO, false);
			pthread_mutex_unlock(&np_mutex);
			return;
		}
		if (p[1].revents & POLLIN)
			np_wake_drain(wake_fd);

		if (p[0].revents & (POLLIN | POLLHUP | POLLERR)) {
			ssize_t n = recv(fd, rx_scratch, sizeof(rx_scratch), 0);
			int err = errno;
			pthread_mutex_lock(&np_mutex);
			if (n > 0) {
				process_bytes(rx_scratch, (size_t)n, now_ms());
			} else if (n == 0) {
				NP_LOG("Remote disconnected");
				end_session(NP_ENDED, NP_REASON_PEER_LEFT, false);
			} else if (err != EAGAIN && err != EWOULDBLOCK && err != EINTR) {
				end_session(NP_ENDED, socket_error_reason(err), false);
			}
			pthread_mutex_unlock(&np_mutex);
		}

		pthread_mutex_lock(&np_mutex);
		flush_queue(fd, now_ms());
		pthread_mutex_unlock(&np_mutex);
	}
}

// close_session's flush step (engine thread, takes the mutex).
static bool flush_pending(int fd) {
	pthread_mutex_lock(&np_mutex);
	flush_queue(fd, now_ms());
	bool pending = np_outq_pending(&np.out);
	pthread_mutex_unlock(&np_mutex);
	return pending;
}

// Send what is still queued (DISCONNECT), half-close, give the peer a moment to close too, then close.
static void close_session(int fd) {
	np_tcp_final_flush(fd, flush_pending, NP_FINAL_FLUSH_MS, NP_POLL_MS, NP_LINGER_MS, rx_scratch, sizeof(rx_scratch));
	pthread_mutex_lock(&np_mutex);
	np.sock = -1;
	np_outq_clear(&np.out);
	pthread_mutex_unlock(&np_mutex);
	close(fd);
}

static void* engine_thread(void* arg) {
	(void)arg;
	// role, peer, port and the fds are fixed while the thread runs
	pthread_mutex_lock(&np_mutex);
	np_role role = np.role;
	char ip[sizeof(np.peer_ip)];
	memcpy(ip, np.peer_ip, sizeof(ip));
	struct in_addr peer = np.peer_addr;
	uint16_t port = np.port;
	int listen_fd = np.listen_fd;
	int wake_fd = np.wake.rd;
	pthread_mutex_unlock(&np_mutex);

	// Host: only the client the control channel agreed on; any other address is closed and the host listens on.
	int fd = role == NP_ROLE_HOST ? np_tcp_accept_from(listen_fd, wake_fd, peer, quit_locked, "")
								  : connect_peer(ip, port, wake_fd);

	pthread_mutex_lock(&np_mutex);
	if (np.listen_fd >= 0) { // host: accept only one connection
		close(np.listen_fd);
		np.listen_fd = -1;
	}
	if (fd >= 0) {
		if (should_quit()) {
			close(fd);
			fd = -1;
		} else {
			np.sock = fd;
			enter_sync(now_ms());
		}
	}
	pthread_mutex_unlock(&np_mutex);

	if (fd >= 0) {
		run_session(fd, wake_fd);
		close_session(fd);
	}
	return NULL;
}

//////////////////////////////////////////////////////////////////////////////
// Public API
//////////////////////////////////////////////////////////////////////////////

static int np_presync_wanted;

int np_start(np_role role, const char* peer_ip, uint16_t port, np_clock_fn clock) {
	pthread_mutex_lock(&np_mutex);
	if (np.started) {
		pthread_mutex_unlock(&np_mutex);
		return -1;
	}
	reset_fields();
	np.presync_frames = np_presync_wanted;
	np.presync_done = 0;
	np.role = role;
	np.port = port;
	np.clock = clock;

	// Both roles name the peer (dotted IPv4): the client connects to it, the host accepts only it.
	if (!np_tcp_parse_peer(peer_ip, sizeof(np.peer_ip), &np.peer_addr)) {
		NP_LOG("Invalid IP address");
		reset_fields();
		pthread_mutex_unlock(&np_mutex);
		return -1;
	}
	memcpy(np.peer_ip, peer_ip, strlen(peer_ip) + 1);

	// The thread takes np_mutex first, so it sees the phase set below. A listener that cannot open leaves the fields as
	// they are (still IDLE, not started; the next np_start resets them); np_link resets.
	int rc = np_tcp_start(role == NP_ROLE_HOST, port, &np.listen_fd, &np.wake, &np.thread, engine_thread);
	if (rc != 0) {
		if (rc == NP_TCP_START_SETUP)
			reset_fields();
		pthread_mutex_unlock(&np_mutex);
		return -1;
	}
	np.phase = NP_CONNECTING;
	np.started = true;
	np.thread_started = true;
	NP_LOG(role == NP_ROLE_HOST ? "hosting on port %u" : "joining on port %u", port);
	pthread_mutex_unlock(&np_mutex);
	return 0;
}

void np_pause(void) {
	pthread_mutex_lock(&np_mutex);
	if (np.phase == NP_RUNNING && !np.local_paused) {
		np.local_paused = true;
		// PAUSE, and np_pre_frame stops sending our input: never one without the other (an old handheld free-runs on a
		// PAUSE followed by input). frame_armed is left alone: np_pause may run on another thread between a np_pre_frame
		// that returned 1 and its np_post_frame; that in-flight frame completes with its real words and posts normally
		// (the peer runs it too).
		send_packet(CMD_PAUSE, 0, NULL, 0);
		update_countdown(now_ms());
	}
	pthread_mutex_unlock(&np_mutex);
}

void np_resume(void) {
	pthread_mutex_lock(&np_mutex);
	if (np.phase == NP_RUNNING && np.local_paused) {
		np.local_paused = false;
		// RESUME is queued ahead of the input the next np_pre_frame sends. Frames run once the peer is not paused too.
		send_packet(CMD_RESUME, 0, NULL, 0);
		if (!np.remote_paused) {
			np.stalled = false;
			np.cd_active = false;
		}
		update_countdown(now_ms());
	}
	pthread_mutex_unlock(&np_mutex);
}

void np_leave(void) {
	pthread_mutex_lock(&np_mutex);
	end_session(NP_ENDED, NP_REASON_NONE, true);
	pthread_mutex_unlock(&np_mutex);
}

np_state np_get_state(int* seconds_left, np_reason* reason) {
	pthread_mutex_lock(&np_mutex);
	uint64_t now = now_ms();
	check_timers(now);
	np_state st = np.phase;
	if (st == NP_RUNNING) {
		if (np.remote_paused)
			st = NP_PEER_PAUSED;
		else if (np.stalled && !np.local_paused)
			st = NP_STALLED;
	}
	if (seconds_left) {
		int secs = 0;
		if (np.phase == NP_RUNNING && np.cd_active) {
			uint64_t e = elapsed(now, np.cd_t0);
			secs = e >= NP_COUNTDOWN_MS ? 0 : (int)((NP_COUNTDOWN_MS - e + 999) / 1000);
		}
		*seconds_left = secs;
	}
	if (reason)
		*reason = np.reason;
	pthread_mutex_unlock(&np_mutex);
	return st;
}

bool np_active(void) {
	pthread_mutex_lock(&np_mutex);
	bool a = np.started && is_live();
	pthread_mutex_unlock(&np_mutex);
	return a;
}

// Host: serialise on the GL thread (outside the mutex) and queue STATE_HDR + raw bytes for the engine thread.
static void host_send_state(np_size_fn size, np_ser_fn ser) {
	np.sync = SYNC_HOST_SERIALIZING;
	pthread_mutex_unlock(&np_mutex);

	size_t n = size ? size() : 0;
	uint8_t* buf = NULL;
	bool ok = ser && n > 0 && n <= NP_MAX_STATE_SIZE && (buf = (uint8_t*)malloc(n)) != NULL && ser(buf, n);

	pthread_mutex_lock(&np_mutex);
	if (np.phase == NP_SYNCING && np.sync == SYNC_HOST_SERIALIZING) {
		if (!ok) {
			NP_LOG("serialize failed (size %zu)", n);
			end_session(NP_FAILED, NP_REASON_IO, true);
		} else {
			uint8_t sz[4] = {(uint8_t)(n >> 24), (uint8_t)(n >> 16), (uint8_t)(n >> 8), (uint8_t)n};
			if (send_packet(CMD_STATE_HDR, 0, sz, sizeof(sz)) && queue_bytes(buf, n)) {
				sync_wait(SYNC_HOST_WAIT_ACK, now_ms());
				wake_thread();
			} else {
				end_session(NP_FAILED, NP_REASON_IO, false);
			}
		}
	}
	free(buf);
}

// Client: unserialise the received state on the GL thread (outside the mutex), then queue STATE_ACK.
// Called and returns with np_mutex held; the core callbacks run with it released.
static void client_load_state(np_size_fn size, np_unser_fn unser) {
	pthread_mutex_unlock(&np_mutex);
	size_t n = size ? size() : 0;
	pthread_mutex_lock(&np_mutex);
	if (np.phase != NP_SYNCING)
		return;
	np.have_local_size = true;
	np.local_size = n;
	if (np.sync != SYNC_CLIENT_UNSERIALIZE)
		return;
	if (n != np.state_len || !unser) {
		NP_LOG("State size mismatch: %zu vs %zu", np.state_len, n);
		end_session(NP_FAILED, NP_REASON_STATE_MISMATCH, true);
		return;
	}
	uint8_t* buf = np.state_buf;
	size_t len = np.state_len;
	np.state_buf = NULL;
	np.sync = SYNC_CLIENT_UNSERIALIZING;
	pthread_mutex_unlock(&np_mutex);

	bool ok = unser(buf, len);
	free(buf);

	pthread_mutex_lock(&np_mutex);
	if (np.phase == NP_SYNCING && np.sync == SYNC_CLIENT_UNSERIALIZING) {
		if (ok) {
			send_packet(CMD_STATE_ACK, 0, NULL, 0);
			sync_wait(SYNC_CLIENT_WAIT_READY, now_ms());
		} else {
			NP_LOG("unserialize failed");
			end_session(NP_FAILED, NP_REASON_STATE_MISMATCH, true);
		}
	}
}

int np_pre_frame(uint16_t local, const int16_t* local_analog, np_size_fn size, np_ser_fn ser, np_unser_fn unser) {
	pthread_mutex_lock(&np_mutex);
	np.frame_armed = false;
	if (!np.started) {
		pthread_mutex_unlock(&np_mutex);
		return 0;
	}
	uint64_t now = now_ms();
	check_timers(now);

	if (np.phase == NP_SYNCING) {
		if (np.presync_done < np.presync_frames) {
			np.presync_done++;
			pthread_mutex_unlock(&np_mutex);
			return 1; // run on our own (not armed: no lockstep frame); the host's state replaces it at the sync
		}
		if (np.role == NP_ROLE_HOST) {
			if (np.sync == SYNC_HOST_SERIALIZE)
				host_send_state(size, ser);
		} else {
			client_load_state(size, unser);
		}
		pthread_mutex_unlock(&np_mutex);
		return 0;
	}
	if (np.phase != NP_RUNNING || np.local_paused || np.remote_paused) {
		pthread_mutex_unlock(&np_mutex);
		return 0;
	}

	// Store and send our input for the FUTURE frame (self_frame). Initialise the slot only when it still holds an
	// older frame: an early remote input for self_frame (ring fix) must survive.
	FrameInput* input_slot = get_frame_slot(np.self_frame);
	if (input_slot->frame != np.self_frame)
		init_frame_slot(np.self_frame);
	bool* have = np.role == NP_ROLE_HOST ? &input_slot->have_p1 : &input_slot->have_p2;
	if (!*have) {
		int16_t axes[NP_ANALOG_AXES] = {0, 0, 0, 0};
		if (local_analog)
			memcpy(axes, local_analog, sizeof(axes));
		if (np.role == NP_ROLE_HOST) {
			input_slot->p1_input = local;
			memcpy(input_slot->p1_analog, axes, sizeof(axes));
		} else {
			input_slot->p2_input = local;
			memcpy(input_slot->p2_analog, axes, sizeof(axes));
		}
		*have = true;
		uint8_t payload[NP_INPUT_SIZE];
		put_be16(payload, local);
		for (int i = 0; i < NP_ANALOG_AXES; i++)
			put_be16(payload + 2 + 2 * i, (uint16_t)axes[i]);
		send_packet(CMD_INPUT, np.self_frame, payload, sizeof(payload));
	}

	if (run_slot_complete()) {
		FrameInput* run_slot = get_frame_slot(np.run_frame);
		np.stalled = false;
		update_countdown(now);
		np.cur_p1 = run_slot->p1_input;
		np.cur_p2 = run_slot->p2_input;
		memcpy(np.cur_a1, run_slot->p1_analog, sizeof(np.cur_a1));
		memcpy(np.cur_a2, run_slot->p2_analog, sizeof(np.cur_a2));
		np.frame_armed = true;
		pthread_mutex_unlock(&np_mutex);
		return 1;
	}

	if (!np.stalled) {
		np.stalled = true;
		np.last_keepalive = now;
	}
	update_countdown(now);
	pthread_mutex_unlock(&np_mutex);
	return 0;
}

void np_set_presync_frames(int frames) {
	pthread_mutex_lock(&np_mutex);
	np_presync_wanted = frames > 0 ? frames : 0;
	pthread_mutex_unlock(&np_mutex);
}

void np_post_frame(void) {
	pthread_mutex_lock(&np_mutex);
	if (np.frame_armed && np.phase == NP_RUNNING) {
		np.run_frame++;
		np.self_frame++;
	}
	np.frame_armed = false;
	pthread_mutex_unlock(&np_mutex);
}

uint16_t np_input(unsigned port) {
	pthread_mutex_lock(&np_mutex);
	uint16_t v = 0;
	if (np.frame_armed) {
		if (port == 0)
			v = np.cur_p1;
		else if (port == 1)
			v = np.cur_p2;
	}
	pthread_mutex_unlock(&np_mutex);
	return v;
}

int16_t np_analog(unsigned port, unsigned index, unsigned id) {
	if (port > 1 || index > 1 || id > 1)
		return 0;
	pthread_mutex_lock(&np_mutex);
	int16_t v = 0;
	if (np.frame_armed)
		v = (port == 0 ? np.cur_a1 : np.cur_a2)[index * 2 + id];
	pthread_mutex_unlock(&np_mutex);
	return v;
}

void np_shutdown(void) {
	pthread_mutex_lock(&np_mutex);
	if (!np.started || np.stop) {
		pthread_mutex_unlock(&np_mutex);
		return;
	}
	end_session(NP_ENDED, NP_REASON_NONE, true);
	np.stop = true;
	wake_thread();
	bool join = np.thread_started;
	pthread_t thread = np.thread;
	pthread_mutex_unlock(&np_mutex);

	if (join)
		pthread_join(thread, NULL);

	pthread_mutex_lock(&np_mutex);
	np_tcp_release(&np.sock, &np.listen_fd, &np.wake, &np.out);
	free(np.state_buf);
	reset_fields();
	pthread_mutex_unlock(&np_mutex);
}
