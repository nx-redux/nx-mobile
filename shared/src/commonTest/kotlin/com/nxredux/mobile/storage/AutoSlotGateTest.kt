package com.nxredux.mobile.storage

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AutoSlotGateTest {
    private class Store(var info: ResumeInfo, var shot: String? = "file:///a.9.png") : UserDataStore by NoUserData {
        override suspend fun resumeInfo(tag: String, fileName: String) = info
        override suspend fun hiddenStateShot(tag: String, fileName: String) = shot
    }

    @Test fun autoSlotHiddenWhenOff() = runTest {
        val gate = AutoSlotGate(Store(ResumeInfo(NxPaths.AUTO_SLOT, "file:///a.png"))) { false }
        assertEquals(ResumeInfo.NONE, gate.resumeInfo("GBA", "a.gba"))
        assertNull(gate.hiddenStateShot("GBA", "a.gba"))
    }

    @Test fun autoSlotShownWhenOn() = runTest {
        val gate = AutoSlotGate(Store(ResumeInfo(NxPaths.AUTO_SLOT, "file:///a.png"))) { true }
        assertEquals(ResumeInfo(NxPaths.AUTO_SLOT, "file:///a.png"), gate.resumeInfo("GBA", "a.gba"))
        assertEquals("file:///a.9.png", gate.hiddenStateShot("GBA", "a.gba"))
    }

    @Test fun manualSlotPassesThrough() = runTest {
        val gate = AutoSlotGate(Store(ResumeInfo(3, "file:///b.png"))) { false }
        assertEquals(ResumeInfo(3, "file:///b.png"), gate.resumeInfo("GBA", "a.gba"))
    }

    @Test fun readsSettingEachCall() = runTest {
        var on = false
        val gate = AutoSlotGate(Store(ResumeInfo(NxPaths.AUTO_SLOT))) { on }
        assertEquals(ResumeInfo.NONE, gate.resumeInfo("GBA", "a.gba"))
        on = true
        assertEquals(ResumeInfo(NxPaths.AUTO_SLOT), gate.resumeInfo("GBA", "a.gba"))
    }

    // A core without save states never resumes, whatever slot file is left from before: Continue plays it fresh.
    @Test fun noStateCoreNeverResumes() = runTest {
        for (slot in listOf(NxPaths.AUTO_SLOT, 3)) {
            val gate = AutoSlotGate(Store(ResumeInfo(slot, "file:///w.png"))) { true }
            assertFalse(gate.resumeInfo("WIIU", "zelda.wua").canResume)
            assertNull(gate.hiddenStateShot("WIIU", "zelda.wua"))
        }
    }
}
