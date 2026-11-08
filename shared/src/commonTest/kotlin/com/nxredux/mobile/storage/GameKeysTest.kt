package com.nxredux.mobile.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GameKeysTest {
    @Test fun homeKeyBecomesExtraKey() = assertEquals("/Extra/x1/Game Boy (GB)/Tetris.gb", GameKeys.toExtra("/Roms/Game Boy (GB)/Tetris.gb", "x1"))
    @Test fun extraKeyUntouched() = assertNull(GameKeys.toExtra("/Extra/y/a.gb", "x1"))
    @Test fun linesKeepAliases() = assertEquals("/Extra/x1/GB/a.gb\tMy A\n/Extra/z/b.gb\n", GameKeys.rewriteLines("/Roms/GB/a.gb\tMy A\n/Extra/z/b.gb\n", "x1"))
    @Test fun extraKeyBecomesHomeKeyAgain() {
        assertEquals("/Roms/GB/a.gb", GameKeys.toHome("/Extra/x1/GB/a.gb", "x1"))
        assertNull(GameKeys.toHome("/Extra/x10/GB/a.gb", "x1"))   // another extra
        assertNull(GameKeys.toHome("/Roms/GB/a.gb", "x1"))
    }
    @Test fun aBomOnTheFirstLineStillRewritesIt() =
        assertEquals("/Extra/x1/GB/a.gb\tMy A\n/Extra/x1/GB/b.gb\n", GameKeys.rewriteLines("\uFEFF/Roms/GB/a.gb\tMy A\n/Roms/GB/b.gb\n", "x1"))
    @Test fun linesBackToHome() = assertEquals("/Roms/GB/a.gb\tMy A\n/Extra/z/b.gb\n", GameKeys.rewriteLinesToHome("/Extra/x1/GB/a.gb\tMy A\n/Extra/z/b.gb\n", "x1"))
}
