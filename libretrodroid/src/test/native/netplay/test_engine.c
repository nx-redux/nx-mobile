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
static int unser_calls;	   /* f_unser calls (the state sync, then each resync) */
static uint32_t unser_frame; /* core_frame at the last f_unser */
static uint32_t corrupt_at[4]; /* after these frames the state flips a byte: a drift (0 = unused) */

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
	unser_calls++;
	unser_frame = core_frame;
	return true;
}

static void core_reset(int randomise) {
	core_frame = 0;
	port_bad = 0;
	saw_nonzero = 0;
	saw_analog = 0;
	unser_calls = 0;
	unser_frame = 0;
	memset(corrupt_at, 0, sizeof(corrupt_at));
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
	for (int k = 0; k < 4; k++)
		if (corrupt_at[k] && corrupt_at[k] == core_frame)
			core_buf[0] ^= 0xFF;
}

static uint32_t core_checksum(void) {
	uint32_t h = 2166136261u;
	for (size_t i = 0; i < CORE_SIZE; i++) {
		h ^= core_buf[i];
		h *= 16777619u;
	}
	return h ^ core_frame;
}

/* The pair hash: the whole state and the frame count. */
static uint64_t f_hash(void) { return ((uint64_t)core_checksum() << 32) | core_frame; }

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

/* ---------------------------------------------------------------- wire capture */

typedef struct {
	uint8_t* b;
	size_t n, cap;
} capture;

static void cap_add(capture* c, const void* d, size_t n) {
	if (!c || n == 0)
		return;
	if (c->n + n > c->cap) {
		size_t want = c->cap ? c->cap * 2 : 16384;
		while (want < c->n + n)
			want *= 2;
		c->b = (uint8_t*)realloc(c->b, want);
		c->cap = want;
	}
	memcpy(c->b + c->n, d, n);
	c->n += n;
}

/* Everything the engine has written so far (never blocks). */
static void cap_drain(int fd, capture* c) {
	uint8_t buf[4096];
	for (;;) {
		struct pollfd p = {fd, POLLIN, 0};
		if (poll(&p, 1, 0) <= 0)
			return;
		ssize_t n = recv(fd, buf, sizeof(buf), 0);
		if (n <= 0)
			return;
		cap_add(c, buf, (size_t)n);
	}
}

/* Everything until the engine closes the connection. */
static int cap_until_eof(int fd, capture* c, int timeout_ms) {
	uint8_t buf[4096];
	uint64_t t0 = real_ms();
	for (;;) {
		int left = timeout_ms - (int)(real_ms() - t0);
		struct pollfd p = {fd, POLLIN, 0};
		if (left <= 0 || poll(&p, 1, left) <= 0)
			return -1;
		ssize_t n = recv(fd, buf, sizeof(buf), 0);
		if (n == 0)
			return 0;
		if (n < 0)
			return errno == ECONNRESET ? 0 : -1;
		cap_add(c, buf, (size_t)n);
	}
}

/* Golden files live in golden/ next to this source (run.sh runs from there). NP_WRITE_GOLDEN=1 rewrites them. */
static int golden_check(const char* name, const capture* c) {
	char path[256];
	snprintf(path, sizeof(path), "golden/%s", name);
	if (getenv("NP_WRITE_GOLDEN")) {
		FILE* f = fopen(path, "wb");
		if (!f || fwrite(c->b, 1, c->n, f) != c->n) {
			if (f)
				fclose(f);
			return -1;
		}
		fclose(f);
		fprintf(stderr, "  wrote %s (%zu bytes)\n", path, c->n);
		return 0;
	}
	FILE* f = fopen(path, "rb");
	if (!f) {
		fprintf(stderr, "  missing %s\n", path);
		return -1;
	}
	uint8_t* want = (uint8_t*)malloc(c->n + 1);
	size_t got = fread(want, 1, c->n + 1, f);
	fclose(f);
	int same = got == c->n && memcmp(want, c->b, c->n) == 0;
	if (!same) {
		size_t i = 0;
		while (i < got && i < c->n && want[i] == c->b[i])
			i++;
		fprintf(stderr, "  %s: %zu bytes on the wire, golden %zu; first difference at %zu\n", name, c->n, got, i);
	}
	free(want);
	return same ? 0 : -1;
}

