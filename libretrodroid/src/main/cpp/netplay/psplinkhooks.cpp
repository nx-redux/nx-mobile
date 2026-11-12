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

#include "psplinkhooks.h"
#include "dslinkhooks.h"
#include "nx_3ds_link.h"
#include "nx_psp_link.h"

#include <arpa/inet.h>
#include <cerrno>
#include <chrono>
#include <condition_variable>
#include <cstring>
#include <mutex>
#include <netinet/in.h>
#include <random>
#include <string>
#include <sys/socket.h>
#include <thread>
#include <unistd.h>

#include "../environment.h"
#include "../log.h"

extern "C" {
#include "psl_finder.h"
}

namespace libretrodroid::psplink {

namespace {

constexpr int MODE_OFF = -1;
constexpr int TICK_MS = PSL_ANNOUNCE_MS / 5;
constexpr int MAX_DRAIN = 32;   // packets read per tick: a flood cannot keep the thread from stepping

struct Settings {
    int mode = MODE_OFF;     // PSL_AUTO / PSL_HOST / PSL_JOIN
    uint32_t hostIp = 0;     // join: the session host's address; network order, as every address here
    uint32_t broadcast = 0;
    uint32_t self = 0;       // this device's address on the Wi-Fi the sockets are routed to

    bool operator==(const Settings& o) const {
        return mode == o.mode && hostIp == o.hostIp && broadcast == o.broadcast && self == o.self;
    }
};

// Guards everything below except `registered`, which only the lifecycle calls touch (under lifeMutex). Never held
// while calling into the core or into dslink: dslink calls the adapter with its coreRoomMutex held, and the adapter
// takes this one, so the order is coreRoomMutex, then this.
std::mutex mutex;
std::condition_variable changed;   // published, generation or stopping changed
Settings settings;
nx_psp_link_interface core {};
bool hasCore = false;
psl election;
bool parked = false;
// The core's last active report, kept while parked so it can be replayed at unpark: psl_active must not be told
// "active" while parked, or the listen window would already have run out when the device comes back.
int coreActive = 0, coreGrouped = 0;
// Automatic route, while parked: the game has been out of ad hoc since the leave, so its next trigger brings the
// device back (a game still in ad hoc at the leave must leave it first).
bool outSinceLeave = false;
// The choice server() answers from, and the core has been told about (serve, relogin).
psl_choice published {};
// Bumped whenever the finder stops: a waiting server() gives up and answers 0.
unsigned generation = 0;
bool rebind = false;
bool stopping = false;
std::thread finder;

// Serialises setCore, configure and forgetCore, which register with dslink and start and join the finder.
std::mutex lifeMutex;
bool registered = false;

int64_t nowMs() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();
}

uint32_t processTie() {
    static const uint32_t tie = [] {
        std::random_device rd;
        return (uint32_t) rd();
    }();
    return tie;
}

std::string text(uint32_t ip) {
    char buf[INET_ADDRSTRLEN] = "?";
    in_addr a {};
    a.s_addr = ip;
    inet_ntop(AF_INET, &a, buf, sizeof(buf));
    return buf;
}

const char* modeName(int mode) {
    switch (mode) {
        case PSL_AUTO: return "auto";
        case PSL_HOST: return "host";
        case PSL_JOIN: return "join";
        default: return "off";
    }
}

const std::string* find(const std::vector<Variable>& vars, const char* key) {
    for (const auto& v : vars) {
        if (v.key == key) return &v.value;
    }
    return nullptr;
}

uint32_t address(const std::vector<Variable>& vars, const char* key) {
    const std::string* value = find(vars, key);
    in_addr a {};
    if (value == nullptr || inet_pton(AF_INET, value->c_str(), &a) != 1) return 0;
    return a.s_addr;
}

Settings read(const std::vector<Variable>& vars) {
    Settings s;
    const std::string* mode = find(vars, "nx_psp_link");
    if (mode == nullptr) return s;
    if (*mode == "auto") s.mode = PSL_AUTO;
    else if (*mode == "host") s.mode = PSL_HOST;
    else if (*mode == "join") s.mode = PSL_JOIN;
    else return s;
    s.hostIp = address(vars, "nx_psp_link_ip");
    s.broadcast = address(vars, "nx_psp_link_broadcast");
    s.self = address(vars, "nx_psp_link_self");
    return s;
}

// A fresh election for the settings, carrying over what the core has already reported. Under mutex.
void resetElectionLocked() {
    psl_init(&election, settings.mode == MODE_OFF ? PSL_AUTO : settings.mode, settings.self, processTie(), settings.hostIp);
    if (parked) psl_park(&election, 1);
    else if (coreActive) psl_active(&election, 1, coreGrouped, nowMs());
}

void runFinder();

// Host and join run the finder for the life of the link; automatic starts it at the game's first ad hoc trigger. Under
// mutex.
void maybeStartLocked() {
    if (stopping || !hasCore || settings.mode == MODE_OFF || finder.joinable()) return;
    if (settings.mode == PSL_AUTO && !election.active) return;
    finder = std::thread(runFinder);
}

// Ends the finder and waits for it. With the core still there the finder first takes back what it told the core
// (serve(0), relogin()); without it nothing more is called. Leaves `stopping` set, so no report starts a finder on the
// old settings: the caller clears it with the new ones in place. Under lifeMutex, not mutex.
void stopFinder() {
    std::thread t;
    {
        std::lock_guard<std::mutex> guard(mutex);
        stopping = true;
        generation++;
        changed.notify_all();
        t = std::move(finder);
    }
    if (t.joinable()) t.join();
}

int openSocket() {
    int fd = socket(AF_INET, SOCK_DGRAM | SOCK_CLOEXEC, 0);
    if (fd < 0) {
        LOGW("PSP link: cannot open the finder's socket (errno %d)", errno);
        return -1;
    }
    int one = 1;
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &one, sizeof(one));
    setsockopt(fd, SOL_SOCKET, SO_BROADCAST, &one, sizeof(one));
    dslink::routeCoreSocket(fd, 0);
    sockaddr_in addr {};
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_ANY);
    addr.sin_port = htons(PSL_PORT);
    if (bind(fd, (sockaddr*) &addr, sizeof(addr)) != 0) {
        LOGW("PSP link: cannot bind the finder to port %d (errno %d)", PSL_PORT, errno);
        close(fd);
        return -1;
    }
    return fd;
}

