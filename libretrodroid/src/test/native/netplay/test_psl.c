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

/* Host tests for the PSP ad hoc finder and election (psl_finder.c): pure logic on a fake clock. */

#include "psl_finder.h"

#include <assert.h>
#include <stdio.h>
#include <string.h>

/* IPv4 in network byte order, as the module takes them. */
static uint32_t ip4(int a, int b, int c, int d) {
    uint8_t v[4] = {(uint8_t)a, (uint8_t)b, (uint8_t)c, (uint8_t)d};
    uint32_t r;
    memcpy(&r, v, 4);
    return r;
}

/* A packet as a peer would announce it. */
static void announce(uint32_t ip, uint32_t tie, int flags, uint8_t out[PSL_PACKET]) {
    psl p;
    psl_init(&p, PSL_AUTO, ip, tie, 0);
    psl_active(&p, 1, (flags & PSL_F_GROUPED) != 0, 0);
    if (flags & PSL_F_SERVING) psl_step(&p, PSL_LISTEN_MS);
    size_t n = psl_pack(&p, out);
    assert(n == PSL_PACKET);
    out[5] = (uint8_t)flags;
}

static void hear(psl* s, uint32_t ip, uint32_t tie, int flags, int64_t now) {
    uint8_t pkt[PSL_PACKET];
    announce(ip, tie, flags, pkt);
    psl_heard(s, pkt, PSL_PACKET, ip, now);
}

static void make_auto(psl* s, uint32_t self, uint32_t tie, int grouped, int64_t now) {
    psl_init(s, PSL_AUTO, self, tie, 0);
    psl_active(s, 1, grouped, now);
}

static void pack_parse_roundtrip(void) {
    psl host, other;
    uint8_t pkt[PSL_PACKET];
    psl_init(&host, PSL_HOST, ip4(192, 168, 1, 5), 7, 0);
    assert(psl_pack(&host, pkt) == PSL_PACKET);
    assert(memcmp(pkt, "NXPL\x01", 5) == 0);
    assert(pkt[5] == (PSL_F_SERVING | PSL_F_SESSION));
    make_auto(&other, ip4(192, 168, 1, 6), 3, 0, 0);
    psl_heard(&other, pkt, PSL_PACKET, ip4(192, 168, 1, 5), 10);
    psl_choice c = psl_step(&other, 20);
    assert(c.server_ip == ip4(192, 168, 1, 5) && c.serve == 0 && c.settled == 1);
}

static void junk_ignored(void) {
    psl s;
    uint8_t pkt[PSL_PACKET];
    make_auto(&s, ip4(10, 0, 0, 1), 5, 0, 0);
    announce(ip4(10, 0, 0, 2), 1, PSL_F_SERVING, pkt);
    psl_choice before = psl_step(&s, 100);
    psl_heard(&s, pkt, 15, ip4(10, 0, 0, 2), 100);
    psl_choice c = psl_step(&s, 100);
    assert(memcmp(&c, &before, sizeof c) == 0);
    uint8_t bad[PSL_PACKET];
    memcpy(bad, pkt, PSL_PACKET);
    bad[0] = 'X';
    psl_heard(&s, bad, PSL_PACKET, ip4(10, 0, 0, 2), 100);
    c = psl_step(&s, 100);
    assert(memcmp(&c, &before, sizeof c) == 0);
    memcpy(bad, pkt, PSL_PACKET);
    bad[4] = 2;
    psl_heard(&s, bad, PSL_PACKET, ip4(10, 0, 0, 2), 100);
    c = psl_step(&s, 100);
    assert(memcmp(&c, &before, sizeof c) == 0);
    psl_heard(&s, pkt, PSL_PACKET, ip4(10, 0, 0, 1), 100);
    c = psl_step(&s, 100);
    assert(memcmp(&c, &before, sizeof c) == 0);
    assert(c.settled == 0 && c.serve == 0);
}

static void alone_serves_after_listen(void) {
    psl s;
    make_auto(&s, ip4(10, 0, 0, 1), 5, 0, 0);
    psl_choice c = psl_step(&s, 999);
    assert(c.settled == 0 && c.serve == 0);
    c = psl_step(&s, 1000);
    assert(c.serve == 1 && c.settled == 1 && c.server_ip == ip4(10, 0, 0, 1));
}

static void follows_announced_server(void) {
    psl s;
    make_auto(&s, ip4(10, 0, 0, 1), 5, 0, 0);
    hear(&s, ip4(10, 0, 0, 2), 9, PSL_F_SERVING, 200);
    psl_choice c = psl_step(&s, 200);
    assert(c.settled == 1 && c.serve == 0 && c.server_ip == ip4(10, 0, 0, 2));
}

/* Both serve, then each hears the other; the caller checks which one kept serving. */
static void election(uint32_t ipa, uint32_t tiea, uint32_t ipb, uint32_t tieb, psl* a, psl* b) {
    make_auto(a, ipa, tiea, 0, 0);
    make_auto(b, ipb, tieb, 0, 0);
    assert(psl_step(a, PSL_LISTEN_MS).serve == 1);
    assert(psl_step(b, PSL_LISTEN_MS).serve == 1);
    hear(a, ipb, tieb, PSL_F_SERVING, PSL_LISTEN_MS);
    hear(b, ipa, tiea, PSL_F_SERVING, PSL_LISTEN_MS);
}

static void both_start_one_yields(void) {
    psl a, b;
    election(ip4(10, 0, 0, 1), 5, ip4(10, 0, 0, 2), 9, &a, &b);
    assert(psl_step(&a, 1100).serve == 1);
    psl_choice c = psl_step(&b, 1100);
    assert(c.serve == 0 && c.server_ip == ip4(10, 0, 0, 1));
}

/* Two servers with the same tie, as a third device ranks them (a device takes its own tie for an echo of itself). */
static void equal_tie_lower_ip_wins(void) {
    psl s;
    make_auto(&s, ip4(10, 0, 0, 9), 1, 0, 0);
    /* 10.0.0.2 is lower than 10.0.1.1 in host order, but higher as a little-endian word. */
    hear(&s, ip4(10, 0, 1, 1), 4, PSL_F_SERVING, 100);
    hear(&s, ip4(10, 0, 0, 2), 4, PSL_F_SERVING, 100);
    psl_choice c = psl_step(&s, 100);
    assert(c.serve == 0 && c.server_ip == ip4(10, 0, 0, 2));
}

static void grouped_server_keeps(void) {
    psl s;
    make_auto(&s, ip4(10, 0, 0, 1), 9, 1, 0);
    assert(psl_step(&s, PSL_LISTEN_MS).serve == 1);
    hear(&s, ip4(10, 0, 0, 2), 5, PSL_F_SERVING, 1100);
    hear(&s, ip4(10, 0, 0, 3), 1, PSL_F_SERVING | PSL_F_SESSION, 1100);
    psl_choice c = psl_step(&s, 1100);
    assert(c.serve == 1 && c.server_ip == ip4(10, 0, 0, 1));
}

