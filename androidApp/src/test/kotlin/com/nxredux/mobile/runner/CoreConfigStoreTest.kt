package com.nxredux.mobile.runner

import com.nxredux.mobile.shortcuts.ShortcutAction
import com.nxredux.mobile.shortcuts.ShortcutBinding
import com.nxredux.mobile.shortcuts.ShortcutOverrides
import com.nxredux.mobile.shortcuts.Shortcuts
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Save Changes: only the overridden shortcut rows are written, one given back to the default leaves the file; an old key goes once its new key is written. */
class CoreConfigStoreTest {
    private val dir = Files.createTempDirectory("cfg").toFile()
    private val console = File(dir, "console.cfg")
    private val game = File(dir, "game.cfg")

    @Test fun a_save_writes_only_overrides_and_drops_the_rest() {
        game.writeText("nx_bind_toggle_ff = 103\nnx_bind_touch_1 = reset\nfoo = 1\n")
        val o = ShortcutOverrides(mapOf(ShortcutAction.RESET to ShortcutBinding(109, 102)))
        CoreConfigStore(console, game).saveGame(listOf("foo" to "2") + Shortcuts.toConfig(o), Shortcuts.droppedOnSave(o))
        assertEquals("foo = 2\nnx_bind_reset = 109+102\n", game.readText())
        CoreConfigStore(console, game).saveConsole(listOf("foo" to "3"), Shortcuts.droppedOnSave(ShortcutOverrides.NONE))
        assertEquals("foo = 3\n", console.readText())
    }

    @Test fun a_save_drops_an_old_key_only_with_its_new_key() {
        game.writeText("nx_screen_scaling = Native\nfoo = 1\n")
        CoreConfigStore(console, game).saveGame(listOf("foo" to "2"))
        assertEquals("nx_screen_scaling = Native\nfoo = 2\n", game.readText())   // the old pick kept
        CoreConfigStore(console, game).saveGame(listOf("foo" to "2", "nx_screen_scaling_v2" to "Native"))
        assertEquals("foo = 2\nnx_screen_scaling_v2 = Native\n", game.readText())
    }
}
