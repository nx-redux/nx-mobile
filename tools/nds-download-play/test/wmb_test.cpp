// The core's Download Play protocol against frames from a real exchange (test-data/) and frames built per GBATEK.
#include "wmb.hpp"

#include <cstdio>
#include <cstdlib>
#include <unistd.h>
#include <cstring>
#include <fstream>
#include <functional>
#include <sstream>
#include <string>
#include <vector>

using namespace MelonDsDs;
using Bytes = std::vector<uint8_t>;
using Mac = std::array<uint8_t, 6>;

int failures = 0;
void mailbox_headers_identical();
void mailbox_layout();
void offer_to_mailbox();
void ui_state_from_mailbox();
void ui_progress_percent();
void ui_selection_clamps();
void ui_ascii_from_ucs2();
#define CHECK(...) do { if (!(__VA_ARGS__)) { std::printf("  FAIL %s:%d: %s\n", __FILE__, __LINE__, #__VA_ARGS__); failures++; } } while (0)

static const Mac HOST {0x00, 0x08, 0xBF, 0x63, 0xB6, 0xB2};
static const Mac OTHER_HOST {0x00, 0x08, 0xBF, 0x11, 0x22, 0x33};
static const Mac CLIENT {0x00, 0x08, 0xBF, 0xDE, 0xF2, 0x77};
static const char* DATA = "../test-data/";

static Bytes Hex(const std::string& s) {
    Bytes b;
    for (size_t i = 0; i + 1 < s.size(); i += 2) b.push_back(std::stoi(s.substr(i, 2), nullptr, 16));
    return b;
}

static void Put16(Bytes& b, uint16_t v) { b.push_back(v & 0xFF); b.push_back(v >> 8); }
static void Put32(Bytes& b, uint32_t v) { Put16(b, v & 0xFFFF); Put16(b, v >> 16); }
static void PutMac(Bytes& b, const Mac& m) { b.insert(b.end(), m.begin(), m.end()); }
static uint16_t Le16(const uint8_t* p) { return p[0] | p[1] << 8; }

/// melonDS's 12-byte radio header (rate, channel 13, frame length) and an 802.11 header.
static Bytes Frame(uint16_t fc, const Mac& a1, const Mac& a2, const Mac& a3, const Bytes& body) {
    Bytes f(8, 0);
    f.push_back(0x14);
    f.push_back(13);
    Put16(f, 24 + body.size() + 4);
    Put16(f, fc);
    Put16(f, 0);
    PutMac(f, a1);
    PutMac(f, a2);
    PutMac(f, a3);
    Put16(f, 0);
    f.insert(f.end(), body.begin(), body.end());
    f.insert(f.end(), 4, 0);
    return f;
}

/// A Download Play beacon carrying one advertisement snippet (GBATEK "DS Wifi Nintendo Beacons"), checksum included.
static Bytes Beacon(const Mac& host, uint32_t gameId, uint16_t stream, uint8_t snippet, const uint8_t* data, uint16_t size,
                    uint8_t players = 1) {
    Bytes body(8, 0);
    Put16(body, 0xDA);
    Put16(body, 0x21);
    body.insert(body.end(), {0x01, 0x02, 0x82, 0x84, 0x03, 0x01, 13, 0x05, 0x05, 0x00, 0x02, 0, 0, 0});
    Bytes dd {0x00, 0x09, 0xBF, 0x00};
    Put16(dd, 0x000A);
    Put16(dd, 0);
    Put32(dd, 0x00010001);
    Put32(dd, gameId);
    Put16(dd, stream);
    dd.push_back(0x70);
    dd.push_back(0x03);
    Put16(dd, 0x0100);
    Put16(dd, 0x0008);
    Put32(dd, gameId);
    dd.push_back(snippet == 9 ? 2 : 0);
    dd.push_back(0);
    dd.push_back(1);
    dd.push_back(snippet);
    Put16(dd, 0); // checksum, below
    dd.push_back(snippet == 9 ? players : snippet);
    dd.push_back(9);
    Put16(dd, snippet == 9 ? 0x000F : size);
    Bytes snip(0x62, 0);
    if (data) std::memcpy(snip.data(), data, std::min<size_t>(size, 0x62));
    dd.insert(dd.end(), snip.begin(), snip.end());
    uint32_t sum = 0;
    for (size_t i = 0x22; i < 0x88; i += 2) sum += Le16(&dd[i]);
    const uint16_t chk = 0xFFFF & ~(sum + (sum >> 16));
    dd[0x20] = chk & 0xFF;
    dd[0x21] = chk >> 8;
    body.push_back(0xDD);
    body.push_back(dd.size());
    body.insert(body.end(), dd.begin(), dd.end());
    return Frame(0x0080, Mac {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF}, host, host, body);
}

