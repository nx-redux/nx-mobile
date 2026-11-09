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
 * Host tests for the shared TCP plumbing (np_tcp.c) on loopback: the send queue, listen/connect/send/flush/close, the
 * accept filter turning away a wrong peer, a poke waking a thread blocked in accept or connect, and start/shutdown.
 * The engine and link tests cover the same code again through their sessions.
 */

#define _POSIX_C_SOURCE 200809L
#if defined(__APPLE__)
#define _DARWIN_C_SOURCE
#endif

#include "np_net.h"
#include "np_tcp.h"

#include <arpa/inet.h>
#include <errno.h>
#include <netinet/in.h>
#include <poll.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>

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

static void nap_ms(int ms) {
	struct timespec ts = {ms / 1000, (long)(ms % 1000) * 1000000L};
	nanosleep(&ts, NULL);
}

static struct in_addr addr_of(const char* ip) {
	struct in_addr a;
	inet_pton(AF_INET, ip, &a);
	return a;
}

/* ---------------------------------------------------------------- quit flag and a thread in accept */

static _Atomic bool quit_flag;
static bool quit_fn(void) { return atomic_load(&quit_flag); }

typedef struct {
	int listen_fd;
	int wake_fd;
	struct in_addr peer;
	_Atomic int result; /* -100 while running */
} accept_job;

static void* accept_thread(void* arg) {
	accept_job* j = (accept_job*)arg;
	atomic_store(&j->result, np_tcp_accept_from(j->listen_fd, j->wake_fd, j->peer, quit_fn, "test: "));
	return NULL;
}

/* Reads into buf until want bytes or EOF/error; waits up to timeout_ms. Bytes read. */
static size_t read_all(int fd, uint8_t* buf, size_t want, int timeout_ms) {
	size_t have = 0;
	uint64_t t0 = np_net_now_ms();
	while (have < want && np_net_now_ms() - t0 < (uint64_t)timeout_ms) {
		struct pollfd p = {fd, POLLIN, 0};
		if (poll(&p, 1, 20) <= 0)
			continue;
		ssize_t n = recv(fd, buf + have, want - have, 0);
		if (n <= 0 && !(n < 0 && (errno == EAGAIN || errno == EWOULDBLOCK || errno == EINTR)))
			break;
		if (n > 0)
			have += (size_t)n;
	}
	return have;
}

/* ---------------------------------------------------------------- cases */

static int case_outq_grows_compacts_and_caps(uint16_t port) {
	(void)port;
	np_outq q = {0};
	uint8_t a[3000], b[3000];
	memset(a, 'a', sizeof(a));
	memset(b, 'b', sizeof(b));
	CHECK(np_outq_push(&q, a, sizeof(a), 0));
	CHECK(q.cap == 4096 && q.len == 3000 && np_outq_pending(&q));
	CHECK(np_outq_push(&q, b, sizeof(b), 0)); /* doubles */
	CHECK(q.cap == 8192 && q.len == 6000);
	q.off = 5000; /* pretend 5000 went out */
	CHECK(np_outq_push(&q, a, 3000, 0)); /* fits after compaction: no growth */
	CHECK(q.cap == 8192 && q.off == 0 && q.len == 4000);
	CHECK(q.buf[0] == 'b' && q.buf[999] == 'b' && q.buf[1000] == 'a');
	/* the cap counts pending bytes only */
	CHECK(!np_outq_push(&q, a, 1001, 5000));
	CHECK(q.len == 4000);
	CHECK(np_outq_push(&q, a, 1000, 5000));
	/* fully sent: the next push starts from the front */
	q.off = q.len;
	CHECK(!np_outq_pending(&q));
	CHECK(np_outq_push(&q, b, 10, 0) && q.off == 0 && q.len == 10);
	np_outq_clear(&q);
	CHECK(!np_outq_pending(&q) && q.buf != NULL);
	np_outq_free(&q);
	CHECK(q.buf == NULL && q.cap == 0 && q.len == 0 && q.off == 0);
	return 0;
}

