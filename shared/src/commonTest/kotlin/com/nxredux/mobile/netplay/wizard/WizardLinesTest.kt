package com.nxredux.mobile.netplay.wizard

import com.nxredux.mobile.netplay.RejectReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fixtures from nx-redux workspace/all/common/tests/test_wiz_caps.c and wizard_net.c at 8f5a760b. */
class WizardLinesTest {
    private val esc = "\u001f"
    private val kart = "Super${esc}Mario${esc}Kart${esc}(USA)"

    @Test fun caps_tokens_are_short_and_wire_safe() {
        for (ok in listOf("dcbios=0123456789ab", "dcbios=none", "a=1,b=2", "x.y_z-1", "a".repeat(63))) assertTrue(Caps.isValid(ok), ok)
        for (bad in listOf("", "has space", "semi;colon", "quote'", "dollar\$x", "a".repeat(64), "é=1")) assertFalse(Caps.isValid(bad), bad)
        assertFalse(Caps.isValid(null))
    }

    @Test fun find_matches_the_handheld_fixtures() {
        val cases = listOf(
            "HELLO 1 Soulcalibur${esc}(USA) client caps=dcbios=abc" to "dcbios=abc",
            "HELLO 1 game client any caps=dcbios=none" to "dcbios=none",
            "HELLO 1 game host 2 caps=dcbios=abc" to "dcbios=abc",
            "HELLO 1 game client" to "",
            "HELLO 1 game host 2" to "",
            "HELLO 1 game client any" to "",
            "HELLO 1 caps=x client" to "",            // a game token that merely contains caps= is not the field
            "HELLO 1 game client caps=bad;rm" to "",  // an invalid token is dropped, not trusted
            "HELLO 1 game client caps=" to "",
            "HELLO 1 game host 2 caps=dcbios=abc\n" to "dcbios=abc",
        )
        cases.forEach { (line, want) -> assertEquals(want, Caps.find(line), line) }
    }

    @Test fun value_looks_up_one_key() {
        assertEquals("picodrive", Caps.value("core=picodrive,tag=MD", "core"))
        assertEquals("MD", Caps.value("core=picodrive,tag=MD", "tag"))
        assertEquals("genesis_plus_gx", Caps.value("tag=GPGX,core=genesis_plus_gx", "core"))
        for ((caps, key) in listOf("dcbios=abc" to "core", "dcbios=abc" to "tag", "xcore=gpsp" to "core", "core=" to "core", "core" to "core", "" to "core")) {
            assertNull(Caps.value(caps, key), "$key of '$caps'")
        }
        assertNull(Caps.value(null, "core"))
    }

    @Test fun core_mismatch_needs_both_cores_to_name_one() {
        assertTrue(Caps.coreMismatch("core=gpsp,tag=GBA", "core=mgba,tag=MGBA"))
        assertTrue(Caps.coreMismatch("core=picodrive,tag=MD", "core=genesis_plus_gx,tag=GPGX"))
        assertTrue(Caps.coreMismatch("core=snes9x,tag=SFC", "tag=SUPA,core=mednafen_supafaust"))
        assertFalse(Caps.coreMismatch("core=gpsp,tag=GBA", "core=gpsp,tag=GBA"))
        assertFalse(Caps.coreMismatch("core=picodrive,tag=MD", "core=picodrive,tag=GG"))
        assertFalse(Caps.coreMismatch("core=mgba,tag=MGBA", ""))
        assertFalse(Caps.coreMismatch("", "core=mgba,tag=MGBA"))
        assertFalse(Caps.coreMismatch("core=mgba", null))
        assertFalse(Caps.coreMismatch(null, null))
        assertFalse(Caps.coreMismatch("tag=MD", "tag=GPGX"))
        assertFalse(Caps.coreMismatch("dcbios=a", "dcbios=b"))
    }

    @Test fun the_phone_sends_its_core_and_folder_tag() {
        assertEquals("core=snes9x,tag=SFC", Caps.of("snes9x", "SFC"))
        assertEquals("core=genesis_plus_gx,tag=GPGX", Caps.of("genesis_plus_gx", "gpgx"))
        assertEquals("", Caps.of("snes9x", "My Tag"))                 // breaks the charset: no caps
        assertEquals("", Caps.of("x".repeat(50), "SFCSFCSFC"))         // 69 chars: no caps
    }

    @Test fun the_gba_link_cores_add_the_games_wifi_mode() {
        assertEquals("core=gpsp,tag=GBA,wl=rfu", Caps.of("gpsp", "GBA", wl = "rfu"))
        assertEquals("core=mgba,tag=MGBA,wl=none,auto=1", Caps.of("mgba", "MGBA", auto = true, wl = "none"))
        assertEquals("core=snes9x,tag=SFC", Caps.of("snes9x", "SFC", wl = "rfu"))   // only gpSP and mGBA say it
        assertEquals("core=gpsp,tag=GBA", Caps.of("gpsp", "GBA"))
        val h = HelloCodec.parse("HELLO 2 $kart client caps=core=mgba,tag=MGBA,wl=poke")!!
        assertEquals("poke", h.wl)
        assertEquals(null, HelloCodec.parse("HELLO 2 $kart client caps=core=mgba,tag=MGBA")!!.wl)
    }

