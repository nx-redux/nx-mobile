package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreRegistryRewindTest {
    private val noRewind = setOf("azahar", "flycast", "ppsspp", "prboom", "dolphin")

    @Test fun noRewindCores() {
        val found = CoreRegistry.all.filter { it.id in noRewind }
        assertEquals(noRewind, found.map { it.id }.toSet())
        found.forEach { assertFalse(it.rewind, "${it.id} cannot rewind") }
        // `all` keeps one descriptor per id, so check both Dolphin views directly.
        assertFalse(CoreRegistry.forTag("WII")!!.rewind)
        assertFalse(CoreRegistry.forTag("GC")!!.rewind)
    }

    @Test fun everyOtherCoreRewinds() {
        val others = CoreRegistry.all.filter { it.id !in noRewind }
        assertTrue(others.isNotEmpty())
        others.forEach { assertTrue(it.rewind, "${it.id} can rewind") }
    }
}