static void session_beats_auto(void) {
    psl s;
    make_auto(&s, ip4(10, 0, 0, 1), 5, 0, 0);
    hear(&s, ip4(10, 0, 0, 2), 1, PSL_F_SERVING, 100);
    hear(&s, ip4(10, 0, 0, 3), 9, PSL_F_SERVING | PSL_F_SESSION, 100);
    psl_choice c = psl_step(&s, 100);
    assert(c.serve == 0 && c.server_ip == ip4(10, 0, 0, 3));
}

static void grouped_beats_ungrouped(void) {
    psl s;
    make_auto(&s, ip4(10, 0, 0, 1), 5, 0, 0);
    hear(&s, ip4(10, 0, 0, 2), 1, PSL_F_SERVING, 100);
    hear(&s, ip4(10, 0, 0, 3), 9, PSL_F_SERVING | PSL_F_GROUPED, 100);
    assert(psl_step(&s, 100).server_ip == ip4(10, 0, 0, 3));
}

static void server_expires_reelect(void) {
    psl s;
    make_auto(&s, ip4(10, 0, 0, 1), 5, 0, 0);
    hear(&s, ip4(10, 0, 0, 2), 9, PSL_F_SERVING, 0);
    assert(psl_step(&s, 2999).server_ip == ip4(10, 0, 0, 2));
    psl_choice c = psl_step(&s, 3000);
    assert(c.settled == 0 && c.serve == 0);
    c = psl_step(&s, 3000 + PSL_LISTEN_MS);
    assert(c.serve == 1 && c.server_ip == ip4(10, 0, 0, 1));
}

static void linger_then_stop(void) {
    psl s;
    make_auto(&s, ip4(10, 0, 0, 1), 5, 0, 0);
    assert(psl_step(&s, PSL_LISTEN_MS).serve == 1);
    int64_t t = 5000;
    psl_active(&s, 0, 0, t);
    psl_choice c = psl_step(&s, t + 9999);
    assert(c.serve == 1 && c.announce == 1);
    c = psl_step(&s, t + 10000);
    assert(c.serve == 0 && c.announce == 0);
}

static void host_and_join_fixed(void) {
    psl h, j;
    psl_init(&h, PSL_HOST, ip4(10, 0, 0, 1), 5, 0);
    psl_init(&j, PSL_JOIN, ip4(10, 0, 0, 2), 5, ip4(10, 0, 0, 9));
    hear(&h, ip4(10, 0, 0, 3), 1, PSL_F_SERVING | PSL_F_SESSION, 0);
    hear(&j, ip4(10, 0, 0, 3), 1, PSL_F_SERVING | PSL_F_SESSION, 0);
    for (int64_t t = 0; t < 20000; t += 5000) {
        psl_choice c = psl_step(&h, t);
        assert(c.serve == 1 && c.settled == 1 && c.announce == 1 && c.server_ip == ip4(10, 0, 0, 1));
        c = psl_step(&j, t);
        assert(c.serve == 0 && c.settled == 1 && c.announce == 0 && c.server_ip == ip4(10, 0, 0, 9));
    }
}

/* A session's host or joiner that leaves stops serving, announcing and following its server until it comes back. */
static void host_and_join_parked(void) {
    psl h, j;
    uint8_t pkt[PSL_PACKET];
    psl_init(&h, PSL_HOST, ip4(10, 0, 0, 1), 5, 0);
    psl_init(&j, PSL_JOIN, ip4(10, 0, 0, 2), 5, ip4(10, 0, 0, 9));
    psl_park(&h, 1);
    psl_park(&j, 1);
    psl_choice c = psl_step(&h, 1000);
    assert(c.serve == 0 && c.settled == 0 && c.announce == 0);
    assert(psl_pack(&h, pkt) == 0);
    c = psl_step(&j, 1000);
    assert(c.serve == 0 && c.settled == 0 && c.announce == 0);
    assert(psl_pack(&j, pkt) == 0);
    psl_park(&h, 0);
    psl_park(&j, 0);
    c = psl_step(&h, 2000);
    assert(c.serve == 1 && c.settled == 1 && c.announce == 1 && c.server_ip == ip4(10, 0, 0, 1));
    assert(psl_pack(&h, pkt) == PSL_PACKET);
    c = psl_step(&j, 2000);
    assert(c.serve == 0 && c.settled == 1 && c.announce == 0 && c.server_ip == ip4(10, 0, 0, 9));
}

static void parked_is_silent(void) {
    psl s;
    uint8_t pkt[PSL_PACKET];
    make_auto(&s, ip4(10, 0, 0, 1), 5, 0, 0);
    assert(psl_step(&s, PSL_LISTEN_MS).serve == 1);
    assert(psl_pack(&s, pkt) == PSL_PACKET);
    psl_park(&s, 1);
    psl_choice c = psl_step(&s, 2000);
    assert(c.serve == 0 && c.announce == 0 && c.settled == 0);
    assert(psl_pack(&s, pkt) == 0);
    psl_park(&s, 0);
    psl_active(&s, 1, 0, 5000);
    c = psl_step(&s, 5999);
    assert(c.serve == 0 && c.settled == 0);
    c = psl_step(&s, 6000);
    assert(c.serve == 1);
}

static void inactive_is_silent(void) {
    psl s;
    uint8_t pkt[PSL_PACKET];
    psl_init(&s, PSL_AUTO, ip4(10, 0, 0, 1), 5, 0);
    assert(psl_pack(&s, pkt) == 0);
    hear(&s, ip4(10, 0, 0, 2), 1, PSL_F_SERVING, 0);
    assert(psl_pack(&s, pkt) == 0);
    psl_choice c = psl_step(&s, 5000);
    assert(c.serve == 0 && c.settled == 0 && c.announce == 0);
}

/* A server that other devices are logged in to keeps them: it does not yield to a better ranked server. */
static void server_with_users_never_yields(void) {
    psl s;
    make_auto(&s, ip4(10, 0, 0, 1), 9, 0, 0);
    assert(psl_step(&s, PSL_LISTEN_MS).serve == 1);
    psl_active(&s, 1, 1, 1050);   /* the game is not in a group, but its server has another user */
    hear(&s, ip4(10, 0, 0, 2), 5, PSL_F_SERVING, 1100);
    hear(&s, ip4(10, 0, 0, 3), 1, PSL_F_SERVING | PSL_F_SESSION, 1100);
    psl_choice c = psl_step(&s, 1100);
    assert(c.serve == 1 && c.server_ip == ip4(10, 0, 0, 1));
}

/* Out of ad hoc, a server with users lingers for as long as they stay, then for the usual linger. */
static void linger_holds_while_users(void) {
    psl s;
    uint8_t pkt[PSL_PACKET];
    make_auto(&s, ip4(10, 0, 0, 1), 5, 0, 0);
    assert(psl_step(&s, PSL_LISTEN_MS).serve == 1);
    int64_t t = 5000;
    psl_active(&s, 0, 1, t);
    psl_choice c = psl_step(&s, t + 10000);
    assert(c.serve == 1 && c.announce == 1);
    assert(psl_pack(&s, pkt) == PSL_PACKET && (pkt[5] & PSL_F_SERVING));
    c = psl_step(&s, t + 60000);
    assert(c.serve == 1 && c.announce == 1);
    assert(psl_pack(&s, pkt) == PSL_PACKET && (pkt[5] & PSL_F_SERVING));
    psl_active(&s, 0, 0, t + 60000);
    c = psl_step(&s, t + 60000 + PSL_LINGER_MS - 1);
    assert(c.serve == 1);
    c = psl_step(&s, t + 60000 + PSL_LINGER_MS);
    assert(c.serve == 0 && c.announce == 0);
}