void logChoice(const psl_choice& was, const psl_choice& now) {
    if (now.serve != was.serve) LOGI("PSP link: serve %s", now.serve ? "on" : "off");
    if (now.settled != was.settled || now.server_ip != was.server_ip) {
        if (now.settled) LOGI("PSP link: server %s%s", text(now.server_ip).c_str(), now.serve ? " (this device)" : "");
        else LOGI("PSP link: no server");
    }
}

void runFinder() {
    // Without a socket the election still runs: host and join need none, and an automatic device then serves alone.
    int fd = openSocket();
    int64_t lastAnnounce = 0;
    bool announced = false;
    std::unique_lock<std::mutex> lock(mutex);
    while (!stopping) {
        bool route = rebind;
        rebind = false;
        lock.unlock();

        if (fd >= 0 && route) dslink::routeCoreSocket(fd, 0);
        struct Packet {
            uint8_t data[PSL_PACKET];
            ssize_t len;
            uint32_t from;
        } packets[MAX_DRAIN];
        int count = 0;
        while (fd >= 0 && count < MAX_DRAIN) {
            sockaddr_in from {};
            socklen_t fromLen = sizeof(from);
            // One byte over the packet size, so a longer datagram reads as longer and is refused, not cut to fit.
            uint8_t buf[PSL_PACKET + 1];
            ssize_t n = recvfrom(fd, buf, sizeof(buf), MSG_DONTWAIT, (sockaddr*) &from, &fromLen);
            if (n < 0) break;
            if (n != PSL_PACKET) continue;
            std::memcpy(packets[count].data, buf, PSL_PACKET);
            packets[count].len = n;
            packets[count].from = from.sin_addr.s_addr;
            count++;
        }

        lock.lock();
        if (stopping) break;
        const int64_t now = nowMs();
        for (int i = 0; i < count; i++) psl_heard(&election, packets[i].data, (size_t) packets[i].len, packets[i].from, now);
        const psl_choice choice = psl_step(&election, now);   // before psl_pack: it reads what the step updates
        uint8_t out[PSL_PACKET];
        size_t outLen = 0;
        if (!announced || now - lastAnnounce >= PSL_ANNOUNCE_MS) {
            outLen = psl_pack(&election, out);
            if (outLen > 0) {
                lastAnnounce = now;
                announced = true;
            }
        }
        const uint32_t broadcast = settings.broadcast;
        const psl_choice was = published;
        const nx_psp_link_interface link = core;
        const bool live = hasCore;
        const bool starting = choice.serve && !was.serve;
        const bool stoppingServer = !choice.serve && was.serve;
        const bool relogin = was.settled && (!choice.settled || choice.server_ip != was.server_ip);
        // A new server on this device is answered only once it listens, so a client never asks it too early.
        if (!starting) {
            published = choice;
            changed.notify_all();
        }
        lock.unlock();

        if (outLen > 0 && broadcast != 0 && fd >= 0) {
            sockaddr_in to {};
            to.sin_family = AF_INET;
            to.sin_addr.s_addr = broadcast;
            to.sin_port = htons(PSL_PORT);
            sendto(fd, out, outLen, 0, (sockaddr*) &to, sizeof(to));
        }
        logChoice(was, choice);
        if (live && starting) {
            link.serve(1);
            // Published even when stopping meanwhile, so the finder's exit stops this server again.
            std::lock_guard<std::mutex> guard(mutex);
            if (hasCore) {
                published = choice;
                changed.notify_all();
            }
        }
        if (live && stoppingServer) link.serve(0);
        if (live && relogin) link.relogin();

        lock.lock();
        changed.wait_for(lock, std::chrono::milliseconds(TICK_MS), [] { return stopping; });
    }

    // Stopping, the lock held. A finder ended by new settings takes back what it told the core; one ended because
    // the core is going (hasCore already false, published already cleared) calls nothing.
    const psl_choice was = published;
    const nx_psp_link_interface link = core;
    const bool live = hasCore;
    published = psl_choice {};
    changed.notify_all();
    lock.unlock();
    if (live) {
        logChoice(was, psl_choice {});
        if (was.serve) link.serve(0);
        if (was.settled) link.relogin();
    }
    if (fd >= 0) close(fd);
}

