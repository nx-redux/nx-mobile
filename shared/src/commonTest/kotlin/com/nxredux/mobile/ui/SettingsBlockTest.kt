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
}
