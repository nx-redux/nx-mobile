package com.nxredux.mobile.achievements

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class AndroidRaStoreLockTest {
    @get:Rule val tmp = TemporaryFolder()

    // Another process (a child JVM here) holding the journal's lock keeps this one out until it lets go.
    @Test fun lockedWaitsForAnotherProcess() = runBlocking {
        val private = tmp.newFolder("sd"); val home = tmp.newFolder("home")
        val store = AndroidRaStore({ private }, { home })
        store.append(RaPaths.JOURNAL, "a\n".encodeToByteArray())
        val lockFile = File(private, "${RaPaths.ROOT}/${RaPaths.JOURNAL}.lock")
        val child = holdLock(lockFile)
        try {
            assertEquals("locked", child.inputStream.bufferedReader().readLine())
            val entered = CompletableDeferred<Unit>()
            val inside = async(Dispatchers.IO) { store.locked(RaPaths.JOURNAL) { entered.complete(Unit) } }
            Thread.sleep(500)
            assertFalse("entered while another process held the lock", entered.isCompleted)
            child.outputStream.apply { write('\n'.code); flush() }
            withTimeout(10_000) { inside.await() }
            assertTrue(entered.isCompleted)
        } finally {
            child.destroyForcibly()
        }
    }

    // Two stores in one process (each service builds its own) take turns rather than failing on an overlapping lock.
    @Test fun twoStoresInOneProcessTakeTurns() = runBlocking {
        val private = tmp.newFolder("sd"); val home = tmp.newFolder("home")
        val a = AndroidRaStore({ private }, { home }); val b = AndroidRaStore({ private }, { home })
        val release = CompletableDeferred<Unit>(); val held = CompletableDeferred<Unit>()
        val order = ArrayList<String>()
        val first = launch(Dispatchers.IO) { a.locked(RaPaths.JOURNAL) { held.complete(Unit); release.await(); synchronized(order) { order += "a" } } }
        held.await()
        val second = launch(Dispatchers.IO) { b.locked(RaPaths.JOURNAL) { synchronized(order) { order += "b" } } }
        Thread.sleep(200)
        release.complete(Unit)
        withTimeout(10_000) { first.join(); second.join() }
        assertEquals(listOf("a", "b"), order)
    }

    // A caller cancelled while it waits on another process's lock lets go of the lock it gets later: the next caller in
    // this process still locks, rather than meeting an overlapping lock it can never take.
    @Test fun aCallerCancelledWhileWaitingLeavesNoLockBehind() = runBlocking {
        val private = tmp.newFolder("sd"); val home = tmp.newFolder("home")
        val store = AndroidRaStore({ private }, { home })
        store.append(RaPaths.JOURNAL, "a\n".encodeToByteArray())
        val lockFile = File(private, "${RaPaths.ROOT}/${RaPaths.JOURNAL}.lock")
        val child = holdLock(lockFile)
        try {
            assertEquals("locked", child.inputStream.bufferedReader().readLine())
            var ran = false
            // Off the IO pool, as a menu caller is: the hop back from the lock is where a cancel used to land.
            val waiting = launch(Dispatchers.Default) { store.locked(RaPaths.JOURNAL) { ran = true } }
            Thread.sleep(500)
            waiting.cancel()
            child.outputStream.apply { write('\n'.code); flush() }
            withTimeout(10_000) { waiting.join() }
            assertFalse("ran after its caller was cancelled", ran)
            // The next caller really holds the OS lock (not run unlocked past an overlap): another process waits for it.
            val other = withTimeout(10_000) {
                store.locked(RaPaths.JOURNAL) {
                    holdLock(lockFile).also { o ->
                        Thread.sleep(500)
                        assertEquals("another process locked while this one held it", 0, o.inputStream.available())
                    }
                }
            }
            try {
                assertEquals("locked", other.inputStream.bufferedReader().readLine())
            } finally {
                other.destroyForcibly()
            }
        } finally {
            child.destroyForcibly()
        }
    }

    // The menu's sync removes one entry and the game process appends one of the same length: the file has its old size,
    // but the game's journal still sees it changed, so it never appends a second copy.
    @Test fun aSameSizeRewriteChangesTheStamp() = runBlocking {
        val private = tmp.newFolder("sd"); val home = tmp.newFolder("home")
        val gameStore = AndroidRaStore({ private }, { home }); val menuStore = AndroidRaStore({ private }, { home })
        val game = RaJournal(gameStore, RaPaths.JOURNAL); val menu = RaJournal(menuStore, RaPaths.JOURNAL)
        val e1 = RaUnlock("me", 1, "aaaa", 100L); val e2 = RaUnlock("me", 2, "aaaa", 101L)
        game.append(e1)
        val before = gameStore.stamp(RaPaths.JOURNAL)
        menu.remove(listOf(e1)); menu.append(e2)
        assertTrue(before != gameStore.stamp(RaPaths.JOURNAL))
        assertFalse(game.append(e2))
        assertEquals(listOf(e2), game.entries())
        assertEquals(null, gameStore.stamp("${RaPaths.PENDING}/absent.jsonl"))
    }

    /** A JVM that locks [file], prints "locked" and lets go when it reads a line. */
    private fun holdLock(file: File): Process {
        file.parentFile.mkdirs()
        val src = File(tmp.newFolder(), "Locker.java")
        src.writeText(
            """
            import java.io.RandomAccessFile;
            public class Locker {
                public static void main(String[] args) throws Exception {
                    try (RandomAccessFile f = new RandomAccessFile(args[0], "rw"); java.nio.channels.FileLock l = f.getChannel().lock()) {
                        System.out.println("locked"); System.out.flush();
                        System.in.read();
                    }
                }
            }
            """.trimIndent(),
        )
        val java = File(System.getProperty("java.home"), "bin/java").path
        return ProcessBuilder(java, src.path, file.path).redirectError(ProcessBuilder.Redirect.INHERIT).start().also {
            assertFalse(it.waitFor(0, TimeUnit.MILLISECONDS))
        }
    }
}
