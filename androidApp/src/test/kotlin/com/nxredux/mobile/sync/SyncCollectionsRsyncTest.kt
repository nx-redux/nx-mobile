package com.nxredux.mobile.sync

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Collections sync's pull, merge and push ([SyncCollectionFiles], [SyncCommands.collectionsArgs]) against a real rsync
 * ≥ 3.1 daemon started with [SyncCommands.daemonConf] (skipped when there is no such rsync, e.g. on CI).
 */
class SyncCollectionsRsyncTest {
    @get:Rule val tmp = TemporaryFolder()
    private var daemon: Process? = null

    @After fun stopDaemon() {
        daemon?.let { it.destroy(); if (!it.waitFor(5, TimeUnit.SECONDS)) it.destroyForcibly() }
    }

    private fun rsync(): String? = listOf("/opt/homebrew/bin/rsync", "/usr/local/bin/rsync", "/usr/bin/rsync").firstOrNull { path ->
        File(path).canExecute() && runCatching {
            val out = ProcessBuilder(path, "--version").redirectErrorStream(true).start().inputStream.bufferedReader().readText()
            Regex("""version (\d+)\.(\d+)""").find(out)?.destructured?.let { (a, b) -> a.toInt() > 3 || (a.toInt() == 3 && b.toInt() >= 1) } == true
        }.getOrDefault(false)
    }

    private class Served(val bin: String, val port: Int, val collections: File)

    /** The daemon, serving `[collections]` unless [withCollections] is false. */
    private fun serve(withCollections: Boolean = true): Served {
        val bin = rsync()
        assumeTrue("no rsync >= 3.1 on this machine", bin != null)
        val work = tmp.newFolder("work")
        val collections = tmp.newFolder("served-collections")
        val port = ServerSocket(0).use { it.localPort }
        val conf = SyncCommands.daemonConf("127.0.0.1", shared = tmp.newFolder("sh").path, saves = tmp.newFolder("sv").path,
            pidFile = File(work, "rsyncd.pid").path, logFile = File(work, "sync.log").path,
            collections = collections.path.takeIf { withCollections })
            .replace("port = ${SyncWire.RSYNC_PORT}\n", "port = $port\n")
        val confFile = File(work, "rsyncd.conf").apply { writeText(conf) }
        daemon = ProcessBuilder(bin!!, "--daemon", "--no-detach", "--config=${confFile.path}")
            .redirectErrorStream(true).redirectOutput(File(work, "daemon.out")).start()
        val deadline = System.currentTimeMillis() + 5_000
        while (runCatching { Socket("127.0.0.1", port).close() }.isFailure) {
            check(System.currentTimeMillis() < deadline) { "daemon did not listen: ${File(work, "daemon.out").readText()}" }
            Thread.sleep(50)
        }
        return Served(bin, port, collections)
    }

    /** Runs the args with the daemon's port swapped in. */
    private fun runner(port: Int): suspend (List<String>, (String) -> Unit) -> Int = { args, onLine ->
        val p = ProcessBuilder(args.map { it.replace("127.0.0.1:${SyncWire.RSYNC_PORT}/", "127.0.0.1:$port/") }).redirectErrorStream(true).start()
        p.inputStream.bufferedReader().forEachLine(onLine)
        p.waitFor()
    }

    @Test fun pull_merge_push_moves_only_changed_collections_and_never_map_txt() = runBlocking {
        val s = serve()
        File(s.collections, "RPGs.txt").writeText("/Roms/Nintendo ES (FC)/B.nes\r\n")
        File(s.collections, "Same.txt").apply { writeText("/Roms/Game Boy (GB)/T.gb\n"); setLastModified(1_700_000_000_000L) }
        File(s.collections, "Map.txt").writeText("x\ty\n")
        File(s.collections, "notes.md").writeText("n\n")
        File(s.collections, "Sub").mkdirs(); File(s.collections, "Sub/Deep.txt").writeText("d\n")
        val pullCache = File(tmp.root, "pull")
        val pull = SyncCollectionFiles.pull(runner(s.port), s.bin, "127.0.0.1", pullCache)
        assertEquals(pull.lines.joinToString("\n"), 0, pull.code)
        assertEquals(setOf("RPGs.txt", "Same.txt"), pull.files.keys)
        assertFalse(pullCache.exists())   // the cache is gone again

        val paths = CollectionPaths(pairs = listOf(ConsolePair("Famicom (FC)", "Nintendo ES (FC)", "FC")))
        val plan = SyncCollections.plan(mapOf("RPGs" to listOf("/Roms/Famicom (FC)/A.nes"), "Same" to listOf("/Roms/Game Boy (GB)/T.gb"),
            "Mine" to listOf("/Roms/Famicom (FC)/M.nes")), pull.files, paths)
        assertEquals(setOf("RPGs.txt", "Mine.txt"), plan.peer.keys)
        val pushCache = File(tmp.root, "push")
        val (code, lines) = SyncCollectionFiles.push(runner(s.port), s.bin, "127.0.0.1", pushCache, plan.peer + ("map.txt" to "evil\n"))
        assertEquals(lines.joinToString("\n"), 0, code)
        assertFalse(pushCache.exists())
        assertEquals("/Roms/Nintendo ES (FC)/B.nes\n/Roms/Nintendo ES (FC)/A.nes\n", File(s.collections, "RPGs.txt").readText())
        assertEquals("/Roms/Nintendo ES (FC)/M.nes\n", File(s.collections, "Mine.txt").readText())
        assertEquals(1_700_000_000_000L, File(s.collections, "Same.txt").lastModified())   // unchanged: never pushed
        assertEquals("x\ty\n", File(s.collections, "Map.txt").readText())
    }

