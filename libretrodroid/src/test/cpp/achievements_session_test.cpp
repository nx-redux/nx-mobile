// Host check for the Achievements session lifecycle (achievements.cpp over the real rcheevos, android log shimmed,
// the hash callbacks stubbed below). From the repo root:
//   c=$PWD/libretrodroid/src/main/cpp; r=$c/rcheevos/src; d=$(mktemp -d) && \
//   (cd "$d" && clang -c -fsanitize=address -DRC_DISABLE_LUA -DRC_CLIENT_SUPPORTS_HASH -I$c/rcheevos/include -I$r \
//     -I$c/libretro/libretro-common/include $r/rc_client.c $r/rc_libretro.c $r/rc_compat.c $r/rc_util.c \
//     $r/rc_version.c $r/rcheevos/*.c $r/rapi/*.c $r/rhash/*.c) && \
//   clang++ -std=c++17 -fsanitize=address -DRC_DISABLE_LUA -DRC_CLIENT_SUPPORTS_HASH -Ilibretrodroid/src/test/cpp/shim \
//     -I$c -I$c/rcheevos/include -I$r -I$c/libretro/libretro-common/include \
//     libretrodroid/src/test/cpp/achievements_session_test.cpp $c/achievements/achievements.cpp \
//     $c/achievements/memoryregiontable.cpp "$d"/*.o -o "$d/achievements_session_test" && "$d/achievements_session_test"
// Built with AddressSanitizer: touching the freed memory of a previous "core" aborts the run.
#include "achievements/achievements.h"
#include "achievements/achievementshash.h"
#include "rc_consoles.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>

namespace libretrodroid {
// The real one lives with the CHD reader (libchdr); the session tests never hash.
const rc_hash_callbacks_t* AchievementsHash::callbacks() {
    static rc_hash_callbacks_t none {};
    return &none;
}
}

using namespace libretrodroid;

static int failures = 0;

static void check(bool ok, const char* what) {
    if (!ok) { std::printf("FAIL %s\n", what); failures++; }
}

static uint8_t* coreRam = nullptr;
static size_t coreRamSize = 0;
static size_t coreGetSize(unsigned id) { return id == RETRO_MEMORY_SYSTEM_RAM ? coreRamSize : 0; }
static void* coreGetData(unsigned id) { return id == RETRO_MEMORY_SYSTEM_RAM ? coreRam : nullptr; }

static void newRam() {
    coreRamSize = 0x10000;
    coreRam = static_cast<uint8_t*>(std::calloc(1, coreRamSize));
}

static void freeRam() {
    std::free(coreRam);
    coreRam = nullptr; coreRamSize = 0;
}

static const char* HASH = "0123456789abcdef0123456789abcdef";

// A Mega Drive set whose one achievement reads 0x0010 every frame (response shapes from rcheevos' own tests).
static const char* LOGIN = "{\"Success\":true,\"User\":\"User\",\"Token\":\"Token\",\"Score\":0,\"SoftcoreScore\":0,\"Messages\":0}";
static const char* PATCH = "{\"Success\":true,\"GameId\":1234,\"Title\":\"Sample Game\",\"ConsoleId\":1,"
    "\"ImageIconUrl\":\"http://server/Images/112233.png\",\"RichPresenceGameId\":1234,\"RichPresencePatch\":\"\","
    "\"Sets\":[{\"AchievementSetId\":1111,\"GameId\":1234,\"Title\":null,\"Type\":\"core\","
    "\"ImageIconUrl\":\"http://server/Images/112233.png\",\"Achievements\":["
    "{\"ID\":5501,\"Title\":\"Ach1\",\"Description\":\"Desc1\",\"Flags\":3,\"Points\":5,\"MemAddr\":\"0xH0010=1\","
    "\"Author\":\"User1\",\"BadgeName\":\"00234\",\"Created\":1367266583,\"Modified\":1376929305}],"
    "\"Leaderboards\":[]}]}";
static const char* START = "{\"Success\":true,\"Unlocks\":[],\"HardcoreUnlocks\":[]}";

