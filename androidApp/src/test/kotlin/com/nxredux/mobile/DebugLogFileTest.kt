package com.nxredux.mobile

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDateTime

class DebugLogFileTest {
    @Test fun nameSortsByStartAndSaysWhichProcess() {
        val at = LocalDateTime.of(2026, 11, 9, 7, 5, 3)
        assertEquals("20261109-070503-app.log", DebugLogFile.name(at, game = false))
        assertEquals("20261109-070503-game.log", DebugLogFile.name(at, game = true))
    }

    @Test fun staleDropsOldestSessionsWithTheirRotations() {
        val names = listOf(
            "20261101-000000-app.log", "20261101-000000-app.log.1",
            "20261102-000000-game.log",
            "20261103-000000-app.log", "20261103-000000-app.log.1", "20261103-000000-app.log.2",
            "notes.txt",
        )
        assertEquals(
            listOf("20261102-000000-game.log", "20261101-000000-app.log", "20261101-000000-app.log.1"),
            DebugLogFile.stale(names, keep = 1),
        )
    }

    @Test fun staleKeepsEverythingUnderTheLimit() {
        assertEquals(emptyList<String>(), DebugLogFile.stale(listOf("a.log", "b.log.1", "b.log"), keep = 5))
    }

    @Test fun staleLeavesOtherFilesAlone() {
        assertEquals(emptyList<String>(), DebugLogFile.stale(listOf("readme.txt", "x.log.bak"), keep = 0))
    }

    @get:Rule val tmp = TemporaryFolder()

    @Test fun rotatingFileMovesFullFilesAlongAndKeepsTheNewest() {
        val f = File(tmp.root, "s.log")
        val r = RotatingFile(f, maxBytes = 4, rotations = 2)
        listOf("aaaa", "bbbb", "cccc", "dddd").forEach(r::write)   // each line fills a file (5 bytes with its newline)
        r.close()
        assertEquals("dddd\n", f.readText())
        assertEquals("cccc\n", File(tmp.root, "s.log.1").readText())
        assertEquals("bbbb\n", File(tmp.root, "s.log.2").readText())
        assertEquals(false, File(tmp.root, "s.log.3").exists())
    }

    @Test fun rotatingFileAppendsToAnExistingFile() {
        val f = File(tmp.root, "s.log").apply { writeText("old\n") }
        RotatingFile(f, maxBytes = 1024, rotations = 2).apply { write("new"); close() }
        assertEquals("old\nnew\n", f.readText())
    }

    @Test fun gameFileNamesCarryTheConsoleAndCore() {
        val at = LocalDateTime.of(2026, 11, 9, 7, 5, 3)
        assertEquals("20261109-070503-game-PS2-armsx2.log", DebugLogFile.name(at, game = true, label = "PS2-armsx2"))
        assertEquals("PS2-armsx2", DebugLogFile.label("PS2", "armsx2"))
        assertEquals("A_B-mel_n", DebugLogFile.label("A/B", "mel n"))
    }

    @Test fun labelledReplacesAnEarlierLabelAndKeepsTheStamp() {
        assertEquals("20261109-070503-game-GBA-mgba.log", DebugLogFile.labelled("20261109-070503-game.log", "GBA-mgba"))
        assertEquals("20261109-070503-game-GBA-mgba.log", DebugLogFile.labelled("20261109-070503-game-PS2-armsx2.log", "GBA-mgba"))
    }

    @Test fun movedFileKeepsItsLinesAndTakesItsRotations() {
        val f = File(tmp.root, "a.log")
        val r = RotatingFile(f, maxBytes = 4, rotations = 2)
        r.write("1111"); r.write("2222")   // the second rotates the first to a.log.1
        val to = File(tmp.root, "b.log")
        assertEquals(true, r.moveTo(to))
        r.write("3333")
        r.close()
        assertEquals(false, f.exists())
        assertEquals("1111\n", File(tmp.root, "b.log.2").readText())
        assertEquals("2222\n", File(tmp.root, "b.log.1").readText())
        assertEquals("3333\n", to.readText())
    }
}
