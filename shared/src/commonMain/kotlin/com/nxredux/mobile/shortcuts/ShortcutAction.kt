package com.nxredux.mobile.shortcuts

/** What a bound button (or touch button) does in game. [key] is the stable config name; [rewind] actions vanish where a core can't rewind. */
enum class ShortcutAction(val label: String, val key: String, val rewind: Boolean) {
    TOGGLE_FF("Toggle FF", "toggle_ff", false),
    HOLD_FF("Hold FF", "hold_ff", false),
    TOGGLE_REWIND("Toggle Rewind", "toggle_rewind", true),
    HOLD_REWIND("Hold Rewind", "hold_rewind", true),
    RESET("Reset Game", "reset", false);

    companion object {
        fun fromKey(key: String): ShortcutAction? = entries.firstOrNull { it.key == key }
    }
}

/** The two on-screen touch buttons a console or game can give an action. */
enum class TouchSlot(val key: String) {
    ONE("touch_1"),
    TWO("touch_2"),
}
