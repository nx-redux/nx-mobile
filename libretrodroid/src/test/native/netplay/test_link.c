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
 * Host tests for the GBA link transport (np_link.c). The engine runs in this process (host or client) and the test is
 * the other device: it speaks the wire byte by byte on a plain socket, as the NX Redux handheld does, which pins the
 * exact bytes. Every engine timer runs on the fake clock; real-time waits only bound loopback delivery.
 */

#define _POSIX_C_SOURCE 200809L
#if defined(__APPLE__)
#define _DARWIN_C_SOURCE
#endif

#include "np_link.h"

#include <arpa/inet.h>
#include <errno.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <poll.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>

#ifndef MSG_NOSIGNAL
#define MSG_NOSIGNAL 0
#endif

static _Atomic uint64_t fake_now = 1000000;
static uint64_t fake_clock(void) { return atomic_load(&fake_now); }
static void fake_set(uint64_t v) { atomic_store(&fake_now, v); }
static void fake_add(uint64_t d) { atomic_fetch_add(&fake_now, d); }

static uint64_t real_ms(void) {
	struct timespec ts;
	clock_gettime(CLOCK_MONOTONIC, &ts);
	return (uint64_t)ts.tv_sec * 1000u + (uint64_t)ts.tv_nsec / 1000000u;
}

static void nap_ms(int ms) {
	struct timespec ts = {ms / 1000, (long)(ms % 1000) * 1000000L};
	nanosleep(&ts, NULL);
}

static int fails;
static uint16_t next_port;

static void report(const char* name, int ok) {
	printf("%s %s\n", ok ? "PASS" : "FAIL", name);
	if (!ok)
		fails++;
	fflush(stdout);
}

#define CHECK(cond)                                                                    \
	do {                                                                               \
		if (!(cond)) {                                                                 \
			fprintf(stderr, "  check failed %s:%d: %s\n", __FILE__, __LINE__, #cond); \
			return -1;                                                                 \
		}                                                                              \
	} while (0)

/* ---------------------------------------------------------------- the raw peer */

static int raw_connect(uint16_t port, int timeout_ms) {
	uint64_t t0 = real_ms();
	for (;;) {
		int fd = socket(AF_INET, SOCK_STREAM, 0);
		struct sockaddr_in a;
		memset(&a, 0, sizeof(a));
		a.sin_family = AF_INET;
		a.sin_port = htons(port);
		a.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
		if (connect(fd, (struct sockaddr*)&a, sizeof(a)) == 0) {
			int one = 1;
			setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &one, sizeof(one));
			return fd;
		}
		close(fd);
		if (real_ms() - t0 > (uint64_t)timeout_ms)
			return -1;
		nap_ms(20);
	}
}

static int raw_listen(uint16_t port) {
	int fd = socket(AF_INET, SOCK_STREAM, 0);
	int one = 1;
	setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &one, sizeof(one));
	struct sockaddr_in a;
	memset(&a, 0, sizeof(a));
	a.sin_family = AF_INET;
	a.sin_port = htons(port);
	a.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
	if (bind(fd, (struct sockaddr*)&a, sizeof(a)) != 0 || listen(fd, 1) != 0) {
		close(fd);
		return -1;
	}
	return fd;
}

static int raw_accept(int lfd, int timeout_ms) {
	struct pollfd p = {lfd, POLLIN, 0};
	if (poll(&p, 1, timeout_ms) <= 0)
		return -1;
	int fd = accept(lfd, NULL, NULL);
	if (fd >= 0) {
		int one = 1;
		setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &one, sizeof(one));
	}
	return fd;
}

/* 0 all n bytes, -1 timeout, closed or error. */
static int read_exact(int fd, void* buf, size_t n, int timeout_ms) {
	uint8_t* b = buf;
	uint64_t t0 = real_ms();
	while (n > 0) {
		uint64_t spent = real_ms() - t0;
		if (spent >= (uint64_t)timeout_ms)
			return -1;
		struct pollfd p = {fd, POLLIN, 0};
		if (poll(&p, 1, (int)((uint64_t)timeout_ms - spent)) <= 0)
			return -1;
		ssize_t r = recv(fd, b, n, 0);
		if (r <= 0)
			return -1;
		b += r;
		n -= (size_t)r;
	}
	return 0;
}

static int send_all(int fd, const void* buf, size_t n) {
	return send(fd, buf, n, MSG_NOSIGNAL) == (ssize_t)n ? 0 : -1;
}