/* Raw client side of the state sync, checking the handheld's exact bytes; what the host sent goes to c (may be NULL). */
static int raw_sync_cap(int fd, capture* c) {
	/* Drive the host's GL-thread pre_frame until it has serialised (it returns 0 throughout SYNCING). */
	for (int i = 0; i < 5; i++) {
		if (step(0) != 0)
			return -1;
		nap_ms(2);
	}
	const uint8_t hdr[11] = {0x03, 0, 0, 0, 0, 0x00, 0x04, 0x00, 0x00, 0x10, 0x00}; /* STATE_HDR f0 size4, u32 4096 */
	if (expect_bytes(fd, hdr, sizeof(hdr)) != 0)
		return -1;
	cap_add(c, hdr, sizeof(hdr));
	static uint8_t state[CORE_SIZE];
	if (raw_read(fd, state, CORE_SIZE, 3000) != 0 || memcmp(state, core_buf, CORE_SIZE) != 0)
		return -1;
	cap_add(c, state, CORE_SIZE);
	if (raw_pkt(fd, 0x05, 0, NULL, 0) != 0) /* STATE_ACK */
		return -1;
	const uint8_t ready[7] = {0x09, 0, 0, 0, 0, 0, 0};
	if (expect_bytes(fd, ready, sizeof(ready)) != 0)
		return -1;
	cap_add(c, ready, sizeof(ready));
	return wait_state(NP_RUNNING, 3000, 0);
}

