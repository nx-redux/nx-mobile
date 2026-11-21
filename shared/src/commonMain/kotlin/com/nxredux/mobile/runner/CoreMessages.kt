package com.nxredux.mobile.runner

/**
 * Which of a core's on-screen messages (libretro SET_MESSAGE) the app shows as a toast: fceumm's FDS disk inserts
 * ("Disk 1 of 2 Side B Inserted", after a disk flip or a hand insert), so the player sees which side went in, and
 * Azahar's local wireless news (connected, a player joined or left, a refusal). Every other message stays in the log.
 */
object CoreMessages {
    private const val LOCAL_WIRELESS = "Local wireless: "
    /** A room of its own with nobody in it yet: the game plays as on its own, nothing to tell. */
    private const val ROOM_OPEN = "Local wireless: room open"
    private val FDS_INSERTED = Regex("""^Disk (\d+) of (\d+) Side ([A-Z]) Inserted$""")

    /** The toast for [msg] from the core [coreId], or null: not shown. */
    fun toast(coreId: String, msg: String): String? {
        if (coreId == "azahar") return msg.trim().takeIf { it.startsWith(LOCAL_WIRELESS) && it != ROOM_OPEN }
        if (coreId != "fceumm") return null
        val m = FDS_INSERTED.matchEntire(msg.trim()) ?: return null
        val (disk, of, side) = m.destructured
        return "Disk $disk of $of, side $side"
    }
}
