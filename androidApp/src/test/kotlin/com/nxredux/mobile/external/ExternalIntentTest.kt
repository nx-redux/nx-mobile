package com.nxredux.mobile.external

import android.content.Intent
import com.nxredux.mobile.runner.ContentRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalIntentTest {
    private val uri = "content://com.nxredux.mobile.games/root/storage/emulated/0/NX/Roms/Game.iso"

    private fun emulator(pass: GamePass, extraKey: String? = null, clearTask: Boolean = false, extras: Map<String, String> = emptyMap(),
                         action: String = "android.intent.action.VIEW", categories: List<String> = emptyList()) =
        ExternalEmulator(
            id = "app", name = "App", packages = listOf("org.app", "org.app.paid"), activity = "org.app.ui.PlayActivity",
            pass = pass, extraKey = extraKey, extras = extras, clearTask = clearTask, action = action, categories = categories,
            tags = setOf("GC"),
        )

    private fun launch(e: ExternalEmulator, realPath: String? = "/storage/emulated/0/NX/Roms/Game.iso") = ExternalLaunch(
        emulator = e, packageId = "org.app.paid", tag = "GC", ref = ContentRef("file:///storage/emulated/0/NX/Roms/Game.iso"),
        folder = ContentRef("file:///storage/emulated/0/NX/Roms"), fileName = "Game.iso", realPath = realPath,
    )

    @Test fun componentIsTheInstalledPackageAndTheRecipesActivity() {
        val i = buildIntent(launch(emulator(GamePass.VIEW_DATA)), uri, null)!!
        assertEquals("org.app.paid", i.packageId)
        assertEquals("org.app.ui.PlayActivity", i.activity)
    }

    @Test fun actionAndCategoriesComeFromTheRecipe() {
        val i = buildIntent(launch(emulator(GamePass.URI_EXTRA, "AutoStartFile", action = "android.intent.action.MAIN",
            categories = listOf("android.intent.category.LEANBACK_LAUNCHER"))), uri, null)!!
        assertEquals("android.intent.action.MAIN", i.action)
        assertEquals(listOf("android.intent.category.LEANBACK_LAUNCHER"), i.categories)
    }

    @Test fun viewDataCarriesTheUriAsDataWithItsExtension() {
        val i = buildIntent(launch(emulator(GamePass.VIEW_DATA)), uri, null)!!
        assertEquals(uri, i.data)
        assertEquals("iso", i.extension)
        assertEquals(uri, i.grantUri)
        assertTrue(i.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test fun uriExtraCarriesTheUriInTheNamedExtraAndGrantsIt() {
        val i = buildIntent(launch(emulator(GamePass.URI_EXTRA, "AutoStartFile")), uri, null)!!
        assertNull(i.data)
        assertEquals(uri, i.extras["AutoStartFile"])
        assertEquals(uri, i.grantUri)
        assertTrue(i.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test fun pathExtraCarriesThePathAndNoDataOrGrant() {
        val i = buildIntent(launch(emulator(GamePass.PATH_EXTRA, "bootPath")), null, null)!!
        assertNull(i.data)
        assertNull(i.grantUri)
        assertEquals("/storage/emulated/0/NX/Roms/Game.iso", i.extras["bootPath"])
        assertEquals(0, i.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    @Test fun recipeExtrasAreAdded() {
        val i = buildIntent(launch(emulator(GamePass.VIEW_DATA, extras = mapOf("mode" to "boot"))), uri, null)!!
        assertEquals("boot", i.extras["mode"])
    }

    @Test fun alwaysANewTaskAndClearedOnlyWhenTheRecipeSays() {
        val plain = buildIntent(launch(emulator(GamePass.VIEW_DATA)), uri, null)!!
        assertTrue(plain.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(0, plain.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val cleared = buildIntent(launch(emulator(GamePass.VIEW_DATA, clearTask = true)), uri, null)!!
        assertTrue(cleared.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(cleared.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
    }

    @Test fun displayIdIsHonouredWhenGiven() {
        assertEquals(2, buildIntent(launch(emulator(GamePass.VIEW_DATA)), uri, 2)!!.displayId)
        assertNull(buildIntent(launch(emulator(GamePass.VIEW_DATA)), uri, null)!!.displayId)
    }

    private val idTech = ExternalEmulator(
        id = "idtech", name = "idTech", packages = listOf("org.app"), activity = "org.app.Main", pass = GamePass.VIEW_DATA,
        extras = mapOf("game" to "zdoom"), commandsSidecar = true, tags = setOf("PRBOOM"),
    )

    @Test fun sidecarTextGoesInTheCommandExtraBesideTheRecipesGame() {
        val i = buildIntent(launch(idTech), null, null, commands = "game.arm -iwad /Roms/Doom/doom.wad")!!
        assertEquals(mapOf("game" to "zdoom", "command" to "game.arm -iwad /Roms/Doom/doom.wad"), i.extras)
        assertNull(i.data)
        assertNull(i.grantUri)
        assertEquals(0, i.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    @Test fun noSidecarStillLaunchesWithoutACommand() {
        val i = buildIntent(launch(idTech), null, null, commands = null)!!
        assertEquals(mapOf("game" to "zdoom"), i.extras)
    }

    @Test fun commandsFileIsTheGamesBaseNameBesideIt() {
        assertEquals("Doom II.commands", commandsFileName("Doom II.wad"))
        assertEquals("freedoom.commands", commandsFileName("freedoom"))
    }

    private val mame = ExternalEmulator(
        id = "mame", name = "MAME", packages = listOf("org.app"), activity = "org.app.Main", pass = GamePass.VIEW_DATA,
        data = "jaguar", extras = mapOf("cli_params" to "-rompath '{dir}' -cart '{path}'"), tags = setOf("JAGUAR"),
    )

    @Test fun recipeDataReplacesTheGameUriAndTokensAreFilled() {
        val tokens = GameTokens(path = "/R/Jag/Doom.j64", dir = "/R/Jag", text = null)
        val i = buildIntent(launch(mame), null, null, tokens = tokens)!!
        assertEquals("jaguar", i.data)
        assertNull(i.extension)
        assertNull(i.grantUri)
        assertEquals("-rompath '/R/Jag' -cart '/R/Jag/Doom.j64'", i.extras["cli_params"])
    }

    @Test fun arrayExtrasAreFilledToo() {
        val e = emulator(GamePass.VIEW_DATA).copy(arrayExtras = mapOf("AppStartParameters" to listOf("-r", "{text}")))
        val i = buildIntent(launch(e), null, null, tokens = GameTokens(path = null, dir = null, text = "PCSE00000"))!!
        assertEquals(mapOf("AppStartParameters" to listOf("-r", "PCSE00000")), i.arrayExtras)
        assertNull(i.data)
    }

    @Test fun aTokenWithNoValueIsNoIntent() {
        assertNull(buildIntent(launch(mame), uri, null, tokens = GameTokens(path = null, dir = null, text = null)))
        val text = emulator(GamePass.VIEW_DATA).copy(extras = mapOf("title_id" to "{text}"))
        assertNull(buildIntent(launch(text), uri, null, tokens = GameTokens(path = "/a", dir = "/", text = null)))
    }

    @Test fun dirIsThePathsFolder() {
        assertEquals("/storage/emulated/0/Roms/Jaguar", dirOf("/storage/emulated/0/Roms/Jaguar/Doom.j64"))
        assertNull(dirOf(null))
    }

    @Test fun noUriForAUriRecipeOrNoPathForAPathRecipeIsNoIntent() {
        assertNull(buildIntent(launch(emulator(GamePass.VIEW_DATA)), null, null))
        assertNull(buildIntent(launch(emulator(GamePass.URI_EXTRA, "AutoStartFile")), null, null))
        assertNull(buildIntent(launch(emulator(GamePass.PATH_EXTRA, "bootPath"), realPath = null), uri, null))
    }
}
