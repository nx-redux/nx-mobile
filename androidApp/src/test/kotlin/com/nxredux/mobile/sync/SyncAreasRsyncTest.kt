package com.nxredux.mobile.sync

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** [SyncAreas.inShared] against a real rsync ≥ 3.1 on the PATH (skipped when there is none, e.g. on CI). */
class SyncAreasRsyncTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun rsync_copies_exactly_the_files_in_shared_accepts() {
        val bin = hostRsync()
        assumeTrue("no rsync >= 3.1 on this machine", bin != null)
        val paths = listOf(
            "GB-gambatte/Tetris.gb.st0", ".minui/GB/Tetris.gb.0.bmp", "DC-flycast/Crazy Taxi.state", "N64-mupen64plus_next/M.state1",
            "GB-gambatte/Pokémon Crystal (USA) [!].st0", ".x-y/z", ".ra/token", ".minui/recent.txt", ".minui/placeholders/x.png",
            "NDS-advanced-drastic/config/tg5040/drastic.cfg", "DC-flycast/data/vmu.bin", "DC-flycast/nx-seeded.txt",
            "N64-mupen64plus/x", "music-player/list.m3u", "GB-gambatte/._T.st0", "GB-gambatte/.DS_Store", "minuisettings.txt",
        )
        val src = tmp.newFolder("src")
        paths.forEach { File(src, it).apply { parentFile.mkdirs(); writeText(it) } }
        val dst = tmp.newFolder("dst")
        val code = ProcessBuilder(listOf(bin!!, "-rt") + SyncAreas.sharedFilterArgs() + listOf(src.path + "/", dst.path + "/"))
            .redirectErrorStream(true).start().waitFor()
        assertEquals(0, code)
        val copied = dst.walkTopDown().filter { it.isFile }.map { it.relativeTo(dst).invariantSeparatorsPath }.toSortedSet()
        assertEquals(paths.filter(SyncAreas::inShared).toSortedSet(), copied)
    }
}