/* Three devices that come into ad hoc together, each hearing the others every tick, end with one server. */
static void three_devices_one_server(void) {
    psl d[3];
    const uint32_t ips[3] = {ip4(10, 0, 0, 1), ip4(10, 0, 0, 2), ip4(10, 0, 0, 3)};
    const uint32_t ties[3] = {7, 3, 9};
    const int64_t start[3] = {0, 300, 600};
    for (int i = 0; i < 3; i++) psl_init(&d[i], PSL_AUTO, ips[i], ties[i], 0);
    psl_choice c[3];
    for (int64_t now = 0; now <= 5000; now += 100) {
        uint8_t pkt[3][PSL_PACKET];
        size_t len[3];
        for (int i = 0; i < 3; i++) {
            if (now == start[i]) psl_active(&d[i], 1, 0, now);
            c[i] = psl_step(&d[i], now);
            len[i] = psl_pack(&d[i], pkt[i]);
        }
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (i != j && len[j] > 0) psl_heard(&d[i], pkt[j], len[j], ips[j], now);
            }
        }
    }
    int servers = 0, server = -1;
    for (int i = 0; i < 3; i++) {
        if (c[i].serve) { servers++; server = i; }
    }
    assert(servers == 1);
    for (int i = 0; i < 3; i++) assert(c[i].settled && c[i].server_ip == ips[server]);
}

/* The same three, all coming in at once: all three start serving together and two of them yield. */
static void three_devices_at_once(void) {
    psl d[3];
    const uint32_t ips[3] = {ip4(10, 0, 0, 1), ip4(10, 0, 0, 2), ip4(10, 0, 0, 3)};
    const uint32_t ties[3] = {7, 3, 9};
    for (int i = 0; i < 3; i++) make_auto(&d[i], ips[i], ties[i], 0, 0);
    psl_choice c[3];
    for (int64_t now = 0; now <= 3000; now += 100) {
        uint8_t pkt[3][PSL_PACKET];
        size_t len[3];
        for (int i = 0; i < 3; i++) {
            c[i] = psl_step(&d[i], now);
            len[i] = psl_pack(&d[i], pkt[i]);
        }
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (i != j && len[j] > 0) psl_heard(&d[i], pkt[j], len[j], ips[j], now);
            }
        }
    }
    assert(!c[0].serve && c[1].serve && !c[2].serve);
    for (int i = 0; i < 3; i++) assert(c[i].settled && c[i].server_ip == ips[1]);
}

/* The followed server says it stopped: it is gone at once, and the listen window starts again from then. */
static void server_says_goodbye(void) {
    psl s;
    make_auto(&s, ip4(10, 0, 0, 1), 5, 0, 0);
    hear(&s, ip4(10, 0, 0, 2), 9, PSL_F_SERVING, 200);
    assert(psl_step(&s, 200).server_ip == ip4(10, 0, 0, 2));
    int64_t t = 2000;
    hear(&s, ip4(10, 0, 0, 2), 9, 0, t);
    psl_choice c = psl_step(&s, t);
    assert(c.settled == 0 && c.serve == 0);
    c = psl_step(&s, t + PSL_LISTEN_MS - 1);
    assert(c.settled == 0 && c.serve == 0);
    c = psl_step(&s, t + PSL_LISTEN_MS);
    assert(c.serve == 1 && c.server_ip == ip4(10, 0, 0, 1));
}

/* A stopping server's last packet: not serving, whatever the device's state, so its followers drop it. */
static void goodbye_packet(void) {
    psl h, a;
    uint8_t pkt[PSL_PACKET];
    psl_init(&h, PSL_HOST, ip4(10, 0, 0, 1), 5, 0);
    psl_park(&h, 1);
    assert(psl_pack(&h, pkt) == 0);
    assert(psl_pack_goodbye(&h, pkt) == PSL_PACKET);
    assert(memcmp(pkt, "NXPL\x01", 5) == 0 && pkt[5] == 0);
    make_auto(&a, ip4(10, 0, 0, 2), 9, 0, 0);
    hear(&a, ip4(10, 0, 0, 1), 5, PSL_F_SERVING | PSL_F_SESSION, 100);
    assert(psl_step(&a, 100).server_ip == ip4(10, 0, 0, 1));
    psl_heard(&a, pkt, PSL_PACKET, ip4(10, 0, 0, 1), 200);
    assert(psl_step(&a, 200).settled == 0);
}

/* After a Wi-Fi change this device's own announcement can arrive from what looks like another address. */
static void own_tie_ignored(void) {
    psl s;
    make_auto(&s, ip4(10, 0, 0, 1), 5, 0, 0);
    psl_choice before = psl_step(&s, 100);
    hear(&s, ip4(10, 0, 0, 7), 5, PSL_F_SERVING | PSL_F_SESSION, 100);
    psl_choice c = psl_step(&s, 100);
    assert(memcmp(&c, &before, sizeof c) == 0);
    c = psl_step(&s, PSL_LISTEN_MS);
    assert(c.serve == 1 && c.server_ip == ip4(10, 0, 0, 1));
}

/* A finder packet from outside this device's Wi-Fi subnet is not a server to follow. */
static void off_subnet_ignored(void) {
    psl s;
    make_auto(&s, ip4(192, 168, 1, 10), 5, 0, 0);
    psl_set_net(&s, ip4(192, 168, 1, 255));
    hear(&s, ip4(10, 0, 0, 2), 1, PSL_F_SERVING, 100);
    hear(&s, ip4(192, 168, 7, 2), 1, PSL_F_SERVING, 100);
    psl_choice c = psl_step(&s, 100);
    assert(c.settled == 0);
    hear(&s, ip4(192, 168, 1, 20), 1, PSL_F_SERVING, 100);
    c = psl_step(&s, 100);
    assert(c.settled == 1 && c.server_ip == ip4(192, 168, 1, 20));

    /* A /20: the host part reaches into the third octet. */
    psl w;
    make_auto(&w, ip4(172, 16, 3, 4), 5, 0, 0);
    psl_set_net(&w, ip4(172, 16, 15, 255));
    hear(&w, ip4(172, 16, 9, 9), 1, PSL_F_SERVING, 100);
    assert(psl_step(&w, 100).server_ip == ip4(172, 16, 9, 9));

    /* No mask to be had from the two: every source is accepted, as without one. */
    psl n;
    make_auto(&n, ip4(192, 168, 1, 10), 5, 0, 0);
    psl_set_net(&n, ip4(255, 255, 255, 255));
    hear(&n, ip4(10, 0, 0, 2), 1, PSL_F_SERVING, 100);
    assert(psl_step(&n, 100).server_ip == ip4(10, 0, 0, 2));
    psl_set_net(&n, 0);
    hear(&n, ip4(10, 0, 0, 3), 0, PSL_F_SERVING, 100);
    assert(psl_step(&n, 100).server_ip == ip4(10, 0, 0, 3));

    /* A new address keeps the mask the broadcast gives it. */
    psl_set_self(&s, ip4(192, 168, 1, 11));
    hear(&s, ip4(10, 0, 0, 2), 0, PSL_F_SERVING, 200);
    assert(psl_step(&s, 200).server_ip == ip4(192, 168, 1, 20));
}

