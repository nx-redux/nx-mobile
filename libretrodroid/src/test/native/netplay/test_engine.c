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
 * Host tests for the lockstep netplay engine (np_engine.c / np_net.c).
 *
 * Two kinds of peer:
 * - fork pairs: the engine is a singleton, so the parent plays the host and a forked child plays the client, both
 *   over 127.0.0.1. Pipes carry test coordination bytes (never engine traffic).
 * - raw peer: the host engine runs in this process and the test itself speaks the wire protocol on a plain socket,
 *   byte by byte, as the NX Redux handheld would. This pins the exact wire bytes and makes ordering deterministic.
 *
 * Time: every engine timer runs on the injected fake clock, so the 30 s countdown is tested without sleeping.
 * Real-time waits below only bound how long we wait for loopback delivery; they never decide a result on timing.
 */

#define _POSIX_C_SOURCE 200809L
#if defined(__APPLE__)
#define _DARWIN_C_SOURCE
#endif

#include "np_engine.h"
#include "np_net.h"

#include <arpa/inet.h>
#include <errno.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <poll.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

#ifndef MSG_NOSIGNAL
#define MSG_NOSIGNAL 0
#endif

/* ---------------------------------------------------------------- fake clock / real time */

static _Atomic uint64_t fake_now = 1000000;
static uint64_t fake_clock(void) { return atomic_load(&fake_now); }
static void fake_set(uint64_t v) { atomic_store(&fake_now, v); }

static uint64_t real_ms(void) {
	struct timespec ts;
	clock_gettime(CLOCK_MONOTONIC, &ts);
	return (uint64_t)ts.tv_sec * 1000u + (uint64_t)ts.tv_nsec / 1000000u;
}

static void nap_ms(int ms) {
	struct timespec ts = {ms / 1000, (long)(ms % 1000) * 1000000L};
	nanosleep(&ts, NULL);
}

/* ---------------------------------------------------------------- fake core */

#define CORE_SIZE 4096
static uint8_t core_buf[CORE_SIZE];
static size_t core_size_reported = CORE_SIZE;
static uint32_t core_frame;
static int port_bad;	   /* np_input(2/3) was ever non-zero while running */
static int saw_nonzero;	   /* P1 and P2 words were non-zero at least once */
static int saw_analog;     /* P1 and P2 left sticks were non-zero at least once */

static size_t f_size(void) { return core_size_reported; }
static bool f_ser(void* d, size_t n) {
	if (n < CORE_SIZE)
		return false;
	memcpy(d, core_buf, CORE_SIZE);
	return true;
}
static bool f_unser(const void* d, size_t n) {
	if (n != CORE_SIZE)
		return false;
	memcpy(core_buf, d, CORE_SIZE);
	return true;
}

static void core_reset(int randomise) {
	core_frame = 0;
	port_bad = 0;
	saw_nonzero = 0;
	saw_analog = 0;
	core_size_reported = CORE_SIZE;
	uint32_t x = 0x9E3779B9u;
	for (size_t i = 0; i < CORE_SIZE; i++) {
		x ^= x << 13;
		x ^= x >> 17;
		x ^= x << 5;
		core_buf[i] = randomise ? (uint8_t)x : 0;
	}
}

static void core_run(uint16_t p1, uint16_t p2) {
	/* the synced sticks feed the state too, so the checksum proves they crossed the wire */
	uint32_t mix = p1 + 3u * p2;
	for (unsigned port = 0; port < 2; port++)
		for (unsigned i = 0; i < 2; i++)
			for (unsigned id = 0; id < 2; id++)
				mix = mix * 31u + (uint16_t)np_analog(port, i, id);
	if (np_analog(0, 0, 0) && np_analog(1, 0, 0))
		saw_analog = 1;
	if (np_analog(2, 0, 0) || np_analog(0, 2, 0) || np_analog(0, 0, 2))
		port_bad = 1;
	for (size_t i = 0; i < CORE_SIZE; i++)
		core_buf[i] ^= (uint8_t)(mix + core_frame + (uint32_t)i);
	core_frame++;
}

static uint32_t core_checksum(void) {
	uint32_t h = 2166136261u;
	for (size_t i = 0; i < CORE_SIZE; i++) {
		h ^= core_buf[i];
		h *= 16777619u;
	}
	return h ^ core_frame;
}

static uint16_t word_for(uint32_t seed, uint32_t frame) {
	uint32_t x = (frame + 1u) * 2654435761u ^ seed;
	x ^= x >> 15;
	x *= 0x2C1B3C6Du;
	x ^= x >> 12;
	return (uint16_t)x;
}

/* This device's sticks for a frame, derived from its word: non-zero and different per frame, so the state checksum covers them. */
static void axes_for(uint16_t local, int16_t a[4]) {
	a[0] = (int16_t)(uint16_t)(local * 3u);
	a[1] = (int16_t)(uint16_t)~local;
	a[2] = (int16_t)(uint16_t)(local ^ 0x5A5Au);
	a[3] = (int16_t)(uint16_t)(0u - local);
}

/* One GL-thread "step": 1 if a frame ran. */
static int step(uint16_t local) {
	int16_t a[4];
	axes_for(local, a);
	if (!np_pre_frame(local, a, f_size, f_ser, f_unser))
		return 0;
	uint16_t p1 = np_input(0), p2 = np_input(1);
	if (np_input(2) != 0 || np_input(3) != 0 || np_input(7) != 0)
		port_bad = 1;
	if (p1 && p2)
		saw_nonzero = 1;
	core_run(p1, p2);
	np_post_frame();
	return 1;
}

static int is_over(void) {
	np_state s = np_get_state(NULL, NULL);
	return s == NP_ENDED || s == NP_FAILED;
}

/* Run n frames with pseudo-random local words; 0 ok, -1 on timeout or a session end. */
static int run_frames(uint32_t n, uint32_t seed, int timeout_ms, int calls_per_iter) {
	uint64_t t0 = real_ms();
	uint32_t done = 0;
	while (done < n) {
		for (int c = 0; c < calls_per_iter && done < n; c++)
			done += (uint32_t)step(word_for(seed, core_frame));
		if (done >= n)
			break;
		if (is_over() || real_ms() - t0 > (uint64_t)timeout_ms)
			return -1;
		nap_ms(1);
	}
	return 0;
}

/* Poll until get_state reports `want` (or a terminal state). Calls pre_frame too when drive != 0. */
static int wait_state(np_state want, int timeout_ms, int drive) {
	uint64_t t0 = real_ms();
	for (;;) {
		np_state s = np_get_state(NULL, NULL);
		if (s == want)
			return 0;
		if (s == NP_ENDED || s == NP_FAILED)
			return -1;
		if (real_ms() - t0 > (uint64_t)timeout_ms)
			return -1;
		if (drive)
			step(word_for(1, core_frame));
		nap_ms(1);
	}
}