static void PutUcs2(Bytes& block, size_t at, const std::u16string& s) {
    for (size_t i = 0; i < s.size(); i++) { block[at + i * 2] = s[i] & 0xFF; block[at + i * 2 + 1] = s[i] >> 8; }
}

/// The nine snippets of an advertisement (icon, host name, players, game name, description).
static std::vector<Bytes> Advertisement(const Mac& host, uint32_t gameId, uint16_t stream, const std::u16string& game,
                                        const std::u16string& hostName, uint8_t maxPlayers) {
    Bytes block(9 * 0x62, 0);
    for (size_t i = 0; i < 0x220; i++) block[i] = i & 0xFF;
    block[0x221] = hostName.size();
    PutUcs2(block, 0x222, hostName);
    block[0x236] = maxPlayers;
    PutUcs2(block, 0x238, game);
    PutUcs2(block, 0x298, u"It's time to Mario Party on the DS!");
    std::vector<Bytes> beacons;
    for (uint8_t n = 0; n < 9; n++) beacons.push_back(Beacon(host, gameId, stream, n, &block[n * 0x62], n < 8 ? 0x62 : 0x48));
    return beacons;
}

struct Captured {
    bool in;
    int type; // 0 reply, 1 command, 2 other
    uint64_t ts;
    Bytes frame;
};

static std::vector<Captured> Capture() {
    std::vector<Captured> out;
    std::ifstream f(std::string(DATA) + "wmb-capture.txt");
    std::string line;
    while (std::getline(f, line)) {
        std::istringstream s(line);
        std::string tag, dir, k, hex;
        Captured c;
        int aid;
        size_t len;
        s >> tag >> dir >> k >> c.type >> k >> aid >> k >> c.ts >> k >> len >> hex;
        c.in = dir == "in";
        c.frame = Hex(hex);
        out.push_back(c);
    }
    return out;
}

static WmbOffer MarioParty() {
    WmbOffer o;
    o.host = HOST;
    o.gameId = 0x390;
    o.streamCode = 0x112F;
    o.channel = 13;
    o.beaconInterval = 0xDA;
    o.cmdDataSize = 0x100;
    o.maxPlayers = 4;
    o.gameName = "Mario Party DS";
    return o;
}

static Bytes BodyOf(const Bytes& frame) { return Bytes(frame.begin() + 36, frame.end() - 4); }

/// A host command (GBATEK "CMD DATA") to the multiboot address; [payload] follows the command byte.
static Bytes Command(const Mac& host, uint8_t command, const Bytes& payload, uint8_t sizeHalfwords) {
    Bytes body;
    Put16(body, 0x0106);
    Put16(body, 0x0002);
    body.push_back(sizeHalfwords);
    body.push_back(0x11);
    body.push_back(command);
    body.insert(body.end(), payload.begin(), payload.end());
    Put16(body, 0x0002);
    return Frame(0x0228, Mac {0x03, 0x09, 0xBF, 0x00, 0x00, 0x00}, host, host, body);
}

static Bytes DataCommand(const Mac& host, uint16_t number, const Bytes& data) {
    Bytes p {0, 0};
    Put16(p, number);
    p.insert(p.end(), data.begin(), data.end());
    p.push_back(0);
    return Command(host, 0x04, p, (data.size() + 6 + 1) / 2);
}

static Bytes RsaCommand(const Mac& host, uint32_t arm9Size, uint32_t arm7Size) {
    Bytes rsa(0xE4, 0);
    auto at32 = [&](size_t at, uint32_t v) { for (int i = 0; i < 4; i++) rsa[at + i] = v >> (8 * i); };
    at32(0x00, 0x02000800);
    at32(0x04, 0x02380000);
    at32(0x20, 0x02000000);
    at32(0x24, arm9Size);
    at32(0x30, 0x02380000);
    at32(0x34, arm7Size);
    return Command(host, 0x03, rsa, 0x75);
}

