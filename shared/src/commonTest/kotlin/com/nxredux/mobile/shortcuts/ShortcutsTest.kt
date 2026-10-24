package com.nxredux.mobile.shortcuts

import com.nxredux.mobile.options.CoreConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShortcutsTest {
    private val r1 = ShortcutBinding(null, KeyCodes.BUTTON_R1)
    private val l1 = ShortcutBinding(null, KeyCodes.BUTTON_L1)
    private fun eff(base: ShortcutSet, o: ShortcutOverrides = ShortcutOverrides.NONE, tag: String = "GBA", rewind: Boolean = true) =
        Shortcuts.effective(base, o, ConsoleHotkeys.claimed(tag), rewind)

    @Test fun encodeDecodeRoundTrip() {
        for (b in listOf(ShortcutBinding(null, 103), ShortcutBinding(110, 103))) assertEquals(b, ShortcutBinding.decode(b.encode()))
        assertEquals("110+103", ShortcutBinding(110, 103).encode())
        assertNull(ShortcutBinding.decode("x"))
        assertNull(ShortcutBinding.decode(""))
        assertNull(ShortcutBinding.decode("1+2+3"))
    }

    // Only MENU and SELECT are modifiers: a stored "103+96" (R1 held + A) is garbage, not a combo nothing can press.
    @Test fun decodeOnlyTakesMenuOrSelectAsModifier() {
        assertEquals(ShortcutBinding(KeyCodes.BUTTON_SELECT, 96), ShortcutBinding.decode("109+96"))
        assertEquals(ShortcutBinding(KeyCodes.BUTTON_MODE, 96), ShortcutBinding.decode("110+96"))
        assertNull(ShortcutBinding.decode("103+96"))
        assertNull(ShortcutBinding.decode("0+96"))
    }

    @Test fun overrideInherits() {
        val e = eff(ShortcutSet(mapOf(ShortcutAction.TOGGLE_FF to r1), emptyMap()), ShortcutOverrides(mapOf(ShortcutAction.HOLD_FF to l1)))
        assertEquals(mapOf(ShortcutAction.TOGGLE_FF to r1, ShortcutAction.HOLD_FF to l1), e.bindings)
    }

    @Test fun noneOverrideClears() {
        val e = eff(ShortcutSet(mapOf(ShortcutAction.TOGGLE_FF to r1), emptyMap()), ShortcutOverrides(mapOf(ShortcutAction.TOGGLE_FF to null)))
        assertTrue(e.bindings.isEmpty())
    }

    @Test fun overrideStealsButton() {
        val e = eff(ShortcutSet(mapOf(ShortcutAction.TOGGLE_FF to r1), emptyMap()), ShortcutOverrides(mapOf(ShortcutAction.RESET to r1)))
        assertEquals(mapOf(ShortcutAction.RESET to r1), e.bindings)
    }

    @Test fun hotkeyBlocks() {
        val e = eff(ShortcutSet(mapOf(ShortcutAction.TOGGLE_FF to ShortcutBinding(null, KeyCodes.BUTTON_R2)), emptyMap()), tag = "NDS")
        assertEquals("R2 swaps the DS screens", e.blocked[ShortcutAction.TOGGLE_FF]?.note)
        assertFalse(ShortcutAction.TOGGLE_FF in e.bindings)
        // the same base fires elsewhere
        assertTrue(ShortcutAction.TOGGLE_FF in eff(ShortcutSet(mapOf(ShortcutAction.TOGGLE_FF to ShortcutBinding(null, KeyCodes.BUTTON_R2)), emptyMap())).bindings)
    }

    @Test fun rewindDroppedWithoutRewind() {
        val base = ShortcutSet(
            mapOf(ShortcutAction.TOGGLE_REWIND to r1, ShortcutAction.HOLD_REWIND to l1, ShortcutAction.RESET to ShortcutBinding(null, KeyCodes.BUTTON_X)),
            mapOf(TouchSlot.ONE to ShortcutAction.HOLD_REWIND, TouchSlot.TWO to ShortcutAction.RESET),
        )
        val e = eff(base, rewind = false)
        assertEquals(setOf(ShortcutAction.RESET), e.bindings.keys)
        assertEquals(mapOf(TouchSlot.TWO to ShortcutAction.RESET), e.touch)
        assertEquals(2, eff(base).bindings.size - 1)
    }

    @Test fun touchOverrides() {
        val base = ShortcutSet(emptyMap(), mapOf(TouchSlot.ONE to ShortcutAction.RESET, TouchSlot.TWO to ShortcutAction.HOLD_FF))
        val e = eff(base, ShortcutOverrides(touch = mapOf(TouchSlot.ONE to null, TouchSlot.TWO to ShortcutAction.TOGGLE_FF)))
        assertEquals(mapOf(TouchSlot.TWO to ShortcutAction.TOGGLE_FF), e.touch)
    }

    @Test fun configRoundTrip() {
        val o = ShortcutOverrides(
            mapOf(ShortcutAction.TOGGLE_FF to ShortcutBinding(110, 103), ShortcutAction.RESET to r1),
            mapOf(TouchSlot.ONE to ShortcutAction.HOLD_FF),
        )
        val cfg = Shortcuts.toConfig(o)
        assertTrue("nx_bind_toggle_ff" to "110+103" in cfg)
        assertTrue("nx_bind_touch_1" to "hold_ff" in cfg)
        assertEquals(o, Shortcuts.fromConfig(cfg.toMap()))
    }

    @Test fun noneSurvivesConfig() {
        val o = ShortcutOverrides(mapOf(ShortcutAction.HOLD_FF to null), mapOf(TouchSlot.TWO to null))
        val cfg = Shortcuts.toConfig(o)
        assertTrue("nx_bind_hold_ff" to "none" in cfg)
        assertEquals(o, Shortcuts.fromConfig(cfg.toMap()))
        assertEquals(ShortcutOverrides.NONE, Shortcuts.fromConfig(mapOf("nx_bind_hold_ff" to "junk", "nx_bind_touch_1" to "")))
    }

    @Test fun baseRoundTrip() {
        val s = ShortcutSet(
            mapOf(ShortcutAction.TOGGLE_FF to ShortcutBinding(110, 103), ShortcutAction.RESET to ShortcutBinding(null, 188)),
            mapOf(TouchSlot.TWO to ShortcutAction.HOLD_REWIND),
        )
        assertEquals(s, Shortcuts.decodeBase(Shortcuts.encodeBase(s)))
        assertEquals(ShortcutSet.EMPTY, Shortcuts.decodeBase(""))
        assertEquals(ShortcutSet.EMPTY, Shortcuts.decodeBase("garbage;x=1"))
    }

    @Test fun labels() {
        assertEquals("MENU+R1", KeyLabels.label(ShortcutBinding(110, 103)))
        assertEquals("SELECT+X", KeyLabels.label(ShortcutBinding(109, KeyCodes.BUTTON_X)))
        assertEquals("Button 1", KeyLabels.label(ShortcutBinding(null, 188)))
        assertEquals("Button 16", KeyLabels.label(ShortcutBinding(null, 203)))
        assertEquals("L3", KeyLabels.label(ShortcutBinding(null, 106)))
        assertEquals("Up", KeyLabels.label(ShortcutBinding(null, 19)))
        assertEquals("Key 312", KeyLabels.label(ShortcutBinding(null, 312)))
    }

    // One button, one action: a binding another action had moves, and the map stays in action order.
    @Test fun withBindingMovesADuplicateAndKeepsOrder() {
        val s = ShortcutSet(mapOf(ShortcutAction.RESET to r1), emptyMap())
            .withBinding(ShortcutAction.TOGGLE_FF, l1)
            .withBinding(ShortcutAction.HOLD_FF, r1)
        assertEquals(mapOf(ShortcutAction.TOGGLE_FF to l1, ShortcutAction.HOLD_FF to r1), s.bindings)
        assertEquals(listOf(ShortcutAction.TOGGLE_FF, ShortcutAction.HOLD_FF), s.bindings.keys.toList())
        assertEquals(mapOf(ShortcutAction.HOLD_FF to r1), s.withBinding(ShortcutAction.TOGGLE_FF, null).bindings)
    }

    @Test fun withTouchSetsAndClearsInSlotOrder() {
        val s = ShortcutSet.EMPTY.withTouch(TouchSlot.TWO, ShortcutAction.RESET).withTouch(TouchSlot.ONE, ShortcutAction.HOLD_FF)
        assertEquals(listOf(TouchSlot.ONE, TouchSlot.TWO), s.touch.keys.toList())
        assertEquals(mapOf(TouchSlot.TWO to ShortcutAction.RESET), s.withTouch(TouchSlot.ONE, null).touch)
    }

    @Test fun aSaveDropsTheBindLinesNoLongerOverridden() {
        val existing = CoreConfig.parse("nx_bind_toggle_ff = 103\nnx_bind_hold_ff = none\nnx_bind_touch_1 = reset\nfoo = 1\n")
        val o = ShortcutOverrides(mapOf(ShortcutAction.HOLD_FF to ShortcutBinding(null, 102)))
        val text = CoreConfig.render(existing, Shortcuts.toConfig(o), drop = Shortcuts.droppedOnSave(o))
        assertEquals("nx_bind_hold_ff = 102\nfoo = 1\n", text)
        assertEquals("foo = 1\n", CoreConfig.render(existing, emptyList(), drop = Shortcuts.droppedOnSave(ShortcutOverrides.NONE)))
    }
}
