package com.nxredux.mobile.gpudriver

/** Settings → Emulation → GPU driver's platform side: the installed custom drivers, adding one from a picked zip, removing one. */
interface GpuDriverStore {
    /** Null: drivers can be used here. Text: why not, shown on the dimmed row. "": the feature is off, so no row at all. */
    fun unavailableReason(): String?

    /** The installed drivers, sorted by name. Reads their folders (small files), so it blocks briefly. */
    fun installed(): List<GpuDriver>

    /** Opens the zip picker and installs the pick: the outcome, or null when the pick was cancelled. Never selects the driver. */
    suspend fun add(): PackageCheck?

    /** Deletes the driver [id]'s folder; blocking. */
    fun remove(id: String)
}