static int case_parse_peer(uint16_t port) {
	(void)port;
	struct in_addr a;
	CHECK(np_tcp_parse_peer("192.168.1.7", 16, &a) && a.s_addr == addr_of("192.168.1.7").s_addr);
	CHECK(!np_tcp_parse_peer(NULL, 16, &a));
	CHECK(!np_tcp_parse_peer("not.an.ip", 64, &a));
	CHECK(!np_tcp_parse_peer("::1", 64, &a));
	CHECK(!np_tcp_parse_peer("192.168.100.100", 15, &a)); /* 15 chars + NUL does not fit in 15 */
	return 0;
}

static int case_wake_pipe(uint16_t port) {
	(void)port;
	np_wake w;
	np_wake_init(&w);
	np_wake_poke(&w); /* closed: nothing happens */
	CHECK(np_wake_open(&w) == 0 && w.rd >= 0 && w.wr >= 0);
	for (int i = 0; i < 100000; i++) /* far past the pipe's buffer: never blocks */
		np_wake_poke(&w);
	struct pollfd p = {w.rd, POLLIN, 0};
	CHECK(poll(&p, 1, 0) == 1);
	np_wake_drain(w.rd);
	CHECK(poll(&p, 1, 0) == 0);
	np_wake_close(&w);
	CHECK(w.rd == -1 && w.wr == -1);
	return 0;
}

static uint8_t big[300000];
static uint8_t got[300000];
static np_outq final_q;

static bool final_flush_cb(int fd) {
	bool wrote;
	np_outq_flush(&final_q, fd, &wrote);
	return np_outq_pending(&final_q);
}

typedef struct {
	int fd;
	uint64_t took;
} final_job;

static void* final_thread(void* arg) {
	final_job* j = (final_job*)arg;
	uint8_t scratch[512];
	uint64_t t0 = np_net_now_ms();
	np_tcp_final_flush(j->fd, final_flush_cb, 500, 20, 2000, scratch, sizeof(scratch));
	j->took = np_net_now_ms() - t0;
	return NULL;
}

static int case_loopback_send_flush_close(uint16_t port) {
	atomic_store(&quit_flag, false);
	np_wake w;
	CHECK(np_wake_open(&w) == 0);
	int lfd = np_net_listen(port);
	CHECK(lfd >= 0);
	accept_job j = {lfd, w.rd, addr_of("127.0.0.1"), -100};
	pthread_t t;
	CHECK(pthread_create(&t, NULL, accept_thread, &j) == 0);
	int cfd = np_tcp_connect_retry("127.0.0.1", port, -1, 3, 1000, 50, quit_fn);
	pthread_join(t, NULL);
	int sfd = atomic_load(&j.result);
	CHECK(cfd >= 0 && sfd >= 0);

	/* more than the socket buffers take at once: FULL until the peer reads */
	for (size_t i = 0; i < sizeof(big); i++)
		big[i] = (uint8_t)(i * 31 + 7);
	np_outq q = {0};
	CHECK(np_outq_push(&q, big, sizeof(big), 0));
	size_t have = 0;
	bool saw_full = false, saw_wrote = false;
	uint64_t t0 = np_net_now_ms();
	while ((np_outq_pending(&q) || have < sizeof(big)) && np_net_now_ms() - t0 < 5000) {
		bool wrote;
		np_outq_result r = np_outq_flush(&q, sfd, &wrote);
		CHECK(r != NP_OUTQ_ERROR);
		saw_full |= r == NP_OUTQ_FULL;
		saw_wrote |= wrote;
		if (r == NP_OUTQ_DRAINED)
			CHECK(!np_outq_pending(&q));
		ssize_t n = recv(cfd, got + have, sizeof(got) - have, 0);
		if (n > 0)
			have += (size_t)n;
		else
			nap_ms(1);
	}
	CHECK(have == sizeof(big) && memcmp(got, big, sizeof(big)) == 0);
	CHECK(saw_full && saw_wrote);
	np_outq_free(&q);

	/* on the way out: the last bytes go, then the half-close; the peer's close ends the linger early */
	CHECK(np_outq_push(&final_q, "bye", 3, 0));
	final_job fj = {sfd, 0};
	CHECK(pthread_create(&t, NULL, final_thread, &fj) == 0);
	uint8_t tail[8];
	CHECK(read_all(cfd, tail, 3, 2000) == 3 && memcmp(tail, "bye", 3) == 0);
	CHECK(read_all(cfd, tail, 1, 2000) == 0); /* EOF: SHUT_WR */
	close(cfd);
	pthread_join(t, NULL);
	CHECK(fj.took < 1500); /* not the whole 2 s linger */
	CHECK(!np_outq_pending(&final_q));
	np_outq_free(&final_q);

	/* the peer gone: a send fails */
	nap_ms(50);
	np_outq q2 = {0};
	np_outq_result r = NP_OUTQ_DRAINED;
	for (int i = 0; i < 50 && r != NP_OUTQ_ERROR; i++) {
		CHECK(np_outq_push(&q2, big, 1000, 0));
		bool wrote;
		r = np_outq_flush(&q2, sfd, &wrote);
		nap_ms(10);
	}
	CHECK(r == NP_OUTQ_ERROR && np_outq_pending(&q2)); /* left as it was for the caller */
	np_outq_free(&q2);
	close(sfd);
	close(lfd);
	np_wake_close(&w);
	return 0;
}

