package com.nxredux.mobile.runner

import com.nxredux.mobile.shortcuts.ConsoleHotkeys
import com.nxredux.mobile.shortcuts.KeyLabels
import com.nxredux.mobile.shortcuts.ShortcutBinding
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.PadProfile

/**
 * One Button Mapping row: the job of [job]'s key, named as the console names it. [locked]: a key the console keeps
 * ([ButtonMap.locked]), shown with what it does but not rebound.
 */
data class MapRow(val job: MapKey, val label: String, val locked: Boolean = false)

/** A built-in combo the Button Mapping page lists, not rebound: [label] what it does, [binding] its keys. */
data class MapCombo(val label: String, val binding: ShortcutBinding) {
    /** Its keys as pressed under [map]: a pad key in it is the physical key doing that key's job, as the hotkeys read it. */
    fun keys(map: ButtonMap): String {
        val key = MapKey.ofKeyCode(binding.keyCode)?.let { map.keyFor(it).label } ?: KeyLabels.label(ShortcutBinding(null, binding.keyCode))
        val modifier = binding.modifier ?: return key
        return "${KeyLabels.label(ShortcutBinding(null, modifier))} + $key"
    }
}

/**
 * The rows a console's Button Mapping page lists: each key whose job the console's pad draws, labelled as the pad labels
 * the button that key reaches ([PhysicalKeys.toRunner], then [PadProfile]), then the hotkeys a key reaches although the
 * pad leaves its button out. A key that does nothing on the console has no row. Hotkeys and analog triggers are
 * [MapRow.locked]; the built-in combos are listed after the rows ([combos]).
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
    fun hotkeys(tag: String): Map<MapKey, String> = when (tag.trim().uppercase()) {
        // NdsHotkeys: L2 and L3 swap the screens, R2 toggles stylus mode, R3 touches at the pen.
        "NDS" -> mapOf(MapKey.L2 to "Swap screens", MapKey.R2 to "Stylus", MapKey.L3 to "Swap screens 2", MapKey.R3 to "Touch")
        // Azahar's swap-screens key and its touch at the right-stick cursor.
        "3DS" -> mapOf(MapKey.L3 to "Swap screens", MapKey.R3 to "Touch")
        "WII" -> mapOf(MapKey.L3 to "Recenter", MapKey.R3 to "HOME")
        else -> emptyMap()
    }

    fun rowsFor(tag: String, wiiPad: WiiPad? = null, newControls: Boolean = false): List<MapRow> {
        val t = tag.trim().uppercase()
        val profile = PadProfile.forTag(t, n3dsNew = newControls, wii = wiiPad)
        val hot = hotkeys(t)
        val locked = ButtonMap.locked(t)
        val pad = MapKey.entries.filter { it !in hot }.mapNotNull { job ->
            // Every MapKey has an NxButton of the same name; toRunner only swaps among the face keys.
            val button = NxButton.valueOf(MapKey.ofKeyCode(PhysicalKeys.toRunner(t, job.keyCode))!!.name)
            when {
                N64Input.appliesTo(t) -> N64_NAMES[button]
                button == NxButton.L3 || button == NxButton.R3 -> if (t in STICK_CLICK_TAGS) profile.label(button) else null
                profile.isHidden(button) -> null
                else -> profile.label(button)
            }?.let { MapRow(job, it, locked = job in locked) }
        }
        return pad + hot.map { (job, label) -> MapRow(job, label, locked = true) }
    }

    /** [tag]'s built-in SELECT chords ([ConsoleHotkeys.claimed]), in its order: shown on the page, never rebound. */
    fun combos(tag: String): List<MapCombo> =
        ConsoleHotkeys.claimed(tag.trim().uppercase()).filter { it.binding.modifier != null }.map { MapCombo(it.action, it.binding) }
}
