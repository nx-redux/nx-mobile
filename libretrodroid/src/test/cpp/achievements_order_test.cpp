// Host check that Achievements::list() is in the set's display order (the server's list order), not grouped by lock
// state. Built like achievements_session_test.cpp, with this file in its place:
//   c=$PWD/libretrodroid/src/main/cpp; r=$c/rcheevos/src; d=$(mktemp -d) && \
//   (cd "$d" && clang -c -fsanitize=address -DRC_DISABLE_LUA -DRC_CLIENT_SUPPORTS_HASH -I$c/rcheevos/include -I$r \
//     -I$c/libretro/libretro-common/include $r/rc_client.c $r/rc_libretro.c $r/rc_compat.c $r/rc_util.c \
//     $r/rc_version.c $r/rcheevos/*.c $r/rapi/*.c $r/rhash/*.c) && \
//   clang++ -std=c++17 -fsanitize=address -DRC_DISABLE_LUA -DRC_CLIENT_SUPPORTS_HASH -Ilibretrodroid/src/test/cpp/shim \
//     -I$c -I$c/rcheevos/include -I$r -I$c/libretro/libretro-common/include \
//     libretrodroid/src/test/cpp/achievements_order_test.cpp $c/achievements/achievements.cpp \
//     $c/achievements/memoryregiontable.cpp $c/logflag.cpp "$d"/*.o -o "$d/achievements_order_test" && "$d/achievements_order_test"
#include "achievements/achievements.h"
#include "achievements/achievementshash.h"
#include "rc_consoles.h"

#include <cstdio>
#include <cstdlib>
#include <string>

namespace libretrodroid {
const rc_hash_callbacks_t* AchievementsHash::callbacks() {
    static rc_hash_callbacks_t none {};
    return &none;
}
}

using namespace libretrodroid;

static uint8_t ram[0x10000];
static size_t coreGetSize(unsigned id) { return id == RETRO_MEMORY_SYSTEM_RAM ? sizeof(ram) : 0; }
static void* coreGetData(unsigned id) { return id == RETRO_MEMORY_SYSTEM_RAM ? ram : nullptr; }

static std::string ach(int id) {
    return "{\"ID\":" + std::to_string(id) + ",\"Title\":\"A" + std::to_string(id) + "\",\"Description\":\"\",\"Flags\":3,"
        "\"Points\":5,\"MemAddr\":\"0xH0010=1\",\"Author\":\"\",\"BadgeName\":\"00234\",\"Created\":1,\"Modified\":1}";
}

static const char* LOGIN = "{\"Success\":true,\"User\":\"User\",\"Token\":\"Token\",\"Score\":0,\"SoftcoreScore\":0,\"Messages\":0}";
// Tetris (GB) in miniature: the server lists the set in its display order, which is not id order.
static const std::string PATCH = "{\"Success\":true,\"GameId\":1234,\"Title\":\"Sample Game\",\"ConsoleId\":4,"
    "\"ImageIconUrl\":\"\",\"RichPresenceGameId\":1234,\"RichPresencePatch\":\"\","
    "\"Sets\":[{\"AchievementSetId\":1111,\"GameId\":1234,\"Title\":null,\"Type\":\"core\",\"ImageIconUrl\":\"\","
    "\"Achievements\":[" + ach(300) + "," + ach(46819) + "," + ach(100) + "," + ach(200) + "],\"Leaderboards\":[]}]}";
// The second in display order is unlocked: grouping by lock state would move it last.
static const char* START = "{\"Success\":true,\"Unlocks\":[{\"ID\":46819,\"When\":1}],\"HardcoreUnlocks\":[]}";

static void pump(Achievements& a) {
    for (int round = 0; round < 8; round++) {
        auto calls = a.takeServerCalls();
        if (calls.empty()) return;
        for (auto& c : calls) {
            std::string body = "{\"Success\":false}";
            if (c.postData.find("r=login2") != std::string::npos) body = LOGIN;
            else if (c.postData.find("r=achievementsets") != std::string::npos) body = PATCH;
            else if (c.postData.find("r=startsession") != std::string::npos) body = START;
            a.serverResponse(c.requestId, 200, body.c_str());
        }
        a.onFrame(true);
    }
}

int main() {
    auto& a = Achievements::getInstance();
    a.enable();
    a.setCoreMemoryAccessors(coreGetSize, coreGetData);
    a.login("User", "Token");
    pump(a);
    a.loadGame("0123456789abcdef0123456789abcdef", RC_CONSOLE_GAMEBOY);
    pump(a);

    std::string got;
    for (auto& i : a.list()) got += std::to_string(i.id) + " ";
    a.resetSession(false);
    const std::string want = "300 46819 100 200 ";
    if (got != want) { std::printf("FAIL list order: got %s, want %s\n", got.c_str(), want.c_str()); return EXIT_FAILURE; }
    std::printf("achievements_order_test: all passed\n");
    return EXIT_SUCCESS;
}
