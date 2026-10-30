package com.nxredux.mobile.options

import com.nxredux.mobile.runner.CoreRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphicsApiOptionsTest {
    @Test fun wii_and_gamecube_offer_the_graphics_api_row() {
        assertEquals(WiiOptions.table + GraphicsApiOptions.row, ConsoleSettings.tableFor("WII").filter { it !in PadOptions.all && it != BottomScreenOptions.row && it !in (SpeedOptions.table + AudioOptions.row) })
        assertTrue(WiiOptions.screenRow in WiiOptions.table)
        assertEquals(listOf(GraphicsApiOptions.row), ConsoleSettings.tableFor(" gc ").filter { it !in PadOptions.all && it != BottomScreenOptions.row && it !in (SpeedOptions.table + AudioOptions.row) })
        assertEquals(listOf("Auto", "OpenGL ES", "Vulkan"), GraphicsApiOptions.row.values.map(GraphicsApiOptions.row::labelOf))
        assertEquals(GraphicsApiOptions.AUTO, GraphicsApiOptions.row.default)
    }

    @Test fun the_row_shows_for_every_console_whose_core_has_a_vulkan_path() {
        listOf("WII", "GC", "PSP", "PSX", "N64", "DC", "3DS").forEach { assertTrue(GraphicsApiOptions.appliesTo(it), it) }
        listOf("PS", "GBA", "NDS", "SNES", "").forEach { assertFalse(GraphicsApiOptions.appliesTo(it), it) }
        assertTrue(ConsoleSettings.tableFor("PSX").any { it.key == GraphicsApiOptions.KEY })
        assertEquals(N3dsOptions.layoutRows + N3dsOptions.resolutionRows(null) + listOf(N3dsOptions.newControlsRow, GraphicsApiOptions.row), ConsoleSettings.tableFor("3DS").filter { it !in PadOptions.all && it != BottomScreenOptions.row && it !in (SpeedOptions.table + AudioOptions.row) })
        assertTrue(ConsoleSettings.tableFor("PS").none { it.key == GraphicsApiOptions.KEY })
    }

    // The rows read only when the game starts, kept out of the in-game menu.
    @Test fun exactly_the_rows_read_at_launch_are_marked() {
        val all = listOf("WII", "GC", "N64", "PSP", "PSX", "DC", "3DS", "NDS", "MD", "GBA").flatMap(ConsoleSettings::tableFor).distinct()
        assertEquals(
            setOf(GraphicsApiOptions.KEY, WiiOptions.SCREEN, WiiOptions.POINTER_WIDTH, WiiOptions.POINTER_HEIGHT, WiiOptions.POINTER_OFFSET),
            all.filter { it.atLaunch }.map { it.key }.toSet(),
        )
    }

    @Test fun dreamcast_vulkan_is_a_plain_switch() {
        assertEquals(GraphicsApiLaunch(true, emptyMap(), false), GraphicsApiOptions.resolve(GraphicsApiOptions.VULKAN, CoreRegistry.forTag("DC")!!.vulkan, { true }))
    }

    private val n64 = CoreRegistry.forTag("N64")!!.vulkan

    @Test fun auto_and_gles_force_the_gles_options_and_never_offer_vulkan() {
        for (v in listOf(GraphicsApiOptions.AUTO, GraphicsApiOptions.GLES, null, "metal")) {
            val l = GraphicsApiOptions.resolve(v, n64, vulkanSupported = { true })
            assertFalse(l.preferVulkan, "$v")
            assertEquals(mapOf("mupen64plus-rdp-plugin" to "gliden64", "mupen64plus-rsp-plugin" to "hle"), l.forcedOptions)
        }
    }

    @Test fun vulkan_forces_the_vulkan_options_and_offers_vulkan() {
        val l = GraphicsApiOptions.resolve(GraphicsApiOptions.VULKAN, n64, vulkanSupported = { true })
        assertTrue(l.preferVulkan)
        assertEquals(mapOf("mupen64plus-rdp-plugin" to "parallel", "mupen64plus-rsp-plugin" to "parallel"), l.forcedOptions)
    }

    @Test fun vulkan_on_an_unsupported_phone_uses_the_gles_options() {
        val l = GraphicsApiOptions.resolve(GraphicsApiOptions.VULKAN, n64, vulkanSupported = { false })
        assertFalse(l.preferVulkan)
        assertEquals("gliden64", l.forcedOptions["mupen64plus-rdp-plugin"])
    }

    // ParaLLEl-RDP's 8/16-bit shader types don't compile on the Adreno 840 driver (every compute pipeline failed: black
    // screen); with them off it renders. Set for a Vulkan launch, and cleared for any other so it never lingers.
    @Test fun n64_on_vulkan_turns_parallel_rdp_small_types_off_and_clears_it_otherwise() {
        assertEquals(mapOf("PARALLEL_RDP_SMALL_TYPES" to "0"), GraphicsApiOptions.resolve(GraphicsApiOptions.VULKAN, n64, { true }).env)
        assertEquals(mapOf("PARALLEL_RDP_SMALL_TYPES" to null), GraphicsApiOptions.resolve(GraphicsApiOptions.GLES, n64, { true }).env)
        assertEquals(mapOf("PARALLEL_RDP_SMALL_TYPES" to null), GraphicsApiOptions.resolve(GraphicsApiOptions.VULKAN, n64, { false }).env)
        assertEquals(emptyMap(), GraphicsApiOptions.resolve(GraphicsApiOptions.VULKAN, CoreRegistry.forTag("PSP")!!.vulkan, { true }).env)
    }

    @Test fun a_core_without_a_vulkan_path_gets_nothing() {
        assertEquals(GraphicsApiLaunch(false, emptyMap(), false), GraphicsApiOptions.resolve(GraphicsApiOptions.VULKAN, null, { true }))
    }

    @Test fun the_vulkan_probe_runs_only_when_vulkan_is_picked_on_a_core_with_a_vulkan_path() {
        var calls = 0
        val probe = { calls++; true }
        for (v in listOf(GraphicsApiOptions.AUTO, GraphicsApiOptions.GLES, null, "metal")) GraphicsApiOptions.resolve(v, n64, probe)
        for (v in listOf(GraphicsApiOptions.AUTO, GraphicsApiOptions.GLES, GraphicsApiOptions.VULKAN, null)) GraphicsApiOptions.resolve(v, null, probe)
        assertEquals(0, calls)
        assertTrue(GraphicsApiOptions.resolve(GraphicsApiOptions.VULKAN, n64, probe).preferVulkan)
        assertEquals(1, calls)
    }

    @Test fun dolphin_keeps_its_swapchain_bound() {
        val wii = GraphicsApiOptions.resolve(GraphicsApiOptions.VULKAN, CoreRegistry.forTag("WII")!!.vulkan, { true })
        assertTrue(wii.preferVulkan && wii.swapchainFrames && wii.forcedOptions.isEmpty())
        val psp = GraphicsApiOptions.resolve(GraphicsApiOptions.VULKAN, CoreRegistry.forTag("PSP")!!.vulkan, { true })
        assertFalse(psp.swapchainFrames)
    }

    @Test fun only_vulkan_offers_vulkan_auto_and_unknown_stay_on_gles() {
        assertTrue(GraphicsApiOptions.preferVulkan(GraphicsApiOptions.VULKAN))
        assertFalse(GraphicsApiOptions.preferVulkan(GraphicsApiOptions.GLES))
        assertFalse(GraphicsApiOptions.preferVulkan(GraphicsApiOptions.AUTO))
        assertFalse(GraphicsApiOptions.preferVulkan("metal"))
        assertFalse(GraphicsApiOptions.preferVulkan(null))
    }

    @Test fun the_key_is_a_frontend_key_never_sent_to_the_core() {
        assertTrue(FrontendOptions.isFrontendKey(GraphicsApiOptions.KEY))
        assertEquals(GraphicsApiOptions.row, FrontendOptions.option(GraphicsApiOptions.KEY))
        assertEquals(GraphicsApiOptions.AUTO, FrontendOptions.defaults("GC")[GraphicsApiOptions.KEY])
    }
}
