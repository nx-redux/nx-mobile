package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.shared.res.*
import com.nxredux.mobile.ui.ToolKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MenuArtTest {
    @Test fun everyConsoleHasALogo() {
        val missing = CoreRegistry.consoles.map { it.id }.filter { it !in consoleLogosById}
        assertTrue(missing.isEmpty(), "no logo for $missing")
    }

    @Test fun saturnHasItsOwnLogo() {
        assertSame(Res.drawable.menu_logo_saturn, consoleLogo("SATURN"))
        assertNotEquals(consoleLogosById["md"], consoleLogo("SATURN"))
    }

    @Test fun aTagFindsItsConsolesLogoWhateverItsCase() {
        assertEquals(consoleLogosById["md"], consoleLogo("GPGX"))
        assertEquals(consoleLogosById["md"], consoleLogo("md"))
        assertEquals(consoleLogosById["sfc"], consoleLogo("SUPA"))
        assertEquals(consoleLogosById["gba"], consoleLogo("MGBA"))
    }

    @Test fun androidHasALogo() {
        assertNotNull(consoleLogo(com.nxredux.mobile.ui.ANDROID_TAG))
    }

    @Test fun anUnknownTagHasNoLogo() { assertNull(consoleLogo("NOPE")) }

    @Test fun everyToolHasAnIcon() { ToolKind.entries.forEach { assertNotNull(toolIcon(it)) } }
}
