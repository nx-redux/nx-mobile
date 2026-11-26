package com.nxredux.mobile.settings

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackBooleanTest {
    private fun prefs(data: Map<String, Boolean>): SharedPreferences =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "contains" -> data.containsKey(args[0])
                "getBoolean" -> data[args[0]] ?: args[1]
                else -> error(method.name)
            }
        } as SharedPreferences

    @Test fun anUnwrittenKeyFollowsTheFallbackKey() {
        assertTrue(prefs(mapOf("old" to true)).booleanOrFallback("new", "old"))
        assertFalse(prefs(mapOf("old" to false)).booleanOrFallback("new", "old"))
        assertFalse(prefs(emptyMap()).booleanOrFallback("new", "old"))
    }

    @Test fun aWrittenKeyStandsAlone() {
        assertFalse(prefs(mapOf("new" to false, "old" to true)).booleanOrFallback("new", "old"))
        assertTrue(prefs(mapOf("new" to true, "old" to false)).booleanOrFallback("new", "old"))
    }
}
