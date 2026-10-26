package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NdsRendererTest {
    @Test fun the_core_id_matches_the_registry() {
        assertEquals("melondsds", NdsRenderer.CORE_ID_MELONDSDS)
    }

    @Test fun gpu_by_default_from_gles_3_2() {
        assertEquals(mapOf("melonds_render_mode" to "opengl"), NdsRenderer.defaults(0x30002))
        assertEquals(mapOf("melonds_render_mode" to "opengl"), NdsRenderer.defaults(0x30003))
        assertEquals(mapOf("melonds_render_mode" to "software"), NdsRenderer.defaults(0x30001))
        assertEquals(mapOf("melonds_render_mode" to "software"), NdsRenderer.defaults(0x20000))
        assertEquals(mapOf("melonds_render_mode" to "software"), NdsRenderer.defaults(0))
    }

    @Test fun the_renderer_applies_on_restart() {
        assertEquals(setOf("melonds_render_mode"), NdsRenderer.APPLY_ON_RESTART)
    }

    @Test fun the_notice_shows_only_when_opengl_did_not_start() {
        assertTrue(NdsRenderer.showFallbackNotice("opengl", hardwareRendered = false))
        assertFalse(NdsRenderer.showFallbackNotice("opengl", hardwareRendered = true))
        assertFalse(NdsRenderer.showFallbackNotice("software", hardwareRendered = false))
        assertFalse(NdsRenderer.showFallbackNotice(null, hardwareRendered = false))
    }

    @Test
    fun theRunningGameRendersSoftwareInSoftwareModeOrAfterAFallback() {
        assertFalse(NdsRenderer.rendersSoftware(NdsRenderer.OPENGL, fellBack = false))
        assertTrue(NdsRenderer.rendersSoftware(NdsRenderer.OPENGL, fellBack = true))
        assertTrue(NdsRenderer.rendersSoftware(NdsRenderer.SOFTWARE, fellBack = false))
        assertTrue(NdsRenderer.rendersSoftware(null, fellBack = false))
    }
}