/* The cable: an app-owned /30, host end .1, device end .2, broadcast .3. */
#define CABLE_A  ip4(10, 99, 0, 1)
#define CABLE_B  ip4(10, 99, 0, 2)
#define CABLE_BC ip4(10, 99, 0, 3)
#define WIFI_A   ip4(192, 168, 1, 10)
#define WIFI_B   ip4(192, 168, 1, 20)

/* An automatic device on the Wi-Fi that is serving alone. */
static void make_wifi_server(psl* s, uint32_t self, uint32_t tie, int64_t now) {
    make_auto(s, self, tie, 0, now);
    psl_set_net(s, ip4(192, 168, 1, 255));
    psl_choice c = psl_step(s, now + PSL_LISTEN_MS);
    assert(c.serve == 1 && c.cable == 0 && c.self == self);
}

static void cable_coming_holds_wifi_server(void) {
    psl s;
    uint8_t pkt[PSL_PACKET];
    make_wifi_server(&s, WIFI_A, 5, 0);
    psl_set_cable(&s, PSL_CABLE_COMING, 0, 0, 2000);
    psl_choice c = psl_step(&s, 2000);
    assert(c.serve == 0 && c.settled == 0 && c.held == 1 && c.cable == 0);
    /* Still announced as active, but no longer serving: Wi-Fi peers stop following it. */
    assert(psl_pack(&s, pkt) == PSL_PACKET && !(pkt[5] & PSL_F_SERVING));
    /* The stopped server was on the Wi-Fi: its goodbye carries the Wi-Fi address. */
    assert(psl_pack_goodbye(&s, pkt) == PSL_PACKET && pkt[5] == 0);
    uint32_t ip;
    memcpy(&ip, pkt + 8, 4);
    assert(ip == WIFI_A);
}

static void cable_coming_blocks_serving(void) {
    psl s;
    make_auto(&s, WIFI_A, 5, 0, 0);
    psl_set_cable(&s, PSL_CABLE_COMING, 0, 0, 100);
    psl_choice c = psl_step(&s, 3000);
    assert(c.serve == 0 && c.settled == 0 && c.held == 1);
    /* A Wi-Fi server is not followed either. */
    hear(&s, WIFI_B, 1, PSL_F_SERVING | PSL_F_SESSION, 3100);
    c = psl_step(&s, 3100);
    assert(c.serve == 0 && c.settled == 0 && c.held == 1 && c.server_ip == 0);

    /* The game's trigger while COMING arms it too. */
    psl t;
    psl_init(&t, PSL_AUTO, WIFI_A, 5, 0);
    psl_set_cable(&t, PSL_CABLE_COMING, 0, 0, 0);
    assert(psl_step(&t, 5000).held == 0);
    psl_active(&t, 1, 0, 10000);
    c = psl_step(&t, 10000 + PSL_CABLE_HOLD_MS - 1);
    assert(c.held == 1 && c.serve == 0);
    c = psl_step(&t, 10000 + PSL_CABLE_HOLD_MS + PSL_LISTEN_MS);
    assert(c.held == 0 && c.serve == 1);
}

static void cable_coming_hold_expires(void) {
    psl s;
    make_auto(&s, WIFI_A, 5, 0, 0);
    psl_set_cable(&s, PSL_CABLE_COMING, 0, 0, 1000);
    psl_choice c = psl_step(&s, 1000 + PSL_CABLE_HOLD_MS - 1);
    assert(c.held == 1 && c.serve == 0 && c.settled == 0);
    c = psl_step(&s, 1000 + PSL_CABLE_HOLD_MS);
    assert(c.held == 0 && c.serve == 0 && c.settled == 0 && c.announce == 1);
    c = psl_step(&s, 1000 + PSL_CABLE_HOLD_MS + PSL_LISTEN_MS - 1);
    assert(c.held == 0 && c.serve == 0 && c.settled == 0);
    c = psl_step(&s, 1000 + PSL_CABLE_HOLD_MS + PSL_LISTEN_MS);
    assert(c.serve == 1 && c.server_ip == WIFI_A && c.cable == 0);

    /* Staying COMING does not arm it again; only a new transition into COMING does. */
    psl_set_cable(&s, PSL_CABLE_COMING, 0, 0, 20000);
    c = psl_step(&s, 20000);
    assert(c.held == 0 && c.serve == 1);
    psl_set_cable(&s, PSL_CABLE_NONE, 0, 0, 21000);
    psl_set_cable(&s, PSL_CABLE_COMING, 0, 0, 22000);
    c = psl_step(&s, 22000);
    assert(c.held == 1 && c.serve == 0);

    /* A lingering server whose last user went comes back into ad hoc ungrouped: that trigger holds too. */
    psl l;
    make_auto(&l, WIFI_A, 5, 0, 0);
    assert(psl_step(&l, PSL_LISTEN_MS).serve == 1);
    psl_active(&l, 0, 1, 2000);
    psl_set_cable(&l, PSL_CABLE_COMING, 0, 0, 2100);
    assert(psl_step(&l, 2100).serve == 1);   /* grouped and out of ad hoc: kept */
    psl_active(&l, 1, 0, 2200);
    psl_choice d = psl_step(&l, 2200);
    assert(d.held == 1 && d.serve == 0);
}

/* A phone on the port whose cable never comes up costs one hold, not one at every lobby entry or group left. */
static void one_hold_per_coming_episode(void) {
    psl s;
    make_auto(&s, WIFI_A, 5, 0, 0);
    psl_set_cable(&s, PSL_CABLE_COMING, 0, 0, 1000);
    assert(psl_step(&s, 1000).held == 1);
    assert(psl_step(&s, 1000 + PSL_CABLE_HOLD_MS + PSL_LISTEN_MS).serve == 1);
    /* Out of ad hoc and back in, still in the same COMING: no hold. */
    psl_active(&s, 0, 0, 9000);
    psl_active(&s, 1, 0, 9100);
    psl_choice c = psl_step(&s, 9100);
    assert(c.held == 0 && c.serve == 1);
    /* Into a group and out of it while COMING: no hold. */
    psl_active(&s, 1, 1, 10000);
    psl_step(&s, 10000);
    psl_active(&s, 1, 0, 11000);
    c = psl_step(&s, 11000);
    assert(c.held == 0 && c.serve == 1);
    /* The cable leaves COMING and comes again: a new wait, so a hold. */
    psl_set_cable(&s, PSL_CABLE_NONE, 0, 0, 12000);
    psl_set_cable(&s, PSL_CABLE_COMING, 0, 0, 13000);
    c = psl_step(&s, 13000);
    assert(c.held == 1 && c.serve == 0);

    /* COMING from before the game's first entry: that entry holds, the next ones in the same COMING do not. */
    psl g;
    psl_init(&g, PSL_AUTO, WIFI_A, 5, 0);
    psl_set_cable(&g, PSL_CABLE_COMING, 0, 0, 0);
    psl_active(&g, 1, 0, 5000);
    assert(psl_step(&g, 5000).held == 1);
    assert(psl_step(&g, 5000 + PSL_CABLE_HOLD_MS).held == 0);
    psl_active(&g, 0, 0, 20000);
    psl_active(&g, 1, 0, 30000);
    assert(psl_step(&g, 30000).held == 0);
}

