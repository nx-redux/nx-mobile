package com.nxredux.mobile.netplay

/** The words for where DS and 3DS local wireless stands: beside its in-game row, and the DS toasts. */
object LocalWirelessText {
    /** Beside an On Local Wireless row; [others] below 1 when the link does not say. */
    fun now(inRoom: Boolean, seeking: Boolean, others: Int): String = when {
        inRoom -> if (others < 1) "linked" else "linked with ${devices(others)}"
        seeking -> "looking for others"
        else -> "game's wireless off"
    }

    /** The DS toast when the other devices in the room go from [before] to [now]; a count below 0 is unknown. */
    fun change(before: Int, now: Int): String? = when {
        before < 0 || now < 0 || before == now -> null
        now > before -> "Local wireless: linked with ${devices(now)}"
        now == 0 -> if (before == 1) "Local wireless: the other device left" else "Local wireless: the other devices left"
        else -> "Local wireless: a device left"
    }

    private fun devices(n: Int) = if (n == 1) "1 device" else "$n devices"
}
