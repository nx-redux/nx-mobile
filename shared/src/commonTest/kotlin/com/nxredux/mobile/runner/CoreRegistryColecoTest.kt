package com.nxredux.mobile.runner

import com.nxredux.mobile.options.CoreConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** ColecoVision on Gearcoleco: cartridges only, so its ADAM computer options stay locked at their defaults. */
class CoreRegistryColecoTest {
    private val coleco get() = CoreRegistry.forTag("COLECO")!!

    private val adam = mapOf(
        "gearcoleco_cartridge_hardware" to "ColecoVision",
        "gearcoleco_adam_disk_drive" to "Loaded media",
        "gearcoleco_adam_writable_media" to "Disabled",
        "gearcoleco_adam_computer_reset" to "Idle",
    )

    @Test fun adam_options_are_locked_at_their_defaults() {
        assertEquals(adam.keys, coleco.lockedOptions)
        assertEquals(adam, coleco.defaultOptions.filterKeys { it in adam })
    }

    @Test fun a_cfg_cannot_switch_cartridges_to_the_adam() {
        val game = CoreConfig.parse("gearcoleco_cartridge_hardware = ADAM\ngearcoleco_mapper = MegaCart\n")
        val r = CoreConfig.resolve(coleco.defaultOptions, null, game, forced = coleco.lockedOptions)
        assertEquals("ColecoVision", r.values["gearcoleco_cartridge_hardware"])
        assertEquals("MegaCart", r.values["gearcoleco_mapper"])
        assertTrue("gearcoleco_cartridge_hardware" in r.locked)
    }
}