/* Leaving a group while COMING holds only a device the cable's going took off it, and only once. */
static void freed_hold_only_after_a_dropped_cable(void) {
    /* Grouped on the Wi-Fi when the phone came on the port: leaving the group is no hold. */
    psl w;
    make_auto(&w, WIFI_A, 5, 1, 0);
    assert(psl_step(&w, PSL_LISTEN_MS).serve == 1);
    psl_set_cable(&w, PSL_CABLE_COMING, 0, 0, 2000);
    assert(psl_step(&w, 2000).held == 0);
    psl_active(&w, 1, 0, 3000);
    assert(psl_step(&w, 3000).held == 0);

    /* Grouped on the cable when it went to COMING: off it at once, held once it leaves its group, and only once. */
    psl f;
    make_auto(&f, WIFI_B, 9, 0, 0);
    psl_set_cable(&f, PSL_CABLE_UP, CABLE_B, CABLE_BC, 0);
    hear(&f, CABLE_A, 5, PSL_F_SERVING, 100);
    assert(psl_step(&f, 100).server_ip == CABLE_A);
    psl_active(&f, 1, 1, 200);
    psl_set_cable(&f, PSL_CABLE_COMING, 0, 0, 2000);
    psl_choice c = psl_step(&f, 2000);
    assert(c.cable == 0 && c.held == 0);
    psl_active(&f, 1, 0, 3000);
    assert(psl_step(&f, 3000).held == 1);
    assert(psl_step(&f, 3000 + PSL_CABLE_HOLD_MS).held == 0);
    psl_active(&f, 1, 1, 12000);
    psl_active(&f, 1, 0, 13000);
    assert(psl_step(&f, 13000).held == 0);

    /* Dropped to NONE (no phone on the port), the phone back later: the group left after that holds. */
    psl n;
    make_auto(&n, WIFI_B, 9, 0, 0);
    psl_set_cable(&n, PSL_CABLE_UP, CABLE_B, CABLE_BC, 0);
    psl_active(&n, 1, 1, 200);
    psl_set_cable(&n, PSL_CABLE_NONE, 0, 0, 2000);
    assert(psl_step(&n, 2000).cable == 0);
    psl_set_cable(&n, PSL_CABLE_COMING, 0, 0, 3000);
    assert(psl_step(&n, 3000).held == 0);
    psl_active(&n, 1, 0, 4000);
    assert(psl_step(&n, 4000).held == 1);
}

/* Stage-1 rules hold on the cable: linger and goodbye, a busy server, this instance's own tie. */
static void stage_one_rules_on_the_cable(void) {
    psl s;
    uint8_t pkt[PSL_PACKET];
    uint32_t ip;
    make_auto(&s, WIFI_A, 5, 0, 0);
    psl_set_cable(&s, PSL_CABLE_UP, CABLE_A, CABLE_BC, 0);
    psl_choice c = psl_step(&s, PSL_LISTEN_MS);
    assert(c.serve == 1 && c.cable == 1 && c.server_ip == CABLE_A);
    psl_active(&s, 0, 0, 5000);
    c = psl_step(&s, 5000 + PSL_LINGER_MS - 1);
    assert(c.serve == 1 && c.announce == 1 && c.cable == 1);
    assert(psl_pack(&s, pkt) == PSL_PACKET && (pkt[5] & PSL_F_SERVING));
    memcpy(&ip, pkt + 8, 4);
    assert(ip == CABLE_A);
    c = psl_step(&s, 5000 + PSL_LINGER_MS);
    assert(c.serve == 0 && c.announce == 0);
    assert(psl_pack(&s, pkt) == 0);
    psl_pack_goodbye(&s, pkt);
    memcpy(&ip, pkt + 8, 4);
    assert(ip == CABLE_A && pkt[5] == 0);

    /* A cable server with another device on it keeps it against a better cable server. */
    psl b;
    make_auto(&b, WIFI_A, 9, 0, 0);
    psl_set_cable(&b, PSL_CABLE_UP, CABLE_A, CABLE_BC, 0);
    assert(psl_step(&b, PSL_LISTEN_MS).serve == 1);
    psl_active(&b, 1, 1, 1050);
    hear(&b, CABLE_B, 1, PSL_F_SERVING | PSL_F_SESSION, 1100);
    c = psl_step(&b, 1100);
    assert(c.serve == 1 && c.cable == 1 && c.server_ip == CABLE_A);

    /* This instance's own tie from a cable address is not another device. */
    psl o;
    make_auto(&o, WIFI_A, 5, 0, 0);
    psl_set_cable(&o, PSL_CABLE_UP, CABLE_A, CABLE_BC, 0);
    psl_choice before = psl_step(&o, 100);
    hear(&o, CABLE_B, 5, PSL_F_SERVING | PSL_F_SESSION, 100);
    c = psl_step(&o, 100);
    assert(memcmp(&c, &before, sizeof c) == 0);
    c = psl_step(&o, PSL_LISTEN_MS);
    assert(c.serve == 1 && c.server_ip == CABLE_A);
}

/* The hold ends without the cable: the election resumes, listening afresh. */
static void cable_none_ends_hold(void) {
    psl s;
    make_auto(&s, WIFI_A, 5, 0, 0);
    psl_set_cable(&s, PSL_CABLE_COMING, 0, 0, 0);
    assert(psl_step(&s, 2000).held == 1);
    psl_set_cable(&s, PSL_CABLE_NONE, 0, 0, 3000);
    psl_choice c = psl_step(&s, 3000 + PSL_LISTEN_MS - 1);
    assert(c.held == 0 && c.serve == 0 && c.settled == 0);
    c = psl_step(&s, 3000 + PSL_LISTEN_MS);
    assert(c.serve == 1 && c.cable == 0);
}

/* UP without a known cable address is no cable: it neither holds nor switches. */
static void cable_up_without_self_is_none(void) {
    psl s;
    make_auto(&s, WIFI_A, 5, 0, 0);
    psl_set_cable(&s, PSL_CABLE_UP, 0, CABLE_BC, 0);
    psl_choice c = psl_step(&s, PSL_LISTEN_MS);
    assert(c.held == 0 && c.serve == 1 && c.cable == 0 && c.self == WIFI_A);
    /* Counted as NONE, it ends a hold the way NONE does, without a move to the cable. */
    psl_set_cable(&s, PSL_CABLE_COMING, 0, 0, 2000);
    assert(psl_step(&s, 2000).held == 1);
    psl_set_cable(&s, PSL_CABLE_UP, 0, CABLE_BC, 2100);
    c = psl_step(&s, 2100);
    assert(c.held == 0 && c.cable == 0);
}