/** Plays the host: answers every queued server call, then lets the emulation thread apply the answers. */
static void pump(Achievements& a) {
    for (int round = 0; round < 8; round++) {
        auto calls = a.takeServerCalls();
        if (calls.empty()) return;
        for (auto& c : calls) {
            const char* body = "{\"Success\":false}";
            if (c.postData.find("r=login2") != std::string::npos) body = LOGIN;
            else if (c.postData.find("r=achievementsets") != std::string::npos) body = PATCH;
            else if (c.postData.find("r=patch") != std::string::npos) body = PATCH;
            else if (c.postData.find("r=startsession") != std::string::npos) body = START;
            a.serverResponse(c.requestId, 200, body);
        }
        a.onFrame(true);
    }
}

/** A first session as the app runs it: enable before the view, then the core, then login and game load. */
static void startLoadedSession(Achievements& a) {
    a.enable();                                         // AchievementsBridge.prepare(), before the view exists
    newRam();
    a.setCoreMemoryAccessors(coreGetSize, coreGetData);   // afterGameLoad()
    a.login("User", "Token");                           // after the first frame
    pump(a);
    a.loadGame(HASH, RC_CONSOLE_MEGA_DRIVE);            // on RA_EVENT_LOGIN_OK
    pump(a);
}

int main() {
    auto& a = Achievements::getInstance();

    // The normal path: the session logs in, loads the game and reads the core's memory every frame.
    startLoadedSession(a);
    check(a.isEnabled(), "first session enabled");
    check(rc_client_get_user_info(a.rawClient()) != nullptr, "first session logged in");
    check(rc_client_is_game_loaded(a.rawClient()), "first session has its game loaded");
    check(a.memoryRegionsReady(), "first session built its region table");
    a.onFrame(false);   // do_frame reads 0x0010 through the region table

    // The crash: a second view starts a game while the first was only stopped. Its prepare() enables (a no-op on the
    // live client), then LibretroDroid::create() resets before releasing the old core.
    a.enable();
    a.resetSession(true);
    freeRam();   // releaseCore(): anything still pointing into the old core's memory now faults under ASan
    check(a.isEnabled(), "an enabled client survives the reset as a fresh one");
    check(rc_client_get_user_info(a.rawClient()) == nullptr, "the fresh client has no user from the old session");
    check(!rc_client_is_game_loaded(a.rawClient()), "the fresh client has no game from the old session");
    check(!a.memoryRegionsReady(), "the reset dropped the old region table");
    check(a.takeServerCalls().empty() && a.takeEvents().empty(), "the reset dropped the old queues");
    a.onFrame(false);   // the step() that would read the freed core memory
    a.onFrame(true);

    // The second session then works normally on the new core.
    newRam();
    a.setCoreMemoryAccessors(coreGetSize, coreGetData);
    a.login("User", "Token");
    pump(a);
    a.loadGame(HASH, RC_CONSOLE_MEGA_DRIVE);
    pump(a);
    check(rc_client_is_game_loaded(a.rawClient()), "second session loads its game");
    check(a.memoryRegionsReady(), "second session builds its region table");
    a.onFrame(false);

    // Any change to the table's inputs invalidates it, and the next read rebuilds from the current inputs.
    a.setCoreMemoryAccessors(nullptr, nullptr);
    check(!a.memoryRegionsReady(), "clearing the accessors invalidates the region table");
    a.setCoreMemoryAccessors(coreGetSize, coreGetData);
    a.onFrame(false);
    check(a.memoryRegionsReady(), "the next frame rebuilds the table from the new accessors");
    a.setMemoryMap(nullptr);
    check(!a.memoryRegionsReady(), "a memory map change invalidates the region table");

    // destroy(): nothing stays enabled; a disabled reset stays disabled.
    a.resetSession(false);
    check(!a.isEnabled(), "resetSession(false) disables");
    a.resetSession(true);
    check(!a.isEnabled(), "resetSession(true) does not enable a disabled client");
    freeRam();

    if (failures == 0) std::printf("achievements_session_test: all passed\n");
    return failures == 0 ? EXIT_SUCCESS : EXIT_FAILURE;
}
