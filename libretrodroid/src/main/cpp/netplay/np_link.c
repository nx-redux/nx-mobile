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
 *     Ported from NX Redux workspace/all/netplay/gbalink.c @ 2f25643b (GPL-3.0). Differences: one network thread reads
 *     the socket and answers heartbeats (the handheld reads on its main thread); every core callback stays with the
 *     caller (linkhooks.cpp, GL thread); the host takes its joiner back only when it leaves within 10 s of READY with no
 *     data sent (the handheld reloading for the host's mode, netplay_boot.c), not after every leave.
 */

#define _POSIX_C_SOURCE 200809L
#if defined(__APPLE__)
#define _DARWIN_C_SOURCE
#endif

#include "np_link.h"
#include "np_net.h"
#include "np_tcp.h"

#include <arpa/inet.h>
#include <errno.h>
#include <netinet/in.h>
#include <poll.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

#define NL_HEARTBEAT_MS 500	  // host: a HEARTBEAT after this long with nothing sent (gbalink.c HEARTBEAT_INTERVAL_MS)
#define NL_RX_TIMEOUT_MS 60000 // no packet for this long ends the session (GBALINK_CONNECTION_TIMEOUT_MS)
#define NL_SEND_STUCK_MS 2000  // the send queue made no progress for this long (send_all's 2 s)
#define NL_READY_WAIT_MS 5000  // either side's READY wait (100 x 50 ms on the handheld)
#define NL_PAUSE_MAX_MS 30000  // a pause, either side (GBALINK_PEER_PAUSE_MAX_MS)
#define NL_CONNECT_ATTEMPTS 60 // client: 60 x 500 ms, as the lockstep engine (the phone starts before its core loads)
#define NL_CONNECT_RETRY_MS 500
#define NL_CONNECT_TIMEOUT_MS 5000
#define NL_POLL_MS 20		  // network thread poll slice (re-checks timers)
#define NL_FINAL_FLUSH_MS 500 // real time spent flushing DISCONNECT on the way out
#define NL_REJOIN_WINDOW_MS 10000 // host: a joiner leaving this soon after READY, before any data, is taken back
#define NL_RFU_SLOTS 32		  // MAX_PENDING_PACKETS
#define NL_LS_SLOTS 16		  // LS_PENDING
#define NL_PER_FRAME 64		  // GBALINK_MAX_PACKETS_PER_FRAME
#define NL_OUT_MAX (256 * 1024)

enum { HS_NONE = 0, HS_HOST_WAIT, HS_CLIENT_WAIT, HS_DONE };

typedef struct {
	uint16_t len;
	uint8_t data[NL_MAX_PAYLOAD];
} nl_pkt;

static pthread_mutex_t nl_mutex = PTHREAD_MUTEX_INITIALIZER;

static struct {
	bool started, stop;
	pthread_t thread;
	nl_role role;
	uint16_t port;
	nl_clock_fn clock;
	char peer_ip[INET_ADDRSTRLEN];
	struct in_addr peer_addr;
	char own_mode[NL_MODE_MAX + 1];	 // host: its READY payload
	char host_mode[NL_MODE_MAX + 1]; // client: the host's, from READY
	int listen_fd, sock;
	np_wake wake;
	nl_state phase;
	nl_reason reason;
	int hs;
	uint64_t hs_since, last_rx, last_tx;
	bool local_paused, peer_paused, ls_seen;
	uint64_t local_paused_at, peer_paused_at;
	uint64_t ready_at; // when this connection's READY completed
	bool rx_data;	   // any SIO_DATA on this connection (host: the joiner is committed)
	bool rejoin;	   // host, network thread: the joiner left early; run_session takes it back (never seen unlocked)
	np_outq out;
	uint64_t out_since; // when the queue last stopped moving (0: moving or empty)
	// reader
	uint8_t hdr[NL_HDR_SIZE];
	size_t hdr_have, payload_have, skip;
	uint8_t cmd;
	uint16_t size;
	uint8_t payload[NL_MAX_PAYLOAD];
	// received, not yet handed to the core
	nl_pkt rfu[NL_RFU_SLOTS];
	int rfu_head, rfu_n;
	uint8_t ls[NL_LS_SLOTS][NL_NXLS_SIZE];
	int ls_head, ls_n;
} nl;

static uint8_t rx_scratch[16384];					   // network thread only
static nl_pkt frame_rfu[NL_PER_FRAME];				   // GL thread: nl_frame, delivered without the lock
static uint8_t frame_ls[NL_LS_SLOTS][NL_NXLS_SIZE];   // GL thread: nl_frame
static uint8_t midframe_ls[NL_LS_SLOTS + 1][NL_NXLS_SIZE]; // GL thread: nl_take_lockstep (+1: the HOLD release)

// Process-wide, outside nl so they outlive reset_fields: the core can still sit inside retro_run, held by the peer's
// real-cable HOLD (sio_lockstep waits up to 30 s), after the session ended or was shut down.
static _Atomic bool release_owed; // one synthetic HOLD release is owed to the core (nl_release_hold, any session end)
static _Atomic bool ls_traffic;	  // real-cable traffic was seen this session (ls_seen); cleared by nl_start

//////////////////////////////////////////////////////////////////////////////
// Helpers (caller holds nl_mutex unless noted)
//////////////////////////////////////////////////////////////////////////////

static uint64_t now_ms(void) {
	return nl.clock ? nl.clock() : np_net_now_ms();
}

static uint64_t elapsed(uint64_t now, uint64_t since) {
	return now >= since ? now - since : 0;
}

static void reset_fields(void) {
	memset(&nl, 0, sizeof(nl));
	nl.listen_fd = nl.sock = -1;
	np_wake_init(&nl.wake);
	nl.phase = NL_IDLE;
}

static void reset_reader(void) {
	nl.hdr_have = nl.payload_have = nl.skip = 0;
}

static bool is_live(void) {
	return nl.started && nl.phase != NL_ENDED && nl.phase != NL_FAILED;
}

static bool is_linked(void) {
	return nl.phase == NL_LINKED || nl.phase == NL_PEER_PAUSED;
}

static void wake_thread(void) {
	np_wake_poke(&nl.wake);
}

static const char* reason_name(nl_reason r) {
	switch (r) {
	case NL_REASON_PEER_LEFT: return "peer left";
	case NL_REASON_TIMEOUT: return "timeout";
	case NL_REASON_CONNECT: return "connect";
	case NL_REASON_IO: return "io";
	default: return "local";
	}
}

// Capped at NL_OUT_MAX pending: a peer that stops reading ends the session (send_packet) instead of growing it.
static bool queue_bytes(const void* data, size_t n) {
	return np_outq_push(&nl.out, data, n, NL_OUT_MAX);
}

static void end_session(nl_state st, nl_reason why, bool send_disconnect) {
	if (!is_live())
		return;
	if (send_disconnect && nl.sock >= 0 && nl.hs == HS_DONE) {
		uint8_t h[NL_HDR_SIZE];
		nl_encode_header(h, NL_CMD_DISCONNECT, 0, 0);
		queue_bytes(h, sizeof(h));
	}
	nl.phase = st;
	nl.reason = why;
	atomic_store(&release_owed, true); // a core held by the peer's HOLD must not wait out its 30 s
	NP_LOG("link: %s (%s)", st == NL_FAILED ? "failed" : "ended", reason_name(why));
	wake_thread();
}

static bool send_packet(uint8_t cmd, const void* data, uint16_t size, uint16_t cid) {
	if (nl.sock < 0)
		return false;
	uint8_t h[NL_HDR_SIZE];
	nl_encode_header(h, cmd, size, cid);
	if (!queue_bytes(h, sizeof(h)) || (size > 0 && !queue_bytes(data, size))) {
		NP_LOG("link: send queue full");
		end_session(NL_ENDED, NL_REASON_IO, false);
		return false;
	}
	wake_thread();
	return true;
}

// Non-blocking: writes what the socket takes now. Both threads call it, under the lock.
static void flush_queue(int fd, uint64_t now) {
	bool wrote;
	np_outq_result res = np_outq_flush(&nl.out, fd, &wrote);
	if (wrote) {
		nl.last_tx = now;
		nl.out_since = 0;
	}
	if (res == NP_OUTQ_FULL) {
		if (nl.out_since == 0)
			nl.out_since = now;
	} else if (res == NP_OUTQ_ERROR) {
		nl.out.off = nl.out.len;
		end_session(NL_ENDED, NL_REASON_IO, false);
	} else {
		nl.out_since = 0;
	}
}

static uint16_t local_id(void) {
	return nl.role == NL_ROLE_HOST ? 0 : 1;
}

static uint16_t remote_id(void) {
	return nl.role == NL_ROLE_HOST ? 1 : 0;
}

// gbalink.c GBALink_holdLink: the peer may be stalled inside its core on the real cable, where PAUSE only lands at its
// next frame; HOLD stops its lockstep timers at once. Sent twice (idempotent; covers a lost copy).
static void send_hold(bool hold) {
	uint8_t m[NL_NXLS_SIZE] = {'N', 'X', 'L', 'S', NL_NXLS_HOLD};
	m[12] = hold ? 1 : 0;
	for (int i = 0; i < 2; i++)
		send_packet(NL_CMD_SIO_DATA, m, sizeof(m), remote_id());
}

static void send_pause(bool paused) {
	uint8_t b = paused ? 1 : 0;
	send_packet(NL_CMD_PAUSE, &b, 1, local_id());
	if (nl.ls_seen)
		send_hold(paused);
}

static void linked(uint64_t now) {
	nl.hs = HS_DONE;
	nl.phase = NL_LINKED;
	nl.last_rx = nl.last_tx = now;
	nl.ready_at = now;
	nl.rx_data = false;
	NP_LOG("link: linked as %s", nl.role == NL_ROLE_HOST ? "host" : "client");
}

// Host: the joiner sent data, or stayed NL_REJOIN_WINDOW_MS past READY. Until then a leave is the handheld reloading for
// the host's mode (netplay_boot.c: DISCONNECT, reload, connect again) and the host waits for it again.
static bool joiner_committed(uint64_t now) {
	return nl.rx_data || elapsed(now, nl.ready_at) >= NL_REJOIN_WINDOW_MS;
}

// Network thread only (it alone polls the listener): a committed host takes nobody else, so it stops listening.
static void close_listener_once_committed(uint64_t now) {
	if (nl.listen_fd >= 0 && nl.hs == HS_DONE && joiner_committed(now)) {
		close(nl.listen_fd);
		nl.listen_fd = -1;
	}
}

static bool may_take_back(uint64_t now) {
	return nl.role == NL_ROLE_HOST && nl.listen_fd >= 0 && nl.hs == HS_DONE && is_live() && !joiner_committed(now);
}

// A pause asked for before the link was up is told now (after READY), and its 30 s count starts now.
static void announce_held_pause(uint64_t now) {
	if (nl.local_paused) {
		nl.local_paused_at = now;
		send_pause(true);
	}
}

//////////////////////////////////////////////////////////////////////////////
// Receiving (network thread, under the lock)
//////////////////////////////////////////////////////////////////////////////

static void handle_packet(uint64_t now) {
	const uint8_t* p = nl.payload;
	uint16_t n = nl.size;
	nl.last_rx = now;
	if (nl.hs == HS_HOST_WAIT) {
		if (nl.cmd == NL_CMD_READY) {
			uint16_t len = nl.own_mode[0] ? (uint16_t)(strlen(nl.own_mode) + 1) : 0;
			linked(now);
			send_packet(NL_CMD_READY, nl.own_mode, len, 0);
			announce_held_pause(now);
		}
		return; // nothing else counts before READY
	}
	if (nl.hs == HS_CLIENT_WAIT) {
		if (nl.cmd == NL_CMD_READY) {
			size_t k = 0;
			while (k < n && k < NL_MODE_MAX && p[k]) {
				nl.host_mode[k] = (char)p[k];
				k++;
			}
			nl.host_mode[k] = '\0';
			linked(now);
			announce_held_pause(now);
		} else if (nl.cmd == NL_CMD_DISCONNECT) {
			NP_LOG("link: the host turned us away");
			end_session(NL_FAILED, NL_REASON_CONNECT, false);
		}
		return;
	}
	switch (nl.cmd) {
	case NL_CMD_SIO_DATA:
		nl.rx_data = true;
		close_listener_once_committed(now);
		if (n == 0)
			break;
		if (nl_is_lockstep(p, n)) {
			if (nl.ls_n < NL_LS_SLOTS) {
				memcpy(nl.ls[(nl.ls_head + nl.ls_n) % NL_LS_SLOTS], p, NL_NXLS_SIZE);
				nl.ls_n++;
			} else {
				NP_LOG("link: real-cable queue full, one message dropped");
			}
		} else if (nl.rfu_n < NL_RFU_SLOTS) {
			nl_pkt* q = &nl.rfu[(nl.rfu_head + nl.rfu_n) % NL_RFU_SLOTS];
			q->len = n;
			memcpy(q->data, p, n);
			nl.rfu_n++;
		} else {
			NP_LOG("link: packet queue full, one packet dropped");
		}
		break;
	case NL_CMD_HEARTBEAT:
		if (nl.role == NL_ROLE_CLIENT) // one echo per host heartbeat keeps the host's receive clock fed
			send_packet(NL_CMD_HEARTBEAT, NULL, 0, 0);
		break;
	case NL_CMD_PAUSE:
		nl.peer_paused = n >= 1 && p[0];
		nl.peer_paused_at = now;
		if (is_linked())
			nl.phase = nl.peer_paused ? NL_PEER_PAUSED : NL_LINKED;
		NP_LOG("link: peer %s", nl.peer_paused ? "paused" : "resumed");
		break;
	case NL_CMD_DISCONNECT:
		if (may_take_back(now))
			nl.rejoin = true;
		else
			end_session(NL_ENDED, NL_REASON_PEER_LEFT, false);
		break;
	default: // PING/PONG and anything newer: ignored, as on the handheld
		break;
	}
}

static void process_bytes(const uint8_t* b, size_t n, uint64_t now) {
	while (n > 0 && is_live() && !nl.rejoin) {
		if (nl.skip > 0) {
			size_t k = n < nl.skip ? n : nl.skip;
			nl.skip -= k;
			b += k;
			n -= k;
			continue;
		}
		if (nl.hdr_have < NL_HDR_SIZE) {
			size_t k = NL_HDR_SIZE - nl.hdr_have;
			if (k > n)
				k = n;
			memcpy(nl.hdr + nl.hdr_have, b, k);
			nl.hdr_have += k;
			b += k;
			n -= k;
			if (nl.hdr_have < NL_HDR_SIZE)
				break;
			nl.cmd = nl.hdr[0];
			nl.size = (uint16_t)((nl.hdr[1] << 8) | nl.hdr[2]);
			nl.payload_have = 0;
			if (nl.size > NL_MAX_PAYLOAD) { // skip just this packet: its size still frames the stream (recv_packet)
				NP_LOG("link: skipped an oversized packet (%u bytes)", nl.size);
				nl.skip = nl.size;
				nl.hdr_have = 0;
				nl.last_rx = now;
				continue;
			}
			if (nl.size == 0) {
				nl.hdr_have = 0;
				handle_packet(now);
			}
			continue;
		}
		size_t k = nl.size - nl.payload_have;
		if (k > n)
			k = n;
		memcpy(nl.payload + nl.payload_have, b, k);
		nl.payload_have += k;
		b += k;
		n -= k;
		if (nl.payload_have == nl.size) {
			nl.hdr_have = 0;
			handle_packet(now);
		}
	}
}

static void check_timers(uint64_t now) {
	if (!is_live())
		return;
	if (nl.hs == HS_CLIENT_WAIT && elapsed(now, nl.hs_since) >= NL_READY_WAIT_MS) {
		NP_LOG("link: the host sent no READY in 5 s");
		end_session(NL_FAILED, NL_REASON_CONNECT, false);
		return;
	}
	if (nl.hs != HS_DONE)
		return;
	close_listener_once_committed(now);
	if (elapsed(now, nl.last_rx) > NL_RX_TIMEOUT_MS) {
		end_session(NL_ENDED, NL_REASON_TIMEOUT, true);
		return;
	}
	if ((nl.peer_paused && elapsed(now, nl.peer_paused_at) >= NL_PAUSE_MAX_MS) ||
		(nl.local_paused && elapsed(now, nl.local_paused_at) >= NL_PAUSE_MAX_MS)) {
		end_session(NL_ENDED, NL_REASON_TIMEOUT, true);
		return;
	}
	if (nl.out_since && elapsed(now, nl.out_since) >= NL_SEND_STUCK_MS) {
		end_session(NL_ENDED, NL_REASON_IO, false);
		return;
	}
	if (nl.role == NL_ROLE_HOST && !np_outq_pending(&nl.out) && elapsed(now, nl.last_tx) >= NL_HEARTBEAT_MS)
		send_packet(NL_CMD_HEARTBEAT, NULL, 0, 0);
}

//////////////////////////////////////////////////////////////////////////////
// Network thread
//////////////////////////////////////////////////////////////////////////////

static bool should_quit(void) {
	return nl.stop || !is_live();
}

// np_tcp's loops ask between waits, taking the mutex themselves.
static bool quit_locked(void) {
	pthread_mutex_lock(&nl_mutex);
	bool quit = should_quit();
	pthread_mutex_unlock(&nl_mutex);
	return quit;
}

static int connect_peer(const char* ip, uint16_t port, int wake_fd) {
	int fd = np_tcp_connect_retry(ip, port, wake_fd, NL_CONNECT_ATTEMPTS, NL_CONNECT_TIMEOUT_MS, NL_CONNECT_RETRY_MS,
								  quit_locked);
	if (fd != NP_TCP_CONNECT_GAVE_UP)
		return fd;
	pthread_mutex_lock(&nl_mutex);
	NP_LOG("link: could not reach %s:%u", ip, port);
	end_session(NL_FAILED, NL_REASON_CONNECT, false);
	pthread_mutex_unlock(&nl_mutex);
	return -1;
}

// Host only: the joiner left before READY or never sent it. The fd is closed; the caller listens again.
static void drop_joiner(int fd) {
	nl.sock = -1;
	nl.hs = HS_NONE;
	np_outq_clear(&nl.out);
	nl.out_since = 0;
	reset_reader();
	close(fd);
}

// Host only: the joiner left within NL_REJOIN_WINDOW_MS of READY with no data (reloading for our mode). Back to waiting
// for it: this connection's state goes, a pause asked for here stays (told again after the next READY).
static void take_back(int fd) {
	NP_LOG("link: the joiner left just after READY; waiting for it again");
	drop_joiner(fd);
	nl.rejoin = false;
	nl.rx_data = false;
	nl.phase = NL_CONNECTING;
	nl.peer_paused = false;
	nl.ls_seen = false;
	nl.rfu_head = nl.rfu_n = 0;
	nl.ls_head = nl.ls_n = 0;
}

// 0: the session is over; 1: the host's joiner is gone before READY, or just after it (take_back): listen again.
static int run_session(int fd, int wake_fd) {
	for (;;) {
		pthread_mutex_lock(&nl_mutex);
		uint64_t now = now_ms();
		check_timers(now);
		if (nl.hs == HS_HOST_WAIT && elapsed(now, nl.hs_since) >= NL_READY_WAIT_MS) {
			NP_LOG("link: the joiner sent no READY in 5 s");
			send_packet(NL_CMD_DISCONNECT, NULL, 0, 0);
			flush_queue(fd, now);
			drop_joiner(fd);
			pthread_mutex_unlock(&nl_mutex);
			return 1;
		}
		bool quit = should_quit();
		bool want_out = np_outq_pending(&nl.out);
		pthread_mutex_unlock(&nl_mutex);
		if (quit)
			return 0;

		struct pollfd p[2] = {{fd, (short)(POLLIN | (want_out ? POLLOUT : 0)), 0}, {wake_fd, POLLIN, 0}};
		int r = poll(p, 2, NL_POLL_MS);
		if (r < 0 && errno != EINTR) {
			pthread_mutex_lock(&nl_mutex);
			end_session(NL_ENDED, NL_REASON_IO, false);
			pthread_mutex_unlock(&nl_mutex);
			return 0;
		}
		if (p[1].revents & POLLIN)
			np_wake_drain(wake_fd);

		if (p[0].revents & (POLLIN | POLLHUP | POLLERR)) {
			ssize_t n = recv(fd, rx_scratch, sizeof(rx_scratch), 0);
			int err = errno;
			pthread_mutex_lock(&nl_mutex);
			if (n > 0) {
				process_bytes(rx_scratch, (size_t)n, now_ms());
			} else if (n == 0 || (err != EAGAIN && err != EWOULDBLOCK && err != EINTR)) {
				if (nl.hs == HS_HOST_WAIT) {
					drop_joiner(fd);
					pthread_mutex_unlock(&nl_mutex);
					return 1;
				}
				if (nl.hs == HS_DONE && may_take_back(now_ms()))
					nl.rejoin = true;
				else if (nl.hs == HS_DONE)
					end_session(NL_ENDED, n == 0 ? NL_REASON_PEER_LEFT : NL_REASON_IO, false);
				else
					end_session(NL_FAILED, NL_REASON_CONNECT, false);
			}
			if (nl.rejoin) { // DISCONNECT, close or error just after READY
				take_back(fd);
				pthread_mutex_unlock(&nl_mutex);
				return 1;
			}
			pthread_mutex_unlock(&nl_mutex);
		}

		pthread_mutex_lock(&nl_mutex);
		flush_queue(fd, now_ms());
		pthread_mutex_unlock(&nl_mutex);
	}
}

// close_session's flush step (network thread, takes the lock).
static bool flush_pending(int fd) {
	pthread_mutex_lock(&nl_mutex);
	flush_queue(fd, now_ms());
	bool pending = np_outq_pending(&nl.out);
	pthread_mutex_unlock(&nl_mutex);
	return pending;
}

// Send what is still queued (DISCONNECT) for a moment, then close. No linger for the peer's FIN (the engine has one).
static void close_session(int fd) {
	np_tcp_final_flush(fd, flush_pending, NL_FINAL_FLUSH_MS, NL_POLL_MS, 0, NULL, 0);
	pthread_mutex_lock(&nl_mutex);
	nl.sock = -1;
	np_outq_clear(&nl.out);
	pthread_mutex_unlock(&nl_mutex);
	close(fd);
}

static void* link_thread(void* arg) {
	(void)arg;
	pthread_mutex_lock(&nl_mutex); // fixed while the thread runs
	nl_role role = nl.role;
	char ip[sizeof(nl.peer_ip)];
	memcpy(ip, nl.peer_ip, sizeof(ip));
	struct in_addr peer = nl.peer_addr;
	uint16_t port = nl.port;
	int wake_fd = nl.wake.rd;
	pthread_mutex_unlock(&nl_mutex);

	for (;;) {
		// Read again each time: once the joiner is committed the listener is closed (close_listener_once_committed).
		pthread_mutex_lock(&nl_mutex);
		int listen_fd = nl.listen_fd;
		pthread_mutex_unlock(&nl_mutex);
		if (role == NL_ROLE_HOST && listen_fd < 0)
			return NULL;
		// Host: only the wizard's peer (gbalink.c allowed_ip); anyone else is closed and the host keeps listening.
		int fd = role == NL_ROLE_HOST ? np_tcp_accept_from(listen_fd, wake_fd, peer, quit_locked, "link: ")
									  : connect_peer(ip, port, wake_fd);
		if (fd < 0)
			return NULL;
		pthread_mutex_lock(&nl_mutex);
		if (should_quit()) {
			pthread_mutex_unlock(&nl_mutex);
			close(fd);
			return NULL;
		}
		nl.sock = fd;
		reset_reader();
		nl.hs = role == NL_ROLE_HOST ? HS_HOST_WAIT : HS_CLIENT_WAIT;
		nl.hs_since = now_ms();
		if (role == NL_ROLE_CLIENT)
			send_packet(NL_CMD_READY, NULL, 0, 1);
		pthread_mutex_unlock(&nl_mutex);

		if (run_session(fd, wake_fd) == 1)
			continue; // host: the joiner never sent READY, or left just after it; listen again
		close_session(fd);
		return NULL;
	}
}

//////////////////////////////////////////////////////////////////////////////
// Public API
//////////////////////////////////////////////////////////////////////////////

void nl_encode_header(uint8_t out[NL_HDR_SIZE], uint8_t cmd, uint16_t size, uint16_t client_id) {
	out[0] = cmd;
	out[1] = (uint8_t)(size >> 8);
	out[2] = (uint8_t)size;
	out[3] = (uint8_t)(client_id >> 8);
	out[4] = (uint8_t)client_id;
}

bool nl_is_lockstep(const void* buf, size_t len) {
	return buf && len == NL_NXLS_SIZE && memcmp(buf, "NXLS", 4) == 0;
}

int nl_start(nl_role role, const char* peer_ip, uint16_t port, const char* mode, nl_clock_fn clock) {
	pthread_mutex_lock(&nl_mutex);
	if (nl.started) {
		pthread_mutex_unlock(&nl_mutex);
		return -1;
	}
	reset_fields();
	atomic_store(&ls_traffic, false);
	atomic_store(&release_owed, false); // a new session: the last one's core is gone
	nl.role = role;
	nl.port = port;
	nl.clock = clock;
	if (!np_tcp_parse_peer(peer_ip, sizeof(nl.peer_ip), &nl.peer_addr) || (mode && strlen(mode) > NL_MODE_MAX)) {
		NP_LOG("link: bad address or mode");
		reset_fields();
		pthread_mutex_unlock(&nl_mutex);
		return -1;
	}
	memcpy(nl.peer_ip, peer_ip, strlen(peer_ip) + 1);
	if (mode)
		memcpy(nl.own_mode, mode, strlen(mode) + 1);
	// The thread takes nl_mutex first, so it sees the phase set below. Any failure resets the fields.
	if (np_tcp_start(role == NL_ROLE_HOST, port, &nl.listen_fd, &nl.wake, &nl.thread, link_thread) != 0) {
		reset_fields();
		pthread_mutex_unlock(&nl_mutex);
		return -1;
	}
	nl.phase = NL_CONNECTING;
	nl.started = true;
	NP_LOG(role == NL_ROLE_HOST ? "link: hosting on port %u" : "link: joining on port %u", port);
	pthread_mutex_unlock(&nl_mutex);
	return 0;
}

int nl_host_mode(char* out, size_t cap) {
	pthread_mutex_lock(&nl_mutex);
	int known = nl.started && nl.role == NL_ROLE_CLIENT && nl.hs == HS_DONE;
	if (known && out && cap > 0) {
		size_t n = strlen(nl.host_mode);
		if (n >= cap)
			n = cap - 1;
		memcpy(out, nl.host_mode, n);
		out[n] = '\0';
	}
	pthread_mutex_unlock(&nl_mutex);
	return known;
}

int nl_client_id(void) {
	pthread_mutex_lock(&nl_mutex);
	int id = nl.started ? (int)local_id() : -1;
	pthread_mutex_unlock(&nl_mutex);
	return id;
}

void nl_pause(void) {
	pthread_mutex_lock(&nl_mutex);
	if (is_live() && !nl.local_paused) {
		nl.local_paused = true;
		nl.local_paused_at = now_ms();
		if (nl.hs == HS_DONE)
			send_pause(true);
	}
	pthread_mutex_unlock(&nl_mutex);
}

void nl_resume(void) {
	pthread_mutex_lock(&nl_mutex);
	if (nl.local_paused) {
		nl.local_paused = false;
		if (is_live() && nl.hs == HS_DONE)
			send_pause(false);
	}
	pthread_mutex_unlock(&nl_mutex);
}

void nl_leave(void) {
	atomic_store(&release_owed, true);
	pthread_mutex_lock(&nl_mutex);
	end_session(NL_ENDED, NL_REASON_NONE, true);
	pthread_mutex_unlock(&nl_mutex);
}

void nl_release_hold(void) {
	atomic_store(&release_owed, true);
}

nl_state nl_get_state(int* seconds_left, nl_reason* reason) {
	pthread_mutex_lock(&nl_mutex);
	nl_state st = nl.started ? nl.phase : NL_IDLE;
	int secs = 0;
	if (st == NL_PEER_PAUSED) {
		uint64_t e = elapsed(now_ms(), nl.peer_paused_at);
		secs = e >= NL_PAUSE_MAX_MS ? 0 : (int)((NL_PAUSE_MAX_MS - e + 999) / 1000);
	}
	if (seconds_left)
		*seconds_left = secs;
	if (reason)
		*reason = nl.reason;
	pthread_mutex_unlock(&nl_mutex);
	return st;
}

bool nl_active(void) {
	pthread_mutex_lock(&nl_mutex);
	bool a = is_live();
	pthread_mutex_unlock(&nl_mutex);
	return a;
}

int nl_frame(nl_deliver_fn deliver, void* ctx) {
	pthread_mutex_lock(&nl_mutex);
	int run = 1;
	int nls = 0, nrfu = 0;
	if (nl.started && is_live()) {
		run = !nl.local_paused && nl.phase != NL_PEER_PAUSED;
		if (run && is_linked()) {
			while (nl.ls_n > 0) {
				memcpy(frame_ls[nls++], nl.ls[nl.ls_head], NL_NXLS_SIZE);
				nl.ls_head = (nl.ls_head + 1) % NL_LS_SLOTS;
				nl.ls_n--;
			}
			while (nl.rfu_n > 0 && nrfu < NL_PER_FRAME) {
				nl_pkt* q = &nl.rfu[nl.rfu_head];
				frame_rfu[nrfu].len = q->len;
				memcpy(frame_rfu[nrfu].data, q->data, q->len);
				nrfu++;
				nl.rfu_head = (nl.rfu_head + 1) % NL_RFU_SLOTS;
				nl.rfu_n--;
			}
			if (nls > 0) {
				nl.ls_seen = true;
				atomic_store(&ls_traffic, true);
			}
		}
	}
	pthread_mutex_unlock(&nl_mutex);
	if (deliver) {
		for (int i = 0; i < nls; i++)
			deliver(frame_ls[i], NL_NXLS_SIZE, ctx);
		for (int i = 0; i < nrfu; i++)
			deliver(frame_rfu[i].data, frame_rfu[i].len, ctx);
	}
	return run;
}

int nl_take_lockstep(nl_deliver_fn deliver, void* ctx) {
	int n = 0;
	if (pthread_mutex_trylock(&nl_mutex) == 0) { // busy (the network thread): no queued ones this poll, never a stall
		if (nl.started && is_live() && is_linked()) {
			while (nl.ls_n > 0) {
				memcpy(midframe_ls[n++], nl.ls[nl.ls_head], NL_NXLS_SIZE);
				nl.ls_head = (nl.ls_head + 1) % NL_LS_SLOTS;
				nl.ls_n--;
			}
			if (n > 0) {
				nl.ls_seen = true;
				atomic_store(&ls_traffic, true);
			}
		}
		pthread_mutex_unlock(&nl_mutex);
	}
	// The owed HOLD release goes after the queued messages, so a queued HOLD 1 cannot hold the core again; never
	// without real-cable traffic (a wireless-adapter or cable-fake core would misread it, gbalink.c:1590).
	if (atomic_exchange(&release_owed, false) && atomic_load(&ls_traffic)) {
		uint8_t* m = midframe_ls[n++];
		memset(m, 0, NL_NXLS_SIZE);
		memcpy(m, "NXLS", 4);
		m[4] = NL_NXLS_HOLD; // word byte 12 = 0: release
	}
	if (deliver)
		for (int i = 0; i < n; i++)
			deliver(midframe_ls[i], NL_NXLS_SIZE, ctx);
	return n;
}

int nl_send(const void* buf, size_t len, uint16_t client_id) {
	if (!buf || len == 0 || len > NL_MAX_PAYLOAD)
		return -1;
	pthread_mutex_lock(&nl_mutex);
	int r = -1;
	if (nl.started && is_live() && is_linked() && send_packet(NL_CMD_SIO_DATA, buf, (uint16_t)len, client_id)) {
		flush_queue(nl.sock, now_ms()); // straight out: a real-cable START waits on its REPLY
		r = 0;
	}
	pthread_mutex_unlock(&nl_mutex);
	return r;
}

void nl_shutdown(void) {
	atomic_store(&release_owed, true);
	pthread_mutex_lock(&nl_mutex);
	if (!nl.started || nl.stop) {
		pthread_mutex_unlock(&nl_mutex);
		return;
	}
	end_session(NL_ENDED, NL_REASON_NONE, true);
	nl.stop = true;
	wake_thread();
	pthread_t thread = nl.thread;
	pthread_mutex_unlock(&nl_mutex);

	pthread_join(thread, NULL);

	pthread_mutex_lock(&nl_mutex);
	np_tcp_release(&nl.sock, &nl.listen_fd, &nl.wake, &nl.out);
	reset_fields();
	pthread_mutex_unlock(&nl_mutex);
}
