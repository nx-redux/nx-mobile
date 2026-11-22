// Host check for achievements/memoryregiontable.cpp over the real rcheevos rc_libretro. From the repo root:
//   c=$PWD/libretrodroid/src/main/cpp; r=$c/rcheevos/src; d=$(mktemp -d) && \
//   (cd "$d" && clang -c -fsanitize=address -DRC_DISABLE_LUA -DRC_CLIENT_SUPPORTS_HASH -I$c/rcheevos/include -I$r \
//     -I$c/libretro/libretro-common/include $r/rc_libretro.c $r/rc_compat.c $r/rc_util.c $r/rcheevos/*.c $r/rhash/*.c) && \
//   clang++ -std=c++17 -fsanitize=address -DRC_DISABLE_LUA -DRC_CLIENT_SUPPORTS_HASH -I$c -I$c/rcheevos/include -I$r \
//     -I$c/libretro/libretro-common/include libretrodroid/src/test/cpp/memoryregiontable_test.cpp \
//     $c/achievements/memoryregiontable.cpp "$d"/*.o -o "$d/memoryregiontable_test" && "$d/memoryregiontable_test"
// Built with AddressSanitizer: a read that touches a freed core buffer aborts the run.
#include "achievements/memoryregiontable.h"
#include "rc_consoles.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>

#include "check.h"

using namespace libretrodroid;

static uint8_t* coreRam = nullptr;
static size_t coreRamSize = 0;

static void coreMemoryInfo(uint32_t id, rc_libretro_core_memory_info_t* info) {
    info->data = id == RETRO_MEMORY_SYSTEM_RAM ? coreRam : nullptr;
    info->size = id == RETRO_MEMORY_SYSTEM_RAM ? coreRamSize : 0;
}

static uint8_t* newRam(uint8_t fill) {
    coreRamSize = 0x10000;
    coreRam = static_cast<uint8_t*>(std::malloc(coreRamSize));
    std::memset(coreRam, fill, coreRamSize);
    return coreRam;
}

int main() {
    uint8_t out[4];

    {
        MemoryRegionTable table;
        check(!table.ready(), "a new table is not ready");
        std::memset(out, 0xEE, sizeof(out));
        check(table.read(0, out, 4) == 0, "a new table reads nothing");
        check(out[0] == 0xEE, "a new table leaves the buffer untouched");
    }

    {
        MemoryRegionTable table;
        uint8_t* ram = newRam(0x11);
        ram[0x10] = 0x42;
        check(table.build(nullptr, coreMemoryInfo, RC_CONSOLE_MEGA_DRIVE), "build over the core's system RAM");
        check(table.ready(), "a built table is ready");
        check(table.count() >= 1, "a built table has regions");
        check(table.read(0x10, out, 1) == 1 && out[0] == 0x42, "a built table reads the core's RAM");

        // The crash: the core that owned this RAM goes away, a stale table must not read through it.
        table.invalidate();
        std::free(ram);
        coreRam = nullptr; coreRamSize = 0;
        check(!table.ready(), "an invalidated table is not ready");
        check(table.count() == 0, "an invalidated table has no regions");
        std::memset(out, 0xEE, sizeof(out));
        check(table.read(0x10, out, 4) == 0, "an invalidated table reads nothing");
        check(out[0] == 0xEE, "an invalidated table leaves the buffer untouched");
    }

    {
        MemoryRegionTable table;
        uint8_t* first = newRam(0x01);
        check(table.build(nullptr, coreMemoryInfo, RC_CONSOLE_MEGA_DRIVE), "first build");
        uint8_t* second = newRam(0x02);
        check(table.build(nullptr, coreMemoryInfo, RC_CONSOLE_MEGA_DRIVE), "second build");
        std::free(first);   // the first table's memory is gone: the rebuilt table must only point at the second
        check(table.read(0, out, 1) == 1 && out[0] == 0x02, "a rebuild replaces the old regions");
        std::free(second);
        coreRam = nullptr; coreRamSize = 0;
        table.invalidate();
    }

    {
        MemoryRegionTable table;
        uint8_t* ram = newRam(0x05);
        check(table.build(nullptr, coreMemoryInfo, RC_CONSOLE_MEGA_DRIVE), "build before a failed rebuild");
        coreRam = nullptr; coreRamSize = 0;   // the next core exposes no memory
        check(!table.build(nullptr, coreMemoryInfo, RC_CONSOLE_MEGA_DRIVE), "a build without memory fails");
        std::free(ram);
        check(!table.ready(), "a failed build leaves the table not ready (old regions dropped)");
        check(table.read(0, out, 1) == 0, "a failed build reads nothing");
    }

    if (failures == 0) std::printf("memoryregiontable_test: all passed\n");
    return failures == 0 ? EXIT_SUCCESS : EXIT_FAILURE;
}
