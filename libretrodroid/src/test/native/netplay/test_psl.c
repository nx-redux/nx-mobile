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

/* Both serve, each hears the other; returns whether a (the lower rank) kept serving and b followed. */
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

static void equal_tie_lower_ip_wins(void) {
    psl a, b;
    /* 10.0.0.2 is lower than 10.0.1.1 in host order, but higher as a little-endian word. */
    election(ip4(10, 0, 1, 1), 4, ip4(10, 0, 0, 2), 4, &a, &b);
    assert(psl_step(&b, 1100).serve == 1);
    psl_choice c = psl_step(&a, 1100);
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
    parked_is_silent();
    inactive_is_silent();
    printf("ALL PASS\n");
    return 0;
}