/* ---------------------------------------------------------------- coordination pipes */

static int pipe_put(int fd, const void* b, size_t n) { return write(fd, b, n) == (ssize_t)n ? 0 : -1; }
static int pipe_get(int fd, void* b, size_t n, int timeout_ms) {
	size_t got = 0;
	uint64_t t0 = real_ms();
	while (got < n) {
		struct pollfd p = {fd, POLLIN, 0};
		int left = timeout_ms - (int)(real_ms() - t0);
		if (left <= 0 || poll(&p, 1, left) <= 0)
			return -1;
		ssize_t r = read(fd, (uint8_t*)b + got, n - got);
		if (r <= 0)
			return -1;
		got += (size_t)r;
	}
	return 0;
}
static int put_byte(int fd, char c) { return pipe_put(fd, &c, 1); }
static int expect_byte(int fd, char want, int timeout_ms) {
	char c = 0;
	return (pipe_get(fd, &c, 1, timeout_ms) == 0 && c == want) ? 0 : -1;
}

/* ---------------------------------------------------------------- harness */

static int fails;
static uint16_t next_port;

static void report(const char* name, int ok, const char* why) {
	if (ok)
		printf("PASS %s\n", name);
	else {
		printf("FAIL %s%s%s\n", name, why ? ": " : "", why ? why : "");
		fails++;
	}
	fflush(stdout);
}

