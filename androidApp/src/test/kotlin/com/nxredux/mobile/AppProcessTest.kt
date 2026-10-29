package com.nxredux.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppProcessTest {
    @Test fun gameProcessNamesAreRecognised() {
        assertTrue(AppProcess.isGameName("com.nxredux.mobile:game"))
        assertTrue(AppProcess.isGameName("com.nxredux.mobile.dev:game"))
    }

    @Test fun menuProcessAndEmptyNamesAreNotGame() {
        assertFalse(AppProcess.isGameName("com.nxredux.mobile"))
        assertFalse(AppProcess.isGameName("com.nxredux.mobile.dev"))
        assertFalse(AppProcess.isGameName(""))
    }
}
