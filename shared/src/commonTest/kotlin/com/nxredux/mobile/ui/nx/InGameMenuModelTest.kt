package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.achievements.RaAchievement
import com.nxredux.mobile.achievements.RaAchievementState
import com.nxredux.mobile.achievements.RaSortOrder
import com.nxredux.mobile.cheats.CheatEntry
import com.nxredux.mobile.options.ConfigSource
import com.nxredux.mobile.options.BottomScreenOptions
import com.nxredux.mobile.options.ConsoleSettings
import com.nxredux.mobile.options.CoreOption
import com.nxredux.mobile.options.FrontendOptions
import com.nxredux.mobile.options.PadOptions
import com.nxredux.mobile.options.ShaderOptions
import com.nxredux.mobile.options.SpeedOptions
import com.nxredux.mobile.options.NdsOptions
import com.nxredux.mobile.shortcuts.ConsoleHotkeys
import com.nxredux.mobile.shortcuts.KeyCodes
import com.nxredux.mobile.shortcuts.ShortcutAction
import com.nxredux.mobile.shortcuts.ShortcutBinding
import com.nxredux.mobile.shortcuts.ShortcutOverrides
import com.nxredux.mobile.shortcuts.ShortcutSet
import com.nxredux.mobile.shortcuts.TouchSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InGameMenuModelTest {
    /**
     * A console whose Console Settings page is empty: every tag has the shared Controller Layout and speed rows, so they
     * are locked here (a locked row is hidden, and a page with none left is not offered).
     */
    private val plainTag = "GBA"   // a console with the Controller Layout row only (N64 has Graphics API too)
    private val plainLocked = setOf(PadOptions.LAYOUT) + SpeedOptions.table.map { it.key }

    private fun plain(m: InGameMenuModel) = m.apply { setFrontend(FrontendOptions.defaults(plainTag), emptyList(), plainLocked, plainTag) }

    private fun model() = plain(InGameMenuModel("Tetris", slotCount = 8, initialSlot = 2))

    private val palette = CoreOption("pal", "a", label = "Palette", info = "Colors.", category = "video",
        categoryLabel = "Video", categoryInfo = "Display settings.", values = listOf("a", "b"), valueLabels = listOf("A", "B"))
    private val speed = CoreOption("spd", "1", label = "Speed", values = listOf("1", "2"), valueLabels = listOf("1x", "2x"))
    private val hidden = CoreOption("res", "x", label = "Resolution (restart)", values = listOf("x", "y"))

    private fun InGameMenuModel.openCoreOptions(): InGameMenuModel {
        press(NxButton.DOWN); press(NxButton.DOWN); press(NxButton.DOWN)   // Options
        press(NxButton.A)                                                  // -> Options page
        press(NxButton.DOWN); press(NxButton.DOWN); press(NxButton.DOWN)   // Core Options
        assertEquals(MenuEffect.OpenCoreOptions, press(NxButton.A))        // Core Options (host loads)
        showCoreOptions(listOf(palette, speed, hidden), locked = emptySet())
        return this
    }

    @Test fun root_page_lists_the_menu_and_starts_at_the_given_slot() {
        val m = model()
        assertEquals("Tetris", m.state.page.title)
        assertEquals(listOf("Continue", "Save", "Load", "Options", "Quit"), m.state.page.rows.map { it.label })
        assertEquals(2, m.state.slotIndex)
    }

    @Test fun up_down_wrap_and_slot_rows_change_the_slot() {
        val m = model()
        m.press(NxButton.UP)
        assertEquals(4, m.state.page.selected)
        m.press(NxButton.DOWN); m.press(NxButton.DOWN)       // Save
        m.press(NxButton.RIGHT)
        assertEquals(3, m.state.slotIndex)
        assertEquals(MenuEffect.SaveSlot(4), m.press(NxButton.A))
        m.press(NxButton.DOWN)                               // Load
        m.press(NxButton.LEFT); m.press(NxButton.LEFT); m.press(NxButton.LEFT); m.press(NxButton.LEFT)
        assertEquals(7, m.state.slotIndex)                   // wrapped
        assertEquals(MenuEffect.LoadSlot(8), m.press(NxButton.A))
    }

    @Test fun continue_b_and_menu_close_and_a_on_quit_saves_and_quits() {
        val m = model()
        assertEquals(MenuEffect.Close, m.press(NxButton.A))
        assertEquals(MenuEffect.Close, m.press(NxButton.B))
        assertEquals(MenuEffect.Close, m.press(NxButton.MENU))
        m.press(NxButton.UP)
        assertEquals(MenuEffect.Quit, m.press(NxButton.A))
    }

    @Test fun x_on_quit_quits_without_a_resume_point() {
        val m = model()
        assertNull(m.press(NxButton.X))   // Continue: X does nothing
        m.press(NxButton.UP)
        assertEquals(MenuEffect.QuitWithoutResume, m.press(NxButton.X))
    }

    @Test fun quit_hints_name_both_quits() {
        val m = model()
        m.press(NxButton.UP)
        assertEquals(
            listOf(Hint(NxButton.A, "Save & Quit"), Hint(NxButton.X, "Quit"), Hint(NxButton.B, "Resume")),
            hintsFor(m.state.page, m.state.page.rows[m.state.page.selected]),
        )
    }

    @Test fun a_tap_on_quit_asks_how_to_quit() {
        val m = model()
        m.select(4)
        assertNull(m.activate(4))
        assertEquals(PageKind.QuitGame, m.state.page.kind)
        assertEquals("Quit Game", m.state.page.title)
        assertEquals(listOf("Save & Quit", "Quit"), m.state.page.rows.map { it.label })
        assertEquals(0, m.state.page.selected)
        assertEquals(MenuEffect.Quit, m.activate(0))
    }

    @Test fun quit_on_the_quit_page_quits_without_a_resume_point() {
        val m = model()
        m.activate(4)
        assertEquals(MenuEffect.QuitWithoutResume, m.activate(1))
        m.press(NxButton.UP)                     // the pad works there too: back on Save & Quit
        assertEquals(MenuEffect.Quit, m.press(NxButton.A))
    }

    @Test fun the_quit_page_draws_as_a_dialog_over_the_root() {
        val m = model()
        assertNull(menuDialog(m.state).dialog)
        assertEquals(m.state, menuDialog(m.state).under)
        m.select(4)
        m.activate(4)
        val d = menuDialog(m.state)
        assertEquals(PageKind.QuitGame, d.dialog?.kind)
        assertTrue(d.dialog!!.footer.isNotBlank())   // the dialog's line of text
        assertEquals(PageKind.Root, d.under.page.kind)
        assertEquals(4, d.under.page.selected)        // Quit stays highlighted under it
        m.press(NxButton.DOWN)
        assertEquals(1, menuDialog(m.state).dialog?.selected)
    }

    @Test fun b_on_the_quit_page_goes_back_to_quit() {
        val m = model()
        m.activate(4)
        assertNull(m.press(NxButton.B))
        assertEquals(PageKind.Root, m.state.page.kind)
        assertEquals("Quit", m.state.page.rows[m.state.page.selected].label)
    }

    @Test fun save_and_load_take_two_taps_and_quit_one() {
        val m = model()
        val twoTaps = m.state.page.rows.map { inGameRowTakesTwoTaps(it) }
        assertEquals(listOf(false, true, true, false, false), twoTaps)   // Continue, Save, Load, Options, Quit (its dialog asks)
        assertFalse(inGameRowTakesTwoTaps(MenuRow.Option("Disc", InGameMenuModel.DISC_KEY, "1/2")))
        assertFalse(inGameRowTakesTwoTaps(MenuRow.Action("Leave", MenuAction.LEAVE_NETPLAY)))
        assertFalse(inGameRowTakesTwoTaps(MenuRow.Action("Save & Quit", MenuAction.SAVE_AND_QUIT)))
        assertFalse(inGameRowTakesTwoTaps(null))
    }

    @Test fun a_swipe_on_the_slot_card_steps_one_slot() {
        assertEquals(0, slotSwipeStep(-39f, 40f))   // too short
        assertEquals(0, slotSwipeStep(39f, 40f))
        assertEquals(1, slotSwipeStep(-40f, 40f))   // finger left: the next slot
        assertEquals(-1, slotSwipeStep(40f, 40f))   // finger right: the previous slot
        assertEquals(1, slotSwipeStep(-400f, 40f))  // a long swipe is still one slot
    }

    @Test fun options_page_shows_core_options_save_changes_and_status() {
        val m = model()
        m.select(3)
        assertNull(m.activate(3))
        assertEquals(PageKind.Options, m.state.page.kind)
        assertEquals(listOf("Shortcuts", "Frontend", "Shaders", "Core Options", "Save Changes"), m.state.page.rows.map { it.label })
        assertEquals("Using defaults.", m.state.page.footer)
        m.setStatus(ConfigSource.GAME)
        assertEquals("Using game config.", m.state.page.footer)
        assertNull(m.press(NxButton.B))
        assertEquals(PageKind.Root, m.state.page.kind)
    }

    @Test fun core_options_lists_categories_then_live_uncategorized_options() {
        val m = model().openCoreOptions()
        val page = m.state.page
        assertEquals(PageKind.CoreOptions, page.kind)
        assertEquals("Core Options", page.title)
        assertEquals(
            listOf(
                MenuRow.Submenu("Video", PageKind.Category("video"), detail = "Display settings."),   // the core's text: info icon only
                MenuRow.Option("Speed", "spd", "1x", "", choices = listOf("1x", "2x"), index = 0),
            ),
            page.rows,
        )
    }

    @Test fun category_page_and_live_change_emits_set_option() {
        val m = model().openCoreOptions()
        assertNull(m.press(NxButton.A))                      // open Video
        assertEquals(PageKind.Category("video"), m.state.page.kind)
        assertEquals("Video", m.state.page.title)
        assertEquals(MenuEffect.SetOption("pal", "b"), m.press(NxButton.RIGHT))
        assertEquals("B", (m.state.page.rows.single() as MenuRow.Option).valueLabel)
        assertEquals(MenuEffect.SetOption("pal", "a"), m.press(NxButton.RIGHT))   // wraps
    }

    @Test fun a_on_a_segmented_option_steps_it_and_shows_no_page() {
        val m = model().openCoreOptions()
        m.press(NxButton.A)                                  // Video
        assertEquals(NxSettingControl.SEGMENTED, m.optionRow(0).control)   // A / B
        assertEquals(MenuEffect.SetOption("pal", "b"), m.press(NxButton.A))
        assertEquals(PageKind.Category("video"), m.state.page.kind)
        assertNull(m.state.popup)
        assertEquals(1, m.optionRow(0).index)
    }

    @Test fun a_on_option_without_choices_does_nothing() {
        val m = model()
        m.select(3); m.activate(3); m.activate(3)
        m.showCoreOptions(listOf(CoreOption("k", "v", label = "Raw", info = "Raw value.")), emptySet())
        assertEquals(NxSettingControl.VALUE, m.optionRow(0).control)
        assertNull(m.press(NxButton.A))
        assertEquals(PageKind.CoreOptions, m.state.page.kind)
        assertNull(m.state.popup)                            // no details page either: X shows a cut-off text
    }

    @Test fun left_right_on_option_without_choices_does_nothing() {
        val m = model()
        m.select(3); m.activate(3); m.activate(3)
        m.showCoreOptions(listOf(CoreOption("k", "v", label = "Raw")), emptySet())
        assertNull(m.press(NxButton.RIGHT))
        assertEquals("v", (m.state.page.rows.single() as MenuRow.Option).valueLabel)
    }

    @Test fun no_live_options_shows_the_empty_message() {
        val m = model()
        m.select(3); m.activate(3); m.activate(3)
        m.showCoreOptions(listOf(hidden), emptySet())
        assertEquals(PageKind.Message, m.state.page.kind)
        assertEquals("This core has no options that\ncan be changed while running.", m.state.page.footer)
    }

    @Test fun update_options_clamps_selection() {
        val m = model().openCoreOptions()
        m.press(NxButton.DOWN)                               // Speed, index 1
        m.updateOptions(listOf(speed))                       // Video category disappears
        assertEquals(0, m.state.page.selected)
        assertEquals(listOf("Speed"), m.state.page.rows.map { it.label })
    }

    @Test fun save_changes_actions_emit_effects_and_return_to_options() {
        val m = model()
        m.select(3); m.activate(3)
        m.press(NxButton.UP)                                 // Save Changes (last row)
        assertNull(m.press(NxButton.A))
        assertEquals(PageKind.SaveChanges, m.state.page.kind)
        assertEquals(listOf("Save for console", "Save for game", "Restore defaults"), m.state.page.rows.map { it.label })
        assertEquals(MenuEffect.SaveForConsole, m.press(NxButton.A))
        assertEquals(PageKind.Options, m.state.page.kind)
        m.activate(4)                                        // Save Changes (index 4, the last)
        m.select(1); assertEquals(MenuEffect.SaveForGame, m.activate(1))
        m.activate(4); m.select(2)
        assertIs<MenuEffect.RestoreDefaults>(m.activate(2))
        m.showMessage("Save Changes", "Restored defaults.")
        assertEquals(PageKind.Message, m.state.page.kind)
        m.press(NxButton.B)
        assertEquals(PageKind.Options, m.state.page.kind)
    }

    @Test fun options_has_no_cheats_row_the_panel_has_them() {
        val m = model().also { it.cheatsAvailable = true }
        m.select(3)
        assertNull(m.activate(3))                            // -> Options page
        assertEquals(PageKind.Options, m.state.page.kind)
        assertEquals(listOf("Shortcuts", "Frontend", "Shaders", "Core Options", "Save Changes"), m.state.page.rows.map { it.label })
        assertTrue(GamePanelTab.CHEATS in m.panelState().tabs)
    }

    private fun InGameMenuModel.toCoreOptionsRow(): InGameMenuModel {
        select(3); activate(3); select(3)                    // Options page, Core Options selected
        return this
    }

    @Test fun open_core_options_twice_pushes_one_page() {
        val m = model().toCoreOptionsRow()
        assertEquals(MenuEffect.OpenCoreOptions, m.press(NxButton.A))
        assertNull(m.press(NxButton.A))
        m.showCoreOptions(listOf(palette, speed), emptySet())
        assertEquals(1, m.state.stack.count { it.kind == PageKind.CoreOptions })
        val before = m.state
        m.showCoreOptions(listOf(palette, speed), emptySet())
        assertEquals(before, m.state)
    }

    @Test fun late_core_options_after_back_is_ignored() {
        val m = model().toCoreOptionsRow()
        assertEquals(MenuEffect.OpenCoreOptions, m.press(NxButton.A))
        m.press(NxButton.B)
        m.showCoreOptions(listOf(palette, speed), emptySet())
        assertEquals(PageKind.Root, m.state.page.kind)
        assertEquals(1, m.state.stack.size)
    }

    @Test fun update_options_keeps_selection_by_key() {
        val a = CoreOption("a", "1", label = "A", values = listOf("1", "2"))
        val b = CoreOption("b", "1", label = "B", values = listOf("1", "2"))
        val c = CoreOption("c", "1", label = "C", values = listOf("1", "2"))
        val m = model().toCoreOptionsRow()
        m.press(NxButton.A)
        m.showCoreOptions(listOf(a, b, c), emptySet())
        m.press(NxButton.DOWN); m.press(NxButton.DOWN)
        m.updateOptions(listOf(a.copy(visible = false), b, c))
        assertEquals(1, m.state.page.selected)
        assertEquals("c", (m.state.page.rows[m.state.page.selected] as MenuRow.Option).key)
        assertEquals(MenuEffect.SetOption("c", "2"), m.press(NxButton.RIGHT))
        m.updateOptions(listOf(a, b, c.copy(value = "2")))  // a revealed above the selection
        assertEquals(2, m.state.page.selected)
        assertEquals("c", (m.state.page.rows[m.state.page.selected] as MenuRow.Option).key)
    }

    @Test fun emptied_category_keeps_title_and_shows_message() {
        val m = model().openCoreOptions()
        m.press(NxButton.A)                                  // Video
        m.updateOptions(listOf(palette.copy(visible = false), speed, hidden))
        assertEquals(PageKind.Category("video"), m.state.page.kind)
        assertEquals("Video", m.state.page.title)
        assertEquals(emptyList(), m.state.page.rows)
        assertEquals("This category has no options.", m.state.page.footer)
    }

    @Test fun menu_closes_from_a_deep_page() {
        val m = model().openCoreOptions()
        m.press(NxButton.A)                                  // Video
        assertEquals(MenuEffect.Close, m.press(NxButton.MENU))
    }

    @Test fun cycling_from_a_raw_value_goes_to_first_choice() {
        val m = model().toCoreOptionsRow()
        m.press(NxButton.A)
        m.showCoreOptions(listOf(CoreOption("k", "z", label = "K", values = listOf("x", "y"))), emptySet())
        assertEquals(MenuEffect.SetOption("k", "x"), m.press(NxButton.RIGHT))
    }

    private val overlayNames = listOf("Aspect.png", "Native.png")

    private fun InGameMenuModel.openFrontend(
        values: Map<String, String> = FrontendOptions.defaults(),
        overlays: List<String> = overlayNames,
        locked: Set<String> = emptySet(),
        tag: String = plainTag,
        overlayAvailable: Boolean = true,
    ): InGameMenuModel {
        setFrontend(values, overlays, locked, tag, overlayAvailable)
        select(3); activate(3)                               // Options page
        assertNull(activate(state.page.rows.indexOfFirst { it.label == "Frontend" }))   // NDS has Console Settings first
        assertEquals(PageKind.Frontend, state.page.kind)
        return this
    }

    private fun InGameMenuModel.optionRow(i: Int) = state.page.rows[i] as MenuRow.Option

    @Test fun frontend_page_lists_the_settings_with_values_and_visual_flags() {
        val m = model().openFrontend()
        assertEquals("Frontend", m.state.page.title)
        assertEquals(
            listOf("Screen Scaling", "Screen Sharpness", "Overlay", "Offset screen X", "Offset screen Y", "Pad Opacity (landscape)", "Debug HUD"),
            m.state.page.rows.map { it.label },
        )
        assertEquals(listOf("Aspect", "NEAREST", "None", "0", "0", "40%", "Off"), m.state.page.rows.map { (it as MenuRow.Option).valueLabel })
        assertEquals(listOf(true, true, true, true, true, false, false), m.state.page.rows.map { (it as MenuRow.Option).visual })
    }

    @Test fun left_right_on_the_list_emit_set_frontend() {
        val m = model().openFrontend()
        assertEquals(MenuEffect.SetFrontend(FrontendOptions.SCALING, "Fullscreen"), m.press(NxButton.RIGHT))
        assertEquals("Fullscreen", m.optionRow(0).valueLabel)
        assertEquals(MenuEffect.SetFrontend(FrontendOptions.SCALING, "Aspect"), m.press(NxButton.LEFT))
        m.select(5)
        assertEquals(MenuEffect.SetFrontend(FrontendOptions.PAD_LANDSCAPE_OPACITY, "25"), m.press(NxButton.RIGHT))
        assertEquals("25%", m.optionRow(5).valueLabel)
        assertEquals(MenuEffect.SetFrontend(FrontendOptions.PAD_LANDSCAPE_OPACITY, "40"), m.press(NxButton.LEFT))
        m.select(6)
        assertEquals(MenuEffect.SetFrontend(FrontendOptions.DEBUG_HUD, "On"), m.press(NxButton.RIGHT))
    }

    @Test fun frontend_page_shows_none_and_aspect_by_default_whatever_the_files() {
        val m = model().openFrontend(values = emptyMap(), overlays = listOf("Aspect - LCD Grid.png", "Aspect.png"))
        assertEquals("None", m.optionRow(2).valueLabel)
        assertEquals("NEAREST", m.optionRow(1).valueLabel)
        assertEquals("Aspect", m.optionRow(0).valueLabel)
        m.setFrontend(mapOf(FrontendOptions.OVERLAY to "Aspect - LCD Grid.png"), listOf("Aspect - LCD Grid.png"), emptySet())
        assertEquals("Aspect - LCD Grid.png", m.optionRow(2).valueLabel)   // a stored pick still shows
    }

    @Test fun overlay_cycles_through_none_and_the_given_files() {
        val m = model().openFrontend()
        m.select(2)
        assertEquals(MenuEffect.SetFrontend(FrontendOptions.OVERLAY, "Aspect.png"), m.press(NxButton.RIGHT))
        assertEquals(MenuEffect.SetFrontend(FrontendOptions.OVERLAY, "Native.png"), m.press(NxButton.RIGHT))
        assertEquals(MenuEffect.SetFrontend(FrontendOptions.OVERLAY, "None"), m.press(NxButton.RIGHT))
    }

    @Test fun overlay_without_files_does_nothing() {
        val m = model().openFrontend(overlays = emptyList())
        m.select(2)
        assertNull(m.press(NxButton.RIGHT))
        assertEquals("None", m.optionRow(2).valueLabel)
    }

    @Test fun a_on_a_visual_row_enters_adjust_mode() {
        val m = model().openFrontend()
        m.select(1)                                          // Screen Sharpness
        assertNull(m.press(NxButton.A))
        assertEquals(PageKind.Adjust(FrontendOptions.SHARPNESS), m.state.page.kind)
        assertEquals("Screen Sharpness", m.state.page.title)
        val o = FrontendOptions.option(FrontendOptions.SHARPNESS)!!
        assertEquals(
            MenuRow.Option(
                "Screen Sharpness", FrontendOptions.SHARPNESS, "NEAREST", o.brief, visual = true,
                choices = listOf("NEAREST", "LINEAR"), index = 0, detail = o.info,
            ),
            m.state.page.rows.single(),
        )
        assertEquals(MenuEffect.SetFrontend(FrontendOptions.SHARPNESS, "LINEAR"), m.press(NxButton.RIGHT))
        assertEquals("LINEAR", m.optionRow(0).valueLabel)
        assertEquals(MenuEffect.SetFrontend(FrontendOptions.SHARPNESS, "NEAREST"), m.press(NxButton.LEFT))
        assertNull(m.press(NxButton.A))                      // A does nothing in adjust mode
        assertEquals(PageKind.Adjust(FrontendOptions.SHARPNESS), m.state.page.kind)
    }

    @Test fun a_on_debug_hud_flips_its_switch() {
        val m = model().openFrontend()
        m.select(6)
        assertEquals(NxSettingControl.SWITCH, m.optionRow(6).control)
        assertEquals(MenuEffect.SetFrontend(FrontendOptions.DEBUG_HUD, "On"), m.press(NxButton.A))
        assertEquals(PageKind.Frontend, m.state.page.kind)
        assertEquals(MenuEffect.SetFrontend(FrontendOptions.DEBUG_HUD, "Off"), m.press(NxButton.A))
    }

    @Test fun up_down_in_adjust_step_visual_rows_skipping_debug_hud_and_wrapping() {
        val m = model().openFrontend()
        m.select(4); m.press(NxButton.A)                     // adjust Offset screen Y
        m.press(NxButton.DOWN)
        assertEquals(PageKind.Adjust(FrontendOptions.SCALING), m.state.page.kind)   // Pad Opacity and Debug HUD skipped, wrapped
        m.press(NxButton.UP)
        assertEquals(PageKind.Adjust(FrontendOptions.OFFSET_Y), m.state.page.kind)
        m.press(NxButton.UP)
        assertEquals(PageKind.Adjust(FrontendOptions.OFFSET_X), m.state.page.kind)
        assertEquals(2, m.state.stack.count { it.kind is PageKind.Adjust || it.kind == PageKind.Frontend })
    }

    @Test fun adjust_hints_map_change_to_right_next_to_down_and_done_to_b() {
        assertEquals(NxButton.RIGHT, adjustHintButton(NxButton.LEFT))
        assertEquals(NxButton.DOWN, adjustHintButton(NxButton.UP))
        assertEquals(NxButton.B, adjustHintButton(NxButton.B))
        assertNull(adjustHintButton(NxButton.A))
    }

    @Test fun tapped_adjust_hints_change_the_value_and_move_to_the_next_visual_row() {
        val m = model().openFrontend()
        m.select(1); m.press(NxButton.A)                     // adjust Screen Sharpness
        assertEquals(MenuEffect.SetFrontend(FrontendOptions.SHARPNESS, "LINEAR"), m.press(adjustHintButton(NxButton.LEFT)!!))
        assertNull(m.press(adjustHintButton(NxButton.UP)!!))
        assertEquals(PageKind.Adjust(FrontendOptions.OVERLAY), m.state.page.kind)
    }

    @Test fun b_leaves_adjust_mode_with_the_adjusted_row_selected() {
        val m = model().openFrontend()
        m.press(NxButton.A)                                  // adjust Screen Scaling
        m.press(NxButton.DOWN)                               // Screen Sharpness
        m.press(NxButton.RIGHT)
        assertNull(m.press(NxButton.B))
        assertEquals(PageKind.Frontend, m.state.page.kind)
        assertEquals(1, m.state.page.selected)
        assertEquals("LINEAR", m.optionRow(1).valueLabel)
    }

    @Test fun menu_closes_from_adjust_mode() {
        val m = model().openFrontend()
        m.press(NxButton.A)
        assertEquals(MenuEffect.Close, m.press(NxButton.MENU))
    }

    @Test fun stored_raw_value_shows_raw_and_right_goes_to_first() {
        val m = model().openFrontend(values = FrontendOptions.defaults() + (FrontendOptions.SCALING to "Cropped"))
        assertEquals("Cropped", m.optionRow(0).valueLabel)
        assertEquals(MenuEffect.SetFrontend(FrontendOptions.SCALING, "Native"), m.press(NxButton.RIGHT))
    }

    @Test fun locked_frontend_keys_are_hidden_and_skipped_in_adjust() {
        val m = model().openFrontend(locked = setOf(FrontendOptions.SHARPNESS))
        assertEquals(
            listOf("Screen Scaling", "Overlay", "Offset screen X", "Offset screen Y", "Pad Opacity (landscape)", "Debug HUD"),
            m.state.page.rows.map { it.label },
        )
        m.press(NxButton.A)                                  // adjust Screen Scaling
        m.press(NxButton.DOWN)
        assertEquals(PageKind.Adjust(FrontendOptions.OVERLAY), m.state.page.kind)
    }

    @Test fun set_frontend_refreshes_open_pages() {
        val m = model().openFrontend()
        m.press(NxButton.A)                                  // adjust Screen Scaling
        m.setFrontend(FrontendOptions.defaults() + (FrontendOptions.SCALING to "Native"), overlayNames, emptySet())
        assertEquals("Native", m.optionRow(0).valueLabel)
        m.press(NxButton.B)
        assertEquals("Native", m.optionRow(0).valueLabel)
    }

    private val scan = com.nxredux.mobile.shaders.ShaderParam("SCAN", "Scanline weight", 0.5f, 0f, 1f, 0.5f)
    private val mask = com.nxredux.mobile.shaders.ShaderParam("MASK", "Mask", 1f, 0f, 2f, 1f)
    private val presets = listOf("crt/crt-pi.glslp", "nx/line.glslp")

    private fun shadersModel(values: Map<String, String> = mapOf("nx_shader" to "crt/crt-pi.glslp"), params: List<com.nxredux.mobile.shaders.ShaderParam> = listOf(scan, mask)): InGameMenuModel {
        val m = plain(InGameMenuModel("Game", 9))
        m.setShaders(values, presets, params)
        m.press(NxButton.DOWN); m.press(NxButton.DOWN); m.press(NxButton.DOWN)   // Options
        m.press(NxButton.A)
        m.press(NxButton.DOWN); m.press(NxButton.DOWN)   // Shaders
        m.press(NxButton.A)
        return m
    }

    @Test fun options_page_lists_frontend_shaders_core_options_save_changes() {
        val m = plain(InGameMenuModel("Game", 9))
        m.press(NxButton.DOWN); m.press(NxButton.DOWN); m.press(NxButton.DOWN); m.press(NxButton.A)
        assertEquals(listOf("Shortcuts", "Frontend", "Shaders", "Core Options", "Save Changes"), m.state.page.rows.map { it.label })
    }

    @Test fun shaders_page_rows_and_values() {
        val m = shadersModel()
        assertEquals(PageKind.Shaders, m.state.page.kind)
        val rows = m.state.page.rows
        assertEquals(listOf("Shader", "Shader Parameters", "Reset Parameters"), rows.map { it.label })
        val preset = rows[0] as MenuRow.Option
        assertEquals("crt/crt-pi.glslp", preset.valueLabel); assertEquals(true, preset.visual)
        assertEquals(PageKind.ShaderParams, (rows[1] as MenuRow.Submenu).target)
        assertEquals(MenuAction.RESET_SHADER_PARAMS, (rows[2] as MenuRow.Action).action)
    }

    @Test fun shader_parameters_and_reset_only_show_with_a_preset() {
        val none = shadersModel(values = mapOf("nx_shader" to "None"))
        assertEquals(listOf("Shader"), none.state.page.rows.map { it.label })
        val absent = shadersModel(values = emptyMap())
        assertEquals(listOf("Shader"), absent.state.page.rows.map { it.label })
        // Choosing a preset rebuilds the page with all three, the selection still on the Shader row.
        none.setShaders(mapOf("nx_shader" to "crt/crt-pi.glslp"), presets, listOf(scan, mask))
        assertEquals(listOf("Shader", "Shader Parameters", "Reset Parameters"), none.state.page.rows.map { it.label })
        assertEquals(0, none.state.page.selected)
        none.press(NxButton.DOWN)
        none.setShaders(mapOf("nx_shader" to "None"), presets, listOf(scan, mask))   // back to None: the selection stays valid
        assertEquals(listOf("Shader"), none.state.page.rows.map { it.label })
        assertEquals(0, none.state.page.selected)
    }

    @Test fun frontend_row_on_the_options_page_is_compact_with_its_text_behind_info() {
        val m = plain(InGameMenuModel("Game", 9))
        m.press(NxButton.DOWN); m.press(NxButton.DOWN); m.press(NxButton.DOWN); m.press(NxButton.A)
        val row = m.state.page.rows.first { it.label == "Frontend" }
        assertEquals("", row.info); assertEquals(InGameMenuModel.FRONTEND_DETAIL, row.detail); assertTrue(row.hasMoreInfo)
    }

    @Test fun left_right_on_the_shader_row_emit_set_shader() {
        val m = shadersModel(values = mapOf("nx_shader" to "None"))
        assertEquals(MenuEffect.SetShader("nx_shader", "crt/crt-pi.glslp"), m.press(NxButton.RIGHT))
        assertEquals("crt/crt-pi.glslp", (m.state.page.rows[0] as MenuRow.Option).valueLabel)
        assertEquals(MenuEffect.SetShader("nx_shader", "None"), m.press(NxButton.LEFT))
    }

    @Test fun raw_preset_value_shows_raw_and_right_goes_to_none() {
        val m = shadersModel(values = mapOf("nx_shader" to "gone/x.glslp"))
        assertEquals("gone/x.glslp", (m.state.page.rows[0] as MenuRow.Option).valueLabel)
        assertEquals(MenuEffect.SetShader("nx_shader", "None"), m.press(NxButton.RIGHT))
    }

    @Test fun params_page_lists_labels_values_and_is_visual() {
        val m = shadersModel(values = mapOf("nx_shader" to "crt/crt-pi.glslp", "nx_shader_param_SCAN" to "1.00"))
        m.press(NxButton.DOWN); m.press(NxButton.A)
        assertEquals(PageKind.ShaderParams, m.state.page.kind)
        val rows = m.state.page.rows.map { it as MenuRow.Option }
        assertEquals(listOf("Scanline weight", "Mask"), rows.map { it.label })
        assertEquals(listOf("1.00", "1.00"), rows.map { it.valueLabel })   // stored, then default
        assertEquals(listOf("", ""), rows.map { it.info })                  // compact: never the raw parameter id
        assertTrue(rows.none { it.hasMoreInfo || "SCAN" in it.fullInfo || "MASK" in it.fullInfo })
        assertEquals(true, rows.all { it.visual })
        assertEquals(MenuEffect.SetShader("nx_shader_param_SCAN", "0.00"), m.press(NxButton.RIGHT))   // wraps past max
    }

    @Test fun params_page_without_params_shows_the_message() {
        val m = shadersModel(params = emptyList())
        m.press(NxButton.DOWN); m.press(NxButton.A)
        assertEquals(PageKind.ShaderParams, m.state.page.kind)
        assertEquals(0, m.state.page.rows.size)
        assertEquals("No settings for this shader.", m.state.page.footer)
    }

    @Test fun set_shaders_rebuilds_the_open_params_page() {
        val m = shadersModel()
        m.press(NxButton.DOWN); m.press(NxButton.A)
        m.setShaders(mapOf("nx_shader" to "nx/line.glslp"), presets, emptyList())
        assertEquals(PageKind.ShaderParams, m.state.page.kind)
        assertEquals("No settings for this shader.", m.state.page.footer)
        m.setShaders(mapOf("nx_shader" to "crt/crt-pi.glslp"), presets, listOf(scan))
        assertEquals(listOf("Scanline weight"), m.state.page.rows.map { it.label })
    }

    @Test fun reset_parameters_emits_defaults_and_shows_a_message() {
        val m = shadersModel(values = mapOf("nx_shader" to "crt/crt-pi.glslp", "nx_shader_param_SCAN" to "1.00"))
        m.press(NxButton.DOWN); m.press(NxButton.DOWN)
        assertEquals(MenuEffect.SetShaders(listOf("nx_shader_param_SCAN" to "0.50", "nx_shader_param_MASK" to "1.00")), m.press(NxButton.A))
        assertEquals(PageKind.Message, m.state.page.kind)
        assertEquals("Parameters reset.", m.state.page.footer)
        m.press(NxButton.B)
        assertEquals(PageKind.Shaders, m.state.page.kind)
    }

    @Test fun adjust_on_the_shader_row_and_on_params_stays_within_its_page() {
        val m = shadersModel()
        assertEquals(null, m.press(NxButton.A))
        assertEquals(PageKind.Adjust("nx_shader"), m.state.page.kind)
        m.press(NxButton.DOWN)   // only one visual row on the Shaders page: stays
        assertEquals(PageKind.Adjust("nx_shader"), m.state.page.kind)
        assertEquals(MenuEffect.SetShader("nx_shader", "nx/line.glslp"), m.press(NxButton.RIGHT))
        m.press(NxButton.B)
        m.press(NxButton.DOWN); m.press(NxButton.A)   // params page
        m.press(NxButton.A)
        assertEquals(PageKind.Adjust("nx_shader_param_SCAN"), m.state.page.kind)
        m.press(NxButton.DOWN)
        assertEquals(PageKind.Adjust("nx_shader_param_MASK"), m.state.page.kind)
        m.press(NxButton.DOWN)
        assertEquals(PageKind.Adjust("nx_shader_param_SCAN"), m.state.page.kind)   // wraps
        assertEquals(MenuEffect.SetShader("nx_shader_param_SCAN", "1.00"), m.press(NxButton.RIGHT))
        m.press(NxButton.B)
        assertEquals(PageKind.ShaderParams, m.state.page.kind)
        assertEquals(0, m.state.page.selected)
    }

    @Test fun adjust_of_a_vanished_param_closes_when_the_preset_changes() {
        val m = shadersModel()
        m.press(NxButton.DOWN); m.press(NxButton.A); m.press(NxButton.A)
        assertEquals(PageKind.Adjust("nx_shader_param_SCAN"), m.state.page.kind)
        m.setShaders(mapOf("nx_shader" to "nx/line.glslp"), presets, emptyList())
        assertEquals(PageKind.ShaderParams, m.state.page.kind)
    }

    private val lives = CheatEntry("Infinite Lives", "01", "Infinite Lives", enabled = false)
    private val score = CheatEntry("Max Score", "02", "(GameShark) Max Score", enabled = true)

    /** A core that takes cheats, with [entries] resolved; the panel on its Cheats tab, the focus on its tab row. */
    private fun cheatsModel(vararg entries: CheatEntry, expected: String = "Tetris.gb.cht"): InGameMenuModel = model().apply {
        cheatsAvailable = true
        setCheats(entries.toList(), "Cheats/GB", expected)
        press(NxButton.RIGHT)
        toTab(GamePanelTab.CHEATS)
    }

    @Test fun cheats_tab_shows_only_for_a_core_that_takes_cheats() {
        val m = model()
        m.setCheats(listOf(lives), "Cheats/GB", "Tetris.gb.cht")
        assertEquals(listOf(GamePanelTab.SHORTCUTS), m.panelState().tabs)   // the core ignores cheats
        m.cheatsAvailable = true
        assertEquals(listOf(GamePanelTab.SHORTCUTS, GamePanelTab.CHEATS), m.panelState().tabs)
        m.setCheats(emptyList(), "Cheats/GB", "Tetris.gb.cht")
        assertEquals(listOf(GamePanelTab.SHORTCUTS, GamePanelTab.CHEATS), m.panelState().tabs)   // none: still there, to say how
    }

    @Test fun cheats_tab_sits_after_achievements_and_before_players() {
        val m = model().also { it.cheatsAvailable = true }
        m.setAchievements(listOf(ach(1, RaAchievementState.UNLOCKED)), 1 to 1, RaSortOrder.DEFAULT, emptySet(), BADGES)
        m.setPlayers(listOf(p1, PlayerSlotRow(null, "", connected = false)), listOf(padA))
        assertEquals(listOf(GamePanelTab.SHORTCUTS, GamePanelTab.ACHIEVEMENTS, GamePanelTab.CHEATS, GamePanelTab.PLAYERS), m.panelState().tabs)
    }

    @Test fun no_cheats_tab_in_netplay() {
        val m = model().also { it.cheatsAvailable = true }
        m.netplay = true
        m.refresh()
        assertEquals(listOf(GamePanelTab.SHORTCUTS), m.panelState().tabs)
    }

    @Test fun cheats_tab_lists_one_row_per_cheat_with_its_source() {
        val m = cheatsModel(lives, score)
        assertEquals(listOf(CheatRow("Infinite Lives", "", false), CheatRow("Max Score", "GameShark", true)), m.panelState().cheats)
    }

    @Test fun a_on_a_cheat_row_flips_it_and_emits_set_cheat() {
        val m = cheatsModel(lives, score)
        m.press(NxButton.DOWN)                                       // the first cheat
        assertEquals(PanelFocus.Item(0), m.state.panelFocus)
        assertEquals(MenuEffect.SetCheat(0, true), m.press(NxButton.A))
        assertTrue(m.panelState().cheats[0].on)
        assertEquals(MenuEffect.SetCheat(0, false), m.press(NxButton.A))
        assertFalse(m.panelState().cheats[0].on)
        m.press(NxButton.DOWN)
        assertEquals(MenuEffect.SetCheat(1, false), m.press(NxButton.A))
        assertEquals(PanelFocus.Item(1), m.state.panelFocus)
        assertEquals(GamePanelTab.CHEATS, m.state.panelTab)
    }

    @Test fun a_tap_on_a_cheat_row_flips_it_and_moves_the_focus_there() {
        val m = cheatsModel(lives, score)
        m.press(NxButton.DOWN)
        val tap = GamePanelModel.cheatEvent(1, m.panelState().cheats)
        assertEquals(GamePanelEvent.SetCheat(1, on = false), tap)
        assertEquals(MenuEffect.SetCheat(1, false), m.panelEvent(tap!!))
        assertEquals(PanelFocus.Item(1), m.state.panelFocus)
        assertFalse(m.panelState().cheats[1].on)
        assertNull(m.panelEvent(GamePanelEvent.SetCheat(1, on = false)))   // already off
        assertNull(m.panelEvent(GamePanelEvent.SetCheat(5, on = true)))    // past the list
    }

    @Test fun setCheats_refreshes_the_panel_rows() {
        val m = cheatsModel()                                        // opened before the files loaded
        assertEquals(emptyList(), m.panelState().cheats)
        m.setCheats(listOf(lives, score), "Cheats/GB", "Tetris.gb.cht")
        assertEquals(listOf("Infinite Lives", "Max Score"), m.panelState().cheats.map { it.name })
        m.press(NxButton.DOWN); m.press(NxButton.DOWN)
        // A toggle from elsewhere (the dual-screen panel) comes back as the host's set: the focus stays.
        m.setCheats(listOf(lives, score.copy(enabled = false)), "Cheats/GB", "Tetris.gb.cht")
        assertEquals(PanelFocus.Item(1), m.state.panelFocus)
        assertFalse(m.panelState().cheats[1].on)
    }

    @Test fun empty_cheats_tab_says_how_to_add_them() {
        val m = cheatsModel()
        assertEquals(
            "No cheats for this game.\nDownload them with Tools → Cheat Database, or add a RetroArch .cht file as Cheats/GB/Tetris.gb.cht.",
            m.panelState().noCheatsText,
        )
        m.press(NxButton.DOWN)                                       // nothing to focus
        assertEquals(PanelFocus.Tabs, m.state.panelFocus)
        assertNull(m.press(NxButton.A))
        assertEquals(PageKind.Root, m.state.page.kind)               // the tab opens no page
    }

    @Test fun before_resolution_the_empty_tab_names_no_file() {
        val m = cheatsModel(expected = "")
        assertEquals(
            "No cheats for this game.\nDownload them with Tools → Cheat Database, or add a RetroArch .cht file to the Cheats folder.",
            m.panelState().noCheatsText,
        )
    }

    private fun InGameMenuModel.labels() = state.page.rows.map { it.label }

    @Test fun a_single_disc_game_shows_no_disc_row() {
        val m = model()
        m.setDiscs(current = 0, count = 1)
        assertEquals(listOf("Continue", "Save", "Load", "Options", "Quit"), m.labels())
        m.setDiscs(current = 0, count = 0)   // the core has no disk control
        assertEquals(listOf("Continue", "Save", "Load", "Options", "Quit"), m.labels())
    }

    @Test fun a_multi_disc_game_shows_disc_n_of_m_under_continue_and_cycles_it() {
        val m = model()
        m.setDiscs(current = 0, count = 2)
        assertEquals(listOf("Continue", "Disc", "Save", "Load", "Options", "Quit"), m.labels())
        val row = assertIs<MenuRow.Option>(m.state.page.rows[1])
        assertEquals(InGameMenuModel.DISC_KEY, row.key)
        assertEquals("1/2", row.valueLabel)
        assertEquals("", row.info); assertEquals(InGameMenuModel.DISC_INFO, row.detail)   // compact, its text behind A Info
        m.press(NxButton.DOWN)
        assertEquals(MenuEffect.ChangeDisc(1), m.press(NxButton.RIGHT))
        assertEquals("2/2", assertIs<MenuRow.Option>(m.state.page.rows[1]).valueLabel)
        assertEquals(MenuEffect.ChangeDisc(0), m.press(NxButton.RIGHT))   // wraps
        assertEquals(MenuEffect.ChangeDisc(1), m.press(NxButton.LEFT))
        assertEquals(1, m.state.page.selected)                             // still on the Disc row
        assertNull(m.press(NxButton.A))                                    // A shows the row's info, no effect
        assertEquals(PageKind.Message, m.state.page.kind)
        assertEquals(InGameMenuModel.DISC_INFO, m.state.page.footer)
    }

    @Test fun the_disc_row_appearing_keeps_the_selected_row() {
        val m = model()
        m.press(NxButton.DOWN)                                             // Save
        m.setDiscs(current = 2, count = 3)
        assertEquals("Save", m.state.page.rows[m.state.page.selected].label)
        assertEquals("3/3", assertIs<MenuRow.Option>(m.state.page.rows[1]).valueLabel)
        assertEquals(MenuEffect.SaveSlot(3), m.press(NxButton.A))          // slot 2 (0-based) from model(), 1-based in the effect
    }

    // ---- the game panel on the root's right side

    private fun InGameMenuModel.toTab(tab: GamePanelTab): InGameMenuModel {
        repeat(3) { if (state.panelTab != tab) press(NxButton.R1) }
        assertEquals(tab, state.panelTab)
        return this
    }

    @Test fun root_has_no_second_screen_or_players_row() {
        val m = model()
        m.setSecondScreen(connected = true, on = true)
        m.setPlayers(listOf(p1, PlayerSlotRow(null, "", connected = false)), listOf(padA))
        assertEquals(listOf("Continue", "Save", "Load", "Options", "Quit"), m.labels())
    }

    @Test fun right_enters_panel_on_continue() {
        val m = model()
        assertNull(m.press(NxButton.RIGHT))
        assertEquals(PanelFocus.Tabs, m.state.panelFocus)
        assertEquals(PageKind.Root, m.state.page.kind)
        assertEquals(PanelFocus.Tabs, m.panelState().focus)
    }

    @Test fun right_on_save_does_not_enter_panel() {
        val m = model()
        m.press(NxButton.DOWN)                               // Save
        m.press(NxButton.RIGHT)
        assertNull(m.state.panelFocus)
        assertEquals(3, m.state.slotIndex)                   // RIGHT still steps the slot
    }

    @Test fun right_on_the_disc_row_still_changes_the_disc() {
        val m = model()
        m.setDiscs(current = 0, count = 2)
        m.press(NxButton.DOWN)                               // Disc
        assertEquals(MenuEffect.ChangeDisc(1), m.press(NxButton.RIGHT))
        assertNull(m.state.panelFocus)
    }

    @Test fun l1_r1_switch_tabs() {
        val m = model()
        m.setAchievements(listOf(ach(1, RaAchievementState.UNLOCKED)), 1 to 1, RaSortOrder.DEFAULT, emptySet(), BADGES)
        m.setPlayers(listOf(p1, PlayerSlotRow(null, "", connected = false)), listOf(padA))
        assertEquals(listOf(GamePanelTab.SHORTCUTS, GamePanelTab.ACHIEVEMENTS, GamePanelTab.PLAYERS), m.panelState().tabs)
        // On the list too: the focus stays there.
        m.press(NxButton.R1); assertEquals(GamePanelTab.ACHIEVEMENTS, m.state.panelTab)
        m.press(NxButton.R1); assertEquals(GamePanelTab.PLAYERS, m.state.panelTab)
        m.press(NxButton.R1); assertEquals(GamePanelTab.SHORTCUTS, m.state.panelTab)   // wraps
        m.press(NxButton.L1); assertEquals(GamePanelTab.PLAYERS, m.state.panelTab)
        assertNull(m.state.panelFocus)
        m.press(NxButton.RIGHT); m.press(NxButton.L1)
        assertEquals(GamePanelTab.ACHIEVEMENTS, m.state.panelTab)
        assertEquals(PanelFocus.Tabs, m.state.panelFocus)
    }

    @Test fun b_leaves_panel() {
        val m = model()
        m.press(NxButton.RIGHT)
        assertNull(m.press(NxButton.B))
        assertNull(m.state.panelFocus)
        assertEquals(MenuEffect.Close, m.press(NxButton.B))   // B again closes the menu, as ever
    }

    @Test fun left_on_tabs_or_the_grid_edge_leaves_panel() {
        val m = model()
        m.press(NxButton.RIGHT); m.press(NxButton.LEFT)
        assertNull(m.state.panelFocus)
        m.press(NxButton.RIGHT); m.press(NxButton.DOWN)
        m.press(NxButton.RIGHT); assertEquals(PanelFocus.Item(1), m.state.panelFocus)
        m.press(NxButton.LEFT); assertEquals(PanelFocus.Item(0), m.state.panelFocus)
        m.press(NxButton.LEFT); assertNull(m.state.panelFocus)
    }

    @Test fun down_then_up_returns_to_tabs() {
        val m = model()
        m.press(NxButton.RIGHT)
        m.press(NxButton.DOWN); assertEquals(PanelFocus.Item(0), m.state.panelFocus)
        m.press(NxButton.UP); assertEquals(PanelFocus.Tabs, m.state.panelFocus)
    }

    @Test fun the_shortcut_grid_moves_by_its_columns() {
        val m = model()
        m.setDiscs(current = 0, count = 2)
        m.setSecondScreen(connected = true, on = true)
        m.setSpeedTiles(ff = false, rewind = false)          // 5 shortcuts
        m.panelColumns = 2
        m.press(NxButton.RIGHT); m.press(NxButton.DOWN)      // Item 0
        m.press(NxButton.DOWN); assertEquals(PanelFocus.Item(2), m.state.panelFocus)
        m.press(NxButton.RIGHT); assertEquals(PanelFocus.Item(3), m.state.panelFocus)
        m.press(NxButton.RIGHT); assertEquals(PanelFocus.Item(3), m.state.panelFocus)   // the row's right end
        m.press(NxButton.DOWN); assertEquals(PanelFocus.Item(4), m.state.panelFocus)    // the short last row's last pill
        m.press(NxButton.DOWN); assertEquals(PanelFocus.Item(4), m.state.panelFocus)
        m.press(NxButton.UP); assertEquals(PanelFocus.Item(2), m.state.panelFocus)
        m.press(NxButton.UP); m.press(NxButton.UP); assertEquals(PanelFocus.Tabs, m.state.panelFocus)
    }

    @Test fun ffTileToggles() {
        val m = model()                                      // no discs, no second screen: Fast forward, Rewind, Reset
        m.setSpeedTiles(ff = false, rewind = false)
        assertEquals(listOf(Shortcut.FAST_FORWARD, Shortcut.REWIND, Shortcut.RESET), m.panelState().shortcuts.map { it.shortcut })
        m.press(NxButton.RIGHT); m.press(NxButton.DOWN)
        // FF on: rewind's toggle goes off (last toggle wins); the host closes the menu and the game runs fast.
        assertEquals(MenuEffect.SetSpeed(ff = true, rewind = false), m.press(NxButton.A))
        m.setSpeedTiles(ff = true, rewind = false)
        assertEquals(true, m.panelState().shortcuts[0].on)
        assertEquals(MenuEffect.SetSpeed(ff = false, rewind = null), m.panelEvent(GamePanelEvent.SetFastForward(on = false)))
        assertEquals(MenuEffect.SetSpeed(ff = false, rewind = true), m.panelEvent(GamePanelEvent.SetRewind(on = true)))
        assertEquals(MenuEffect.SetSpeed(ff = null, rewind = false), m.panelEvent(GamePanelEvent.SetRewind(on = false)))
    }

    @Test fun rewindHiddenWithoutRewind() {
        val m = model()
        m.setSpeedTiles(ff = false, rewind = null)
        assertEquals(listOf(Shortcut.FAST_FORWARD, Shortcut.RESET), m.panelState().shortcuts.map { it.shortcut })
        assertNull(m.panelEvent(GamePanelEvent.SetRewind(on = true)))   // no tile, no rewind
    }

    @Test fun resetTileOpensDialog() {
        val m = model()
        m.setSpeedTiles(ff = false, rewind = false)
        assertNull(m.panelEvent(GamePanelEvent.Reset))
        assertEquals(PageKind.ResetGame, m.state.page.kind)
        assertEquals("Reset game?", m.state.page.title)
        assertEquals("Progress since your last save will be lost.", m.state.page.footer)
        assertEquals(listOf("Reset", "Cancel"), m.state.page.rows.map { it.label })
        assertEquals(PageKind.ResetGame, menuDialog(m.state).dialog?.kind)   // drawn as a dialog over the root
        assertEquals(PageKind.Root, menuDialog(m.state).under.page.kind)
        // Cancel: back on the root, nothing done.
        assertNull(m.activate(1))
        assertEquals(PageKind.Root, m.state.page.kind)
        assertEquals(1, m.state.stack.size)
        // Reset resets (the host closes the menu).
        m.panelEvent(GamePanelEvent.Reset)
        assertEquals(MenuEffect.ResetGame, m.activate(0))
        // B on the dialog is Cancel too.
        m.panelEvent(GamePanelEvent.Reset)
        assertNull(m.press(NxButton.B))
        assertEquals(PageKind.Root, m.state.page.kind)
    }

    @Test fun openResetDialog_opens_the_confirm_on_the_root() {
        // A Reset binding: the menu opened for the dialog, so Cancel and B resume the game.
        val m = model()
        m.openResetDialog(closesOnCancel = true)
        assertEquals(PageKind.ResetGame, m.state.page.kind)
        assertEquals(2, m.state.stack.size)
        m.openResetDialog(closesOnCancel = true)             // a second Reset press: still one dialog
        assertEquals(2, m.state.stack.size)
        m.press(NxButton.DOWN)
        assertEquals(MenuEffect.Close, m.press(NxButton.A))  // Cancel by pad
        val b = model()
        b.openResetDialog(closesOnCancel = true)
        assertEquals(MenuEffect.Close, b.press(NxButton.B))
        val t = model()
        t.openResetDialog(closesOnCancel = true)
        assertEquals(MenuEffect.Close, t.activate(1))        // Cancel by tap
        val r = model()
        r.openResetDialog(closesOnCancel = true)
        assertEquals(MenuEffect.ResetGame, r.press(NxButton.A))
    }

    @Test fun the_reset_tile_dialog_cancels_back_to_the_root() {
        val m = model()
        m.openResetDialog(closesOnCancel = true)
        assertEquals(MenuEffect.Close, m.press(NxButton.B))
        // The menu's own tile (the binding's flag does not carry over): Cancel and B come back to the root.
        val t = model()
        t.panelEvent(GamePanelEvent.Reset)
        assertNull(t.press(NxButton.B))
        assertEquals(PageKind.Root, t.state.page.kind)
        t.panelEvent(GamePanelEvent.Reset)
        assertNull(t.activate(1))
        assertEquals(PageKind.Root, t.state.page.kind)
        // Opened from the menu already up (no flag): back to the root too.
        t.openResetDialog()
        assertNull(t.press(NxButton.B))
        assertEquals(PageKind.Root, t.state.page.kind)
    }

    @Test fun netplay_has_no_reset_or_speed() {
        val m = model()
        m.netplay = true; m.refresh()
        m.setSpeedTiles(ff = false, rewind = false)
        assertTrue(m.panelState().shortcuts.none { it.shortcut in listOf(Shortcut.FAST_FORWARD, Shortcut.REWIND, Shortcut.RESET) })
        m.openResetDialog()
        assertEquals(PageKind.Root, m.state.page.kind)
        assertNull(m.panelEvent(GamePanelEvent.Reset))
        assertEquals(PageKind.Root, m.state.page.kind)
        assertNull(m.panelEvent(GamePanelEvent.SetFastForward(on = true)))
        assertNull(m.panelEvent(GamePanelEvent.SetRewind(on = true)))
    }

    @Test fun swap_disc_and_second_screen_shortcuts_reuse_the_menu_effects() {
        val m = model()
        m.setDiscs(current = 0, count = 3)
        m.setSecondScreen(connected = true, on = true)
        assertEquals(listOf(Shortcut.SWAP_DISC, Shortcut.SECOND_SCREEN), m.panelState().shortcuts.take(2).map { it.shortcut })
        m.press(NxButton.RIGHT); m.press(NxButton.DOWN)
        assertEquals(MenuEffect.ChangeDisc(1, toast = true), m.press(NxButton.A))   // the panel's swap says "Disc N of M"
        assertEquals("2/3", m.panelState().shortcuts[0].value)
        assertEquals("2/3", assertIs<MenuRow.Option>(m.state.page.rows[1]).valueLabel)   // the root's Disc row follows
        m.press(NxButton.RIGHT)
        assertEquals(MenuEffect.SetSecondScreen(false), m.press(NxButton.A))
        assertEquals(false, m.panelState().shortcuts[1].on)
        // A tap acts as A.
        assertEquals(MenuEffect.SetSecondScreen(true), m.panelEvent(GamePanelEvent.SetSecondScreen(true)))
        assertEquals(MenuEffect.ChangeDisc(2, toast = true), m.panelEvent(GamePanelEvent.SwapDisc))
    }

    @Test fun a_tap_on_a_panel_item_moves_the_controller_focus_there() {
        val m = model()
        m.setDiscs(current = 0, count = 3)
        m.setSecondScreen(connected = true, on = true)
        m.press(NxButton.RIGHT); m.press(NxButton.DOWN)
        assertEquals(PanelFocus.Item(0), m.state.panelFocus)
        m.panelEvent(GamePanelEvent.SetSecondScreen(false))
        assertEquals(PanelFocus.Item(1), m.state.panelFocus)
        m.panelEvent(GamePanelEvent.SwapDisc)
        assertEquals(PanelFocus.Item(0), m.state.panelFocus)
        // Players: a slot is its row.
        m.setPlayers(listOf(p1, PlayerSlotRow(null, "", connected = false)), listOf(padA))
        m.toTab(GamePanelTab.PLAYERS)
        m.panelEvent(GamePanelEvent.SetPlayer(0, null))
        assertEquals(PanelFocus.Item(0), m.state.panelFocus)
        m.panelEvent(GamePanelEvent.SetPlayer(1, "k1"))
        assertEquals(PanelFocus.Item(1), m.state.panelFocus)
    }

    @Test fun a_tap_on_the_panel_leaves_the_focus_on_the_list() {
        val m = model()
        m.setDiscs(current = 0, count = 3)
        m.panelEvent(GamePanelEvent.SwapDisc)
        assertNull(m.state.panelFocus)
    }

    @Test fun a_lost_second_screen_clamps_the_panel_focus() {
        val m = model()
        m.setSecondScreen(connected = true, on = true)
        m.setSpeedTiles(ff = false, rewind = false)          // Second screen, Fast forward, Rewind, Reset
        m.press(NxButton.RIGHT); m.press(NxButton.DOWN)
        repeat(3) { m.press(NxButton.RIGHT) }
        assertEquals(PanelFocus.Item(3), m.state.panelFocus)
        m.setSecondScreen(connected = false, on = true)
        assertEquals(PanelFocus.Item(2), m.state.panelFocus)
    }

    @Test fun achievement_a_in_the_panel_does_nothing() {
        val m = model()
        m.setAchievements(listOf(ach(2, RaAchievementState.LOCKED, "Zed"), ach(1, RaAchievementState.UNLOCKED, "Alpha")), 1 to 2, RaSortOrder.TITLE_AZ, emptySet(), BADGES)
        m.press(NxButton.RIGHT); m.toTab(GamePanelTab.ACHIEVEMENTS)
        assertEquals(listOf("Alpha", "Zed"), m.panelState().achievements.map { it.title })   // the chosen order
        m.press(NxButton.DOWN); m.press(NxButton.DOWN)
        assertNull(m.press(NxButton.A))
        assertEquals(PageKind.Root, m.state.page.kind)                 // no detail page
        assertEquals(PanelFocus.Item(1), m.state.panelFocus)
    }

    @Test fun page_push_resets_panel_focus() {
        val m = model()
        m.setAchievements(listOf(ach(1, RaAchievementState.UNLOCKED)), 1 to 1, RaSortOrder.DEFAULT, emptySet(), BADGES)
        m.press(NxButton.RIGHT); m.toTab(GamePanelTab.ACHIEVEMENTS)
        m.press(NxButton.A)                                   // A on the tab row opens the full page
        assertNull(m.state.panelFocus)
        m.press(NxButton.B)
        assertEquals(PageKind.Root, m.state.page.kind)
        assertNull(m.state.panelFocus)                       // back on the list, not in the panel
        assertEquals(GamePanelTab.ACHIEVEMENTS, m.state.panelTab)
    }

    @Test fun a_tap_on_the_list_leaves_panel_and_close_resets_it() {
        val m = model()
        m.press(NxButton.RIGHT)
        m.select(3)
        assertNull(m.state.panelFocus)
        m.press(NxButton.RIGHT)
        assertEquals(MenuEffect.Close, m.press(NxButton.MENU))
        assertNull(m.state.panelFocus)
    }

    @Test fun losing_the_achievements_tab_moves_the_focus_to_shortcuts() {
        val m = model()
        m.setAchievements(listOf(ach(1, RaAchievementState.UNLOCKED), ach(2, RaAchievementState.LOCKED)), 1 to 2, RaSortOrder.DEFAULT, emptySet(), BADGES)
        m.press(NxButton.RIGHT); m.toTab(GamePanelTab.ACHIEVEMENTS); m.press(NxButton.DOWN)
        assertEquals(PanelFocus.Item(0), m.state.panelFocus)
        m.setAchievementsAvailable(false)                    // signed out mid-menu
        assertEquals(GamePanelTab.SHORTCUTS, m.state.panelTab)
        assertEquals(GamePanelTab.SHORTCUTS, m.panelState().tab)
        assertEquals(PanelFocus.Item(0), m.state.panelFocus) // in range of the shortcuts
        assertTrue(0 < m.panelState().shortcuts.size)
    }

    @Test fun root_rows_beside_the_panel_lead_with_a_panel_hint() {
        val m = model()
        m.setDiscs(current = 0, count = 2)
        fun hints() = hintsFor(m.state.page, m.state.page.rows[m.state.page.selected], panelFocus = m.state.panelFocus, panelTab = m.state.panelTab, panel = true)
        assertEquals(listOf(Hint(NxButton.RIGHT, "Panel"), Hint(NxButton.A, "Select"), Hint(NxButton.B, "Resume")), hints())   // Continue
        m.press(NxButton.DOWN)                               // Disc: ◄► changes the disc
        assertEquals(Hint(NxButton.LEFT, "Change"), hints().first())
        m.press(NxButton.DOWN)                               // Save: the slot card
        assertEquals(Hint(NxButton.LEFT, "Slot"), hints().first())
        m.press(NxButton.UP); m.press(NxButton.UP)           // Continue again
        assertEquals(listOf(Hint(NxButton.A, "Select"), Hint(NxButton.B, "Resume")),
            hintsFor(m.state.page, m.state.page.rows[0]))     // no panel drawn: no hint
    }

    @Test fun the_options_row_previews_the_options_page() {
        val m = model()
        m.setDiscs(current = 0, count = 2)
        for (label in listOf("Continue", "Disc", "Save", "Load", "Quit")) {
            m.select(m.labels().indexOf(label))
            assertNull(m.state.preview, label)
        }
        val options = m.labels().indexOf("Options")
        m.select(options)
        val preview = m.state.preview!!
        assertEquals(PageKind.Options, preview.kind)
        // Not the panel: L1/R1 leave its tab alone.
        m.press(NxButton.R1)
        assertEquals(GamePanelTab.SHORTCUTS, m.state.panelTab)
        // A opens the same page, the first category highlighted; the root underneath previews nothing.
        assertNull(m.press(NxButton.A))
        assertEquals(preview.rows, m.state.page.rows)
        assertEquals(0, m.state.page.selected)
        assertNull(m.state.preview)
        // B back to the root on Options, its preview again.
        m.press(NxButton.B)
        assertEquals(listOf(PageKind.Root), m.state.stack.map { it.kind })
        assertEquals(options, m.state.page.selected)
        assertEquals(preview, m.state.preview)
    }

    @Test fun a_tap_on_a_preview_row_opens_the_options_page_on_it() {
        val m = model()
        assertNull(m.tapPreview(1))                          // Continue: no preview, nothing opens
        assertEquals(1, m.state.stack.size)
        m.select(m.labels().indexOf("Options"))
        val shaders = m.state.preview!!.rows.indexOfFirst { it.label == "Shaders" }
        assertNull(m.tapPreview(shaders))
        assertEquals(listOf(PageKind.Root, PageKind.Options), m.state.stack.map { it.kind })
        assertEquals(shaders, m.state.page.selected)
        // nxTap's second call finds the page open: nothing changes.
        assertNull(m.tapPreview(shaders))
        assertEquals(PageKind.Options, m.state.page.kind)
    }

    @Test fun the_options_row_hints_open_and_resume_without_the_panel_hint() {
        val m = model()
        m.select(m.labels().indexOf("Options"))
        assertEquals(listOf(Hint(NxButton.A, "Open"), Hint(NxButton.B, "Resume")),
            hintsFor(m.state.page, m.state.page.rows[m.state.page.selected], panelFocus = m.state.panelFocus, panelTab = m.state.panelTab, panel = true))
        m.press(NxButton.A)
        assertEquals(Hint(NxButton.B, "Back"), hintsFor(m.state.page, m.state.page.rows[0], panel = true).last())
    }

    @Test fun the_panel_state_is_reused_until_an_input_changes() {
        val m = model()
        m.setPlayers(listOf(p1, PlayerSlotRow(null, "", connected = false)), listOf(padA))
        val first = m.panelState()
        assertTrue(first === m.panelState())
        m.setPlayers(listOf(p1, PlayerSlotRow("k2", "Xbox Wireless Controller", connected = true)), listOf(padA, padB))
        assertEquals("Xbox Wireless Controller", m.panelState().players[1].name)
        m.press(NxButton.RIGHT)
        assertEquals(PanelFocus.Tabs, m.panelState().focus)
    }

    @Test fun panel_only_changes_still_change_the_state() {
        // The host publishes the state after each change; an equal value would not redraw the panel.
        val m = model()
        var before = m.state
        m.setPlayers(listOf(p1, PlayerSlotRow(null, "", connected = false)), listOf(padA))
        assertTrue(before != m.state); before = m.state
        m.setSecondScreen(connected = true, on = true)
        assertTrue(before != m.state); before = m.state
        m.setAchievements(listOf(ach(1, RaAchievementState.UNLOCKED)), 1 to 1, RaSortOrder.DEFAULT, emptySet(), BADGES)
        assertTrue(before != m.state); before = m.state
        m.setAchievementsAvailable(false)
        assertTrue(before != m.state); before = m.state
        m.panelEvent(GamePanelEvent.SetSecondScreen(false))
        assertTrue(before != m.state); before = m.state
        m.setPanelHeader(GamePanelHeader("Tetris", null, "GBA", 2L, null, "12:00", 50))
        assertTrue(before != m.state)
        assertEquals(listOf("Continue", "Save", "Load", "Options", "Quit"), m.labels())   // the pages themselves did not change
    }

    @Test fun a_tab_tap_selects_it() {
        val m = model()
        m.setPlayers(listOf(p1, PlayerSlotRow(null, "", connected = false)), listOf(padA))
        assertNull(m.panelEvent(GamePanelEvent.SelectTab(GamePanelTab.PLAYERS)))
        assertEquals(GamePanelTab.PLAYERS, m.state.panelTab)
        m.panelEvent(GamePanelEvent.SelectTab(GamePanelTab.ACHIEVEMENTS))   // no set: not shown, so ignored
        assertEquals(GamePanelTab.PLAYERS, m.state.panelTab)
    }

    @Test fun panel_hints_name_tabs_select_and_back() {
        val m = model()
        m.setPlayers(listOf(p1, PlayerSlotRow(null, "", connected = false)), listOf(padA))
        m.press(NxButton.RIGHT)
        assertEquals(listOf(Hint(NxButton.L1, "Tab"), Hint(NxButton.B, "Back"), Hint(NxButton.R1, "Tab")),
            hintsFor(m.state.page, m.state.page.rows[0], panelFocus = m.state.panelFocus, panelTab = m.state.panelTab))
        m.toTab(GamePanelTab.PLAYERS)
        assertEquals(listOf(Hint(NxButton.L1, "Tab"), Hint(NxButton.A, "Open"), Hint(NxButton.B, "Back"), Hint(NxButton.R1, "Tab")),
            hintsFor(m.state.page, m.state.page.rows[0], panelFocus = m.state.panelFocus, panelTab = m.state.panelTab))
        m.press(NxButton.DOWN)
        assertEquals(listOf(Hint(NxButton.L1, "Tab"), Hint(NxButton.A, "Select"), Hint(NxButton.B, "Back"), Hint(NxButton.R1, "Tab")),
            hintsFor(m.state.page, m.state.page.rows[0], panelFocus = m.state.panelFocus, panelTab = m.state.panelTab))
    }

    private val BADGES = mapOf("b1.png" to "content://b1", "b2_lock.png" to "content://b2l")
    private fun ach(id: Int, state: RaAchievementState, title: String = "A$id", points: Int = 5) =
        RaAchievement(id, title, "desc $id", points, "b$id", state, if (state == RaAchievementState.LOCKED) null else 100L, "3/10", 30f, 50f, null)

    private fun InGameMenuModel.openAchievements(): InGameMenuModel {
        press(NxButton.RIGHT); toTab(GamePanelTab.ACHIEVEMENTS); press(NxButton.A)   // A on the tab row opens the full page
        return this
    }

    @Test fun options_has_no_achievements_row() {
        val m = model()
        m.setAchievements(listOf(ach(1, RaAchievementState.UNLOCKED), ach(2, RaAchievementState.LOCKED)), 1 to 2, RaSortOrder.DEFAULT, emptySet(), BADGES)
        m.press(NxButton.DOWN); m.press(NxButton.DOWN); m.press(NxButton.DOWN); m.press(NxButton.A)   // Options
        assertEquals(listOf("Shortcuts", "Frontend", "Shaders", "Core Options", "Save Changes"), m.state.page.rows.map { it.label })
    }

    @Test fun the_achievements_tab_shows_with_a_set_and_the_header_counts_it() {
        val m = model()
        assertEquals(listOf(GamePanelTab.SHORTCUTS), m.panelState().tabs)
        m.setAchievements(listOf(ach(1, RaAchievementState.UNLOCKED), ach(2, RaAchievementState.LOCKED)), 1 to 2, RaSortOrder.DEFAULT, emptySet(), BADGES)
        assertEquals(listOf(GamePanelTab.SHORTCUTS, GamePanelTab.ACHIEVEMENTS), m.panelState().tabs)
        assertEquals(1 to 2, m.panelState().header.ra)
        m.setAchievementsAvailable(false)
        assertEquals(listOf(GamePanelTab.SHORTCUTS), m.panelState().tabs)
        assertNull(m.panelState().header.ra)
    }

    @Test fun achievements_page_lists_sorted_rows_filters_locked_and_toggles_mute() {
        val m = model()
        m.setAchievements(listOf(ach(2, RaAchievementState.LOCKED, "Zed"), ach(1, RaAchievementState.UNLOCKED, "Alpha"), ach(3, RaAchievementState.PENDING, "Mid")), 2 to 3, RaSortOrder.TITLE_AZ, setOf(2), BADGES)
        m.openAchievements()
        assertEquals(PageKind.Achievements, m.state.page.kind)
        assertEquals(listOf("Alpha", "Mid", "[M] Zed"), m.state.page.rows.map { it.label })
        assertEquals(listOf("Unlocked", "Pending sync", "Locked · 5 pts"), m.state.page.rows.map { (it as MenuRow.Option).valueLabel })
        assertNull(m.press(NxButton.Y)); assertEquals(listOf("[M] Zed"), m.state.page.rows.map { it.label })
        assertEquals(MenuEffect.ToggleMute(2), m.press(NxButton.X)); assertEquals(listOf("Zed"), m.state.page.rows.map { it.label })
        m.press(NxButton.Y); assertEquals(3, m.state.page.rows.size)
    }
    @Test fun achievement_rows_open_nothing_and_x_still_mutes() {
        val m = model()
        m.setAchievements(listOf(ach(1, RaAchievementState.UNLOCKED), ach(2, RaAchievementState.LOCKED)), 1 to 2, RaSortOrder.DEFAULT, emptySet(), BADGES)
        m.openAchievements()
        assertNull(m.press(NxButton.A)); assertEquals(PageKind.Achievements, m.state.page.kind)   // no detail, no message
        assertNull(m.press(NxButton.RIGHT)); assertEquals(PageKind.Achievements, m.state.page.kind)
        assertEquals("content://b1", m.badgePath(m.achievementOf(m.state.page.rows[0])!!))
        assertEquals("content://b2l", m.badgePath(m.achievementOf(m.state.page.rows[1])!!))   // the locked badge
        assertEquals(MenuEffect.ToggleMute(1), m.press(NxButton.X))
        m.press(NxButton.B); assertEquals(PageKind.Root, m.state.page.kind)
    }
    @Test fun an_unrecognised_game_has_no_achievements_tab() {
        val m = model()
        m.setAchievements(null, null, RaSortOrder.DEFAULT, emptySet(), BADGES)
        assertEquals(listOf(GamePanelTab.SHORTCUTS), m.panelState().tabs)
        m.press(NxButton.R1)
        assertEquals(GamePanelTab.SHORTCUTS, m.state.panelTab)
    }

    @Test fun nds_frontend_page_keeps_only_the_all_console_rows() {
        val m = model().openFrontend(values = FrontendOptions.defaults("NDS"), tag = "NDS")
        assertEquals(
            listOf("Screen Scaling", "Screen Sharpness", "Overlay", "Offset screen X", "Offset screen Y", "Pad Opacity (landscape)", "Debug HUD"),
            m.state.page.rows.map { it.label },
        )
    }

    private fun InGameMenuModel.openConsoleSettings(locked: Set<String> = emptySet()): InGameMenuModel {
        setFrontend(FrontendOptions.defaults("NDS"), emptyList(), locked, "NDS", overlayAvailable = false)
        select(3); activate(3)                               // Options page
        assertNull(activate(0))                              // Console Settings
        assertEquals(PageKind.ConsoleSettings, state.page.kind)
        return this
    }

    @Test fun nds_options_start_with_console_settings() {
        val m = model()
        m.setFrontend(FrontendOptions.defaults("NDS"), emptyList(), emptySet(), "NDS", overlayAvailable = false)
        m.select(3); m.activate(3)                           // Root → Options
        assertEquals(
            listOf("Console Settings", "Shortcuts", "Frontend", "Shaders", "Core Options", "Save Changes"),
            m.state.page.rows.map { it.label },
        )
        assertEquals(MenuRow.Submenu(InGameMenuModel.CONSOLE_SETTINGS_LABEL, PageKind.ConsoleSettings), m.state.page.rows.first())
    }

    /** The in-game Options → Console Settings rows for [tag]. */
    private fun inGameConsoleRows(tag: String): List<String> {
        val m = model()
        m.setFrontend(FrontendOptions.defaults(tag), emptyList(), emptySet(), tag)
        m.select(3); m.activate(3)
        assertEquals(listOf("Console Settings", "Shortcuts", "Frontend", "Shaders", "Core Options", "Save Changes"), m.state.page.rows.map { it.label })
        assertNull(m.activate(0))
        return m.state.page.rows.map { it.label }
    }

    @Test fun a_plain_console_has_the_controller_layout_and_speed_rows() {
        assertEquals(listOf("Controller Layout", "Max FF Speed", "Fast Forward Audio", "Rewind", "Rewind Audio"), inGameConsoleRows("GBA"))
    }

    // A row read only when the game starts (FrontendOption.atLaunch) is set in Game Settings or Emulator
    // Settings, not in the running game's menu.
    @Test fun rows_read_only_at_launch_stay_out_of_the_in_game_console_settings() {
        assertEquals(listOf("Controller Layout", "Max FF Speed", "Fast Forward Audio", "Rewind", "Rewind Audio"), inGameConsoleRows("N64"))   // no Graphics API
        assertEquals(listOf("Controller Layout", "Max FF Speed", "Fast Forward Audio", "Rewind", "Rewind Audio"), inGameConsoleRows("GC"))
        assertEquals(listOf("Wii Controller", "Motion Controls", "Controller Layout", "Max FF Speed", "Fast Forward Audio", "Rewind", "Rewind Audio"), inGameConsoleRows("WII"))
    }

    // Bottom screen (every console) shows in the running game's Console Settings only while a second display is
    // attached, and changes live like any frontend row; the page follows the display coming and going.
    @Test fun the_bottom_screen_row_shows_with_a_second_display_and_edits_live() {
        val m = model()
        m.setFrontend(FrontendOptions.defaults("GBA"), emptyList(), emptySet(), "GBA")
        m.select(3); m.activate(3)                                         // Options
        assertNull(m.activate(0))                                          // Console Settings
        assertEquals(listOf("Controller Layout", "Max FF Speed", "Fast Forward Audio", "Rewind", "Rewind Audio"), m.state.page.rows.map { it.label })
        m.setSecondScreen(connected = true, on = true)
        assertEquals(listOf("Bottom screen", "Controller Layout", "Max FF Speed", "Fast Forward Audio", "Rewind", "Rewind Audio"), m.state.page.rows.map { it.label })
        m.select(0)
        assertEquals("System default", m.optionRow(0).valueLabel)
        assertEquals(MenuEffect.SetFrontend(BottomScreenOptions.KEY, "main"), m.press(NxButton.RIGHT))
        assertEquals("Main screen", m.optionRow(0).valueLabel)
        m.setSecondScreen(connected = false, on = true)
        assertEquals(listOf("Controller Layout", "Max FF Speed", "Fast Forward Audio", "Rewind", "Rewind Audio"), m.state.page.rows.map { it.label })
    }

    @Test fun a_second_display_offers_console_settings_where_every_other_row_is_locked() {
        val m = model()   // plainLocked: the plain console's rows locked
        m.setSecondScreen(connected = true, on = true)
        m.select(3); m.activate(3)                                         // Options (no Second screen row on the root)
        assertEquals(listOf("Console Settings", "Shortcuts", "Frontend", "Shaders", "Core Options", "Save Changes"), m.state.page.rows.map { it.label })
        m.setSecondScreen(connected = false, on = true)
        assertEquals(listOf("Shortcuts", "Frontend", "Shaders", "Core Options", "Save Changes"), m.state.page.rows.map { it.label })
    }

    // A new lock set while Options is open (Save Changes → Restore defaults, the Wii's sensors once known) brings the
    // Console Settings row back, or takes it away, so it never opens an empty page; the status footer stays.
    @Test fun a_new_lock_set_rebuilds_the_open_options_page() {
        val m = model()   // plainLocked: the plain console's rows locked
        m.select(3); m.activate(3)                                         // Options
        assertEquals(listOf("Shortcuts", "Frontend", "Shaders", "Core Options", "Save Changes"), m.state.page.rows.map { it.label })
        val footer = m.state.page.footer
        m.setFrontend(FrontendOptions.defaults(plainTag), emptyList(), emptySet(), plainTag)
        assertEquals(listOf("Console Settings", "Shortcuts", "Frontend", "Shaders", "Core Options", "Save Changes"), m.state.page.rows.map { it.label })
        assertEquals(footer, m.state.page.footer)
        m.setFrontend(FrontendOptions.defaults(plainTag), emptyList(), plainLocked, plainTag)
        assertEquals(listOf("Shortcuts", "Frontend", "Shaders", "Core Options", "Save Changes"), m.state.page.rows.map { it.label })
        assertEquals(footer, m.state.page.footer)
    }

    @Test fun console_settings_open_on_only_bottom_screen_closes_when_the_display_goes() {
        val m = model()   // plainLocked: the plain console's rows locked
        m.setSecondScreen(connected = true, on = true)
        m.select(3); m.activate(3)                                         // Options
        assertNull(m.activate(0))                                          // Console Settings
        assertEquals(listOf("Bottom screen"), m.state.page.rows.map { it.label })
        m.setSecondScreen(connected = false, on = true)
        assertEquals(PageKind.Options, m.state.page.kind)                  // back to Options, never an empty page
        assertEquals(listOf("Shortcuts", "Frontend", "Shaders", "Core Options", "Save Changes"), m.state.page.rows.map { it.label })
    }

    @Test fun console_settings_is_not_offered_when_every_row_is_locked() {
        val m = model()   // plainLocked: the plain console's rows locked
        m.select(3); m.activate(3)
        assertEquals(listOf("Shortcuts", "Frontend", "Shaders", "Core Options", "Save Changes"), m.state.page.rows.map { it.label })
    }

    @Test fun console_settings_lists_the_ds_rows_and_edits_them_like_frontend_rows() {
        val m = model().openConsoleSettings()
        assertEquals("Console Settings", m.state.page.title)
        assertEquals(ConsoleSettings.tableFor("NDS").filter { it != BottomScreenOptions.row }.map { it.label }, m.state.page.rows.map { it.label })
        assertEquals(
            listOf(
                "Layout (portrait)", "Layout (portrait, controller)", "Layout (landscape)", "Big Screen", "Inset Corner", "Inset Size",
                "Inset Opacity", "Portrait Pad", "Controller Layout", "Max FF Speed", "Fast Forward Audio", "Rewind", "Rewind Audio",
            ),
            m.state.page.rows.map { it.label },
        )
        assertEquals("Auto", m.optionRow(0).valueLabel)                   // the portrait layout's default
        assertEquals(MenuEffect.SetFrontend(NdsOptions.LAYOUT_PORTRAIT, "pip"), m.press(NxButton.RIGHT))
        assertEquals("Picture in picture", m.optionRow(0).valueLabel)
        assertEquals("Stacked", m.optionRow(1).valueLabel)                 // the controller layout's default
        m.select(3)                                                        // Big Screen
        assertEquals(MenuEffect.SetFrontend(NdsOptions.BIG_SCREEN, "bottom"), m.press(NxButton.RIGHT))
        assertNull(m.press(NxButton.A))                                    // a visual row: adjust mode
        assertEquals(PageKind.Adjust(NdsOptions.BIG_SCREEN), m.state.page.kind)
        assertEquals("Bottom screen", (m.state.page.rows.single() as MenuRow.Option).valueLabel)
        assertEquals(MenuEffect.SetFrontend(NdsOptions.BIG_SCREEN, "top"), m.press(NxButton.LEFT))
        m.press(NxButton.DOWN)                                             // DOWN walks this page's visual rows
        assertEquals(PageKind.Adjust(NdsOptions.PIP_CORNER), m.state.page.kind)
        m.press(NxButton.B)
        assertEquals(PageKind.ConsoleSettings, m.state.page.kind)
        assertEquals(4, m.state.page.selected)                             // the owner followed the strip
        assertEquals("Top screen", m.optionRow(3).valueLabel)              // refreshed from the edit
    }

    @Test fun a_locked_console_row_is_hidden() {
        val m = model().openConsoleSettings(locked = setOf(NdsOptions.BIG_SCREEN))
        assertTrue(m.state.page.rows.none { it.label == "Big Screen" })
        assertEquals(12, m.state.page.rows.size)
    }

    @Test fun other_consoles_keep_the_plain_frontend_page() {
        val m = model().openFrontend(tag = "GBA")
        assertEquals(7, m.state.page.rows.size)
        assertEquals("Screen Scaling", m.state.page.rows.first().label)
    }

    @Test fun the_overlay_row_is_unavailable_while_the_screens_are_split() {
        val m = model().openFrontend(values = FrontendOptions.defaults("NDS"), tag = "NDS", overlayAvailable = false)
        val row = m.optionRow(2)
        assertEquals("Overlay", row.label)
        assertEquals(InGameMenuModel.OVERLAY_UNAVAILABLE, row.valueLabel)
        assertFalse(row.visual)
        m.select(2)
        assertNull(m.press(NxButton.RIGHT))
        assertTrue(row.choices.isEmpty())
        assertNull(m.press(NxButton.A))                                    // the row's text says why
        assertNull(m.state.popup)
        m.setFrontend(FrontendOptions.defaults("NDS"), overlayNames, emptySet(), "NDS", overlayAvailable = true)
        assertEquals("None", m.optionRow(2).valueLabel)
        assertEquals(MenuEffect.SetFrontend(FrontendOptions.OVERLAY, "Aspect.png"), m.press(NxButton.RIGHT))
    }

    @Test fun adjust_mode_steps_over_the_unavailable_overlay() {
        val m = model().openFrontend(values = FrontendOptions.defaults("NDS"), tag = "NDS", overlayAvailable = false)
        m.select(1)                                                        // Screen Sharpness
        m.press(NxButton.A)
        m.press(NxButton.DOWN)
        assertEquals(PageKind.Adjust(FrontendOptions.OFFSET_X), m.state.page.kind)
    }

    @Test fun x_shows_the_full_text_of_a_compact_visual_row() {
        // NDS Console Settings: Layout (portrait) is visual (A would Adjust) and compact, its text behind the info icon.
        val m = model().openConsoleSettings()
        m.select(0)
        assertTrue(m.optionRow(0).visual)
        val full = ConsoleSettings.tableFor("NDS")[0]
        assertEquals("", m.optionRow(0).info)               // no line
        assertEquals(full.info, m.optionRow(0).detail)      // the full text behind it
        assertTrue(m.optionRow(0).hasMoreInfo)
        assertFalse(m.state.selectedInfoTruncated)          // no report needed: X opens the detail
        assertTrue(m.state.selectedShowsInfo)
        assertNull(m.press(NxButton.X))
        assertEquals(MenuPopup.Info("Layout (portrait)", full.info), m.state.popup)
        assertEquals(PageKind.ConsoleSettings, m.state.page.kind)   // over the page, not a page
        assertNull(m.press(NxButton.X))                      // X closes it again
        assertNull(m.state.popup)
        assertEquals(0, m.state.page.selected)
    }

    /** Core Options with two plain options, their core text behind the info icon only. */
    private fun twoCoreOptions(): InGameMenuModel {
        val m = model()
        m.select(3); m.activate(3); m.activate(3)
        m.showCoreOptions(
            listOf(
                CoreOption("a", "1", label = "Alpha", info = "Alpha text.", values = listOf("1", "2")),
                CoreOption("b", "1", label = "Beta", info = "Beta text.", values = listOf("1", "2")),
            ),
            emptySet(),
        )
        return m
    }

    @Test fun a_core_option_is_compact_and_x_shows_its_text() {
        val m = twoCoreOptions()
        assertEquals("", m.optionRow(0).info)                // no line on the row
        assertFalse(m.state.selectedInfoTruncated)
        assertTrue(m.state.selectedShowsInfo)                // no report needed: the text is behind the icon
        assertNull(m.press(NxButton.X))
        assertEquals(MenuPopup.Info("Alpha", "Alpha text."), m.state.popup)
    }

    @Test fun a_truncation_report_fits_and_counts_only_for_its_own_row() {
        // NDS Frontend: Overlay (unavailable) and Debug HUD keep a short line.
        val m = model().openFrontend(values = FrontendOptions.defaults("NDS"), tag = "NDS", overlayAvailable = false)
        val hud = m.rowIndex("Debug HUD")
        m.select(hud)
        m.setInfoTruncated(m.optionRow(hud).info, true); m.setInfoTruncated(m.optionRow(hud).info, false)   // e.g. a rotation made it fit
        assertFalse(m.state.selectedInfoTruncated)
        m.setInfoTruncated(m.optionRow(hud).info, true)
        m.select(2)                                          // Overlay: its own line was not reported yet
        assertEquals(InGameMenuModel.OVERLAY_UNAVAILABLE_LINE, m.optionRow(2).info)
        assertFalse(m.state.selectedInfoTruncated)
        m.setInfoTruncated(m.optionRow(hud).info, false)     // a late report for the old row
        m.setInfoTruncated(InGameMenuModel.OVERLAY_UNAVAILABLE_LINE, true)
        assertTrue(m.state.selectedInfoTruncated)
        m.press(NxButton.X)
        assertEquals(MenuPopup.Info("Overlay", InGameMenuModel.OVERLAY_UNAVAILABLE_INFO), m.state.popup)
    }

    @Test fun x_shows_info_on_a_core_option_row_and_a_plain_frontend_row() {
        val core = model().openCoreOptions()
        core.press(NxButton.A)                               // Video category
        assertEquals(PageKind.Category("video"), core.state.page.kind)
        core.setInfoTruncated("Colors.", true)               // Palette
        assertNull(core.press(NxButton.X))
        assertEquals(MenuPopup.Info("Palette", "Colors."), core.state.popup)
        assertNull(core.press(NxButton.RIGHT))               // the popup takes the input
        core.press(NxButton.B)
        assertNull(core.state.popup)
        assertEquals(PageKind.Category("video"), core.state.page.kind)

        val fe = model().openFrontend()
        fe.select(6)                                         // Debug HUD (not visual)
        val hud = FrontendOptions.option(FrontendOptions.DEBUG_HUD)!!.info
        fe.setInfoTruncated(hud, true)
        assertNull(fe.press(NxButton.X))
        assertEquals(MenuPopup.Info("Debug HUD", hud), fe.state.popup)
        assertNull(fe.press(NxButton.A))                     // A closes it, not the switch under it
        assertNull(fe.state.popup)
        assertEquals("Off", fe.optionRow(6).valueLabel)
    }

    @Test fun x_on_an_option_without_info_or_in_adjust_does_nothing() {
        val m = model().openCoreOptions()
        m.press(NxButton.DOWN)                               // Speed (no info)
        m.setInfoTruncated("", true)
        assertNull(m.press(NxButton.X))
        assertEquals(PageKind.CoreOptions, m.state.page.kind)

        val fe = model().openFrontend()
        fe.select(1)
        fe.setInfoTruncated(fe.optionRow(1).info, true)
        fe.press(NxButton.A)                                 // Adjust Screen Sharpness
        assertNull(fe.press(NxButton.X))
        assertEquals(PageKind.Adjust(FrontendOptions.SHARPNESS), fe.state.page.kind)
    }

    @Test fun x_still_mutes_on_achievements() {
        val m = model()
        m.setAchievements(listOf(ach(1, RaAchievementState.UNLOCKED), ach(2, RaAchievementState.LOCKED)), 1 to 2, RaSortOrder.DEFAULT, emptySet(), BADGES)
        m.openAchievements()
        assertEquals(PageKind.Achievements, m.state.page.kind)
        assertEquals("desc 1", (m.state.page.rows[0] as MenuRow.Option).info)
        m.setInfoTruncated("desc 1", true)                   // even cut off, X mutes here
        assertEquals(MenuEffect.ToggleMute(1), m.press(NxButton.X))
        assertEquals(PageKind.Achievements, m.state.page.kind)
        assertEquals("[M] A1", m.state.page.rows[0].label)
        assertEquals(listOf(Hint(NxButton.Y, "Locked only"), Hint(NxButton.X, "Mute"), Hint(NxButton.B, "Back")),
            hintsFor(m.state.page, m.state.page.rows[0], infoTruncated = true))
    }

    @Test fun hints_offer_x_info_only_when_the_info_is_cut_off() {
        val visual = MenuRow.Option("Layout (portrait)", "k", "v", "Long help.", visual = true)
        val plain = MenuRow.Option("Debug HUD", "k", "v", "Help.")
        val bare = MenuRow.Option("Speed", "k", "v", "")
        fun page(kind: PageKind, row: MenuRow) = MenuPage(kind, "T", listOf(row))
        assertEquals(
            listOf(Hint(NxButton.LEFT, "Change"), Hint(NxButton.A, "Adjust"), Hint(NxButton.X, "Info"), Hint(NxButton.B, "Back")),
            hintsFor(page(PageKind.ConsoleSettings, visual), visual, infoTruncated = true),
        )
        assertEquals(
            listOf(Hint(NxButton.LEFT, "Change"), Hint(NxButton.A, "Adjust"), Hint(NxButton.B, "Back")),
            hintsFor(page(PageKind.ConsoleSettings, visual), visual, infoTruncated = false),
        )
        assertEquals(
            listOf(Hint(NxButton.LEFT, "Change"), Hint(NxButton.X, "Info"), Hint(NxButton.B, "Back")),
            hintsFor(page(PageKind.Frontend, plain), plain, infoTruncated = true),
        )
        assertEquals(listOf(Hint(NxButton.LEFT, "Change"), Hint(NxButton.B, "Back")), hintsFor(page(PageKind.Frontend, plain), plain))
        assertEquals(listOf(Hint(NxButton.LEFT, "Change"), Hint(NxButton.B, "Back")), hintsFor(page(PageKind.CoreOptions, bare), bare, infoTruncated = true))
        // The root has no info area: its option (Disc N/M) keeps A Info when it has info.
        assertEquals(
            listOf(Hint(NxButton.LEFT, "Change"), Hint(NxButton.A, "Info"), Hint(NxButton.B, "Resume")),
            hintsFor(page(PageKind.Root, plain), plain),
        )
        assertEquals(listOf(Hint(NxButton.LEFT, "Change"), Hint(NxButton.B, "Resume")), hintsFor(page(PageKind.Root, bare), bare))
        assertEquals(listOf(Hint(NxButton.B, "Back")), hintsFor(MenuPage(PageKind.Message, "T", emptyList(), footer = "x"), null))
    }

    @Test fun up_down_on_the_info_popup_ask_the_ui_to_scroll_it() {
        val m = model().openConsoleSettings()
        m.press(NxButton.X)
        assertEquals(0, (m.state.popup as MenuPopup.Info).scrollSeq)
        assertNull(m.press(NxButton.DOWN))
        assertEquals(MenuPopup.Info("Layout (portrait)", m.optionRow(0).detail, scrollSeq = 1, scrollDir = 1), m.state.popup)
        m.press(NxButton.DOWN)
        m.press(NxButton.UP)
        val info = m.state.popup as MenuPopup.Info
        assertEquals(3, info.scrollSeq); assertEquals(-1, info.scrollDir)
        m.press(NxButton.B)
        assertNull(m.state.popup)
        assertEquals(0, m.state.page.selected)              // UP/DOWN on the popup did not move the list
    }

    @Test fun up_down_on_a_message_ask_the_ui_to_scroll_it() {
        val m = model()
        m.showMessage("Save Changes", "Restored defaults.")
        assertNull(m.press(NxButton.DOWN))
        assertEquals(1, m.state.page.scrollSeq); assertEquals(1, m.state.page.scrollDir)
        m.press(NxButton.UP)
        assertEquals(2, m.state.page.scrollSeq); assertEquals(-1, m.state.page.scrollDir)
        assertEquals(PageKind.Message, m.state.page.kind)   // scrolling never leaves the page
    }

    @Test fun tapped_menu_hints_send_their_button_and_change_steps_forward() {
        assertEquals(NxButton.X, menuHintButton(NxButton.X))   // X Info / X Mute
        assertEquals(NxButton.Y, menuHintButton(NxButton.Y))   // Y Locked only
        assertEquals(NxButton.A, menuHintButton(NxButton.A))
        assertEquals(NxButton.B, menuHintButton(NxButton.B))
        assertEquals(NxButton.RIGHT, menuHintButton(NxButton.LEFT))   // CHANGE
        // A tapped X Info hint opens the full text, as the button does.
        val m = model().openConsoleSettings()
        m.setInfoTruncated(m.optionRow(0).info, true)
        m.press(menuHintButton(NxButton.X))
        assertIs<MenuPopup.Info>(m.state.popup)
        m.press(menuHintButton(NxButton.B))
        assertNull(m.state.popup)
    }

    private val p1 = PlayerSlotRow("k1", "8BitDo Pro 2", connected = true)
    private val padA = PlayerPadRow("k1", "8BitDo Pro 2")
    private val padB = PlayerPadRow("k2", "Xbox Wireless Controller")
    private val padC = PlayerPadRow("k3", "Retroid Pocket")   // benched (its slot set to Empty) or not: a candidate all the same

    private fun InGameMenuModel.openPlayers(): InGameMenuModel {
        press(NxButton.RIGHT); toTab(GamePanelTab.PLAYERS); press(NxButton.A)   // A on the tab row opens the full page
        return this
    }

    @Test fun players_tab_shows_only_for_a_multiplayer_core() {
        val m = model()
        assertEquals(listOf(GamePanelTab.SHORTCUTS), m.panelState().tabs)
        m.setPlayers(listOf(p1), listOf(padA))   // one slot: a one-player core
        assertEquals(listOf(GamePanelTab.SHORTCUTS), m.panelState().tabs)
        m.setPlayers(listOf(p1, PlayerSlotRow(null, "", connected = false)), listOf(padA))
        assertEquals(listOf(GamePanelTab.SHORTCUTS, GamePanelTab.PLAYERS), m.panelState().tabs)
    }

    @Test fun a_on_a_player_row_in_the_panel_cycles_its_pad() {
        val m = model()
        m.setPlayers(listOf(p1, PlayerSlotRow(null, "", connected = false)), listOf(padA, padB, padC))
        m.press(NxButton.RIGHT); m.toTab(GamePanelTab.PLAYERS)
        m.press(NxButton.DOWN)                                                   // Player 1
        assertEquals(MenuEffect.SetPlayer(0, "k2"), m.press(NxButton.A))
        m.press(NxButton.DOWN); m.press(NxButton.DOWN)                           // stays on Player 2: no pad rows after the slots
        assertEquals(PanelFocus.Item(1), m.state.panelFocus)
        assertEquals(PageKind.Root, m.state.page.kind)
    }

    @Test fun players_page_lists_only_the_slots() {
        val m = model()
        m.setPlayers(
            listOf(p1, PlayerSlotRow("k9", "DualSense", connected = false), PlayerSlotRow(null, "", connected = false)),
            listOf(padA, padB, padC),
        )
        m.openPlayers()
        assertEquals(PageKind.Players, m.state.page.kind)
        val rows = m.state.page.rows.map { assertIs<MenuRow.Option>(it) }
        assertEquals(listOf("Player 1", "Player 2", "Player 3"), rows.map { it.label })
        assertEquals(listOf("8BitDo Pro 2", "DualSense (disconnected)", "Empty"), rows.map { it.valueLabel })
        assertEquals(InGameMenuModel.PLAYERS_FOOTER, m.state.page.footer)
    }

    @Test fun left_right_on_a_player_row_cycles_empty_and_every_pad() {
        val m = model()
        m.setPlayers(listOf(p1, PlayerSlotRow(null, "", connected = false)), listOf(padA, padB, padC))
        m.openPlayers()
        // Player 1 holds k1; candidates are [Empty, k1, k2, k3]: a benched pad too (the host puts it back in play).
        assertEquals(MenuEffect.SetPlayer(0, "k2"), m.press(NxButton.RIGHT))
        assertEquals(MenuEffect.SetPlayer(0, null), m.press(NxButton.LEFT))     // Empty: the host benches k1
        m.press(NxButton.DOWN)                                                   // Player 2, Empty
        assertEquals(MenuEffect.SetPlayer(1, "k3"), m.press(NxButton.LEFT))     // wraps to the last candidate
    }

    @Test fun a_held_slot_cycles_from_empty() {
        val m = model()
        m.setPlayers(listOf(PlayerSlotRow("k9", "DualSense", connected = false), PlayerSlotRow(null, "", connected = false)), listOf(padA))
        m.openPlayers()
        assertEquals(MenuEffect.SetPlayer(0, "k1"), m.press(NxButton.RIGHT))
    }

    @Test fun set_players_refreshes_the_open_page_keeping_the_row() {
        val m = model()
        m.setPlayers(listOf(p1, PlayerSlotRow(null, "", connected = false)), listOf(padA, padB))
        m.openPlayers()
        m.press(NxButton.DOWN)                                                   // Player 2
        m.setPlayers(listOf(p1, PlayerSlotRow("k2", "Xbox Wireless Controller", connected = true)), listOf(padA, padB))
        assertEquals(1, m.state.page.selected)
        assertEquals("Xbox Wireless Controller", assertIs<MenuRow.Option>(m.state.page.rows[1]).valueLabel)
    }

    @Test fun player_rows_carry_no_info_so_the_footer_shows() {
        val m = model()
        m.setPlayers(listOf(p1, PlayerSlotRow(null, "", connected = false)), listOf(padA, padB))
        m.openPlayers()
        m.setPlayers(listOf(p1, PlayerSlotRow("k2", "Xbox Wireless Controller", connected = true)), listOf(padA, padB))
        val rows = m.state.page.rows.map { assertIs<MenuRow.Option>(it) }
        assertEquals(listOf("", ""), rows.take(2).map { it.info })              // Player rows: the footer shows instead
        assertTrue(rows.drop(2).all { it.info.isNotBlank() })                   // pad rows keep their info
        assertEquals(InGameMenuModel.PLAYERS_FOOTER, m.state.page.footer)
        // Presses while the menu is open never claim a slot, so the hint says to close it first.
        assertEquals("Close the menu, then press a button\non a new controller to join.", m.state.page.footer)
    }

    @Test fun netplay_mode_root_is_leave_prompt() {
        val m = model()
        m.setPlayers(listOf(PlayerSlotRow("a", "Pad A", true), PlayerSlotRow(null, "", false)), emptyList())
        m.netplay = true
        m.refresh()
        assertEquals("Leave netplay?", m.state.page.title)
        assertEquals(listOf("Continue", "Leave"), m.state.page.rows.map { it.label })
        assertEquals("Leaving ends the netplay session.", m.state.page.footer)
        assertEquals(listOf(GamePanelTab.SHORTCUTS), m.panelState().tabs)   // no Players tab in netplay, as no Players row before
        m.press(NxButton.DOWN)
        assertEquals(MenuEffect.LeaveNetplay, m.press(NxButton.A))
        m.press(NxButton.UP)
        assertEquals(MenuEffect.Close, m.press(NxButton.A))
        assertEquals(MenuEffect.Close, m.press(NxButton.B))
    }

    @Test fun netplay_mode_after_the_session_ended_is_continue_quit() {
        val m = model()
        m.netplay = true
        m.refresh()
        m.press(NxButton.DOWN)   // on Leave
        m.netplayEnded = true
        m.refresh()
        assertEquals("Tetris", m.state.page.title)
        assertEquals(listOf("Continue", "Quit"), m.state.page.rows.map { it.label })
        assertEquals("", m.state.page.footer)
        assertEquals(0, m.state.page.selected)   // the Leave row is gone: the selection falls back to Continue
        assertEquals(MenuEffect.Close, m.press(NxButton.A))
        m.press(NxButton.DOWN)
        // No save states after netplay: one way to quit, A or a second tap, and no X.
        assertEquals(listOf(Hint(NxButton.A, "Quit"), Hint(NxButton.B, "Resume")), hintsFor(m.state.page, m.state.page.rows[1]))
        assertTrue(inGameRowTakesTwoTaps(m.state.page.rows[1]))
        assertNull(m.press(NxButton.X))
        assertEquals(MenuEffect.QuitWithoutResume, m.press(NxButton.A))
        assertEquals(MenuEffect.QuitWithoutResume, m.activate(1))
    }

    @Test fun netplay_root_has_no_panel_beside_the_list() {
        val m = model()
        m.setAchievements(listOf(ach(1, RaAchievementState.UNLOCKED)), 1 to 1, RaSortOrder.DEFAULT, emptySet(), BADGES)
        assertTrue(m.panelBeside)
        m.netplay = true
        m.refresh()
        assertFalse(m.panelBeside)
        val tab = m.state.panelTab
        assertNull(m.press(NxButton.RIGHT))                  // on Continue: no panel to enter
        assertNull(m.state.panelFocus)
        m.press(NxButton.R1); m.press(NxButton.L1)
        assertEquals(tab, m.state.panelTab)                  // no tabs to switch
        // The UI passes panel = panelBeside: no ◄► Panel hint.
        val hints = hintsFor(m.state.page, m.state.page.rows[0], panelFocus = m.state.panelFocus, panelTab = m.state.panelTab, panel = m.panelBeside)
        assertTrue(hints.none { it.button == NxButton.RIGHT })
        // Once the session ended (Continue / Quit): still no panel.
        m.netplayEnded = true
        m.refresh()
        assertFalse(m.panelBeside)
        assertNull(m.press(NxButton.RIGHT))
        assertNull(m.state.panelFocus)
    }

    @Test fun an_ended_lockstep_session_stays_continue_quit() {
        val m = model()
        m.netplay = true
        m.refresh()
        m.endNetplay(local = false)
        assertEquals(listOf("Continue", "Quit"), m.state.page.rows.map { it.label })
        assertFalse(m.panelBeside)
        m.press(NxButton.DOWN)
        assertEquals(MenuEffect.QuitWithoutResume, m.press(NxButton.A))   // no states: no auto state
    }

    @Test fun an_ended_link_session_is_the_normal_root() {
        val m = model().also { it.cheatsAvailable = false }   // as in a session (no cheats)
        m.setPlayers(listOf(p1), listOf(padA))
        m.setAchievements(listOf(ach(1, RaAchievementState.UNLOCKED)), 1 to 1, RaSortOrder.DEFAULT, emptySet(), BADGES)
        m.netplay = true
        m.refresh()
        m.press(NxButton.DOWN)   // on Leave
        m.cheatsAvailable = true   // the host: the local game's cheats
        m.endNetplay(local = true)
        assertFalse(m.netplay)
        assertFalse(m.netplayEnded)
        assertEquals("Tetris", m.state.page.title)
        assertEquals(listOf("Continue", "Save", "Load", "Options", "Quit"), m.state.page.rows.map { it.label })
        assertEquals(0, m.state.page.selected)   // the Leave row is gone: back to Continue
        assertTrue(m.panelBeside)
        assertEquals(listOf(GamePanelTab.SHORTCUTS, GamePanelTab.ACHIEVEMENTS, GamePanelTab.CHEATS), m.panelState().tabs)
        // Quit as a normal launch's: the resume state with it.
        m.press(NxButton.DOWN); m.press(NxButton.DOWN); m.press(NxButton.DOWN); m.press(NxButton.DOWN)
        assertEquals("Quit", m.state.page.rows[m.state.page.selected].label)
        assertEquals(MenuEffect.Quit, m.press(NxButton.A))
    }

    @Test fun an_ended_link_session_offers_options_and_cheats() {
        val m = model().also { it.cheatsAvailable = false }
        m.netplay = true
        m.refresh()
        m.cheatsAvailable = true
        m.endNetplay(local = true)
        assertTrue(GamePanelTab.CHEATS in m.panelState().tabs)
        m.press(NxButton.DOWN); m.press(NxButton.DOWN); m.press(NxButton.DOWN)   // Options
        m.press(NxButton.A)
        assertEquals(PageKind.Options, m.state.page.kind)
    }

    @Test fun a_link_session_with_achievements_shows_them_only_once_it_ends() {
        val m = model().also { it.cheatsAvailable = false }
        m.setAchievements(listOf(ach(1, RaAchievementState.UNLOCKED)), 1 to 1, RaSortOrder.DEFAULT, emptySet(), BADGES)
        m.netplay = true
        m.netplayLink = true
        m.refresh()
        // Live: still Continue / Leave, no panel beside (the second screen's panel is not the menu's).
        assertEquals(listOf("Continue", "Leave"), m.state.page.rows.map { it.label })
        assertFalse(m.panelBeside)
        m.endNetplay(local = true)
        assertTrue(m.panelBeside)
        assertEquals(listOf(GamePanelTab.SHORTCUTS, GamePanelTab.ACHIEVEMENTS), m.panelState().tabs)
    }

    @Test fun ending_netplay_outside_a_session_changes_nothing() {
        val m = model()
        val before = m.state.page.rows.map { it.label }
        m.endNetplay(local = false)
        assertFalse(m.netplayEnded)
        assertEquals(before, m.state.page.rows.map { it.label })
    }

    @Test fun a_link_session_root_says_leaving_keeps_you_playing() {
        val m = model()
        m.netplay = true
        m.netplayLink = true
        m.refresh()
        assertEquals("Leave netplay?", m.state.page.title)
        assertEquals(listOf("Continue", "Leave"), m.state.page.rows.map { it.label })
        assertEquals("Leaving ends the link. You keep playing on your own save.", m.state.page.footer)
    }

    @Test fun a_lockstep_session_root_says_leaving_ends_the_session() {
        val m = model()
        m.netplay = true
        m.refresh()
        assertEquals("Leaving ends the netplay session.", m.state.page.footer)
        m.press(NxButton.DOWN)
        assertEquals(MenuEffect.LeaveNetplay, m.press(NxButton.A))   // the host leaves and quits
        m.endNetplay(local = false)   // an ended lockstep game stays Continue / Quit
        assertEquals(listOf("Continue", "Quit"), m.state.page.rows.map { it.label })
    }

    @Test fun leave_in_a_link_session_plays_on_with_the_normal_root() {
        val m = plain(InGameMenuModel("Tetris", slotCount = 8, initialSlot = 0)).also { it.cheatsAvailable = false }
        m.netplay = true
        m.netplayLink = true
        m.refresh()
        m.press(NxButton.DOWN)
        assertEquals(MenuEffect.LeaveNetplay, m.press(NxButton.A))
        // The host ends the link: cheats, the last-used slot, then the local root; the menu stays open.
        m.cheatsAvailable = true
        m.showSlot(4)
        m.endNetplay(local = true)
        assertEquals(PageKind.Root, m.state.page.kind)
        assertEquals(1, m.state.stack.size)
        assertEquals("Tetris", m.state.page.title)
        assertEquals(listOf("Continue", "Save", "Load", "Options", "Quit"), m.state.page.rows.map { it.label })
        assertEquals("", m.state.page.footer)
        assertTrue(m.panelBeside)
        assertEquals(4, m.state.slotIndex)
        m.press(NxButton.DOWN)   // Save: the last-used slot
        assertEquals(MenuEffect.SaveSlot(5), m.press(NxButton.A))
    }

    @Test fun show_slot_stays_in_range() {
        val m = model()
        m.showSlot(20)
        assertEquals(7, m.state.slotIndex)
        m.showSlot(-1)
        assertEquals(0, m.state.slotIndex)
    }

    @Test fun panel_header_and_a_new_console_tag_bump_the_panel_version() {
        val m = model()
        val v0 = m.state.panelVersion
        m.setPanelHeader(GamePanelHeader("Tetris", null, plainTag, 0L, null, "12:01", 80))
        assertTrue(m.state.panelVersion > v0)
        assertEquals("12:01", m.panelState().header.clockText)
        val v1 = m.state.panelVersion
        m.setFrontend(FrontendOptions.defaults(plainTag), emptyList(), plainLocked, plainTag)   // same tag: the panel is unchanged
        assertEquals(v1, m.state.panelVersion)
        m.setFrontend(FrontendOptions.defaults("PS"), emptyList(), plainLocked, "PS")
        assertTrue(m.state.panelVersion > v1)
    }

    // ------------------------------------------------------------------ touch options rows: choices, controls, popups

    private fun InGameMenuModel.rowIndex(label: String) = state.page.rows.indexOfFirst { it.label == label }.also { assertTrue(it >= 0, label) }

    @Test fun option_control_kind_follows_the_values() {
        assertEquals(NxSettingControl.VALUE, nxSettingControl(emptyList()))
        assertEquals(NxSettingControl.VALUE, nxSettingControl(listOf("None")))
        for (pair in listOf(listOf("Off", "On"), listOf("enabled", "disabled"), listOf("True", "False"), listOf("no", "yes"))) {
            assertEquals(NxSettingControl.SWITCH, nxSettingControl(pair), pair.toString())
        }
        assertEquals(1, nxSwitchOnIndex(listOf("Off", "On")))
        assertEquals(0, nxSwitchOnIndex(listOf("enabled", "disabled")))
        assertEquals(-1, nxSwitchOnIndex(listOf("On", "Auto")))
        assertEquals(-1, nxSwitchOnIndex(listOf("Off", "On", "Auto")))
        // Two to four short values: segments (two that are not on/off too).
        assertEquals(NxSettingControl.SEGMENTED, nxSettingControl(listOf("On", "Auto")))
        assertEquals(NxSettingControl.SEGMENTED, nxSettingControl(listOf("NEAREST", "LINEAR")))
        assertEquals(NxSettingControl.SEGMENTED, nxSettingControl(listOf("Native", "Aspect", "Fullscreen")))
        assertEquals(NxSettingControl.SEGMENTED, nxSettingControl(listOf("100%", "60%", "40%", "25%")))
        assertEquals(NxSettingControl.SEGMENTED, nxSettingControl(listOf("Auto", "OpenGL ES", "Vulkan")))
        // More values, a long one, or too long together: the picker.
        assertEquals(NxSettingControl.PICKER, nxSettingControl(listOf("1", "2", "3", "4", "5")))
        assertEquals(NxSettingControl.PICKER, nxSettingControl(listOf("Auto", "Sideways Wiimote")))
        assertEquals(NxSettingControl.PICKER, nxSettingControl(listOf("Top screen", "Bottom screen")))
        assertEquals(NxSettingControl.PICKER, nxSettingControl(listOf("Abcdefghij", "Abcdefghij", "Abcde")))
    }

    @Test fun frontend_rows_carry_their_value_labels_and_index() {
        val m = model().openFrontend()
        val scaling = m.optionRow(m.rowIndex("Screen Scaling"))
        assertEquals(listOf("Native", "Aspect", "Fullscreen"), scaling.choices)
        assertEquals(1, scaling.index)
        val overlay = m.optionRow(m.rowIndex("Overlay"))
        assertEquals(listOf("None", "Aspect.png", "Native.png"), overlay.choices)
        assertEquals(0, overlay.index)
        val opacity = m.optionRow(m.rowIndex("Pad Opacity (landscape)"))
        assertEquals(listOf("100%", "60%", "40%", "25%"), opacity.choices)
        assertEquals(2, opacity.index)
        assertEquals(129, m.optionRow(m.rowIndex("Offset screen X")).choices.size)
        assertEquals(NxSettingControl.PICKER, m.optionRow(m.rowIndex("Offset screen X")).control)
    }

    @Test fun core_option_rows_offer_their_visible_values() {
        val m = model()
        m.select(3); m.activate(3); m.activate(3)
        val hiddenValue = CoreOption("q", "hi", label = "Quality", values = listOf("lo", "hi", "max"), valueLabels = listOf("Low", "High", "Max"), hiddenValues = setOf("hi"))
        m.showCoreOptions(listOf(speed, hiddenValue), emptySet())
        assertEquals(listOf("1x", "2x"), m.optionRow(0).choices)
        assertEquals(0, m.optionRow(0).index)
        assertEquals(listOf("Low", "Max"), m.optionRow(1).choices)   // the hidden value is not offered
        assertEquals(-1, m.optionRow(1).index)                       // and, current, marks nothing
        assertEquals(MenuEffect.SetOption("q", "max"), m.setChoice(1, 1))
        assertEquals(1, m.optionRow(1).index)
    }

    @Test fun shader_rows_carry_presets_and_parameter_steps() {
        val m = shadersModel()
        val preset = m.optionRow(0)
        assertEquals(listOf("None", "crt/crt-pi.glslp", "nx/line.glslp"), preset.choices)
        assertEquals(1, preset.index)
        assertEquals(MenuEffect.SetShader("nx_shader", "nx/line.glslp"), m.setChoice(0, 2))
        assertEquals(2, m.optionRow(0).index)
        m.select(1); m.press(NxButton.A)                     // Shader Parameters
        val scanRow = m.optionRow(0)
        assertEquals(listOf("0.00", "0.50", "1.00"), scanRow.choices)
        assertEquals(1, scanRow.index)
        assertEquals(MenuEffect.SetShader("nx_shader_param_SCAN", "1.00"), m.setChoice(0, 2))
    }

    @Test fun player_and_disc_rows_carry_their_choices() {
        val m = model()
        m.setPlayers(listOf(p1, PlayerSlotRow(null, "", connected = false)), listOf(padA, padB))
        m.openPlayers()
        assertEquals(PageKind.Players, m.state.page.kind)
        assertEquals(listOf("Empty", "8BitDo Pro 2", "Xbox Wireless Controller"), m.optionRow(0).choices)
        assertEquals(1, m.optionRow(0).index)
        assertEquals(0, m.optionRow(1).index)
        assertEquals(MenuEffect.SetPlayer(1, "k2"), m.setChoice(1, 2))
        assertEquals(MenuEffect.SetPlayer(0, null), m.setChoice(0, 0))
        assertNull(m.setChoice(0, 1))                        // already set
        assertNull(m.setChoice(0, 9))                        // out of range

        val d = model()
        d.setDiscs(current = 0, count = 3)
        val disc = d.state.page.rows[1] as MenuRow.Option
        assertEquals(listOf("Disc 1", "Disc 2", "Disc 3"), disc.choices)
        assertEquals(0, disc.index)
        assertEquals(MenuEffect.ChangeDisc(2), d.setChoice(1, 2))
        assertEquals("3/3", (d.state.page.rows[1] as MenuRow.Option).valueLabel)
    }

    @Test fun set_choice_matches_the_cycle_effect_and_selects_the_row() {
        val m = model().openFrontend()
        val scaling = m.rowIndex("Screen Scaling")
        m.select(scaling + 1)
        assertEquals(MenuEffect.SetFrontend(FrontendOptions.SCALING, "Fullscreen"), m.setChoice(scaling, 2))
        assertEquals(scaling, m.state.page.selected)
        assertEquals("Fullscreen", m.optionRow(scaling).valueLabel)
        assertNull(m.setChoice(scaling, 2))                  // already that value
        assertNull(m.setChoice(scaling, -1))
        assertEquals(MenuEffect.SetFrontend(FrontendOptions.SCALING, "Native"), m.press(NxButton.RIGHT))   // the cycle still works
    }

    @Test fun a_opens_the_picker_and_the_dpad_drives_it() {
        val m = model().openConsoleSettings()
        val layout = m.rowIndex("Controller Layout")
        m.select(layout)
        assertEquals(NxSettingControl.PICKER, m.optionRow(layout).control)
        assertNull(m.press(NxButton.A))
        val labels = PadOptions.layoutRow.valueLabels
        assertEquals(MenuPopup.Picker(layout, "Controller Layout", labels, 0, 0), m.state.popup)
        m.press(NxButton.DOWN)
        assertEquals(1, (m.state.popup as MenuPopup.Picker).cursor)
        m.press(NxButton.UP); m.press(NxButton.UP)           // wraps
        assertEquals(2, (m.state.popup as MenuPopup.Picker).cursor)
        assertNull(m.press(NxButton.LEFT))                   // LEFT/RIGHT do nothing in the picker
        assertEquals(PageKind.ConsoleSettings, m.state.page.kind)
        assertEquals(MenuEffect.SetFrontend(PadOptions.LAYOUT, PadOptions.NINTENDO), m.press(NxButton.A))
        assertNull(m.state.popup)
        assertEquals(2, m.optionRow(layout).index)
        m.press(NxButton.A)
        assertEquals(2, (m.state.popup as MenuPopup.Picker).current)
        assertNull(m.press(NxButton.B))                      // B closes it, nothing set
        assertNull(m.state.popup)
        assertEquals(PageKind.ConsoleSettings, m.state.page.kind)
        m.press(NxButton.A)
        assertEquals(MenuEffect.Close, m.press(NxButton.MENU))
        assertNull(m.state.popup)
    }

    @Test fun touch_events_open_pick_and_close_popups() {
        val m = model().openFrontend()
        val overlay = m.rowIndex("Overlay")                  // visual: A adjusts, its "Value ›" opens the picker
        assertNull(m.optionEvent(OptionEvent.OpenPicker(overlay)))
        assertEquals(overlay, m.state.page.selected)
        assertEquals(MenuEffect.SetFrontend(FrontendOptions.OVERLAY, "Native.png"), m.optionEvent(OptionEvent.Pick(2)))
        assertNull(m.state.popup)
        assertNull(m.optionEvent(OptionEvent.Pick(1)))      // no picker open
        assertEquals(MenuEffect.SetFrontend(FrontendOptions.SCALING, "Native"), m.optionEvent(OptionEvent.Choose(m.rowIndex("Screen Scaling"), 0)))
        val hud = m.rowIndex("Debug HUD")
        m.optionEvent(OptionEvent.Info(hud))
        assertEquals(MenuPopup.Info("Debug HUD", FrontendOptions.option(FrontendOptions.DEBUG_HUD)!!.info), m.state.popup)
        assertEquals(hud, m.state.page.selected)
        m.optionEvent(OptionEvent.ClosePopup)
        assertNull(m.state.popup)
        m.optionEvent(OptionEvent.OpenPicker(overlay))
        m.press(NxButton.B)
        assertNull(m.state.popup)
        assertEquals(PageKind.Frontend, m.state.page.kind)   // B closed the picker, not the page
    }

    @Test fun no_popup_for_a_row_without_info_or_choices_and_a_page_change_closes_one() {
        val m = model().openCoreOptions()
        m.optionEvent(OptionEvent.Info(1))                   // Speed: no info
        assertNull(m.state.popup)
        m.select(0)                                          // a submenu: no picker
        m.openPicker(0)
        assertNull(m.state.popup)
        m.showInfo(0)                                        // a submenu's own text
        assertEquals(MenuPopup.Info("Video", "Display settings."), m.state.popup)
        m.closePopup()
        m.select(1); m.openPicker(1)
        assertIs<MenuPopup.Picker>(m.state.popup)
        m.showMessage("Save Changes", "Saved.")              // a page pushed over it
        assertNull(m.state.popup)
    }

    @Test fun x_shows_a_core_categorys_text_behind_its_icon() {
        val m = model().openCoreOptions()
        m.select(0)                                          // Video: compact
        assertEquals("", m.state.page.rows[0].info)
        assertTrue(m.state.page.rows[0].hasMoreInfo)
        assertTrue(Hint(NxButton.X, "Info") in hintsFor(m.state.page, m.state.page.rows[0], m.state.selectedShowsInfo))
        m.press(NxButton.X)
        assertEquals(MenuPopup.Info("Video", "Display settings."), m.state.popup)
    }

    @Test fun option_hints_name_what_a_does_per_control() {
        val switch = MenuRow.Option("HUD", "h", "Off", "x", choices = listOf("Off", "On"), index = 0)
        val picker = MenuRow.Option("Layout", "l", "Auto-detect", "x", choices = PadOptions.layoutRow.valueLabels, index = 0)
        val segments = MenuRow.Option("Size", "s", "Small", "x", choices = listOf("Small", "Large"), index = 0)
        val visual = segments.copy(visual = true)
        val page = MenuPage(PageKind.Frontend, "F", listOf(switch, picker, segments, visual))
        assertEquals(listOf(Hint(NxButton.LEFT, "Change"), Hint(NxButton.A, "Toggle"), Hint(NxButton.B, "Back")), hintsFor(page, switch))
        assertEquals(listOf(Hint(NxButton.LEFT, "Change"), Hint(NxButton.A, "Choose"), Hint(NxButton.X, "Info"), Hint(NxButton.B, "Back")), hintsFor(page, picker, infoTruncated = true))
        assertEquals(listOf(Hint(NxButton.LEFT, "Change"), Hint(NxButton.B, "Back")), hintsFor(page, segments))
        assertEquals(listOf(Hint(NxButton.LEFT, "Change"), Hint(NxButton.A, "Adjust"), Hint(NxButton.B, "Back")), hintsFor(page, visual))
        val sub = MenuRow.Submenu("Video", PageKind.Category("video"), "Display settings.")
        assertEquals(listOf(Hint(NxButton.A, "Open"), Hint(NxButton.X, "Info"), Hint(NxButton.B, "Back")), hintsFor(page, sub, infoTruncated = true))
    }

    @Test fun our_option_rows_show_a_short_line_and_keep_the_full_text() {
        val ours = ConsoleSettings.all + FrontendOptions.table
        for (o in ours) {
            assertTrue(o.brief.length <= 76 && '\n' !in o.brief, "${o.label}: ${o.brief}")
            assertTrue(o.info.isNotBlank() && o.info != o.brief, o.label)   // the full text kept for the info icon
        }
        // The full texts stay behind the info icon.
        assertTrue("Auto: stacked when both screens fit the width, else picture in picture." in FrontendOptions.option(NdsOptions.LAYOUT_PORTRAIT)!!.info)
        // In game: the short line on the row, the full text in its popup (the info icon / X without a truncation report).
        val m = model().openFrontend()
        val hud = m.rowIndex("Debug HUD")
        val o = FrontendOptions.option(FrontendOptions.DEBUG_HUD)!!
        assertEquals(o.brief, m.optionRow(hud).info)
        assertEquals(o.info, m.optionRow(hud).fullInfo)
        m.select(hud)
        assertTrue(m.state.selectedShowsInfo)
        assertTrue(Hint(NxButton.X, "Info") in hintsFor(m.state.page, m.optionRow(hud), m.state.selectedShowsInfo))
        m.press(NxButton.X)
        assertEquals(MenuPopup.Info("Debug HUD", o.info), m.state.popup)
        // The preset row and a core option are compact: their text behind the info icon only.
        val sh = shadersModel()
        assertEquals("", sh.optionRow(0).info)
        assertEquals(ShaderOptions.PRESET_INFO, sh.optionRow(0).detail)
        val core = model().openCoreOptions()
        core.press(NxButton.A)                               // Video category: Palette
        assertEquals("", core.optionRow(0).info)
        assertEquals("Colors.", core.optionRow(0).detail)
    }

    // ------------------------------------------------------------------ Options → Shortcuts

    private val r1 = ShortcutBinding(null, KeyCodes.BUTTON_R1)
    private val r2 = ShortcutBinding(null, KeyCodes.BUTTON_R2)
    private val menuR1 = ShortcutBinding(KeyCodes.BUTTON_MODE, KeyCodes.BUTTON_R1)

    /** The Options → Shortcuts page over [base] and [overrides], for [tag] (its hotkeys claimed). */
    private fun shortcuts(
        base: ShortcutSet = ShortcutSet.EMPTY,
        overrides: ShortcutOverrides = ShortcutOverrides.NONE,
        tag: String = plainTag,
        canRewind: Boolean = true,
        canMic: Boolean = false,
    ): InGameMenuModel {
        val m = model()
        m.setShortcuts(base, overrides, ConsoleHotkeys.claimed(tag), canRewind, canMic)
        m.select(3); m.activate(3)                                         // Options
        assertNull(m.activate(m.state.page.rows.indexOfFirst { it.label == "Shortcuts" }))
        assertEquals(PageKind.Shortcuts, m.state.page.kind)
        return m
    }

    private fun InGameMenuModel.row(label: String): MenuRow.Option = state.page.rows.first { it.label == label } as MenuRow.Option
    private fun InGameMenuModel.selectRow(label: String) = select(state.page.rows.indexOfFirst { it.label == label })

    @Test fun optionsOrder() {
        val m = model()
        m.setFrontend(FrontendOptions.defaults("GBA"), emptyList(), emptySet(), "GBA")
        m.select(3); m.activate(3)
        assertEquals(listOf("Console Settings", "Shortcuts", "Frontend", "Shaders", "Core Options", "Save Changes"), m.state.page.rows.map { it.label })
    }

    @Test fun inheritedShowsDefault() {
        val m = shortcuts(base = ShortcutSet(mapOf(ShortcutAction.TOGGLE_FF to r1), emptyMap()))
        assertEquals("R1 (default)", m.row("Toggle FF").valueLabel)
        assertEquals("None (default)", m.row("Hold FF").valueLabel)
        assertEquals("None (default)", m.row("Touch button 1").valueLabel)
        assertEquals("Changes here apply to this console or game. Save them with Save Changes.", m.state.page.footer)
    }

    @Test fun overrideShowsValue() {
        val m = shortcuts(
            base = ShortcutSet(mapOf(ShortcutAction.TOGGLE_FF to r1), emptyMap()),
            overrides = ShortcutOverrides(mapOf(ShortcutAction.TOGGLE_FF to menuR1, ShortcutAction.HOLD_FF to null), mapOf(TouchSlot.ONE to ShortcutAction.RESET)),
        )
        assertEquals("MENU+R1", m.row("Toggle FF").valueLabel)
        assertEquals("None", m.row("Hold FF").valueLabel)
        assertEquals("Reset Game", m.row("Touch button 1").valueLabel)
    }

    @Test fun captureSetsOverride() {
        val m = shortcuts()
        m.selectRow("Toggle FF")
        assertNull(m.press(NxButton.A))
        assertTrue(m.capturingShortcut)
        assertEquals("Press a button…", m.row("Toggle FF").valueLabel)
        assertTrue(m.takesKey(KeyCodes.BUTTON_R1, down = true))
        val expected = ShortcutOverrides(mapOf(ShortcutAction.TOGGLE_FF to r1))
        assertEquals(MenuEffect.ShortcutsChanged(expected), m.captureKey(KeyCodes.BUTTON_R1, down = true))
        assertFalse(m.capturingShortcut)
        assertEquals(expected, m.shortcutOverrides)
        assertEquals("R1", m.row("Toggle FF").valueLabel)
        // Its release is the capture's too, never the menu's.
        assertTrue(m.takesKey(KeyCodes.BUTTON_R1, down = false))
        assertNull(m.captureKey(KeyCodes.BUTTON_R1, down = false))
        assertFalse(m.takesKey(KeyCodes.BUTTON_R1, down = false))
    }

    @Test fun captureMovesADuplicateAndCancels() {
        val m = shortcuts(overrides = ShortcutOverrides(mapOf(ShortcutAction.HOLD_FF to r1)))
        m.selectRow("Toggle FF"); m.press(NxButton.A)
        assertEquals(
            MenuEffect.ShortcutsChanged(ShortcutOverrides(mapOf(ShortcutAction.HOLD_FF to null, ShortcutAction.TOGGLE_FF to r1))),
            m.captureKey(KeyCodes.BUTTON_R1, down = true),
        )
        assertEquals("None", m.row("Hold FF").valueLabel)
        m.selectRow("Reset Game"); m.press(NxButton.A)
        assertNull(m.captureKey(KeyCodes.BUTTON_B, down = true))           // B cancels
        assertFalse(m.capturingShortcut)
        assertEquals("None (default)", m.row("Reset Game").valueLabel)
    }

    @Test fun menuWhileCapturingNeitherClosesNorBinds() {
        val m = shortcuts()
        m.selectRow("Toggle FF"); m.press(NxButton.A)
        assertNull(m.captureKey(KeyCodes.BUTTON_MODE, down = true))
        assertNull(m.captureKey(KeyCodes.BUTTON_MODE, down = false))       // MENU alone binds nothing
        assertTrue(m.capturingShortcut)
        assertNull(m.press(NxButton.MENU))                                 // a touch MENU neither
        assertEquals(PageKind.Shortcuts, m.state.page.kind)
        assertNull(m.press(NxButton.B))                                    // a touch B cancels
        assertFalse(m.capturingShortcut)
    }

    @Test fun claimedRefused() {
        val m = shortcuts(tag = "NDS")
        m.selectRow("Toggle FF"); m.press(NxButton.A)
        assertNull(m.captureKey(KeyCodes.BUTTON_R2, down = true))
        assertEquals(ShortcutOverrides.NONE, m.shortcutOverrides)
        assertEquals(PageKind.Message, m.state.page.kind)
        assertTrue("R2 swaps the DS screens" in m.state.page.footer)
        m.press(NxButton.A)
        assertEquals("None (default)", m.row("Toggle FF").valueLabel)
    }

    @Test fun startRefusedWithMenuToo() {
        val m = shortcuts()
        m.selectRow("Toggle FF"); m.press(NxButton.A)
        assertNull(m.captureKey(KeyCodes.BUTTON_MODE, down = true))
        assertNull(m.captureKey(KeyCodes.BUTTON_START, down = true))
        assertEquals(ShortcutOverrides.NONE, m.shortcutOverrides)
        assertTrue("SELECT and START stay the game's" in m.state.page.footer)
    }

    @Test fun blockedRowUnavailable() {
        val m = shortcuts(base = ShortcutSet(mapOf(ShortcutAction.TOGGLE_FF to r2), emptyMap()), tag = "NDS")
        val row = m.row("Toggle FF")
        assertEquals("Unavailable", row.valueLabel)
        assertEquals("R2 swaps the DS screens", row.info)
    }

    @Test fun xClearsToNoneHere() {
        val m = shortcuts(base = ShortcutSet(mapOf(ShortcutAction.TOGGLE_FF to r1), emptyMap()))
        m.selectRow("Toggle FF")
        assertEquals(MenuEffect.ShortcutsChanged(ShortcutOverrides(mapOf(ShortcutAction.TOGGLE_FF to null))), m.press(NxButton.X))
        assertEquals("None", m.row("Toggle FF").valueLabel)
    }

    @Test fun useDefaultDropsOverride() {
        val m = shortcuts(
            base = ShortcutSet(mapOf(ShortcutAction.TOGGLE_FF to r1), emptyMap()),
            overrides = ShortcutOverrides(mapOf(ShortcutAction.TOGGLE_FF to menuR1)),
        )
        m.selectRow("Toggle FF")
        assertEquals(MenuEffect.ShortcutsChanged(ShortcutOverrides.NONE), m.press(NxButton.Y))
        assertEquals("R1 (default)", m.row("Toggle FF").valueLabel)
        assertNull(m.press(NxButton.Y))                                    // already inherited
    }

    @Test fun touchRowsCycleAndUseDefault() {
        val m = shortcuts(canRewind = false)
        m.selectRow("Touch button 1")
        assertEquals(listOf("None", "Toggle FF", "Hold FF", "Reset Game"), m.row("Touch button 1").choices)
        assertEquals(MenuEffect.ShortcutsChanged(ShortcutOverrides(touch = mapOf(TouchSlot.ONE to ShortcutAction.TOGGLE_FF))), m.press(NxButton.RIGHT))
        assertEquals("Toggle FF", m.row("Touch button 1").valueLabel)
        assertEquals(MenuEffect.ShortcutsChanged(ShortcutOverrides.NONE), m.press(NxButton.Y))
        assertEquals("None (default)", m.row("Touch button 1").valueLabel)
    }

    @Test fun noRewindRowsWithoutRewind() {
        assertEquals(
            listOf("Toggle FF", "Hold FF", "Reset Game", "Touch button 1", "Touch button 2"),
            shortcuts(canRewind = false).state.page.rows.map { it.label },
        )
        assertEquals(
            listOf("Toggle FF", "Hold FF", "Toggle Rewind", "Hold Rewind", "Reset Game", "Touch button 1", "Touch button 2"),
            shortcuts(canRewind = true).state.page.rows.map { it.label },
        )
    }

    @Test fun micRowOnlyOnMicConsoles() {
        assertTrue("Toggle mic" in shortcuts(canMic = true).state.page.rows.map { it.label })
        assertFalse("Toggle mic" in shortcuts(canMic = false).state.page.rows.map { it.label })
        val m = shortcuts(canMic = true)
        assertFalse("Toggle mic" in m.row("Touch button 1").choices)
    }

    @Test fun restoreDefaultsClears() {
        val m = model()
        m.setShortcuts(ShortcutSet.EMPTY, ShortcutOverrides(mapOf(ShortcutAction.TOGGLE_FF to r1)), ConsoleHotkeys.claimed(plainTag), true)
        m.select(3); m.activate(3)
        m.activate(m.state.page.rows.indexOfFirst { it.label == "Save Changes" })
        assertEquals(MenuEffect.RestoreDefaults, m.activate(m.state.page.rows.indexOfFirst { it.label == "Restore defaults" }))
        assertEquals(ShortcutOverrides.NONE, m.shortcutOverrides)
    }

    @Test fun shortcutsHints() {
        val m = shortcuts()
        m.selectRow("Toggle FF")
        val bind = listOf(Hint(NxButton.A, "Bind"), Hint(NxButton.X, "None"), Hint(NxButton.Y, "Default"), Hint(NxButton.B, "Back"))
        assertEquals(bind, hintsFor(m.state.page, m.row("Toggle FF")))
        m.press(NxButton.A)
        assertEquals(listOf(Hint(NxButton.B, "Cancel")), hintsFor(m.state.page, m.row("Toggle FF")))
        assertEquals(Hint(NxButton.LEFT, "Change"), hintsFor(m.state.page, m.row("Touch button 1")).first())
    }
}
