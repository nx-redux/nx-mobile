package com.nxredux.mobile.sync

import com.nxredux.mobile.storage.SaveArchive
import com.nxredux.mobile.storage.SaveArea
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncAreasTest {
    @Test fun the_rule_list_is_the_handhelds_verbatim() {
        assertEquals(
            listOf(
                "- ._*", "- .DS_Store", "- /music-player/", "- /media-player/", "- /PORTS-portmaster/", "- /*-*/config/",
                "- /DC-flycast/*/", "- /DC-flycast/nx-seeded.txt", "- /N64-mupen64plus/", "- /.minui/placeholders/",
                "+ /.minui/", "+ /.minui/*/", "+ /.minui/*/**", "- /.*", "+ /*-*/", "+ /*-*/**", "- *",
            ),
            SyncAreas.SHARED_RULES,
        )
        assertEquals(SyncAreas.SHARED_RULES.map { "--filter=$it" }, SyncAreas.sharedFilterArgs())
        assertEquals(
            listOf("--exclude=._*", "--exclude=.DS_Store", "--exclude=*.tmp", "--exclude=*.part") +
                SyncAreas.SAVES_PER_DEVICE.map { "--exclude=$it" },
            SyncAreas.savesExcludeArgs(),
        )
        assertEquals(SyncAreas.SHARED_RULES.joinToString(" "), SyncAreas.daemonSharedFilter())
        assertEquals("._* .DS_Store *.tmp *.part", SyncAreas.daemonSavesExclude())
    }

    /** The response's cases, plus file names with glob and regex characters. */
    @Test fun in_shared_matches_the_handhelds_areas() {
        val inside = listOf(
            "GB-gambatte/Tetris.gb.st0", ".minui/GB/Tetris.gb.0.bmp", ".minui/GB/Tetris.gb.txt", "DC-flycast/Crazy Taxi.state",
            "N64-mupen64plus_next/Mario.state1", "PSP-ppsspp/x.ppst", "NDS-advanced-drastic/x.dss",
            "GB-gambatte/Pokémon Crystal (USA) [!].st0", "GBA-gpsp/a+b.state", "GB-gambatte/x\$.srm", "GB-gambatte/sub/deep.st0",
        )
        val outside = listOf(
            ".x-y/z", ".ra/token", ".minui/recent.txt", ".minui/shortcuts.txt", ".minui/placeholders/x.png",
            "NDS-advanced-drastic/config/tg5040/drastic.cfg", "DC-flycast/data/vmu.bin", "DC-flycast/nx-seeded.txt",
            "N64-mupen64plus/x", "music-player/list.m3u", "media-player/x", "PORTS-portmaster/.bashrc",
            "GB-gambatte/._Tetris.gb.st0", "GB-gambatte/.DS_Store", "minuisettings.txt", "game_logs.sqlite", "noshyphen/x",
        )
        inside.forEach { assertTrue(SyncAreas.inShared(it), it) }
        outside.forEach { assertFalse(SyncAreas.inShared(it), it) }
        assertFalse(SyncAreas.inShared(""))
        assertFalse(SyncAreas.inShared("GB-gambatte//x"))
    }

    @Test fun junk_is_macos_metadata_and_cut_short_writes() {
        assertTrue(SyncAreas.isJunk("GB/._Tetris.srm"))
        assertTrue(SyncAreas.isJunk(".DS_Store"))
        assertTrue(SyncAreas.isJunk("GB/Tetris.srm.1a2b3c4d.tmp"))
        assertTrue(SyncAreas.isJunk("GB/Tetris.srm.part"))
        assertFalse(SyncAreas.isJunk("GB/Tetris.srm"))
        assertFalse(SyncAreas.isJunk("GB/Tetris.part.srm"))
        assertNull(SaveArchive.areaOf("Saves/GB/Tetris.srm.1a2b3c4d.tmp"))
    }

    @Test fun export_and_import_use_the_same_areas() {
        assertEquals(SaveArea.STATES, SaveArchive.areaOf(".userdata/shared/GB-gambatte/Tetris.gb.st0"))
        assertEquals(SaveArea.MARKERS, SaveArchive.areaOf(".userdata/shared/.minui/GB/Tetris.gb.0.bmp"))
        assertEquals(SaveArea.SAVES, SaveArchive.areaOf("Saves/GB/Tetris.srm"))
        assertNull(SaveArchive.areaOf(".userdata/shared/NDS-advanced-drastic/config/tg5040/drastic.cfg"))
        assertNull(SaveArchive.areaOf(".userdata/shared/DC-flycast/data/vmu.bin"))
        assertNull(SaveArchive.areaOf(".userdata/shared/.minui/placeholders/x.png"))
        assertNull(SaveArchive.areaOf(".userdata/shared/.minui/recent.txt"))
        assertNull(SaveArchive.areaOf("Saves/GB/._Tetris.srm"))
        assertNull(SaveArchive.areaOf("Saves/GB/.DS_Store"))
        assertNull(SaveArchive.areaOf(".userdata/android/x/y"))
    }

    @Test fun per_device_files_are_each_emulators_settings_identities_and_caches() {
        val perDevice = listOf(
            "GC/User/Config/GFX.ini", "WII/User/Config/Dolphin.ini", "WII/User/Config/WiimoteNew.ini", "GC/User/Config/GCPadNew.ini",
            "WII/User/Config/GraphicMods/x.ini", "WII/User/Wii/shared2/sys/SYSCONF", "WII/User/Wii/shared2/sys/net/02/config.dat",
            "GC/User/Cache/GMSE01.uidcache", "WII/User/Cache/Shaders/Vulkan-uber-vs-3C6DED40.cache", "GC/User/ShaderCache/x.cache",
            "GC/User/Logs/dolphin.log", "WII/User/Dump/Textures/x.png",
            "3DS/Azahar/sysdata/mac.txt", "3DS/Azahar/sysdata/mcu.dat",
            "3DS/Azahar/nand/data/00000000000000000000000000000000/sysdata/00010017/00000000/config",
            "3DS/Azahar/config/config.ini", "3DS/Azahar/shaders/vulkan/pipeline/00040000001B5000-514343050A01.bin",
            "3DS/Azahar/shaders/opengl/transferable/0004000000030800.bin", "3DS/Azahar/cache/x", "3DS/Azahar/log/azahar_log.txt",
            "3DS/Azahar/dump/x", "PSP/SYSTEM/ppsspp.ini", "PSP/SYSTEM/controls.ini", "PSP/SYSTEM/CACHE/ULUS10160.cache",
        )
        val saves = listOf(
            "GBA/Advance Wars.srm", "DC/Soulcalibur (USA).A1.bin", "FBN/fbneo/mslug.fs", "N64/Paper Mario.srm",
            "PS2/God of War II (USA)/Mcd001.ps2", "PSP/SAVEDATA/ULUS10160DATA00/DATA.BIN", "PSP/Cheats/ULUS10160.ini",
            "PSP/NAND/flash0/font/ltn0.pgf", "NDS/melonDS DS/dldi_sd_card.bin",
            "GC/User/GC/USA/Card A/01-GMSE-super_mario_sunshine.gci", "GC/User/GC/SRAM.raw", "WII/User/GBA/Saves/x.sav",
            "WII/User/Wii/title/00010000/53423445/data/banner.bin", "WII/User/Wii/title/00010000/53423445/content/title.tmd",
            "WII/User/Wii/sys/uid.sys", "WII/User/Wii/shared2/wc24/misc.bin", "WII/User/Load/WiiSD.raw", "WII/User/Wii/fst.bin",
            "3DS/Azahar/sdmc/Nintendo 3DS/0/0/title/00040000/001b5000/data/00000001/main",
            "3DS/Azahar/nand/data/00000000000000000000000000000000/extdata/00048000/F000000B/user/CFL_DB.dat",
            "3DS/Azahar/nand/data/00000000000000000000000000000000/sysdata/00010026/00000000/eventlog.dat",
            "3DS/Azahar/sysdata/aes_keys.txt", "3DS/Azahar/nand/dbs/ticket.db",
            // Like the patterns' names, but not where they apply.
            "GBA/User/Config.srm", "PSX/SYSTEM/x.mcd", "GB/mac.txt", "3DS/sysdata/mac.txt", "3DS/Azahar/sysdata/mac.txt.bak",
        )
        perDevice.forEach { assertTrue(SyncAreas.isPerDevice(it), it) }
        saves.forEach { assertFalse(SyncAreas.isPerDevice(it), it) }
    }
}
