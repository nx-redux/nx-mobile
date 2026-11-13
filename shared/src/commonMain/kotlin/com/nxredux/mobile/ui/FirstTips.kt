package com.nxredux.mobile.ui

/**
 * The first-launch tips, each a dialog shown once a launch until its Don't show again: [MAIN_MENU] on the main menu
 * (MENU and its stand-ins, START, SELECT), [GAME_LIST] the first time a game list or the Games tab shows (Y, X).
 */
enum class FirstTip(val title: String, val text: String) {
    MAIN_MENU(
        "Tips",
        "MENU opens the highlighted item's menu, with more you can do with it.\n\n" +
            "No MENU button? Set one in Settings \u2192 Controls \u2192 System buttons, or press SELECT + START together.\n\n" +
            "START searches your games.\n" +
            "SELECT opens the Game Switcher.",
    ),
    GAME_LIST(
        "Game list tips",
        "Y plays the highlighted game with others (Netplay).\n" +
            "X shows its achievements.",
    ),
}

/** The tip dialog's buttons: closes it for now (it shows again next launch), and never again. */
internal const val FIRST_TIP_CLOSE = "Got it"
internal const val FIRST_TIP_HIDE = "Don't show again"
