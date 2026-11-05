package com.nxredux.mobile.runner

/**
 * Which of a core's on-screen messages (libretro SET_MESSAGE) the app shows as a toast. Only fceumm's FDS disk inserts
 * ("Disk 1 of 2 Side B Inserted", after a disk flip or a hand insert): the player sees which side went in. Every other
 * message stays in the log.
 */
object CoreMessages {
    private val FDS_INSERTED = Regex("""^Disk (\d+) of (\d+) Side ([A-Z]) Inserted$""")

    /** The toast for [msg] from the core [coreId], or null: not shown. */
    fun toast(coreId: String, msg: String): String? {
        if (coreId != "fceumm") return null
        val m = FDS_INSERTED.matchEntire(msg.trim()) ?: return null
        val (disk, of, side) = m.destructured
        return "Disk $disk of $of, side $side"
    }
}