// What dslink is given as the core's room: the core's presence, leave and rejoin, with the election parked while the
// core stays out. dslink calls these with coreRoomMutex held.
nx_psp_link_interface coreCopy() {
    std::lock_guard<std::mutex> guard(mutex);
    return core;
}

int adapterPresence() {
    nx_psp_link_interface link = coreCopy();
    return link.presence != nullptr ? link.presence() : NX_3DS_LINK_OUT;
}

void adapterLeave() {
    nx_psp_link_interface link;
    {
        std::lock_guard<std::mutex> guard(mutex);
        parked = true;
        outSinceLeave = !coreActive;
        psl_park(&election, 1);
        link = core;
    }
    LOGI("PSP link: left");
    if (link.leave != nullptr) link.leave();
}

// Back from a leave, with the core's last report replayed at a fresh time, so the listen window starts now. Under mutex.
void unparkLocked() {
    parked = false;
    psl_park(&election, 0);
    if (coreActive) psl_active(&election, 1, coreGrouped, nowMs());
    maybeStartLocked();
    LOGI("PSP link: back");
}

// A session comes back; the automatic route waits for the game to re-enter ad hoc instead (coreActiveReport).
void adapterRejoin() {
    nx_psp_link_interface link;
    {
        std::lock_guard<std::mutex> guard(mutex);
        if (parked && settings.mode != PSL_AUTO) unparkLocked();
        link = core;
    }
    if (link.rejoin != nullptr) link.rejoin();
}

void adapterSetCable(int, const char*) {}

nx_3ds_link_interface adapter {adapterPresence, adapterLeave, adapterRejoin, adapterSetCable, nullptr};

// The core's half of the interface. Safe whether or not the link is configured: off, server() answers 0 at once.
int coreServer(uint32_t* ip, uint32_t* self) {
    std::unique_lock<std::mutex> lock(mutex);
    if (!hasCore || settings.mode == MODE_OFF) return 0;
    const unsigned start = generation;
    changed.wait_for(lock, std::chrono::milliseconds(PSL_LISTEN_MS + 200),
                     [start] { return published.settled || generation != start; });
    if (generation != start || !published.settled || published.server_ip == 0) return 0;
    if (ip != nullptr) *ip = published.server_ip;
    if (self != nullptr) *self = settings.self;
    return 1;
}

