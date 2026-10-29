package com.nxredux.mobile.shortcuts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Per-model SELECT / START / MENU bindings: scan-code keys, the swap rule, translation and storage. */
class SystemButtonsTest {
    private val m = "4001:0428"   // AYANEO Pocket Air Mini's pad
    private val ayaMenu = PhysicalKey.of(0, 0x2f1)

    @Test fun modelKeyIsHexVendorProduct() {
        assertEquals(m, PadModel(0x4001, 0x0428, "AYANEO Controller").key)
    }

    @Test fun standardKeysAreTheirRoles() {
        assertEquals(SystemRole.MENU, SystemButtons.roleOf(SystemButtonMap(), m, PhysicalKey.of(110, 0)))
        assertNull(SystemButtons.translate(SystemButtonMap(), m, 110, 0))
    }

    @Test fun unknownKeyBindsByScanCode() {
        assertEquals(PhysicalKey(0, 0x2f1), ayaMenu)
        val map = SystemButtons.bind(SystemButtonMap(), m, SystemRole.MENU, ayaMenu)
        assertEquals(110, SystemButtons.translate(map, m, 0, 0x2f1))
        assertNull(SystemButtons.translate(map, m, 0, 0x2f2))
        assertNull(SystemButtons.translate(map, "0000:0000", 0, 0x2f1))
    }

    @Test fun keyCodeIgnoresScanCode() {
        assertEquals(PhysicalKey(106, 0), PhysicalKey.of(106, 317))
        val map = SystemButtons.bind(SystemButtonMap(), m, SystemRole.MENU, PhysicalKey.of(106, 0))
        assertEquals(110, SystemButtons.translate(map, m, 106, 999))
    }

    @Test fun boundButtonLosesOldJob() {
        val map = SystemButtons.bind(SystemButtonMap(), m, SystemRole.MENU, PhysicalKey.of(106, 0))
        assertEquals(110, SystemButtons.translate(map, m, 106, 0))
    }

    @Test fun bindingAnotherRolesKeySwaps() {
        val map = SystemButtons.bind(SystemButtonMap(), m, SystemRole.SELECT, PhysicalKey.of(108, 0))
        assertEquals(SystemRole.SELECT, SystemButtons.roleOf(map, m, PhysicalKey.of(108, 0)))
        assertEquals(PhysicalKey(109, 0), SystemButtons.keyOf(map, m, SystemRole.START))
        assertEquals(109, SystemButtons.translate(map, m, 108, 0))
        assertEquals(108, SystemButtons.translate(map, m, 109, 0))
    }

    @Test fun rebindingSameRoleReplacesOld() {
        val l3 = SystemButtons.bind(SystemButtonMap(), m, SystemRole.MENU, PhysicalKey.of(106, 0))
        val map = SystemButtons.bind(l3, m, SystemRole.MENU, PhysicalKey.of(107, 0))
        assertNull(SystemButtons.translate(map, m, 106, 0))
        assertEquals(110, SystemButtons.translate(map, m, 107, 0))
    }

    @Test fun bindingTheStandardKeyBackDropsTheOverride() {
        val bound = SystemButtons.bind(SystemButtonMap(), m, SystemRole.MENU, ayaMenu)
        val map = SystemButtons.bind(bound, m, SystemRole.MENU, PhysicalKey.of(110, 0))
        assertEquals(SystemButtonMap(), map)
    }

    @Test fun resetKeepsAsked() {
        val map = SystemButtons.reset(SystemButtons.markAsked(SystemButtons.bind(SystemButtonMap(), m, SystemRole.MENU, ayaMenu), m), m)
        assertNull(map.bound[m])
        assertTrue(m in map.asked)
    }

    @Test fun encodeDecodeRoundTrips() {
        var map = SystemButtons.bind(SystemButtonMap(), m, SystemRole.MENU, ayaMenu)
        map = SystemButtons.bind(map, m, SystemRole.SELECT, PhysicalKey.of(106, 0))
        map = SystemButtons.bind(map, "045e:028e", SystemRole.START, PhysicalKey.of(188, 0))
        map = SystemButtons.markAsked(map, "057e:2009")
        assertEquals(map, SystemButtons.decode(SystemButtons.encode(map)))
        assertEquals(SystemButtonMap(), SystemButtons.decode(""))
        assertEquals(SystemButtonMap(), SystemButtons.decode("nonsense"))
    }
}