static int send_pkt(int fd, uint8_t cmd, const void* payload, uint16_t n, uint16_t cid) {
	uint8_t h[NL_HDR_SIZE];
	nl_encode_header(h, cmd, n, cid);
	if (send_all(fd, h, sizeof(h)) != 0)
		return -1;
	return n ? send_all(fd, payload, n) : 0;
}

typedef struct {
	uint8_t cmd;
	uint16_t size, cid;
	uint8_t data[NL_MAX_PAYLOAD];
} pkt;

static int read_pkt(int fd, pkt* p, int timeout_ms) {
	uint8_t h[NL_HDR_SIZE];
	if (read_exact(fd, h, sizeof(h), timeout_ms) != 0)
		return -1;
	p->cmd = h[0];
	p->size = (uint16_t)((h[1] << 8) | h[2]);
	p->cid = (uint16_t)((h[3] << 8) | h[4]);
	if (p->size > NL_MAX_PAYLOAD)
		return -1;
	return p->size ? read_exact(fd, p->data, p->size, timeout_ms) : 0;
}

/* The next packet that is not a HEARTBEAT (unless one is wanted) must be cmd with exactly this payload. */
static int expect_pkt(int fd, uint8_t cmd, const void* payload, uint16_t n, int timeout_ms) {
	pkt p;
	for (;;) {
		if (read_pkt(fd, &p, timeout_ms) != 0)
			return -1;
		if (p.cmd == NL_CMD_HEARTBEAT && cmd != NL_CMD_HEARTBEAT)
			continue;
		return p.cmd == cmd && p.size == n && (n == 0 || memcmp(p.data, payload, n) == 0) ? 0 : -1;
	}
}

/* 1 when the engine closed the connection (pending bytes are read and dropped). */
static int peer_closed(int fd, int timeout_ms) {
	uint8_t junk[256];
	uint64_t t0 = real_ms();
	while (real_ms() - t0 < (uint64_t)timeout_ms) {
		struct pollfd p = {fd, POLLIN, 0};
		if (poll(&p, 1, 50) > 0) {
			ssize_t r = recv(fd, junk, sizeof(junk), 0);
			if (r == 0 || (r < 0 && errno != EAGAIN && errno != EINTR))
				return 1;
		}
	}
	return 0;
}

static int wait_state(nl_state want, int timeout_ms) {
	uint64_t t0 = real_ms();
	while (real_ms() - t0 < (uint64_t)timeout_ms) {
		if (nl_get_state(NULL, NULL) == want)
			return 0;
		nap_ms(5);
	}
	return -1;
}

static nl_reason reason_now(void) {
	nl_reason r = NL_REASON_NONE;
	nl_get_state(NULL, &r);
	return r;
}

/* ---------------------------------------------------------------- delivered packets */

static uint8_t got[16][NL_MAX_PAYLOAD];
static size_t got_len[16];
static int got_n;

static void collect(const uint8_t* b, size_t n, void* ctx) {
	(void)ctx;
	if (got_n < 16) {
		memcpy(got[got_n], b, n);
		got_len[got_n++] = n;
	}
}

static void got_reset(void) { got_n = 0; }

/* Calls nl_frame until want packets were delivered (or 1 s). */
static int frames_until(int want) {
	uint64_t t0 = real_ms();
	while (got_n < want && real_ms() - t0 < 1000) {
		nl_frame(collect, NULL);
		nap_ms(5);
	}
	return got_n >= want ? 0 : -1;
}

static uint8_t nxls[NL_NXLS_SIZE] = {'N', 'X', 'L', 'S', 1, 0, 0, 0, 7, 0};

/* ---------------------------------------------------------------- helpers: a linked session */

static const uint8_t no_payload[1] = {0};

/* Starts a host for mode and links a raw client to it; the raw fd, or -1. */
static int linked_host(uint16_t port, const char* mode) {
	if (nl_start(NL_ROLE_HOST, "127.0.0.1", port, mode, fake_clock) != 0)
		return -1;
	int fd = raw_connect(port, 2000);
	if (fd < 0 || send_pkt(fd, NL_CMD_READY, no_payload, 0, 1) != 0)
		return -1;
	uint16_t n = (uint16_t)(mode[0] ? strlen(mode) + 1 : 0);
	if (expect_pkt(fd, NL_CMD_READY, mode, n, 2000) != 0 || wait_state(NL_LINKED, 1000) != 0)
		return -1;
	return fd;
}

