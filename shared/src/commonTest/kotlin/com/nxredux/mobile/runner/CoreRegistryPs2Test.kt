package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals

class CoreRegistryPs2Test {
    @Test fun armsx2LoadsThroughTheVfs() {
        assertEquals(LoadMode.FILE_DESCRIPTOR, CoreRegistry.forTag("PS2")!!.loadMode)
    }
}