static int raw_sync(int fd) {
	return raw_sync_cap(fd, NULL);
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

/* The peer's 10-byte INPUT for a frame: its word plus sticks derived from it. */
static int raw_input10(int fd, uint32_t frame, uint16_t word) {
	int16_t a[4];
	axes_for(word, a);
	uint8_t b[10] = {(uint8_t)(word >> 8), (uint8_t)word};
	for (int i = 0; i < 4; i++) {
		b[2 + 2 * i] = (uint8_t)((uint16_t)a[i] >> 8);
		b[3 + 2 * i] = (uint8_t)a[i];
	}
	return raw_pkt(fd, 0x01, frame, b, sizeof(b));
}

/* The raw peer sends its input for exactly the next frames, then the engine runs them. */
static int raw_feed_and_run(int fd, uint32_t* peer_sent, uint32_t frames, uint32_t seed, capture* c, uint32_t ms_per_frame) {
	for (uint32_t done = 0; done < frames;) {
		uint32_t batch = frames - done < 30 ? frames - done : 30;
		while (*peer_sent + 1 < core_frame + batch) {
			(*peer_sent)++;
			CHECK(raw_input10(fd, *peer_sent, word_for(seed ^ 0xFFFF, *peer_sent)) == 0);
		}
		CHECK(raw_barrier(fd) == 0);
		for (uint32_t i = 0; i < batch; i++) {
			fake_set(fake_clock() + ms_per_frame);
			CHECK(step(word_for(seed, core_frame)) == 1);
		}
		done += batch;
		cap_drain(fd, c);
	}
	return 0;
}

/* After the frames: one stall with a KEEPALIVE, a local pause and resume, then leave. */
static int wire_tail(int fd, capture* c) {
	CHECK(step(0x0F0F) == 0);
	CHECK(np_get_state(NULL, NULL) == NP_STALLED);
	fake_set(fake_clock() + 600u);
	CHECK(step(0x0F0F) == 0);
	np_pause();
	np_resume();
	np_leave();
	np_shutdown();
	CHECK(cap_until_eof(fd, c, 3000) == 0);
	return 0;
}

/* Pair mode off: a 300-frame host session puts exactly the bytes the engine sent before pair mode existed. */
static int case_pair_off_wire_unchanged_host(void) {
	uint16_t port = next_port++;
	fake_set(1000000);
	core_reset(1);
	capture c = {0};
	CHECK(np_start(NP_ROLE_HOST, "127.0.0.1", port, fake_clock) == 0);
	int fd = raw_connect(port);
	CHECK(fd >= 0);
	CHECK(raw_sync_cap(fd, &c) == 0);
	uint32_t peer_sent = 1;
	CHECK(raw_feed_and_run(fd, &peer_sent, 300, 0x600D, &c, 0) == 0);
	CHECK(wire_tail(fd, &c) == 0);
	close(fd);
	int rc = golden_check("pair_off_host.bin", &c);
	free(c.b);
	return rc;
}

/* The same for a client: STATE_ACK, its inputs, the stall, pause, resume and DISCONNECT. */
static int case_pair_off_wire_unchanged_client(void) {
	uint16_t port = next_port++;
	fake_set(1000000);
	core_reset(0);
	capture c = {0};
	int lfd = -1;
	int fd = raw_host_with_client(port, &lfd);
	CHECK(fd >= 0);
	static uint8_t state[CORE_SIZE];
	for (size_t i = 0; i < CORE_SIZE; i++)
		state[i] = (uint8_t)(i * 7u + 3u);
	const uint8_t hdr[11] = {0x03, 0, 0, 0, 0, 0x00, 0x04, 0x00, 0x00, 0x10, 0x00};
	CHECK(raw_send(fd, hdr, sizeof(hdr)) == 0);
	CHECK(raw_send(fd, state, CORE_SIZE) == 0);
	const uint8_t ack[7] = {0x05, 0, 0, 0, 0, 0, 0};
	uint64_t t0 = real_ms();
	for (;;) { /* the client unserialises on its GL thread, then acks */
		CHECK(step(0) == 0);
		struct pollfd p = {fd, POLLIN, 0};
		if (poll(&p, 1, 2) > 0)
			break;
		CHECK(real_ms() - t0 < 3000);
	}
	CHECK(expect_bytes(fd, ack, sizeof(ack)) == 0);
	cap_add(&c, ack, sizeof(ack));
	CHECK(memcmp(core_buf, state, CORE_SIZE) == 0);
	CHECK(raw_pkt(fd, 0x09, 0, NULL, 0) == 0); /* READY */
	CHECK(wait_state(NP_RUNNING, 3000, 0) == 0);
	uint32_t peer_sent = 1;
	CHECK(raw_feed_and_run(fd, &peer_sent, 300, 0xD00D, &c, 0) == 0);
	CHECK(wire_tail(fd, &c) == 0);
	close(fd);
	close(lfd);
	int rc = golden_check("pair_off_client.bin", &c);
	free(c.b);
	return rc;
}

/* ---------------------------------------------------------------- pair mode */

/* The next packet of cmd in a captured stream of plain packets (no raw state), from *pos on. 0 found. */
static int cap_find(const capture* c, size_t* pos, uint8_t cmd, uint32_t* frame, const uint8_t** payload, uint16_t* size) {
	while (*pos + 7 <= c->n) {
		const uint8_t* h = c->b + *pos;
		uint16_t sz = (uint16_t)((h[5] << 8) | h[6]);
		if (*pos + 7 + sz > c->n)
			return -1;
		*pos += 7u + sz;
		if (h[0] == cmd) {
			*frame = ((uint32_t)h[1] << 24) | ((uint32_t)h[2] << 16) | ((uint32_t)h[3] << 8) | h[4];
			*payload = h + 7;
			*size = sz;
			return 0;
		}
	}
	return -1;
}

static uint64_t get_be64(const uint8_t* b) {
	uint64_t v = 0;
	for (int i = 0; i < 8; i++)
		v = (v << 8) | b[i];
	return v;
}

static int raw_hash(int fd, uint32_t frame, uint64_t h) {
	uint8_t b[8];
	for (int i = 0; i < 8; i++)
		b[i] = (uint8_t)(h >> (56 - 8 * i));
	return raw_pkt(fd, 0x0D, frame, b, sizeof(b));
}

/* Waits for the engine's next cmd packet after *pos (reading the stream into c); *pos ends past it. Its payload is
 * copied to pl (up to 64 bytes). A STATE_HDR's raw state is not skipped: the caller reads it at *pos. */
static int wait_pkt(int fd, capture* c, size_t* pos, uint8_t cmd, uint32_t* frame, uint8_t* pl, uint16_t* sz) {
	uint64_t t0 = real_ms();
	for (;;) {
		cap_drain(fd, c);
		const uint8_t* p = NULL;
		size_t at = *pos;
		if (cap_find(c, &at, cmd, frame, &p, sz) == 0) {
			*pos = at;
			memcpy(pl, p, *sz < 64 ? *sz : 64);
			return 0;
		}
		if (real_ms() - t0 > 3000)
			return -1;
		nap_ms(2);
	}
}

/* Waits for the engine's HASH for frame want. */
static int wait_hash(int fd, capture* c, size_t* pos, uint32_t want, uint64_t* h) {
	uint32_t frame = 0;
	uint8_t pl[64];
	uint16_t sz = 0;
	do {
		if (wait_pkt(fd, c, pos, 0x0D, &frame, pl, &sz) != 0)
			return -1;
	} while (frame != want || sz != 8);
	*h = get_be64(pl);
	return 0;
}

/* The highest frame the engine sent INPUT for, in everything captured so far. */
static uint32_t max_input_frame(const capture* c) {
	size_t at = 0;
	uint32_t frame = 0, best = 0;
	const uint8_t* p = NULL;
	uint16_t sz = 0;
	while (cap_find(c, &at, 0x01, &frame, &p, &sz) == 0)
		if (frame > best)
			best = frame;
	return best;
}

static int start_pair_host(uint16_t port, int* fd) {
	np_set_pair_mode(true, f_hash);
	if (np_start(NP_ROLE_HOST, "127.0.0.1", port, fake_clock) != 0)
		return -1;
	*fd = raw_connect(port);
	if (*fd < 0)
		return -1;
	return raw_sync(*fd);
}

/* Equal hashes: HASH goes out every 120 frames (frame = the frames run, u64 BE) and the peer's equal one verifies it. */
static int case_pair_hash_match_advances_verified(void) {
	uint16_t port = next_port++;
	fake_set(1000000);
	core_reset(1);
	int fd = -1;
	CHECK(start_pair_host(port, &fd) == 0);
	CHECK(np_last_verified_frame() == -1);
	capture c = {0};
	size_t pos = 0;
	uint32_t peer_sent = 1;
	for (uint32_t check = 120; check <= 240; check += 120) {
		CHECK(raw_feed_and_run(fd, &peer_sent, 120, 0x1A1A, &c, 16) == 0);
		uint64_t h = 0;
		CHECK(wait_hash(fd, &c, &pos, check, &h) == 0);
		CHECK(core_frame == check && h == f_hash() && np_run_frame() == (int64_t)check);
		CHECK(np_last_verified_frame() == (check == 120 ? -1 : 120));
		CHECK(raw_hash(fd, check, h) == 0);
		CHECK(raw_barrier(fd) == 0);
		CHECK(np_last_verified_frame() == (int64_t)check);
	}
	/* a hash for a frame never checked is ignored */
	CHECK(raw_hash(fd, 300, 1) == 0);
	CHECK(raw_barrier(fd) == 0);
	CHECK(np_last_verified_frame() == 240 && np_get_state(NULL, NULL) == NP_RUNNING);
	free(c.b);
	np_shutdown();
	close(fd);
	return 0;
}

/* Fork pair, both in pair mode: one checksum and the client's state numbers come back over the pipe. */
static int pair_meet_host(uint32_t frames, int out, int in, uint32_t* theirs, int64_t* their_verified) {
	CHECK(run_frames(frames, 0x7171, 15000, 1) == 0);
	CHECK(pipe_get(in, theirs, sizeof(*theirs), 15000) == 0);
	CHECK(pipe_get(in, their_verified, sizeof(*their_verified), 5000) == 0);
	return put_byte(out, 'D');
}
static int pair_meet_client(uint32_t frames, int out, int in) {
	CHECK(run_frames(frames, 0x7272, 15000, 1) == 0);
	uint32_t sum = core_checksum();
	int64_t v = np_last_verified_frame();
	CHECK(pipe_put(out, &sum, sizeof(sum)) == 0);
	CHECK(pipe_put(out, &v, sizeof(v)) == 0);
	return expect_byte(in, 'D', 15000);
}

static int resync_host(uint16_t port, int out, int in) {
	np_set_pair_mode(true, f_hash);
	CHECK(start_host(port, out) == 0);
	uint32_t theirs = 0;
	int64_t their_verified = -2;
	CHECK(pair_meet_host(300, out, in, &theirs, &their_verified) == 0);
	if (theirs != core_checksum()) {
		fprintf(stderr, "  checksum host %08x client %08x\n", core_checksum(), theirs);
		return -1;
	}
	CHECK(unser_calls == 0);
	CHECK(np_last_verified_frame() == 240 && their_verified == 240);
	return 0;
}
static int resync_client(uint16_t port, int out, int in) {
	corrupt_at[0] = 50; /* the check at 120 differs */
	np_set_pair_mode(true, f_hash);
	CHECK(start_client(port, in) == 0);
	CHECK(pair_meet_client(300, out, in) == 0);
	/* the state sync, then the host's state at the agreed frame: past the check, inside the lead */
	if (unser_calls != 2 || unser_frame <= 120 || unser_frame > 150) {
		fprintf(stderr, "  unserialised %d times, last at frame %u\n", unser_calls, unser_frame);
		return -1;
	}
	return 0;
}

/* Each side runs until the session ends; the client reports its reason, resyncs and verified frame. */
static int drift_side(int host, int out, int in) {
	uint64_t t0 = real_ms();
	while (!is_over()) {
		CHECK(real_ms() - t0 < 15000);
		step(word_for(host ? 0x3131u : 0x3232u, core_frame));
		nap_ms(1);
	}
	np_reason why = NP_REASON_NONE;
	CHECK(np_get_state(NULL, &why) == NP_ENDED);
	if (host) {
		uint8_t theirs[3] = {0, 0, 0};
		CHECK(pipe_get(in, theirs, sizeof(theirs), 15000) == 0);
		if (why != NP_REASON_DRIFT || theirs[0] != NP_REASON_DRIFT) {
			fprintf(stderr, "  reasons host %d client %d\n", why, theirs[0]);
			return -1;
		}
		CHECK(theirs[1] == 3);	/* the state sync and two resyncs */
		CHECK(theirs[2] == 1);	/* never verified */
		CHECK(np_last_verified_frame() == -1);
		CHECK(core_frame >= 360 && core_frame < 400);
		return put_byte(out, 'D');
	}
	uint8_t mine[3] = {(uint8_t)why, (uint8_t)unser_calls, (uint8_t)(np_last_verified_frame() == -1)};
	CHECK(pipe_put(out, mine, sizeof(mine)) == 0);
	return expect_byte(in, 'D', 15000);
}
static int drift_host(uint16_t port, int out, int in) {
	np_set_pair_mode(true, f_hash);
	CHECK(start_host(port, out) == 0);
	return drift_side(1, out, in);
}
static int drift_client(uint16_t port, int out, int in) {
	/* each lands after the resync before it: the checks at 120, 240 and 360 all differ */
	corrupt_at[0] = 50;
	corrupt_at[1] = 170;
	corrupt_at[2] = 290;
	np_set_pair_mode(true, f_hash);
	CHECK(start_client(port, in) == 0);
	return drift_side(0, out, in);
}

/* A stall raises the delay at once (to 8 at most); 10 s without one lowers it by 1 (to 1 at least). */
static int case_adaptive_delay_rises_on_stall_and_eases(void) {
	uint16_t port = next_port++;
	fake_set(1000000);
	core_reset(1);
	np_set_delay_pin(0);
	int fd = -1;
	CHECK(start_pair_host(port, &fd) == 0);
	CHECK(np_current_delay() == 2);
	uint32_t ps = 1;
	CHECK(raw_feed_and_run(fd, &ps, 30, 0x2B2B, NULL, 16) == 0);
	CHECK(np_current_delay() == 2);
	CHECK(raw_feed_and_run(fd, &ps, 600, 0x2B2B, NULL, 16) == 0); /* 10.08 s calm */
	CHECK(np_current_delay() == 1);
	CHECK(raw_feed_and_run(fd, &ps, 660, 0x2B2B, NULL, 16) == 0);
	CHECK(np_current_delay() == 1); /* the floor */
	for (int want = 2; want <= 8; want++) {
		CHECK(step(0x0101) == 0); /* the peer's input is late: a stall */
		CHECK(np_get_state(NULL, NULL) == NP_STALLED);
		CHECK(np_current_delay() == want);
		CHECK(raw_feed_and_run(fd, &ps, 5, 0x2B2B, NULL, 16) == 0);
	}
	CHECK(step(0x0101) == 0);
	CHECK(np_current_delay() == 8); /* the ceiling */
	CHECK(raw_feed_and_run(fd, &ps, 600, 0x2B2B, NULL, 16) == 0);
	CHECK(np_current_delay() == 8); /* 9.6 s since the stall */
	CHECK(raw_feed_and_run(fd, &ps, 30, 0x2B2B, NULL, 16) == 0);
	CHECK(np_current_delay() == 7);
	CHECK(np_get_state(NULL, NULL) == NP_RUNNING);
	np_shutdown();
	close(fd);
	return 0;
}

/* Pinned at 3: the first frame sends input for frames 2 and 3, and stalls or calm never move it. */
static int case_delay_pin_holds(void) {
	uint16_t port = next_port++;
	fake_set(1000000);
	core_reset(1);
	np_set_delay_pin(3);
	int fd = -1;
	int rc = start_pair_host(port, &fd);
	if (rc == 0) {
		rc = -1;
		capture c = {0};
		uint32_t ps = 1;
		do {
			CHECK(np_current_delay() == 3);
			CHECK(raw_feed_and_run(fd, &ps, 1, 0x3C3C, &c, 16) == 0);
			uint64_t t0 = real_ms();
			while (c.n < 34 && real_ms() - t0 < 3000)
				cap_drain(fd, &c);
			CHECK(c.n >= 34 && c.b[0] == 0x01 && c.b[4] == 2 && c.b[17] == 0x01 && c.b[21] == 3);
			for (int i = 0; i < 3; i++) {
				CHECK(raw_feed_and_run(fd, &ps, 10, 0x3C3C, NULL, 16) == 0);
				CHECK(step(0x0101) == 0);
				CHECK(np_current_delay() == 3);
			}
			CHECK(raw_feed_and_run(fd, &ps, 700, 0x3C3C, NULL, 16) == 0);
			CHECK(np_current_delay() == 3);
			rc = 0;
		} while (0);
		free(c.b);
	}
	np_set_delay_pin(0);
	np_shutdown();
	if (fd >= 0)
		close(fd);
	return rc;
}

/* The frame thread manages 30 frames a second while the peer's input is always there: after 10 s the session ends,
 * PAIR_END (reason 2) then DISCONNECT on the wire. */
static int case_too_slow_ends_with_reason(void) {
	uint16_t port = next_port++;
	fake_set(1000000);
	core_reset(1);
	int fd = -1;
	CHECK(start_pair_host(port, &fd) == 0);
	uint64_t t0 = fake_clock();
	uint32_t ps = 1;
	capture c = {0};
	CHECK(raw_feed_and_run(fd, &ps, 270, 0x4D4D, &c, 33) == 0); /* 8.9 s */
	CHECK(np_get_state(NULL, NULL) == NP_RUNNING);
	while (!is_over() && fake_clock() - t0 < 15000) {
		while (ps + 1 < core_frame + 10) {
			ps++;
			CHECK(raw_input10(fd, ps, 0x1111) == 0);
		}
		if (raw_barrier(fd) != 0)
			break;
		for (int i = 0; i < 10 && !is_over(); i++) {
			fake_set(fake_clock() + 33u);
			step(0x0202);
		}
	}
	np_reason why = NP_REASON_NONE;
	CHECK(np_get_state(NULL, &why) == NP_ENDED && why == NP_REASON_TOO_SLOW);
	uint64_t took = fake_clock() - t0;
	if (took <= 10000 || took > 12000) {
		fprintf(stderr, "  ended after %llu ms\n", (unsigned long long)took);
		return -1;
	}
	np_shutdown();
	CHECK(cap_until_eof(fd, &c, 3000) == 0);
	size_t pos = 0;
	uint32_t frame = 1;
	const uint8_t* pl = NULL;
	uint16_t sz = 0;
	CHECK(cap_find(&c, &pos, 0x0F, &frame, &pl, &sz) == 0);
	CHECK(frame == 0 && sz == 1 && pl[0] == 2);
	CHECK(pos + 7 <= c.n && c.b[pos] == 0x08); /* DISCONNECT next */
	free(c.b);
	close(fd);
	return 0;
}

/* The peer's PAIR_END too slow (reason 2) ends this side with the peer-side reason, so it blames the other phone. */
static int case_peer_too_slow_ends_with_peer_reason(void) {
	uint16_t port = next_port++;
	fake_set(1000000);
	core_reset(1);
	int fd = -1;
	CHECK(start_pair_host(port, &fd) == 0);
	uint32_t ps = 1;
	CHECK(raw_feed_and_run(fd, &ps, 30, 0x6A6A, NULL, 16) == 0);
	const uint8_t code = 2;
	CHECK(raw_pkt(fd, 0x0F, 0, &code, 1) == 0);
	CHECK(wait_state(NP_ENDED, 3000, 1) == 0);
	np_reason why = NP_REASON_NONE;
	CHECK(np_get_state(NULL, &why) == NP_ENDED && why == NP_REASON_PEER_TOO_SLOW);
	np_shutdown();
	close(fd);
	return 0;
}

/* A local pause of 15 s with no frames at all, or frames that keep returning 0, is not "too slow"; nor is a frame
 * thread that stopped for 15 s without a pause (the peer's stall countdown handles that). */
static int case_pair_pause_gap_not_too_slow(void) {
	uint16_t port = next_port++;
	fake_set(1000000);
	core_reset(1);
	int fd = -1;
	CHECK(start_pair_host(port, &fd) == 0);
	uint32_t ps = 1;
	CHECK(raw_feed_and_run(fd, &ps, 30, 0x5E5E, NULL, 16) == 0);
	np_pause();
	fake_set(fake_clock() + 15000u);
	np_resume();
	CHECK(raw_feed_and_run(fd, &ps, 30, 0x5E5E, NULL, 16) == 0);
	CHECK(np_get_state(NULL, NULL) == NP_RUNNING);
	np_pause();
	for (int i = 0; i < 450; i++) {
		fake_set(fake_clock() + 33u);
		CHECK(step(1) == 0);
	}
	np_resume();
	CHECK(raw_feed_and_run(fd, &ps, 30, 0x5E5E, NULL, 16) == 0);
	CHECK(np_get_state(NULL, NULL) == NP_RUNNING);
	fake_set(fake_clock() + 15000u); /* no pause: the frame thread just stopped */
	CHECK(raw_feed_and_run(fd, &ps, 30, 0x5E5E, NULL, 16) == 0);
	CHECK(np_get_state(NULL, NULL) == NP_RUNNING);
	np_shutdown();
	close(fd);
	return 0;
}

/* 15 s waiting on the peer's input at 30 calls a second is a stall (counting down), never "too slow". */
static int case_pair_peer_stall_not_too_slow(void) {
	uint16_t port = next_port++;
	fake_set(1000000);
	core_reset(1);
	int fd = -1;
	CHECK(start_pair_host(port, &fd) == 0);
	uint32_t ps = 1;
	CHECK(raw_feed_and_run(fd, &ps, 30, 0x6F6F, NULL, 16) == 0);
	for (int i = 0; i < 450; i++) {
		fake_set(fake_clock() + 33u);
		CHECK(step(1) == 0);
	}
	int secs = 0;
	CHECK(np_get_state(&secs, NULL) == NP_STALLED && secs == 16); /* 14.85 s into the 30 s countdown */
	CHECK(raw_feed_and_run(fd, &ps, 30, 0x6F6F, NULL, 16) == 0);
	CHECK(np_get_state(NULL, NULL) == NP_RUNNING);
	np_shutdown();
	close(fd);
	return 0;
}

/* A resync survives the peer pausing and a stall before the resync frame, and our own pause while the host waits for
 * the ack; the calm period restarts after a pause, so the delay does not drop on the first frame back. */
static int case_pair_resync_across_pause_and_stall(void) {
	uint16_t port = next_port++;
	fake_set(1000000);
	core_reset(1);
	np_set_delay_pin(0);
	int fd = -1;
	CHECK(start_pair_host(port, &fd) == 0);
	capture c = {0};
	size_t pos = 0;
	uint32_t ps = 1;
	CHECK(raw_feed_and_run(fd, &ps, 120, 0x7E7E, &c, 16) == 0);
	uint64_t h = 0;
	CHECK(wait_hash(fd, &c, &pos, 120, &h) == 0);
	CHECK(raw_hash(fd, 120, h ^ 1u) == 0);
	uint32_t r = 0;
	uint8_t pl[64];
	uint16_t sz = 0;
	CHECK(wait_pkt(fd, &c, &pos, 0x0E, &r, pl, &sz) == 0); /* RESYNC */
	CHECK(sz == 0 && r > 120 && r <= 120 + 16 + 9);
	CHECK(np_get_state(NULL, NULL) == NP_RESYNCING);

	/* the peer pauses for 5 s */
	CHECK(raw_pkt(fd, 0x0A, 0, NULL, 0) == 0);
	CHECK(wait_state(NP_PEER_PAUSED, 3000, 0) == 0);
	for (int i = 0; i < 5; i++) {
		fake_set(fake_clock() + 1000u);
		CHECK(step(1) == 0);
	}
	CHECK(raw_pkt(fd, 0x0B, 0, NULL, 0) == 0);
	CHECK(wait_state(NP_RESYNCING, 3000, 0) == 0);
	/* then its input is late for 2 s */
	CHECK(step(1) == 0);
	fake_set(fake_clock() + 2000u);
	CHECK(step(1) == 0);
	CHECK(np_get_state(NULL, NULL) == NP_RESYNCING);
	int delay = np_current_delay();
	CHECK(delay == 3);

	/* up to the resync frame, where the host holds and sends its state */
	CHECK(raw_feed_and_run(fd, &ps, r - core_frame, 0x7E7E, &c, 16) == 0);
	CHECK(core_frame == r);
	CHECK(step(1) == 0);
	uint32_t frame = 0;
	CHECK(wait_pkt(fd, &c, &pos, 0x03, &frame, pl, &sz) == 0); /* STATE_HDR */
	CHECK(frame == r && sz == 4 && pl[2] == 0x10 && pl[3] == 0);
	uint64_t t0 = real_ms();
	while (c.n < pos + CORE_SIZE && real_ms() - t0 < 3000)
		cap_drain(fd, &c);
	CHECK(c.n >= pos + CORE_SIZE && memcmp(c.b + pos, core_buf, CORE_SIZE) == 0);
	pos += CORE_SIZE;
	CHECK(step(1) == 0); /* holding for the ack */
	CHECK(np_get_state(NULL, NULL) == NP_RESYNCING);

	/* our pause while the ack comes in, 11 s long */
	np_pause();
	CHECK(raw_pkt(fd, 0x05, r, NULL, 0) == 0); /* STATE_ACK, frame R */
	CHECK(raw_barrier(fd) == 0);
	CHECK(np_get_state(NULL, NULL) == NP_RUNNING);
	fake_set(fake_clock() + 11000u);
	np_resume();
	CHECK(raw_feed_and_run(fd, &ps, 1, 0x7E7E, &c, 16) == 0);
	CHECK(np_current_delay() == delay);

	/* the next check matches */
	CHECK(raw_feed_and_run(fd, &ps, 240 - core_frame, 0x7E7E, &c, 16) == 0);
	CHECK(wait_hash(fd, &c, &pos, 240, &h) == 0);
	CHECK(raw_hash(fd, 240, h) == 0);
	CHECK(raw_barrier(fd) == 0);
	CHECK(np_last_verified_frame() == 240 && np_run_frame() == 240);
	free(c.b);
	np_shutdown();
	close(fd);
	return 0;
}

/* Pinning mid-session moves the delay at once (the next pre_frame sends the frames up to it); unpinning resumes
 * adapting from there. */
static int case_pair_delay_pin_changes_mid_session(void) {
	uint16_t port = next_port++;
	fake_set(1000000);
	core_reset(1);
	np_set_delay_pin(0);
	int fd = -1;
	int rc = start_pair_host(port, &fd);
	capture c = {0};
	if (rc == 0) {
		rc = -1;
		do {
			uint32_t ps = 1;
			CHECK(raw_feed_and_run(fd, &ps, 10, 0x8F8F, &c, 16) == 0);
			CHECK(np_current_delay() == 2);
			np_set_delay_pin(5);
			CHECK(np_current_delay() == 5);
			CHECK(step(1) == 0); /* frame 10 waits for the peer; our input goes out up to 10 + 5 */
			uint64_t t0 = real_ms();
			while (max_input_frame(&c) < 15 && real_ms() - t0 < 3000)
				cap_drain(fd, &c);
			nap_ms(50);
			cap_drain(fd, &c);
			CHECK(max_input_frame(&c) == 15);
			CHECK(np_current_delay() == 5); /* a stall does not move a pin */
			np_set_delay_pin(0);
			CHECK(np_current_delay() == 5);
			CHECK(raw_feed_and_run(fd, &ps, 5, 0x8F8F, &c, 16) == 0);
			CHECK(step(1) == 0);
			CHECK(np_current_delay() == 6);
			rc = 0;
		} while (0);
	}
	free(c.b);
	np_set_delay_pin(0);
	np_shutdown();
	if (fd >= 0)
		close(fd);
	return rc;
}

/* np_set_pair_mode then np_shutdown of the previous session (the app shuts down before every start) keeps the request; np_start consumes it, so
 * the next session without a request is not in pair mode. A pinned delay shows which mode a session is in. */
static int case_pair_request_survives_shutdown_and_is_consumed(void) {
	uint16_t port = next_port++;
	int rc = -1;
	np_set_delay_pin(3);
	do {
		CHECK(np_start(NP_ROLE_HOST, "127.0.0.1", port, fake_clock) == 0); /* the previous session */
		np_set_pair_mode(true, f_hash);
		np_shutdown();
		CHECK(np_start(NP_ROLE_HOST, "127.0.0.1", port, fake_clock) == 0);
		CHECK(np_current_delay() == 3); /* pair mode */
		np_shutdown();
		CHECK(np_start(NP_ROLE_HOST, "127.0.0.1", port, fake_clock) == 0);
		CHECK(np_current_delay() == 2); /* not inherited */
		rc = 0;
	} while (0);
	np_set_delay_pin(0);
	np_shutdown();
	return rc;
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
	/* Below the ephemeral range (49152 on macOS and most Linux setups): a listen port there can collide with the
	 * outgoing sockets these tests open themselves. 50 ports per run covers every case below. */
	next_port = (uint16_t)(40000 + (getpid() % 180) * 50);

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
	local_case("pair_off_wire_unchanged_host", case_pair_off_wire_unchanged_host);
	local_case("pair_off_wire_unchanged_client", case_pair_off_wire_unchanged_client);
	local_case("pair_hash_match_advances_verified", case_pair_hash_match_advances_verified);
	run_pair("pair_hash_mismatch_resyncs", resync_host, resync_client);
	run_pair("pair_resync_then_drift_again_ends_drift", drift_host, drift_client);
	local_case("adaptive_delay_rises_on_stall_and_eases", case_adaptive_delay_rises_on_stall_and_eases);
	local_case("delay_pin_holds", case_delay_pin_holds);
	local_case("too_slow_ends_with_reason", case_too_slow_ends_with_reason);
	local_case("peer_too_slow_ends_with_peer_reason", case_peer_too_slow_ends_with_peer_reason);
	local_case("pair_pause_gap_not_too_slow", case_pair_pause_gap_not_too_slow);
	local_case("pair_peer_stall_not_too_slow", case_pair_peer_stall_not_too_slow);
	local_case("pair_resync_across_pause_and_stall", case_pair_resync_across_pause_and_stall);
	local_case("pair_delay_pin_changes_mid_session", case_pair_delay_pin_changes_mid_session);
	local_case("pair_request_survives_shutdown_and_is_consumed", case_pair_request_survives_shutdown_and_is_consumed);

	if (fails) {
		printf("%d test(s) failed\n", fails);
		return 1;
	}
	return 0;
}