/// A client past auth and assoc with the host, from the capture's own answers.
static WmbClient JoinedClient(uint64_t now = 0) {
    WmbClient c;
    c.Start(MarioParty(), CLIENT, u"NX Redux", now);
    std::vector<WmbOutgoing> out;
    for (const Captured& f : Capture()) {
        if (!f.in || f.type != 2) continue;
        c.FrameReceived(f.frame.data(), f.frame.size(), f.ts, false, now, out);
        if (c.GetState() == WmbClient::State::Download) break;
    }
    return c;
}

static void scanner_decodes_real_beacon_layout() {
    WmbScanner s;
    std::ifstream f(std::string(DATA) + "beacons.txt");
    std::string line;
    int lines = 0;
    while (std::getline(f, line)) {
        Bytes b = Hex(line);
        b.insert(b.end(), 4, 0);
        CHECK(!s.FrameReceived(b.data(), b.size(), 0));
        lines++;
    }
    CHECK(lines == 6);
    CHECK(s.SnippetsHeard(HOST) == 0x187); // snippets 0, 1, 2, 7 and 8 (9 lists players)
    CHECK(s.Offers().empty());
}

static void scanner_completes_offer() {
    WmbScanner s;
    bool completed = false;
    for (const Bytes& b : Advertisement(HOST, 0x390, 0x112F, u"Mario Party DS", u"KotakNova", 4))
        completed = s.FrameReceived(b.data(), b.size(), 0) || completed;
    CHECK(completed);
    CHECK(s.Offers().size() == 1);
    const WmbOffer& o = s.Offers().at(HOST);
    CHECK(o.gameName == "Mario Party DS");
    CHECK(o.hostName == "KotakNova");
    CHECK(o.description == "It's time to Mario Party on the DS!");
    CHECK(o.maxPlayers == 4);
    CHECK(o.cmdDataSize == 0x100);
    CHECK(o.channel == 13);
    CHECK(o.beaconInterval == 0xDA);
    CHECK(o.icon[0x21F] == 0x1F);
}

static void scanner_reads_players_from_snippet_9() {
    WmbScanner s;
    for (const Bytes& b : Advertisement(HOST, 0x390, 0x112F, u"Mario Party DS", u"KotakNova", 4)) s.FrameReceived(b.data(), b.size(), 0);
    const Bytes nine = Beacon(HOST, 0x390, 0x112F, 9, nullptr, 0, 3);
    CHECK(s.FrameReceived(nine.data(), nine.size(), 0));
    CHECK(s.Offers().at(HOST).players == 3);
}

static void scanner_lists_two_hosts() {
    WmbScanner s;
    for (const Bytes& b : Advertisement(HOST, 0x390, 0x112F, u"Mario Party DS", u"KotakNova", 4)) s.FrameReceived(b.data(), b.size(), 0);
    for (const Bytes& b : Advertisement(OTHER_HOST, 0x136, 0x0042, u"Over the Hedge", u"Friend", 2)) s.FrameReceived(b.data(), b.size(), 0);
    CHECK(s.Offers().size() == 2);
    CHECK(s.Offers().at(OTHER_HOST).gameName == "Over the Hedge");
}

static void scanner_restarts_on_new_stream() {
    WmbScanner s;
    auto first = Advertisement(HOST, 0x390, 0x112F, u"Mario Party DS", u"KotakNova", 4);
    for (const Bytes& b : first) s.FrameReceived(b.data(), b.size(), 0);
    auto second = Advertisement(HOST, 0x390, 0x2222, u"Mario Party DS", u"KotakNova", 4);
    // Half of the new upload's snippets: not merged into the old offer.
    for (int n = 0; n < 4; n++) s.FrameReceived(second[n].data(), second[n].size(), 0);
    CHECK(s.SnippetsHeard(HOST) == 0x0F);
    for (int n = 4; n < 9; n++) s.FrameReceived(second[n].data(), second[n].size(), 0);
    CHECK(s.Offers().at(HOST).streamCode == 0x2222);
}

