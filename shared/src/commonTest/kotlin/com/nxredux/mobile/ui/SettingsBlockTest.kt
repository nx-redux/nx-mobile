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

    @Test fun sectionSize() {
        assertEquals(24.sp, settingsSectionSize(SettingsSizeClass.PHONE_UPRIGHT))
        assertEquals(26.sp, settingsSectionSize(SettingsSizeClass.PHONE_LANDSCAPE))
        assertEquals(26.sp, settingsSectionSize(SettingsSizeClass.TABLET))
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

    @Test fun stageLeftEdge() {
        assertEquals(SETTINGS_ROWS_LEFT, settingsStageLeft(blockPage = true))
        assertEquals(SETTINGS_SPLIT_LEFT, settingsStageLeft(blockPage = false))
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