#define CHECK(cond)                                                                    \
	do {                                                                               \
		if (!(cond)) {                                                                 \
			fprintf(stderr, "  check failed %s:%d: %s\n", __FILE__, __LINE__, #cond); \
			return -1;                                                                 \
		}                                                                              \
	} while (0)

/* Both sides get (port, out pipe, in pipe). */
typedef int (*side_fn)(uint16_t port, int out, int in);

static int run_pair(const char* name, side_fn host, side_fn client) {
	uint16_t port = next_port++;
	int p2c[2], c2p[2];
	if (pipe(p2c) != 0 || pipe(c2p) != 0) {
		report(name, 0, "pipe");
		return -1;
	}
	fflush(stdout);
	fflush(stderr);
	pid_t pid = fork();
	if (pid < 0) {
		report(name, 0, "fork");
		return -1;
	}
	if (pid == 0) {
		close(p2c[1]);
		close(c2p[0]);
		fake_set(1000000);
		core_reset(0);
		int rc = client(port, c2p[1], p2c[0]);
		np_shutdown();
		_exit(rc == 0 ? 0 : 1);
	}
	close(p2c[0]);
	close(c2p[1]);
	fake_set(1000000);
	core_reset(1);
	int rc = host(port, p2c[1], c2p[0]);
	np_shutdown();
	close(p2c[1]);
	int st = 0;
	waitpid(pid, &st, 0);
	close(c2p[0]);
	int child_ok = WIFEXITED(st) && WEXITSTATUS(st) == 0;
	report(name, rc == 0 && child_ok, rc != 0 ? "host side" : (!child_ok ? "client side" : NULL));
	return 0;
}

static int start_host(uint16_t port, int out) {
	if (np_start(NP_ROLE_HOST, "127.0.0.1", port, fake_clock) != 0)
		return -1;
	return put_byte(out, 'R');
}
static int start_client(uint16_t port, int in) {
	if (expect_byte(in, 'R', 5000) != 0)
		return -1;
	return np_start(NP_ROLE_CLIENT, "127.0.0.1", port, fake_clock);
}

/* ---------------------------------------------------------------- fork-pair cases */

/* Both sides run n frames, then meet: neither pauses or leaves while the other still needs a frame. */
static int host_frames_then_meet(uint32_t n, uint32_t seed, int out, int in) {
	if (run_frames(n, seed, 15000, 1) != 0 || put_byte(out, 'G') != 0)
		return -1;
	return expect_byte(in, 'g', 15000);
}
static int client_frames_then_meet(uint32_t n, uint32_t seed, int out, int in) {
	if (run_frames(n, seed, 15000, 1) != 0 || expect_byte(in, 'G', 15000) != 0)
		return -1;
	return put_byte(out, 'g');
}

static int lockstep_ports_ok;

static int lockstep_host(uint16_t port, int out, int in) {
	CHECK(start_host(port, out) == 0);
	CHECK(run_frames(200, 0x1111, 15000, 1) == 0);
	uint32_t mine = core_checksum(), theirs = 0;
	uint8_t their_ports = 0;
	CHECK(pipe_get(in, &theirs, sizeof(theirs), 15000) == 0);
	CHECK(pipe_get(in, &their_ports, 1, 5000) == 0);
	lockstep_ports_ok = !port_bad && saw_nonzero && saw_analog && their_ports == 1;
	CHECK(put_byte(out, 'D') == 0);
	if (mine != theirs) {
		fprintf(stderr, "  checksum host %08x client %08x\n", mine, theirs);
		return -1;
	}
	return 0;
}
static int lockstep_client(uint16_t port, int out, int in) {
	CHECK(start_client(port, in) == 0);
	CHECK(run_frames(200, 0x2222, 15000, 1) == 0);
	uint32_t sum = core_checksum();
	uint8_t ports = (!port_bad && saw_nonzero && saw_analog) ? 1 : 0;
	CHECK(pipe_put(out, &sum, sizeof(sum)) == 0);
	CHECK(pipe_put(out, &ports, 1) == 0);
	CHECK(expect_byte(in, 'D', 15000) == 0);
	return 0;
}

static int early_host(uint16_t port, int out, int in) {
	CHECK(start_host(port, out) == 0);
	CHECK(run_frames(80, 0x3333, 15000, 1) == 0);
	CHECK(expect_byte(in, 'F', 15000) == 0);
	CHECK(put_byte(out, 'D') == 0);
	return 0;
}
static int early_client(uint16_t port, int out, int in) {
	CHECK(start_client(port, in) == 0);
	CHECK(run_frames(80, 0x4444, 15000, 2) == 0); /* two pre_frame calls per iteration */
	CHECK(put_byte(out, 'F') == 0);
	CHECK(expect_byte(in, 'D', 15000) == 0);
	return 0;
}

static int stall_host(uint16_t port, int out, int in) {
	CHECK(start_host(port, out) == 0);
	/* The client runs 10 frames, so it sends input for frames 2..11: the host can run exactly 12 frames. */
	CHECK(run_frames(12, 0x5555, 15000, 1) == 0);
	CHECK(expect_byte(in, 'S', 15000) == 0);
	CHECK(step(1) == 0);
	CHECK(wait_state(NP_STALLED, 2000, 1) == 0);
	uint64_t t0 = fake_clock();
	int secs = -1;
	np_reason why = NP_REASON_NONE;
	for (int k = 0; k < 30; k++) {
		fake_set(t0 + (uint64_t)k * 1000u);
		CHECK(step(1) == 0);
		CHECK(np_get_state(&secs, &why) == NP_STALLED);
		if (secs != 30 - k) {
			fprintf(stderr, "  at +%d ms seconds_left %d, want %d\n", k * 1000, secs, 30 - k);
			return -1;
		}
	}
	fake_set(t0 + 29999u);
	CHECK(np_get_state(&secs, &why) == NP_STALLED && secs == 1);
	fake_set(t0 + 30000u);
	CHECK(np_get_state(&secs, &why) == NP_ENDED);
	CHECK(why == NP_REASON_TIMEOUT);
	CHECK(!np_active());
	CHECK(step(1) == 0);
	CHECK(put_byte(out, 'D') == 0);
	CHECK(expect_byte(in, 'E', 5000) == 0); /* the client saw our DISCONNECT */
	return 0;
}
static int stall_client(uint16_t port, int out, int in) {
	CHECK(start_client(port, in) == 0);
	CHECK(run_frames(10, 0x6666, 15000, 1) == 0);
	CHECK(put_byte(out, 'S') == 0); /* and never call pre_frame again */
	CHECK(expect_byte(in, 'D', 15000) == 0);
	np_reason why = NP_REASON_NONE;
	uint64_t t0 = real_ms();
	while (np_get_state(NULL, &why) != NP_ENDED && real_ms() - t0 < 3000)
		nap_ms(2);
	CHECK(np_get_state(NULL, &why) == NP_ENDED && why == NP_REASON_PEER_LEFT);
	CHECK(put_byte(out, 'E') == 0);
	return 0;
}

static int pause_host(uint16_t port, int out, int in) {
	CHECK(start_host(port, out) == 0);
	CHECK(host_frames_then_meet(10, 0x7777, out, in) == 0);
	CHECK(expect_byte(in, 'P', 15000) == 0);
	/* the client paused locally: PAUSE arrives and its input stops, so we wait and count down */
	CHECK(wait_state(NP_PEER_PAUSED, 3000, 1) == 0);
	int secs = -1;
	CHECK(np_get_state(&secs, NULL) == NP_PEER_PAUSED && secs == 30);
	CHECK(np_active());
	fake_set(fake_clock() + 5000u);
	CHECK(np_get_state(&secs, NULL) == NP_PEER_PAUSED && secs == 25);
	CHECK(put_byte(out, 'C') == 0);
	CHECK(run_frames(10, 0x7777, 5000, 1) == 0);
	CHECK(np_get_state(&secs, NULL) == NP_RUNNING && secs == 0);
	CHECK(expect_byte(in, 'F', 15000) == 0);
	CHECK(put_byte(out, 'D') == 0);
	return 0;
}
static int pause_client(uint16_t port, int out, int in) {
	CHECK(start_client(port, in) == 0);
	CHECK(client_frames_then_meet(10, 0x8888, out, in) == 0);
	np_pause();
	CHECK(put_byte(out, 'P') == 0);
	for (int i = 0; i < 20; i++) {
		CHECK(step(1) == 0);
		nap_ms(1);
	}
	CHECK(expect_byte(in, 'C', 15000) == 0);
	np_resume();
	CHECK(run_frames(12, 0x8888, 5000, 1) == 0);
	CHECK(put_byte(out, 'F') == 0);
	CHECK(expect_byte(in, 'D', 15000) == 0);
	return 0;
}

static int dpause_host(uint16_t port, int out, int in) {
	CHECK(start_host(port, out) == 0);
	CHECK(host_frames_then_meet(10, 0x9999, out, in) == 0);
	np_pause();
	CHECK(put_byte(out, 'P') == 0);
	CHECK(expect_byte(in, 'Q', 15000) == 0);
	np_resume(); /* the host resumes first: the client is still paused, so no frame runs */
	uint32_t before = core_frame;
	for (int i = 0; i < 100; i++) {
		step(1);
		nap_ms(1);
	}
	CHECK(core_frame - before <= 2);
	CHECK(np_get_state(NULL, NULL) == NP_PEER_PAUSED);
	CHECK(put_byte(out, 'C') == 0);
	CHECK(run_frames(10, 0x9999, 5000, 1) == 0);
	CHECK(expect_byte(in, 'F', 15000) == 0);
	CHECK(put_byte(out, 'D') == 0);
	return 0;
}
static int dpause_client(uint16_t port, int out, int in) {
	CHECK(start_client(port, in) == 0);
	CHECK(client_frames_then_meet(10, 0xAAAA, out, in) == 0);
	CHECK(expect_byte(in, 'P', 15000) == 0);
	np_pause();
	CHECK(put_byte(out, 'Q') == 0);
	CHECK(expect_byte(in, 'C', 15000) == 0);
	np_resume();
	CHECK(run_frames(12, 0xAAAA, 5000, 1) == 0);
	CHECK(put_byte(out, 'F') == 0);
	CHECK(expect_byte(in, 'D', 15000) == 0);
	return 0;
}

/* np_pause from another thread (the UI thread opening the menu) between pre_frame -> 1 and post_frame. */
static void* pause_thread(void* arg) {
	(void)arg;
	np_pause();
	return NULL;
}

static int midframe_host(uint16_t port, int out, int in) {
	CHECK(start_host(port, out) == 0);
	CHECK(host_frames_then_meet(10, 0xDDDD, out, in) == 0);
	uint64_t t0 = real_ms();
	while (!np_pre_frame(word_for(0xDDDD, core_frame), NULL, f_size, f_ser, f_unser)) {
		CHECK(real_ms() - t0 < 5000);
		nap_ms(1);
	}
	uint16_t p1 = np_input(0), p2 = np_input(1);
	pthread_t t;
	CHECK(pthread_create(&t, NULL, pause_thread, NULL) == 0);
	pthread_join(t, NULL);
	/* the in-flight frame keeps its real words and posts normally */
	CHECK(np_input(0) == p1 && np_input(1) == p2);
	core_run(np_input(0), np_input(1));
	np_post_frame();
	/* the pause gates the next frame */
	for (int i = 0; i < 20; i++) {
		CHECK(np_pre_frame(1, NULL, f_size, f_ser, f_unser) == 0);
		nap_ms(1);
	}
	np_resume();
	CHECK(run_frames(20, 0xDDDD, 15000, 1) == 0); /* 31 frames in all */
	uint32_t mine = core_checksum(), theirs = 0;
	CHECK(pipe_get(in, &theirs, sizeof(theirs), 15000) == 0);
	CHECK(put_byte(out, 'D') == 0);
	if (mine != theirs) {
		fprintf(stderr, "  checksum host %08x client %08x (host frames %u)\n", mine, theirs, core_frame);
		return -1;
	}
	return 0;
}
static int midframe_client(uint16_t port, int out, int in) {
	CHECK(start_client(port, in) == 0);
	CHECK(client_frames_then_meet(10, 0xEEEE, out, in) == 0);
	CHECK(run_frames(21, 0xEEEE, 15000, 1) == 0); /* 31 frames in all, across the host's pause */
	uint32_t sum = core_checksum();
	CHECK(pipe_put(out, &sum, sizeof(sum)) == 0);
	CHECK(expect_byte(in, 'D', 15000) == 0);
	return 0;
}

static int mismatch_host(uint16_t port, int out, int in) {
	CHECK(start_host(port, out) == 0);
	np_reason why = NP_REASON_NONE;
	uint64_t t0 = real_ms();
	while (np_get_state(NULL, &why) != NP_ENDED && real_ms() - t0 < 5000) {
		CHECK(step(1) == 0);
		nap_ms(1);
	}
	CHECK(np_get_state(NULL, &why) == NP_ENDED);
	CHECK(why == NP_REASON_PEER_LEFT);
	CHECK(expect_byte(in, 'M', 5000) == 0);
	return 0;
}
static int mismatch_client(uint16_t port, int out, int in) {
	core_size_reported = CORE_SIZE - 1;
	CHECK(start_client(port, in) == 0);
	np_reason why = NP_REASON_NONE;
	uint64_t t0 = real_ms();
	while (np_get_state(NULL, &why) != NP_FAILED && real_ms() - t0 < 5000) {
		CHECK(step(1) == 0);
		nap_ms(1);
	}
	CHECK(np_get_state(NULL, &why) == NP_FAILED);
	CHECK(why == NP_REASON_STATE_MISMATCH);
	CHECK(!np_active());
	CHECK(put_byte(out, 'M') == 0);
	return 0;
}

static int leave_host(uint16_t port, int out, int in) {
	CHECK(start_host(port, out) == 0);
	CHECK(host_frames_then_meet(10, 0xBBBB, out, in) == 0);
	CHECK(expect_byte(in, 'L', 15000) == 0);
	uint64_t t0 = real_ms();
	np_reason why = NP_REASON_NONE;
	while (np_get_state(NULL, &why) != NP_ENDED && real_ms() - t0 < 1000) {
		step(1);
		nap_ms(1);
	}
	CHECK(np_get_state(NULL, &why) == NP_ENDED);
	CHECK(why == NP_REASON_PEER_LEFT);
	return 0;
}
static int leave_client(uint16_t port, int out, int in) {
	CHECK(start_client(port, in) == 0);
	CHECK(client_frames_then_meet(10, 0xCCCC, out, in) == 0);
	np_leave();
	CHECK(put_byte(out, 'L') == 0);
	np_reason why = NP_REASON_TIMEOUT;
	CHECK(np_get_state(NULL, &why) == NP_ENDED && why == NP_REASON_NONE);
	CHECK(!np_active());
	CHECK(step(1) == 0);
	nap_ms(200);
	return 0;
}

static int late_host(uint16_t port, int out, int in) {
	nap_ms(1500); /* the core is still loading: the joiner's connects are refused and retried */
	CHECK(np_start(NP_ROLE_HOST, "127.0.0.1", port, fake_clock) == 0);
	CHECK(run_frames(20, 0x1357, 15000, 1) == 0);
	CHECK(expect_byte(in, 'F', 15000) == 0);
	CHECK(put_byte(out, 'D') == 0);
	return 0;
}
static int late_client(uint16_t port, int out, int in) {
	CHECK(np_start(NP_ROLE_CLIENT, "127.0.0.1", port, fake_clock) == 0);
	CHECK(np_get_state(NULL, NULL) == NP_CONNECTING);
	CHECK(run_frames(20, 0x2468, 15000, 1) == 0);
	CHECK(put_byte(out, 'F') == 0);
	CHECK(expect_byte(in, 'D', 15000) == 0);
	return 0;
}

/* ---------------------------------------------------------------- raw peer helpers */

static int raw_connect(uint16_t port) {
	for (int attempt = 0; attempt < 50; attempt++) {
		int fd = socket(AF_INET, SOCK_STREAM, 0);
		if (fd < 0)
			return -1;
#if defined(__APPLE__)
		int one = 1;
		setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, &one, sizeof(one));
#endif
		struct sockaddr_in a;
		memset(&a, 0, sizeof(a));
		a.sin_family = AF_INET;
		a.sin_port = htons(port);
		a.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
		if (connect(fd, (struct sockaddr*)&a, sizeof(a)) == 0) {
			int on = 1;
			setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &on, sizeof(on));
			return fd;
		}
		close(fd);
		nap_ms(20);
	}
	return -1;
}