/* Starts a client against a raw host that answers READY with mode; the raw (accepted) fd, or -1. *lfd gets the listener. */
static int linked_client(uint16_t port, const char* mode, int* lfd) {
	*lfd = raw_listen(port);
	if (*lfd < 0 || nl_start(NL_ROLE_CLIENT, "127.0.0.1", port, "", fake_clock) != 0)
		return -1;
	int fd = raw_accept(*lfd, 3000);
	pkt p;
	if (fd < 0 || read_pkt(fd, &p, 2000) != 0 || p.cmd != NL_CMD_READY || p.size != 0 || p.cid != 1)
		return -1;
	if (send_pkt(fd, NL_CMD_READY, mode, (uint16_t)(mode[0] ? strlen(mode) + 1 : 0), 0) != 0)
		return -1;
	return wait_state(NL_LINKED, 1000) == 0 ? fd : -1;
}

/* ---------------------------------------------------------------- cases */

static int case_header_and_lockstep_detection(uint16_t port) {
	(void)port;
	uint8_t h[NL_HDR_SIZE];
	nl_encode_header(h, NL_CMD_SIO_DATA, 0x0102, 0xFFFF);
	const uint8_t want[NL_HDR_SIZE] = {0x01, 0x01, 0x02, 0xFF, 0xFF};
	CHECK(memcmp(h, want, sizeof(h)) == 0);
	CHECK(nl_is_lockstep(nxls, NL_NXLS_SIZE));
	CHECK(!nl_is_lockstep(nxls, NL_NXLS_SIZE - 1));
	uint8_t other[NL_NXLS_SIZE];
	memcpy(other, nxls, sizeof(other));
	other[3] = 'T';
	CHECK(!nl_is_lockstep(other, sizeof(other)));
	return 0;
}

static int case_start_refuses_bad_input(uint16_t port) {
	CHECK(nl_start(NL_ROLE_HOST, "not-an-ip", port, "", fake_clock) == -1);
	CHECK(nl_start(NL_ROLE_HOST, "127.0.0.1", port, "0123456789012345678901234567890123456789", fake_clock) == -1);
	CHECK(nl_start(NL_ROLE_HOST, "127.0.0.1", port, "auto", fake_clock) == 0);
	CHECK(nl_start(NL_ROLE_HOST, "127.0.0.1", port, "auto", fake_clock) == -1); /* already started */
	CHECK(nl_get_state(NULL, NULL) == NL_CONNECTING);
	CHECK(nl_client_id() == 0);
	nl_shutdown();
	nl_shutdown(); /* twice is fine */
	CHECK(nl_get_state(NULL, NULL) == NL_IDLE);
	CHECK(nl_client_id() == -1);
	return 0;
}

static int case_host_ready_exchange(uint16_t port) {
	int fd = linked_host(port, "auto");
	CHECK(fd >= 0);
	char mode[NL_MODE_MAX + 1];
	CHECK(nl_host_mode(mode, sizeof(mode)) == 0); /* a host has no host mode */
	CHECK(nl_active());
	close(fd);
	return 0;
}

static int case_host_without_mode_sends_empty_ready(uint16_t port) {
	int fd = linked_host(port, ""); /* mGBA */
	CHECK(fd >= 0);
	close(fd);
	return 0;
}

static int case_ready_split_byte_by_byte(uint16_t port) {
	CHECK(nl_start(NL_ROLE_HOST, "127.0.0.1", port, "rfu", fake_clock) == 0);
	int fd = raw_connect(port, 2000);
	CHECK(fd >= 0);
	uint8_t h[NL_HDR_SIZE];
	nl_encode_header(h, NL_CMD_READY, 0, 1);
	for (size_t i = 0; i < sizeof(h); i++) {
		CHECK(send_all(fd, &h[i], 1) == 0);
		nap_ms(20);
	}
	CHECK(expect_pkt(fd, NL_CMD_READY, "rfu", 4, 2000) == 0);
	CHECK(wait_state(NL_LINKED, 1000) == 0);
	close(fd);
	return 0;
}

static int case_host_refuses_other_peer(uint16_t port) {
	CHECK(nl_start(NL_ROLE_HOST, "10.255.255.1", port, "auto", fake_clock) == 0);
	int fd = raw_connect(port, 2000); /* from 127.0.0.1: not the wizard's peer */
	CHECK(fd >= 0);
	CHECK(peer_closed(fd, 2000));
	CHECK(nl_get_state(NULL, NULL) == NL_CONNECTING); /* still waiting */
	close(fd);
	return 0;
}

