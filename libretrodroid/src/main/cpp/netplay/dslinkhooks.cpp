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

#include "dslinkhooks.h"

#include <algorithm>
#include <atomic>
#include <cerrno>
#include <chrono>
#include <cstdint>
#include <cstdlib>
#include <cstdio>
#include <cstring>
#include <android/multinetwork.h>
#include <mutex>
#include <string>

#include "../environment.h"
#include "../log.h"

extern "C" {
#include "ndl_link.h"
}

namespace libretrodroid::dslink {

namespace {

// The Wi-Fi network's handle (0 = none). The default network can be mobile data, which would send the room's
// replies out over the carrier instead of the LAN.
std::atomic<int64_t> wifiNetwork { 0 };

void bindToWifi(int fd, void*) {
    int64_t handle = wifiNetwork.load();
    if (handle == 0) return;
    int rc = android_setsocknetwork((net_handle_t) handle, fd);
    if (rc != 0) LOGW("DS link: cannot bind a socket to the Wi-Fi network (errno %d)", errno);
}

constexpr uint16_t ROOM_PORT = 24874;
constexpr uint16_t SEARCH_PORT = 24875;

struct Settings {
    ndl_mode mode = NDL_OFF;
    std::string ip;
    uint16_t port = ROOM_PORT;
    std::string name;
    std::string broadcast;

    bool operator==(const Settings& o) const {
        return mode == o.mode && ip == o.ip && port == o.port && name == o.name && broadcast == o.broadcast;
    }
};

nl_netpacket_callback callbacks {};
bool registered = false;
// GL thread only: the core had start() and not yet stop().
bool coreStarted = false;
unsigned lastRadio = NDL_RADIO_OFF;
Settings current;

// Written only by the GL thread, under linkMutex; the GL thread reads it without the lock, the other threads with it,
// so a JNI call never sees it freed.
std::mutex linkMutex;
ndl_link* theLink = nullptr;

// Frame timing diagnostics, emulation thread only: the core waits for the others inside retro_run, so network delay
// shows up as long frames and a long span between its first and last poll.
using Clock = std::chrono::steady_clock;

struct Stats {
    bool active = false;                  // a frame is open
    Clock::time_point frameStart, firstPoll, lastPoll, windowStart;
    bool windowOpen = false;
    unsigned polls = 0;                   // this frame
    uint64_t framePktsIn = 0, framePktsOut = 0;

    uint64_t frames = 0, over17 = 0, over33 = 0;
    uint64_t sumUs = 0, maxUs = 0, sumPolls = 0, maxPolls = 0, maxSpanUs = 0;
    uint64_t pktsIn = 0, pktsOut = 0, bytesIn = 0, bytesOut = 0;

    void clearWindow() {
        frames = over17 = over33 = 0;
        sumUs = maxUs = sumPolls = maxPolls = maxSpanUs = 0;
        pktsIn = pktsOut = bytesIn = bytesOut = 0;
    }
} stats;

// The stats line comes every second while the link struggles (a slow frame, a drop, a slow round trip), else every
// 10 s, so the log stays readable on a good link.
constexpr int64_t STATS_QUIET_US = 10000000;
constexpr uint64_t STATS_SLOW_FRAME_US = 33000;
constexpr int STATS_SLOW_RTT_MS = 50;
Clock::time_point lastStatsLine {};

#ifdef NX_DSLINK_TRACE
// Stall trace, emulation thread only: the last core packets in either direction, dumped when one frame runs long, to
// show which packet a waiting core never got. Built only with NX_DSLINK_TRACE: it costs a few stores per packet.
struct TraceEntry {
    int64_t us;          // steady clock
    bool out;            // sent by the core (peer = destination) or delivered to it (peer = sender)
    uint16_t peer;
    int type;            // melonDS packet type, -1 when too short to carry one
    int aid;
    uint32_t stamp;      // low 32 bits of the melonDS timestamp
    uint32_t len;
};

constexpr size_t TRACE_SIZE = 64;
constexpr uint64_t TRACE_FRAME_US = 100000;
constexpr int TRACE_DUMPS = 3;
constexpr int64_t TRACE_WINDOW_US = 10000000;

struct Trace {
    TraceEntry ring[TRACE_SIZE];
    size_t head = 0, count = 0;
    int64_t dumps[TRACE_DUMPS] = {};
    int dumpsMade = 0;

    void clear() { head = 0; count = 0; }