static int raw_send(int fd, const void* b, size_t n) {
	return send(fd, b, n, MSG_NOSIGNAL) == (ssize_t)n ? 0 : -1;
}

static int raw_pkt(int fd, uint8_t cmd, uint32_t frame, const void* payload, uint16_t size) {
	uint8_t b[7 + 16];
	b[0] = cmd;
	b[1] = (uint8_t)(frame >> 24);
	b[2] = (uint8_t)(frame >> 16);
	b[3] = (uint8_t)(frame >> 8);
	b[4] = (uint8_t)frame;
	b[5] = (uint8_t)(size >> 8);
	b[6] = (uint8_t)size;
	if (size)
		memcpy(b + 7, payload, size);
	return raw_send(fd, b, 7u + size);
}

static int raw_input(int fd, uint32_t frame, uint16_t word) {
	uint8_t w[2] = {(uint8_t)(word >> 8), (uint8_t)word};
	return raw_pkt(fd, 0x01, frame, w, 2);
}

static int raw_read(int fd, void* b, size_t n, int timeout_ms) { return pipe_get(fd, b, n, timeout_ms); }

static int expect_bytes(int fd, const uint8_t* want, size_t n) {
	uint8_t got[64];
	if (n > sizeof(got) || raw_read(fd, got, n, 3000) != 0)
		return -1;
	if (memcmp(got, want, n) != 0) {
		fprintf(stderr, "  wire bytes differ:");
		for (size_t i = 0; i < n; i++)
			fprintf(stderr, " %02x/%02x", got[i], want[i]);
		fprintf(stderr, "\n");
		return -1;
	}
	return 0;
}

/* Raw client side of the state sync, checking the handheld's exact bytes. */
static int raw_sync(int fd) {
	/* Drive the host's GL-thread pre_frame until it has serialised (it returns 0 throughout SYNCING). */
	for (int i = 0; i < 5; i++) {
		if (step(0) != 0)
			return -1;
		nap_ms(2);
	}
	const uint8_t hdr[11] = {0x03, 0, 0, 0, 0, 0x00, 0x04, 0x00, 0x00, 0x10, 0x00}; /* STATE_HDR f0 size4, u32 4096 */
	if (expect_bytes(fd, hdr, sizeof(hdr)) != 0)
		return -1;
	static uint8_t state[CORE_SIZE];
	if (raw_read(fd, state, CORE_SIZE, 3000) != 0 || memcmp(state, core_buf, CORE_SIZE) != 0)
		return -1;
	if (raw_pkt(fd, 0x05, 0, NULL, 0) != 0) /* STATE_ACK */
		return -1;
	const uint8_t ready[7] = {0x09, 0, 0, 0, 0, 0, 0};
	if (expect_bytes(fd, ready, sizeof(ready)) != 0)
		return -1;
	return wait_state(NP_RUNNING, 3000, 0);
}