static int case_host_drops_a_joiner_without_ready(uint16_t port) {
	CHECK(nl_start(NL_ROLE_HOST, "127.0.0.1", port, "auto", fake_clock) == 0);
	int quiet = raw_connect(port, 2000);
	CHECK(quiet >= 0);
	/* The host stamps the accept on the fake clock: move it until the 5 s READY wait runs out. */
	int dropped = -1;
	for (int i = 0; i < 15 && dropped != 0; i++) {
		fake_add(5001);
		dropped = expect_pkt(quiet, NL_CMD_DISCONNECT, no_payload, 0, 200);
	}
	CHECK(dropped == 0);
	CHECK(peer_closed(quiet, 2000));
	CHECK(nl_get_state(NULL, NULL) == NL_CONNECTING);
	int fd = raw_connect(port, 2000); /* listening again */
	CHECK(fd >= 0);
	CHECK(send_pkt(fd, NL_CMD_READY, no_payload, 0, 1) == 0);
	CHECK(expect_pkt(fd, NL_CMD_READY, "auto", 5, 2000) == 0);
	CHECK(wait_state(NL_LINKED, 1000) == 0);
	close(quiet);
	close(fd);
	return 0;
}

static int case_client_learns_host_mode(uint16_t port) {
	int lfd;
	int fd = linked_client(port, "lockstep", &lfd);
	CHECK(fd >= 0);
	char mode[NL_MODE_MAX + 1];
	CHECK(nl_host_mode(mode, sizeof(mode)) == 1);
	CHECK(strcmp(mode, "lockstep") == 0);
	CHECK(nl_client_id() == 1);
	close(fd);
	close(lfd);
	return 0;
}

static int case_client_empty_mode(uint16_t port) {
	int lfd;
	int fd = linked_client(port, "", &lfd);
	CHECK(fd >= 0);
	char mode[NL_MODE_MAX + 1] = "x";
	CHECK(nl_host_mode(mode, sizeof(mode)) == 1);
	CHECK(mode[0] == '\0');
	close(fd);
	close(lfd);
	return 0;
}

static int case_client_turned_away_fails(uint16_t port) {
	int lfd = raw_listen(port);
	CHECK(lfd >= 0);
	CHECK(nl_start(NL_ROLE_CLIENT, "127.0.0.1", port, "", fake_clock) == 0);
	int fd = raw_accept(lfd, 3000);
	CHECK(fd >= 0);
	pkt p;
	CHECK(read_pkt(fd, &p, 2000) == 0 && p.cmd == NL_CMD_READY);
	CHECK(send_pkt(fd, NL_CMD_DISCONNECT, no_payload, 0, 0) == 0);
	CHECK(wait_state(NL_FAILED, 1000) == 0);
	CHECK(reason_now() == NL_REASON_CONNECT);
	close(fd);
	close(lfd);
	return 0;
}

static int case_client_without_host_ready_fails(uint16_t port) {
	int lfd = raw_listen(port);
	CHECK(lfd >= 0);
	CHECK(nl_start(NL_ROLE_CLIENT, "127.0.0.1", port, "", fake_clock) == 0);
	int fd = raw_accept(lfd, 3000);
	CHECK(fd >= 0);
	pkt p;
	CHECK(read_pkt(fd, &p, 2000) == 0 && p.cmd == NL_CMD_READY); /* sent after the wait was stamped */
	fake_add(5001);
	CHECK(wait_state(NL_FAILED, 1000) == 0);
	CHECK(reason_now() == NL_REASON_CONNECT);
	close(fd);
	close(lfd);
	return 0;
}

static int case_host_heartbeat_and_client_echo(uint16_t port) {
	int fd = linked_host(port, "auto");
	CHECK(fd >= 0);
	fake_add(500);
	CHECK(expect_pkt(fd, NL_CMD_HEARTBEAT, no_payload, 0, 1000) == 0);
	close(fd);
	nl_shutdown();

	int lfd;
	fd = linked_client(port + 1, "auto", &lfd);
	CHECK(fd >= 0);
	CHECK(send_pkt(fd, NL_CMD_HEARTBEAT, no_payload, 0, 0) == 0);
	CHECK(expect_pkt(fd, NL_CMD_HEARTBEAT, no_payload, 0, 1000) == 0);
	close(fd);
	close(lfd);
	return 0;
}

static int case_silence_times_out(uint16_t port) {
	int fd = linked_host(port, "auto");
	CHECK(fd >= 0);
	fake_add(60001);
	CHECK(wait_state(NL_ENDED, 1000) == 0);
	CHECK(reason_now() == NL_REASON_TIMEOUT);
	CHECK(!nl_active());
	close(fd);
	return 0;
}

