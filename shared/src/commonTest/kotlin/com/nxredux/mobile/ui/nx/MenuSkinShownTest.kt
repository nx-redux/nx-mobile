package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MenuSkinShownTest {
    private val bools = listOf(false, true)

    // Any controller connected: never the skin, whatever else holds (portrait or landscape, flat or half-folded, two screens or one).
    @Test fun a_connected_controller_never_shows_the_menu_skin() {
        for (skin in bools) for (pad in bools) for (canTwo in bools) for (tabletop in bools) {
            assertFalse(
                menuSkinShown(skinPicked = skin, showPad = pad, controllerConnected = true, canTwo = canTwo, tabletop = tabletop),
                "skin=$skin pad=$pad canTwo=$canTwo tabletop=$tabletop",
            )
        }
    }

    // No controller: exactly the rule before it (a skin, the pad shown, and flat or a two-screen layout when half-folded).
    @Test fun without_a_controller_the_rule_is_unchanged() {
        for (skin in bools) for (pad in bools) for (canTwo in bools) for (tabletop in bools) {
            assertEquals(
                skin && pad && (canTwo || !tabletop),
                menuSkinShown(skinPicked = skin, showPad = pad, controllerConnected = false, canTwo = canTwo, tabletop = tabletop),
                "skin=$skin pad=$pad canTwo=$canTwo tabletop=$tabletop",
            )
        }
    }

    // The same pad flips with the controller, both ways.
    @Test fun connecting_and_disconnecting_switches_it_both_ways() {
        assertEquals(true, menuSkinShown(skinPicked = true, showPad = true, controllerConnected = false, canTwo = false, tabletop = false))
        assertEquals(false, menuSkinShown(skinPicked = true, showPad = true, controllerConnected = true, canTwo = false, tabletop = false))
        assertEquals(true, menuSkinShown(skinPicked = true, showPad = true, controllerConnected = false, canTwo = false, tabletop = false))
        // the Fold half-open on its two-screen layout too
        assertEquals(true, menuSkinShown(skinPicked = true, showPad = true, controllerConnected = false, canTwo = true, tabletop = true))
        assertEquals(false, menuSkinShown(skinPicked = true, showPad = true, controllerConnected = true, canTwo = true, tabletop = true))
    }
}