static void cable_up_ends_hold_and_switches(void) {
    psl s;
    make_auto(&s, WIFI_A, 5, 0, 0);
    psl_set_cable(&s, PSL_CABLE_COMING, 0, 0, 0);
    assert(psl_step(&s, 1000).held == 1);
    psl_set_cable(&s, PSL_CABLE_UP, CABLE_A, CABLE_BC, 2000);
    psl_choice c = psl_step(&s, 2000);
    assert(c.held == 0 && c.cable == 1 && c.serve == 0 && c.settled == 0 && c.self == CABLE_A);
    c = psl_step(&s, 2000 + PSL_LISTEN_MS - 1);
    assert(c.serve == 0 && c.settled == 0);
    c = psl_step(&s, 2000 + PSL_LISTEN_MS);
    assert(c.serve == 1 && c.cable == 1 && c.server_ip == CABLE_A);
}

static void cable_up_kept_off_while_grouped(void) {
    psl s;
    make_auto(&s, WIFI_A, 5, 1, 0);
    assert(psl_step(&s, PSL_LISTEN_MS).serve == 1);
    psl_set_cable(&s, PSL_CABLE_COMING, 0, 0, 2000);
    psl_choice c = psl_step(&s, 2000);
    assert(c.held == 0 && c.serve == 1 && c.cable == 0);
    psl_set_cable(&s, PSL_CABLE_UP, CABLE_A, CABLE_BC, 3000);
    for (int64_t t = 3000; t < 30000; t += 1000) {
        c = psl_step(&s, t);
        assert(c.serve == 1 && c.cable == 0 && c.server_ip == WIFI_A && c.self == WIFI_A);
    }

    /* A grouped follower stays with its Wi-Fi server too. */
    psl f;
    make_auto(&f, WIFI_B, 9, 0, 0);
    hear(&f, WIFI_A, 5, PSL_F_SERVING | PSL_F_GROUPED, 100);
    psl_active(&f, 1, 1, 200);
    psl_set_cable(&f, PSL_CABLE_UP, CABLE_B, CABLE_BC, 300);
    hear(&f, WIFI_A, 5, PSL_F_SERVING | PSL_F_GROUPED, 300);
    c = psl_step(&f, 300);
    assert(c.cable == 0 && c.settled == 1 && c.server_ip == WIFI_A);
}

static void cable_up_switches_once_ungrouped(void) {
    psl s;
    uint8_t pkt[PSL_PACKET];
    make_auto(&s, WIFI_A, 5, 1, 0);
    psl_choice prev = psl_step(&s, PSL_LISTEN_MS);
    assert(prev.serve == 1);
    psl_set_cable(&s, PSL_CABLE_UP, CABLE_A, CABLE_BC, 2000);
    assert(psl_step(&s, 2000).cable == 0);
    psl_active(&s, 1, 0, 5000);
    psl_choice c = psl_step(&s, 5000);
    assert(c.cable == 1 && c.serve == 0 && c.settled == 0 && c.held == 0);
    /* The server left behind was on the Wi-Fi: the previous choice says so, and the goodbye carries its address. */
    assert(prev.cable == 0);
    psl_pack_goodbye(&s, pkt);
    uint32_t ip;
    memcpy(&ip, pkt + 8, 4);
    assert(ip == WIFI_A && pkt[5] == 0);
    /* Announcing now goes on the cable, from the cable address. */
    assert(psl_pack(&s, pkt) == PSL_PACKET);
    memcpy(&ip, pkt + 8, 4);
    assert(ip == CABLE_A);
    c = psl_step(&s, 5000 + PSL_LISTEN_MS);
    assert(c.serve == 1 && c.cable == 1 && c.server_ip == CABLE_A);
}

static void cable_mode_ignores_wifi_sources(void) {
    psl s;
    make_auto(&s, WIFI_A, 5, 0, 0);
    psl_set_net(&s, ip4(192, 168, 1, 255));
    psl_set_cable(&s, PSL_CABLE_UP, CABLE_A, CABLE_BC, 0);
    assert(psl_step(&s, 0).cable == 1);
    hear(&s, WIFI_B, 1, PSL_F_SERVING | PSL_F_SESSION, 100);
    hear(&s, ip4(10, 99, 0, 6), 1, PSL_F_SERVING | PSL_F_SESSION, 100);   /* another /30 */
    hear(&s, CABLE_A, 1, PSL_F_SERVING, 100);                             /* itself */
    psl_choice c = psl_step(&s, 100);
    assert(c.settled == 0 && c.serve == 0);
    hear(&s, CABLE_B, 9, PSL_F_SERVING, 200);
    c = psl_step(&s, 200);
    assert(c.settled == 1 && c.serve == 0 && c.server_ip == CABLE_B && c.cable == 1);
}

static void wifi_mode_ignores_cable_sources(void) {
    /* Without a cable broadcast given, the cable is 10.99.0.0/30. */
    psl s;
    make_auto(&s, WIFI_A, 5, 0, 0);
    hear(&s, CABLE_B, 1, PSL_F_SERVING | PSL_F_SESSION, 100);
    psl_choice c = psl_step(&s, 100);
    assert(c.settled == 0);
    hear(&s, ip4(10, 0, 0, 2), 1, PSL_F_SERVING, 100);   /* no subnet filter set: any other source */
    assert(psl_step(&s, 100).server_ip == ip4(10, 0, 0, 2));

    /* With a subnet filter and a cable broadcast given: that cable's /30 is not the Wi-Fi either. */
    psl w;
    make_auto(&w, ip4(10, 99, 0, 9), 5, 0, 0);
    psl_set_net(&w, ip4(10, 99, 0, 255));
    psl_set_cable(&w, PSL_CABLE_NONE, 0, ip4(10, 99, 0, 7), 0);
    hear(&w, ip4(10, 99, 0, 5), 1, PSL_F_SERVING, 100);
    assert(psl_step(&w, 100).settled == 0);
    hear(&w, ip4(10, 99, 0, 2), 1, PSL_F_SERVING, 100);   /* on the Wi-Fi subnet, not that cable */
    assert(psl_step(&w, 100).server_ip == ip4(10, 99, 0, 2));
}

