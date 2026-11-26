package com.nxredux.mobile.runner

import com.nxredux.mobile.ui.nx.NotificationQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Which core messages show as an in-game toast, for how long, and which repeats and moments drop them. */
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
    fun every_other_core_shows_its_text_as_sent() {
        assertEquals("Disk 1 of 2 Side B Inserted", CoreMessages.toast("snes9x", "Disk 1 of 2 Side B Inserted"))
        assertEquals("Insert Coin", CoreMessages.toast("fceumm", "  Insert Coin \n"))
        val cemu = "Software keyboard: type with the keyboard (Game Focus on), Enter accepts."
        assertEquals(cemu, CoreMessages.toast("cemu", cemu))
    }

    @Test
    fun a_blank_message_is_not_shown() {
        assertNull(CoreMessages.toast("cemu", ""))
        assertNull(CoreMessages.toast("cemu", "  \n "))
    }

    @Test fun azahar_shows_its_messages_but_not_an_empty_room() {
        assertEquals("Local wireless: Fatins Ta joined", CoreMessages.toast("azahar", "Local wireless: Fatins Ta joined"))
        assertEquals("Local wireless: connected", CoreMessages.toast("azahar", "Local wireless: connected"))
        assertNull(CoreMessages.toast("azahar", "Local wireless: room open"))
        assertEquals("Shader cache loaded", CoreMessages.toast("azahar", "Shader cache loaded"))
    }

    @Test
    fun the_core_s_duration_is_kept_within_readable_bounds() {
        assertEquals(6000L, CoreMessages.durationMs(6000))
        assertEquals(CoreMessages.DEFAULT_MS, CoreMessages.durationMs(0))
        assertEquals(CoreMessages.DEFAULT_MS, CoreMessages.durationMs(-5))
        assertEquals(CoreMessages.MIN_MS, CoreMessages.durationMs(16))   // one frame would never be seen
        assertEquals(CoreMessages.MAX_MS, CoreMessages.durationMs(600_000))
    }

    @Test
    fun a_message_shows_with_its_duration() {
        val gate = CoreMessageToasts()
        assertEquals(CoreToast("Hello", 6000L, status = false), gate.take("cemu", "Hello", 6000, status = false, now = 0L, menuOpen = false))
        assertEquals(CoreToast("Line", 3000L, status = true), gate.take("cemu", "Line", 3000, status = true, now = 0L, menuOpen = false))
    }

    @Test
    fun an_exact_repeat_within_the_window_is_dropped() {
        val gate = CoreMessageToasts()
        assertEquals("Hi", gate.take("cemu", "Hi", 0, false, now = 1_000L, menuOpen = false)?.text)
        assertNull(gate.take("cemu", "Hi", 0, false, now = 1_000L + CoreMessageToasts.REPEAT_WINDOW_MS - 1, menuOpen = false))
        assertEquals("Other", gate.take("cemu", "Other", 0, false, now = 1_500L, menuOpen = false)?.text)
    }

    @Test
    fun a_core_repeating_a_message_without_pause_shows_it_once() {
        val gate = CoreMessageToasts()
        val step = CoreMessageToasts.REPEAT_WINDOW_MS / 2
        assertEquals("Hi", gate.take("cemu", "Hi", 0, false, now = 0L, menuOpen = false)?.text)
        for (i in 1..10) assertNull(gate.take("cemu", "Hi", 0, false, now = i * step, menuOpen = false))
    }

    @Test
    fun a_repeat_after_the_window_shows_again() {
        val gate = CoreMessageToasts()
        assertEquals("Hi", gate.take("cemu", "Hi", 0, false, now = 0L, menuOpen = false)?.text)
        assertEquals("Hi", gate.take("cemu", "Hi", 0, false, now = CoreMessageToasts.REPEAT_WINDOW_MS, menuOpen = false)?.text)
    }

    @Test
    fun nothing_shows_while_the_menu_is_open_and_it_is_not_counted_as_seen() {
        val gate = CoreMessageToasts()
        assertNull(gate.take("cemu", "Hi", 0, false, now = 0L, menuOpen = true))
        assertEquals("Hi", gate.take("cemu", "Hi", 0, false, now = 10L, menuOpen = false)?.text)
    }

    @Test
    fun a_hidden_message_is_dropped_by_the_gate_too() {
        val gate = CoreMessageToasts()
        assertNull(gate.take("fceumm", "Disk 1 of 2 Side B Ejected", 0, false, now = 0L, menuOpen = false))
        assertEquals("Disk 1 of 2, side B", gate.take("fceumm", "Disk 1 of 2 Side B Inserted", 0, false, now = 0L, menuOpen = false)?.text)
    }

    private fun texts(q: NotificationQueue) = q.toasts.map { it.text }

    @Test
    fun a_core_s_changing_text_never_pushes_out_the_app_s_own_toasts() {
        val q = NotificationQueue()
        val gate = CoreMessageToasts()
        q.push("Layout tip", null, NotificationQueue.UNTIL_CLOSED, 0L, "Don't show again")
        q.push("Games close in 10 minutes", null, 6000L, 0L)
        // a counter a second apart (past the coalescing window), and a typed-text echo 100 ms apart
        for (i in 1..20) gate.show(q, "cemu", "Compiling shaders $i/20", 0, status = false, now = i * 1000L, menuOpen = false)
        for (i in 1..20) gate.show(q, "cemu", "Keyboard: ${"a".repeat(i)}_", 0, status = false, now = 30_000L + i * 100L, menuOpen = false)
        assertTrue("Layout tip" in texts(q), texts(q).toString())
        assertTrue("Games close in 10 minutes" in texts(q), texts(q).toString())
        assertTrue(q.toasts.count { it.text.startsWith("Compiling") || it.text.startsWith("Keyboard") } <= CoreMessageToasts.MAX_SHOWN)
        assertEquals("Keyboard: ${"a".repeat(20)}_", q.toasts.last().text)
    }

    @Test
    fun the_app_s_toasts_stay_when_the_queue_is_full_of_them() {
        val q = NotificationQueue(max = 2)
        q.push("One", null, 60_000L, 0L)
        q.push("Two", null, 60_000L, 0L)
        assertNull(CoreMessageToasts().show(q, "cemu", "Core says", 0, status = false, now = 0L, menuOpen = false))
        assertEquals(listOf("One", "Two"), texts(q))
    }

    @Test
    fun plain_messages_close_together_coalesce_into_the_latest() {
        val q = NotificationQueue()
        val gate = CoreMessageToasts()
        gate.show(q, "cemu", "a", 0, status = false, now = 0L, menuOpen = false)
        gate.show(q, "cemu", "ab", 0, status = false, now = 100L, menuOpen = false)
        gate.show(q, "cemu", "abc", 0, status = false, now = 200L, menuOpen = false)
        assertEquals(listOf("abc"), texts(q))
        gate.show(q, "cemu", "later", 0, status = false, now = 200L + CoreMessageToasts.COALESCE_MS, menuOpen = false)
        assertEquals(listOf("abc", "later"), texts(q))
    }

    @Test
    fun a_status_or_progress_line_replaces_the_last_and_a_repeat_of_an_older_one_still_shows() {
        val q = NotificationQueue()
        val gate = CoreMessageToasts()
        gate.show(q, "cemu", "A", 0, status = true, now = 0L, menuOpen = false)
        gate.show(q, "cemu", "B", 0, status = true, now = 1000L, menuOpen = false)
        gate.show(q, "cemu", "A", 0, status = true, now = 2000L, menuOpen = false)
        assertEquals(listOf("A"), texts(q))
        assertNull(gate.show(q, "cemu", "A", 0, status = true, now = 2500L, menuOpen = false))   // the one shown: nothing new
        assertEquals(listOf("A"), texts(q))
    }

    @Test
    fun the_disk_side_and_wireless_news_also_go_on_the_picture() {
        assertTrue(CoreMessages.alsoOnPicture("fceumm"))
        assertTrue(CoreMessages.alsoOnPicture("azahar"))
        assertTrue(!CoreMessages.alsoOnPicture("cemu"))
    }
}
