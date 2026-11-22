package com.nxredux.mobile.ui.nx

/** What a pane of the two-pane in-game menu shows: a page's rows, the save-slot preview, the game panel, or nothing. */
sealed interface MenuPaneContent {
    data class Page(val page: MenuPage) : MenuPaneContent
    data object Slots : MenuPaneContent
    data object Panel : MenuPaneContent
    data object Empty : MenuPaneContent
}

/** The two panes: [left] (a page), [right], and which one has the focus (the other is dimmed; a [MenuPaneContent.Page] on the right beside a focused left is a preview). */
data class MenuPanes(val left: MenuPage, val right: MenuPaneContent, val focusLeft: Boolean)

/**
 * The panes [state] draws as, or null for a page drawn whole (an Adjust strip, a Message). On the root the root is
 * focused on the left and the right is the game panel ([InGameMenuModel.panelState]), but for Save / Load, which show the
 * slot card, and Options, which previews its page ([MenuState.preview]). Deeper (a column browser, as the
 * RetroAchievements levels) the top page is focused on the right and the page under it dimmed on the left, its
 * highlighted row the one that opened the top. When the menu does not [split] (portrait) only the root and the Options
 * page over it draw as two (in the root's columns), while its words fit beside the root's ([optionsBeside]: else the
 * Options page draws whole, and the root keeps the panel beside it instead of the preview); a deeper page draws whole.
 */
fun menuPanes(state: MenuState, split: Boolean = true, optionsBeside: Boolean = true): MenuPanes? {
    val top = state.page
    if (top.kind is PageKind.Adjust || top.kind == PageKind.Message) return null
    if (state.stack.size == 1) {
        val right = when (top.rows.getOrNull(top.selected)) {
            null -> MenuPaneContent.Empty
            is MenuRow.Slot -> MenuPaneContent.Slots
            // The Options preview only where its words fit beside the root's ([optionsBeside]); else the panel stays.
            else -> state.preview?.takeIf { split || optionsBeside }?.let { MenuPaneContent.Page(it) } ?: MenuPaneContent.Panel
        }
        return MenuPanes(top, right, focusLeft = true)
    }
    val parent = state.stack[state.stack.size - 2]
    if (parent.kind is PageKind.Adjust || parent.kind == PageKind.Message) return null
    if (!split && !(state.stack.size == 2 && top.kind == PageKind.Options && optionsBeside)) return null
    return MenuPanes(parent, MenuPaneContent.Page(top), focusLeft = false)
}

/**
 * Whether the in-game menu draws its tab row (Menu, Achievements, Cheats, Players) across the top, under the header: on
 * the root, and on the Options page drawn beside the dimmed root, so the root's words never move as it opens. Never with
 * the panel off (netplay, [InGameMenuModel.panelBeside]); a deeper page draws without it.
 */
fun menuTabsShown(state: MenuState, panelBeside: Boolean = true): Boolean {
    if (!panelBeside || state.stack.firstOrNull()?.kind != PageKind.Root) return false
    return state.stack.size == 1 || (state.stack.size == 2 && state.page.kind == PageKind.Options)
}
