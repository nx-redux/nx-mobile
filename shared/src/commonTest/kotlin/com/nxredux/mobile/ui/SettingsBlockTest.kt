package com.nxredux.mobile.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.ui.nx.FooterMode
import com.nxredux.mobile.ui.nx.NxSettingRow
import com.nxredux.mobile.ui.nx.NxFooterKeep
import com.nxredux.mobile.ui.nx.nxFooterKeep
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
        // The wide stage's frame adds the Layouts groups (their picture under the rows); the Android games picker stays whole.
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

    @Test fun pictureUnderRowsHeight() {
        // The Layouts picture under the rows: the capture's aspect kept inside the mat, at the rows' width.
        assertEquals((8f + 8f + 200f).dp, framedPictureHeight(416.dp, aspect = 2f, mat = 8.dp))
        assertEquals(0.dp, framedPictureHeight(0.dp, aspect = 2f, mat = 8.dp))
    }

    @Test fun layoutPictureWidthBySizeClass() {
        // A phone upright: the window inside the 16 dp gutters.
        assertEquals(412.dp - 32.dp, settingsLayoutPictureWidth(SettingsSizeClass.PHONE_UPRIGHT, 412.dp, sectionRight = 0.dp, gutter = 16.dp))
        // A phone on its side (the wide stage): from the section block's right edge + 24 dp to the right gutter,
        // wider than the 560 dp rows pane.
        val side = settingsLayoutPictureWidth(SettingsSizeClass.PHONE_LANDSCAPE, 915.dp, sectionRight = 180.dp, gutter = 24.dp)
        assertEquals(915.dp - 24.dp - 180.dp - 24.dp, side)
        assertTrue(side!! > settingsFrameWidth(915.dp, sectionRight = 180.dp, gutter = 24.dp))
        // A tablet-class window: unchanged, the rows' width.
        assertEquals(null, settingsLayoutPictureWidth(SettingsSizeClass.TABLET, 1200.dp, sectionRight = 250.dp, gutter = 48.dp))
        // Never below zero.
        assertEquals(0.dp, settingsLayoutPictureWidth(SettingsSizeClass.PHONE_UPRIGHT, 20.dp, sectionRight = 0.dp, gutter = 16.dp))
    }

    @Test fun padKeepsThePictureInView() {
        val rows = listOf(
            NxSettingRow("Group", section = true), NxSettingRow("A"), NxSettingRow("B"),
            NxSettingRow("", spacer = true),
        )
        // The last row that can be selected (B, index 2) with a footer that fits under it (and the fade edges): the footer.
        assertEquals(NxFooterKeep.FOOTER, nxFooterKeep(rows, 2, footer = true, footerPx = 400, rowPx = 100, edge = 60, viewportPx = 620))
        // Too tall to show whole with the row: the row at the top, the picture as far as fits.
        assertEquals(NxFooterKeep.ROW_AT_TOP, nxFooterKeep(rows, 2, footer = true, footerPx = 900, rowPx = 100, edge = 60, viewportPx = 620))
        assertEquals(NxFooterKeep.ROW_AT_TOP, nxFooterKeep(rows, 2, footer = true, footerPx = 401, rowPx = 100, edge = 60, viewportPx = 620))
        // Any other row, no footer, or nothing selected: the row as any list.
        assertEquals(NxFooterKeep.ROW, nxFooterKeep(rows, 1, footer = true, footerPx = 400, rowPx = 100, edge = 60, viewportPx = 620))
        assertEquals(NxFooterKeep.ROW, nxFooterKeep(rows, 2, footer = false, footerPx = 0, rowPx = 100, edge = 60, viewportPx = 620))
        assertEquals(NxFooterKeep.ROW, nxFooterKeep(rows, -1, footer = true, footerPx = 400, rowPx = 100, edge = 60, viewportPx = 620))
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
