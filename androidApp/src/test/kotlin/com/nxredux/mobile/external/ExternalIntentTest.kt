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

    @Test fun noUriForAUriRecipeOrNoPathForAPathRecipeIsNoIntent() {
        assertNull(buildIntent(launch(emulator(GamePass.VIEW_DATA)), null, null))
        assertNull(buildIntent(launch(emulator(GamePass.URI_EXTRA, "AutoStartFile")), null, null))
        assertNull(buildIntent(launch(emulator(GamePass.PATH_EXTRA, "bootPath"), realPath = null), uri, null))
    }
}
