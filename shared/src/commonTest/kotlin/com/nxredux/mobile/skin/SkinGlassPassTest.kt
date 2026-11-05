package com.nxredux.mobile.skin

import com.nxredux.mobile.ui.nx.SkinGlassPass
import kotlin.test.Test
import kotlin.test.assertEquals

class SkinGlassPassTest {
    private val log = mutableListOf<String>()
    private val pass = SkinGlassPass { x, y, down -> log += if (down) "down $x,$y" else "up" }

    @Test
    fun aFingerOnNoCapWhileACapIsHeldReachesTheGame() {   // Mario Kart 7: A held, then the map on the touch screen
        pass.down(1, 5f, 6f, onCap = false)
        pass.move(1, 7f, 8f)
        pass.up(1)
        assertEquals(listOf("down 5.0,6.0", "down 7.0,8.0", "up"), log)
    }

    @Test
    fun aFingerOnACapIsNeverPassed() {
        pass.down(1, 5f, 6f, onCap = true)
        pass.move(1, 7f, 8f)
        pass.up(1)
        assertEquals(emptyList(), log)
    }

    @Test
    fun onlyOneFingerDrivesTheGamePointer() {
        pass.down(1, 1f, 1f, onCap = false)
        pass.down(2, 9f, 9f, onCap = false)
        pass.move(2, 8f, 8f)
        pass.up(2)
        pass.move(1, 2f, 2f)
        assertEquals(listOf("down 1.0,1.0", "down 2.0,2.0"), log)
    }

    @Test
    fun theGestureEndingLiftsIt() {
        pass.down(1, 1f, 1f, onCap = false)
        pass.cancel()
        pass.cancel()
        assertEquals(listOf("down 1.0,1.0", "up"), log)
    }
}
