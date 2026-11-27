package com.nxredux.mobile.runner

import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.PadProfile

/** One Button Mapping row: the job of [job]'s key, named as the console names it. */
data class MapRow(val job: MapKey, val label: String)

/**
 * The rows a console's Button Mapping page lists: each key whose job the console's pad draws, labelled as the pad labels
 * the button that key reaches ([PhysicalKeys.toRunner], then [PadProfile]), then the hotkeys a key reaches although the
 * pad leaves its button out. A key that does nothing on the console has no row.
 */
object ButtonMapRows {
    /** N64 buttons by name, as [N64Input] reads each key. */
    private val N64_NAMES = mapOf(
        NxButton.A to "A", NxButton.B to "B", NxButton.X to "C-Left", NxButton.Y to "C-Down",
        NxButton.L1 to "L", NxButton.R1 to "R", NxButton.L2 to "Z",
    )

    /**
     * The consoles whose pad has stick clicks the core reads: the DualShocks and the Wii U GamePad. Other cores that
     * read the sticks (PSP, N64, Dreamcast, GameCube) have no clicks to read.
     */
    private val STICK_CLICK_TAGS = setOf("PS", "PSX", "PS2", "WIIU")

    /** Jobs a key reaches outside the drawn pad: the DS's and 3DS's hotkeys and the Wii's stick clicks. */
    private fun hotkeys(tag: String): Map<MapKey, String> = when (tag) {
        // NdsHotkeys: L2 and L3 swap the screens, R2 toggles stylus mode, R3 touches at the pen.
        "NDS" -> mapOf(MapKey.L2 to "Swap screens", MapKey.R2 to "Stylus", MapKey.L3 to "Swap screens", MapKey.R3 to "Touch")
        // Azahar's swap-screens key and its touch at the right-stick cursor.
        "3DS" -> mapOf(MapKey.L3 to "Swap screens", MapKey.R3 to "Touch")
        "WII" -> mapOf(MapKey.L3 to "Recenter", MapKey.R3 to "HOME")
        else -> emptyMap()
    }

    fun rowsFor(tag: String, wiiPad: WiiPad? = null, newControls: Boolean = false): List<MapRow> {
        val t = tag.trim().uppercase()
        val profile = PadProfile.forTag(t, n3dsNew = newControls, wii = wiiPad)
        val hot = hotkeys(t)
        val pad = MapKey.entries.filter { it !in hot }.mapNotNull { job ->
            // Every MapKey has an NxButton of the same name; toRunner only swaps among the face keys.
            val button = NxButton.valueOf(MapKey.ofKeyCode(PhysicalKeys.toRunner(t, job.keyCode))!!.name)
            when {
                N64Input.appliesTo(t) -> N64_NAMES[button]
                button == NxButton.L3 || button == NxButton.R3 -> if (t in STICK_CLICK_TAGS) profile.label(button) else null
                profile.isHidden(button) -> null
                else -> profile.label(button)
            }?.let { MapRow(job, it) }
        }
        return pad + hot.map { (job, label) -> MapRow(job, label) }
    }
}