static void scanner_rejects_bad_checksum() {
    WmbScanner s;
    Bytes b = Advertisement(HOST, 0x390, 0x112F, u"Mario Party DS", u"KotakNova", 4)[0];
    b[b.size() - 10] ^= 0xFF; // inside the snippet data, after the checksum was made
    CHECK(!s.FrameReceived(b.data(), b.size(), 0));
    CHECK(s.SnippetsHeard(HOST) == 0);
    Bytes shortFrame(b.begin(), b.begin() + 60);
    CHECK(!s.FrameReceived(shortFrame.data(), shortFrame.size(), 0));
}

static void scanner_forgets_silent_host() {
    WmbScanner s;
    for (const Bytes& b : Advertisement(HOST, 0x390, 0x112F, u"Mario Party DS", u"KotakNova", 4)) s.FrameReceived(b.data(), b.size(), 1000);
    s.Forget(5999);
    CHECK(s.Offers().size() == 1);
    s.Forget(6001);
    CHECK(s.Offers().empty());
}

static void client_auth_assoc_from_capture() {
    const auto cap = Capture();
    WmbClient c;
    c.Start(MarioParty(), CLIENT, u"NX Redux", 0);
    CHECK(c.GetState() == WmbClient::State::Auth);
    std::vector<WmbOutgoing> out;
    const Bytes beacon = Beacon(HOST, 0x390, 0x112F, 0, nullptr, 0x62);
    c.FrameReceived(beacon.data(), beacon.size(), 1000, false, 0, out);
    CHECK(out.size() == 1);
    CHECK(!out.empty() && !out[0].reply);
    CHECK(!out.empty() && BodyOf(out[0].frame) == BodyOf(cap[0].frame)); // the real client's auth request
    out.clear();
    c.FrameReceived(cap[1].frame.data(), cap[1].frame.size(), cap[1].ts, false, 0, out); // auth response
    CHECK(c.GetState() == WmbClient::State::Assoc);
    c.FrameReceived(beacon.data(), beacon.size(), 1000, false, 0, out);
    CHECK(out.size() == 1);
    CHECK(!out.empty() && BodyOf(out[0].frame) == BodyOf(cap[2].frame)); // the real client's assoc request
    out.clear();
    c.FrameReceived(cap[3].frame.data(), cap[3].frame.size(), cap[3].ts, false, 0, out); // assoc response
    CHECK(c.GetState() == WmbClient::State::Download);
}

static void client_replies_like_real_client() {
    const auto cap = Capture();
    WmbClient c = JoinedClient();
    std::vector<WmbOutgoing> out;
    int names = 0;
    bool rsa = false, data0 = false;
    for (const Captured& f : cap) {
        if (!f.in || f.type != 1 || Le16(&f.frame[12]) != 0x0228) continue;
        const uint8_t command = f.frame[36 + 6];
        out.clear();
        c.FrameReceived(f.frame.data(), f.frame.size(), f.ts, true, 0, out);
        if (command == 0x05) {
            // The capture holds only some of the data packets: the final ends a download that is not whole.
            CHECK(out.size() == 2);
            CHECK(c.GetFailure() == WmbClient::Failure::BadDownload);
            break;
        }
        CHECK(out.size() == 1);
        if (out.empty()) continue;
        const Bytes& r = out[0].frame;
        CHECK(out[0].reply && out[0].aid == 1);
        CHECK(Le16(&r[12]) == 0x0118);
        CHECK(Mac {r[16], r[17], r[18], r[19], r[20], r[21]} == HOST);
        CHECK(Mac {r[22], r[23], r[24], r[25], r[26], r[27]} == CLIENT);
        CHECK(Mac {r[28], r[29], r[30], r[31], r[32], r[33]} == (Mac {0x03, 0x09, 0xBF, 0x00, 0x00, 0x10}));
        CHECK(r[36] == 0x04 && r[37] == 0x81);
        if (command == 0x01) {
            CHECK(r[38] == 0x07 && r[39] == names % 5);
            names++;
        } else if (command == 0x03 && !rsa) {
            CHECK(r[38] == 0x08);
            CHECK(c.PacketsTotal() == 2528);
            rsa = true;
        } else if (command == 0x04 && !data0 && Le16(&f.frame[36 + 9]) == 0) {
            CHECK((Bytes(r.begin() + 38, r.begin() + 43) == Bytes {0x09, 0x01, 0x00, 0x01, 0x00})); // as the real client
            data0 = true;
        }
    }
    CHECK(names >= 4 && rsa && data0);
}

