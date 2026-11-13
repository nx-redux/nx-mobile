package com.nxredux.mobile.options

import kotlin.test.Test
import kotlin.test.assertEquals

class OptionGroupTest {
    private fun labelsIn(group: OptionGroup) =
        (ConsoleSettings.all + FrontendOptions.table).filter { OptionGroup.of(it.key) == group }.map { it.label }.toSet()

    @Test fun every_app_setting_is_in_the_group_it_is_for() {
        assertEquals(
            setOf(
                "Controller Layout", "Controller Type", "Pad Opacity (landscape)", "New 3DS Controls",
                "Wii Controller", "Motion Controls", "Pointer Width", "Pointer Height", "Pointer Offset",
            ),
            labelsIn(OptionGroup.CONTROLS),
        )
        assertEquals(setOf("Max FF Speed", "Fast Forward Audio", "Rewind", "Rewind Audio", "Frame Skip", "PS2 BIOS"), labelsIn(OptionGroup.GAMEPLAY))
        assertEquals(setOf("Audio Resampling Quality", "Audio Buffer", "Fill Audio Gaps"), labelsIn(OptionGroup.AUDIO))
        assertEquals(setOf("Local Wireless"), labelsIn(OptionGroup.MULTIPLAYER))
        // The rest is the picture: a new row lands here unless it is put in a group above.
        val display = labelsIn(OptionGroup.DISPLAY)
        for (label in listOf("Screen Scaling", "Overlay", "Debug HUD", "Wii Screen", "Graphics API", "Bottom screen", "3D Resolution", "Layout (portrait)")) {
            assertEquals(true, label in display, label)
        }
    }

    @Test fun grouped_keeps_group_order_and_row_order_and_drops_empty_groups() {
        val keys = listOf(AudioOptions.RESAMPLING, FrontendOptions.SCALING, SpeedOptions.REWIND, FrontendOptions.DEBUG_HUD)
        assertEquals(
            listOf(
                OptionGroup.DISPLAY to listOf(FrontendOptions.SCALING, FrontendOptions.DEBUG_HUD),
                OptionGroup.GAMEPLAY to listOf(SpeedOptions.REWIND),
                OptionGroup.AUDIO to listOf(AudioOptions.RESAMPLING),
            ),
            OptionGroup.grouped(keys) { it },
        )
    }

    @Test fun local_wireless_is_multiplayer_on_each_console_that_has_it() {
        for (key in listOf(NdsOptions.LOCAL_WIRELESS, N3dsOptions.LOCAL_WIRELESS, PspOptions.LOCAL_WIRELESS)) {
            assertEquals(OptionGroup.MULTIPLAYER, OptionGroup.of(key), key)
        }
    }

    @Test fun multiplayer_comes_between_gameplay_and_audio() {
        val keys = listOf(AudioOptions.RESAMPLING, PspOptions.LOCAL_WIRELESS, SpeedOptions.REWIND)
        assertEquals(
            listOf(OptionGroup.GAMEPLAY, OptionGroup.MULTIPLAYER, OptionGroup.AUDIO),
            OptionGroup.grouped(keys) { it }.map { it.first },
        )
    }

    @Test fun a_console_without_local_wireless_shows_no_multiplayer_group() {
        for (tag in listOf("GBA", "SFC", "PS")) {
            val groups = OptionGroup.grouped(ConsoleSettings.tableFor(tag)) { it.key }.map { it.first }
            assertEquals(false, OptionGroup.MULTIPLAYER in groups, tag)
        }
        for (tag in listOf("NDS", "3DS", "PSP")) {
            val groups = OptionGroup.grouped(ConsoleSettings.tableFor(tag)) { it.key }.map { it.first }
            assertEquals(true, OptionGroup.MULTIPLAYER in groups, tag)
        }
    }
}
