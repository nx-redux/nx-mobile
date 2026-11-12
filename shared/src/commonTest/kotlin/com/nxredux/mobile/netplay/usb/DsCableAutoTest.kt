package com.nxredux.mobile.netplay.usb

import com.nxredux.mobile.netplay.usb.DsCableAuto.Cable
import com.nxredux.mobile.netplay.usb.DsCableAuto.Step
import kotlin.test.Test
import kotlin.test.assertEquals

class DsCableAutoTest {
    private fun status(peer: UsbPeer, link: UsbAutoLink) = UsbAutoStatus(peer, link)

    @Test
    fun nothing_on_the_port_starts_nothing() {
        assertEquals(Step(Cable.NONE, false), DsCableAuto().step(status(UsbPeer.NONE, UsbAutoLink.IDLE), 0))
    }

    @Test
    fun a_phone_or_a_host_on_the_port_starts_the_cable_once_per_pause() {
        val a = DsCableAuto()
        assertEquals(Step(Cable.NONE, true), a.step(status(UsbPeer.MAYBE, UsbAutoLink.IDLE), 1_000))
        assertEquals(Step(Cable.NONE, false), a.step(status(UsbPeer.MAYBE, UsbAutoLink.IDLE), 2_000))
        assertEquals(Step(Cable.NONE, true), a.step(status(UsbPeer.SEEN, UsbAutoLink.IDLE), 1_000 + DsCableAuto.RETRY_MS))
    }

    @Test
    fun linking_holds_a_wifi_room_only_with_a_phone_in_sight() {
        val a = DsCableAuto()
        assertEquals(Step(Cable.COMING, false), a.step(status(UsbPeer.SEEN, UsbAutoLink.LINKING), 0))
        assertEquals(Step(Cable.NONE, false), a.step(status(UsbPeer.MAYBE, UsbAutoLink.LINKING), 0))
    }

    @Test
    fun up_is_up_whoever_started_it() {
        assertEquals(Step(Cable.UP, false), DsCableAuto().step(status(UsbPeer.NONE, UsbAutoLink.UP), 0))
    }

    @Test
    fun a_link_that_ended_is_tried_again_after_the_pause() {
        val a = DsCableAuto()
        a.step(status(UsbPeer.SEEN, UsbAutoLink.UP), 0)
        a.step(status(UsbPeer.SEEN, UsbAutoLink.LINKING), 60_000)
        assertEquals(Step(Cable.NONE, false), a.step(status(UsbPeer.SEEN, UsbAutoLink.IDLE), 100_000))
        assertEquals(Step(Cable.NONE, true), a.step(status(UsbPeer.SEEN, UsbAutoLink.IDLE), 100_000 + DsCableAuto.RETRY_MS))
    }

    @Test
    fun refused_is_never_started_again() {
        val a = DsCableAuto()
        assertEquals(Step(Cable.NONE, false), a.step(status(UsbPeer.SEEN, UsbAutoLink.REFUSED), 0))
        assertEquals(Step(Cable.NONE, false), a.step(status(UsbPeer.SEEN, UsbAutoLink.IDLE), 60_000))
    }

    @Test
    fun a_denied_consent_is_not_asked_again() {
        val a = DsCableAuto()
        assertEquals(true, a.step(status(UsbPeer.SEEN, UsbAutoLink.IDLE), 0).start)
        a.refuse()
        assertEquals(Step(Cable.NONE, false), a.step(status(UsbPeer.SEEN, UsbAutoLink.IDLE), 60_000))
    }
}