static void client_reply_stamped_after_command() {
    WmbClient c = JoinedClient();
    std::vector<WmbOutgoing> out;
    const Bytes name = Command(HOST, 0x01, Bytes(5, 0), 3);
    c.FrameReceived(name.data(), name.size(), 5000, true, 0, out);
    CHECK(out.size() == 1 && out[0].timestamp >= 5000 + name.size() * 4 + 96);
}

static void client_ignores_out_of_range_packets() {
    WmbClient c = JoinedClient();
    std::vector<WmbOutgoing> out;
    const Bytes rsa = RsaCommand(HOST, 10, 10);
    c.FrameReceived(rsa.data(), rsa.size(), 0, true, 0, out);
    CHECK(c.PacketsTotal() == 4);
    const Bytes forged = DataCommand(HOST, 9999, Bytes(250, 0xAA));
    c.FrameReceived(forged.data(), forged.size(), 0, true, 0, out);
    CHECK(c.PacketsDone() == 0);
    Bytes huge = DataCommand(HOST, 1, Bytes(4, 0xAA));
    huge[36 + 4] = 0xFF; // a size byte far past the frame
    c.FrameReceived(huge.data(), huge.size(), 0, true, 0, out);
    CHECK(c.PacketsDone() <= 1);
    Bytes cut(huge.begin(), huge.begin() + 40);
    c.FrameReceived(cut.data(), cut.size(), 0, true, 0, out);
}

static void client_ignores_other_hosts_commands() {
    WmbClient c = JoinedClient();
    std::vector<WmbOutgoing> out;
    const Bytes name = Command(OTHER_HOST, 0x01, Bytes(5, 0), 3);
    c.FrameReceived(name.data(), name.size(), 0, true, 0, out);
    CHECK(out.empty());
}

static void client_fails_when_host_silent() {
    WmbClient c;
    c.Start(MarioParty(), CLIENT, u"NX Redux", 1000);
    c.Tick(3999);
    CHECK(c.GetState() == WmbClient::State::Auth);
    c.Tick(4001);
    CHECK(c.GetState() == WmbClient::State::Failed);
    CHECK(c.GetFailure() == WmbClient::Failure::HostGone);
    WmbClient d = JoinedClient(10000);
    std::vector<WmbOutgoing> out;
    const Bytes name = Command(HOST, 0x01, Bytes(5, 0), 3);
    d.FrameReceived(name.data(), name.size(), 0, true, 12000, out);
    d.Tick(14999);
    CHECK(d.GetState() == WmbClient::State::Download);
    d.Tick(15001);
    CHECK(d.GetFailure() == WmbClient::Failure::HostGone);
}

/// Header (2 packets), ARM9 and ARM7 (one each) sent; [arm9] and [arm7] are each 10 bytes.
static WmbClient DownloadedClient() {
    WmbClient c = JoinedClient();
    std::vector<WmbOutgoing> out;
    const Bytes rsa = RsaCommand(HOST, 10, 10);
    c.FrameReceived(rsa.data(), rsa.size(), 0, true, 0, out);
    Bytes header(0x160, 0);
    header[0x20] = 0x00; header[0x21] = 0x40; // ARM9 read from 0x4000
    header[0x30] = 0x00; header[0x31] = 0x50; // ARM7 from 0x5000
    const Bytes parts[4] = {Bytes(header.begin(), header.begin() + 250), Bytes(header.begin() + 250, header.end()), Bytes(10, 0x99),
                            Bytes(10, 0x77)};
    for (uint16_t n = 0; n < 4; n++) {
        const Bytes d = DataCommand(HOST, n, parts[n]);
        c.FrameReceived(d.data(), d.size(), 0, true, 0, out);
    }
    return c;
}

