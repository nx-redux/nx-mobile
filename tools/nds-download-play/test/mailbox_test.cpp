// The mailbox our client and the core share, and how an offer is written into it.
#include "wmb_session.hpp"

#include <cstddef>
#include <cstdio>
#include <fstream>
#include <sstream>
#include <string>

using namespace MelonDsDs;

extern int failures;
#define CHECK(...) do { if (!(__VA_ARGS__)) { std::printf("  FAIL %s:%d: %s\n", __FILE__, __LINE__, #__VA_ARGS__); failures++; } } while (0)

static std::string Slurp(const std::string& path) {
    std::ifstream f(path, std::ios::binary);
    std::stringstream s;
    s << f.rdbuf();
    return s.str();
}

void mailbox_headers_identical() {
    const std::string client = Slurp("../source/mailbox.h");
    const std::string core = Slurp(std::string(CORE_DIR) + "/wmb_mailbox.h");
    CHECK(!client.empty());
    CHECK(client == core);
}

void mailbox_layout() {
    CHECK(sizeof(NxDlpOffer) == 860);
    CHECK(offsetof(NxDlpMailbox, offers) == 40);
    CHECK(offsetof(NxDlpMailbox, clientSeq) == 40 + 8 * 860);
    CHECK(sizeof(NXDLP_MAGIC) == 16);
}

void offer_to_mailbox() {
    WmbOffer o;
    o.host = {0x00, 0x08, 0xBF, 0x63, 0xB6, 0xB2};
    o.gameName = "Mario Party DS";
    o.hostName = "KotakNova";
    o.description = std::string(120, 'x');
    o.players = 1;
    o.maxPlayers = 4;
    o.icon[0x21F] = 0x5A;
    NxDlpOffer box {};
    WriteOffer(o, box);
    CHECK(box.mac[5] == 0xB2 && box.players == 1 && box.maxPlayers == 4);
    CHECK(box.gameName[0] == u'M' && box.gameName[13] == u'S' && box.gameName[14] == 0);
    CHECK(box.hostName[8] == u'a' && box.hostName[9] == 0);
    CHECK(box.description[94] == u'x' && box.description[95] == 0);
    CHECK(box.icon[0x21F] == 0x5A);
    WmbOffer accented;
    accented.gameName = "Pok\xC3\xA9mon"; // é in UTF-8
    WriteOffer(accented, box);
    CHECK(box.gameName[3] == 0x00E9 && box.gameName[7] == 0);
}