static void cable_down_back_to_wifi(void) {
    psl s;
    make_auto(&s, WIFI_A, 5, 0, 0);
    psl_set_net(&s, ip4(192, 168, 1, 255));
    psl_set_cable(&s, PSL_CABLE_UP, CABLE_A, CABLE_BC, 0);
    hear(&s, CABLE_B, 1, PSL_F_SERVING, 100);
    assert(psl_step(&s, 100).server_ip == CABLE_B);
    psl_set_cable(&s, PSL_CABLE_NONE, 0, 0, 2000);
    hear(&s, CABLE_B, 1, PSL_F_SERVING, 2000);   /* a straggler from the cable */
    psl_choice c = psl_step(&s, 2000);
    assert(c.cable == 0 && c.settled == 0 && c.serve == 0 && c.self == WIFI_A);
    c = psl_step(&s, 2000 + PSL_LISTEN_MS - 1);
    assert(c.settled == 0);
    c = psl_step(&s, 2000 + PSL_LISTEN_MS);
    assert(c.serve == 1 && c.cable == 0 && c.server_ip == WIFI_A);

    /* A cable server whose cable goes: it stops; the previous choice says it served on the cable. */
    psl t;
    uint8_t pkt[PSL_PACKET];
    make_auto(&t, WIFI_A, 5, 0, 0);
    psl_set_cable(&t, PSL_CABLE_UP, CABLE_A, CABLE_BC, 0);
    psl_step(&t, 0);
    psl_choice prev = psl_step(&t, PSL_LISTEN_MS);
    assert(prev.serve == 1 && prev.cable == 1);
    psl_set_cable(&t, PSL_CABLE_COMING, 0, 0, 2000);   /* a pull with the phone still on the port */
    c = psl_step(&t, 2000);
    assert(c.serve == 0 && c.cable == 0 && c.held == 1);
    psl_pack_goodbye(&t, pkt);
    uint32_t ip;
    memcpy(&ip, pkt + 8, 4);
    assert(ip == CABLE_A);
}

/* A session on a cable that is gone cannot go on, and a game alone in its lobby would stay grouped for good. */
static void cable_gone_moves_a_grouped_device(void) {
    psl s;
    uint8_t pkt[PSL_PACKET];
    make_auto(&s, WIFI_A, 5, 0, 0);
    psl_set_cable(&s, PSL_CABLE_UP, CABLE_A, CABLE_BC, 0);
    assert(psl_step(&s, PSL_LISTEN_MS).serve == 1);
    psl_active(&s, 1, 1, 1500);   /* another device is on this server */
    psl_choice prev = psl_step(&s, 1600);
    assert(prev.serve == 1 && prev.cable == 1 && prev.server_ip == CABLE_A);
    psl_set_cable(&s, PSL_CABLE_NONE, 0, 0, 2000);
    psl_choice c = psl_step(&s, 2000);
    assert(c.cable == 0 && c.serve == 0 && c.settled == 0 && c.self == WIFI_A);
    /* The goodbye goes on the cable, where the server was. */
    psl_pack_goodbye(&s, pkt);
    uint32_t ip;
    memcpy(&ip, pkt + 8, 4);
    assert(ip == CABLE_A && pkt[5] == 0);
    c = psl_step(&s, 2000 + PSL_LISTEN_MS - 1);
    assert(c.serve == 0 && c.settled == 0);
    c = psl_step(&s, 2000 + PSL_LISTEN_MS);
    assert(c.serve == 1 && c.cable == 0 && c.server_ip == WIFI_A);

    /* A grouped follower, the cable dropping to COMING: on the Wi-Fi at once, unheld while grouped. */
    psl f;
    make_auto(&f, WIFI_B, 9, 0, 0);
    psl_set_cable(&f, PSL_CABLE_UP, CABLE_B, CABLE_BC, 0);
    hear(&f, CABLE_A, 5, PSL_F_SERVING, 100);
    assert(psl_step(&f, 100).server_ip == CABLE_A);
    psl_active(&f, 1, 1, 200);
    psl_set_cable(&f, PSL_CABLE_COMING, 0, 0, 2000);
    c = psl_step(&f, 2000);
    assert(c.cable == 0 && c.settled == 0 && c.held == 0 && c.self == WIFI_B);
    hear(&f, WIFI_A, 5, PSL_F_SERVING, 2100);
    c = psl_step(&f, 2100);
    assert(c.cable == 0 && c.settled == 1 && c.server_ip == WIFI_A);
    /* No longer grouped while the cable is still coming: held, as on any trigger while COMING. */
    psl_active(&f, 1, 0, 3000);
    c = psl_step(&f, 3000);
    assert(c.held == 1 && c.settled == 0);
    /* Held from leaving the group, not from the drop to COMING while it was grouped. */
    assert(psl_step(&f, 3000 + PSL_CABLE_HOLD_MS - 1).held == 1);
    c = psl_step(&f, 3000 + PSL_CABLE_HOLD_MS);
    assert(c.held == 0);
}

static void choice_self_is_cable_self(void) {
    psl s;
    make_auto(&s, WIFI_A, 5, 0, 0);
    assert(psl_step(&s, 0).self == WIFI_A);
    psl_set_cable(&s, PSL_CABLE_UP, CABLE_B, CABLE_BC, 100);
    psl_choice c = psl_step(&s, 100);
    assert(c.self == CABLE_B && c.cable == 1);
    psl_set_self(&s, ip4(192, 168, 1, 11));   /* the Wi-Fi changing does not touch the cable */
    c = psl_step(&s, 100 + PSL_LISTEN_MS);
    assert(c.self == CABLE_B && c.server_ip == CABLE_B && c.serve == 1);
}

static void session_on_cable_by_self(void) {
    psl h, j;
    psl_init(&h, PSL_HOST, CABLE_A, 5, 0);
    psl_init(&j, PSL_JOIN, CABLE_B, 5, CABLE_A);
    psl_choice c = psl_step(&h, 0);
    assert(c.cable == 1 && c.serve == 1 && c.settled == 1 && c.server_ip == CABLE_A && c.self == CABLE_A);
    c = psl_step(&j, 0);
    assert(c.cable == 1 && c.serve == 0 && c.settled == 1 && c.server_ip == CABLE_A && c.self == CABLE_B);
    /* The cable state does not move them. */
    psl_set_cable(&h, PSL_CABLE_NONE, 0, 0, 100);
    psl_set_cable(&j, PSL_CABLE_NONE, 0, 0, 100);
    assert(psl_step(&h, 100).cable == 1 && psl_step(&j, 100).cable == 1);

    psl hw, jw;
    psl_init(&hw, PSL_HOST, WIFI_A, 5, 0);
    psl_init(&jw, PSL_JOIN, WIFI_B, 5, WIFI_A);
    psl_set_cable(&hw, PSL_CABLE_UP, CABLE_A, CABLE_BC, 0);
    psl_set_cable(&jw, PSL_CABLE_UP, CABLE_B, CABLE_BC, 0);
    c = psl_step(&hw, 0);
    assert(c.cable == 0 && c.serve == 1 && c.server_ip == WIFI_A && c.self == WIFI_A);
    c = psl_step(&jw, 0);
    assert(c.cable == 0 && c.settled == 1 && c.server_ip == WIFI_A && c.self == WIFI_B);

    /* A cable given by its broadcast is that /30, not the default one. */
    psl ho;
    psl_init(&ho, PSL_HOST, ip4(10, 99, 0, 5), 5, 0);
    assert(psl_step(&ho, 0).cable == 0);
    psl_set_cable(&ho, PSL_CABLE_UP, ip4(10, 99, 0, 5), ip4(10, 99, 0, 7), 0);
    assert(psl_step(&ho, 0).cable == 1);
    psl hd;
    psl_init(&hd, PSL_HOST, CABLE_A, 5, 0);
    psl_set_cable(&hd, PSL_CABLE_NONE, 0, ip4(10, 99, 0, 7), 0);
    assert(psl_step(&hd, 0).cable == 0);
}

