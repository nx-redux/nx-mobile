package com.nxredux.mobile.options

import com.nxredux.mobile.runner.Res3dConsole

/**
 * Console Settings for the PS2: which BIOS dump ARMSX2 boots when Bios/PS2/ holds more than one. The core scans its BIOS
 * folder for any ".bin" and takes the first it likes; the row forces one by name (`armsx2_bios`), read when the game starts.
 * The choices are the folder's files, so they come from the caller ([rowsFor]), like the Overlay row's.
 */
object Ps2Options {
    const val BIOS = "nx_ps2_bios"
    const val AUTO = "auto"
    /** ARMSX2's own option for it, locked in Emulator Settings. */
    const val CORE_KEY = "armsx2_bios"

    /** The BIOS row offering Auto and [files] (sorted); with no files it is the placeholder the lookups in [ConsoleSettings.all] find. */
    fun biosRow(files: List<String>) = FrontendOption(
        BIOS, "PS2 BIOS", listOf(AUTO) + files.sorted(), AUTO,
        "The BIOS dump the PS2 starts with, from Bios/PS2/.\nAuto lets the core choose.\nSet when the game starts.",
        brief = "Set at launch. Auto: the core chooses",
        visual = false, valueLabels = listOf("Auto") + files.sorted(), atLaunch = true,
    )

    /** The row only while there is a choice to make (two or more files). */
    fun rowsFor(files: List<String>): List<FrontendOption> = if (files.size > 1) listOf(biosRow(files)) else emptyList()

    /** The core option for the stored pick: that file while it is still in [files], else Auto (a deleted dump). */
    fun biosOption(stored: String?, files: List<String>): Map<String, String> =
        mapOf(CORE_KEY to (stored?.takeIf { it in files } ?: AUTO))

    /** The 3D Resolution rows (mode and Max Scale; Performance is native, so no Scale), as the DS and 3DS ones. */
    fun resolutionRows(displayMax: Int?): List<FrontendOption> = resolutionRows(Res3dConsole.PS2, displayMax)

    fun appliesTo(tag: String): Boolean = tag.trim().uppercase() == "PS2"
}