static int case_delivery_order_and_midframe(uint16_t port) {
	int fd = linked_host(port, "auto");
	CHECK(fd >= 0);
	CHECK(send_pkt(fd, NL_CMD_SIO_DATA, "abc", 3, 1) == 0);
	CHECK(send_pkt(fd, NL_CMD_SIO_DATA, nxls, NL_NXLS_SIZE, 1) == 0);
	CHECK(send_pkt(fd, NL_CMD_SIO_DATA, "def", 3, 1) == 0);
	nap_ms(200);
	/* mid-frame: the real-cable message only */
	got_reset();
	uint64_t t0 = real_ms();
	while (got_n == 0 && real_ms() - t0 < 1000) {
		nl_take_lockstep(collect, NULL);
		nap_ms(5);
	}
	CHECK(got_n == 1 && got_len[0] == NL_NXLS_SIZE && memcmp(got[0], nxls, NL_NXLS_SIZE) == 0);
	/* per frame: the rest, in arrival order */
	got_reset();
	CHECK(frames_until(2) == 0);
	CHECK(got_len[0] == 3 && memcmp(got[0], "abc", 3) == 0);
	CHECK(got_len[1] == 3 && memcmp(got[1], "def", 3) == 0);
	/* a frame hands real-cable messages over first */
	CHECK(send_pkt(fd, NL_CMD_SIO_DATA, "ghi", 3, 1) == 0);
	CHECK(send_pkt(fd, NL_CMD_SIO_DATA, nxls, NL_NXLS_SIZE, 1) == 0);
	nap_ms(200);
	got_reset();
	CHECK(nl_frame(collect, NULL) == 1);
	CHECK(got_n == 2 && got_len[0] == NL_NXLS_SIZE && got_len[1] == 3);
	close(fd);
	return 0;
}

static int case_send_goes_out_with_its_client_id(uint16_t port) {
	int fd = linked_host(port, "auto");
	CHECK(fd >= 0);
	CHECK(nl_send("xyz", 3, 0xFFFF) == 0);
	pkt p;
	CHECK(read_pkt(fd, &p, 1000) == 0);
	CHECK(p.cmd == NL_CMD_SIO_DATA && p.size == 3 && p.cid == 0xFFFF && memcmp(p.data, "xyz", 3) == 0);
	CHECK(nl_send("", 0, 1) == -1);
	close(fd);
	return 0;
}

static int case_send_after_end_is_refused(uint16_t port) {
	int fd = linked_host(port, "auto");
	CHECK(fd >= 0);
	fake_add(10001); /* past the host's rejoin window: a leave ends the session */
	CHECK(send_pkt(fd, NL_CMD_DISCONNECT, no_payload, 0, 1) == 0);
	CHECK(wait_state(NL_ENDED, 1000) == 0);
	CHECK(reason_now() == NL_REASON_PEER_LEFT);
	CHECK(nl_send("xyz", 3, 1) == -1);
	got_reset();
	CHECK(nl_frame(collect, NULL) == 1); /* the game plays on alone */
	CHECK(nl_take_lockstep(collect, NULL) == 0);
	close(fd);
	return 0;
}

/* A client's session ends at once on a close (a host's is in host_keeps_the_end_after_data and host_ends_a_late_leave:
 * just after READY a host takes its joiner back). */
static int case_peer_close_ends(uint16_t port) {
	int lfd;
	int fd = linked_client(port, "auto", &lfd);
	CHECK(fd >= 0);
	close(fd);
	CHECK(wait_state(NL_ENDED, 1000) == 0);
	CHECK(reason_now() == NL_REASON_PEER_LEFT);
	close(lfd);
	return 0;
}

static int case_local_pause_and_hold(uint16_t port) {
	int fd = linked_host(port, "auto");
	CHECK(fd >= 0);
	const uint8_t on = 1, off = 0;
	nl_pause();
	nl_pause(); /* idempotent */
	CHECK(expect_pkt(fd, NL_CMD_PAUSE, &on, 1, 1000) == 0);
	CHECK(nl_frame(collect, NULL) == 0);
	nl_resume();
	CHECK(expect_pkt(fd, NL_CMD_PAUSE, &off, 1, 1000) == 0); /* no HOLD: no real-cable traffic yet */
	CHECK(nl_frame(collect, NULL) == 1);
	/* real-cable traffic seen: HOLD twice with the pause */
	CHECK(send_pkt(fd, NL_CMD_SIO_DATA, nxls, NL_NXLS_SIZE, 1) == 0);
	got_reset();
	CHECK(frames_until(1) == 0);
	nl_pause();
	pkt p;
	CHECK(read_pkt(fd, &p, 1000) == 0 && p.cmd == NL_CMD_PAUSE && p.size == 1 && p.data[0] == 1 && p.cid == 0);
	for (int i = 0; i < 2; i++) {
		CHECK(read_pkt(fd, &p, 1000) == 0 && p.cmd == NL_CMD_SIO_DATA && p.size == NL_NXLS_SIZE);
		CHECK(memcmp(p.data, "NXLS", 4) == 0 && p.data[4] == NL_NXLS_HOLD && p.data[12] == 1 && p.cid == 1);
	}
	nl_resume();
	CHECK(read_pkt(fd, &p, 1000) == 0 && p.cmd == NL_CMD_PAUSE && p.data[0] == 0);
	for (int i = 0; i < 2; i++)
		CHECK(read_pkt(fd, &p, 1000) == 0 && p.cmd == NL_CMD_SIO_DATA && p.data[4] == NL_NXLS_HOLD && p.data[12] == 0);
	close(fd);
	return 0;
}