static void client_answers_00_with_phase() {
    WmbClient c = DownloadedClient();
    CHECK(c.PacketsDone() == 4);
    std::vector<WmbOutgoing> out;
    const Bytes dummy = Command(HOST, 0x00, Bytes(5, 0), 3);
    c.FrameReceived(dummy.data(), dummy.size(), 0, true, 0, out);
    CHECK(out.size() == 1 && out[0].frame[38] == 0x0A);
}

static void client_final_boots() {
    WmbClient c = DownloadedClient();
    std::vector<WmbOutgoing> out;
    const Bytes final = Command(HOST, 0x05, Bytes(5, 0), 3);
    c.FrameReceived(final.data(), final.size(), 0, true, 0, out);
    CHECK(out.size() == 2);
    CHECK(out.size() == 2 && out[0].reply && out[0].frame[38] == 0x0B);
    CHECK(out.size() == 2 && Le16(&out[1].frame[12]) == 0x00C0 && out[1].frame[36] == 3);
    CHECK(c.GetState() == WmbClient::State::Done);
    CHECK(c.Region(1) == Bytes(10, 0x99));
    CHECK(c.Region(0).size() == 0x160);
    const auto block = c.BootBlock();
    CHECK(block[0] == 2 && Mac {block[6], block[7], block[8], block[9], block[10], block[11]} == HOST);
    CHECK(Le16(&block[0x12]) == 0x112F && Le16(&block[0x38]) == 13);
}

static void client_cancel_returns_idle() {
    WmbClient c = JoinedClient();
    c.Cancel();
    CHECK(c.GetState() == WmbClient::State::Idle);
    std::vector<WmbOutgoing> out;
    const Bytes name = Command(HOST, 0x01, Bytes(5, 0), 3);
    c.FrameReceived(name.data(), name.size(), 0, true, 0, out);
    CHECK(out.empty());
}

static void make_child_layout() {
    Bytes header(0x160, 0);
    header[0x21] = 0x40;                                         // ARM9 at 0x4000
    header[0x30] = 0x00; header[0x31] = 0x0E; header[0x32] = 0x2E; // ARM7 at 0x2E0E00
    const auto child = MakeWmbChild(header, Bytes(16, 0x99), Bytes(16, 0x77), {});
    CHECK(child.has_value());
    if (!child) return;
    CHECK(child->rom.size() == 0x2E0E00 + 16);
    CHECK(child->rom[0x4000] == 0x99 && child->rom[0x2E0E00] == 0x77);
}

static void make_child_rejects_forged_offsets() {
    Bytes header(0x160, 0);
    header[0x23] = 0x40; // ARM9 at 1 GB
    header[0x31] = 0x50;
    CHECK(!MakeWmbChild(header, Bytes(16, 0), Bytes(16, 0), {}).has_value());
    Bytes low(0x160, 0);
    low[0x20] = 0x10; // ARM9 over the header
    low[0x31] = 0x50;
    CHECK(!MakeWmbChild(low, Bytes(16, 0), Bytes(16, 0), {}).has_value());
    CHECK(!MakeWmbChild(Bytes(0x100, 0), Bytes(16, 0), Bytes(16, 0), {}).has_value());
}

static void client_refuses_oversized_binaries() {
    WmbClient c = JoinedClient();
    std::vector<WmbOutgoing> out;
    const Bytes rsa = RsaCommand(HOST, 0xF0000000, 0x1000);
    c.FrameReceived(rsa.data(), rsa.size(), 0, true, 0, out);
    CHECK(c.GetState() == WmbClient::State::Failed);
    CHECK(c.GetFailure() == WmbClient::Failure::BadDownload);
    CHECK(c.PacketsTotal() == 0);
}

static void client_refuses_impossible_packet_size() {
    WmbOffer o = MarioParty();
    o.cmdDataSize = 0xFFFF;
    WmbClient c;
    c.Start(o, CLIENT, u"NX Redux", 0);
    CHECK(c.GetState() == WmbClient::State::Failed);
    o.cmdDataSize = 6; // no room for data
    c.Start(o, CLIENT, u"NX Redux", 0);
    CHECK(c.GetState() == WmbClient::State::Failed);
}

