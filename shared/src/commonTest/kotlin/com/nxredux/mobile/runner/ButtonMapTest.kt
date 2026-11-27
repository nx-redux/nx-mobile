package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ButtonMapTest {
    @Test
    fun defaultIsIdentity() {
        for (k in MapKey.entries) assertEquals(k, ButtonMap.DEFAULT.jobFor(k))
        assertEquals(108, ButtonMap.DEFAULT.keyCodeFor(108))
    }

    @Test
    fun bindSwaps() {
        val m = ButtonMap.DEFAULT.bind(job = MapKey.A, physical = MapKey.R2)
        assertEquals(MapKey.A, m.jobFor(MapKey.R2))
        assertEquals(MapKey.R2, m.jobFor(MapKey.A))
        assertEquals(MapKey.R2, m.keyFor(MapKey.A))
    }

    @Test
    fun bindToOwnRowIsNoop() {
        assertEquals(ButtonMap.DEFAULT, ButtonMap.DEFAULT.bind(MapKey.A, MapKey.A))
    }

    @Test
    fun chainedSwapsStayPermutation() {
        val m = ButtonMap.DEFAULT.bind(MapKey.A, MapKey.B).bind(MapKey.B, MapKey.X)
        assertEquals(MapKey.entries.size, MapKey.entries.map { m.jobFor(it) }.toSet().size)
        assertEquals(MapKey.B, m.keyFor(MapKey.A))
        assertEquals(MapKey.X, m.keyFor(MapKey.B))
    }

    @Test
    fun configRoundTrip() {
        val m = ButtonMap.DEFAULT.bind(MapKey.A, MapKey.B)
        assertEquals(listOf("nx_map_A" to "B", "nx_map_B" to "A"), ButtonMap.toConfig(m))
        assertEquals(m, ButtonMap.fromConfig(ButtonMap.toConfig(m).toMap(), "PS"))
    }

    @Test
    fun invalidConfigFallsBack() {
        assertEquals(ButtonMap.DEFAULT, ButtonMap.fromConfig(mapOf("nx_map_A" to "B"), "PS"))
        assertEquals(ButtonMap.DEFAULT, ButtonMap.fromConfig(mapOf("nx_map_A" to "START"), "PS"))
    }

    @Test
    fun migratesNintendoLayoutOnLetterConsoles() {
        for (tag in listOf("SFC", "DC")) {
            val m = ButtonMap.fromConfig(mapOf("nx_pad_layout" to "nintendo"), tag)
            assertEquals(MapKey.B, m.keyFor(MapKey.A))
            assertEquals(MapKey.A, m.keyFor(MapKey.B))
            assertEquals(MapKey.Y, m.keyFor(MapKey.X))
            assertEquals(MapKey.X, m.keyFor(MapKey.Y))
        }
        assertEquals(ButtonMap.DEFAULT, ButtonMap.fromConfig(mapOf("nx_pad_layout" to "xbox"), "SFC"))
        assertEquals(ButtonMap.DEFAULT, ButtonMap.fromConfig(mapOf("nx_pad_layout" to "nintendo"), "PS"))
        val own = ButtonMap.fromConfig(mapOf("nx_pad_layout" to "nintendo", "nx_map_L1" to "R1", "nx_map_R1" to "L1"), "SFC")
        assertEquals(MapKey.A, own.keyFor(MapKey.A))
        assertEquals(MapKey.R1, own.keyFor(MapKey.L1))
    }

    @Test
    fun droppedOnSaveDropsUnwrittenAndLegacy() {
        val drop = ButtonMap.droppedOnSave(ButtonMap.DEFAULT.bind(MapKey.A, MapKey.B))
        assertTrue(drop("nx_map_X"))
        assertTrue(drop("nx_pad_layout"))
        assertFalse(drop("nx_map_A"))
        assertFalse(drop("nx_bind_menu"))
    }
}
