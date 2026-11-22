package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Which core messages show as a toast: fceumm's FDS disk inserts and Azahar's local wireless news. */
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

    @Test fun azahar_local_wireless_news_is_shown_but_an_empty_room_is_not() {
        assertEquals("Local wireless: Fatins Ta joined", CoreMessages.toast("azahar", "Local wireless: Fatins Ta joined"))
        assertEquals("Local wireless: connected", CoreMessages.toast("azahar", "Local wireless: connected"))
        assertNull(CoreMessages.toast("azahar", "Local wireless: room open"))
        assertNull(CoreMessages.toast("azahar", "Shader cache loaded"))
        assertNull(CoreMessages.toast("melondsds", "Local wireless: connected"))
    }
}
