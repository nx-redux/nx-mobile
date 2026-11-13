package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PadBandRuleTest {
    private fun keep(vararg names: String) = PadBandRule.keepPadBand(portrait = true, controllerNames = names.toList())

    @Test fun no_controller_keeps_the_band() {
        assertTrue(keep())
    }

    @Test fun a_non_clip_on_controller_drops_the_band_in_portrait() {
        assertFalse(keep("Xbox Wireless Controller"))
        assertFalse(keep("GameSir-T4 Pro"))
        assertFalse(keep("GameSir G7"))
    }

    @Test fun a_clip_on_controller_keeps_the_band() {
        assertTrue(keep("GameSir Pocket Taco"))
        assertTrue(keep("gamesir-pocket-taco"))
        assertTrue(keep("8BitDo FlipPad"))
    }

    @Test fun the_pocket_taco_hardware_name_keeps_the_band() {
        assertTrue(keep("GameSir-Pocket 1"))
    }

    @Test fun one_clip_on_among_several_keeps_the_band() {
        assertTrue(keep("Xbox Wireless Controller", "GameSir_Pocket_Taco"))
        assertTrue(keep("Xbox Wireless Controller", "GameSir-Pocket 1"))
    }

    @Test fun landscape_keeps_todays_path() {
        assertTrue(PadBandRule.keepPadBand(portrait = false, controllerNames = listOf("Xbox Wireless Controller")))
    }

    @Test fun is_clip_on_ignores_case_spaces_hyphens_and_underscores() {
        assertTrue(PadBandRule.isClipOn("GameSir-Pocket-Taco"))
        assertTrue(PadBandRule.isClipOn("8BITDO FLIP_PAD"))
        assertTrue(PadBandRule.isClipOn("gamesir_POCKET-1"))
        assertFalse(PadBandRule.isClipOn("GameSir-T4 Pro"))
    }

    // Only a dual-screen console has a controller portrait layout; any other just loses the band.
    @Test fun the_controller_layout_is_used_exactly_when_a_ds_band_drops() {
        val xbox = listOf("Xbox Wireless Controller")
        assertTrue(PadBandRule.controllerLayout("NDS", portrait = true, controllerNames = xbox))
        assertFalse(PadBandRule.controllerLayout("NDS", portrait = true, controllerNames = listOf("GameSir-Pocket 1")))
        assertFalse(PadBandRule.controllerLayout("NDS", portrait = true, controllerNames = emptyList()))
        assertFalse(PadBandRule.controllerLayout("NDS", portrait = false, controllerNames = xbox))
        assertFalse(PadBandRule.controllerLayout("GBA", portrait = true, controllerNames = xbox))
        assertTrue(PadBandRule.controllerLayout("3DS", portrait = true, controllerNames = xbox))   // the 3DS has one too
    }
}
