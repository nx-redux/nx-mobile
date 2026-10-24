package com.nxredux.mobile.shortcuts

/** How a press-to-bind ended: a [Bound] binding, one [Refused] because a built-in hotkey owns it ([reason]), or [Cancelled]. */
sealed interface CaptureResult {
    data class Bound(val binding: ShortcutBinding) : CaptureResult
    data class Refused(val binding: ShortcutBinding, val reason: String) : CaptureResult
    data object Cancelled : CaptureResult
}

/**
 * Press to bind, on raw key codes (extra paddles' BUTTON_1..16 too, which no NxButton names), shared by Controls ->
 * Shortcuts and the in-game Shortcuts page. A modifier ([ShortcutBinding.MODIFIERS]: MENU or SELECT) held and another
 * key pressed is that combo; any other key pressed alone is that key; a modifier pressed and released alone binds
 * nothing (L23: MENU alone opens the in-game menu) and keeps waiting. B or Back alone cancels, so neither binds as a
 * single button (MENU+B still does). A result in [reserved] ([ConsoleHotkeys.always]: SELECT+START) or MENU+SELECT
 * (L23) is refused.
 *
 * Every key that went down since [start] is swallowed until its release, even after the capture ended ([takes]): MENU's
 * release after MENU+R1, or B's after a cancel, must not reach the menu as a press of its own.
 */
class ShortcutCapture(private val reserved: List<ClaimedInput> = ConsoleHotkeys.always) {
    /** Waiting for a press. */
    var active: Boolean = false
        private set
    private var modifier: Int? = null
    private val held = HashSet<Int>()

    fun start() { active = true; modifier = null }

    fun cancel() { active = false; modifier = null }

    /**
     * Whether [onKey] should see this press ([down]) or release of [keyCode]: a press while waiting, a release of a key
     * that went down since [start]; never a system key. A key held from before [start] keeps its release (a held
     * direction's repeat must hear it stop).
     */
    fun takes(keyCode: Int, down: Boolean): Boolean = keyCode !in SYSTEM && (keyCode in held || (down && active))

    /** A key went down or up; the result when that press ended the capture, else null (still waiting, or a swallowed key). */
    fun onKey(keyCode: Int, down: Boolean): CaptureResult? {
        if (!down) {
            held.remove(keyCode)
            if (keyCode == modifier) modifier = null
            return null
        }
        held += keyCode
        if (!active) return null
        val mod = modifier
        return when {
            mod == null && keyCode in ShortcutBinding.MODIFIERS -> { modifier = keyCode; null }
            mod != null -> end(ShortcutBinding(mod, keyCode))
            keyCode == KeyCodes.BUTTON_B || keyCode == KeyCodes.BACK -> { cancel(); CaptureResult.Cancelled }
            else -> end(ShortcutBinding(null, keyCode))
        }
    }

    private fun end(b: ShortcutBinding): CaptureResult {
        cancel()
        if (b.modifier != null && b.keyCode in ShortcutBinding.MODIFIERS) return CaptureResult.Refused(b, "MENU+SELECT is reserved")
        return reserved.firstOrNull { it.binding == b }?.let { CaptureResult.Refused(b, it.note) } ?: CaptureResult.Bound(b)
    }

    private companion object {
        /** HOME, VOLUME_UP, VOLUME_DOWN, POWER, VOLUME_MUTE: the system's, never a binding. */
        val SYSTEM = setOf(3, 24, 25, 26, 164)
    }
}