/* PAUSE then RESUME: once the host reports PEER_PAUSED, every packet sent before it has been processed. */
static int raw_barrier(int fd) {
	if (raw_pkt(fd, 0x0A, 0, NULL, 0) != 0 || wait_state(NP_PEER_PAUSED, 3000, 0) != 0)
		return -1;
	if (raw_pkt(fd, 0x0B, 0, NULL, 0) != 0)
		return -1;
	uint64_t t0 = real_ms();
	while (np_get_state(NULL, NULL) == NP_PEER_PAUSED) {
		if (real_ms() - t0 > 3000)
			return -1;
		nap_ms(1);
	}
	return 0;
}

/* ---------------------------------------------------------------- raw peer cases */

/* A presync host runs its first frames on its own: no serialize, nothing on the wire, then the usual state sync. */
static int case_presync_runs_before_state_sync(void) {
	uint16_t port = next_port++;
	fake_set(1000000);
	core_reset(1);
	np_set_presync_frames(3);
	CHECK(np_start(NP_ROLE_HOST, "127.0.0.1", port, fake_clock) == 0);
	np_set_presync_frames(0);
	int fd = raw_connect(port);
	CHECK(fd >= 0);
	CHECK(wait_state(NP_SYNCING, 3000, 0) == 0);
	for (int i = 0; i < 3; i++) {
		CHECK(np_pre_frame(0, NULL, f_size, f_ser, f_unser) == 1);
		np_post_frame();
	}
	struct pollfd pfd = {fd, POLLIN, 0};
	CHECK(poll(&pfd, 1, 100) == 0);
	CHECK(np_get_state(NULL, NULL) == NP_SYNCING);
	CHECK(raw_sync(fd) == 0);
	np_shutdown();
	close(fd);
	return 0;
}

static int case_wire_and_keepalive(void) {
	uint16_t port = next_port++;
	fake_set(1000000);
	core_reset(1);
	CHECK(np_start(NP_ROLE_HOST, "127.0.0.1", port, fake_clock) == 0);
	CHECK(np_get_state(NULL, NULL) == NP_CONNECTING);
	int fd = raw_connect(port);
	CHECK(fd >= 0);
	CHECK(raw_sync(fd) == 0);

	/* frames 0 and 1 are prefilled neutral; our words go out for self frames 2 and 3: 10 bytes, centred sticks */
	CHECK(np_pre_frame(0x1234, NULL, f_size, f_ser, f_unser) == 1);
	np_post_frame();
	const uint8_t in2[17] = {0x01, 0, 0, 0, 2, 0, 10, 0x12, 0x34, 0, 0, 0, 0, 0, 0, 0, 0};
	CHECK(expect_bytes(fd, in2, sizeof(in2)) == 0);
	CHECK(np_pre_frame(0xBEEF, NULL, f_size, f_ser, f_unser) == 1);
	np_post_frame();
	const uint8_t in3[17] = {0x01, 0, 0, 0, 3, 0, 10, 0xBE, 0xEF, 0, 0, 0, 0, 0, 0, 0, 0};
	CHECK(expect_bytes(fd, in3, sizeof(in3)) == 0);

	/* frame 2 needs the peer's input: stall, our frame-4 input still goes out exactly once */
	CHECK(np_pre_frame(0x0F0F, NULL, f_size, f_ser, f_unser) == 0);
	CHECK(np_pre_frame(0x0F0F, NULL, f_size, f_ser, f_unser) == 0);
	const uint8_t in4[17] = {0x01, 0, 0, 0, 4, 0, 10, 0x0F, 0x0F, 0, 0, 0, 0, 0, 0, 0, 0};
	CHECK(expect_bytes(fd, in4, sizeof(in4)) == 0);
	int secs = -1;
	CHECK(np_get_state(&secs, NULL) == NP_STALLED && secs == 30);

	/* KEEPALIVE every 500 ms (fake clock) while stalled, frame = self frame */
	fake_set(fake_clock() + 10000u);
	CHECK(np_pre_frame(0x0F0F, NULL, f_size, f_ser, f_unser) == 0);
	const uint8_t ka[7] = {0x0C, 0, 0, 0, 4, 0, 0};
	CHECK(expect_bytes(fd, ka, sizeof(ka)) == 0);
	CHECK(np_get_state(&secs, NULL) == NP_STALLED && secs == 20);

	/* a KEEPALIVE from the peer does not clear the countdown (PAUSE afterwards proves it was read) */
	CHECK(raw_pkt(fd, 0x0C, 2, NULL, 0) == 0);
	CHECK(raw_pkt(fd, 0x0A, 0, NULL, 0) == 0);
	CHECK(wait_state(NP_PEER_PAUSED, 3000, 0) == 0);
	CHECK(np_get_state(&secs, NULL) == NP_PEER_PAUSED && secs == 20);
	fake_set(fake_clock() + 1000u);
	CHECK(np_get_state(&secs, NULL) == NP_PEER_PAUSED && secs == 19);

	/* the peer's input for frame 2, split mid-header across two writes, then RESUME */
	uint8_t pkt[9] = {0x01, 0, 0, 0, 2, 0, 2, 0x00, 0x01};
	CHECK(raw_send(fd, pkt, 3) == 0);
	nap_ms(20);
	CHECK(raw_send(fd, pkt + 3, 6) == 0);
	CHECK(raw_pkt(fd, 0x0B, 0, NULL, 0) == 0);
	CHECK(wait_state(NP_RUNNING, 3000, 0) == 0);
	CHECK(np_pre_frame(0x0F0F, NULL, f_size, f_ser, f_unser) == 1);
	CHECK(np_input(0) == 0x1234 && np_input(1) == 0x0001 && np_input(2) == 0);
	np_post_frame();
	CHECK(np_get_state(&secs, NULL) == NP_RUNNING && secs == 0);

	/* a local pause and resume put PAUSE and RESUME on the wire and no INPUT between them */
	np_pause();
	CHECK(np_pre_frame(0, NULL, f_size, f_ser, f_unser) == 0);
	np_resume();
	np_leave();
	const uint8_t pr[14] = {0x0A, 0, 0, 0, 0, 0, 0, 0x0B, 0, 0, 0, 0, 0, 0};
	CHECK(expect_bytes(fd, pr, sizeof(pr)) == 0);
	const uint8_t disc[7] = {0x08, 0, 0, 0, 0, 0, 0};
	CHECK(expect_bytes(fd, disc, sizeof(disc)) == 0);
	np_shutdown();
	close(fd);
	return 0;
}