static int raw_connect(uint16_t port) {
	int fd = socket(AF_INET, SOCK_STREAM, 0);
	struct sockaddr_in a;
	memset(&a, 0, sizeof(a));
	a.sin_family = AF_INET;
	a.sin_port = htons(port);
	a.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
	if (connect(fd, (struct sockaddr*)&a, sizeof(a)) != 0) {
		close(fd);
		return -1;
	}
	return fd;
}

static int case_accept_refuses_a_wrong_peer_and_wakes(uint16_t port) {
	atomic_store(&quit_flag, false);
	np_wake w;
	CHECK(np_wake_open(&w) == 0);
	int lfd = np_net_listen(port);
	CHECK(lfd >= 0);
	accept_job j = {lfd, w.rd, addr_of("10.255.255.1"), -100}; /* we connect from 127.0.0.1 */
	pthread_t t;
	CHECK(pthread_create(&t, NULL, accept_thread, &j) == 0);

	int fd = raw_connect(port);
	CHECK(fd >= 0);
	uint8_t b;
	CHECK(read_all(fd, &b, 1, 2000) == 0); /* closed at once */
	close(fd);
	nap_ms(150);
	CHECK(atomic_load(&j.result) == -100); /* still listening */

	/* shutdown: the stop flag and a poke end the wait well before another 100 ms poll slice would matter */
	uint64_t t0 = np_net_now_ms();
	atomic_store(&quit_flag, true);
	np_wake_poke(&w);
	pthread_join(t, NULL);
	CHECK(atomic_load(&j.result) == -1);
	CHECK(np_net_now_ms() - t0 < 1000);
	close(lfd);
	np_wake_close(&w);
	atomic_store(&quit_flag, false);
	return 0;
}

typedef struct {
	uint16_t port;
	int wake_fd;
	int attempts;
	_Atomic int result;
} connect_job;

static void* connect_thread(void* arg) {
	connect_job* j = (connect_job*)arg;
	atomic_store(&j->result, np_tcp_connect_retry("127.0.0.1", j->port, j->wake_fd, j->attempts, 1000, 500, quit_fn));
	return NULL;
}

