package com.nxredux.mobile.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.ui.nx.FooterMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Settings section block's geometry: size classes, gutters, the section size, when Back is drawn. */
class SettingsBlockTest {
    @Test fun sizeClasses() {
        assertEquals(SettingsSizeClass.PHONE_UPRIGHT, settingsSizeClass(412f, 915f))
        assertEquals(SettingsSizeClass.PHONE_LANDSCAPE, settingsSizeClass(915f, 412f))
        assertEquals(SettingsSizeClass.TABLET, settingsSizeClass(673f, 841f))
        assertEquals(SettingsSizeClass.TABLET, settingsSizeClass(841f, 673f))
    }

    @Test fun gutters() {
        assertEquals(16.dp, settingsGutter(SettingsSizeClass.PHONE_UPRIGHT))
        assertEquals(24.dp, settingsGutter(SettingsSizeClass.PHONE_LANDSCAPE))
        assertEquals(48.dp, settingsGutter(SettingsSizeClass.TABLET))
    }

    // One size on every screen, a phone held upright too.
    @Test fun sectionSize() {
        assertEquals(26.sp, SETTINGS_SECTION_SIZE)
        assertEquals(24.sp, SETTINGS_BACK_SIZE)   // Back a step smaller
    }

    @Test fun backOnlyInTouchMode() {
        assertTrue(settingsBackShown(FooterMode.BUTTONS))
        assertFalse(settingsBackShown(FooterMode.HINTS))
    }

    @Test fun blockPages() {
        assertTrue(settingsBlockPage(Screen.SettingsMenu(settingsCategories())))
        assertTrue(settingsBlockPage(Screen.Settings(SettingsSection.LIBRARY, emptyList())))
        assertTrue(settingsBlockPage(Screen.Settings(SettingsSection.MAIN_MENU, emptyList())))   // Layouts itself
        assertTrue(settingsBlockPage(Screen.Settings(SettingsSection.SAVES, emptyList())))       // a sub-page
        assertTrue(settingsBlockPage(Screen.EmulatorList(emptyList())))
        // A Layouts group keeps its picture page; the Android games picker its grid.
        assertFalse(settingsBlockPage(Screen.Settings(SettingsSection.LAYOUT_HOME, emptyList())))
        assertFalse(settingsBlockPage(Screen.ToolApps(emptyList(), AppPick.GAMES)))
    }

    @Test fun wideBlockPages() {
        // The wide stage's frame adds the Layouts groups (picture above the line); the Android games picker stays whole.
        assertTrue(settingsWideBlockPage(Screen.Settings(SettingsSection.LAYOUT_HOME, emptyList())))
        assertTrue(settingsWideBlockPage(Screen.Settings(SettingsSection.LIBRARY, emptyList())))
        assertTrue(settingsWideBlockPage(Screen.SettingsMenu(settingsCategories())))
        assertFalse(settingsWideBlockPage(Screen.ToolApps(emptyList(), AppPick.GAMES)))
    }

    @Test fun frameWidthAndLeft() {
        // Room to spare: 560 dp, the right edge at the gutter.
        assertEquals(560.dp, settingsFrameWidth(1200.dp, sectionRight = 250.dp, gutter = 48.dp))
        assertEquals(1200.dp - 48.dp - 560.dp, settingsFrameLeft(1200.dp, sectionRight = 250.dp, gutter = 48.dp))
        // A narrower wide window: the frame gives way, 24 dp clear of the section block.
        assertEquals(800.dp - 48.dp - 250.dp - 24.dp, settingsFrameWidth(800.dp, sectionRight = 250.dp, gutter = 48.dp))
        assertEquals(250.dp + 24.dp, settingsFrameLeft(800.dp, sectionRight = 250.dp, gutter = 48.dp))
        // Too little room: the pane keeps its least width rather than vanishing (and never passes the gutter).
        assertEquals(SETTINGS_FRAME_MIN, settingsFrameWidth(400.dp, sectionRight = 250.dp, gutter = 24.dp))
        assertEquals(200.dp - 24.dp, settingsFrameWidth(200.dp, sectionRight = 250.dp, gutter = 24.dp))
    }

    @Test fun topLine() {
        assertEquals(320.dp, settingsTopLine(800.dp))
        assertEquals(0.dp, settingsTopLine(0.dp))
    }

    @Test fun pictureFit() {
        // Short room: a picture left of the mark's column, its top as high as the mark's top (16 dp).
        val narrow = settingsPictureWidth(560.dp, bottom = 200.dp, aspect = 2f, mat = 8.dp)
        assertEquals(((200f - 16f - 16f) * 2f + 16f).dp, narrow)
        assertTrue(narrow <= 560.dp - SETTINGS_MARK - 16.dp)
        // Room to reach the mark's column: across the pane, its top under the mark (80 dp).
        val wide = settingsPictureWidth(560.dp, bottom = 400.dp, aspect = 2f, mat = 8.dp)
        assertEquals(560.dp, wide)
        assertTrue(400.dp - ((wide - 16.dp) / 2f + 16.dp) >= SETTINGS_PICTURE_TOP)
        // Room for a picture past the mark's column only under the mark: held left of it instead, top still at 16 dp.
        assertEquals(560.dp - SETTINGS_MARK - 16.dp, settingsPictureWidth(560.dp, bottom = 300.dp, aspect = 2f, mat = 8.dp))
        // No room: none.
        assertEquals(0.dp, settingsPictureWidth(560.dp, bottom = 16.dp, aspect = 2f, mat = 8.dp))
    }

    @Test fun narrowBlockPages() {
        // The root is the section block alone; a page below it is the rows block, one level at a time.
        assertFalse(settingsNarrowBlock(Screen.SettingsMenu(settingsCategories())))
        assertTrue(settingsNarrowBlock(Screen.Settings(SettingsSection.LIBRARY, emptyList())))
        assertTrue(settingsNarrowBlock(Screen.Settings(SettingsSection.SAVES, emptyList())))
        assertTrue(settingsNarrowBlock(Screen.EmulatorList(emptyList())))
        // Pages that draw whole stay whole.
        assertFalse(settingsNarrowBlock(Screen.Settings(SettingsSection.LAYOUT_HOME, emptyList())))
        assertFalse(settingsNarrowBlock(Screen.ToolApps(emptyList(), AppPick.GAMES)))
    }

    @Test fun blockLine() {
        assertEquals("Settings / Library", settingsBlockLine("Settings", "Library"))
        assertEquals("Library", settingsBlockLine(null, "Library"))
        assertEquals("Settings", settingsBlockLine("Settings", null))
        assertEquals("", settingsBlockLine(null, ""))
    }
}