static int case_ring_wrap_deterministic(void) {
	uint16_t port = next_port++;
	fake_set(1000000);
	core_reset(1);
	CHECK(np_start(NP_ROLE_HOST, "127.0.0.1", port, fake_clock) == 0);
	int fd = raw_connect(port);
	CHECK(fd >= 0);
	CHECK(raw_sync(fd) == 0);
	uint32_t sent_upto = 1; /* frames 0 and 1 are prefilled */
	for (uint32_t i = 0; i < 100; i++) {
		uint32_t self = i + 2;
		/* the peer may legally be two frames ahead of our self frame: send up to self + 2 */
		while (sent_upto < self + 2) {
			sent_upto++;
			CHECK(raw_input(fd, sent_upto, (uint16_t)(sent_upto * 7u)) == 0);
		}
		CHECK(raw_barrier(fd) == 0); /* all of it is now in the ring, ahead of our self frame */
		if (np_pre_frame((uint16_t)i, NULL, f_size, f_ser, f_unser) != 1) {
			fprintf(stderr, "  stalled at run frame %u (self %u)\n", i, self);
			np_shutdown();
			close(fd);
			return -1;
		}
		CHECK(np_input(1) == (uint16_t)(i < 2 ? 0 : i * 7u));
		np_post_frame();
	}
	np_shutdown();
	close(fd);
	return 0;
}

static int case_oversize_header(void) {
	uint16_t port = next_port++;
	fake_set(1000000);
	CHECK(np_start(NP_ROLE_HOST, "127.0.0.1", port, fake_clock) == 0);
	int fd = raw_connect(port);
	CHECK(fd >= 0);
	const uint8_t big[7] = {0x01, 0, 0, 0, 0, 0x13, 0x88}; /* size 5000 */
	CHECK(raw_send(fd, big, sizeof(big)) == 0);
	np_reason why = NP_REASON_NONE;
	uint64_t t0 = real_ms();
	while (np_get_state(NULL, &why) != NP_ENDED && real_ms() - t0 < 3000)
		nap_ms(2);
	CHECK(np_get_state(NULL, &why) == NP_ENDED);
	CHECK(why == NP_REASON_IO);
	np_shutdown();
	close(fd);
	return 0;
}

static int case_shutdown_idempotent(void) {
	uint16_t port = next_port++;
	np_shutdown(); /* never started */
	CHECK(np_get_state(NULL, NULL) == NP_IDLE && !np_active());
	CHECK(np_start(NP_ROLE_HOST, "127.0.0.1", port, fake_clock) == 0);
	CHECK(np_active());
	CHECK(np_start(NP_ROLE_HOST, "127.0.0.1", port, fake_clock) != 0); /* singleton */
	np_shutdown();
	np_shutdown();
	CHECK(np_get_state(NULL, NULL) == NP_IDLE && !np_active());
	CHECK(np_input(0) == 0 && np_input(1) == 0);
	CHECK(np_pre_frame(1, NULL, f_size, f_ser, f_unser) == 0);
	/* the port is free again */
	CHECK(np_start(NP_ROLE_HOST, "127.0.0.1", port, fake_clock) == 0);
	np_shutdown();
	CHECK(np_start(NP_ROLE_CLIENT, "not an ip", port, fake_clock) != 0);
	CHECK(np_get_state(NULL, NULL) == NP_IDLE);
	/* the host names the one peer it accepts: no address (or a bad one) fails closed */
	CHECK(np_start(NP_ROLE_HOST, "", port, fake_clock) != 0);
	CHECK(np_start(NP_ROLE_HOST, NULL, port, fake_clock) != 0);
	CHECK(np_start(NP_ROLE_HOST, "::ffff:127.0.0.1", port, fake_clock) != 0);
	CHECK(np_get_state(NULL, NULL) == NP_IDLE);
	return 0;
}

/* The host's expected peer connects: the session gets going (SYNCING, then the state goes out). */
static int case_host_accepts_expected_peer(void) {
	uint16_t port = next_port++;
	fake_set(1000000);
	core_reset(1);
	CHECK(np_start(NP_ROLE_HOST, "127.0.0.1", port, fake_clock) == 0);
	int fd = raw_connect(port);
	CHECK(fd >= 0);
	CHECK(wait_state(NP_SYNCING, 3000, 0) == 0);
	int rc = raw_sync(fd);
	np_shutdown();
	close(fd);
	return rc;
}

/* Waits for the engine to close [fd] from its side (EOF or reset); 0 when it did. */
static int expect_closed_by_host(int fd, int timeout_ms) {
	uint64_t t0 = real_ms();
	for (;;) {
		int left = timeout_ms - (int)(real_ms() - t0);
		if (left <= 0)
			return -1;
		struct pollfd p = {fd, POLLIN, 0};
		if (poll(&p, 1, left) <= 0)
			return -1;
		uint8_t b[16];
		ssize_t n = recv(fd, b, sizeof(b), 0);
		if (n == 0 || (n < 0 && errno != EAGAIN && errno != EINTR))
			return 0;
		if (n > 0)
			return -1; /* the host spoke to a stranger */
	}
}

/* Another LAN address than the expected peer is closed at once; the host stays CONNECTING and keeps listening. */
static int case_host_rejects_other_peer(void) {
	uint16_t port = next_port++;
	fake_set(1000000);
	core_reset(1);
	CHECK(np_start(NP_ROLE_HOST, "10.255.255.1", port, fake_clock) == 0);
	for (int round = 0; round < 2; round++) { /* the second connect proves it still listens */
		int fd = raw_connect(port);
		CHECK(fd >= 0);
		raw_pkt(fd, 0x05, 0, NULL, 0); /* a stray STATE_ACK: never read */
		int closed = expect_closed_by_host(fd, 3000);
		close(fd);
		CHECK(closed == 0);
		CHECK(step(1) == 0); /* the GL thread never serialises for a stranger */
		CHECK(np_get_state(NULL, NULL) == NP_CONNECTING);
		CHECK(np_active());
	}
	np_shutdown();
	return 0;
}

/* Raw host: listen on port, start the client engine, accept its connection; the client is then SYNCING. */
static int raw_host_with_client(uint16_t port, int* lfd_out) {
	int lfd = np_net_listen(port);
	if (lfd < 0)
		return -1;
	*lfd_out = lfd;
	if (np_start(NP_ROLE_CLIENT, "127.0.0.1", port, fake_clock) != 0)
		return -1;
	struct pollfd p = {lfd, POLLIN, 0};
	if (poll(&p, 1, 3000) <= 0)
		return -1;
	int fd = accept(lfd, NULL, NULL);
	if (fd < 0)
		return -1;
	if (wait_state(NP_SYNCING, 3000, 0) != 0) { /* drive 0: the client does not know its own size yet */
		close(fd);
		return -1;
	}
	return fd;
}

