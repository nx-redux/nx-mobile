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

#include <algorithm>
#include <arpa/inet.h>
#include <atomic>
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
constexpr int MAX_DRAIN = 32;   // datagrams read per tick, whatever their size: a flood cannot keep the thread from stepping
// What a waiting server() allows the finder: its listen window, then a step and the server starting to listen.
constexpr int SERVER_WAIT_MS = PSL_LISTEN_MS + 500;
// While a cable is coming the election is held, and server() waits through the hold and the listen window after it.
// A hold that starts during the plain wait takes it this far at most, however often the cable comes and goes meanwhile.
// The core sizes a game's request on the same value (nx_psp_link.h).
constexpr int SERVER_WAIT_MAX_MS = NX_PSP_LINK_SERVER_WAIT_MAX_MS;
static_assert(SERVER_WAIT_MAX_MS >= SERVER_WAIT_MS + PSL_CABLE_HOLD_MS + SERVER_WAIT_MS,
              "server() must be able to wait through a hold that starts at the end of its plain wait");
// A cable that is up but gave no address of this device on it (its routes not there yet) is asked again this often.
constexpr int PROBE_RETRY_MS = 1000;

struct Settings {
    int mode = MODE_OFF;     // PSL_AUTO / PSL_HOST / PSL_JOIN
    uint32_t hostIp = 0;     // join: the session host's address; network order, as every address here
    uint32_t broadcast = 0;  // the Wi-Fi's, or a session's network's (the cable's for a session over it)
    uint32_t self = 0;       // this device's address on the Wi-Fi, or a session's on its network

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
// "active" while parked, or the listen window would already have run out when the device comes back. coreGrouped can
// be 1 with coreActive 0: the game left ad hoc while others still play on this device's server.
int coreActive = 0, coreGrouped = 0;
// While parked: the game has been out of ad hoc since the leave. On the automatic route its next trigger then brings the
// device back (a game still in ad hoc at the leave must leave it first). Kept on a session too, which a Disconnect can
// turn into the automatic route while parked (configure).
bool outSinceLeave = false;
// The choice server() answers from, and the core has been told about (serve, relogin).
psl_choice published {};
// Bumped whenever the finder stops: a waiting server() gives up and answers 0.
unsigned generation = 0;
bool rebind = false;
bool stopping = false;
bool wake = false;                 // a change the finder should step on now rather than at its next tick
std::thread finder;
// The cable as dslink last reported it, kept outside the election so a fresh one (resetElectionLocked) starts with it.
int cableState = PSL_CABLE_NONE;
bool cableReported = false;        // dslink has given a state since the core registered
uint32_t cableSelf = 0;            // this device's end, probed when the cable comes up; 0 when the probe found none
uint32_t cableBroadcast = 0;       // as dslink gives it; 0 until given, the cable's default then
unsigned cableSeq = 0;             // bumped by every report, so a probe that ran meanwhile is not applied over it
int64_t nextProbeMs = 0;

// The network the core's new sockets go to: the published choice's, so a login that gets a cable server makes its
// sockets on the cable. Set before a choice is published and before serve(1) makes the listen socket. Read without
// mutex: the core routes its sockets on its own threads, possibly while the finder holds it.
std::atomic<int> coreCable {0};

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

// 0, "no address", for a variable that is missing, empty or not a dotted IPv4 address: the app empties the addresses
// of a session it turns into the automatic route when it knows no Wi-Fi address, since an update keeps every variable
// it leaves out.
uint32_t address(const std::vector<Variable>& vars, const char* key) {
    const std::string* value = find(vars, key);
    in_addr a {};
    if (value == nullptr || value->empty() || inet_pton(AF_INET, value->c_str(), &a) != 1) return 0;
    return a.s_addr;
}

uint32_t defaultCableBroadcast() {
    return htonl((10u << 24) | (99u << 16) | 3u);   // 10.99.0.3: the app's cable is 10.99.0.0/30
}

// Under mutex.
uint32_t cableBroadcastLocked() {
    return cableBroadcast != 0 ? cableBroadcast : defaultCableBroadcast();
}

// The cable as the election is given it. Up with no address of this device on it yet is still coming: the finder's
// probe finds the address within a second or so, and a cable that is up is no reason to end a hold and settle on the
// Wi-Fi meanwhile. Under mutex.
void applyCableLocked(int64_t now) {
    const int state = cableState == PSL_CABLE_UP && cableSelf == 0 ? PSL_CABLE_COMING : cableState;
    psl_set_cable(&election, state, cableSelf, cableBroadcast, now);
}

// One of the cable's two ends (not its network or broadcast address) in the /30 that broadcast closes.
bool cableEnd(uint32_t ip, uint32_t broadcast) {
    const uint32_t base = ntohl(broadcast) & ~3u, host = ntohl(ip);
    return ip != 0 && (host & ~3u) == base && (host == base + 1 || host == base + 2);
}

// This device's end of the cable: the source address of a socket let go of the Wi-Fi and connected (nothing is sent)
// to an end of the cable, which the app's routes take onto the cable. Tried against both ends, since either can be
// this device's own; 0 when neither gives an address on the cable (its routes are not there). No lock needed.
uint32_t probeCableSelf(uint32_t broadcast) {
    const uint32_t base = ntohl(broadcast) & ~3u;
    for (uint32_t end : {base + 1, base + 2}) {
        int fd = socket(AF_INET, SOCK_DGRAM | SOCK_CLOEXEC, 0);
        if (fd < 0) return 0;
        dslink::routeCoreSocket(fd, 1);
        sockaddr_in to {};
        to.sin_family = AF_INET;
        to.sin_port = htons(PSL_PORT);
        to.sin_addr.s_addr = htonl(end);
        sockaddr_in local {};
        socklen_t localLen = sizeof(local);
        uint32_t self = 0;
        if (connect(fd, (sockaddr*) &to, sizeof(to)) == 0 && getsockname(fd, (sockaddr*) &local, &localLen) == 0) {
            self = local.sin_addr.s_addr;
        }
        close(fd);
        if (cableEnd(self, broadcast)) return self;
    }
    return 0;
}

Settings read(const std::vector<Variable>& vars) {
    Settings s;
    const std::string* mode = find(vars, "nx_psp_link");
    if (mode == nullptr) return s;
    if (*mode == "auto") s.mode = PSL_AUTO;
    else if (*mode == "host") s.mode = PSL_HOST;
    else if (*mode == "join") s.mode = PSL_JOIN;
    else return s;
    // Only a join has a host: a session left for the automatic route keeps its old variable.
    if (s.mode == PSL_JOIN) s.hostIp = address(vars, "nx_psp_link_ip");
    s.broadcast = address(vars, "nx_psp_link_broadcast");
    s.self = address(vars, "nx_psp_link_self");
    return s;
}

// The network the election is on now, as its next step will report it, without stepping it. Under mutex.
int networkLocked() {
    psl copy = election;
    return psl_step(&copy, nowMs()).cable;
}

// Without a finder nothing is published, and the core's sockets go to the network the election is on. Under mutex.
void routeIdleLocked() {
    if (!finder.joinable()) coreCable.store(networkLocked());
}

// A session over the cable knows this device's end already: its own address. Kept for the automatic route a session
// can turn into, on a cable that stays up and so is never reported again. Under mutex.
void adoptSessionCableSelfLocked() {
    const bool session = settings.mode == PSL_HOST || settings.mode == PSL_JOIN;
    if (!session || cableState != PSL_CABLE_UP || cableSelf != 0 || !cableEnd(settings.self, cableBroadcastLocked())) return;
    cableSelf = settings.self;
    applyCableLocked(nowMs());
    LOGI("PSP link: cable up, self %s (the session's)", text(cableSelf).c_str());
}

// A fresh election for the settings, carrying over the cable and what the core has already reported. Under mutex.
void resetElectionLocked() {
    const int64_t now = nowMs();
    psl_init(&election, settings.mode == MODE_OFF ? PSL_AUTO : settings.mode, settings.self, processTie(), settings.hostIp);
    psl_set_net(&election, settings.broadcast);
    // The cable goes in before the core's report, so the replayed entry into ad hoc meets the cable as it stands and
    // arms a hold for one that is coming, as the game's own entry did. The report's grouped is not in yet when the
    // cable moves the election; a role change parks the game first (configure), so no grouped game is moved by it.
    applyCableLocked(now);
    if (parked) psl_park(&election, 1);
    else if (coreActive) psl_active(&election, 1, coreGrouped, now);
    routeIdleLocked();
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
    // Without it every announcement fails (EACCES); sendAnnouncement says so when they do.
    if (setsockopt(fd, SOL_SOCKET, SO_BROADCAST, &one, sizeof(one)) != 0) {
        LOGW("PSP link: cannot allow broadcasts on the finder's socket (errno %d)", errno);
    }
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

const char* netName(bool cable) {
    return cable ? "the cable" : "the Wi-Fi";
}

void logChoice(const psl_choice& was, const psl_choice& now) {
    if (now.serve != was.serve) LOGI("PSP link: serve %s", now.serve ? "on" : "off");
    if (now.settled != was.settled || now.server_ip != was.server_ip) {
        if (now.settled) {
            LOGI("PSP link: server %s%s, on %s", text(now.server_ip).c_str(), now.serve ? " (this device)" : "",
                 netName(now.cable));
        } else {
            LOGI("PSP link: no server");
        }
    }
}

// The finder's one socket, for both networks: a socket pinned to the Wi-Fi skips the cable's VPN, so it is let go of
// the Wi-Fi before a send to the cable and pinned again before one to the Wi-Fi (dslink::routeSocket). Between sends it
// stays routed to the network the election is on, where the packets it wants come from.
struct FinderSocket {
    int fd = -1;
    bool cable = false;               // routed onto the cable, else pinned to the Wi-Fi
    bool failing[2] = {false, false}; // per network: a failure is logged once per run of failures
};

void routeFinder(FinderSocket& s, bool cable, bool again) {
    if (s.fd < 0 || (cable == s.cable && !again)) return;
    dslink::routeCoreSocket(s.fd, cable ? 1 : 0);
    s.cable = cable;
}

// Sends one finder packet to a network's broadcast address. A failure is logged once per run of failures: they repeat
// every tick while that network is down.
void sendAnnouncement(FinderSocket& s, const uint8_t* out, size_t len, bool cable, uint32_t broadcast) {
    if (len == 0 || broadcast == 0 || s.fd < 0) return;
    routeFinder(s, cable, false);
    sockaddr_in to {};
    to.sin_family = AF_INET;
    to.sin_addr.s_addr = broadcast;
    to.sin_port = htons(PSL_PORT);
    bool& failing = s.failing[cable ? 1 : 0];
    if (sendto(s.fd, out, len, 0, (sockaddr*) &to, sizeof(to)) < 0) {
        if (!failing) LOGW("PSP link: cannot send to %s (errno %d)", text(broadcast).c_str(), errno);
        failing = true;
    } else if (failing) {
        LOGI("PSP link: sending to %s again", text(broadcast).c_str());
        failing = false;
    }
}

// Where a packet for a network goes, 0 for nowhere: the Wi-Fi's broadcast when one is known; the cable's while it is
// up, or for a session on it until a state is reported (until its registration it has none). A cable that is gone
// has no route of its own, and its broadcast would leave on the default network. Under mutex.
uint32_t destinationLocked(bool cable) {
    if (!cable) return settings.broadcast;
    if (cableState != PSL_CABLE_UP && (settings.mode == PSL_AUTO || cableReported)) return 0;
    return cableBroadcastLocked();
}

// The goodbye of a server that served from `from`: the module's goodbye packet with that address in it, since a step
// that already serves on the other network has replaced the address the module would put there.
size_t packGoodbyeFrom(uint32_t from, uint8_t out[PSL_PACKET]) {
    const size_t len = psl_pack_goodbye(&election, out);
    std::memcpy(out + 8, &from, 4);
    return len;
}

void runFinder() {
    // Without a socket the election still runs: host and join need none, and an automatic device then serves alone.
    FinderSocket sock;
    sock.fd = openSocket();
    int64_t lastAnnounce = 0;
    bool announced = false;
    // The network the last packet sent said serving on (-1: none), and from which address: when this device stops
    // serving there, or the election moves to the other network, one more packet tells its followers, on that network.
    int servingNet = -1;
    uint32_t servingFrom = 0;
    psl_choice lastStep {};
    int lastNet = -1;
    std::unique_lock<std::mutex> lock(mutex);
    while (!stopping) {
        bool route = rebind;
        rebind = false;
        wake = false;
        // A cable that is up without an address of this device on it is probed again, outside the lock.
        const int64_t tickStart = nowMs();
        const bool probe = cableState == PSL_CABLE_UP && cableSelf == 0 && tickStart >= nextProbeMs;
        const unsigned probeSeq = cableSeq;
        const uint32_t probeOn = cableBroadcastLocked();
        if (probe) nextProbeMs = tickStart + PROBE_RETRY_MS;
        lock.unlock();

        if (route) routeFinder(sock, sock.cable, true);
        const uint32_t probed = probe ? probeCableSelf(probeOn) : 0;
        struct Packet {
            uint8_t data[PSL_PACKET];
            ssize_t len;
            uint32_t from;
        } packets[MAX_DRAIN];
        int count = 0;
        for (int reads = 0; sock.fd >= 0 && reads < MAX_DRAIN; reads++) {
            sockaddr_in from {};
            socklen_t fromLen = sizeof(from);
            // One byte over the packet size, so a longer datagram reads as longer and is refused, not cut to fit.
            uint8_t buf[PSL_PACKET + 1];
            ssize_t n = recvfrom(sock.fd, buf, sizeof(buf), MSG_DONTWAIT, (sockaddr*) &from, &fromLen);
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
        if (probed != 0 && probeSeq == cableSeq && cableState == PSL_CABLE_UP && cableSelf == 0) {
            cableSelf = probed;
            applyCableLocked(now);
            LOGI("PSP link: cable up, self %s", text(cableSelf).c_str());
        }
        // Packets from either network go in: the module keeps those from the network it is on.
        for (int i = 0; i < count; i++) psl_heard(&election, packets[i].data, (size_t) packets[i].len, packets[i].from, now);
        const psl_choice choice = psl_step(&election, now);   // before psl_pack: it reads what the step updates
        // Before the choice is published, and before serve(1) below makes the listen socket: the core's sockets for
        // this choice are made on its network.
        coreCable.store(choice.cable);
        const psl_choice was = published;
        // A server that moved network in one step (the finder held up past the listen window) is stopped and started
        // again, so its listen socket is made on the new network.
        const bool restart = choice.serve && was.serve && choice.cable != was.cable;
        const bool starting = (choice.serve && !was.serve) || restart;
        const bool stoppingServer = !choice.serve && was.serve;
        // A move to the other network unsettles the choice first (the module forgets the old network's peers), so it
        // is a relogin; a server on this device moving with it is serve(0) now and serve(1) on the new network later.
        const bool relogin = was.settled && (!choice.settled || choice.server_ip != was.server_ip);
        // A server announced on the network the election has left says goodbye there, at once: whether this device
        // still serves on the new one or not, its followers there cannot reach it.
        uint8_t bye[PSL_PACKET];
        size_t byeLen = 0;
        const int byeNet = servingNet >= 0 && servingNet != choice.cable ? servingNet : -1;
        uint32_t byeTo = 0;
        if (byeNet >= 0) {
            byeLen = packGoodbyeFrom(servingFrom, bye);
            byeTo = destinationLocked(byeNet == 1);
            servingNet = -1;
        }
        // A change is announced at once, not at the next slot: a device listening for a server hears it inside its
        // window, and one following this device's server hears it stop. A server is announced more often for the same
        // reason. A new server on this device is announced once it listens (below), so nobody logs in to it too early.
        const bool moved = choice.serve != lastStep.serve || choice.server_ip != lastStep.server_ip ||
                           choice.cable != lastStep.cable;
        const bool nowHeld = choice.held && !lastStep.held;
        lastStep = choice;
        const int64_t every = choice.serve ? PSL_SERVING_ANNOUNCE_MS : PSL_ANNOUNCE_MS;
        uint8_t out[PSL_PACKET];
        size_t outLen = 0;
        if (!starting && (moved || !announced || now - lastAnnounce >= every)) {
            // On the choice's network only: the module packs this device's address there.
            outLen = psl_pack(&election, out);
            if (outLen == 0 && servingNet >= 0) outLen = packGoodbyeFrom(servingFrom, out);
            if (outLen > 0) {
                lastAnnounce = now;
                announced = true;
                const bool serving = (out[5] & PSL_F_SERVING) != 0;
                servingNet = serving ? choice.cable : -1;
                servingFrom = serving ? choice.self : 0;
            }
        }
        const uint32_t to = destinationLocked(choice.cable);
        const nx_psp_link_interface link = core;
        const bool live = hasCore;
        // A new server on this device is answered only once it listens, so a client never asks it too early.
        if (!starting) {
            published = choice;
            changed.notify_all();
        }
        lock.unlock();

        sendAnnouncement(sock, bye, byeLen, byeNet == 1, byeTo);
        sendAnnouncement(sock, out, outLen, choice.cable, to);
        routeFinder(sock, choice.cable, false);
        if (choice.cable != lastNet) {
            LOGI("PSP link: on %s", netName(choice.cable));
            lastNet = choice.cable;
        }
        if (nowHeld) LOGI("PSP link: held for the cable");
        logChoice(was, choice);
        if (live && starting) {
            if (restart) link.serve(0);
            link.serve(1);
            uint32_t sendTo = 0;
            {
                // Published even when stopping meanwhile, so the finder's exit stops this server again.
                std::lock_guard<std::mutex> guard(mutex);
                if (hasCore) {
                    published = choice;
                    changed.notify_all();
                }
                // Announced only while the election is still on the server's network: a cable reported during
                // serve(1) moves it, and the next step stops this server.
                outLen = networkLocked() == choice.cable ? psl_pack(&election, out) : 0;
                sendTo = destinationLocked(choice.cable);
            }
            if (outLen > 0) {
                lastAnnounce = nowMs();
                announced = true;
                const bool serving = (out[5] & PSL_F_SERVING) != 0;
                servingNet = serving ? choice.cable : -1;
                servingFrom = serving ? choice.self : 0;
            }
            sendAnnouncement(sock, out, outLen, choice.cable, sendTo);
        }
        if (live && stoppingServer) link.serve(0);
        if (live && relogin) link.relogin();

        lock.lock();
        changed.wait_for(lock, std::chrono::milliseconds(TICK_MS), [] { return stopping || wake; });
    }

    // Stopping, the lock held. A finder ended by new settings takes back what it told the core; one ended because
    // the core is going (hasCore already false, published already cleared) calls nothing. Either way a server this
    // device announced says it stopped, on the network it announced on.
    const psl_choice was = published;
    const nx_psp_link_interface link = core;
    const bool live = hasCore;
    published = psl_choice {};
    changed.notify_all();
    uint8_t out[PSL_PACKET];
    const size_t outLen = servingNet >= 0 ? packGoodbyeFrom(servingFrom, out) : 0;
    const uint32_t broadcast = servingNet >= 0 ? destinationLocked(servingNet == 1) : 0;
    lock.unlock();
    sendAnnouncement(sock, out, outLen, servingNet == 1, broadcast);
    if (live) {
        logChoice(was, psl_choice {});
        if (was.serve) link.serve(0);
        if (was.settled) link.relogin();
    }
    if (sock.fd >= 0) close(sock.fd);
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
        changed.notify_all();   // a server() waiting for a choice gives up
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

// A session comes back; the automatic route waits for the game to re-enter ad hoc instead (coreActiveReport), and a
// link switched off stays out (configure). The core is told only when the runner comes back itself, so the two never
// part: the core's own idea of its route is read from the variables a frame later than the runner's.
void adapterRejoin() {
    nx_psp_link_interface link {};
    {
        std::lock_guard<std::mutex> guard(mutex);
        if (parked && (settings.mode == PSL_HOST || settings.mode == PSL_JOIN)) {
            unparkLocked();
            link = core;
        }
    }
    if (link.rejoin != nullptr) link.rejoin();
}

// dslink's cable report (NX_3DS_CABLE_*), with coreRoomMutex held: on every change, and the state as it stands when
// this adapter registers. Kept outside the election, so every fresh one starts with it. This device's end is probed
// when the cable is up, before mutex is taken; a cable up with no end found is still coming to the election, and the
// finder asks again while it runs.
void adapterSetCable(int state, const char* broadcast) {
    if (state < PSL_CABLE_NONE || state > PSL_CABLE_UP) return;
    in_addr a {};
    const uint32_t given = broadcast != nullptr && inet_pton(AF_INET, broadcast, &a) == 1 ? a.s_addr : 0;
    const uint32_t self = state == PSL_CABLE_UP ? probeCableSelf(given != 0 ? given : defaultCableBroadcast()) : 0;
    std::lock_guard<std::mutex> guard(mutex);
    cableState = state;
    cableSelf = self;
    cableReported = true;
    if (given != 0) cableBroadcast = given;
    cableSeq++;
    nextProbeMs = nowMs() + PROBE_RETRY_MS;
    applyCableLocked(nowMs());
    if (state == PSL_CABLE_UP && self != 0) {
        LOGI("PSP link: cable up, self %s", text(self).c_str());
    } else if (state == PSL_CABLE_UP) {
        LOGW("PSP link: cable up, but no address of this device on it yet: waited for as a cable that is coming");
        adoptSessionCableSelfLocked();
    } else {
        LOGI("PSP link: cable %s", state == PSL_CABLE_COMING ? "coming" : "none");
    }
    routeIdleLocked();
    wake = true;
    changed.notify_all();
}

nx_3ds_link_interface adapter {adapterPresence, adapterLeave, adapterRejoin, adapterSetCable, nullptr};

// The core's own sockets go to the network the link is on (coreCable), whatever the core asks for. Lock-free.
void coreRouteSocket(int fd, int) {
    dslink::routeCoreSocket(fd, coreCable.load());
}

// The core's half of the interface. Safe whether or not the link is configured: off, server() answers 0 at once.
// Waits SERVER_WAIT_MS for a server; while the published choice is held for a cable that is coming, through the hold
// and the listen window after it, but never past SERVER_WAIT_MAX_MS. Given up at once when the finder stops (the
// core going, new settings), the device leaves meanwhile, or the game is out of ad hoc: the core tells that before it
// joins the login thread waiting here, so leaving ad hoc never waits out a hold. A call made while still parked waits
// as ever: the core un-parks before it reports the trigger that un-parks the runner.
int coreServer(uint32_t* ip, uint32_t* self) {
    std::unique_lock<std::mutex> lock(mutex);
    if (!hasCore || settings.mode == MODE_OFF) return 0;
    const unsigned start = generation;
    const bool parkedAtStart = parked;
    const int64_t begin = nowMs();
    const int64_t cap = begin + SERVER_WAIT_MAX_MS;
    int64_t until = begin + SERVER_WAIT_MS;
    for (;;) {
        if (generation != start || (parked && !parkedAtStart) || !coreActive || published.settled) break;
        if (published.held) until = std::min(cap, std::max(until, election.hold_until_ms + SERVER_WAIT_MS));
        const int64_t now = nowMs();
        if (now >= until) break;
        changed.wait_for(lock, std::chrono::milliseconds(until - now));
    }
    if (generation != start || !coreActive || !published.settled || published.server_ip == 0) return 0;
    if (ip != nullptr) *ip = published.server_ip;
    if (self != nullptr) *self = published.self;
    return 1;
}

void coreActiveReport(int active, int grouped) {
    std::lock_guard<std::mutex> guard(mutex);
    if (!hasCore) return;
    active = active != 0;
    grouped = grouped != 0;
    const bool changedReport = active != coreActive || grouped != coreGrouped;
    // Kept even while off: settings that turn the link on later carry it into their election.
    coreActive = active;
    coreGrouped = grouped;
    // A server() waiting gives up once the game is out of ad hoc, whatever else this report does.
    if (changedReport) changed.notify_all();
    if (settings.mode == MODE_OFF) return;
    if (changedReport) {
        LOGI("PSP link: game %s", !active ? (grouped ? "left ad hoc, others still on its server" : "left ad hoc")
                                          : grouped ? "in a group, or others on its server" : "in ad hoc");
    }
    if (parked) {
        if (!active) outSinceLeave = true;
        else if (outSinceLeave && settings.mode == PSL_AUTO) unparkLocked();
        return;
    }
    psl_active(&election, active, grouped, nowMs());
    // A game out of its group can take the cable now, and one that is coming holds it: stepped at once.
    if (changedReport) {
        wake = true;
        changed.notify_all();
    }
    routeIdleLocked();
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
    // No cable report reaches an unregistered adapter: the next registration gives the state as it then stands.
    cableState = PSL_CABLE_NONE;
    cableReported = false;
    cableSelf = cableBroadcast = 0;
    cableSeq++;
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
        link->route_socket = coreRouteSocket;
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
    nx_psp_link_interface leaving {};
    {
        std::lock_guard<std::mutex> guard(mutex);
        if (wanted == settings) return;
        // A session over the cable leaving its addresses: its end of a cable that stays up is still this device's.
        adoptSessionCableSelfLocked();
        const bool sameRole = wanted.mode == settings.mode && wanted.hostIp == settings.hostIp;
        settings.broadcast = wanted.broadcast;
        if (wanted.self != settings.self) psl_set_self(&election, wanted.self);
        settings.self = wanted.self;
        psl_set_net(&election, wanted.broadcast);
        if (sameRole) {
            // On the cable the election and its choice are unchanged: only the Wi-Fi's address and subnet are.
            routeIdleLocked();
            LOGI("PSP link: self %s, broadcast %s", text(wanted.self).c_str(), text(wanted.broadcast).c_str());
            return;
        }
        // The sheet's Disconnect: switched off, or a session turned into the automatic route. Either way the game leaves
        // ad hoc as for a leave, so its presence is out and states work again. Off it stays out; on the automatic route
        // it comes back as there, once the game has left ad hoc and re-entered it (coreActiveReport), never by a rejoin.
        // The new mode is set under the same hold as the park, so no rejoin between brings either side back, and the
        // game's reports from here count toward the automatic route's return.
        const bool session = settings.mode == PSL_HOST || settings.mode == PSL_JOIN;
        const bool toOff = wanted.mode == MODE_OFF && settings.mode != MODE_OFF;
        const bool toAuto = wanted.mode == PSL_AUTO && session;
        if ((toOff || toAuto) && hasCore && !parked) {
            parked = true;
            outSinceLeave = !coreActive;
            psl_park(&election, 1);
            leaving = core;
            changed.notify_all();   // a server() waiting for a choice gives up
        }
        if (toOff || toAuto) settings.mode = wanted.mode;
    }
    if (leaving.leave != nullptr) {
        LOGI("PSP link: %s, left", wanted.mode == MODE_OFF ? "switched off" : "session over, automatic once the game re-enters ad hoc");
        leaving.leave();
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
        if (wanted.self == 0) {
            LOGW("PSP link: no address for this device%s", wanted.mode == PSL_AUTO ? " on the Wi-Fi: the cable only" : "");
        }
    }
}

void setNetwork(int64_t) {
    // dslink holds the handle routeCoreSocket binds to; the finder's socket only needs routing again, to the network
    // it is routed to (pinned to the new Wi-Fi, or still let go for the cable).
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
