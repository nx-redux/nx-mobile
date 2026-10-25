package com.nxredux.mobile.storage

import com.nxredux.mobile.runner.ContentRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileRefsTest {
    @Test fun a_file_ref_is_the_raw_path_after_the_scheme() {
        val ref = FileRefs.of("/storage/emulated/0/NXRedux")
        assertEquals(ContentRef("file:///storage/emulated/0/NXRedux"), ref)
        assertTrue(FileRefs.isFile(ref))
        assertEquals("/storage/emulated/0/NXRedux", FileRefs.path(ref))
        assertFalse(FileRefs.isFile(ContentRef("content://com.android.externalstorage.documents/tree/primary%3ANXRedux")))
        assertFalse(FileRefs.isFile(ContentRef("Home")))
    }

    @Test fun names_stay_raw_never_percent_encoded() {
        val rom = FileRefs.child(FileRefs.of("/storage/emulated/0/Roms/GB"), "Pokémon #1 (100%)?.gb")
        assertEquals("file:///storage/emulated/0/Roms/GB/Pokémon #1 (100%)?.gb", rom.value)
        assertEquals("/storage/emulated/0/Roms/GB/Pokémon #1 (100%)?.gb", FileRefs.path(rom))
    }

    @Test fun of_trims_a_trailing_slash_so_child_never_doubles_it() {
        assertEquals(ContentRef("file:///storage/1234-ABCD"), FileRefs.of("/storage/1234-ABCD/"))
        assertEquals("file:///storage/1234-ABCD/Roms", FileRefs.child(FileRefs.of("/storage/1234-ABCD/"), "Roms").value)
        assertEquals("file:///x", FileRefs.child(FileRefs.of("/"), "x").value)
    }

    @Test fun a_plain_name_is_one_path_segment() {
        for (bad in listOf("", ".", "..", "a/b", "/")) assertFalse(FileRefs.isPlainName(bad), bad)
        for (ok in listOf("a b", ".media", "x..y", "Pokémon #1?")) assertTrue(FileRefs.isPlainName(ok), ok)
    }

    @Test fun a_file_ref_maps_to_its_path_and_other_models_do_not() {
        assertEquals(
            "/storage/emulated/0/Roms/GB/.media/boxart/Pokémon #1?.png",
            FileRefs.artPath("file:///storage/emulated/0/Roms/GB/.media/boxart/Pokémon #1?.png"),
        )
        // App-private versioned refs (AndroidArtFiles) too, without their version.
        assertEquals(
            "/data/user/0/com.nxredux.mobile/files/android-art/com.a.png",
            FileRefs.artPath("file:///data/user/0/com.nxredux.mobile/files/android-art/com.a.png?v=1000"),
        )
        assertNull(FileRefs.artPath("content://com.android.externalstorage.documents/tree/primary%3AX/document/primary%3AX%2Fa.png"))
        assertNull(FileRefs.artPath("nx-abstract://seed/1"))
    }

    @Test fun a_versioned_ref_keeps_the_raw_path_and_drops_only_its_version() {
        // A save-state screenshot in the home folder: spaces, '#', '%' and '?' stay as they are (never percent-encoded).
        for (path in listOf(
            "/storage/emulated/0/NX/.userdata/shared/.minui/GBA/Fire Emblem (USA).gba.9.bmp",
            "/storage/emulated/0/NX/.userdata/shared/.minui/GB/Pokémon #1? 100%.gb.0.bmp",
            "/data/user/0/com.nxredux.mobile/files/sd/.userdata/shared/.minui/GB/Tetris.zip.9.bmp",
        )) {
            val ref = FileRefs.versioned(path, 1792909319990L)
            assertEquals("file://$path?v=1792909319990", ref)
            assertEquals(path, FileRefs.artPath(ref))
        }
        // Only a trailing ?v=<digits> is a version: anything else is part of the name.
        assertEquals("/storage/emulated/0/a?v=x.png", FileRefs.artPath("file:///storage/emulated/0/a?v=x.png"))
    }

    @Test fun access_is_off_only_for_a_file_home_without_the_grant() {
        val file = ContentRef("file:///storage/emulated/0/Roms")
        assertTrue(FileRefs.accessOff(file, granted = false))
        assertFalse(FileRefs.accessOff(file, granted = true))
        assertFalse(FileRefs.accessOff(ContentRef("content://x/tree/a"), granted = false))
        assertFalse(FileRefs.accessOff(null, granted = false))
    }

    @Test fun within_is_the_folder_itself_or_inside_it() {
        val media = FileRefs.of("/storage/emulated/0/Android/media/com.nxredux.mobile")
        assertTrue(FileRefs.within(media, media))
        assertTrue(FileRefs.within(media, FileRefs.child(media, "Roms")))
        assertFalse(FileRefs.within(media, FileRefs.of("/storage/emulated/0/Android/media/com.nxredux.mobile.dev")))   // a prefix, not a child
        assertFalse(FileRefs.within(media, FileRefs.child(media, "..")))
        assertFalse(FileRefs.within(media, ContentRef("content://x/tree/primary%3AAndroid")))
    }
}