    @Test fun a_joiner_on_auto_adds_auto_1_when_it_fits() {
        assertEquals("core=gpsp,tag=GBA,auto=1", Caps.of("gpsp", "GBA", auto = true))
        assertEquals("core=gpsp,tag=GBA", Caps.of("gpsp", "GBA", auto = false))
        val long = "x".repeat(48)
        assertEquals("core=$long,tag=SFC", Caps.of(long, "SFC", auto = true))   // 61 chars fit, 68 would not: auto dropped
        assertEquals("", Caps.of("snes9x", "My Tag", auto = true))
        val h = HelloCodec.parse("HELLO 2 $kart client caps=core=gpsp,tag=GBA,auto=1")!!
        assertTrue(h.auto)
        assertEquals("gpsp", h.core)
        assertEquals("GBA", h.tag)
        assertFalse(HelloCodec.parse("HELLO 2 $kart client caps=core=gpsp,tag=GBA")!!.auto)
        assertFalse(HelloCodec.parse("HELLO 2 $kart client caps=core=gpsp,tag=GBA,auto=0")!!.auto)
        assertFalse(HelloCodec.parse("HELLO 2 $kart client")!!.auto)
    }

    @Test fun hello_lines_parse_like_the_handheld() {
        assertEquals(Hello(2, "Super Mario Kart (USA)", "client"), HelloCodec.parse("HELLO 2 $kart client"))
        assertEquals(Hello(2, "Super Mario Kart (USA)", "client", caps = "core=snes9x,tag=SFC"), HelloCodec.parse("HELLO 2 $kart client caps=core=snes9x,tag=SFC"))
        assertEquals(Hello(2, "Super Mario Kart (USA)", "host", player = 2), HelloCodec.parse("HELLO 2 $kart host 2"))
        val any = HelloCodec.parse("HELLO 2 g client any caps=core=picodrive,tag=MD")!!
        assertTrue(any.any)
        assertEquals("picodrive", any.core)
        assertEquals("MD", any.tag)
        assertEquals(2, HelloCodec.parse("HELLO 2 g host")!!.player)                 // missing: 2
        assertEquals(2, HelloCodec.parse("HELLO 2 g host 0")!!.player)               // 0: 2
        assertEquals(3, HelloCodec.parse("HELLO 2 g host 3")!!.player)
        assertEquals(2, HelloCodec.parse("HELLO 2 g host - caps=core=x")!!.player)   // stage 1's "-" is harmless
        assertEquals("core=x", HelloCodec.parse("HELLO 2 g host - caps=core=x")!!.caps)
        assertEquals(1, HelloCodec.parse("HELLO 1 g client")!!.version)
        assertEquals(Hello(2, "g", "client"), HelloCodec.parse("HELLO 2 g client\r"))
        for (bad in listOf("HELLO 2 g", "GOODBYE 2 g client", "HELLO x g client", "")) assertNull(HelloCodec.parse(bad), bad)
    }

    @Test fun hello_lines_format_like_the_handheld() {
        assertEquals("HELLO 2 $kart client caps=core=snes9x,tag=SFC", HelloCodec.format(Hello.client("Super Mario Kart (USA)", false, "core=snes9x,tag=SFC")))
        assertEquals("HELLO 2 g client any caps=core=snes9x,tag=SFC", HelloCodec.format(Hello.client("g", true, "core=snes9x,tag=SFC")))
        assertEquals("HELLO 2 g client", HelloCodec.format(Hello.client("g", false, "")))
        assertEquals("HELLO 2 g host 2 caps=core=picodrive,tag=MD", HelloCodec.format(Hello.host("g", 2, "core=picodrive,tag=MD")))
        assertEquals("HELLO 2 g host 2", HelloCodec.format(Hello.host("g", 2, "bad token")))
    }

    @Test fun gates_run_version_game_role_in_order() {
        val h = HelloCodec.parse("HELLO 1 Other host 2")!!
        assertEquals(RejectReason.VERSION, HelloCodec.mismatch(h, "Super Mario Kart", Hello.CLIENT, any = false))
        assertEquals(RejectReason.GAME, HelloCodec.mismatch(h.copy(version = 2), "Super Mario Kart", Hello.CLIENT, any = false))
        assertEquals(RejectReason.ROLE, HelloCodec.mismatch(h.copy(version = 2), "Super Mario Kart", Hello.CLIENT, any = true))
        assertNull(HelloCodec.mismatch(h.copy(version = 2, game = "super mario kart"), "Super Mario Kart (USA)", Hello.HOST, any = false))
    }

