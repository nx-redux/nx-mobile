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

/* The finder and the election for PSP ad hoc over Wi-Fi; see psl_finder.h. */

#include "psl_finder.h"

#include <string.h>

static uint32_t host_order(uint32_t ip_net) {
    const uint8_t* b = (const uint8_t*)&ip_net;
    return ((uint32_t)b[0] << 24) | ((uint32_t)b[1] << 16) | ((uint32_t)b[2] << 8) | b[3];
}

typedef struct {
    int session, grouped;
    uint32_t tie, ip;
} rank;

/* Does a outrank b: a session's server first, then a grouped device, then the lower (tie, ip). */
static int outranks(rank a, rank b) {
    if (a.session != b.session) return a.session;
    if (a.grouped != b.grouped) return a.grouped;
    if (a.tie != b.tie) return a.tie < b.tie;
    return host_order(a.ip) < host_order(b.ip);
}

static rank peer_rank(const psl_peer* p) {
    rank r = {(p->flags & PSL_F_SESSION) != 0, (p->flags & PSL_F_GROUPED) != 0, p->tie, p->ip};
    return r;
}

static rank self_rank(const psl* s) {
    rank r = {s->mode == PSL_HOST, s->grouped, s->tie, s->self_ip};
    return r;
}

void psl_init(psl* s, int mode, uint32_t self_ip, uint32_t tie, uint32_t host_ip) {
    memset(s, 0, sizeof(*s));
    s->mode = mode;
    s->self_ip = self_ip;
    s->tie = tie;
    s->host_ip = host_ip;
}

void psl_set_self(psl* s, uint32_t self_ip) { s->self_ip = self_ip; }

void psl_active(psl* s, int active, int grouped, int64_t now_ms) {
    if (active) {
        if (!s->active) {
            s->listen_from_ms = now_ms;
            s->active = 1;
        }
        s->grouped = grouped;
    } else {
        if (s->active) s->left_ms = now_ms;
        s->active = 0;
        s->grouped = 0;
    }
}

void psl_park(psl* s, int parked) {
    s->parked = parked;
    if (parked) {
        s->serving = 0;
        s->active = 0;
        s->grouped = 0;
    }
}

void psl_heard(psl* s, const uint8_t* pkt, size_t len, uint32_t from_ip, int64_t now_ms) {
    if (s->mode != PSL_AUTO || len != PSL_PACKET || memcmp(pkt, "NXPL", 4) != 0 || pkt[4] != PSL_VERSION ||
        from_ip == s->self_ip)
        return;
    psl_peer* slot = NULL;
    for (int i = 0; i < PSL_MAX_PEERS; i++) {
        if (s->peers[i].used && s->peers[i].ip == from_ip) { slot = &s->peers[i]; break; }
    }
    if (!slot) {
        for (int i = 0; i < PSL_MAX_PEERS && !slot; i++) {
            if (!s->peers[i].used) slot = &s->peers[i];
        }
    }
    if (!slot) {  /* full: replace the one heard longest ago */
        slot = &s->peers[0];
        for (int i = 1; i < PSL_MAX_PEERS; i++) {
            if (s->peers[i].heard_ms < slot->heard_ms) slot = &s->peers[i];
        }
    }
    slot->used = 1;
    slot->ip = from_ip;
    slot->flags = pkt[5];
    slot->tie = ((uint32_t)pkt[12] << 24) | ((uint32_t)pkt[13] << 16) | ((uint32_t)pkt[14] << 8) | pkt[15];
    slot->heard_ms = now_ms;
    if (slot->flags & PSL_F_SERVING) {
        s->heard_server = 1;
        s->server_heard_ms = now_ms;
    }
}

size_t psl_pack(const psl* s, uint8_t out[PSL_PACKET]) {
    int flags;
    if (s->mode == PSL_HOST) {
        flags = PSL_F_SERVING | PSL_F_SESSION;
    } else if (s->mode == PSL_AUTO && !s->parked && (s->active || s->serving)) {
        flags = (s->serving ? PSL_F_SERVING : 0) | (s->grouped ? PSL_F_GROUPED : 0);
    } else {
        return 0;
    }
    memset(out, 0, PSL_PACKET);
    memcpy(out, "NXPL", 4);
    out[4] = PSL_VERSION;
    out[5] = (uint8_t)flags;
    memcpy(out + 8, &s->self_ip, 4);
    out[12] = (uint8_t)(s->tie >> 24);
    out[13] = (uint8_t)(s->tie >> 16);
    out[14] = (uint8_t)(s->tie >> 8);
    out[15] = (uint8_t)s->tie;
    return PSL_PACKET;
}

/* The best serving peer heard within the expiry, or NULL. */
static const psl_peer* best_server(const psl* s, int64_t now_ms) {
    const psl_peer* best = NULL;
    for (int i = 0; i < PSL_MAX_PEERS; i++) {
        const psl_peer* p = &s->peers[i];
        if (!p->used || !(p->flags & PSL_F_SERVING) || now_ms - p->heard_ms >= PSL_EXPIRE_MS) continue;
        if (!best || outranks(peer_rank(p), peer_rank(best))) best = p;
    }
    return best;
}

psl_choice psl_step(psl* s, int64_t now_ms) {
    psl_choice c = {0, 0, 0, 0};
    if (s->mode == PSL_HOST) {
        c.server_ip = s->self_ip;
        c.serve = c.settled = c.announce = 1;
        return c;
    }
    if (s->mode == PSL_JOIN) {
        c.server_ip = s->host_ip;
        c.settled = 1;
        return c;
    }
    if (s->parked) {
        s->serving = 0;
        return c;
    }
    if (!s->active) {
        if (s->serving && now_ms - s->left_ms >= PSL_LINGER_MS) s->serving = 0;
        if (s->serving) {
            c.server_ip = s->self_ip;
            c.serve = c.settled = c.announce = 1;
        }
        return c;
    }
    c.announce = 1;
    const psl_peer* best = best_server(s, now_ms);
    if (s->serving && best && !s->grouped && outranks(peer_rank(best), self_rank(s))) s->serving = 0;
    if (!s->serving) {
        if (best) {
            c.server_ip = best->ip;
            c.settled = 1;
            return c;
        }
        int64_t from = s->listen_from_ms;
        if (s->heard_server && s->server_heard_ms + PSL_EXPIRE_MS > from) from = s->server_heard_ms + PSL_EXPIRE_MS;
        if (now_ms - from < PSL_LISTEN_MS) return c;
        s->serving = 1;
    }
    c.server_ip = s->self_ip;
    c.serve = c.settled = 1;
    return c;
}