static int case_local_pause_ends_after_30s(uint16_t port) {
	int fd = linked_host(port, "auto");
	CHECK(fd >= 0);
	const uint8_t on = 1;
	nl_pause();
	CHECK(expect_pkt(fd, NL_CMD_PAUSE, &on, 1, 1000) == 0);
	fake_add(30001);
	CHECK(wait_state(NL_ENDED, 1000) == 0);
	CHECK(reason_now() == NL_REASON_TIMEOUT);
	CHECK(expect_pkt(fd, NL_CMD_DISCONNECT, no_payload, 0, 1000) == 0);
	close(fd);
	return 0;
}

static int case_peer_pause_freezes_then_times_out(uint16_t port) {
	int fd = linked_host(port, "auto");
	CHECK(fd >= 0);
	const uint8_t on = 1, off = 0;
	CHECK(send_pkt(fd, NL_CMD_PAUSE, &on, 1, 1) == 0);
	CHECK(wait_state(NL_PEER_PAUSED, 1000) == 0);
	int secs = 0;
	nl_get_state(&secs, NULL);
	CHECK(secs == 30);
	CHECK(nl_frame(collect, NULL) == 0);
	fake_add(10000);
	nl_get_state(&secs, NULL);
	CHECK(secs == 20);
	CHECK(send_pkt(fd, NL_CMD_PAUSE, &off, 1, 1) == 0);
	CHECK(wait_state(NL_LINKED, 1000) == 0);
	CHECK(nl_frame(collect, NULL) == 1);
	CHECK(send_pkt(fd, NL_CMD_PAUSE, &on, 1, 1) == 0);
	CHECK(wait_state(NL_PEER_PAUSED, 1000) == 0);
	fake_add(30001);
	CHECK(wait_state(NL_ENDED, 1000) == 0);
	CHECK(reason_now() == NL_REASON_TIMEOUT);
	close(fd);
	return 0;
}

static int case_leave_sends_disconnect(uint16_t port) {
	int fd = linked_host(port, "auto");
	CHECK(fd >= 0);
	nl_leave();
	CHECK(expect_pkt(fd, NL_CMD_DISCONNECT, no_payload, 0, 1000) == 0);
	CHECK(nl_get_state(NULL, NULL) == NL_ENDED);
	CHECK(reason_now() == NL_REASON_NONE);
	close(fd);
	return 0;
}

static int case_oversize_packet_is_skipped(uint16_t port) {
	int fd = linked_host(port, "auto");
	CHECK(fd >= 0);
	static uint8_t big[3000];
	memset(big, 0xAB, sizeof(big));
	uint8_t h[NL_HDR_SIZE];
	nl_encode_header(h, NL_CMD_SIO_DATA, (uint16_t)sizeof(big), 1);
	CHECK(send_all(fd, h, sizeof(h)) == 0);
	CHECK(send_all(fd, big, sizeof(big)) == 0);
	CHECK(send_pkt(fd, NL_CMD_SIO_DATA, "ok", 2, 1) == 0);
	got_reset();
	CHECK(frames_until(1) == 0);
	CHECK(got_n == 1 && got_len[0] == 2 && memcmp(got[0], "ok", 2) == 0);
	CHECK(nl_get_state(NULL, NULL) == NL_LINKED);
	close(fd);
	return 0;
}