/* The client waits 45 s (not 10 s) for STATE_HDR: a phone host listens before its core runs. */
static int case_client_state_header_wait(void) {
	uint16_t port = next_port++;
	fake_set(1000000);
	int lfd = -1;
	int fd = raw_host_with_client(port, &lfd);
	CHECK(fd >= 0);
	uint64_t t0 = fake_clock();
	np_reason why = NP_REASON_NONE;
	fake_set(t0 + 10000u);
	CHECK(np_get_state(NULL, &why) == NP_SYNCING);
	fake_set(t0 + 44999u);
	CHECK(np_get_state(NULL, &why) == NP_SYNCING);
	fake_set(t0 + 45000u);
	CHECK(np_get_state(NULL, &why) == NP_FAILED && why == NP_REASON_TIMEOUT);
	np_shutdown();
	close(fd);
	close(lfd);
	return 0;
}

/* A STATE_HDR over 64 MiB fails the client before any allocation (it does not know its own size yet). */
static int case_client_state_size_cap(void) {
	uint16_t port = next_port++;
	fake_set(1000000);
	int lfd = -1;
	int fd = raw_host_with_client(port, &lfd);
	CHECK(fd >= 0);
	uint32_t big = 64u * 1024u * 1024u + 1u;
	uint8_t sz[4] = {(uint8_t)(big >> 24), (uint8_t)(big >> 16), (uint8_t)(big >> 8), (uint8_t)big};
	CHECK(raw_pkt(fd, 0x03, 0, sz, 4) == 0);
	np_reason why = NP_REASON_NONE;
	uint64_t t0 = real_ms();
	while (np_get_state(NULL, &why) != NP_FAILED && real_ms() - t0 < 3000)
		nap_ms(2);
	CHECK(np_get_state(NULL, &why) == NP_FAILED && why == NP_REASON_STATE_MISMATCH);
	np_shutdown();
	close(fd);
	close(lfd);
	return 0;
}

/* Protocol 3 INPUT: ours is 10 bytes (buttons, LX, LY, RX, RY, big-endian); the peer's 10-byte and legacy 2-byte packets
 * (centred sticks) both count; any other size is dropped. The latency prefill frames are centred. */
static int case_input_10_bytes_and_legacy_2_bytes(void) {
	uint16_t port = next_port++;
	fake_set(1000000);
	core_reset(1);
	CHECK(np_start(NP_ROLE_HOST, "127.0.0.1", port, fake_clock) == 0);
	int fd = raw_connect(port);
	CHECK(fd >= 0);
	CHECK(raw_sync(fd) == 0);

	const int16_t a[4] = {-32768, 32767, 0x1234, -2};
	CHECK(np_pre_frame(0x1234, a, f_size, f_ser, f_unser) == 1); /* run frame 0 (prefilled) */
	for (unsigned p = 0; p < 2; p++)
		for (unsigned i = 0; i < 2; i++)
			for (unsigned id = 0; id < 2; id++)
				CHECK(np_analog(p, i, id) == 0);
	np_post_frame();
	const uint8_t in2[17] = {0x01, 0, 0, 0, 2, 0, 10, 0x12, 0x34, 0x80, 0x00, 0x7F, 0xFF, 0x12, 0x34, 0xFF, 0xFE};
	CHECK(expect_bytes(fd, in2, sizeof(in2)) == 0);
	CHECK(np_pre_frame(0x0001, NULL, f_size, f_ser, f_unser) == 1); /* frame 1; NULL sticks go out centred */
	np_post_frame();
	const uint8_t in3[17] = {0x01, 0, 0, 0, 3, 0, 10, 0x00, 0x01, 0, 0, 0, 0, 0, 0, 0, 0};
	CHECK(expect_bytes(fd, in3, sizeof(in3)) == 0);

	const uint8_t p2[10] = {0x00, 0x05, 0x00, 0x64, 0xFF, 0x9C, 0x00, 0x00, 0x7F, 0xFF}; /* 5; 100, -100, 0, 32767 */
	CHECK(raw_pkt(fd, 0x01, 2, p2, sizeof(p2)) == 0);
	CHECK(raw_input(fd, 3, 0x0002) == 0); /* legacy 2 bytes */
	const uint8_t odd[4] = {0, 7, 0, 7};
	CHECK(raw_pkt(fd, 0x01, 4, odd, sizeof(odd)) == 0); /* 4 bytes: dropped */
	CHECK(raw_barrier(fd) == 0);

	CHECK(np_pre_frame(0, NULL, f_size, f_ser, f_unser) == 1); /* frame 2 */
	CHECK(np_input(0) == 0x1234 && np_input(1) == 0x0005);
	CHECK(np_analog(0, 0, 0) == -32768 && np_analog(0, 0, 1) == 32767 && np_analog(0, 1, 0) == 0x1234 && np_analog(0, 1, 1) == -2);
	CHECK(np_analog(1, 0, 0) == 100 && np_analog(1, 0, 1) == -100 && np_analog(1, 1, 0) == 0 && np_analog(1, 1, 1) == 32767);
	CHECK(np_analog(2, 0, 0) == 0 && np_analog(0, 2, 0) == 0 && np_analog(0, 0, 2) == 0);
	np_post_frame();
	CHECK(np_pre_frame(0, NULL, f_size, f_ser, f_unser) == 1); /* frame 3: the legacy packet, sticks centred */
	CHECK(np_input(1) == 0x0002);
	CHECK(np_analog(1, 0, 0) == 0 && np_analog(1, 0, 1) == 0 && np_analog(1, 1, 0) == 0 && np_analog(1, 1, 1) == 0);
	np_post_frame();
	CHECK(np_pre_frame(0, NULL, f_size, f_ser, f_unser) == 0); /* frame 4's odd-sized packet was dropped: stalled */
	CHECK(np_get_state(NULL, NULL) == NP_STALLED);
	np_shutdown();
	close(fd);
	return 0;
}

static int expect_silence(int fd, int ms) {
	struct pollfd p = {fd, POLLIN, 0};
	return poll(&p, 1, ms) == 0 ? 0 : -1;
}

/* A local pause sends PAUSE and then no INPUT until RESUME, which goes out ahead of the next INPUT. It counts down
 * 30 s here. */