    @Test fun core_reasons_fit_31_chars() {
        assertEquals("REJECT core-gpsp.GBA", Reject(RejectReason.CORE, "gpsp", "GBA").format())
        assertEquals("REJECT core-mednafen_supafaust.SUPA", Reject(RejectReason.CORE, "mednafen_supafaust", "SUPA").format())
        assertEquals("REJECT core-swanstation", Reject(RejectReason.CORE, "swanstation", null).format())
        assertEquals("REJECT core-picodrive", Reject(RejectReason.CORE, "picodrive", "AVERYLONGCUSTOMFOLDERNAME").format())
        assertEquals("REJECT core", Reject(RejectReason.CORE, "an_extremely_long_core_name_x", null).format())
        assertEquals("REJECT core", Reject(RejectReason.CORE).format())
        assertEquals("REJECT version", Reject(RejectReason.VERSION).format())
        assertEquals("REJECT game", Reject(RejectReason.GAME).format())
        assertEquals("REJECT role", Reject(RejectReason.ROLE).format())
        assertEquals("REJECT sync", Reject(RejectReason.SYNC).format())
        assertEquals("REJECT link-gpsp.rfu.poke", Reject(RejectReason.LINK, "gpsp", wl = "rfu", peerWl = "poke").format())
    }

    @Test fun rejects_parse_like_the_handheld() {
        assertEquals(Reject(RejectReason.CORE, "gpsp", "GBA"), Reject.parse("REJECT core-gpsp.GBA"))
        assertEquals(Reject(RejectReason.CORE, "mednafen_supafaust", "SUPA"), Reject.parse("REJECT core-mednafen_supafaust.SUPA"))
        assertEquals(Reject(RejectReason.CORE, "swanstation", null), Reject.parse("REJECT core-swanstation"))
        assertEquals(Reject(RejectReason.CORE), Reject.parse("REJECT core"))
        assertEquals(Reject(RejectReason.VERSION), Reject.parse("REJECT version"))
        assertEquals(Reject(RejectReason.SYNC), Reject.parse("REJECT sync\r"))
        assertEquals(Reject(RejectReason.OTHER), Reject.parse("REJECT weird"))
        assertEquals(Reject(RejectReason.OTHER), Reject.parse("REJECT"))
        assertEquals(Reject(RejectReason.OTHER), Reject.parse("REJECT core-"))
        // The handheld's link-mode reasons (wiz_caps.c WizCaps_linkReason, the same strings as its test_wiz_caps.c).
        assertEquals(Reject(RejectReason.LINK, "gpsp", wl = "rfu", peerWl = "poke"), Reject.parse("REJECT link-gpsp.rfu.poke"))
        assertEquals(Reject(RejectReason.LINK, "mgba", wl = "poke", peerWl = "poke"), Reject.parse("REJECT link-mgba.poke.poke"))
        for (bad in listOf("link-gpsp.rfu", "link-gpsp.rfu.none", "link-.rfu.poke", "link-gpsp.rfu.poke.x", "link-"))
            assertEquals(Reject(RejectReason.OTHER), Reject.parse("REJECT $bad"), bad)
        assertNull(Reject.parse("HELLO 2 g host 2"))
        // %31s: a longer token is cut at 31 chars before it is read
        assertEquals(Reject(RejectReason.CORE, "abcdefghijklmnopqrstuvwxyz", null), Reject.parse("REJECT core-abcdefghijklmnopqrstuvwxyz0123"))
    }
    @Test fun the_link_mode_reject_follows_the_handhelds_rule() {
        // The same vectors as nx-redux test_wiz_caps.c test_link_reason.
        fun reason(sc: String, sw: String?, rc: String?, rw: String?, usb: Boolean) = Reject.link(sc, sw, rc, rw, usb)?.format()
        assertEquals("REJECT link-gpsp.rfu.poke", reason("gpsp", "rfu", "mgba", "poke", false))
        assertEquals("REJECT link-mgba.poke.rfu", reason("mgba", "poke", "gpsp", "rfu", false))
        assertEquals("REJECT link-gpsp.rfu.poke", reason("gpsp", "rfu", "mgba", "poke", true))
        assertEquals("REJECT link-gpsp.aw1.aw2", reason("gpsp", "aw1", "mgba", "aw2", false))
        assertEquals("REJECT link-gpsp.poke.poke", reason("gpsp", "poke", "mgba", "poke", true))
        assertEquals("REJECT link-mgba.aw2.aw2", reason("mgba", "aw2", "gpsp", "aw2", true))
        assertNull(reason("gpsp", "poke", "mgba", "poke", false))
        assertNull(reason("gpsp", "rfu", "mgba", "rfu", true))
        assertNull(reason("gpsp", "rfu", "gpsp", "poke", false))
        assertNull(reason("gpsp", "rfu", "mgba", "none", true))
        assertNull(reason("gpsp", "none", "mgba", "none", true))
        assertNull(reason("gpsp", "rfu", "mgba", null, false))
        assertNull(reason("gpsp", "rfu", "mgba", "bogus", false))
        assertNull(reason("gpsp", "rfu", null, "poke", false))
        // A reason this side sends parses back to itself.
        val r = Reject.link("mgba", "poke", "gpsp", "rfu", false)!!
        assertEquals(r, Reject.parse(r.format()))
    }
}
