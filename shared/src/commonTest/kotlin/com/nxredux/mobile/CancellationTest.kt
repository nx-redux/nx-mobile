package com.nxredux.mobile

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CancellationTest {
    @Test fun a_failure_gives_the_fallback() {
        assertEquals("boom", tryOr({ it.message }) { throw IllegalStateException("boom") })
        assertNull(tryOrNull<Int> { throw IllegalStateException() })
        tryQuietly { throw IllegalStateException() }
    }

    @Test fun a_result_passes_through() {
        assertEquals(3, tryOrNull { 3 })
    }

    @Test fun cancellation_is_never_swallowed() {
        assertFailsWith<CancellationException> { tryOrNull { throw CancellationException("stop") } }
        assertFailsWith<CancellationException> { tryQuietly { throw CancellationException("stop") } }
    }

    @Test fun an_error_still_propagates() {
        assertFailsWith<AssertionError> { tryOrNull { throw AssertionError() } }
    }
}
