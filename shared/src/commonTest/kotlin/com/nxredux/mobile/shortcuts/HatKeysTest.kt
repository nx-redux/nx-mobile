package com.nxredux.mobile.shortcuts

import kotlin.test.Test
import kotlin.test.assertEquals

class HatKeysTest {
    private val hat = HatKeys()
    private val keys = mutableListOf<Pair<Int, Boolean>>()
    private fun read(x: Float, y: Float): List<Pair<Int, Boolean>> {
        keys.clear()
        hat.update(x, y) { code, down -> keys += code to down }
        return keys.toList()
    }

    @Test fun pressAndRelease() {
        assertEquals(listOf(KeyCodes.DPAD_UP to true), read(0f, -1f))
        assertEquals(emptyList(), read(0f, -1f))
        assertEquals(listOf(KeyCodes.DPAD_UP to false), read(0f, 0f))
    }

    @Test fun releaseComesBeforeTheOppositePress() {
        read(-1f, 0f)
        assertEquals(listOf(KeyCodes.DPAD_LEFT to false, KeyCodes.DPAD_RIGHT to true), read(1f, 0f))
    }

    @Test fun bothAxesAtOnce() {
        assertEquals(listOf(KeyCodes.DPAD_RIGHT to true, KeyCodes.DPAD_DOWN to true), read(1f, 1f))
    }

    @Test fun smallReadingsAreRest() {
        assertEquals(emptyList(), read(0.3f, -0.3f))
    }
}
