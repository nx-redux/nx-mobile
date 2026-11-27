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

    @Test
    fun analogTriggerConsolesKeepL2AndR2() {
        assertTrue(ButtonMap.analogTriggers("DC") && ButtonMap.analogTriggers("GC"))
        assertFalse(ButtonMap.analogTriggers("PS") || ButtonMap.analogTriggers("WII"))
        val stored = ButtonMap.toConfig(ButtonMap.DEFAULT.bind(MapKey.A, MapKey.L2).bind(MapKey.X, MapKey.Y)).toMap()
        for (tag in listOf("DC", "GC")) {
            val m = ButtonMap.fromConfig(stored, tag)
            assertEquals(MapKey.L2, m.keyFor(MapKey.L2))
            assertEquals(MapKey.R2, m.keyFor(MapKey.R2))
            assertEquals(MapKey.A, m.keyFor(MapKey.A))
            assertEquals(MapKey.Y, m.keyFor(MapKey.X))
        }
        assertEquals(MapKey.L2, ButtonMap.fromConfig(stored, "PS").keyFor(MapKey.A))
    }

    @Test
    fun keepingTriggersStaysPermutation() {
        // A three-key cycle through L2: A's key does L2's job, L2's key does B's, B's key does A's.
        val m = ButtonMap.DEFAULT.bind(MapKey.L2, MapKey.A).bind(MapKey.B, MapKey.L2).bind(MapKey.R2, MapKey.R1).keepingTriggers()
        assertEquals(MapKey.entries.size, MapKey.entries.map { m.jobFor(it) }.toSet().size)
        assertEquals(MapKey.L2, m.jobFor(MapKey.L2))
        assertEquals(MapKey.R2, m.jobFor(MapKey.R2))
        assertEquals(MapKey.R1, m.jobFor(MapKey.R1))
        assertEquals(ButtonMap.DEFAULT, ButtonMap.DEFAULT.keepingTriggers())
    }

    @Test
    fun bindsRefusesTriggersOnAnalogConsoles() {
        assertFalse(ButtonMap.binds("DC", MapKey.L2) || ButtonMap.binds("GC", MapKey.R2))
        assertTrue(ButtonMap.binds("DC", MapKey.A) && ButtonMap.binds("PS", MapKey.L2))
    }

    @Test
    fun hotkeyKeysAreLocked() {
        assertEquals(setOf(MapKey.L2, MapKey.R2, MapKey.L3, MapKey.R3), ButtonMap.locked("NDS"))
        assertEquals(setOf(MapKey.L3, MapKey.R3), ButtonMap.locked("3DS"))
        assertEquals(setOf(MapKey.L3, MapKey.R3), ButtonMap.locked("wii"))
        assertEquals(emptySet(), ButtonMap.locked("PS"))
        assertFalse(ButtonMap.binds("NDS", MapKey.R2))
        // A stored line moving a locked key is ignored: L2 keeps swapping the DS screens.
        val stored = ButtonMap.toConfig(ButtonMap.DEFAULT.bind(MapKey.A, MapKey.L2)).toMap()
        assertEquals(MapKey.L2, ButtonMap.fromConfig(stored, "NDS").keyFor(MapKey.L2))
        assertEquals(MapKey.L2, ButtonMap.fromConfig(stored, "SFC").keyFor(MapKey.A))
    }

    @Test
    fun gameLinesWinElseConsoles() {
        val console = mapOf("nx_map_L1" to "R1", "nx_map_R1" to "L1")
        val game = mapOf("nx_map_A" to "B", "nx_map_B" to "A", "opt" to "x")
        assertEquals(ButtonMap.fromConfig(game, "PS"), ButtonMap.resolve(game, console, "PS"))
        assertEquals(ButtonMap.fromConfig(console, "PS"), ButtonMap.resolve(mapOf("opt" to "x"), console, "PS"))
        assertEquals(ButtonMap.fromConfig(console, "PS"), ButtonMap.resolve(null, console, "PS"))
        assertEquals(ButtonMap.DEFAULT, ButtonMap.resolve(null, null, "PS"))
        assertEquals(ButtonMap.DEFAULT, ButtonMap.resolve(mapOf("opt" to "x"), null, "PS"))
    }

    @Test
    fun legacyLayoutMigratesFromTheFileThatSuppliesIt() {
        val legacy = mapOf("nx_pad_layout" to "nintendo")
        val swap = ButtonMap.DEFAULT.bind(MapKey.A, MapKey.B).bind(MapKey.X, MapKey.Y)
        assertEquals(swap, ButtonMap.resolve(legacy, null, "SFC"))
        assertEquals(swap, ButtonMap.resolve(mapOf("opt" to "x"), legacy, "SFC"))
        // The game's own lines win over its legacy key, and over the console's.
        val own = mapOf("nx_pad_layout" to "nintendo", "nx_map_L1" to "R1", "nx_map_R1" to "L1")
        assertEquals(MapKey.A, ButtonMap.resolve(own, legacy, "SFC").keyFor(MapKey.A))
        // A game's legacy key that changes nothing leaves the console's map.
        val console = mapOf("nx_map_L1" to "R1", "nx_map_R1" to "L1")
        assertEquals(ButtonMap.fromConfig(console, "SFC"), ButtonMap.resolve(mapOf("nx_pad_layout" to "xbox"), console, "SFC"))
    }

    @Test
    fun gameSaveWritesOnlyWhatDiffersFromTheConsole() {
        val console = ButtonMap.DEFAULT.bind(MapKey.L1, MapKey.R1)
        assertEquals(emptyList(), ButtonMap.gameConfig(console, console))
        val game = ButtonMap.DEFAULT.bind(MapKey.A, MapKey.B)
        val lines = ButtonMap.gameConfig(game, console)
        assertEquals(game, ButtonMap.resolve(lines.toMap(), ButtonMap.toConfig(console).toMap(), "PS"))
        // The default over a console's mapping still writes lines, so the game keeps it.
        val reset = ButtonMap.gameConfig(ButtonMap.DEFAULT, console)
        assertTrue(reset.isNotEmpty())
        assertEquals(ButtonMap.DEFAULT, ButtonMap.resolve(reset.toMap(), ButtonMap.toConfig(console).toMap(), "PS"))
        val drop = ButtonMap.droppedOnSave(reset)
        assertFalse(drop("nx_map_A"))
        assertTrue(drop("nx_pad_layout"))
        assertTrue(ButtonMap.droppedOnSave(emptyList())("nx_map_A"))
    }
}
