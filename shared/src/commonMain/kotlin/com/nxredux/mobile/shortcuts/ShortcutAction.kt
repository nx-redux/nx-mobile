package com.nxredux.mobile.shortcuts

/**
 * What a bound button (or touch button) does in game. [key] is the stable config name; [rewind] actions vanish where a
 * core can't rewind, [mic] ones where it has no microphone, [saveState] ones where it has no save states; [touch] false:
 * a controller's only, never a touch button (Save & Quit: a touch would quit unasked; Toggle mic: the pad's own mic
 * button covers touch).
 */
enum class ShortcutAction(
    val label: String, val key: String, val rewind: Boolean, val mic: Boolean = false, val touch: Boolean = true, val saveState: Boolean = false,
) {
    TOGGLE_FF("Toggle FF", "toggle_ff", false),
    HOLD_FF("Hold FF", "hold_ff", false),
    TOGGLE_REWIND("Toggle Rewind", "toggle_rewind", true),
    HOLD_REWIND("Hold Rewind", "hold_rewind", true),
    RESET("Reset Game", "reset", false),
    /** Save state / Load state: the save-state slot last used (as the in-game menu's), on the press. */
    SAVE_STATE("Save State", "save_state", false, saveState = true),
    LOAD_STATE("Load State", "load_state", false, saveState = true),
    /** The in-game menu's Save & Quit; unbound unless the player binds it (a MENU combo, say: SELECT is never one). */
    SAVE_AND_QUIT("Save & Quit", "save_and_quit", false, touch = false),
    TOGGLE_MIC("Toggle mic", "toggle_mic", rewind = false, mic = true, touch = false);

    /** Whether a core with these abilities offers this action at all. */
    fun offered(canRewind: Boolean, canMic: Boolean, canSaveStates: Boolean): Boolean =
        (canRewind || !rewind) && (canMic || !mic) && (canSaveStates || !saveState)

    /** The shown name: without Save on quit (or save states), Save & Quit only quits, so it says so. */
    fun label(saveOnQuit: Boolean): String = if (this == SAVE_AND_QUIT && !saveOnQuit) "Quit" else label

    companion object {
        fun fromKey(key: String): ShortcutAction? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The on-screen touch buttons a console or game can give an action: all four on a pad skin's strip under its screen,
 * and in the screen's bottom-right corner where no pad is drawn ([com.nxredux.mobile.ui.nx.cornerShortcuts]).
 */
enum class TouchSlot(val key: String) {
    ONE("touch_1"),
    TWO("touch_2"),
    THREE("touch_3"),
    FOUR("touch_4"),
    ;

    /** Its row's name. */
    val label: String get() = "Touch button ${ordinal + 1}"
}
