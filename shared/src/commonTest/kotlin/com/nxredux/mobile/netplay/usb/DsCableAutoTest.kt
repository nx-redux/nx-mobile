package com.nxredux.mobile.netplay.usb

import com.nxredux.mobile.netplay.usb.DsCableAuto.Cable
import com.nxredux.mobile.netplay.usb.DsCableAuto.Step
import kotlin.test.Test
import kotlin.test.assertEquals

class DsCableAutoTest {
    private fun status(peer: UsbPeer, link: UsbAutoLink) = UsbAutoStatus(peer, link)

    @Test
    fun nothing_on_the_port_starts_nothing() {
        assertEquals(Step(Cable.NONE, false), DsCableAuto().step(status(UsbPeer.NONE, UsbAutoLink.IDLE), true, 0))
    }

    @Test
    fun a_host_that_may_be_a_computer_starts_nothing() {
        assertEquals(Step(Cable.NONE, false), DsCableAuto().step(status(UsbPeer.MAYBE, UsbAutoLink.IDLE), true, 0))
    }

    @Test
    fun a_phone_in_sight_holds_the_search_before_the_wireless_and_starts_with_it() {
        val a = DsCableAuto()
        assertEquals(Step(Cable.COMING, false), a.step(status(UsbPeer.SEEN, UsbAutoLink.IDLE), false, 1_000))
        assertEquals(Step(Cable.COMING, true), a.step(status(UsbPeer.SEEN, UsbAutoLink.IDLE), true, 2_000))
    }

    @Test
    fun a_start_that_did_not_take_is_tried_again_after_the_pause() {
        val a = DsCableAuto()
        assertEquals(true, a.step(status(UsbPeer.SEEN, UsbAutoLink.IDLE), true, 1_000).start)
        assertEquals(false, a.step(status(UsbPeer.SEEN, UsbAutoLink.IDLE), true, 2_000).start)
        assertEquals(true, a.step(status(UsbPeer.SEEN, UsbAutoLink.IDLE), true, 1_000 + DsCableAuto.RETRY_MS).start)
    }

    @Test
    fun linking_is_not_started_again() {
        assertEquals(Step(Cable.COMING, false), DsCableAuto().step(status(UsbPeer.SEEN, UsbAutoLink.LINKING), true, 0))
    }

    @Test
    fun up_is_up_whoever_started_it() {
        assertEquals(Step(Cable.UP, false), DsCableAuto().step(status(UsbPeer.NONE, UsbAutoLink.UP), false, 0))
    }

    @Test
    fun a_link_that_ended_is_tried_again_after_the_pause() {
        val a = DsCableAuto()
        a.step(status(UsbPeer.SEEN, UsbAutoLink.UP), true, 0)
        assertEquals(Step(Cable.COMING, false), a.step(status(UsbPeer.SEEN, UsbAutoLink.IDLE), true, 100_000))
        assertEquals(Step(Cable.COMING, true), a.step(status(UsbPeer.SEEN, UsbAutoLink.IDLE), true, 100_000 + DsCableAuto.RETRY_MS))
    }

    @Test
    fun refused_is_never_started_again_and_holds_nothing() {
        val a = DsCableAuto()
        assertEquals(Step(Cable.NONE, false), a.step(status(UsbPeer.SEEN, UsbAutoLink.REFUSED), true, 0))
        assertEquals(Step(Cable.NONE, false), a.step(status(UsbPeer.SEEN, UsbAutoLink.IDLE), true, 60_000))
    }

    @Test
    fun psp_starts_the_cable_with_a_phone_in_sight_before_the_wireless() {
        assertEquals(Step(Cable.COMING, true), DsCableAuto(withGame = true).step(status(UsbPeer.SEEN, UsbAutoLink.IDLE), false, 0))
        assertEquals(Step(Cable.COMING, false), DsCableAuto(withGame = false).step(status(UsbPeer.SEEN, UsbAutoLink.IDLE), false, 0))
    }

    @Test
    fun psp_starts_nothing_without_a_phone_in_sight() {
        assertEquals(Step(Cable.NONE, false), DsCableAuto(withGame = true).step(status(UsbPeer.MAYBE, UsbAutoLink.IDLE), false, 0))
        assertEquals(Step(Cable.NONE, false), DsCableAuto(withGame = true).step(status(UsbPeer.NONE, UsbAutoLink.IDLE), true, 0))
    }

    @Test
    fun psp_tries_a_link_that_ended_again_after_the_pause_without_the_wireless() {
        val a = DsCableAuto(withGame = true)
        a.step(status(UsbPeer.SEEN, UsbAutoLink.LINKING), false, 0)
        assertEquals(Step(Cable.COMING, false), a.step(status(UsbPeer.SEEN, UsbAutoLink.IDLE), false, 100_000))
        assertEquals(Step(Cable.COMING, true), a.step(status(UsbPeer.SEEN, UsbAutoLink.IDLE), false, 100_000 + DsCableAuto.RETRY_MS))
    }

    @Test
    fun psp_refused_is_never_started_and_holds_nothing() {
        val a = DsCableAuto(withGame = true)
        assertEquals(true, a.step(status(UsbPeer.SEEN, UsbAutoLink.IDLE), false, 0).start)
        a.refuse()
        assertEquals(Step(Cable.NONE, false), a.step(status(UsbPeer.SEEN, UsbAutoLink.IDLE), false, 60_000))
    }

    @Test
    fun psp_up_counts_only_while_a_phone_is_on_the_port() {
        val a = DsCableAuto(withGame = true)
        assertEquals(Step(Cable.UP, false), a.step(status(UsbPeer.SEEN, UsbAutoLink.UP), false, 0))
        assertEquals(Step(Cable.NONE, false), a.step(status(UsbPeer.NONE, UsbAutoLink.UP), false, 1_000))
        assertEquals(Step(Cable.NONE, false), a.step(status(UsbPeer.MAYBE, UsbAutoLink.UP), false, 2_000))
        assertEquals(Step(Cable.UP, false), a.step(status(UsbPeer.SEEN, UsbAutoLink.UP), false, 3_000))
    }

    @Test
    fun ds_and_3ds_up_is_up_whatever_the_port_shows() {
        val a = DsCableAuto(withGame = false)
        assertEquals(Step(Cable.UP, false), a.step(status(UsbPeer.SEEN, UsbAutoLink.UP), true, 0))
        assertEquals(Step(Cable.UP, false), a.step(status(UsbPeer.NONE, UsbAutoLink.UP), true, 1_000))
        assertEquals(Step(Cable.UP, false), a.step(status(UsbPeer.MAYBE, UsbAutoLink.UP), false, 2_000))
    }

    @Test
    fun a_denied_consent_is_not_asked_again() {
        val a = DsCableAuto()
        assertEquals(true, a.step(status(UsbPeer.SEEN, UsbAutoLink.IDLE), true, 0).start)
        a.refuse()
        assertEquals(Step(Cable.NONE, false), a.step(status(UsbPeer.SEEN, UsbAutoLink.IDLE), true, 60_000))
    }
}
