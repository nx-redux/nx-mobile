package com.nxredux.mobile.achievements

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AndroidRaStoreRootsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun accountPathsStayPrivate() = runBlocking {
        val private = tmp.newFolder("sd")
        val home = tmp.newFolder("home")
        val store = AndroidRaStore({ private }, { home })

        store.writeAtomic(RaPaths.LAST_SYNC, "1".encodeToByteArray())
        store.append(RaPaths.JOURNAL, "a\n".encodeToByteArray())

        assertEquals("1", File(private, "${RaPaths.ROOT}/${RaPaths.LAST_SYNC}").readText())
        assertEquals("a\n", File(private, "${RaPaths.ROOT}/${RaPaths.JOURNAL}").readText())
        assertFalse(File(home, RaPaths.ROOT).exists())
        // An account path that climbs out of the account tree is refused, even into the private .userdata.
        assertTrue(runCatching { store.writeAtomic("${RaPaths.ACCOUNT}/../../shared/x", byteArrayOf(1)) }.exceptionOrNull() is RaStoreException)
    }

    @Test fun anUnreadableFolderIsAReadFailureAndAnAbsentOneEmpty() = runBlocking {
        val private = tmp.newFolder("sd")
        val home = tmp.newFolder("home")
        val store = AndroidRaStore({ private }, { home })
        assertEquals(emptyList<String>(), store.list(RaPaths.GAMES))

        store.writeAtomic("${RaPaths.GAMES}/0123456789abcdef0123456789abcdef/sets.json", byteArrayOf(1))
        val games = File(home, "${RaPaths.ROOT}/${RaPaths.GAMES}")
        assertTrue(games.setReadable(false))
        try {
            assertTrue(runCatching { store.list(RaPaths.GAMES) }.exceptionOrNull() is RaStoreException)
        } finally {
            games.setReadable(true)
        }
    }

    @Test fun sharedPathsFollowTheHome() = runBlocking {
        val private = tmp.newFolder("sd")
        val first = tmp.newFolder("first")
        val second = tmp.newFolder("second")
        var home = first
        val store = AndroidRaStore({ private }, { home })
        val path = "${RaPaths.GAMES}/0123456789abcdef0123456789abcdef/sets.json"

        store.writeAtomic(path, byteArrayOf(1, 2))
        assertArrayEquals(byteArrayOf(1, 2), File(first, "${RaPaths.ROOT}/$path").readBytes())
        assertFalse(File(private, "${RaPaths.ROOT}/${RaPaths.SHARED}").exists())

        home = second   // the home changed: resolved per call
        assertNull(store.read(path))
        assertEquals(emptyList<String>(), store.list(RaPaths.GAMES))
        store.writeAtomic(path, byteArrayOf(3))
        assertArrayEquals(byteArrayOf(3), File(second, "${RaPaths.ROOT}/$path").readBytes())
        assertTrue(runCatching { store.read("../outside") }.exceptionOrNull() is RaStoreException)
    }
}