static int case_connect_gives_up_and_wakes(uint16_t port) {
	atomic_store(&quit_flag, false);
	/* nobody listens: every attempt is refused at once */
	uint64_t t0 = np_net_now_ms();
	CHECK(np_tcp_connect_retry("127.0.0.1", port, -1, 2, 1000, 50, quit_fn) == NP_TCP_CONNECT_GAVE_UP);
	CHECK(np_net_now_ms() - t0 < 1000);

	/* a long retry run: the stop flag and a poke end it in its retry wait */
	np_wake w;
	CHECK(np_wake_open(&w) == 0);
	connect_job j = {port, w.rd, 60, -100};
	pthread_t t;
	CHECK(pthread_create(&t, NULL, connect_thread, &j) == 0);
	nap_ms(200);
	t0 = np_net_now_ms();
	atomic_store(&quit_flag, true);
	np_wake_poke(&w);
	pthread_join(t, NULL);
	CHECK(atomic_load(&j.result) == NP_TCP_CONNECT_QUIT);
	CHECK(np_net_now_ms() - t0 < 1000);
	np_wake_close(&w);
	atomic_store(&quit_flag, false);
	return 0;
}

static void* idle_thread(void* arg) {
	(void)arg;
	return NULL;
}

static int case_start_and_release(uint16_t port) {
	int lfd = -1, sock = -1;
	np_wake w;
	np_wake_init(&w);
	pthread_t t;
	CHECK(np_tcp_start(true, port, &lfd, &w, &t, idle_thread) == 0);
	CHECK(lfd >= 0 && w.rd >= 0 && w.wr >= 0);
	pthread_join(t, NULL);

	/* the port is taken: the second host's listener fails and nothing else is opened */
	int lfd2 = -1;
	np_wake w2;
	np_wake_init(&w2);
	pthread_t t2;
	CHECK(np_tcp_start(true, port, &lfd2, &w2, &t2, idle_thread) == NP_TCP_START_LISTEN);
	CHECK(lfd2 == -1 && w2.rd == -1 && w2.wr == -1);

	/* a client opens no listener */
	int lfd3 = -1;
	np_wake w3;
	np_wake_init(&w3);
	pthread_t t3;
	CHECK(np_tcp_start(false, port, &lfd3, &w3, &t3, idle_thread) == 0);
	CHECK(lfd3 == -1 && w3.rd >= 0);
	pthread_join(t3, NULL);
	np_outq q3 = {0};
	np_tcp_release(&sock, &lfd3, &w3, &q3);

	np_outq q = {0};
	CHECK(np_outq_push(&q, "x", 1, 0));
	sock = raw_connect(port);
	CHECK(sock >= 0);
	np_tcp_release(&sock, &lfd, &w, &q);
	CHECK(sock == -1 && lfd == -1 && w.rd == -1 && w.wr == -1 && q.buf == NULL);
	/* the listener is really closed: the port can be listened on again */
	int again = np_net_listen(port);
	CHECK(again >= 0);
	close(again);
	return 0;
}

static void run_case(const char* name, int (*fn)(uint16_t)) {
	uint16_t port = next_port++;
	report(name, fn(port) == 0);
}

int main(void) {
	setvbuf(stdout, NULL, _IOLBF, 0);
	/* Below the ephemeral range (49152): a listen port there can collide with this test's own outgoing sockets. */
	next_port = (uint16_t)(40000 + (getpid() % 180) * 50);

	run_case("outq_grows_compacts_and_caps", case_outq_grows_compacts_and_caps);
	run_case("parse_peer", case_parse_peer);
	run_case("wake_pipe", case_wake_pipe);
	run_case("loopback_send_flush_close", case_loopback_send_flush_close);
	run_case("accept_refuses_a_wrong_peer_and_wakes", case_accept_refuses_a_wrong_peer_and_wakes);
	run_case("connect_gives_up_and_wakes", case_connect_gives_up_and_wakes);
	run_case("start_and_release", case_start_and_release);

	if (fails) {
		fprintf(stderr, "%d case(s) failed\n", fails);
		return 1;
	}
	return 0;
}
