package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals

/** The portrait top section takes the body up to its cap, and a sliver past it rather than showing a sliver of pins. */
class HomeLayoutPortraitTopTest {
    @Test fun aShortBodyIsAllTopSection() = assertEquals(200f, homePortraitTop(body = 200f, cap = 235f, pinH = 120f))
    @Test fun lessThanHalfAPinRowPastTheCapGoesToTheTop() = assertEquals(270f, homePortraitTop(body = 270f, cap = 235f, pinH = 120f))
    @Test fun halfAPinRowOrMoreLeavesTheCap() = assertEquals(235f, homePortraitTop(body = 300f, cap = 235f, pinH = 120f))
}
