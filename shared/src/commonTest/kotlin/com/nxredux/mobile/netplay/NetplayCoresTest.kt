package com.nxredux.mobile.netplay

import com.nxredux.mobile.runner.CoreRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetplayCoresTest {
    private val pico = "picodrive"
    private val gpgx = "genesis_plus_gx"

    @Test fun the_table_is_the_handhelds_paks_default_first() {
        val want = mapOf(
            "FC" to listOf("fceumm"), "FDS" to listOf("fceumm"),
            "SFC" to listOf("snes9x", "mednafen_supafaust"), "SUPA" to listOf("mednafen_supafaust", "snes9x"),
            "MD" to listOf(pico, gpgx), "SMS" to listOf(pico, gpgx), "GG" to listOf(pico, gpgx), "SG1000" to listOf(pico, gpgx), "SEGACD" to listOf(pico, gpgx),
            "GPGX" to listOf(gpgx, pico), "32X" to listOf(pico),
            "PS" to listOf("pcsx_rearmed", "swanstation"), "PSX" to listOf("swanstation", "pcsx_rearmed"),
            "FBN" to listOf("fbneo"),
            "N64" to listOf("mupen64plus_next"),
            "GB" to listOf("gambatte"), "GBC" to listOf("gambatte"), "GBA" to listOf("gpsp", "mgba"), "MGBA" to listOf("mgba", "gpsp"),
            "3DS" to listOf("azahar"), "NDS" to listOf("melondsds"),
        )
        want.forEach { (tag, cores) ->
            assertEquals(cores, NetplayCores.offered(tag), tag)
            assertEquals(cores.first(), NetplayCores.defaultCore(tag), tag)
        }
        for (tag in listOf("PSP", "SGB", "DC")) assertFalse(NetplayCores.supported(tag), tag)
        assertTrue(NetplayCores.supported("sfc"))
        assertNull(NetplayCores.defaultCore("PSP"))
    }

    @Test fun only_mupen64plus_next_runs_frames_before_the_state_sync() {
        assertEquals(30, NetplayCores.presyncFrames("mupen64plus_next"))
        for (core in listOf("snes9x", "fbneo", "pcsx_rearmed", "picodrive")) assertEquals(0, NetplayCores.presyncFrames(core), core)
    }

    @Test fun only_an_n64_session_is_kept_off_vulkan() {
        assertTrue(NetplayCores.forcesGles("mupen64plus_next"))
        for (core in listOf("swanstation", "pcsx_rearmed", null)) assertFalse(NetplayCores.forcesGles(core), core.toString())
    }

    @Test fun every_offered_core_is_registered() {
        listOf("FC", "SFC", "SUPA", "MD", "GG", "GPGX", "32X", "PS", "PSX", "FBN", "N64", "GB", "GBC", "GBA", "MGBA", "3DS", "NDS").flatMap(NetplayCores::offered).forEach {
            assertTrue(CoreRegistry.forId(it) != null, it)
        }
    }

    @Test fun link_cores_and_the_usb_only_tag() {
        assertEquals(NetplayLinkKind.GBLINK, NetplayCores.link("gambatte"))
        assertEquals(NetplayLinkKind.NETPACKET, NetplayCores.link("gpsp"))
        assertEquals(NetplayLinkKind.NETPACKET, NetplayCores.link("mgba"))
        assertEquals(NetplayLinkKind.N3DSLINK, NetplayCores.link("azahar"))
        assertEquals(NetplayLinkKind.NDSLINK, NetplayCores.link("melondsds"))
        assertNull(NetplayCores.link("snes9x"))
        assertTrue(NetplayCores.isLink("gpsp"))
        assertFalse(NetplayCores.isLink("picodrive"))
        assertTrue(NetplayCores.usbOnly("MGBA", "none"))
        assertTrue(NetplayCores.usbOnly(" mgba ", null))
        assertFalse(NetplayCores.usbOnly("MGBA", "rfu"))
        assertFalse(NetplayCores.usbOnly("MGBA", "aw2"))
        for (tag in listOf("GBA", "GB", "GBC", "SFC")) assertFalse(NetplayCores.usbOnly(tag, "none"), tag)
    }

    @Test fun mgba_leaves_the_cable_only_for_a_wifi_mode_game() {
        assertTrue(NetplayCores.wifiOk("mgba", "poke"))
        assertTrue(NetplayCores.wifiOk("mgba", "rfu"))
        assertFalse(NetplayCores.wifiOk("mgba", "none"))
        assertFalse(NetplayCores.wifiOk("mgba", null))
        assertFalse(NetplayCores.wifiOk("mgba", "aw3"))
        assertTrue(NetplayCores.wifiOk("gpsp", "none"))
        assertTrue(NetplayCores.wifiOk("snes9x", null))
        assertEquals(listOf("mgba", "gpsp"), NetplayCores.offered("MGBA", usb = false, wl = "rfu"))
        assertEquals(listOf("gpsp", "mgba"), NetplayCores.offered("GBA", usb = false, wl = "poke"))
        assertEquals(listOf("gpsp"), NetplayCores.offered("MGBA", usb = false, wl = "none"))
        assertEquals(listOf("mgba", "gpsp"), NetplayCores.offered("MGBA", usb = true, wl = "none"))
    }

    @Test fun a_mixed_pair_runs_only_when_switching_is_not_possible_and_the_modes_match() {
        // Joiner pinned to mGBA, host on gpSP, both rfu over Wi-Fi: each keeps its core.
        assertEquals("mgba", NetplayCores.joinerRun("GBA", "mgba", "mgba", "gpsp", usb = false, ourWl = "rfu", peerWl = "rfu"))
        assertNull(NetplayCores.joinerRun("GBA", "mgba", "mgba", "gpsp", usb = false, ourWl = "poke", peerWl = "rfu"))
        assertNull(NetplayCores.joinerRun("GBA", "mgba", "mgba", "gpsp", usb = false, ourWl = "rfu", peerWl = null))   // an older host
        // Over the cable only rfu stays itself: the other modes turn into the real cable there.
        assertEquals("mgba", NetplayCores.joinerRun("GBA", "mgba", "mgba", "gpsp", usb = true, ourWl = "rfu", peerWl = "rfu"))
        assertNull(NetplayCores.joinerRun("GBA", "mgba", "mgba", "gpsp", usb = true, ourWl = "poke", peerWl = "poke"))
        // Auto still switches to the host's core.
        assertEquals("gpsp", NetplayCores.joinerRun("MGBA", "mgba", null, "gpsp", usb = false, ourWl = "rfu", peerWl = "rfu"))
        assertEquals("mgba", NetplayCores.joinerRun("GBA", "gpsp", null, "mgba", usb = false, ourWl = "rfu", peerWl = "rfu"))
        // Lockstep cores never mix.
        assertNull(NetplayCores.joinerRun("MD", pico, pico, gpgx, usb = false, ourWl = "rfu", peerWl = "rfu"))
        // Hosting: pinned, or a handheld joiner on a core this connection does not offer here.
        assertEquals("gpsp", NetplayCores.hostRun("GBA", "gpsp", "gpsp", "mgba", "MGBA", joinerAuto = false, usb = false, ourWl = "rfu", peerWl = "rfu"))
        assertNull(NetplayCores.hostRun("GBA", "gpsp", "gpsp", "mgba", "MGBA", joinerAuto = false, usb = false, ourWl = "rfu", peerWl = "poke"))
        // Auto host and a handheld on mGBA with a Wi-Fi mode: the host adopts it (same core preferred).
        assertEquals("mgba", NetplayCores.hostRun("GBA", "gpsp", null, "mgba", "MGBA", joinerAuto = false, usb = false, ourWl = "rfu", peerWl = "rfu"))
        // A joiner on Auto that will switch: the host keeps its core.
        assertEquals("gpsp", NetplayCores.hostRun("GBA", "gpsp", null, "mgba", "MGBA", joinerAuto = true, usb = false, ourWl = "rfu", peerWl = "rfu"))
        // Retry: on Auto, mGBA over Wi-Fi only for a Wi-Fi mode game.
        assertEquals("mgba", NetplayCores.joinerRetry("GBA", null, "mgba", mayRetry = true, usb = false, ourWl = "aw1"))
        assertNull(NetplayCores.joinerRetry("GBA", null, "mgba", mayRetry = true, usb = false, ourWl = "none"))
    }

    @Test fun the_joiner_switches_to_the_hosts_core_only_on_auto_with_it_offered() {
        assertTrue(NetplayCores.joinerSwitches("gpsp", "MGBA", joinerAuto = true, usb = false, joinerWl = "none"))
        assertTrue(NetplayCores.joinerSwitches("mgba", "GBA", joinerAuto = true, usb = false, joinerWl = "rfu"))
        assertFalse(NetplayCores.joinerSwitches("mgba", "GBA", joinerAuto = true, usb = false, joinerWl = "none"))
        assertFalse(NetplayCores.joinerSwitches("gpsp", "MGBA", joinerAuto = false, usb = false, joinerWl = "rfu"))
        assertFalse(NetplayCores.joinerSwitches("gpsp", null, joinerAuto = true, usb = false, joinerWl = "rfu"))
    }

    @Test fun without_the_cable_or_a_wifi_mode_mgba_is_not_offered() {
        assertEquals(listOf("gpsp", "mgba"), NetplayCores.offered("GBA"))
        assertEquals(listOf("gpsp"), NetplayCores.offered("GBA", usb = false))
        assertEquals(listOf("mgba", "gpsp"), NetplayCores.offered("MGBA"))
        assertEquals(listOf("gpsp"), NetplayCores.offered("MGBA", usb = false))
        assertEquals(listOf(pico, gpgx), NetplayCores.offered("MD", usb = false))
        assertNull(NetplayCores.joinerRun("GBA", "gpsp", null, "mgba", usb = false))
        assertEquals("mgba", NetplayCores.joinerRun("GBA", "gpsp", null, "mgba", usb = true))
        assertNull(NetplayCores.joinerRetry("GBA", null, "mgba", mayRetry = true, usb = false))
        assertEquals("mgba", NetplayCores.joinerRetry("GBA", null, "mgba", mayRetry = true, usb = true))
        assertNull(NetplayCores.hostRun("GBA", "gpsp", null, "mgba", "MGBA", joinerAuto = false, usb = false))
        assertEquals("The host is using\nmGBA (MGBA folder).\n\nPick the game from the same\nfolder on both devices.",
            NetplayCores.refusal("GBA", "gpsp", "mgba", "MGBA", usb = false))
        assertEquals("mGBA links this game over the USB cable only.\n\nPick USB Cable, or another emulator.", NetplayText.cableOnly("mGBA"))
    }

    /** WizCaps_mgbaNeedsWl's rows (test_wiz_caps.c): mGBA off the cable needs the other side's wl=. */
    @Test fun mgba_off_the_cable_needs_the_other_sides_wifi_modes() {
        assertTrue(NetplayCores.needsPeerWl("mgba", null, "rfu", null, usb = false))
        assertTrue(NetplayCores.needsPeerWl("mgba", "mgba", "rfu", null, usb = false))
        assertTrue(NetplayCores.needsPeerWl("mgba", "gpsp", "rfu", null, usb = false))
        assertTrue(NetplayCores.needsPeerWl("gpsp", "mgba", null, "rfu", usb = false))
        assertFalse(NetplayCores.needsPeerWl("mgba", null, "rfu", null, usb = true))
        assertFalse(NetplayCores.needsPeerWl("mgba", "gpsp", "rfu", "rfu", usb = false))
        assertFalse(NetplayCores.needsPeerWl("mgba", "mgba", "none", "none", usb = false))
        assertFalse(NetplayCores.needsPeerWl("gpsp", null, "rfu", null, usb = false))
        assertFalse(NetplayCores.needsPeerWl("gpsp", "gpsp", "rfu", null, usb = false))
    }

    @Test fun phone_hosts_follow_the_handheld_and_keep_their_core_for_a_phone_on_auto() {
        // Phone <-> phone, both on Auto: the host keeps its core; the joiner switches to it.
        assertEquals("gpsp", NetplayCores.hostRun("GBA", "gpsp", null, "mgba", "MGBA", joinerAuto = true, usb = true))
        assertEquals("mgba", NetplayCores.hostRun("MGBA", "mgba", null, "gpsp", "GBA", joinerAuto = true, usb = true))
        assertEquals(pico, NetplayCores.hostRun("MD", pico, null, gpgx, "GPGX", joinerAuto = true, usb = false))
        // A handheld joiner (no auto): the host adopts its core.
        assertEquals("mgba", NetplayCores.hostRun("GBA", "gpsp", null, "mgba", "MGBA", joinerAuto = false, usb = true))
        // A pinned phone joiner sends no auto either: the host adopts.
        assertEquals(gpgx, NetplayCores.hostRun("MD", pico, null, gpgx, "MD", joinerAuto = false, usb = false))
        // The host pinned: refuse, keep waiting.
        assertNull(NetplayCores.hostRun("GBA", "gpsp", "gpsp", "mgba", "MGBA", joinerAuto = true, usb = true))
        assertNull(NetplayCores.hostRun("GBA", "gpsp", "gpsp", "mgba", "MGBA", joinerAuto = false, usb = true))
        // An auto joiner whose folder does not offer the host's core, or names no folder: adopt the joiner's.
        assertEquals(gpgx, NetplayCores.hostRun("MD", pico, null, gpgx, "XX", joinerAuto = true, usb = false))
        assertEquals(gpgx, NetplayCores.hostRun("MD", pico, null, gpgx, null, joinerAuto = true, usb = false))
        // Over Wi-Fi a host never adopts mGBA.
        assertNull(NetplayCores.hostRun("GBA", "gpsp", null, "mgba", null, joinerAuto = false, usb = false))
    }

    @Test fun picodrive_runs_every_sega_netplay_extension() {
        val ext = CoreRegistry.forId(pico)!!.extensions
        for (e in listOf("md", "gen", "smd", "bin", "32x", "sms", "sg", "cue", "iso", "chd", "m3u")) assertTrue(e in ext, e)
    }

    @Test fun names_come_from_the_registry() {
        assertEquals("PicoDrive", NetplayCores.name(pico))
        assertEquals("Genesis Plus GX", NetplayCores.name(gpgx))
        assertEquals("SwanStation", NetplayCores.name("swanstation"))
        assertEquals("vba_next", NetplayCores.name("vba_next"))
    }

    @Test fun joiner_keeps_its_core_switches_on_auto_or_refuses() {
        assertEquals(pico, NetplayCores.joinerRun("MD", pico, null, null, usb = false))     // an older handheld names none
        assertEquals(pico, NetplayCores.joinerRun("MD", pico, null, pico, usb = false))
        assertEquals(gpgx, NetplayCores.joinerRun("MD", pico, null, gpgx, usb = false))     // Auto and offered: switch
        assertNull(NetplayCores.joinerRun("MD", pico, pico, gpgx, usb = false))            // pinned: refuse
        assertNull(NetplayCores.joinerRun("SFC", "snes9x", null, "fbneo", usb = false))    // not offered: refuse
    }

    @Test fun joiner_retries_once_on_auto_with_an_offered_core() {
        assertEquals(gpgx, NetplayCores.joinerRetry("MD", null, gpgx, mayRetry = true, usb = false))
        assertNull(NetplayCores.joinerRetry("MD", null, gpgx, mayRetry = false, usb = false))
        assertNull(NetplayCores.joinerRetry("MD", pico, gpgx, mayRetry = true, usb = false))
        assertNull(NetplayCores.joinerRetry("SFC", null, "fbneo", mayRetry = true, usb = false))
    }

    @Test fun host_adopts_the_joiners_core_on_auto_or_refuses() {
        assertEquals(pico, NetplayCores.hostRun("MD", pico, null, null, null, joinerAuto = false, usb = false))
        assertEquals(pico, NetplayCores.hostRun("MD", pico, null, pico, null, joinerAuto = false, usb = false))
        assertEquals(gpgx, NetplayCores.hostRun("MD", pico, null, gpgx, null, joinerAuto = false, usb = false))
        assertNull(NetplayCores.hostRun("MD", pico, pico, gpgx, null, joinerAuto = false, usb = false))
        assertNull(NetplayCores.hostRun("SFC", "snes9x", null, "fbneo", null, joinerAuto = false, usb = false))
        assertEquals("swanstation", NetplayCores.hostRun("PS", "pcsx_rearmed", null, "swanstation", null, joinerAuto = false, usb = false))
    }

    @Test fun refusals_name_the_hosts_core() {
        assertEquals("The host is using PicoDrive.\nSet Emulator to PicoDrive.", NetplayCores.refusal("MD", gpgx, pico, "MD", usb = false))
        assertEquals("The host is using\nGenesis Plus GX (GPGX folder).\n\nPick the game from the same\nfolder on both devices.",
            NetplayCores.refusal("MD", null, gpgx, "GPGX", usb = false))
        assertEquals("The host is using\nSwanStation.\n\nPick the game from the same\nfolder on both devices.",
            NetplayCores.refusal("SFC", null, "swanstation", null, usb = false))
    }

    @Test fun new_texts_are_exact() {
        assertEquals("A player using PicoDrive was turned away.", NetplayText.coreTurnedAway("PicoDrive"))
        assertEquals("Saves not copied - playing on empty saves.", NetplayText.SAVES_NOT_COPIED)
        assertEquals("The host is using Snes9x.\nSet Emulator to Snes9x.", NetplayText.setEmulator("Snes9x"))
        assertEquals(setOf("nxsave.srm", "nxsave.sav"), NetplaySaves.WIRE_NAMES)
    }

    @Test fun cable_link_offered_only_for_gba_tags() {
        assertTrue(NetplayCores.cableLinkOffered("GBA"))
        assertTrue(NetplayCores.cableLinkOffered("MGBA"))
        assertTrue(NetplayCores.cableLinkOffered(" gba "))
        for (tag in listOf("GB", "GBC", "SGB", "SFC", "MD", "N64", "NDS", "UNKNOWN")) assertFalse(NetplayCores.cableLinkOffered(tag), tag)
    }

    @Test fun cable_link_runs_mgba_even_for_gpsp_default() {
        assertEquals("gpsp", NetplayCores.defaultCore("GBA"))
        assertEquals("mgba", NetplayCores.CABLE_LINK_CORE)
        assertEquals(NetplayCores.CABLE_LINK_CORE, com.nxredux.mobile.netplay.wizard.Wizard.CABLE_LINK_CORE)
        // A Cable Link session is a lockstep one on the engine, never the GBA link's netpacket session.
        val s = NetplaySession(NetplayRole.HOST, "10.0.0.2", "mgba", "gpsp", cableLink = true, otherRomPath = "/c/x.gba")
        assertNull(s.linkKind)
        assertEquals(NetplayLinkKind.NETPACKET, NetplaySession(NetplayRole.HOST, "10.0.0.2", "mgba", "gpsp").linkKind)
    }
}
