package com.nxredux.mobile.sync

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class SyncLogTailTest {
    private val dir = createTempDirectory("synclog").toFile()
    private val log = File(dir, "rsyncd.log")
    private val lines = mutableListOf<String>()
    private val tail = SyncLogTail(log) { lines += it }

    @After fun cleanUp() { dir.deleteRecursively() }

    @Test fun a_missing_log_reads_nothing() {
        tail.readNew(); tail.flush()
        assertEquals(emptyList<String>(), lines)
    }

    @Test fun whole_lines_come_out_once_and_a_partial_one_waits_for_its_newline() {
        log.writeText("one\ntw")
        tail.readNew()
        assertEquals(listOf("one"), lines)
        log.appendText("o\r\nthree\n")
        tail.readNew(); tail.readNew()
        assertEquals(listOf("one", "two", "three"), lines)
    }

    @Test fun flush_delivers_a_last_line_without_a_newline() {
        log.writeText("a\nlast")
        tail.readNew(); tail.flush(); tail.flush()
        assertEquals(listOf("a", "last"), lines)
    }

    @Test fun a_multibyte_character_split_across_reads_survives() {
        val bytes = "café\n".toByteArray(Charsets.UTF_8)
        log.writeBytes(bytes.copyOfRange(0, 4))   // "caf" + the first byte of é
        tail.readNew()
        log.appendBytes(bytes.copyOfRange(4, bytes.size))
        tail.readNew()
        assertEquals(listOf("café"), lines)
    }

    @Test fun a_recreated_shorter_log_is_read_from_its_start() {
        log.writeText("first run line\n")
        tail.readNew()
        log.writeText("x\n")
        tail.readNew()
        assertEquals(listOf("first run line", "x"), lines)
    }
}