static void no_wifi_self_never_serves(void) {
    psl s;
    uint8_t pkt[PSL_PACKET];
    make_auto(&s, 0, 5, 0, 0);
    psl_choice c = psl_step(&s, 5000);
    assert(c.serve == 0 && c.settled == 0 && c.announce == 0 && c.cable == 0);
    assert(psl_pack(&s, pkt) == 0);
    hear(&s, WIFI_B, 1, PSL_F_SERVING | PSL_F_SESSION, 5100);
    c = psl_step(&s, 5100);
    assert(c.serve == 0 && c.settled == 0 && c.server_ip == 0);
    /* The cable still works. */
    psl_set_cable(&s, PSL_CABLE_UP, CABLE_A, CABLE_BC, 6000);
    c = psl_step(&s, 6000 + PSL_LISTEN_MS);
    assert(c.serve == 1 && c.cable == 1 && c.server_ip == CABLE_A);
    assert(psl_pack(&s, pkt) == PSL_PACKET);
    /* And without it there is no Wi-Fi server to fall back to. */
    psl_set_cable(&s, PSL_CABLE_NONE, 0, 0, 8000);
    c = psl_step(&s, 20000);
    assert(c.serve == 0 && c.settled == 0 && c.cable == 0);
    /* Waiting for a cable that is coming is a hold all the same, and says so. */
    psl_set_cable(&s, PSL_CABLE_COMING, 0, 0, 21000);
    c = psl_step(&s, 21000);
    assert(c.held == 1 && c.settled == 0 && c.serve == 0 && c.announce == 0);
    c = psl_step(&s, 21000 + PSL_CABLE_HOLD_MS - 1);
    assert(c.held == 1);
    c = psl_step(&s, 21000 + PSL_CABLE_HOLD_MS);
    assert(c.held == 0 && c.settled == 0 && c.serve == 0);

    /* A Wi-Fi server that loses its address stops. */
    psl w;
    make_auto(&w, WIFI_A, 5, 0, 0);
    assert(psl_step(&w, PSL_LISTEN_MS).serve == 1);
    psl_set_self(&w, 0);
    c = psl_step(&w, 2000);
    assert(c.serve == 0 && c.settled == 0);
}

static void hold_never_applies_to_sessions(void) {
    psl h, j;
    psl_init(&h, PSL_HOST, WIFI_A, 5, 0);
    psl_init(&j, PSL_JOIN, WIFI_B, 5, WIFI_A);
    psl_active(&h, 1, 0, 0);
    psl_active(&j, 1, 0, 0);
    psl_set_cable(&h, PSL_CABLE_COMING, 0, 0, 100);
    psl_set_cable(&j, PSL_CABLE_COMING, 0, 0, 100);
    psl_active(&h, 0, 0, 200);
    psl_active(&j, 0, 0, 200);
    psl_active(&h, 1, 0, 300);
    psl_active(&j, 1, 0, 300);
    psl_choice c = psl_step(&h, 400);
    assert(c.held == 0 && c.serve == 1 && c.settled == 1);
    c = psl_step(&j, 400);
    assert(c.held == 0 && c.settled == 1 && c.server_ip == WIFI_A);

    /* Nor to a grouped automatic device. */
    psl g;
    make_auto(&g, WIFI_A, 5, 1, 0);
    assert(psl_step(&g, PSL_LISTEN_MS).serve == 1);
    psl_set_cable(&g, PSL_CABLE_COMING, 0, 0, 2000);
    c = psl_step(&g, 2000);
    assert(c.held == 0 && c.serve == 1);
}

/* Two devices that hear each other on the Wi-Fi and are then cabled: both move, one serves, the other follows it. */
static void two_cabled_devices_one_server(void) {
    psl d[2];
    const uint32_t wifi[2] = {WIFI_A, WIFI_B};
    const uint32_t cable[2] = {CABLE_A, CABLE_B};
    const uint32_t ties[2] = {7, 3};
    for (int i = 0; i < 2; i++) {
        make_auto(&d[i], wifi[i], ties[i], 0, 0);
        psl_set_net(&d[i], ip4(192, 168, 1, 255));
    }
    psl_choice c[2];
    for (int64_t now = 0; now <= 12000; now += 100) {
        if (now == 3000) {
            for (int i = 0; i < 2; i++) psl_set_cable(&d[i], PSL_CABLE_COMING, 0, 0, now);
        }
        if (now == 4000) {
            for (int i = 0; i < 2; i++) psl_set_cable(&d[i], PSL_CABLE_UP, cable[i], CABLE_BC, now);
        }
        uint8_t pkt[2][PSL_PACKET];
        size_t len[2];
        for (int i = 0; i < 2; i++) {
            c[i] = psl_step(&d[i], now);
            len[i] = c[i].announce ? psl_pack(&d[i], pkt[i]) : 0;
        }
        /* A packet reaches the other only when both are on the same network, from the sender's address there. */
        for (int i = 0; i < 2; i++) {
            const int j = 1 - i;
            if (len[j] > 0 && c[j].cable == c[i].cable) psl_heard(&d[i], pkt[j], len[j], c[j].self, now);
        }
        if (now == 2900) {
            assert(c[0].settled && c[1].settled && c[0].cable == 0 && c[0].server_ip == c[1].server_ip);
        }
    }
    assert(c[0].cable == 1 && c[1].cable == 1);
    assert(c[0].serve + c[1].serve == 1);
    const int server = c[0].serve ? 0 : 1;
    assert(server == 1);   /* the lower tie */
    for (int i = 0; i < 2; i++) assert(c[i].settled && c[i].server_ip == cable[server] && c[i].self == cable[i]);
}

int main(void) {
    pack_parse_roundtrip();
    junk_ignored();
    alone_serves_after_listen();
    follows_announced_server();
    both_start_one_yields();
    equal_tie_lower_ip_wins();
    grouped_server_keeps();
    session_beats_auto();
    grouped_beats_ungrouped();
    server_expires_reelect();
    linger_then_stop();
    host_and_join_fixed();
    host_and_join_parked();
    parked_is_silent();
    inactive_is_silent();
    server_with_users_never_yields();
    linger_holds_while_users();
    three_devices_one_server();
    three_devices_at_once();
    server_says_goodbye();
    goodbye_packet();
    own_tie_ignored();
    off_subnet_ignored();
    cable_coming_holds_wifi_server();
    cable_coming_blocks_serving();
    cable_coming_hold_expires();
    one_hold_per_coming_episode();
    freed_hold_only_after_a_dropped_cable();
    stage_one_rules_on_the_cable();
    cable_none_ends_hold();
    cable_up_without_self_is_none();
    cable_up_ends_hold_and_switches();
    cable_up_kept_off_while_grouped();
    cable_up_switches_once_ungrouped();
    cable_mode_ignores_wifi_sources();
    wifi_mode_ignores_cable_sources();
    cable_down_back_to_wifi();
    cable_gone_moves_a_grouped_device();
    choice_self_is_cable_self();
    session_on_cable_by_self();
    no_wifi_self_never_serves();
    hold_never_applies_to_sessions();
    two_cabled_devices_one_server();
    printf("ALL PASS\n");
    return 0;
}
