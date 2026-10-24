package com.nxredux.mobile.ui.nx

import androidx.compose.ui.unit.DpSize
import com.nxredux.mobile.ui.settingsSplit

/**
 * Whether the in-game menu draws as two panes in a [window] of that size: the Settings and
 * RetroAchievements rule ([settingsSplit]: at least 600 dp wide and 1.3:1) on the window, as Settings decides, not on
 * the menu's own area (above the pad band in portrait). The unfolded Fold and a phone in landscape split; portrait,
 * the Fold's included, keeps one page at a time.
 */
fun inGameMenuSplit(window: DpSize): Boolean = settingsSplit(window)

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
 * page over it draw as two (in the root's columns); a deeper page draws whole.
 */
fun menuPanes(state: MenuState, split: Boolean = true): MenuPanes? {
    val top = state.page
    if (top.kind is PageKind.Adjust || top.kind == PageKind.Message) return null
    if (state.stack.size == 1) {
        val right = when (top.rows.getOrNull(top.selected)) {
            null -> MenuPaneContent.Empty
            is MenuRow.Slot -> MenuPaneContent.Slots
            else -> state.preview?.let { MenuPaneContent.Page(it) } ?: MenuPaneContent.Panel
        }
        return MenuPanes(top, right, focusLeft = true)
    }
    val parent = state.stack[state.stack.size - 2]
    if (parent.kind is PageKind.Adjust || parent.kind == PageKind.Message) return null
    if (!split && !(state.stack.size == 2 && top.kind == PageKind.Options)) return null
    return MenuPanes(parent, MenuPaneContent.Page(top), focusLeft = false)
}
