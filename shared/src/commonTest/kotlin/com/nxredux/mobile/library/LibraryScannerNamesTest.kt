package com.nxredux.mobile.library

import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.settings.ExtraFolder
import com.nxredux.mobile.storage.GamePath
import com.nxredux.mobile.storage.GameRef
import com.nxredux.mobile.storage.MemorySidecarStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/** Shown names as the Brick: bracketed text dropped from file names and arcade titles, colliding plain names show their stems, map.txt wins. */
class LibraryScannerNamesTest {
    private val gb = "H/Roms/Game Boy (GB)"
    private val fbn = "H/Roms/Arcade (FBN)"
    private val ps = "H/Roms/Sony PlayStation (PS)"

    private suspend fun scan(
        dirs: Map<String, List<String>>,
        texts: Map<String, String> = emptyMap(),
        extras: List<ExtraFolder> = emptyList(),
        sets: ArcadeSets = ArcadeSets.EMPTY,
    ): LibraryIndex {
        val src = FakeRomsSource(dirs = mapOf("H" to listOf("Roms")) + dirs, texts = texts)
        return LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { ContentRef("H") }, arcadeSets = { sets }).scan(ContentRef("H"), extras, emptyMap())
    }

    private fun LibraryIndex.names(tag: String) = systems.first { it.tag == tag }.games.associate { it.file to it.name }

    @Test fun a_file_name_drops_its_bracketed_text() = runTest {
        val index = scan(mapOf("H/Roms" to listOf("Game Boy (GB)"), gb to listOf("Tetris (World) (Rev 1).gb", "Donkey Kong (1982).gb")))
        assertEquals(mapOf("Tetris (World) (Rev 1).gb" to "Tetris", "Donkey Kong (1982).gb" to "Donkey Kong"), index.names("GB"))
        assertEquals(listOf("Donkey Kong", "Tetris"), index.systems.single().games.map { it.name })   // sorted by the final names
    }

    @Test fun colliding_plain_names_show_their_stems() = runTest {
        val index = scan(mapOf("H/Roms" to listOf("Game Boy (GB)"), gb to listOf("Zelda (USA).gb", "Zelda (Europe).gb", "Tetris (World).gb")))
        assertEquals(mapOf("Zelda (USA).gb" to "Zelda (USA)", "Zelda (Europe).gb" to "Zelda (Europe)", "Tetris (World).gb" to "Tetris"), index.names("GB"))
        assertEquals(listOf("Tetris", "Zelda (Europe)", "Zelda (USA)"), index.systems.single().games.map { it.name })
    }

    @Test fun a_collision_is_case_sensitive() = runTest {
        val index = scan(mapOf("H/Roms" to listOf("Game Boy (GB)"), gb to listOf("Zelda (USA).gb", "ZELDA (Europe).gb")))
        assertEquals(mapOf("Zelda (USA).gb" to "Zelda", "ZELDA (Europe).gb" to "ZELDA"), index.names("GB"))
    }

    @Test fun an_alias_is_never_changed_and_does_not_collide() = runTest {
        val index = scan(
            mapOf("H/Roms" to listOf("Game Boy (GB)"), gb to listOf("Zelda (USA).gb", "Link (Japan).gb", "Mario (World).gb", "map.txt")),
            mapOf("$gb/map.txt" to "Link (Japan).gb\tZelda\nMario (World).gb\tMario (World) [Hack]\n"),
        )
        assertEquals(mapOf("Zelda (USA).gb" to "Zelda", "Link (Japan).gb" to "Zelda", "Mario (World).gb" to "Mario (World) [Hack]"), index.names("GB"))
    }

    @Test fun arcade_titles_drop_their_bracketed_text() = runTest {
        val sets = ArcadeSets.parse("1942\t1942 (Revision B)\t\t\t0\nmslug\tMetal Slug - Super Vehicle-001\t\tneogeo\t0\nneogeo\tNeo Geo\t\t\t1\n")
        val index = scan(mapOf("H/Roms" to listOf("Arcade (FBN)"), fbn to listOf("1942.zip", "mslug.zip", "neogeo.zip", "unknown (hack).zip")), sets = sets)
        assertEquals(mapOf("1942.zip" to "1942", "mslug.zip" to "Metal Slug - Super Vehicle-001", "unknown (hack).zip" to "unknown"), index.names("FBN"))
    }

    @Test fun arcade_clones_with_colliding_titles_show_their_stems() = runTest {
        val sets = ArcadeSets.parse("1942\t1942 (Revision B)\t\t\t0\n1942a\t1942 (Revision A)\t1942\t1942\t0\nsf2\tStreet Fighter II (World 910522)\t\t\t0\n")
        val index = scan(mapOf("H/Roms" to listOf("Arcade (FBN)"), fbn to listOf("1942.zip", "1942a.zip", "sf2.zip")), sets = sets)
        assertEquals(mapOf("1942.zip" to "1942", "1942a.zip" to "1942a", "sf2.zip" to "Street Fighter II"), index.names("FBN"))
    }

    @Test fun a_disc_folder_name_drops_its_bracketed_text() = runTest {
        val index = scan(
            mapOf(
                "H/Roms" to listOf("Sony PlayStation (PS)"),
                ps to listOf("Crash (USA)"),
                "$ps/Crash (USA)" to listOf("Crash (USA).m3u", "Crash (USA) (Disc 1).chd"),
            ),
        )
        assertEquals(mapOf("Crash (USA).m3u" to "Crash"), index.names("PS"))
    }

    @Test fun unassigned_and_extra_folder_names_are_cleaned_too() = runTest {
        val index = scan(
            mapOf("sd/Stuff" to listOf("Tetris (World).gb"), "sd/Mixed" to listOf("Mystery (Proto).chd")),
            extras = listOf(ExtraFolder("e1", ContentRef("sd/Stuff")), ExtraFolder("e2", ContentRef("sd/Mixed"))),
        )
        assertEquals(mapOf("Tetris (World).gb" to "Tetris"), index.names("GB"))
        assertEquals(mapOf("Mystery (Proto).chd" to "Mystery"), index.systems.single { it.unassigned }.games.associate { it.file to it.name })
    }

    private fun LibraryIndex.rows(tag: String) = systems.first { it.tag == tag && !it.unassigned }.games.map { Triple(it.source, it.name, it.subtitle) }

    @Test fun a_single_copy_keeps_its_clean_name_and_no_subtitle() = runTest {
        val index = scan(mapOf("H/Roms" to listOf("Game Boy (GB)"), gb to listOf("Zelda (USA).gb")))
        assertEquals(listOf(Triple("home", "Zelda", null)), index.rows("GB"))
    }

    // An extra folder's copy of a home game (the same file name, any case) is left out: the home copy is listed.
    @Test fun an_extra_copy_of_a_home_file_is_hidden() = runTest {
        val index = scan(
            mapOf("H/Roms" to listOf("Game Boy (GB)"), gb to listOf("Zelda (USA).gb"), "sd/My GB" to listOf("zelda (usa).GB")),
            extras = listOf(ExtraFolder("e1", ContentRef("sd/My GB"))),
        )
        assertEquals(listOf(Triple("home", "Zelda", null)), index.rows("GB"))
        // The home copy keeps the hidden one's path, so a Recents or collection line for it still opens the game.
        val home = index.systems.single { it.tag == "GB" }.games.single()
        assertEquals(listOf(GamePath.of(GameRef("e1", "", "zelda (usa).GB"))), home.hiddenCopies)
    }

    // A console folder named for the console but not its tag ("My Game Boy Advance") is the console's own folder: a
    // file loose in it is a copy of the home's file of that name, hidden (the Fold's /Roms/My … folders).
    @Test fun an_extra_copy_in_a_console_named_folder_is_hidden() = runTest {
        val index = scan(
            mapOf(
                "H/Roms" to listOf("Game Boy (GB)"), gb to listOf("Zelda (USA).gb"),
                "sd/Roms" to listOf("My Game Boy"), "sd/Roms/My Game Boy" to listOf("Zelda (USA).gb", "Tetris (World).gb"),
            ),
            extras = listOf(ExtraFolder("e1", ContentRef("sd/Roms"))),
        )
        assertEquals(listOf("home" to "Zelda", "e1" to "Tetris"), index.rows("GB").map { it.first to it.second }.sortedBy { it.second }.reversed())
        assertEquals(listOf(GamePath.of(GameRef("e1", "My Game Boy", "Zelda (USA).gb"))), index.systems.single { it.tag == "GB" }.games.single { it.source == "home" }.hiddenCopies)
    }

    // A generic file name (a PSP EBOOT.PBP, one per game folder) is the same game only in the same place.
    @Test fun an_extra_file_named_like_a_home_file_in_another_named_folder_is_kept() = runTest {
        val psp = "H/Roms/Sony PlayStation Portable (PSP)"
        val index = scan(
            mapOf(
                "H/Roms" to listOf("Sony PlayStation Portable (PSP)"), psp to listOf("EBOOT.PBP"),
                "sd/PSP" to listOf("EBOOT.pbp", "Loco Roco (USA)", "Patapon (USA)"),
                "sd/PSP/Loco Roco (USA)" to listOf("EBOOT.PBP"), "sd/PSP/Patapon (USA)" to listOf("EBOOT.PBP"),
            ),
            extras = listOf(ExtraFolder("e1", ContentRef("sd/PSP"))),
        )
        val games = index.systems.single { it.tag == "PSP" }.games
        // The extra's loose EBOOT.pbp is the home's (hidden); its game folders' are two other games.
        assertEquals(
            setOf("home" to "Sony PlayStation Portable (PSP)", "e1" to "Loco Roco (USA)", "e1" to "Patapon (USA)"),
            games.mapTo(HashSet()) { it.source to it.dir.substringAfterLast('/') },
        )
    }

    @Test fun a_home_copy_shows_no_folder_and_a_different_extra_file_with_its_name_its_folder_name_home_first() = runTest {
        val index = scan(
            mapOf("H/Roms" to listOf("Game Boy (GB)"), gb to listOf("Zelda (USA).gb"), "sd/My GB" to listOf("Zelda (Europe).gb")),
            extras = listOf(ExtraFolder("e1", ContentRef("sd/My GB"))),
        )
        assertEquals(listOf(Triple("home", "Zelda", null), Triple("e1", "Zelda", "My GB")), index.rows("GB"))   // tie: home before extras
    }

    @Test fun copies_in_two_extras_show_their_last_folder_names() = runTest {
        val index = scan(
            mapOf("sd/A" to listOf("x"), "sd/A/x" to listOf("gb"), "sd/A/x/gb" to listOf("Zelda (USA).gb"), "sd/B" to listOf("Zelda (Europe).gb")),
            extras = listOf(ExtraFolder("a", ContentRef("sd/A")), ExtraFolder("b", ContentRef("sd/B"))),
        )
        assertEquals(listOf(Triple("a", "Zelda", "gb"), Triple("b", "Zelda", "B")), index.rows("GB"))   // tie: extras in their order
    }

    @Test fun same_folder_colliders_keep_their_stems_and_extra_copies_show_their_folder() = runTest {
        val index = scan(
            mapOf(
                "H/Roms" to listOf("Game Boy (GB)"), gb to listOf("Zelda (USA).gb", "Zelda (Europe).gb", "Tetris (World).gb"),
                "sd/Stuff" to listOf("Zelda (USA).gb", "Zelda (Japan).gb", "Tetris (World).gb"),
            ),
            extras = listOf(ExtraFolder("e1", ContentRef("sd/Stuff"))),
        )
        // The extra's copies of home files (Tetris (World), Zelda (USA)) are hidden; its Zelda (Japan), now alone in its
        // folder, keeps its clean name and shows the folder.
        assertEquals(
            listOf(
                Triple("home", "Tetris", null),
                Triple("e1", "Zelda", "Stuff"),
                Triple("home", "Zelda (Europe)", null),
                Triple("home", "Zelda (USA)", null),
            ),
            index.rows("GB"),
        )
    }

    @Test fun a_disc_folder_in_an_extra_colliding_with_a_home_copy_shows_the_folder_around_it() = runTest {
        val index = scan(
            mapOf(
                "H/Roms" to listOf("Sony PlayStation (PS)"), ps to listOf("Crash (USA).chd"),
                "sd/Discs" to listOf("PS"), "sd/Discs/PS" to listOf("Crash (USA)"),
                "sd/Discs/PS/Crash (USA)" to listOf("Crash (USA).m3u", "Crash (USA) (Disc 1).chd"),
            ),
            extras = listOf(ExtraFolder("e1", ContentRef("sd/Discs"))),
        )
        assertEquals(listOf(Triple("home", "Crash", null), Triple("e1", "Crash", "PS")), index.rows("PS"))
    }

    @Test fun arcade_copies_in_two_folders_keep_their_titles() = runTest {
        val sets = ArcadeSets.parse("1942\t1942 (Revision B)\t\t\t0\n")
        val index = scan(
            mapOf("H/Roms" to listOf("Arcade (FBN)"), fbn to listOf("1942.zip"), "sd/Arc" to listOf("FBN"), "sd/Arc/FBN" to listOf("1942.zip")),
            extras = listOf(ExtraFolder("e1", ContentRef("sd/Arc"))),
            sets = sets,
        )
        assertEquals(listOf(Triple("home", "1942", null)), index.rows("FBN"))   // the extra's 1942.zip is the home's file
    }

    @Test fun unassigned_keeps_the_stem_rule_and_its_dir_subtitles() = runTest {
        val index = scan(
            mapOf("sd/A" to listOf("Mystery (USA).chd"), "sd/B" to listOf("Mystery (Europe).chd")),
            extras = listOf(ExtraFolder("a", ContentRef("sd/A")), ExtraFolder("b", ContentRef("sd/B"))),
        )
        val un = index.systems.single { it.unassigned }
        assertEquals(setOf("Mystery (USA)", "Mystery (Europe)"), un.games.map { it.name }.toSet())
        assertEquals(listOf("/", "/"), index.toSystems().single { it.unassigned }.games.map { it.subtitle })
    }
}
