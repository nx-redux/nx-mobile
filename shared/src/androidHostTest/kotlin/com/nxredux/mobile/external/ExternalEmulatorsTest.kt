package com.nxredux.mobile.external

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExternalEmulatorsTest {
    private fun offered(e: ExternalEmulator, real: Boolean = true, zip: Boolean = false, naomi: Boolean = false, model3: Boolean = false) =
        ExternalEmulators.offered(e, "game.bin", real, zip, naomi, model3)

    private fun recipe(pass: GamePass, sets: SetRule = SetRule.ALL, readsZip: Boolean = false) = ExternalEmulator(
        id = "x", name = "X", packages = listOf("a.b"), activity = "a.b.Main", pass = pass,
        extraKey = if (pass == GamePass.VIEW_DATA) null else "k", tags = setOf("GC"), sets = sets, readsZip = readsZip,
    )

    @Test
    fun everyRecipeHasAPackageAndAnActivity() {
        val all = ExternalEmulators.all
        assertTrue(all.isNotEmpty())
        all.forEach {
            assertTrue(it.packages.isNotEmpty(), it.id)
            assertTrue('.' in it.activity, it.id)
        }
        assertEquals(all.size, all.map { it.id }.toSet().size)
    }

    @Test
    fun noSpoofedIds() {
        assertEquals(setOf("com.miHoYo.Yuanshen", "com.miHoYo.Yuanshen.nightly", "com.antutu.ABenchMark"), ExternalEmulators.SPOOFED)
        ExternalEmulators.all.forEach { assertTrue(it.packages.none { p -> p in ExternalEmulators.SPOOFED }, it.id) }
    }

    @Test
    fun extraStylesNeedAKey() {
        ExternalEmulators.all.filter { it.pass != GamePass.VIEW_DATA }.forEach { assertNotNull(it.extraKey, it.id) }
    }

    @Test
    fun takesUriExceptForPathExtra() {
        assertTrue(recipe(GamePass.VIEW_DATA).takesUri)
        assertTrue(recipe(GamePass.URI_EXTRA).takesUri)
        assertFalse(recipe(GamePass.PATH_EXTRA).takesUri)
    }

    @Test
    fun pathOnlyNotOfferedForSafOrZip() {
        val path = recipe(GamePass.PATH_EXTRA)
        assertTrue(offered(path))
        assertFalse(offered(path, real = false))
        assertFalse(offered(path, zip = true))
        assertTrue(offered(recipe(GamePass.PATH_EXTRA, readsZip = true), zip = true))
        val uri = recipe(GamePass.URI_EXTRA)
        assertTrue(offered(uri, real = false))
        assertTrue(offered(uri, zip = true))
    }

    @Test
    fun setRulesFilter() {
        val all = recipe(GamePass.VIEW_DATA)
        val naomi = recipe(GamePass.VIEW_DATA, SetRule.NAOMI)
        val model3 = recipe(GamePass.VIEW_DATA, SetRule.MODEL3)
        assertTrue(offered(all))
        assertFalse(offered(naomi))
        assertTrue(offered(naomi, naomi = true))
        assertFalse(offered(naomi, model3 = true))
        assertFalse(offered(model3))
        assertTrue(offered(model3, model3 = true))
        assertFalse(offered(model3, naomi = true))
    }

    @Test
    fun forTagMatchesBaseTagInTableOrder() {
        assertEquals(listOf("dolphin"), ExternalEmulators.forTag("gc").map { it.id })
        assertEquals(listOf("dolphin"), ExternalEmulators.forTag("WII").map { it.id })
        assertEquals(listOf("eden"), ExternalEmulators.forTag("SWITCH").map { it.id })
        assertTrue(ExternalEmulators.forTag("GBA").isEmpty())
    }

    @Test
    fun flycastArcadeIsASeparateNaomiOnlyEntry() {
        assertEquals(listOf("flycast-dc"), ExternalEmulators.forTag("DC").map { it.id })
        val arcade = ExternalEmulators.forTag("FBN").single()
        assertEquals("flycast-arcade", arcade.id)
        assertEquals(SetRule.NAOMI, arcade.sets)
        assertEquals(ExternalEmulators.byId("flycast-dc")!!.packages, arcade.packages)
        assertFalse(offered(arcade))
        assertTrue(offered(arcade, naomi = true))
    }

    @Test
    fun byIdFindsOrNull() {
        assertEquals("eden", ExternalEmulators.byId("eden")?.id)
        assertEquals(null, ExternalEmulators.byId("nope"))
    }
}
