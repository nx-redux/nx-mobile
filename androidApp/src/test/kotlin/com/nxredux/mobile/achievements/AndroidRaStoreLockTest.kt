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

    /** A JVM that locks [file], prints "locked" and lets go when it reads a line. */
    private fun holdLock(file: File): Process {
        file.parentFile.mkdirs()
        val src = File(tmp.newFolder("locker"), "Locker.java")
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
