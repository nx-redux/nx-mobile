package com.nxredux.mobile.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class OnePerNameTest {
    @Test fun sameNameGivesTheSameInstance() {
        var made = 0
        val cache = OnePerName { name: String -> made++; StringBuilder(name) }
        val first = cache["app"]
        assertSame(first, cache["app"])
        assertNotSame(first, cache["netplay"])
        assertEquals(2, made)
    }
}
