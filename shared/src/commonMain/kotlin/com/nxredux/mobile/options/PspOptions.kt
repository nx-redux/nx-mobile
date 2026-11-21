package com.nxredux.mobile.options

import com.nxredux.mobile.runner.PspResolution
import com.nxredux.mobile.runner.Res3dConsole

/** PSP settings the app owns: PPSSPP's ad hoc play over the local network. */
object PspOptions {
    /**
     * Local Wireless (default on): a game's ad hoc multiplayer finds other NX Redux devices on the same network, through the
     * runner's own ad hoc server on the phone that hosts. Read when the game starts.
     */
    const val LOCAL_WIRELESS = "nx_psp_local_wireless"
    const val LOCAL_WIRELESS_ON = "on"
    const val LOCAL_WIRELESS_OFF = "off"

    val localWirelessRow = FrontendOption(
        LOCAL_WIRELESS, "Local Wireless", listOf(LOCAL_WIRELESS_ON, LOCAL_WIRELESS_OFF), LOCAL_WIRELESS_ON,
        "A game's ad hoc multiplayer finds other NX Redux\ndevices on the same Wi-Fi: start the game on each\ndevice and use its ad hoc menu.\n" +
            "Nothing is sent until a game starts ad hoc play.\nA save state can't be loaded while a game is linked.\n" +
            "Turn it off on a network you share with people\nyou don't want to play with.",
        brief = "Ad hoc multiplayer with nearby devices",
        visual = false, valueLabels = listOf("On", "Off"), atLaunch = true,
    )

    fun localWireless(values: Map<String, String>): Boolean = values[LOCAL_WIRELESS] != LOCAL_WIRELESS_OFF

    fun appliesTo(tag: String): Boolean = tag.trim().uppercase() == "PSP"

    /** The 3D Resolution rows (mode, Max Scale, Scale), as the DS, 3DS and PS2 ones. */
    fun resolutionRows(displayMax: Int?): List<FrontendOption> = resolutionRows(Res3dConsole.PSP, displayMax)

    /**
     * Max Scale from a game or console config written before the app set PPSSPP's resolution: its Rendering Resolution
     * above 1x ("1920x1088" is 4) becomes the most Auto draws at, so a player who raised it keeps that sharpness and
     * phones that cannot keep up still get less. Null when there is none to carry or the row is already set.
     */
    fun carriedMaxScale(values: Map<String, String>): String? {
        if (values[Res3dConsole.PSP.maxScaleKey] != null) return null
        val width = values[Res3dConsole.PSP.coreKey]?.substringBefore('x')?.toIntOrNull() ?: return null
        val n = width / PspResolution.NATIVE_W
        return n.takeIf { it > 1 }?.coerceAtMost(Res3dConsole.PSP.coreMax)?.toString()
    }
}
