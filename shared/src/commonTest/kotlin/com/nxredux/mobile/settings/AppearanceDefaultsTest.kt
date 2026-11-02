package com.nxredux.mobile.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/** Appearance and Layouts' named defaults: NX Redux's (config.h CFG_DEFAULT_*, menustyle_model.h) where it has the option. */
class AppearanceDefaultsTest {
    @Test fun anUnsetTabTakesItsOwnDefaultToolsGridCollectionsMinimalConsolesCarousel() {
        assertEquals(MenuStyle.CAROUSEL, MenuStyles.styleOf(emptyMap(), MenuCategory.CONSOLES))
        assertEquals(MenuStyle.MINIMAL, MenuStyles.styleOf(emptyMap(), MenuCategory.COLLECTIONS))
        assertEquals(MenuStyle.GRID, MenuStyles.styleOf(emptyMap(), MenuCategory.TOOLS))
        assertEquals(MenuStyle.GRID, MenuStyles.defaultStyleOf(MenuCategory.TOOLS))
    }

    @Test fun gameListsDefaultToCarousel() { assertEquals(MenuStyle.CAROUSEL, MenuStyles.DEFAULT_GAME_LIST_STYLE) }

    @Test fun everyOrientationDefaultsToHorizontal() {
        assertEquals(MenuOrientation.HORIZONTAL, MenuStyles.DEFAULT_ORIENTATION)
        for (c in listOf(MenuCategory.CONSOLES, MenuCategory.COLLECTIONS, MenuCategory.TOOLS))
            assertEquals(MenuOrientation.HORIZONTAL, MenuStyles.orientationOf(emptyMap(), c))
    }

    @Test fun theOtherAppearanceDefaults() {
        assertEquals(VerticalListAlignment.LEFT, VerticalListAlignment.DEFAULT)
        assertEquals(ConsoleArt.CONTROLLER, ConsoleArt.DEFAULT)
        assertEquals(PadTheme.NX_CARBON, PadTheme.DEFAULT)
        assertEquals(AccentPreset.WHITE, AccentPreset.DEFAULT)
    }
}
