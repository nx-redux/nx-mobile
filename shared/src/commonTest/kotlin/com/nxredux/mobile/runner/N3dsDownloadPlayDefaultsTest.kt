package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class N3dsDownloadPlayDefaultsTest {
    @Test fun mali_with_a_driver_gets_vulkan_and_it() {
        assertEquals(mapOf("nx_graphics_api" to "vulkan", "nx_gpu_driver" to "abc"), n3dsDownloadPlayDefaults("Mali-G76", listOf("abc")))
    }

    // OpenGL ES at 2x ran a Mali guest at ~52 fps, which pulls a race out of sync.
    @Test fun mali_without_driver_starts_at_1x() {
        assertEquals(mapOf("nx_3ds_3d_resolution" to "performance", "nx_3ds_3d_scale" to "1"), n3dsDownloadPlayDefaults("Mali-G76", emptyList()))
    }

    @Test fun other_gpus_keep_the_console_settings() {
        assertEquals(emptyMap(), n3dsDownloadPlayDefaults("Adreno (TM) 740", listOf("abc")))
        assertEquals(emptyMap(), n3dsDownloadPlayDefaults(null, emptyList()))
    }

    @Test fun local_wireless_off_refuses_with_the_setting_name() {
        assertEquals("3DS Download Play needs Local Wireless on (Game Settings → Multiplayer)", n3dsDownloadPlayRefusal(false))
        assertNull(n3dsDownloadPlayRefusal(true))
    }
}
