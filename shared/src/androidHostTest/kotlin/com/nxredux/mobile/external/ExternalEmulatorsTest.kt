package com.nxredux.mobile.external

import com.nxredux.mobile.runner.CoreRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExternalEmulatorsTest {
    private fun offered(e: ExternalEmulator, real: Boolean = true, zip: Boolean = false, naomi: Boolean = false, model3: Boolean = false) =
        ExternalEmulators.offered(e, real, zip, naomi, model3)

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
        assertEquals(listOf("md-emu", "pizza-boy-sc", "pizza-boy-sc-pro", "mastergear"), ExternalEmulators.forTag("GPGX_SMS").map { it.id })
        assertTrue(ExternalEmulators.forTag("VB").isEmpty())
    }

    @Test
    fun everySpecAppIsListed() {
        val spec = setOf(
            // New consoles
            "eden", "eden-nightly", "kenji-nx", "cemu", "aps3e", "armsx3", "emucorec", "yaba-sanshiro", "saturn-emu",
            "x1-box", "hakux", "ax360e", "xendroid", "mame4droid", "m64plus-fz", "mupen64plus-ae", "pce-emu",
            // Optional emulators; Pizza Boy's paid builds use other activity classes, so they are entries of their own
            "melonds", "melonds-nightly", "noods", "skyemu", "watermelonds", "seedlessds",
            "azahar", "azaharplus", "panda3ds", "dolphin",
            "gbc-emu", "my-oldboy", "linkboy", "pizza-boy-c", "pizza-boy-c-pro",
            "gba-emu", "my-boy", "pizza-boy-a", "pizza-boy-a-pro",
            "nes-emu", "ines", "snes9x-explus", "duckstation", "armsx1", "epsxe", "armsx2", "emucorex", "play", "ppsspp",
            "flycast-dc", "redream", "md-emu", "pizza-boy-sc", "pizza-boy-sc-pro", "mastergear", "ngp-emu", "swan-emu",
            "lynx-emu", "a2600-emu", "colem", "msx-emu", "neo-emu", "flycast-arcade", "super3",
            "pixl8", "infinity", "pico8-android", "idtech4a",
        )
        assertEquals(spec, ExternalEmulators.all.map { it.id }.toSet())
    }

    @Test
    fun newConsoleDefaultOrder() {
        assertEquals(listOf("eden", "eden-nightly", "kenji-nx"), ExternalEmulators.forTag("SWITCH").map { it.id })
        assertEquals(listOf("yaba-sanshiro", "saturn-emu"), ExternalEmulators.forTag("SATURN").map { it.id })
        assertEquals(listOf("aps3e", "armsx3", "emucorec"), ExternalEmulators.forTag("PS3").map { it.id })
        assertEquals(listOf("m64plus-fz", "mupen64plus-ae"), ExternalEmulators.forTag("N64DD").map { it.id })
        assertEquals(listOf("pce-emu"), ExternalEmulators.forTag("SGX").map { it.id })
    }

    @Test
    fun optionalAppsInTheSpecsOrderPerConsole() {
        fun ids(tag: String) = ExternalEmulators.forTag(tag).map { it.id }
        assertEquals(listOf("melonds", "melonds-nightly", "noods", "skyemu", "watermelonds", "seedlessds"), ids("NDS"))
        assertEquals(listOf("gbc-emu", "my-oldboy", "linkboy", "pizza-boy-c", "pizza-boy-c-pro", "skyemu"), ids("GB"))
        assertEquals(listOf("gba-emu", "my-boy", "linkboy", "pizza-boy-a", "pizza-boy-a-pro", "noods", "skyemu"), ids("MGBA"))
        assertEquals(listOf("azahar", "azaharplus", "panda3ds"), ids("3DS"))
        assertEquals(listOf("duckstation", "armsx1", "epsxe"), ids("PSX"))
        assertEquals(listOf("armsx2", "emucorex", "play"), ids("PS2"))
        assertEquals(listOf("flycast-dc", "redream"), ids("DC"))
        assertEquals(listOf("md-emu", "pizza-boy-sc", "pizza-boy-sc-pro"), ids("GPGX_SEGACD"))
        assertEquals(listOf("pizza-boy-sc", "pizza-boy-sc-pro", "mastergear"), ids("GG"))
        assertEquals(listOf("mastergear"), ids("SG1000"))
        assertEquals(listOf("mame4droid", "neo-emu", "flycast-arcade", "super3"), ids("FBN"))
        assertEquals(listOf("pixl8", "infinity", "pico8-android"), ids("P8"))
        assertEquals(listOf("idtech4a"), ids("PRBOOM"))
    }

    @Test
    fun anAppCoveringAConsoleListsEveryTagOfIt() {
        ExternalEmulators.all.forEach { e ->
            e.tags.forEach { tag ->
                val console = assertNotNull(CoreRegistry.consoleOf(tag), "${e.id}: $tag")
                assertTrue(e.tags.containsAll(console.tags), "${e.id} lists $tag but not all of ${console.tags}")
            }
        }
        assertEquals(setOf("PS", "PSX"), ExternalEmulators.byId("duckstation")!!.tags)
    }

    @Test
    fun currentPackageIdsFreeFirst() {
        assertEquals(listOf("io.github.lime3ds.android", "org.azahar_emu.azahar"), ExternalEmulators.byId("azahar")!!.packages)
        assertEquals(listOf("com.PceEmu"), ExternalEmulators.byId("pce-emu")!!.packages)
        assertEquals(listOf("org.ppsspp.ppsspp", "org.ppsspp.ppssppgold"), ExternalEmulators.byId("ppsspp")!!.packages)
        assertEquals(listOf("aenu.ax360e.free", "aenu.ax360e"), ExternalEmulators.byId("ax360e")!!.packages)
        assertEquals(listOf("it.dbtecno.pizzaboy"), ExternalEmulators.byId("pizza-boy-c")!!.packages)
        assertEquals(listOf("it.dbtecno.pizzaboypro"), ExternalEmulators.byId("pizza-boy-c-pro")!!.packages)
    }

    @Test
    fun arcadeSetRules() {
        assertEquals(SetRule.ALL, ExternalEmulators.byId("mame4droid")!!.sets)
        assertEquals(SetRule.ALL, ExternalEmulators.byId("neo-emu")!!.sets)
        assertEquals(SetRule.MODEL3, ExternalEmulators.byId("super3")!!.sets)
        assertTrue(ExternalEmulators.isModel3Set("scud"))
        assertTrue(ExternalEmulators.isModel3Set("VF3"))
        assertFalse(ExternalEmulators.isModel3Set("daytona"))
        assertFalse(ExternalEmulators.isModel3Set("sf2"))
    }

    @Test
    fun onlyIdTechReadsACommandsSidecar() {
        assertEquals(listOf("idtech4a"), ExternalEmulators.all.filter { it.commandsSidecar }.map { it.id })
        assertEquals(mapOf("game" to "zdoom"), ExternalEmulators.byId("idtech4a")!!.extras)
    }

    @Test
    fun nothingIsCheckedOnADeviceYet() {
        ExternalEmulators.all.forEach { assertFalse(it.checked, it.id) }
    }

    @Test
    fun flycastArcadeIsASeparateNaomiOnlyEntry() {
        assertEquals(listOf("flycast-dc", "redream"), ExternalEmulators.forTag("DC").map { it.id })
        val arcade = ExternalEmulators.byId("flycast-arcade")!!
        assertEquals(setOf("FBN"), arcade.tags)
        assertEquals(SetRule.NAOMI, arcade.sets)
        assertEquals(ExternalEmulators.byId("flycast-dc")!!.packages, arcade.packages)
        assertFalse(offered(arcade))
        assertTrue(offered(arcade, naomi = true))
    }

    @Test
    fun launchActionAndCategories() {
        val dolphin = ExternalEmulators.byId("dolphin")!!
        assertEquals("android.intent.action.MAIN", dolphin.action)
        assertEquals(listOf("android.intent.category.LEANBACK_LAUNCHER"), dolphin.categories)
        assertEquals("android.nfc.action.TECH_DISCOVERED", ExternalEmulators.byId("eden")!!.action)
        assertEquals("android.intent.action.VIEW", ExternalEmulators.byId("flycast-dc")!!.action)
    }

    @Test
    fun actionDefaultsToView() {
        val r = recipe(GamePass.VIEW_DATA)
        assertEquals("android.intent.action.VIEW", r.action)
        assertTrue(r.categories.isEmpty())
    }

    @Test
    fun byIdFindsOrNull() {
        assertEquals("eden", ExternalEmulators.byId("eden")?.id)
        assertEquals(null, ExternalEmulators.byId("nope"))
    }
}
