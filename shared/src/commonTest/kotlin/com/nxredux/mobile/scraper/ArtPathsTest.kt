package com.nxredux.mobile.scraper

import com.nxredux.mobile.storage.GamePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArtPathsTest {
    @Test fun baseStripsLastExtensionOnly() {
        fun base(file: String) = ArtPaths.target(GamePath.HOME, "Roms/GB", file, folderGame = false).base
        assertEquals("Super Mario World (USA)", base("Super Mario World (USA).sfc"))
        assertEquals("game.v1", base("game.v1.zip")); assertEquals(".hidden", base(".hidden"))
    }
    @Test fun homeGameEverySlotAndEncoding() {
        val t = ArtPaths.target(GamePath.HOME, "Roms/Game Boy (GB)", "Tetris.gb", folderGame = false)
        assertEquals(listOf("Roms", "Game Boy (GB)", ".media"), t.mediaSegments)
        assertEquals("Tetris", t.base)
        assertEquals(listOf("Roms", "Game Boy (GB)", ".media", "boxart"), t.segments(ArtSlot.BOX_3D))
        assertEquals(listOf("Roms", "Game Boy (GB)", ".media", "boxart2d"), t.segments(ArtSlot.BOX_2D))   // NX Redux's folder
        assertEquals(listOf("Roms", "Game Boy (GB)", ".media", "screenshot"), t.segments(ArtSlot.SCREENSHOT))
        assertEquals(listOf("Roms", "Game Boy (GB)", ".media", "grid"), t.segments(ArtSlot.GRID))
        assertEquals(listOf("Roms", "Game Boy (GB)", ".media", "hero"), t.segments(ArtSlot.HERO))
        assertEquals(listOf("Roms", "Game Boy (GB)", ".media", "wheel"), t.segments(ArtSlot.WHEEL))
        assertEquals(listOf("Roms", "Game Boy (GB)", ".media", "mix"), t.segments(ArtSlot.MIX))
        assertEquals("Roms/Game Boy (GB)/.media/box2d/Tetris.jpg", t.legacyBox2dPath(ArtEncoding.JPEG))
        assertEquals("Tetris.png", t.name(ArtEncoding.PNG)); assertEquals("Tetris.jpg", t.name(ArtEncoding.JPEG))
        assertEquals("Roms/Game Boy (GB)/.media/hero/Tetris.jpg", t.path(ArtSlot.HERO, ArtEncoding.JPEG))
        assertEquals("Roms/Game Boy (GB)/.media/boxart/Tetris.png", t.path(ArtSlot.BOX_3D, ArtEncoding.PNG))
    }
    @Test fun extraGameGoesToTheHomeMirror() {
        val t = ArtPaths.target("x1", "Genesis", "Sonic.md", folderGame = false)
        assertEquals(listOf("Roms", ".sources", "x1", "Genesis", ".media"), t.mediaSegments)
        assertEquals("Roms/.sources/x1/Genesis/.media/boxart2d/Sonic.jpg", t.path(ArtSlot.BOX_2D, ArtEncoding.JPEG))
        assertEquals("Roms/.sources/x1/Genesis/.media/screenshot/Sonic.png", t.path(ArtSlot.SCREENSHOT, ArtEncoding.PNG))
    }
    @Test fun folderGameUsesParentMediaAndFolderName() {
        // relativeDir is the disc folder itself; art goes to its parent's .media/<slot>/<folder>.<ext>
        val t = ArtPaths.target(GamePath.HOME, "Roms/Sony PlayStation (PS)/Final Fantasy VII", "Final Fantasy VII.m3u", folderGame = true)
        assertEquals(listOf("Roms", "Sony PlayStation (PS)", ".media"), t.mediaSegments)
        assertEquals("Roms/Sony PlayStation (PS)/.media/grid/Final Fantasy VII.jpg", t.path(ArtSlot.GRID, ArtEncoding.JPEG))
    }
    @Test fun folderGameKeepsTheFullFolderName() {
        // the scanner looks disc-folder art up by the whole folder name, dots included
        val t = ArtPaths.target(GamePath.HOME, "Roms/Sony PlayStation (PS)/Metal Gear Solid (Ver. 1.1)", "MGS.m3u", folderGame = true)
        assertEquals("Metal Gear Solid (Ver. 1.1).png", t.name(ArtEncoding.PNG))
        assertEquals(listOf("Roms", "Sony PlayStation (PS)", ".media"), t.mediaSegments)
    }
    @Test fun legacyMixPath() {
        assertEquals("Roms/GB/.media/Tetris.png", ArtPaths.target(GamePath.HOME, "Roms/GB", "Tetris.gb", folderGame = false).legacyMixPath)
        assertEquals("Roms/.sources/x1/Genesis/.media/Sonic.png", ArtPaths.target("x1", "Genesis", "Sonic.md", folderGame = false).legacyMixPath)
    }
    @Test fun folderGameNeedsAFolder() {
        assertFailsWith<IllegalArgumentException> { ArtPaths.target(GamePath.HOME, "", "x.m3u", folderGame = true) }
    }
}