static void make_child_pads_small_image() {
    Bytes header(0x160, 0);
    header[0x21] = 0x02; // ARM9 at 0x200
    header[0x31] = 0x03; // ARM7 at 0x300
    const auto child = MakeWmbChild(header, Bytes(16, 0x99), Bytes(16, 0x77), {});
    CHECK(child.has_value());
    if (child) CHECK(child->rom.size() >= 0x1000);
}

static void client_takes_a_kept_copy_for_the_same_rsa_frame() {
    WmbClient c = JoinedClient();
    std::vector<WmbOutgoing> out;
    const Bytes rsa = RsaCommand(HOST, 10, 10);
    c.FrameReceived(rsa.data(), rsa.size(), 0, true, 0, out);
    CHECK(c.HaveRsa());
    const Bytes header(0x160, 0x11);
    CHECK(c.UseKept({header, Bytes(10, 0x99), Bytes(10, 0x77)}));
    CHECK(c.PacketsDone() == c.PacketsTotal());
    out.clear();
    const Bytes d = DataCommand(HOST, 0, Bytes(250, 0));
    c.FrameReceived(d.data(), d.size(), 0, true, 0, out);
    CHECK(out.size() == 1 && out[0].frame[38] == 0x0A); // got all: the host goes on to Final
    out.clear();
    const Bytes final = Command(HOST, 0x05, Bytes(5, 0), 3);
    c.FrameReceived(final.data(), final.size(), 0, true, 0, out);
    CHECK(c.GetState() == WmbClient::State::Done);
    CHECK(c.Region(0) == header && c.Region(1) == Bytes(10, 0x99) && c.Region(2) == Bytes(10, 0x77));
}

static void client_refuses_a_kept_copy_of_other_sizes() {
    WmbClient c = JoinedClient();
    std::vector<WmbOutgoing> out;
    const Bytes rsa = RsaCommand(HOST, 10, 10);
    c.FrameReceived(rsa.data(), rsa.size(), 0, true, 0, out);
    CHECK(!c.UseKept({Bytes(0x160, 0), Bytes(12, 0), Bytes(10, 0)}));
    CHECK(c.PacketsDone() == 0);
    WmbClient early = JoinedClient(); // no RSA frame yet
    CHECK(!early.UseKept({Bytes(0x160, 0), Bytes(10, 0), Bytes(10, 0)}));
}

static void client_reports_its_rsa_frame() {
    WmbClient a = DownloadedClient(), b = DownloadedClient();
    CHECK(a.Rsa() == b.Rsa());
    WmbClient c = JoinedClient();
    std::vector<WmbOutgoing> out;
    const Bytes rsa = RsaCommand(HOST, 10, 11);
    c.FrameReceived(rsa.data(), rsa.size(), 0, true, 0, out);
    CHECK(c.Rsa() != a.Rsa());
}

static std::string TempDir() {
    char tmpl[] = "/tmp/wmbkeptXXXXXX";
    return mkdtemp(tmpl);
}

static std::array<uint8_t, 0xE4> RsaOf(uint8_t v) {
    std::array<uint8_t, 0xE4> r {};
    r.fill(v);
    return r;
}

static void kept_copies_round_trip() {
    const std::string dir = TempDir() + "/Download Play";
    const std::array<Bytes, 3> regions {Bytes(0x160, 1), Bytes(300, 2), Bytes(40, 3)};
    CHECK(SaveKept(dir, 0x390, RsaOf(1), regions));
    const auto back = LoadKept(dir, 0x390, RsaOf(1));
    CHECK(back.has_value() && *back == regions);
    CHECK(!LoadKept(dir, 0x391, RsaOf(1)).has_value());
}

static void kept_copy_is_one_per_game_and_only_its_version() {
    const std::string dir = TempDir();
    CHECK(SaveKept(dir, 0x390, RsaOf(1), {Bytes(0x160, 0), Bytes(8, 1), Bytes(8, 0)}));
    CHECK(!LoadKept(dir, 0x390, RsaOf(2)).has_value()); // another version of the game: downloaded again
    CHECK(SaveKept(dir, 0x390, RsaOf(2), {Bytes(0x160, 0), Bytes(8, 2), Bytes(8, 0)}));
    CHECK(!LoadKept(dir, 0x390, RsaOf(1)).has_value()); // replaced, not kept beside
    const auto now = LoadKept(dir, 0x390, RsaOf(2));
    CHECK(now.has_value() && (*now)[1] == Bytes(8, 2));
    for (uint32_t g = 1; g <= 6; g++) CHECK(SaveKept(dir, g, RsaOf(0), {Bytes(0x160, 0), Bytes(8, 0), Bytes(8, 0)}));
    CHECK(LoadKept(dir, 1, RsaOf(0)).has_value() && LoadKept(dir, 0x390, RsaOf(2)).has_value()); // no count limit
}

