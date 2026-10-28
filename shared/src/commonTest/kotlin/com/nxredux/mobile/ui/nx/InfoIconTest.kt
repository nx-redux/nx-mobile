package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InfoIconTest {
    // Hidden by default: only the selected row shows its info icon, and only when there is more to read.
    @Test fun the_info_icon_shows_only_on_the_selected_row_with_more_to_read() {
        assertTrue(nxInfoIconShown(selected = true, truncated = true, moreInfo = false))
        assertTrue(nxInfoIconShown(selected = true, truncated = false, moreInfo = true))
        assertFalse(nxInfoIconShown(selected = false, truncated = true, moreInfo = true))
        assertFalse(nxInfoIconShown(selected = true, truncated = false, moreInfo = false))
    }
}
