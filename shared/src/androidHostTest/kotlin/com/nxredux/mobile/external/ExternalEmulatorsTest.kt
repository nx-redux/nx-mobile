package com.nxredux.mobile.external

import com.nxredux.mobile.runner.CoreRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExternalEmulatorsTest {
    private fun offered(
        e: ExternalEmulator, real: Boolean = true, zip: Boolean = false, naomi: Boolean = false, model3: Boolean = false,
        ext: String = "iso", commands: Boolean = false,
    ) = ExternalEmulators.offered(e, real, zip, naomi, model3, ext, commands)

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
    fun sheetsAndPlaylistsOnlyForPathApps() {
        val uri = recipe(GamePass.URI_EXTRA)
        val path = recipe(GamePass.PATH_EXTRA)
        for (ext in listOf("cue", "m3u", "gdi", "ccd", "toc", "mds")) {
            assertFalse(offered(uri, ext = ext), ext)
            assertTrue(offered(path, ext = ext), ext)
        }
        assertTrue(offered(uri, ext = "chd"))
        val mame = ExternalEmulators.byId("mame4droid-neocd")!!
        assertTrue(offered(mame, ext = "cue"))
    }

    @Test
    fun extensionFilterLimitsARecipe() {
        val vita = ExternalEmulators.byId("vita3k")!!
        assertTrue(offered(vita, real = false, ext = "psvita"))
        assertFalse(offered(vita, ext = "vpk"))
        assertFalse(offered(ExternalEmulators.byId("emucorev")!!, ext = "vpk"))
        val ps4 = ExternalEmulators.byId("bachatas4")!!
        assertTrue(offered(ps4, real = false, ext = "ps4"))
        assertFalse(offered(ps4, ext = "pkg"))
    }

    @Test
    fun aSidecarAppNeedsItsSidecar() {
        val idTech = ExternalEmulators.byId("idtech4a")!!
        assertFalse(offered(idTech, ext = "wad"))
        assertTrue(offered(idTech, ext = "wad", commands = true))
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
            "vita3k", "emucorev", "bachatas4", "x1-box", "hakux", "ax360e", "xendroid",
            "mame4droid-jaguar", "mame4droid-neocd", "mame4droid-cdi", "mame4droid", "mame4droid-a7800",
            "m64plus-fz", "mupen64plus-ae", "pce-emu",
            // Optional emulators; Pizza Boy's paid builds use other activity classes, so they are entries of their own
            "melonds", "melonds-nightly", "noods", "skyemu", "watermelonds", "seedlessds",
            "azahar", "azaharplus", "dolphin",
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
        assertEquals(listOf("vita3k", "emucorev"), ExternalEmulators.forTag("PSVITA").map { it.id })
        assertEquals(listOf("bachatas4"), ExternalEmulators.forTag("PS4").map { it.id })
        assertEquals(listOf("mame4droid-jaguar"), ExternalEmulators.forTag("JAGUAR").map { it.id })
        assertEquals(listOf("mame4droid-neocd"), ExternalEmulators.forTag("NEOCD").map { it.id })
        assertEquals(listOf("mame4droid-cdi"), ExternalEmulators.forTag("CDI").map { it.id })
        assertEquals(listOf("mame4droid-a7800"), ExternalEmulators.forTag("A7800").map { it.id })
    }

    @Test
    fun mameMachinesNameTheirMachineAndTheGamesPath() {
        val jaguar = ExternalEmulators.byId("mame4droid-jaguar")!!
        assertEquals("jaguar", jaguar.data)
        assertEquals("-rompath '{dir}' -cart '{path}'", jaguar.extras["cli_params"])
        assertTrue(jaguar.needsPath)
        assertFalse(offered(jaguar, real = false))
        assertEquals("neocdz", ExternalEmulators.byId("mame4droid-neocd")!!.data)
        assertEquals("cdimono1", ExternalEmulators.byId("mame4droid-cdi")!!.data)
        assertEquals("a7800", ExternalEmulators.byId("mame4droid-a7800")!!.data)
    }

    @Test
    fun textGamesSendTheirIdAndAreNotPathOnly() {
        val vita = ExternalEmulators.byId("vita3k")!!
        assertEquals(mapOf("title_id" to "{text}"), vita.extras)
        assertTrue(vita.needsText)
        assertFalse(vita.needsPath)
        assertTrue(offered(vita, real = false, ext = "psvita"))
        assertEquals(mapOf("AppStartParameters" to listOf("-r", "{text}")), ExternalEmulators.byId("emucorev")!!.arrayExtras)
        assertEquals(mapOf("game_id" to "{text}"), ExternalEmulators.byId("bachatas4")!!.extras)
    }

    @Test
    fun tokensFillPathDirAndText() {
        val t = GameTokens(path = "/R/Jaguar/Doom.j64", dir = "/R/Jaguar", text = "PCSE00000")
        assertEquals("-rompath '/R/Jaguar' -cart '/R/Jaguar/Doom.j64'", t.fill("-rompath '{dir}' -cart '{path}'"))
        assertEquals("PCSE00000", t.fill("{text}"))
        assertEquals("plain", t.fill("plain"))
        assertEquals(null, GameTokens(path = null, dir = null, text = "x").fill("-cart '{path}'"))
        assertEquals(null, GameTokens(path = "/a", dir = "/", text = null).fill("{text}"))
        // A value is never read as a token.
        assertEquals("/a/{dir}", GameTokens(path = "/a/{dir}", dir = "/a", text = null).fill("{path}"))
    }

    @Test
    fun pathTokensMakeARecipePathOnly() {
        val tokenised = recipe(GamePass.VIEW_DATA).copy(data = "jaguar", extras = mapOf("cli" to "-cart '{path}'"))
        assertTrue(tokenised.needsPath)
        assertFalse(tokenised.takesUri)
        assertFalse(offered(tokenised, real = false))
        assertFalse(offered(tokenised, zip = true))
        assertTrue(offered(tokenised))
        val dirInArray = recipe(GamePass.VIEW_DATA).copy(arrayExtras = mapOf("a" to listOf("{dir}")))
        assertTrue(dirInArray.needsPath)
        assertFalse(recipe(GamePass.VIEW_DATA).copy(extras = mapOf("id" to "{text}")).needsPath)
        assertFalse(recipe(GamePass.VIEW_DATA).copy(extras = mapOf("id" to "{text}")).takesUri)
    }

    @Test
    fun gameTextIsTheFilesTrimmedTextOrNothing() {
        assertEquals("PCSE00000", gameText(" PCSE00000\n".encodeToByteArray()))
        assertEquals(null, gameText("  \n".encodeToByteArray()))
        assertEquals(null, gameText(null))
        assertEquals(null, gameText(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x14)))   // a vpk (zip) is not an id
    }

    @Test
    fun optionalAppsInTheSpecsOrderPerConsole() {
        fun ids(tag: String) = ExternalEmulators.forTag(tag).map { it.id }
        assertEquals(listOf("melonds", "melonds-nightly", "noods", "skyemu", "watermelonds", "seedlessds"), ids("NDS"))
        assertEquals(listOf("gbc-emu", "my-oldboy", "linkboy", "pizza-boy-c", "pizza-boy-c-pro", "skyemu"), ids("GB"))
        assertEquals(listOf("gba-emu", "my-boy", "linkboy", "pizza-boy-a", "pizza-boy-a-pro", "noods", "skyemu"), ids("MGBA"))
        assertEquals(listOf("azahar", "azaharplus"), ids("3DS"))
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
