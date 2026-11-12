package com.nxredux.mobile.achievements

import kotlin.test.Test
import kotlin.test.assertEquals

class RaScreenshotsTest {
    @Test fun namesTheGameTheAchievementAndTheTime() {
        assertEquals("1942 - Midway - 20261113-012700.png", RaScreenshots.fileName("1942", "Midway", "20261113-012700"))
    }

    @Test fun dropsWhatAFileNameCannotHold() {
        assertEquals("Ys I & II - Who are you - 1.png", RaScreenshots.fileName("Ys: I & II", "Who are you?", "1"))
        assertEquals("a b - c - 1.png", RaScreenshots.fileName("a/b", " c. ", "1"))
    }

    @Test fun emptyNamesGetAWordAndLongOnesAreCut() {
        assertEquals("Game - Achievement - 1.png", RaScreenshots.fileName("", "???", "1"))
        assertEquals("${"x".repeat(60)} - a - 1.png", RaScreenshots.fileName("x".repeat(90), "a", "1"))
    }
}
