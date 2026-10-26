package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals

class N3dsFrameFactorTest {
    @Test
    fun startsAtTheLaunchFactor() {
        assertEquals(3, N3dsFrameFactor(3).of(1200f, 1440f))
    }

    @Test
    fun aSentFactorWaitsForTheFrameSizeToChange() {
        val f = N3dsFrameFactor(2)
        f.sent(4, 800f, 960f)
        assertEquals(2, f.of(800f, 960f))   // the core has not drawn at 4x yet
        assertEquals(2, f.of(800f, 960f))
        assertEquals(4, f.of(1600f, 1920f))
        assertEquals(4, f.of(1600f, 1920f))
    }

    @Test
    fun aLayoutChangeWithNothingSentKeepsTheFactor() {
        val f = N3dsFrameFactor(3)
        assertEquals(3, f.of(1200f, 1440f))
        assertEquals(3, f.of(2160f, 720f))   // Side by side at 3x
    }

    @Test
    fun anUnchangedFactorWaitsForNothing() {
        val f = N3dsFrameFactor(3)
        f.sent(3, 1200f, 1440f)
        assertEquals(3, f.of(2160f, 720f))
    }

    @Test
    fun aSecondSendBeforeTheFirstArrivedWaitsFromTheSameFrame() {
        val f = N3dsFrameFactor(2)
        f.sent(4, 800f, 960f)
        f.sent(5, 800f, 960f)
        assertEquals(2, f.of(800f, 960f))
        assertEquals(5, f.of(2000f, 2400f))
    }

    @Test
    fun aSendAfterTheFirstArrivedUnseenWaitsFromTheNewFrame() {
        val f = N3dsFrameFactor(2)
        f.sent(4, 800f, 960f)
        f.sent(5, 1600f, 1920f)   // the core drew at 4x before any layout read it
        assertEquals(4, f.of(1600f, 1920f))
        assertEquals(5, f.of(2000f, 2400f))
    }

    @Test
    fun sendingTheShownFactorBackCancelsTheWait() {
        val f = N3dsFrameFactor(2)
        f.sent(4, 800f, 960f)
        f.sent(2, 800f, 960f)
        assertEquals(2, f.of(800f, 960f))
        assertEquals(2, f.of(2160f, 720f))
    }

    @Test
    fun aSendBeforeTheFirstFrameAppliesToIt() {
        val f = N3dsFrameFactor(3)
        f.sent(4, 0f, 0f)
        assertEquals(4, f.of(1600f, 1920f))
    }

    /** Quality on a 1840 px wide picture (Stacked, 400 px at 1x) on a display reaching 6x, launched at 2x: settles at 4x. */
    @Test
    fun qualitySettlesWithoutSwinging() {
        val values = mapOf(Res3dConsole.N3DS.modeKey to Nds3dMode.QUALITY.value)
        val f = N3dsFrameFactor(2)
        val sender = Nds3dSender()
        sender.offer(2, sizeChange = false, nowMs = 0)
        var coreFactor = 2
        val sent = mutableListOf<Int>()
        repeat(10) { frame ->
            // A layout before the core caught up (odd frames) and after it (even frames): the frame carries the core's factor.
            val frameW = 400f * coreFactor
            val frameH = 480f * coreFactor
            val current = N3ds3dResolution.currentScale(1840f, frameW, f.of(frameW, frameH))
            val value = Nds3dResolution.valueFor(values, current, 6, Res3dConsole.N3DS)
            sender.offer(value, sizeChange = false, nowMs = frame.toLong())?.let { sent += it; f.sent(it, frameW, frameH) }
            if (frame % 2 == 1) coreFactor = sent.lastOrNull() ?: coreFactor
        }
        assertEquals(listOf(4), sent)
    }
}
