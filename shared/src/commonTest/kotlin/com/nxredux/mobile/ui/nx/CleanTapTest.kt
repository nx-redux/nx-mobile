package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CleanTapTest {
    @Test fun a_quick_still_lift_is_a_tap() = assertTrue(isCleanTap(80, 2f, consumed = false, timeoutMs = 400, slopPx = 8f))
    @Test fun a_finger_held_too_long_is_not() = assertFalse(isCleanTap(900, 0f, consumed = false, timeoutMs = 400, slopPx = 8f))
    @Test fun a_consumed_gesture_is_not() = assertFalse(isCleanTap(80, 0f, consumed = true, timeoutMs = 400, slopPx = 8f))
    @Test fun a_drag_is_not() = assertFalse(isCleanTap(80, 30f, consumed = false, timeoutMs = 400, slopPx = 8f))
}
