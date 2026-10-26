package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MicControlTest {
    @Test fun startsOff() {
        val c = MicControl(usable = true)
        assertFalse(c.on)
        assertEquals(MicSource.SILENCE, c.source)
    }

    @Test fun firstOnAsks() {
        val c = MicControl(usable = true)
        assertTrue(c.setOn(true))
        assertTrue(c.on)
        assertEquals(MicSource.NOISE, c.source)
    }

    @Test fun deniedWhileOnGivesNoise() {
        val c = MicControl(usable = true)
        c.setOn(true)
        c.setGranted(false)
        c.setAsked(true)
        assertEquals(MicSource.NOISE, c.source)
        assertTrue(c.on)
    }

    @Test fun askedOnceOnly() {
        val c = MicControl(usable = true)
        c.setAsked(true)
        assertFalse(c.setOn(true))
        assertEquals(MicSource.NOISE, c.source)
    }

    @Test fun grantedGivesLive() {
        val c = MicControl(usable = true)
        c.setGranted(true)
        assertFalse(c.setOn(true))
        assertEquals(MicSource.LIVE, c.source)
        c.setOn(false)
        assertEquals(MicSource.SILENCE, c.source)
    }

    @Test fun regrantFlipsNoiseToLive() {
        val c = MicControl(usable = true)
        c.setAsked(true)
        c.setOn(true)
        assertEquals(MicSource.NOISE, c.source)
        c.setGranted(true)
        assertEquals(MicSource.LIVE, c.source)
        c.setGranted(false)
        assertEquals(MicSource.NOISE, c.source)
    }

    @Test fun unusableNeverAsks() {
        val c = MicControl(usable = false)
        assertFalse(c.setOn(true))
        c.setGranted(true)
        assertEquals(MicSource.NOISE, c.source)
    }

    @Test fun sessionTurnsOff() {
        val c = MicControl(usable = true)
        c.setGranted(true)
        c.setOn(true)
        assertEquals(MicSource.LIVE, c.source)
        c.setSession(true)
        assertFalse(c.on)
        assertEquals(MicSource.SILENCE, c.source)
        assertFalse(c.shown)
        assertFalse(c.setOn(true))
        assertFalse(c.on)
        c.setSession(false)
        assertTrue(c.shown)
        assertFalse(c.on)
    }
}
