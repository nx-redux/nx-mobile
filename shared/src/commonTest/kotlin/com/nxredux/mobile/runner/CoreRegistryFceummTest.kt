package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals

/** fceumm (NES, FDS): X / Y are its turbo A / B, on for player 1 by default (the GB-style skins' TA / TB caps). */
class CoreRegistryFceummTest {
    @Test
    fun fceummTurboOnByDefault() {
        for (tag in listOf("FDS", "FC")) assertEquals("Player 1", CoreRegistry.forTag(tag)!!.defaultOptions["fceumm_turbo_enable"], tag)
    }
}
