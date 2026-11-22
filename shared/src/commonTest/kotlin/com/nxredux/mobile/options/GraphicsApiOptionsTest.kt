package com.nxredux.mobile.options

import com.nxredux.mobile.runner.CoreRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphicsApiOptionsTest {
    // N64's ParaLLEl-RDP: its general shader only on an Adreno's own driver, whose compiler fails the specialised ones.
    @Test fun parallelTakesItsGeneralShaderOnlyOnAnAdrenosOwnDriver() {
        val switch = CoreRegistry.forTag("N64")!!.vulkan!!
        val vulkan = GraphicsApiOptions.resolve(GraphicsApiOptions.VULKAN, switch, { true })
        val gles = GraphicsApiOptions.resolve(GraphicsApiOptions.AUTO, switch, { true })
        val adreno = "Adreno (TM) 740"
        fun uber(launch: GraphicsApiLaunch, customDriver: Boolean, gpu: String?) =
            GraphicsApiOptions.envFor(launch, switch, customDriver, gpu).let { it.containsKey("PARALLEL_RDP_UBERSHADER") to it["PARALLEL_RDP_UBERSHADER"] }
        assertEquals(true to "1", uber(vulkan, customDriver = false, adreno))
        assertEquals(true to null, uber(vulkan, customDriver = true, adreno))   // Turnip compiles the specialised ones
        assertEquals(true to null, uber(vulkan, customDriver = false, "Mali-G76 MC4"))
        assertEquals(true to null, uber(gles, customDriver = false, adreno))   // unset after a Vulkan launch
        assertEquals("0", GraphicsApiOptions.envFor(vulkan, switch, false, adreno)["PARALLEL_RDP_SMALL_TYPES"])
    }

    @Test fun wii_and_gamecube_offer_the_graphics_api_row() {
        assertEquals(WiiOptions.table + DolphinOptions.resolutionRows(null) + GraphicsApiOptions.row, ConsoleSettings.tableFor("WII").filter { it !in PadOptions.all && it != BottomScreenOptions.row && it !in (SpeedOptions.table + AudioOptions.rows + FrameSkipOptions.row) })
        assertTrue(WiiOptions.screenRow in WiiOptions.table)
        assertEquals(DolphinOptions.resolutionRows(null) + GraphicsApiOptions.row, ConsoleSettings.tableFor(" gc ").filter { it !in PadOptions.all && it != BottomScreenOptions.row && it !in (SpeedOptions.table + AudioOptions.rows + FrameSkipOptions.row) })
        assertEquals(listOf("Auto", "OpenGL ES", "Vulkan"), GraphicsApiOptions.row.values.map(GraphicsApiOptions.row::labelOf))
        assertEquals(GraphicsApiOptions.AUTO, GraphicsApiOptions.row.default)
    }

    @Test fun the_row_shows_for_every_console_whose_core_has_a_vulkan_path() {
        listOf("WII", "GC", "PSP", "PSX", "N64", "DC", "3DS").forEach { assertTrue(GraphicsApiOptions.appliesTo(it), it) }
        listOf("PS", "GBA", "NDS", "SNES", "").forEach { assertFalse(GraphicsApiOptions.appliesTo(it), it) }
        assertTrue(ConsoleSettings.tableFor("PSX").any { it.key == GraphicsApiOptions.KEY })
        assertEquals(N3dsOptions.layoutRows + N3dsOptions.resolutionRows(null) + listOf(NdsOptions.shaderScreensRow, N3dsOptions.newControlsRow, N3dsOptions.localWirelessRow, GraphicsApiOptions.row), ConsoleSettings.tableFor("3DS").filter { it !in PadOptions.all && it != BottomScreenOptions.row && it !in (SpeedOptions.table + AudioOptions.rows) })
        assertTrue(ConsoleSettings.tableFor("PS").none { it.key == GraphicsApiOptions.KEY })
    }

    // The rows read only when the game starts, kept out of the in-game menu.
    @Test fun exactly_the_rows_read_at_launch_are_marked() {
        val all = listOf("WII", "GC", "N64", "PSP", "PSX", "DC", "3DS", "NDS", "MD", "GBA").flatMap(ConsoleSettings::tableFor).distinct()
        assertEquals(
            setOf(GraphicsApiOptions.KEY, WiiOptions.SCREEN, WiiOptions.POINTER_WIDTH, WiiOptions.POINTER_HEIGHT, WiiOptions.POINTER_OFFSET, AudioOptions.BUFFER, PspOptions.LOCAL_WIRELESS),
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

    private val ps2 = CoreRegistry.forTag("PS2")!!.vulkan

    @Test fun auto_is_vulkan_for_ps2_on_a_mali_gpu() {
        for (v in listOf(GraphicsApiOptions.AUTO, null)) {
            assertEquals(GraphicsApiOptions.VULKAN, GraphicsApiOptions.effective(v, ps2) { "Mali-G76 MC4" }, "$v")
            assertEquals(v, GraphicsApiOptions.effective(v, ps2) { "Adreno (TM) 740" }, "$v")
            assertEquals(v, GraphicsApiOptions.effective(v, ps2) { null }, "$v")
        }
        assertTrue(GraphicsApiOptions.resolve(GraphicsApiOptions.effective(GraphicsApiOptions.AUTO, ps2) { "Mali-G76 MC4" }, ps2, { true }).preferVulkan)
    }

    @Test fun a_picked_api_is_kept_on_a_mali_gpu_and_the_gpu_is_not_asked() {
        for (v in listOf(GraphicsApiOptions.GLES, GraphicsApiOptions.VULKAN)) {
            assertEquals(v, GraphicsApiOptions.effective(v, ps2) { error("asked") })
        }
    }

    @Test fun auto_is_vulkan_on_an_xclipse_gpu_except_n64_and_wii() {
        val xclipse = "ANGLE ((Samsung Xclipse 540) on Vulkan 1.3.279)"
        val onXclipse = listOf("3DS", "PS2", "GC", "PSP", "PSX", "DC")
        for (tag in onXclipse + listOf("N64", "WII")) {
            val switch = CoreRegistry.forTag(tag)!!.vulkan
            for (v in listOf(GraphicsApiOptions.AUTO, null)) {
                val expected = if (tag in onXclipse) GraphicsApiOptions.VULKAN else v
                assertEquals(expected, GraphicsApiOptions.effective(v, switch) { xclipse }, "$tag $v")
            }
        }
        val n3ds = CoreRegistry.forTag("3DS")!!.vulkan
        for (v in listOf(GraphicsApiOptions.AUTO, null)) {
            assertEquals(v, GraphicsApiOptions.effective(v, n3ds) { "Mali-G76 MC4" }, "$v")
            assertEquals(v, GraphicsApiOptions.effective(v, n3ds) { "Adreno (TM) 740" }, "$v")
        }
        assertEquals(GraphicsApiOptions.GLES, GraphicsApiOptions.effective(GraphicsApiOptions.GLES, n3ds) { error("asked") })
    }

    @Test fun only_ps2_dolphin_and_psp_follow_the_gpu_on_auto() {
        assertEquals(setOf("armsx2", "dolphin", "ppsspp"), CoreRegistry.all.filter { it.vulkan?.autoOnMali == true }.map { it.id }.toSet())
        // Dolphin follows the Mali GPU only with a custom driver (the stock driver tints some games); PS2 and PSP always.
        val wii = CoreRegistry.forTag("WII")!!.vulkan
        assertEquals(GraphicsApiOptions.AUTO, GraphicsApiOptions.effective(GraphicsApiOptions.AUTO, wii) { "Mali-G76 MC4" })
        assertEquals(GraphicsApiOptions.VULKAN, GraphicsApiOptions.effective(GraphicsApiOptions.AUTO, wii, customDriver = true) { "Mali-G76 MC4" })
        assertEquals(GraphicsApiOptions.AUTO, GraphicsApiOptions.effective(GraphicsApiOptions.AUTO, CoreRegistry.forTag("GC")!!.vulkan, customDriver = true) { "Adreno (TM) 740" })
        assertEquals(GraphicsApiOptions.VULKAN, GraphicsApiOptions.effective(GraphicsApiOptions.AUTO, CoreRegistry.forTag("PS2")!!.vulkan) { "Mali-G76 MC4" })
        assertEquals(GraphicsApiOptions.VULKAN, GraphicsApiOptions.effective(GraphicsApiOptions.AUTO, CoreRegistry.forTag("PSP")!!.vulkan) { "Mali-G76 MC4" })
        assertEquals(setOf("dolphin"), CoreRegistry.all.filter { it.vulkan?.maliNeedsCustomDriver == true }.map { it.id }.toSet())
        assertEquals(GraphicsApiOptions.AUTO, GraphicsApiOptions.effective(GraphicsApiOptions.AUTO, CoreRegistry.forTag("GC")!!.vulkan) { "Adreno (TM) 740" })
        assertEquals(GraphicsApiOptions.AUTO, GraphicsApiOptions.effective(GraphicsApiOptions.AUTO, n64) { error("asked") })
        assertEquals(GraphicsApiOptions.AUTO, GraphicsApiOptions.effective(GraphicsApiOptions.AUTO, null) { error("asked") })
    }

    @Test fun where_auto_is_vulkan_the_rows_say_so_and_the_gpu_driver_row_shows_on_auto() {
        val drivers = listOf(com.nxredux.mobile.gpudriver.GpuDriver("d", "Driver", "1", "a", "d", "libvulkan_d.so", 28))
        val plain = ConsoleSettings.tableFor("PS2", gpuDrivers = drivers)
        assertEquals(GraphicsApiOptions.row, plain.single { it.key == GraphicsApiOptions.KEY })
        assertTrue(plain.shownFor(emptyMap()).none { it.key == GpuDriverOptions.KEY })
        val mali = ConsoleSettings.tableFor("PS2", gpuDrivers = drivers, autoVulkan = true)
        val api = mali.single { it.key == GraphicsApiOptions.KEY }
        assertEquals("Applies next launch. Auto is Vulkan", api.brief)
        assertTrue("Auto uses Vulkan" in api.info)
        assertTrue(mali.shownFor(emptyMap()).any { it.key == GpuDriverOptions.KEY })
        assertTrue(mali.shownFor(mapOf(GraphicsApiOptions.KEY to GraphicsApiOptions.GLES)).none { it.key == GpuDriverOptions.KEY })
    }
}
