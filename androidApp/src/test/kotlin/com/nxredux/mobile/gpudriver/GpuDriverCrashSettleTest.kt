package com.nxredux.mobile.gpudriver

import com.nxredux.mobile.settings.AppSettings
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpuDriverCrashSettleTest {
    /** An [AppSettings] holding only the two driver values the settle reads and writes; anything else is not called. */
    private class Store {
        var marker: String? = null
        var crashes: String? = null
        val settings: AppSettings = Proxy.newProxyInstance(AppSettings::class.java.classLoader, arrayOf(AppSettings::class.java)) { _, method, args ->
            when (method.name) {
                "getGpuDriverMarker" -> marker
                "setGpuDriverMarker" -> { marker = args[0] as String?; Unit }
                "getGpuDriverCrashes" -> crashes
                "setGpuDriverCrashes" -> { crashes = args[0] as String?; Unit }
                else -> throw UnsupportedOperationException(method.name)
            }
        } as AppSettings
    }

    private val marker = GpuDriverCrash.Marker(4242, "Roms/PS2/God of War.iso", "God of War", "abc123", "Turnip v25")

    @Test fun noMarkerSaysNothing() {
        val s = Store()
        assertNull(GpuDriverCrashSettle.settle(s.settings, setOf(1, 2)))
        assertNull(s.crashes)
    }

    @Test fun liveLaunchKeepsItsMarker() {
        val s = Store().apply { marker = GpuDriverCrash.encode(this@GpuDriverCrashSettleTest.marker) }
        assertNull(GpuDriverCrashSettle.settle(s.settings, setOf(1, 4242)))
        assertEquals(GpuDriverCrash.encode(marker), s.marker)
        assertNull(s.crashes)
    }

    @Test fun deadLaunchRecordsTheCrashOnce() {
        val s = Store().apply { marker = GpuDriverCrash.encode(this@GpuDriverCrashSettleTest.marker) }
        assertEquals(GpuDriverCrash.notice(marker), GpuDriverCrashSettle.settle(s.settings, setOf(1, 2)))
        assertEquals(setOf("abc123"), GpuDriverCrash.crashed(s.crashes, "Roms/PS2/God of War.iso"))
        assertNull(s.marker)
        assertNull(GpuDriverCrashSettle.settle(s.settings, setOf(1, 2)))
        assertEquals(setOf("abc123"), GpuDriverCrash.crashed(s.crashes, "Roms/PS2/God of War.iso"))
    }

    @Test fun unreadableMarkerIsDroppedSilently() {
        val s = Store().apply { marker = "not a marker" }
        assertNull(GpuDriverCrashSettle.settle(s.settings, setOf(1)))
        assertNull(s.marker)
        assertNull(s.crashes)
    }
}
