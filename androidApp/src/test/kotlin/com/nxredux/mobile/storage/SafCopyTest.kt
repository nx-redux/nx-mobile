package com.nxredux.mobile.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafCopyTest {
    // Nothing read (-1) is never a finished copy, even when the provider reports no size.
    @Test fun a_copy_is_finished_only_when_its_bytes_were_written() {
        assertFalse(safCopyComplete(written = -1, expected = null))
        assertFalse(safCopyComplete(written = -1, expected = 10))
        assertFalse(safCopyComplete(written = 9, expected = 10))
        assertTrue(safCopyComplete(written = 10, expected = 10))
        assertTrue(safCopyComplete(written = 4, expected = null))
    }
}
