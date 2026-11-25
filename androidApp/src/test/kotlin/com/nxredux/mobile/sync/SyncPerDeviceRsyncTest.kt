package com.nxredux.mobile.sync

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [SyncAreas.SAVES_PER_DEVICE] between two phones: [SyncCommands.clientArgs] against a real rsync ≥ 3.1 daemon started
 * with [SyncCommands.daemonConf] (skipped when there is no such rsync, e.g. on CI).
 */
class SyncPerDeviceRsyncTest {
    @get:Rule val tmp = TemporaryFolder()
    private val daemon = RsyncDaemon()
    private lateinit var bin: String
    private lateinit var served: File
    private var port = 0

    private val perDevice = listOf(
        "GC/User/Config/GFX.ini", "WII/User/Config/WiimoteNew.ini", "WII/User/Wii/shared2/sys/SYSCONF",
        "WII/User/Cache/Shaders/Vulkan-uber-vs-3C6DED40.cache", "GC/User/Logs/dolphin.log", "3DS/Azahar/sysdata/mac.txt",
        "3DS/Azahar/nand/data/00000000000000000000000000000000/sysdata/00010017/00000000/config",
        "3DS/Azahar/shaders/vulkan/pipeline/x.bin", "PSP/SYSTEM/ppsspp.ini", "PSP/SYSTEM/CACHE/x.cache",
    )
    private val saves = listOf(
        "GBA/Advance Wars.srm", "PS2/God of War II (USA)/Mcd001.ps2", "PSP/SAVEDATA/ULUS10160DATA00/DATA.BIN",
        "GC/User/GC/USA/Card A/01-GMSE-super_mario_sunshine.gci", "WII/User/Wii/title/00010000/53423445/data/banner.bin",
        "3DS/Azahar/sdmc/Nintendo 3DS/0/0/title/00040000/001b5000/data/00000001/main", "3DS/Azahar/sysdata/aes_keys.txt",
    )

    @Before fun serve() {
        val found = hostRsync()
        assumeTrue("no rsync >= 3.1 on this machine", found != null)
        bin = found!!
        val work = tmp.newFolder("work")
        served = tmp.newFolder("served-saves")
        val conf = SyncCommands.daemonConf("127.0.0.1", shared = tmp.newFolder("served-shared").path, saves = served.path,
            pidFile = File(work, "rsyncd.pid").path, logFile = File(work, "sync.log").path)
        port = daemon.start(bin, work, conf)
    }

    @After fun stopDaemon() {
        daemon.stop()
    }

    private fun run(args: List<String>): Int {
        val p = ProcessBuilder(swapPort(args, port)).redirectErrorStream(true).start()
        p.inputStream.bufferedReader().readText()
        return p.waitFor()
    }

    private fun client(step: SyncStep, root: File) = run(SyncCommands.clientArgs(bin, step, "127.0.0.1", root.path))

    private fun write(root: File, paths: List<String>, text: String) =
        paths.forEach { File(root, it).apply { parentFile.mkdirs(); writeText(text) } }

    private fun files(root: File): Set<String> =
        root.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).invariantSeparatorsPath }.toSortedSet()

    @Test fun real_saves_cross_both_ways_and_per_device_files_stay_put() {
        val phone = tmp.newFolder("phone")
        val mine = File(phone, "Saves")
        write(mine, perDevice + saves, "mine")
        write(served, perDevice, "theirs")
        assertEquals(0, client(SyncStep.SEND_SAVES, phone))
        // The peer got the saves; its own per-device files are untouched.
        assertEquals((perDevice + saves).toSortedSet(), files(served))
        perDevice.forEach { assertEquals(it, "theirs", File(served, it).readText()) }
        saves.forEach { assertEquals(it, "mine", File(served, it).readText()) }

        val other = tmp.newFolder("other")
        File(other, "Saves").mkdirs()
        assertEquals(0, client(SyncStep.RECEIVE_SAVES, other))
        assertEquals(saves.toSortedSet(), files(File(other, "Saves")))
    }

    /** A peer phone on a version without these excludes still syncs: the daemon takes what it sends. */
    @Test fun a_client_without_the_excludes_is_not_refused() {
        val phone = tmp.newFolder("old")
        write(File(phone, "Saves"), perDevice + saves, "old")
        val args = SyncCommands.clientArgs(bin, SyncStep.SEND_SAVES, "127.0.0.1", phone.path)
            .filterNot { a -> SyncAreas.SAVES_PER_DEVICE.any { a == "--exclude=$it" } }
        assertEquals(0, run(args))
        assertEquals((perDevice + saves).toSortedSet(), files(served))
    }
}
