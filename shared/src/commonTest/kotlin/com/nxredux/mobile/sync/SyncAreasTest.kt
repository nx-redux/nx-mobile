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
        assertEquals(listOf("--exclude=._*", "--exclude=.DS_Store", "--exclude=*.tmp", "--exclude=*.part"), SyncAreas.savesExcludeArgs())
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
}
