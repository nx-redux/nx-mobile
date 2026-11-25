package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.options.PxRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkinKeypadTest {
    @Test
    fun theKeysReadAsAPhoneKeypad() {
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"), KeypadKey.entries.map { it.label })
    }

    @Test
    fun theGridSitsCentredInTheAreaWithSquareKeys() {
        val area = PxRect(100f, 50f, 600f, 800f)
        val keys = keypadKeys(area, dp = 1f)
        assertEquals(12, keys.size)
        for ((_, r) in keys) {
            assertEquals(r.width, r.height, 0.01f)
            assertTrue(r.x >= area.x && r.x + r.width <= area.x + area.width, "inside across")
            assertTrue(r.y >= area.y && r.y + r.height <= area.y + area.height, "inside down")
        }
        val left = keys.minOf { it.second.x }; val right = keys.maxOf { it.second.x + it.second.width }
        val top = keys.minOf { it.second.y }; val bottom = keys.maxOf { it.second.y + it.second.height }
        assertEquals(area.x + area.width / 2f, (left + right) / 2f, 0.5f)
        assertEquals(area.y + area.height / 2f, (top + bottom) / 2f, 0.5f)
        // reading order: 1 2 3 on the top row, * 0 # on the bottom
        val one = keys.first { it.first == KeypadKey.ONE }.second; val three = keys.first { it.first == KeypadKey.THREE }.second
        val hash = keys.first { it.first == KeypadKey.HASH }.second
        assertEquals(one.y, three.y); assertTrue(three.x > one.x); assertTrue(hash.y > one.y)
    }

    @Test
    fun aBigAreaKeepsTheKeysAThumbsSize() {
        val keys = keypadKeys(PxRect(0f, 0f, 3000f, 3000f), dp = 2f)
        assertTrue(keys.all { it.second.width <= KEYPAD_KEY_MAX_DP * 2f + 0.01f })
    }

    @Test
    fun aPointFindsItsKeyAndTheGapsFindNone() {
        val keys = keypadKeys(PxRect(0f, 0f, 400f, 600f), dp = 1f)
        val five = keys.first { it.first == KeypadKey.FIVE }.second
        assertEquals(KeypadKey.FIVE, keypadKeyAt(keys, five.x + five.width / 2f, five.y + five.height / 2f))
        assertNull(keypadKeyAt(keys, 1f, 1f))
    }

    @Test
    fun gearcolecoReadsTenKeysAsButtonsAndNineAndZeroAsTheStick() {
        val buttons = mapOf(
            KeypadKey.ONE to NxButton.Y, KeypadKey.TWO to NxButton.X, KeypadKey.THREE to NxButton.L1, KeypadKey.FOUR to NxButton.R1,
            KeypadKey.FIVE to NxButton.L2, KeypadKey.SIX to NxButton.R2, KeypadKey.SEVEN to NxButton.L3, KeypadKey.EIGHT to NxButton.R3,
            KeypadKey.STAR to NxButton.START, KeypadKey.HASH to NxButton.SELECT,
        )
        for ((k, b) in buttons) assertEquals(KeypadPress.Button(b), colecoKeypad(k), k.label)
        assertEquals(KeypadPress.Stick(0f, 1f), colecoKeypad(KeypadKey.NINE))   // the left stick's Y past the core's threshold
        assertEquals(KeypadPress.Stick(1f, 0f), colecoKeypad(KeypadKey.ZERO))   // its X
    }
}