    void add(bool out, uint16_t peer, const uint8_t* buf, size_t len) {
        TraceEntry& e = ring[(head + count) % TRACE_SIZE];
        if (count == TRACE_SIZE) head = (head + 1) % TRACE_SIZE; else count++;
        e.us = nowUs();
        e.out = out;
        e.peer = peer;
        e.len = (uint32_t) len;
        // [timestamp u64 BE][aid u8][type u8]: the low half of the timestamp is enough to tell packets apart.
        if (buf != nullptr && len >= 10) {
            e.stamp = ((uint32_t) buf[4] << 24) | ((uint32_t) buf[5] << 16) | ((uint32_t) buf[6] << 8) | buf[7];
            e.aid = buf[8];
            e.type = buf[9];
        } else {
            e.stamp = 0;
            e.aid = -1;
            e.type = -1;
        }
    }

    static int64_t nowUs() {
        return std::chrono::duration_cast<std::chrono::microseconds>(Clock::now().time_since_epoch()).count();
    }

    // At most TRACE_DUMPS dumps per TRACE_WINDOW_US; the ring is cleared either way.
    bool allowDump(int64_t now) {
        int recent = 0;
        for (int i = 0; i < dumpsMade && i < TRACE_DUMPS; i++) {
            if (now - dumps[i] < TRACE_WINDOW_US) recent++;
        }
        if (recent >= TRACE_DUMPS) return false;
        dumps[dumpsMade % TRACE_DUMPS] = now;
        dumpsMade++;
        return true;
    }
} trace;

void dumpTrace(uint64_t frameUs, unsigned polls) {
    int64_t now = Trace::nowUs();
    if (trace.count > 0 && trace.allowDump(now)) {
        unsigned sent = 0, received = 0;
        int64_t lastIn = 0, lastOut = 0;
        for (size_t i = 0; i < trace.count; i++) {
            const TraceEntry& e = trace.ring[(trace.head + i) % TRACE_SIZE];
            if (e.out) { sent++; lastOut = e.us; } else { received++; lastIn = e.us; }
            LOGI("DS link: trace t=%lld %s peer=%u type=%d aid=%d ts=%u len=%u", (long long) (e.us - now),
                 e.out ? "out" : "in", (unsigned) e.peer, e.type, e.aid, (unsigned) e.stamp, (unsigned) e.len);
        }
        LOGI("DS link: trace summary frameMs=%.1f polls=%u entries=%zu sent=%u received=%u lastInAgoMs=%.1f "
             "lastOutAgoMs=%.1f (t in us before this line)",
             frameUs / 1000.0, polls, trace.count, sent, received, received ? (now - lastIn) / 1000.0 : -1.0,
             sent ? (now - lastOut) / 1000.0 : -1.0);
    }
    trace.clear();
}
#endif

void destroyLink() {
    ndl_link* old;
    {
        std::lock_guard<std::mutex> guard(linkMutex);
        old = theLink;
        theLink = nullptr;
    }
    // Outside the lock: it joins the network thread, and a JNI caller must not wait for that.
    if (old != nullptr) {
        ndl_destroy(old);
        LOGI("DS link: ended");
    }
}

void endCoreSession() {
    if (!coreStarted) return;
    coreStarted = false;   // first: nothing reaches the core from here on
    stats.active = false;
    stats.windowOpen = false;
#ifdef NX_DSLINK_TRACE
    trace.clear();
#endif
    if (callbacks.stop != nullptr) callbacks.stop();
}

void deliver(const uint8_t* buf, size_t len, uint16_t sender, void*) {
    if (stats.active) { stats.pktsIn++; stats.bytesIn += len; }
#ifdef NX_DSLINK_TRACE
    if (coreStarted) trace.add(false, sender, buf, len);
#endif
    if (coreStarted && callbacks.receive != nullptr) callbacks.receive(buf, len, sender);
}

// The core sends unreliable, unsequenced packets with a flush hint: every send is a datagram at once anyway.
void coreSend(int flags, const void* buf, size_t len, uint16_t clientId) {
    (void) flags;
    if (!coreStarted) return;
    if (buf == nullptr || len == 0) return;   // the core's flush hint, not a packet
    if (stats.active) { stats.pktsOut++; stats.bytesOut += len; }
#ifdef NX_DSLINK_TRACE
    trace.add(true, clientId, (const uint8_t*) buf, len);
#endif
    ndl_send(theLink, buf, len, clientId);
}

// melonDS DS waits inside retro_run for the others' packets and polls until they arrive: hand over all of them.
void corePollReceive() {
    if (!coreStarted) return;
    if (stats.active) {
        Clock::time_point now = Clock::now();
        if (stats.polls++ == 0) stats.firstPoll = now;
        stats.lastPoll = now;
    }
    ndl_take(theLink, deliver, nullptr);
}

const std::string* find(const std::vector<Variable>& vars, const char* key) {
    for (const auto& v : vars) {
        if (v.key == key) return &v.value;
    }
    return nullptr;
}

Settings read(const std::vector<Variable>& vars) {
    Settings s;
    const std::string* mode = find(vars, "nx_nds_link");
    if (mode == nullptr) return s;
    if (*mode == "host") s.mode = NDL_HOST;
    else if (*mode == "join") s.mode = NDL_JOIN;
    else if (*mode == "auto") s.mode = NDL_AUTO;
    else return s;
    if (const std::string* ip = find(vars, "nx_nds_link_ip")) s.ip = *ip;
    if (const std::string* port = find(vars, "nx_nds_link_port")) {
        long p = std::strtol(port->c_str(), nullptr, 10);
        if (p > 0 && p <= 65535) s.port = (uint16_t) p;
    }
    if (const std::string* name = find(vars, "nx_nds_link_name")) s.name = *name;
    if (const std::string* broadcast = find(vars, "nx_nds_link_broadcast")) s.broadcast = *broadcast;
    return s;
}

const char* modeName(ndl_mode m) {
    switch (m) {
        case NDL_HOST: return "host";
        case NDL_JOIN: return "join";
        case NDL_AUTO: return "auto";
        default: return "off";
    }
}

template <size_t N>
void copyField(char (&dst)[N], const std::string& src) {
    std::strncpy(dst, src.c_str(), N - 1);
    dst[N - 1] = '\0';
}

}

bool setNetpacket(const nl_netpacket_callback* cb) {
    callbacks = cb != nullptr ? *cb : nl_netpacket_callback {};
    registered = callbacks.start != nullptr && callbacks.receive != nullptr;
    LOGI("DS link: netpacket interface %s", registered ? "registered" : "cleared");
    return true;
}

void forgetCore() {
    // The core is unloaded or about to be: never call into it again.
    callbacks = nl_netpacket_callback {};
    registered = false;
    coreStarted = false;
    lastRadio = NDL_RADIO_OFF;
    destroyLink();
    current = Settings {};
}

void configure(const std::vector<Variable>& vars) {
    Settings wanted = read(vars);
    if (wanted == current) return;
    current = wanted;

    // Any change starts over: the core stops before its link goes, so it never sends into a freed one.
    endCoreSession();
    destroyLink();
    if (wanted.mode == NDL_OFF) return;

    ndl_config cfg {};
    cfg.mode = wanted.mode;
    copyField(cfg.name, wanted.name);
    cfg.room_port = wanted.port;
    cfg.search_port = SEARCH_PORT;
    copyField(cfg.broadcast, wanted.broadcast);
    copyField(cfg.host_ip, wanted.ip);
    cfg.max_members = NDL_MAX_MEMBERS;
    cfg.clock = nullptr;
    cfg.bind_socket = bindToWifi;
    cfg.bind_ctx = nullptr;

    ndl_link* created = ndl_create(&cfg);
    if (created == nullptr) {
        LOGE("DS link: cannot start %s on port %u", modeName(wanted.mode), (unsigned) wanted.port);
        return;
    }
    ndl_radio_changed(created, (ndl_radio) lastRadio);
    {
        std::lock_guard<std::mutex> guard(linkMutex);
        theLink = created;
    }
    LOGI("DS link: %s on port %u", modeName(wanted.mode), (unsigned) wanted.port);
}

void setNetwork(int64_t handle) {
    wifiNetwork.store(handle);
}

void radio(unsigned state) {
    if (state > NDL_RADIO_HOSTING) return;
    lastRadio = state;
    ndl_radio_changed(theLink, (ndl_radio) state);
}

void preFrame() {
    const bool in = ndl_in_room(theLink);
    if (!coreStarted && in && registered) {
        int id = ndl_client_id(theLink);
        if (id > 0) {
            coreStarted = true;
            // melonDS DS ignores this id and learns the DS host from each command's sender.
            callbacks.start((uint16_t) id, coreSend, corePollReceive);
            LOGI("DS link: core started as member %d of %d", id, ndl_member_count(theLink));
        }
    } else if (coreStarted && !in) {
        endCoreSession();   // the room ended or this phone left: the game plays on alone
        LOGI("DS link: core stopped");
    }
    if (coreStarted) ndl_take(theLink, deliver, nullptr);
}

void frameBegin() {
    if (!coreStarted) return;
    stats.frameStart = Clock::now();
    if (!stats.windowOpen) {
        stats.windowOpen = true;
        stats.windowStart = stats.frameStart;
        stats.clearWindow();
    }
    stats.polls = 0;
    stats.active = true;
}

void frameEnd() {
    if (!stats.active) return;
    stats.active = false;
    Clock::time_point now = Clock::now();
    auto us = [](Clock::duration d) { return (uint64_t) std::chrono::duration_cast<std::chrono::microseconds>(d).count(); };
    uint64_t frameUs = us(now - stats.frameStart);
    uint64_t spanUs = stats.polls > 1 ? us(stats.lastPoll - stats.firstPoll) : 0;
    stats.frames++;
    if (frameUs > 17000) stats.over17++;
    if (frameUs > 33000) stats.over33++;
    stats.sumUs += frameUs;
    stats.maxUs = std::max(stats.maxUs, frameUs);
    stats.sumPolls += stats.polls;
    stats.maxPolls = std::max<uint64_t>(stats.maxPolls, stats.polls);
    stats.maxSpanUs = std::max(stats.maxSpanUs, spanUs);
#ifdef NX_DSLINK_TRACE
    if (frameUs > TRACE_FRAME_US) dumpTrace(frameUs, stats.polls);
#endif

    uint64_t windowUs = us(now - stats.windowStart);
    if (windowUs < 1000000 || stats.frames == 0) return;
    int rttMin, rttAvg, rttMax;
    ndl_stats_rtt(theLink, &rttMin, &rttAvg, &rttMax);
    ndl_drop_counts drops {};
    ndl_stats_drops(theLink, &drops);
    stats.windowOpen = false;
    const bool notable = stats.maxUs > STATS_SLOW_FRAME_US || rttMax > STATS_SLOW_RTT_MS || drops.queue != 0 ||
                         drops.unknown != 0 || drops.wrong_src != 0 || drops.bad != 0 || drops.echo != 0 ||
                         drops.dest != 0;
    if (!notable && lastStatsLine != Clock::time_point {} && us(now - lastStatsLine) < (uint64_t) STATS_QUIET_US) return;
    lastStatsLine = now;
    char dropText[160] = "";
    {
        const struct { const char* name; uint32_t n; } kinds[] = {
            { "queue", drops.queue }, { "src", drops.unknown }, { "wrongsrc", drops.wrong_src },
            { "bad", drops.bad }, { "echo", drops.echo }, { "dest", drops.dest },
        };
        size_t at = 0;
        for (const auto& k : kinds) {
            if (k.n == 0 || at >= sizeof(dropText)) continue;
            int w = std::snprintf(dropText + at, sizeof(dropText) - at, "%s%s:%u", at ? "," : " drops=", k.name,
                                  (unsigned) k.n);
            if (w > 0) at += (size_t) w;
        }
    }
    double sec = (double) windowUs / 1e6;
    LOGI("DS link: stats frames=%llu over17=%llu over33=%llu maxFrameMs=%.1f avgFrameMs=%.2f avgPolls=%.1f maxPolls=%llu "
         "maxWaitMs=%.1f pktsIn/s=%.0f pktsOut/s=%.0f bytesIn/s=%.0f bytesOut/s=%.0f members=%d rttMs=%d/%d/%d qHigh=%u%s",
         (unsigned long long) stats.frames, (unsigned long long) stats.over17, (unsigned long long) stats.over33,
         stats.maxUs / 1000.0, stats.sumUs / 1000.0 / stats.frames, (double) stats.sumPolls / stats.frames,
         (unsigned long long) stats.maxPolls, stats.maxSpanUs / 1000.0, stats.pktsIn / sec, stats.pktsOut / sec,
         stats.bytesIn / sec, stats.bytesOut / sec, ndl_member_count(theLink), rttMin, rttAvg, rttMax, (unsigned) drops.queue_hwm, dropText);
}

void stopCore() {
    endCoreSession();
    destroyLink();
    current = Settings {};
}

void shutdown() {
    std::lock_guard<std::mutex> guard(linkMutex);
    ndl_leave_now(theLink);
}

bool inRoom() {
    std::lock_guard<std::mutex> guard(linkMutex);
    return ndl_in_room(theLink);
}

void leaveNow() {
    std::lock_guard<std::mutex> guard(linkMutex);
    ndl_leave_now(theLink);
}

void rejoin() {
    std::lock_guard<std::mutex> guard(linkMutex);
    ndl_rejoin(theLink);
}

}
