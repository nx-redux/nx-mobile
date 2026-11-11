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
 * Host tests for the DS local wireless room (ndl_link.c): a holder and its members in one process on loopback. Every
 * link runs on the fake clock, so keepalives and timeouts only move when a test advances it; real-time waits only bound
 * loopback delivery.
 */

#define _POSIX_C_SOURCE 200809L
#if defined(__APPLE__)
#define _DARWIN_C_SOURCE
#endif

#include "ndl_link.h"

#include <arpa/inet.h>
#include <netinet/in.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>

static _Atomic uint64_t fake_now = 1000000;
static uint64_t fake_clock(void) { return atomic_load(&fake_now); }
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

#define CHECK(cond)                                                                    \
	do {                                                                               \
		if (!(cond)) {                                                                 \
			fprintf(stderr, "  check failed %s:%d: %s\n", __FILE__, __LINE__, #cond); \
			return -1;                                                                 \
		}                                                                              \
	} while (0)

/* A UDP port nobody holds right now: the kernel's pick for port 0. */
static uint16_t free_udp_port(void) {
	int fd = socket(AF_INET, SOCK_DGRAM, 0);
	struct sockaddr_in a;
	memset(&a, 0, sizeof(a));
	a.sin_family = AF_INET;
	a.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
	a.sin_port = 0;
	bind(fd, (struct sockaddr*)&a, sizeof(a));
	socklen_t n = sizeof(a);
	getsockname(fd, (struct sockaddr*)&a, &n);
	close(fd);
	return ntohs(a.sin_port);
}

static ndl_config config(ndl_mode mode, const char* name, uint16_t port, int max_members) {
	ndl_config c;
	memset(&c, 0, sizeof(c));
	c.mode = mode;
	snprintf(c.name, sizeof(c.name), "%s", name);
	c.room_port = port;
	c.search_port = 0;
	snprintf(c.broadcast, sizeof(c.broadcast), "127.0.0.1");
	snprintf(c.host_ip, sizeof(c.host_ip), "127.0.0.1");
	c.max_members = max_members;
	c.clock = fake_clock;
	return c;
}

static void count_bind(int fd, void* ctx) {
	(void)fd;
	(*(int*)ctx)++;
}

/* ---- Collected deliveries ---- */

typedef struct {
	int n;
	uint8_t buf[8][NDL_MAX_PAYLOAD];
	size_t len[8];
	uint16_t sender[8];
} inbox;

static void collect(const uint8_t* b, size_t n, uint16_t sender, void* ctx) {
	inbox* in = ctx;
	if (in->n < 8) {
		memcpy(in->buf[in->n], b, n);
		in->len[in->n] = n;
		in->sender[in->n] = sender;
	}
	in->n++;
}

/* Takes from l until it has want packets or timeout_ms of real time passed; the inbox count after. */
static int take_until(ndl_link* l, inbox* in, int want, int timeout_ms) {
	uint64_t end = real_ms() + (uint64_t)timeout_ms;
	for (;;) {
		ndl_take(l, collect, in);
		if (in->n >= want || real_ms() >= end)
			return in->n;
		nap_ms(2);
	}
}

static int wait_count(ndl_link* l, int want, int timeout_ms) {
	uint64_t end = real_ms() + (uint64_t)timeout_ms;
	while (ndl_member_count(l) != want) {
		if (real_ms() >= end)
			return 0;
		nap_ms(2);
	}
	return 1;
}

static int wait_in_room(ndl_link* l, bool want, int timeout_ms) {
	uint64_t end = real_ms() + (uint64_t)timeout_ms;
	while (ndl_in_room(l) != want) {
		if (real_ms() >= end)
			return 0;
		nap_ms(2);
	}
	return 1;
}

/* Moves the fake clock ms forward in 100 ms steps, giving every network thread real time to run between steps (their
 * poll slice is 20 ms), so keepalives keep up and only a frozen link falls silent. */
static void advance(uint64_t ms) {
	for (uint64_t done = 0; done < ms; done += 100) {
		fake_add(100);
		nap_ms(25);
	}
}

/* ---- A room of a holder and joiners ---- */

#define ROOM_MAX 4
static ndl_link* room[ROOM_MAX + 1]; /* room[id], id 1 = holder */

static void room_close(void) {
	for (int i = 0; i <= ROOM_MAX; i++) {
		ndl_destroy(room[i]);
		room[i] = NULL;
	}
}

/* Holder + joiners, each joined one after another so ids follow creation order. */
static int room_open(uint16_t port, int joiners) {
	ndl_config c = config(NDL_HOST, "holder", port, 8);
	room[1] = ndl_create(&c);
	CHECK(room[1] != NULL);
	CHECK(ndl_in_room(room[1]));
	for (int i = 2; i <= joiners + 1; i++) {
		char name[17];
		snprintf(name, sizeof(name), "joiner%d", i);
		c = config(NDL_JOIN, name, port, 8);
		room[i] = ndl_create(&c);
		CHECK(room[i] != NULL);
		CHECK(wait_in_room(room[i], true, 2000));
	}
	for (int i = 1; i <= joiners + 1; i++)
		CHECK(wait_count(room[i], joiners + 1, 2000));
	return 0;
}

static void drain(ndl_link* l) {
	inbox in = {0};
	take_until(l, &in, 1000, 50);
}

/* ---- Cases ---- */

static int case_host_and_three_joiners_get_distinct_ids(uint16_t port) {
	CHECK(room_open(port, 3) == 0);
	bool seen[5] = {false};
	for (int i = 1; i <= 4; i++) {
		CHECK(ndl_in_room(room[i]));
		int id = ndl_client_id(room[i]);
		CHECK(id >= 1 && id <= 4);
		CHECK(!seen[id]);
		seen[id] = true;
		CHECK(ndl_member_count(room[i]) == 4);
	}
	CHECK(ndl_client_id(room[1]) == 1);
	return 0;
}

static int case_bind_socket_called_for_each_socket(uint16_t port) {
	int host_calls = 0, join_calls = 0;
	ndl_config c = config(NDL_HOST, "holder", port, 2);
	c.bind_socket = count_bind;
	c.bind_ctx = &host_calls;
	room[1] = ndl_create(&c);
	CHECK(room[1] != NULL);
	CHECK(host_calls >= 1);
	c = config(NDL_JOIN, "joiner", port, 2);
	c.bind_socket = count_bind;
	c.bind_ctx = &join_calls;
	room[2] = ndl_create(&c);
	CHECK(room[2] != NULL);
	CHECK(join_calls >= 1);
	CHECK(wait_in_room(room[2], true, 2000));
	CHECK(wait_count(room[1], 2, 2000));
	return 0;
}

static int case_broadcast_reaches_every_other_member(uint16_t port) {
	CHECK(room_open(port, 3) == 0);
	uint8_t msg[2000];
	for (size_t i = 0; i < sizeof(msg); i++)
		msg[i] = (uint8_t)(i * 7 + 3);
	CHECK(ndl_client_id(room[3]) == 3);
	CHECK(ndl_send(room[3], msg, sizeof(msg), NDL_BROADCAST) == 0);
	for (int i = 1; i <= 4; i++) {
		if (i == 3)
			continue;
		inbox in = {0};
		CHECK(take_until(room[i], &in, 1, 1000) == 1);
		CHECK(in.len[0] == sizeof(msg));
		CHECK(memcmp(in.buf[0], msg, sizeof(msg)) == 0);
		CHECK(in.sender[0] == 3);
		CHECK(take_until(room[i], &in, 2, 50) == 1);
	}
	inbox self = {0};
	CHECK(take_until(room[3], &self, 1, 100) == 0);
	return 0;
}

static int case_directed_reaches_only_its_member(uint16_t port) {
	CHECK(room_open(port, 3) == 0);
	const char msg[] = "to the holder";
	CHECK(ndl_send(room[2], msg, sizeof(msg), 1) == 0);
	inbox in = {0};
	CHECK(take_until(room[1], &in, 1, 1000) == 1);
	CHECK(in.len[0] == sizeof(msg) && memcmp(in.buf[0], msg, sizeof(msg)) == 0);
	CHECK(in.sender[0] == 2);
	for (int i = 2; i <= 4; i++) {
		inbox none = {0};
		CHECK(take_until(room[i], &none, 1, 100) == 0);
	}
	/* Member to member goes through the holder and reaches only that member. */
	const char msg2[] = "two to four";
	CHECK(ndl_send(room[2], msg2, sizeof(msg2), 4) == 0);
	inbox in4 = {0};
	CHECK(take_until(room[4], &in4, 1, 1000) == 1);
	CHECK(in4.sender[0] == 2 && in4.len[0] == sizeof(msg2));
	for (int i = 1; i <= 3; i++) {
		inbox none = {0};
		CHECK(take_until(room[i], &none, 1, 100) == 0);
	}
	return 0;
}

static int case_leave_tells_the_others(uint16_t port) {
	CHECK(room_open(port, 3) == 0);
	ndl_set_mode(room[4], NDL_OFF);
	CHECK(!ndl_in_room(room[4]));
	CHECK(ndl_client_id(room[4]) == -1);
	for (int i = 1; i <= 3; i++)
		CHECK(wait_count(room[i], 3, 1000));
	const char msg[] = "after the leave";
	CHECK(ndl_send(room[1], msg, sizeof(msg), NDL_BROADCAST) == 0);
	for (int i = 2; i <= 3; i++) {
		inbox in = {0};
		CHECK(take_until(room[i], &in, 1, 1000) == 1);
		CHECK(in.sender[0] == 1);
	}
	inbox none = {0};
	CHECK(take_until(room[4], &none, 1, 150) == 0);
	CHECK(ndl_send(room[4], msg, sizeof(msg), NDL_BROADCAST) == -1);
	return 0;
}

static int case_member_timeout_drops_silent_member(uint16_t port) {
	CHECK(room_open(port, 3) == 0);
	ndl_test_freeze(room[3], true);
	advance(2500);
	for (int i = 1; i <= 4; i++)
		CHECK(ndl_member_count(room[i]) == 4); /* not yet */
	advance(1000);
	CHECK(wait_count(room[1], 3, 1000));
	CHECK(wait_count(room[2], 3, 1000));
	CHECK(wait_count(room[4], 3, 1000));
	CHECK(ndl_in_room(room[2]) && ndl_in_room(room[4]));
	/* The dropped member gets nothing more. */
	drain(room[3]);
	const char msg[] = "without three";
	CHECK(ndl_send(room[2], msg, sizeof(msg), NDL_BROADCAST) == 0);
	inbox in1 = {0}, in4 = {0};
	CHECK(take_until(room[1], &in1, 1, 1000) == 1);
	CHECK(take_until(room[4], &in4, 1, 1000) == 1);
	ndl_test_freeze(room[3], false);
	inbox none = {0};
	CHECK(take_until(room[3], &none, 1, 100) == 0);
	return 0;
}

static int case_room_gone_member_leaves(uint16_t port) {
	CHECK(room_open(port, 2) == 0);
	ndl_test_freeze(room[1], true);
	ndl_destroy(room[1]);
	room[1] = NULL;
	advance(2500);
	CHECK(ndl_in_room(room[2]) && ndl_in_room(room[3]));
	advance(1000);
	CHECK(wait_in_room(room[2], false, 1000));
	CHECK(wait_in_room(room[3], false, 1000));
	CHECK(ndl_client_id(room[2]) == -1);
	CHECK(ndl_member_count(room[3]) == 0);
	return 0;
}

static int case_full_room_refuses(uint16_t port) {
	ndl_config c = config(NDL_HOST, "holder", port, 2);
	room[1] = ndl_create(&c);
	CHECK(room[1] != NULL);
	c = config(NDL_JOIN, "first", port, 2);
	room[2] = ndl_create(&c);
	CHECK(wait_in_room(room[2], true, 2000));
	c = config(NDL_JOIN, "second", port, 2);
	room[3] = ndl_create(&c);
	nap_ms(200);
	CHECK(!ndl_in_room(room[3]));
	CHECK(ndl_client_id(room[3]) == -1);
	CHECK(ndl_member_count(room[1]) == 2);
	CHECK(ndl_in_room(room[2]) && ndl_client_id(room[2]) == 2 && ndl_member_count(room[2]) == 2);
	/* A refused joiner stops asking. */
	advance(2000);
	CHECK(!ndl_in_room(room[3]));
	CHECK(ndl_member_count(room[1]) == 2);
	return 0;
}

static void raw_send(uint16_t port, const void* buf, size_t n) {
	int fd = socket(AF_INET, SOCK_DGRAM, 0);
	struct sockaddr_in a;
	memset(&a, 0, sizeof(a));
	a.sin_family = AF_INET;
	a.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
	a.sin_port = htons(port);
	sendto(fd, buf, n, 0, (struct sockaddr*)&a, sizeof(a));
	close(fd);
}

static int case_bad_datagrams_are_ignored(uint16_t port) {
	CHECK(room_open(port, 2) == 0);
	/* Each is a JOIN but for one fault, so taking any of them would add a member. */
	uint8_t join[NDL_HDR_SIZE + 4] = {'N', 'D', 1, 3, 0, 0, 0xFF, 0xFF, 'b', 'a', 'd', 0};
	uint8_t bad[NDL_HDR_SIZE + 4];
	memcpy(bad, join, sizeof(bad));
	bad[0] = 'X';
	raw_send(port, bad, sizeof(bad));
	memcpy(bad, join, sizeof(bad));
	bad[2] = 2;
	raw_send(port, bad, sizeof(bad));
	raw_send(port, join, NDL_HDR_SIZE - 1);
	static uint8_t big[NDL_MAX_PAYLOAD + NDL_HDR_SIZE + 1];
	memcpy(big, join, NDL_HDR_SIZE);
	raw_send(port, big, sizeof(big));
	/* DATA from an address that is no member. */
	uint8_t data[NDL_HDR_SIZE + 3] = {'N', 'D', 1, 6, 0, 2, 0xFF, 0xFF, 'x', 'y', 'z'};
	raw_send(port, data, sizeof(data));
	nap_ms(100);
	const char msg[] = "still fine";
	CHECK(ndl_send(room[2], msg, sizeof(msg), NDL_BROADCAST) == 0);
	inbox in1 = {0}, in3 = {0};
	CHECK(take_until(room[1], &in1, 1, 1000) == 1);
	CHECK(in1.sender[0] == 2 && in1.len[0] == sizeof(msg) && memcmp(in1.buf[0], msg, sizeof(msg)) == 0);
	CHECK(take_until(room[3], &in3, 1, 1000) == 1);
	CHECK(in3.sender[0] == 2);
	for (int i = 1; i <= 3; i++) {
		CHECK(ndl_client_id(room[i]) == i);
		CHECK(ndl_member_count(room[i]) == 3);
	}
	return 0;
}

static int case_oversize_send_rejected(uint16_t port) {
	CHECK(room_open(port, 1) == 0);
	static uint8_t big[NDL_MAX_PAYLOAD + 1];
	CHECK(ndl_send(room[2], big, sizeof(big), NDL_BROADCAST) == -1);
	CHECK(ndl_send(room[1], big, sizeof(big), NDL_BROADCAST) == -1);
	CHECK(ndl_send(room[2], big, NDL_MAX_PAYLOAD, NDL_BROADCAST) == 0);
	inbox in = {0};
	CHECK(take_until(room[1], &in, 1, 1000) == 1);
	CHECK(in.len[0] == NDL_MAX_PAYLOAD);
	return 0;
}

static int case_keepalive_rtt_reported(uint16_t port) {
	CHECK(room_open(port, 1) == 0);
	int mn, av, mx;
	ndl_stats_rtt(room[2], &mn, &av, &mx);   /* clear whatever the join left */
	advance(1500);                          /* a few member keepalives, each echoed by the holder */
	ndl_stats_rtt(room[2], &mn, &av, &mx);
	CHECK(mn >= 0 && mn <= av && av <= mx);
	ndl_stats_rtt(room[2], &mn, &av, &mx);  /* read resets */
	CHECK(mn == -1 && av == -1 && mx == -1);
	ndl_stats_rtt(room[1], &mn, &av, &mx);  /* the holder has nothing to measure */
	CHECK(mn == -1 && av == -1 && mx == -1);
	return 0;
}

static int case_drops_are_counted(uint16_t port) {
	CHECK(room_open(port, 1) == 0);
	ndl_drop_counts d;
	ndl_stats_drops(room[1], &d);   /* clear what the join left */
	/* DATA from an address that is no member, and a datagram with bad magic. */
	uint8_t data[NDL_HDR_SIZE + 3] = {'N', 'D', 1, 6, 0, 2, 0xFF, 0xFF, 'x', 'y', 'z'};
	raw_send(port, data, sizeof(data));
	uint8_t bad[NDL_HDR_SIZE + 3];
	memcpy(bad, data, sizeof(bad));
	bad[0] = 'X';
	raw_send(port, bad, sizeof(bad));
	nap_ms(150);
	ndl_stats_drops(room[1], &d);
	CHECK(d.unknown == 1);
	CHECK(d.bad == 1);
	CHECK(d.queue == 0 && d.wrong_src == 0 && d.echo == 0 && d.dest == 0);
	ndl_stats_drops(room[1], &d);   /* read resets */
	CHECK(d.unknown == 0 && d.bad == 0);
	/* A delivered packet shows in the queue's high-water mark. */
	ndl_stats_drops(room[2], &d);
	const char msg[] = "hi";
	CHECK(ndl_send(room[1], msg, sizeof(msg), 2) == 0);
	nap_ms(100);
	ndl_stats_drops(room[2], &d);
	CHECK(d.queue_hwm == 1);
	return 0;
}

/* ---- Search and the automatic route ---- */

static int case_prefer_settled_then_lower_id(uint16_t port) {
	(void)port;
	ndl_room_info settled_high = {900, true}, unsettled_low = {5, false};
	CHECK(ndl_prefer(settled_high, unsettled_low));
	CHECK(!ndl_prefer(unsettled_low, settled_high));
	ndl_room_info a = {7, false}, b = {9, false};
	CHECK(ndl_prefer(a, b) && !ndl_prefer(b, a));
	ndl_room_info c = {7, true}, d = {9, true};
	CHECK(ndl_prefer(c, d) && !ndl_prefer(d, c));
	CHECK(!ndl_prefer(a, a));
	return 0;
}

static ndl_config auto_config(const char* name, uint16_t port, uint16_t search_port) {
	ndl_config c = config(NDL_AUTO, name, port, 8);
	c.search_port = search_port;
	return c;
}

static int case_auto_joins_a_sheet_host(uint16_t port) {
	uint16_t sp = free_udp_port();
	ndl_config c = config(NDL_HOST, "sheet", port, 8);
	c.search_port = sp;
	room[1] = ndl_create(&c);
	CHECK(room[1] != NULL && ndl_in_room(room[1]));
	c = auto_config("auto", port, sp);
	room[2] = ndl_create(&c);
	CHECK(room[2] != NULL);
	ndl_radio_changed(room[2], NDL_RADIO_ON);
	advance(400);
	CHECK(wait_in_room(room[2], true, 2000));
	CHECK(ndl_client_id(room[2]) == 2);
	CHECK(wait_count(room[1], 2, 2000));
	return 0;
}

/* An AUTO phone whose game hosts and one whose game only looks, in the same room. */
static int auto_pair(uint16_t port, uint16_t sp) {
	ndl_config c = auto_config("hosting", port, sp);
	room[1] = ndl_create(&c);
	CHECK(room[1] != NULL);
	ndl_radio_changed(room[1], NDL_RADIO_HOSTING);
	advance(400);
	CHECK(wait_in_room(room[1], true, 2000));
	CHECK(ndl_client_id(room[1]) == 1);
	c = auto_config("looking", port, sp);
	room[2] = ndl_create(&c);
	CHECK(room[2] != NULL);
	ndl_radio_changed(room[2], NDL_RADIO_ON);
	advance(400);
	CHECK(wait_in_room(room[2], true, 2000));
	CHECK(ndl_client_id(room[2]) == 2);
	CHECK(wait_count(room[1], 2, 2000));
	CHECK(wait_count(room[2], 2, 2000));
	return 0;
}

static int case_auto_host_opens_when_none(uint16_t port) {
	CHECK(auto_pair(port, free_udp_port()) == 0);
	const char msg[] = "found you";
	CHECK(ndl_send(room[2], msg, sizeof(msg), NDL_BROADCAST) == 0);
	inbox in = {0};
	CHECK(take_until(room[1], &in, 1, 1000) == 1);
	CHECK(in.sender[0] == 2);
	return 0;
}

static int case_auto_idle_without_radio(uint16_t port) {
	ndl_config c = auto_config("quiet", port, free_udp_port());
	room[1] = ndl_create(&c);
	CHECK(room[1] != NULL);
	advance(3000);
	CHECK(!ndl_in_room(room[1]));
	CHECK(ndl_test_sent(room[1]) == 0);
	/* Radio on without a room around: it asks. */
	ndl_radio_changed(room[1], NDL_RADIO_ON);
	advance(400);
	CHECK(ndl_test_sent(room[1]) > 0);
	CHECK(!ndl_in_room(room[1]));
	return 0;
}

static int case_linger_then_leave(uint16_t port) {
	CHECK(auto_pair(port, free_udp_port()) == 0);
	ndl_radio_changed(room[2], NDL_RADIO_OFF);
	advance(9900);
	CHECK(ndl_in_room(room[2]));
	CHECK(ndl_member_count(room[1]) == 2);
	advance(200);
	CHECK(wait_in_room(room[2], false, 1000));
	CHECK(wait_count(room[1], 1, 1000));
	CHECK(ndl_in_room(room[1]));
	/* Out for good while the radio stays off. */
	advance(2000);
	CHECK(!ndl_in_room(room[2]));
	return 0;
}

static int case_leave_now_skips_linger(uint16_t port) {
	CHECK(auto_pair(port, free_udp_port()) == 0);
	ndl_radio_changed(room[2], NDL_RADIO_OFF);
	ndl_leave_now(room[2]);
	CHECK(!ndl_in_room(room[2]));
	CHECK(wait_count(room[1], 1, 1000));
	/* Radio back on: it searches and joins again. */
	ndl_radio_changed(room[2], NDL_RADIO_ON);
	advance(1200);
	CHECK(wait_in_room(room[2], true, 2000));
	CHECK(wait_count(room[1], 2, 2000));
	/* A leave with the radio still on stays out until the next radio on. */
	ndl_leave_now(room[2]);
	CHECK(!ndl_in_room(room[2]));
	advance(2000);
	CHECK(!ndl_in_room(room[2]));
	CHECK(wait_count(room[1], 1, 1000));
	return 0;
}

static void run(const char* name, int (*fn)(uint16_t)) {
	uint16_t port = free_udp_port();
	int ok = fn(port) == 0;
	room_close();
	printf("%s %s\n", ok ? "PASS" : "FAIL", name);
	if (!ok)
		fails++;
	fflush(stdout);
}

int main(void) {
	setvbuf(stdout, NULL, _IOLBF, 0);
	run("host_and_three_joiners_get_distinct_ids", case_host_and_three_joiners_get_distinct_ids);
	run("bind_socket_called_for_each_socket", case_bind_socket_called_for_each_socket);
	run("broadcast_reaches_every_other_member", case_broadcast_reaches_every_other_member);
	run("directed_reaches_only_its_member", case_directed_reaches_only_its_member);
	run("leave_tells_the_others", case_leave_tells_the_others);
	run("member_timeout_drops_silent_member", case_member_timeout_drops_silent_member);
	run("room_gone_member_leaves", case_room_gone_member_leaves);
	run("full_room_refuses", case_full_room_refuses);
	run("bad_datagrams_are_ignored", case_bad_datagrams_are_ignored);
	run("oversize_send_rejected", case_oversize_send_rejected);
	run("keepalive_rtt_reported", case_keepalive_rtt_reported);
	run("drops_are_counted", case_drops_are_counted);
	run("prefer_settled_then_lower_id", case_prefer_settled_then_lower_id);
	run("auto_joins_a_sheet_host", case_auto_joins_a_sheet_host);
	run("auto_host_opens_when_none", case_auto_host_opens_when_none);
	run("auto_idle_without_radio", case_auto_idle_without_radio);
	run("linger_then_leave", case_linger_then_leave);
	run("leave_now_skips_linger", case_leave_now_skips_linger);
	if (fails) {
		printf("%d test(s) failed\n", fails);
		return 1;
	}
	return 0;
}