    /** An upper-case `.TXT` on the peer is pulled and merged: both sides' lines survive, under the peer's name there. */
    @Test fun an_upper_case_txt_collection_is_merged_not_replaced() = runBlocking {
        val s = serve()
        File(s.collections, "Faves.TXT").writeText("/Roms/Game Boy (GB)/B.gb\n")
        val pull = SyncCollectionFiles.pull(runner(s.port), s.bin, "127.0.0.1", File(tmp.root, "pull"))
        assertEquals(pull.lines.joinToString("\n"), 0, pull.code)
        assertEquals(setOf("Faves.TXT"), pull.files.keys)
        val plan = SyncCollections.plan(mapOf("faves" to listOf("/Roms/Game Boy (GB)/A.gb")), pull.files, CollectionPaths())
        assertEquals(mapOf("faves" to listOf("/Roms/Game Boy (GB)/A.gb", "/Roms/Game Boy (GB)/B.gb")), plan.own)
        val (code, lines) = SyncCollectionFiles.push(runner(s.port), s.bin, "127.0.0.1", File(tmp.root, "push"), plan.peer)
        assertEquals(lines.joinToString("\n"), 0, code)
        assertEquals(listOf("Faves.TXT"), s.collections.list()!!.toList())
        assertEquals("/Roms/Game Boy (GB)/B.gb\n/Roms/Game Boy (GB)/A.gb\n", File(s.collections, "Faves.TXT").readText())
    }

    /** A file that is not UTF-8 is reported, not decoded with replacement characters (which a push would spread). */
    @Test fun a_file_that_is_not_utf8_is_reported_unreadable() = runBlocking {
        val s = serve()
        File(s.collections, "Latin.txt").writeBytes(byteArrayOf(0x50, 0x6f, 0x6b, 0xe9.toByte(), 0x0a))
        File(s.collections, "Ok.txt").writeText("/Roms/Game Boy (GB)/Pokémon.gb\n")
        val pull = SyncCollectionFiles.pull(runner(s.port), s.bin, "127.0.0.1", File(tmp.root, "pull"))
        assertEquals(0, pull.code)
        assertEquals(setOf("Ok.txt"), pull.files.keys)
        assertEquals("/Roms/Game Boy (GB)/Pokémon.gb\n", pull.files["Ok.txt"])
        assertEquals(setOf("Latin.txt"), pull.unreadable)
    }

    /** No `--update`: a peer file newer than the merged one (its clock ahead) is still replaced. */
    @Test fun a_push_replaces_a_peer_file_with_a_newer_time() = runBlocking {
        val s = serve()
        File(s.collections, "RPGs.txt").apply { writeText("old\n"); setLastModified(System.currentTimeMillis() + 3_600_000L) }
        val (code, lines) = SyncCollectionFiles.push(runner(s.port), s.bin, "127.0.0.1", File(tmp.root, "push"), mapOf("RPGs.txt" to "old\nnew\n"))
        assertEquals(lines.joinToString("\n"), 0, code)
        assertEquals("old\nnew\n", File(s.collections, "RPGs.txt").readText())
    }

    @Test fun a_daemon_without_the_module_says_unknown_module() = runBlocking {
        val s = serve(withCollections = false)
        val pull = SyncCollectionFiles.pull(runner(s.port), s.bin, "127.0.0.1", File(tmp.root, "pull"))
        assertEquals(5, pull.code)
        assertTrue(pull.lines.joinToString("\n"), pull.lines.any { "Unknown module" in it })
        assertTrue(pull.files.isEmpty())
    }
}
