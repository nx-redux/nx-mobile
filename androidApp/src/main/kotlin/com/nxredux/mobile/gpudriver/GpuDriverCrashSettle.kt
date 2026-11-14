package com.nxredux.mobile.gpudriver

import com.nxredux.mobile.settings.AppSettings

/**
 * Reads the custom-driver launch marker ([AppSettings.gpuDriverMarker]) and, when its launch died, records the driver as
 * crashed for that game ([AppSettings.gpuDriverCrashes]) and clears the marker. Called by the menu when it comes back
 * (with every live process of the app) and by the game process before a custom-driver launch (with its own pid only:
 * a marker from any other process is a launch that is over).
 */
object GpuDriverCrashSettle {
    /** Returns the notice to show, or null. Records the crash and clears the marker when its launch died. */
    fun settle(settings: AppSettings, alivePids: Set<Int>): String? {
        val stored = settings.gpuDriverMarker ?: return null
        val marker = GpuDriverCrash.decode(stored)
        if (marker == null) {
            settings.gpuDriverMarker = null   // unreadable: it can never be settled, so it would only linger
            return null
        }
        if (!GpuDriverCrash.died(marker, alivePids)) return null
        settings.gpuDriverCrashes = GpuDriverCrash.record(settings.gpuDriverCrashes, marker.gamePath, marker.driverId)
        settings.gpuDriverMarker = null
        return GpuDriverCrash.notice(marker)
    }
}