static int case_host_takes_back_a_joiner_that_reloads(uint16_t port) {
	int fd = linked_host(port, "auto");
	CHECK(fd >= 0);
	/* the handheld adopts the host's mode: DISCONNECT, close, reload its game, connect again (netplay_boot.c) */
	CHECK(send_pkt(fd, NL_CMD_DISCONNECT, no_payload, 0, 1) == 0);
	close(fd);
	CHECK(wait_state(NL_CONNECTING, 1000) == 0);
	nap_ms(100);
	CHECK(nl_get_state(NULL, NULL) == NL_CONNECTING); /* not ENDED */
	CHECK(nl_active());
	CHECK(nl_send("xyz", 3, 1) == -1); /* nothing goes out while it waits */
	fd = raw_connect(port, 2000);
	CHECK(fd >= 0);
	CHECK(send_pkt(fd, NL_CMD_READY, no_payload, 0, 1) == 0);
	CHECK(expect_pkt(fd, NL_CMD_READY, "auto", 5, 2000) == 0);
	CHECK(wait_state(NL_LINKED, 1000) == 0);
	/* the second connection works as the first */
	CHECK(send_pkt(fd, NL_CMD_SIO_DATA, "abc", 3, 1) == 0);
	got_reset();
	CHECK(frames_until(1) == 0);
	CHECK(got_len[0] == 3 && memcmp(got[0], "abc", 3) == 0);
	close(fd);
	return 0;
}

static int case_host_keeps_the_end_after_data(uint16_t port) {
	int fd = linked_host(port, "auto");
	CHECK(fd >= 0);
	CHECK(send_pkt(fd, NL_CMD_SIO_DATA, "abc", 3, 1) == 0);
	got_reset();
	CHECK(frames_until(1) == 0); /* the data is in: the session is committed */
	close(fd);
	CHECK(wait_state(NL_ENDED, 1000) == 0);
	CHECK(reason_now() == NL_REASON_PEER_LEFT);
	int again = raw_connect(port, 300); /* nobody listens any more */
	if (again >= 0)
		close(again);
	CHECK(again < 0);
	return 0;
}

static int case_host_ends_a_late_leave(uint16_t port) {
	int fd = linked_host(port, "auto");
	CHECK(fd >= 0);
	fake_add(10001);
	/* read the heartbeat the jump brings: a close with unread bytes is a reset (IO), not a leave */
	CHECK(expect_pkt(fd, NL_CMD_HEARTBEAT, no_payload, 0, 1000) == 0);
	close(fd);
	CHECK(wait_state(NL_ENDED, 1000) == 0);
	CHECK(reason_now() == NL_REASON_PEER_LEFT);
	return 0;
}

/* Takes mid-frame messages until one arrives (or 1 s). */
static int take_until_one(void) {
	got_reset();
	uint64_t t0 = real_ms();
	while (got_n == 0 && real_ms() - t0 < 1000) {
		nl_take_lockstep(collect, NULL);
		nap_ms(5);
	}
	return got_n == 1 ? 0 : -1;
}

static int is_release(int i) {
	return got_len[i] == NL_NXLS_SIZE && memcmp(got[i], "NXLS", 4) == 0 && got[i][4] == NL_NXLS_HOLD && got[i][12] == 0;
}

static int case_release_reaches_a_held_core(uint16_t port) {
	int fd = linked_host(port, "auto");
	CHECK(fd >= 0);
	/* the peer's real-cable HOLD: real-cable traffic is seen */
	uint8_t hold[NL_NXLS_SIZE] = {'N', 'X', 'L', 'S', NL_NXLS_HOLD};
	hold[12] = 1;
	CHECK(send_pkt(fd, NL_CMD_SIO_DATA, hold, NL_NXLS_SIZE, 1) == 0);
	CHECK(take_until_one() == 0);
	CHECK(got[0][4] == NL_NXLS_HOLD && got[0][12] == 1);
	nl_release_hold();
	got_reset();
	CHECK(nl_take_lockstep(collect, NULL) == 1);
	CHECK(got_n == 1 && is_release(0));
	got_reset();
	CHECK(nl_take_lockstep(collect, NULL) == 0); /* once */
	CHECK(got_n == 0);
	/* a HOLD still queued goes first: the release is the last word */
	CHECK(send_pkt(fd, NL_CMD_SIO_DATA, hold, NL_NXLS_SIZE, 1) == 0);
	nap_ms(200);
	nl_release_hold();
	got_reset();
	CHECK(nl_take_lockstep(collect, NULL) == 2);
	CHECK(got[0][4] == NL_NXLS_HOLD && got[0][12] == 1 && is_release(1));
	/* the session over (no longer live): a leave still releases the core, once */
	nl_leave();
	CHECK(nl_get_state(NULL, NULL) == NL_ENDED);
	got_reset();
	CHECK(nl_take_lockstep(collect, NULL) == 1);
	CHECK(got_n == 1 && is_release(0));
	got_reset();
	CHECK(nl_take_lockstep(collect, NULL) == 0);
	/* and a shutdown, after the reset */
	nl_shutdown();
	got_reset();
	CHECK(nl_take_lockstep(collect, NULL) == 1);
	CHECK(got_n == 1 && is_release(0));
	CHECK(nl_take_lockstep(collect, NULL) == 0);
	close(fd);
	return 0;
}

