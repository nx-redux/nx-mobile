package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.shared.res.Res
import com.nxredux.mobile.shared.res.nav_icon_delete
import com.nxredux.mobile.shared.res.nav_icon_filter
import kotlin.test.Test
import kotlin.test.assertEquals

// A hint's touch stand-in: an icon at the title's right, a button on the page, or nothing (tap, long press, back).
class HintTouchTest {
    private val hints = listOf(
        Hint(NxButton.B, "Back"),
        Hint(NxButton.Y, "Remove", HintTouch.Icon(Res.drawable.nav_icon_delete)),
        Hint(NxButton.X, "Cancel", HintTouch.Button(primary = false)),
        Hint(NxButton.Y, "Pause", HintTouch.Button(primary = true)),
        Hint(NxButton.A, "Resume"),
    )

    @Test fun icons_keep_their_art_label_and_button() {
        val pressed = mutableListOf<NxButton>()
        val icons = hints.touchIcons { pressed += it }
        assertEquals(listOf("Remove"), icons.map { it.description })
        icons.single().onClick()
        assertEquals(listOf(NxButton.Y), pressed)
    }

    @Test fun buttons_keep_hint_order_and_primary() {
        assertEquals(
            listOf(FooterAction(NxButton.X, "Cancel", primary = false), FooterAction(NxButton.Y, "Pause", primary = true)),
            hints.touchButtons(),
        )
    }

    @Test fun a_lit_icon_stays_lit() {
        val lit = listOf(Hint(NxButton.Y, "Show all", HintTouch.Icon(Res.drawable.nav_icon_filter, lit = true)))
        assertEquals(listOf(true), lit.touchIcons {}.map { it.lit })
    }

    // Recent and Search are always icons, whatever the hint bar; Search only with games to search.
    @Test fun launcher_icons_are_always_there() {
        assertEquals(listOf("Recent", "Search"), launcherTopActions({}, canSearch = true).map { it.description })
        assertEquals(listOf("Recent"), launcherTopActions({}, canSearch = false).map { it.description })
        assertEquals(listOf("Search"), launcherTopActions({}, canSearch = true, recent = false).map { it.description })
        assertEquals(emptyList(), launcherTopActions(null, canSearch = true))
    }

    // Onboarding and empty pages: every hint but Back becomes a button, A filled (Back is Android's back).
    @Test fun empty_state_buttons_skip_back_and_mark_a_primary() {
        assertEquals(
            listOf(FooterAction(NxButton.A, "Add folder", true), FooterAction(NxButton.B, "Skip", false)),
            emptyStateButtons(listOf(Hint(NxButton.A, "Add folder"), Hint(NxButton.B, "Skip"))),
        )
        assertEquals(listOf(FooterAction(NxButton.A, "Pick folder", true)), emptyStateButtons(listOf(Hint(NxButton.A, "Pick folder"))))
        assertEquals(emptyList(), emptyStateButtons(listOf(Hint(NxButton.B, "Back"))))
    }
}
