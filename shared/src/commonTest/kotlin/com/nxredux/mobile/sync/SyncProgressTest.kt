package com.nxredux.mobile.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncProgressTest {
    @Test fun progress2_lines_give_files_done_of_total() {
        assertEquals(StepProgress(transferred = 1, done = 6, total = 6), SyncProgress.progress("              2  50%    0.00kB/s    0:00:00 (xfr#1, to-chk=0/6)"))
        assertEquals(StepProgress(transferred = 0, done = 3, total = 6), SyncProgress.progress("              0   0%    0.00kB/s    0:00:00 (xfr#0, to-chk=3/6)"))
        assertEquals(StepProgress(transferred = 4, done = 2, total = 9), SyncProgress.progress(" 1,234  1%  1.0MB/s 0:00:01 (xfr#4, to-check=7/9)"))
        assertNull(SyncProgress.progress("              2  50%    0.00kB/s    0:00:00  "))
        assertNull(SyncProgress.progress("GB-gambatte/t.gb.st0"))
    }

    @Test fun file_lines_are_what_the_log_shows() {
        assertEquals("GB-gambatte/t.gb.st0", SyncProgress.fileLine("GB-gambatte/t.gb.st0"))
        assertEquals("GB/Pokémon (USA).srm", SyncProgress.fileLine("GB/Pokémon (USA).srm"))
        listOf("", "   ", "sending incremental file list", "receiving incremental file list", "sent 172 bytes  received 15 bytes  374.00 bytes/sec",
            "total size is 4  speedup is 0.02", "GB-gambatte/", ".minui/GB/", "              2  50%    0.00kB/s    0:00:00  ",
            "     0   0%    0.00kB/s    0:00:00 (xfr#0, to-chk=3/6)",
            "rsync: [sender] change_dir \"/r/.media\" (in roms) failed: No such file or directory (2)", "rsync error: some files/attrs were not transferred (code 23) at main.c(1338) [sender=3.4.1]",
            "@ERROR: Unknown module 'x'").forEach { assertNull(SyncProgress.fileLine(it), it) }
    }

    @Test fun daemon_log_lines_name_module_direction_and_path() {
        assertEquals(DaemonEntry("shared", received = true, path = "GB-gambatte/t.gb.st0"), SyncProgress.daemonLine("2026/10/14 23:28:27 [13004] shared recv GB-gambatte/t.gb.st0"))
        assertEquals(DaemonEntry("saves", received = false, path = "GB/a b.srm"), SyncProgress.daemonLine("2026/10/14 23:28:27 [13018] saves send GB/a b.srm"))
        assertNull(SyncProgress.daemonLine("2026/10/14 23:28:27 [13004] rsync to shared/ from UNDETERMINED (127.0.0.1)"))
        assertNull(SyncProgress.daemonLine("2026/10/14 23:28:27 [13004] sent 46 bytes  received 232 bytes  total size 4"))
        assertNull(SyncProgress.daemonLine("garbage"))
    }

    @Test fun daemon_lines_for_rom_modules() {
        assertEquals(DaemonEntry("roms", true, "Game Boy (GB)/a.gb"), SyncProgress.daemonLine("2026/10/15 12:00:00 [9] roms recv Game Boy (GB)/a.gb"))
        assertEquals(DaemonEntry("extra-1", false, "a.gb"), SyncProgress.daemonLine("2026/10/15 12:00:00 [9] extra-1 send a.gb"))
        assertEquals(DaemonEntry("nx-art", false, "y/GB/.media/boxart/c.png"), SyncProgress.daemonLine("2026/10/15 12:00:00 [9] nx-art send y/GB/.media/boxart/c.png"))
        assertEquals(DaemonEntry("bios", true, "PS/scph5501.bin"), SyncProgress.daemonLine("2026/10/15 12:00:00 [9] bios recv PS/scph5501.bin"))
        assertEquals(DaemonEntry("collections", true, "RPGs.txt"), SyncProgress.daemonLine("2026/10/15 12:00:00 [9] collections recv RPGs.txt"))
    }
}
