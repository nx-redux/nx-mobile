package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A toast with a button (the layout tip's "Don't show again"), and dismissing one early. */
class NotificationActionTest {
    @Test fun aToastCarriesItsAction() {
        val q = NotificationQueue()
        val id = q.push("tip", null, 5000L, now = 0L, action = "Don't show again")
        assertEquals("Don't show again", q.toasts.single { it.id == id }.action)
        val plain = NotificationQueue().apply { push("plain", null, 1000L, 0L) }
        assertNull(plain.toasts.single().action)
    }

    @Test fun dismissDropsItAtOnce() {
        val q = NotificationQueue()
        val a = q.push("a", null, 5000L, 0L); val b = q.push("b", null, 5000L, 0L)
        q.dismiss(a)
        assertEquals(listOf(b), q.toasts.map { it.id })
    }

    @Test fun aToastUntilClosedNeverExpires() {   // the layout tip waits for Close or its button
        val q = NotificationQueue()
        q.push("tip", null, NotificationQueue.UNTIL_CLOSED, now = 1_000L, action = "Don't show again")
        q.tick(now = 10L * 24 * 3600 * 1000)
        assertEquals(1, q.toasts.size)
    }
}