void coreActiveReport(int active, int grouped) {
    std::lock_guard<std::mutex> guard(mutex);
    if (!hasCore) return;
    active = active != 0;
    grouped = active && grouped != 0;
    const bool changedReport = active != coreActive || grouped != coreGrouped;
    // Kept even while off: settings that turn the link on later carry it into their election.
    coreActive = active;
    coreGrouped = grouped;
    if (settings.mode == MODE_OFF) return;
    if (changedReport) LOGI("PSP link: game %s", !active ? "left ad hoc" : grouped ? "in a group" : "in ad hoc");
    if (parked) {
        if (settings.mode != PSL_AUTO) return;
        if (!active) outSinceLeave = true;
        else if (outSinceLeave) unparkLocked();
        return;
    }
    psl_active(&election, active, grouped, nowMs());
    maybeStartLocked();
}

// Registers with dslink when there is a core and the link is on, and unregisters otherwise. Under lifeMutex.
void syncRegistration() {
    bool want;
    {
        std::lock_guard<std::mutex> guard(mutex);
        want = hasCore && settings.mode != MODE_OFF;
    }
    if (want == registered) return;
    dslink::setCoreRoom(want ? &adapter : nullptr);
    registered = want;
}

// The core goes: nothing calls into it once this returns. Under lifeMutex.
void dropCore() {
    if (registered) {
        dslink::setCoreRoom(nullptr);   // returns once no adapter call is in flight
        registered = false;
    }
    {
        std::lock_guard<std::mutex> guard(mutex);
        hasCore = false;
        published = psl_choice {};
        generation++;
        changed.notify_all();
    }
    stopFinder();
    std::lock_guard<std::mutex> guard(mutex);
    core = nx_psp_link_interface {};
    parked = false;
    coreActive = coreGrouped = 0;
    outSinceLeave = false;
    resetElectionLocked();
    stopping = false;
}

}

void setCore(nx_psp_link_interface* link) {
    std::lock_guard<std::mutex> life(lifeMutex);
    bool hadCore;
    {
        std::lock_guard<std::mutex> guard(mutex);
        hadCore = hasCore;
    }
    dropCore();
    if (link != nullptr) {
        link->server = coreServer;
        link->active = coreActiveReport;
        link->route_socket = dslink::routeCoreSocket;
    }
    if (link == nullptr || link->presence == nullptr || link->leave == nullptr || link->rejoin == nullptr ||
        link->serve == nullptr || link->relogin == nullptr) {
        if (link != nullptr) LOGW("PSP link: the core's link is incomplete, ignored");
        else if (hadCore) LOGI("PSP link: core cleared");
        return;
    }
    int mode;
    {
        std::lock_guard<std::mutex> guard(mutex);
        core = *link;
        hasCore = true;
        maybeStartLocked();
        mode = settings.mode;
    }
    syncRegistration();
    LOGI("PSP link: core registered (%s)", modeName(mode));
}

void configure(const std::vector<Variable>& vars) {
    std::lock_guard<std::mutex> life(lifeMutex);
    const Settings wanted = read(vars);
    {
        std::lock_guard<std::mutex> guard(mutex);
        if (wanted == settings) return;
        const bool sameRole = wanted.mode == settings.mode && wanted.hostIp == settings.hostIp;
        settings.broadcast = wanted.broadcast;
        if (wanted.self != settings.self) psl_set_self(&election, wanted.self);
        settings.self = wanted.self;
        if (sameRole) {
            LOGI("PSP link: self %s, broadcast %s", text(wanted.self).c_str(), text(wanted.broadcast).c_str());
            return;
        }
    }
    // A new role starts the election over; the old finder first takes back what it told the core.
    stopFinder();
    {
        std::lock_guard<std::mutex> guard(mutex);
        settings = wanted;
        resetElectionLocked();
        stopping = false;
        maybeStartLocked();
    }
    syncRegistration();
    if (wanted.mode == MODE_OFF) {
        LOGI("PSP link: off");
    } else {
        LOGI("PSP link: %s, self %s, broadcast %s%s%s", modeName(wanted.mode), text(wanted.self).c_str(),
             text(wanted.broadcast).c_str(), wanted.mode == PSL_JOIN ? ", host " : "",
             wanted.mode == PSL_JOIN ? text(wanted.hostIp).c_str() : "");
        if (wanted.self == 0) LOGW("PSP link: no address for this device");
    }
}

void setNetwork(int64_t) {
    // dslink holds the handle routeCoreSocket binds to; the finder's socket only needs routing again.
    std::lock_guard<std::mutex> guard(mutex);
    rebind = true;
}

void forgetCore() {
    std::lock_guard<std::mutex> life(lifeMutex);
    dropCore();
    std::lock_guard<std::mutex> guard(mutex);
    settings = Settings {};
    resetElectionLocked();
}

}