static int case_local_pause_sends_pause_and_stops_input(void) {
	uint16_t port = next_port++;
	fake_set(1000000);
	core_reset(1);
	CHECK(np_start(NP_ROLE_HOST, "127.0.0.1", port, fake_clock) == 0);
	int fd = raw_connect(port);
	CHECK(fd >= 0);
	CHECK(raw_sync(fd) == 0);
	CHECK(np_pre_frame(0x0101, NULL, f_size, f_ser, f_unser) == 1);
	np_post_frame();
	uint8_t first[17];
	CHECK(raw_read(fd, first, sizeof(first), 3000) == 0 && first[0] == 0x01 && first[6] == 10);
	const uint8_t pause[7] = {0x0A, 0, 0, 0, 0, 0, 0};
	const uint8_t resume[7] = {0x0B, 0, 0, 0, 0, 0, 0};
	np_pause();
	CHECK(expect_bytes(fd, pause, sizeof(pause)) == 0);
	CHECK(np_pre_frame(0x0202, NULL, f_size, f_ser, f_unser) == 0); /* paused: no frame, no INPUT */
	CHECK(expect_silence(fd, 300) == 0);
	int secs = -1;
	CHECK(np_get_state(&secs, NULL) == NP_RUNNING && secs == 30);
	np_resume();
	CHECK(expect_bytes(fd, resume, sizeof(resume)) == 0);
	CHECK(expect_silence(fd, 200) == 0);
	CHECK(np_pre_frame(0x0303, NULL, f_size, f_ser, f_unser) == 1); /* frame 1 (prefilled) */
	np_post_frame();
	const uint8_t in3[17] = {0x01, 0, 0, 0, 3, 0, 10, 0x03, 0x03, 0, 0, 0, 0, 0, 0, 0, 0};
	CHECK(expect_bytes(fd, in3, sizeof(in3)) == 0); /* the next bytes are INPUT again */
	/* a pause left alone ends the session after 30 s; PAUSE then DISCONNECT, no INPUT */
	np_pause();
	CHECK(expect_bytes(fd, pause, sizeof(pause)) == 0);
	uint64_t t0 = fake_clock();
	fake_set(t0 + 29999u);
	CHECK(np_get_state(&secs, NULL) == NP_RUNNING && secs == 1);
	fake_set(t0 + 30000u);
	np_reason why = NP_REASON_NONE;
	CHECK(np_get_state(NULL, &why) == NP_ENDED && why == NP_REASON_TIMEOUT);
	const uint8_t disc[7] = {0x08, 0, 0, 0, 0, 0, 0};
	CHECK(expect_bytes(fd, disc, sizeof(disc)) == 0);
	np_shutdown();
	close(fd);
	return 0;
}

/* ---------------------------------------------------------------- reader unit case */

static int case_reader_split(void) {
	np_reader r;
	np_reader_reset(&r);
	uint8_t stream[7 + 2 + 7 + 4 + 5 + 7];
	size_t n = 0;
	np_encode_header(stream + n, 0x01, 0x01020304u, 2);
	n += 7;
	stream[n++] = 0xAB;
	stream[n++] = 0xCD;
	np_encode_header(stream + n, 0x03, 0, 4);
	n += 7;
	stream[n++] = 0;
	stream[n++] = 0;
	stream[n++] = 0;
	stream[n++] = 5;
	memcpy(stream + n, "hello", 5);
	n += 5;
	np_encode_header(stream + n, 0x09, 0, 0);
	n += 7;
	CHECK(stream[0] == 0x01 && stream[1] == 1 && stream[4] == 4 && stream[5] == 0 && stream[6] == 2);

	/* one byte at a time */
	uint8_t raw[5];
	int seen = 0;
	for (size_t i = 0; i < n; i++) {
		size_t used = 0;
		np_packet p;
		np_rd_result res = np_reader_feed(&r, stream + i, 1, &used, &p);
		CHECK(used == 1);
		if (res == NP_RD_PACKET) {
			seen++;
			if (seen == 1)
				CHECK(p.cmd == 0x01 && p.frame == 0x01020304u && p.size == 2 && p.payload[0] == 0xAB && p.payload[1] == 0xCD);
			else if (seen == 2) {
				CHECK(p.cmd == 0x03 && p.size == 4 && p.payload[3] == 5);
				np_reader_expect_raw(&r, raw, 5);
			} else
				CHECK(seen == 4 && p.cmd == 0x09 && p.size == 0);
		} else if (res == NP_RD_RAW_DONE) {
			seen++;
			CHECK(seen == 3 && memcmp(raw, "hello", 5) == 0);
		} else
			CHECK(res == NP_RD_MORE);
	}
	CHECK(seen == 4);

	/* oversize header */
	np_reader_reset(&r);
	uint8_t big[7];
	np_encode_header(big, 0x01, 0, 4097);
	size_t used = 0;
	np_packet p;
	CHECK(np_reader_feed(&r, big, 7, &used, &p) == NP_RD_OVERSIZE);
	np_encode_header(big, 0x01, 0, 4096);
	np_reader_reset(&r);
	CHECK(np_reader_feed(&r, big, 7, &used, &p) == NP_RD_MORE && used == 7);
	return 0;
}

/* ---------------------------------------------------------------- main */

/* In-process case: always leave the singleton engine stopped so one failure cannot cascade. */
static void local_case(const char* name, int (*fn)(void)) {
	int rc = fn();
	np_shutdown();
	report(name, rc == 0, NULL);
}

int main(void) {
	setvbuf(stdout, NULL, _IOLBF, 0);
	next_port = (uint16_t)(40000 + (getpid() % 2000) * 10);

	local_case("reader_split_header_and_raw", case_reader_split);
	local_case("shutdown_idempotent", case_shutdown_idempotent);
	local_case("host_accepts_expected_peer", case_host_accepts_expected_peer);
	local_case("host_rejects_other_peer_keeps_listening", case_host_rejects_other_peer);
	local_case("client_state_header_wait_45s", case_client_state_header_wait);
	local_case("client_state_size_cap_64mib", case_client_state_size_cap);

	lockstep_ports_ok = 0;
	run_pair("sync_and_lockstep_200_frames", lockstep_host, lockstep_client);
	report("port_two_and_up_neutral", lockstep_ports_ok, NULL);
	run_pair("early_remote_input_past_frame_64", early_host, early_client);
	local_case("early_remote_input_ring_wrap_deterministic", case_ring_wrap_deterministic);
	run_pair("stall_countdown_30s_wall_clock", stall_host, stall_client);
	run_pair("peer_pause_reads_as_peer_paused", pause_host, pause_client);
	run_pair("double_pause_never_free_runs", dpause_host, dpause_client);
	run_pair("pause_mid_frame_from_other_thread", midframe_host, midframe_client);
	run_pair("state_size_mismatch_fails", mismatch_host, mismatch_client);
	run_pair("leave_ends_peer", leave_host, leave_client);
	local_case("oversize_header_ends_session", case_oversize_header);
	local_case("presync_runs_before_state_sync", case_presync_runs_before_state_sync);
	local_case("wire_bytes_and_keepalive_countdown", case_wire_and_keepalive);
	local_case("input_10_bytes_and_legacy_2_bytes", case_input_10_bytes_and_legacy_2_bytes);
	local_case("local_pause_sends_pause_and_stops_input", case_local_pause_sends_pause_and_stops_input);
	run_pair("client_connects_once_the_host_listens", late_host, late_client);

	if (fails) {
		printf("%d test(s) failed\n", fails);
		return 1;
	}
	return 0;
}