static void kept_copy_refuses_a_damaged_file() {
    const std::string dir = TempDir();
    CHECK(SaveKept(dir, 7, RsaOf(3), {Bytes(0x160, 0), Bytes(8, 0), Bytes(8, 0)}));
    std::FILE* f = std::fopen((dir + "/00000007.bin").c_str(), "r+b");
    CHECK(f != nullptr);
    if (!f) return;
    std::fseek(f, 8 + 0xE4, SEEK_SET);
    const uint8_t huge[4] = {0xFF, 0xFF, 0xFF, 0x7F};
    std::fwrite(huge, 1, 4, f);
    std::fclose(f);
    CHECK(!LoadKept(dir, 7, RsaOf(3)).has_value());
}

int main() {
    const std::pair<const char*, std::function<void()>> tests[] = {
        {"scanner_decodes_real_beacon_layout", scanner_decodes_real_beacon_layout},
        {"scanner_completes_offer", scanner_completes_offer},
        {"scanner_reads_players_from_snippet_9", scanner_reads_players_from_snippet_9},
        {"scanner_lists_two_hosts", scanner_lists_two_hosts},
        {"scanner_restarts_on_new_stream", scanner_restarts_on_new_stream},
        {"scanner_rejects_bad_checksum", scanner_rejects_bad_checksum},
        {"scanner_forgets_silent_host", scanner_forgets_silent_host},
        {"client_auth_assoc_from_capture", client_auth_assoc_from_capture},
        {"client_replies_like_real_client", client_replies_like_real_client},
        {"client_reply_stamped_after_command", client_reply_stamped_after_command},
        {"client_ignores_out_of_range_packets", client_ignores_out_of_range_packets},
        {"client_ignores_other_hosts_commands", client_ignores_other_hosts_commands},
        {"client_fails_when_host_silent", client_fails_when_host_silent},
        {"client_answers_00_with_phase", client_answers_00_with_phase},
        {"client_final_boots", client_final_boots},
        {"client_cancel_returns_idle", client_cancel_returns_idle},
        {"make_child_layout", make_child_layout},
        {"make_child_rejects_forged_offsets", make_child_rejects_forged_offsets},
        {"client_refuses_oversized_binaries", client_refuses_oversized_binaries},
        {"client_refuses_impossible_packet_size", client_refuses_impossible_packet_size},
        {"make_child_pads_small_image", make_child_pads_small_image},
        {"client_takes_a_kept_copy_for_the_same_rsa_frame", client_takes_a_kept_copy_for_the_same_rsa_frame},
        {"client_refuses_a_kept_copy_of_other_sizes", client_refuses_a_kept_copy_of_other_sizes},
        {"client_reports_its_rsa_frame", client_reports_its_rsa_frame},
        {"kept_copies_round_trip", kept_copies_round_trip},
        {"kept_copy_is_one_per_game_and_only_its_version", kept_copy_is_one_per_game_and_only_its_version},
        {"kept_copy_refuses_a_damaged_file", kept_copy_refuses_a_damaged_file},
        {"mailbox_headers_identical", mailbox_headers_identical},
        {"mailbox_layout", mailbox_layout},
        {"offer_to_mailbox", offer_to_mailbox},
        {"ui_state_from_mailbox", ui_state_from_mailbox},
        {"ui_progress_percent", ui_progress_percent},
        {"ui_selection_clamps", ui_selection_clamps},
        {"ui_ascii_from_ucs2", ui_ascii_from_ucs2},
    };
    for (const auto& [name, test] : tests) {
        const int before = failures;
        test();
        std::printf("%s %s\n", failures == before ? "ok  " : "FAIL", name);
    }
    std::printf("%d failure(s)\n", failures);
    return failures ? 1 : 0;
}
