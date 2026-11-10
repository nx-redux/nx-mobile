package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.options.FrontendOptions
import com.nxredux.mobile.options.OptionGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The in-game menu hosted in the dual-screen panel ([InGameMenuModel.setInPanel]): the header and the menu's pages only. */
class InGameMenuInPanelTest {
    private val tag = "GBA"
    private fun model(inPanel: Boolean = true) = InGameMenuModel("Tetris", slotCount = 8, initialSlot = 2).apply {
        setFrontend(FrontendOptions.defaults(tag), listOf("Aspect.png"), emptySet(), tag)
        setInPanel(inPanel)
    }

    private fun InGameMenuModel.openDisplay(): InGameMenuModel {
        select(4); activate(4)
        activate(state.page.rows.indexOfFirst { it.label == "Display" })
        assertEquals(PageKind.Settings(OptionGroup.DISPLAY), state.page.kind)
        return this
    }

    @Test fun in_the_panel_the_menu_has_no_tabs() {
        val m = model()
        m.setCheats(emptyList(), "", "Tetris.gb.cht"); m.cheatsAvailable = true
        assertFalse(m.panelBeside)
        assertFalse(menuTabsShown(m.state, m.panelBeside))
        m.press(NxButton.UP)                                 // the first word: no tab row to go up to
        assertNull(m.state.panelFocus)
        m.press(NxButton.R1)                                 // no tab to step to
        assertEquals(GamePanelTab.SHORTCUTS, m.state.panelTab)
        assertEquals(PageKind.Root, m.state.page.kind)
    }

    @Test fun out_of_the_panel_the_tabs_come_back() {
        val m = model()
        m.setInPanel(false)
        assertTrue(m.panelBeside)
        assertTrue(menuTabsShown(m.state, m.panelBeside))
    }

    @Test fun in_the_panel_b_on_the_root_closes() {
        assertEquals(MenuEffect.Close, model().press(NxButton.B))
    }

    @Test fun in_the_panel_a_visual_row_changes_in_place() {
        val m = model().openDisplay()
        assertTrue((m.state.page.rows[0] as MenuRow.Option).visual)   // Screen Scaling
        assertEquals(MenuEffect.SetFrontend(FrontendOptions.SCALING, "Native"), m.press(NxButton.RIGHT))
        assertEquals(PageKind.Settings(OptionGroup.DISPLAY), m.state.page.kind)   // no live preview strip
        m.setChoice(0, 0)
        assertEquals(PageKind.Settings(OptionGroup.DISPLAY), m.state.page.kind)
    }

    @Test fun moving_into_the_panel_leaves_a_live_preview() {
        val m = model(inPanel = false).openDisplay()
        m.press(NxButton.RIGHT)
        assertTrue(m.state.page.kind is PageKind.Adjust)
        m.setInPanel(true)
        assertEquals(PageKind.Settings(OptionGroup.DISPLAY), m.state.page.kind)
    }

    @Test fun the_picture_shows_undimmed_on_a_visual_row() {
        val m = model().openDisplay()
        assertTrue(livePreviewRow(m.state))                 // Screen Scaling: its change shows on the picture
        val plain = m.state.page.rows.indexOfFirst { it is MenuRow.Option && !it.visual }
        m.select(plain)
        assertFalse(livePreviewRow(m.state))
        m.press(NxButton.B)
        assertFalse(livePreviewRow(m.state))                 // the Options page
    }
}
