package com.nxredux.mobile.shortcuts

import com.nxredux.mobile.options.N3dsOptions
import com.nxredux.mobile.options.NdsOptions
import com.nxredux.mobile.options.WiiOptions

/** An input the frontend already uses; [note] names it for the player ("R2 swaps the DS screens"). */
data class ClaimedInput(val binding: ShortcutBinding, val note: String)

/**
 * The inputs each console's built-in hotkeys claim; they win over user bindings. Restated in key codes from
 * `NdsHotkeys` (R2, L2, SELECT + d-pad), `N3dsHotkeys` (SELECT + Left/Right/L1/R1, L3 swap) and androidApp's `WiiShortcuts`
 * (L3 recentre, R3 Home), which work in NxButton or platform key terms `shared` can't share.
 */
object ConsoleHotkeys {
    private fun single(code: Int, note: String) = ClaimedInput(ShortcutBinding(null, code), note)
    private fun chord(code: Int, note: String) = ClaimedInput(ShortcutBinding(KeyCodes.BUTTON_SELECT, code), note)

    /**
     * Every console: MENU alone opens the in-game menu, SELECT + START is its chord. SELECT and START themselves are never
     * bindable at all ([ShortcutBinding.GAME_KEYS]).
     */
    val always: List<ClaimedInput> = listOf(
        single(KeyCodes.BUTTON_MODE, "MENU opens the menu"),
        chord(KeyCodes.BUTTON_START, "SELECT+START opens the menu"),
    )

    private val nds = listOf(
        single(KeyCodes.BUTTON_R2, "R2 swaps the DS screens"),
        single(KeyCodes.BUTTON_L2, "L2 is the stylus"),
        chord(KeyCodes.DPAD_LEFT, "SELECT+Left cycles the DS layout"),
        chord(KeyCodes.DPAD_RIGHT, "SELECT+Right cycles the DS layout"),
        chord(KeyCodes.DPAD_UP, "SELECT+Up moves the DS inset"),
        chord(KeyCodes.DPAD_DOWN, "SELECT+Down moves the DS inset"),
    )

    private val n3ds = listOf(
        chord(KeyCodes.DPAD_LEFT, "SELECT+Left cycles the 3DS layout"),
        chord(KeyCodes.DPAD_RIGHT, "SELECT+Right cycles the 3DS layout"),
        chord(KeyCodes.BUTTON_L1, "SELECT+L1 toggles the stylus"),
        chord(KeyCodes.BUTTON_R1, "SELECT+R1 swaps the 3DS screens"),
        single(KeyCodes.BUTTON_THUMBL, "L3 swaps screens"),
    )

    private val wii = listOf(
        single(KeyCodes.BUTTON_THUMBL, "L3 recentres motion"),
        single(KeyCodes.BUTTON_THUMBR, "R3 is HOME"),
    )

    fun claimed(tag: String): List<ClaimedInput> = always + when {
        NdsOptions.appliesTo(tag) -> nds
        N3dsOptions.appliesTo(tag) -> n3ds
        WiiOptions.appliesTo(tag) -> wii
        else -> emptyList()
    }
}
