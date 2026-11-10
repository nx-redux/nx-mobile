package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals

class NxKeepTargetTest {
    private val label = NxSettingRow("Account", section = true)
    private val row = NxSettingRow("Sign out")

    @Test fun a_selection_under_only_labels_keeps_the_list_at_its_top() =
        assertEquals(0, nxKeepTarget(listOf(label, row, row), selected = 1, footer = false))

    @Test fun a_selection_under_a_row_keeps_itself() =
        assertEquals(2, nxKeepTarget(listOf(label, row, row), selected = 2, footer = false))

    @Test fun the_footer_wins() = assertEquals(3, nxKeepTarget(listOf(label, row, row), selected = 1, footer = true))
}