static int case_peer_end_releases_a_held_core(uint16_t port) {
	int fd = linked_host(port, "auto");
	CHECK(fd >= 0);
	CHECK(send_pkt(fd, NL_CMD_SIO_DATA, nxls, NL_NXLS_SIZE, 1) == 0);
	CHECK(take_until_one() == 0);
	close(fd); /* data came: the leave ends the session */
	CHECK(wait_state(NL_ENDED, 1000) == 0);
	got_reset();
	CHECK(nl_take_lockstep(collect, NULL) == 1);
	CHECK(got_n == 1 && is_release(0));
	return 0;
}

static int case_no_release_without_real_cable_traffic(uint16_t port) {
	int fd = linked_host(port, "auto");
	CHECK(fd >= 0);
	CHECK(send_pkt(fd, NL_CMD_SIO_DATA, "abc", 3, 1) == 0); /* wireless-adapter traffic only */
	got_reset();
	CHECK(frames_until(1) == 0);
	nl_release_hold();
	got_reset();
	CHECK(nl_take_lockstep(collect, NULL) == 0);
	CHECK(got_n == 0);
	nl_leave();
	CHECK(nl_take_lockstep(collect, NULL) == 0);
	CHECK(got_n == 0);
	close(fd);
	return 0;
}

typedef int (*case_fn)(uint16_t port);

static void local_case(const char* name, case_fn fn) {
	uint16_t port = next_port;
	next_port += 2; /* the heartbeat case uses port + 1 */
	fake_set(1000000);
	got_reset();
	int rc = fn(port);
	nl_shutdown();
	report(name, rc == 0);
}

int main(void) {
	setvbuf(stdout, NULL, _IOLBF, 0);
	/* Below the ephemeral range (49152): a listen port there can collide with this test's own outgoing sockets. */
	next_port = (uint16_t)(40000 + (getpid() % 180) * 50);

	local_case("header_and_lockstep_detection", case_header_and_lockstep_detection);
	local_case("start_refuses_bad_input", case_start_refuses_bad_input);
	local_case("host_ready_exchange", case_host_ready_exchange);
	local_case("host_without_mode_sends_empty_ready", case_host_without_mode_sends_empty_ready);
	local_case("ready_split_byte_by_byte", case_ready_split_byte_by_byte);
	local_case("host_refuses_other_peer", case_host_refuses_other_peer);
	local_case("host_drops_a_joiner_without_ready", case_host_drops_a_joiner_without_ready);
	local_case("client_learns_host_mode", case_client_learns_host_mode);
	local_case("client_empty_mode", case_client_empty_mode);
	local_case("client_turned_away_fails", case_client_turned_away_fails);
	local_case("client_without_host_ready_fails", case_client_without_host_ready_fails);
	local_case("host_heartbeat_and_client_echo", case_host_heartbeat_and_client_echo);
	local_case("silence_times_out", case_silence_times_out);
	local_case("delivery_order_and_midframe", case_delivery_order_and_midframe);
	local_case("send_goes_out_with_its_client_id", case_send_goes_out_with_its_client_id);
	local_case("send_after_end_is_refused", case_send_after_end_is_refused);
	local_case("peer_close_ends", case_peer_close_ends);
	local_case("local_pause_and_hold", case_local_pause_and_hold);
	local_case("local_pause_ends_after_30s", case_local_pause_ends_after_30s);
	local_case("peer_pause_freezes_then_times_out", case_peer_pause_freezes_then_times_out);
	local_case("leave_sends_disconnect", case_leave_sends_disconnect);
	local_case("oversize_packet_is_skipped", case_oversize_packet_is_skipped);
	local_case("host_takes_back_a_joiner_that_reloads", case_host_takes_back_a_joiner_that_reloads);
	local_case("host_keeps_the_end_after_data", case_host_keeps_the_end_after_data);
	local_case("host_ends_a_late_leave", case_host_ends_a_late_leave);
	local_case("release_reaches_a_held_core", case_release_reaches_a_held_core);
	local_case("peer_end_releases_a_held_core", case_peer_end_releases_a_held_core);
	local_case("no_release_without_real_cable_traffic", case_no_release_without_real_cable_traffic);

	if (fails) {
		printf("%d test(s) failed\n", fails);
		return 1;
	}
	return 0;
}
