package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Which core messages show as a toast: only fceumm's FDS disk inserts. */
class CoreMessagesTest {
    @Test
    fun fceummInsertedShows() {
        assertEquals("Disk 1 of 2, side B", CoreMessages.toast("fceumm", "Disk 1 of 2 Side B Inserted"))
    }

    @Test
    fun fceummEjectedAndSelectedHidden() {
        assertNull(CoreMessages.toast("fceumm", "Disk 1 of 2 Side B Ejected"))
        assertNull(CoreMessages.toast("fceumm", "Disk 1 of 2 Side B Selected"))
    }

    @Test
    fun otherCoresHidden() {
        assertNull(CoreMessages.toast("snes9x", "Disk 1 of 2 Side B Inserted"))
    }

    @Test
    fun otherFceummTextHidden() {
        assertNull(CoreMessages.toast("fceumm", "Insert Coin"))
        assertNull(CoreMessages.toast("fceumm", "Eject disk before selecting"))
    }
}
