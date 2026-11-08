package com.nxredux.mobile.options

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The right analog's job in Azahar: out of stylus mode the C-stick only (its default also moved the core's touch pointer,
 * which in a game without the C-stick was all the nub did); in stylus mode the pointer too, as stylus mode steers it.
 */
class N3dsAnalogFunctionTest {
    @Test fun the_c_stick_only_out_of_stylus_mode() {
        assertEquals("citra_analog_function", N3dsOptions.ANALOG_FUNCTION)
        assertEquals("c_stick", N3dsOptions.analogFunction(stylus = false))
    }

    @Test fun the_pointer_too_in_stylus_mode() {
        assertEquals("c_stick_and_touchscreen", N3dsOptions.analogFunction(stylus = true))
    }
}
