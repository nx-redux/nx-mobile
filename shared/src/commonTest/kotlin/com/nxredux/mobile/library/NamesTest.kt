package com.nxredux.mobile.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NamesTest {
    @Test fun hidden_rules_match_nx_redux() {
        assertTrue(Names.isHidden(".media"))
        assertTrue(Names.isHidden(".sources"))
        assertTrue(Names.isHidden(".DS_Store"))
        assertTrue(Names.isHidden("Old Game.gba.disabled"))
        assertTrue(Names.isHidden("map.txt"))
        assertTrue(Names.isHidden("MAP.TXT"))   // case-insensitive volumes: the same file
        assertTrue(Names.isHidden("Map.txt"))
        assertFalse(Names.isHidden("Mario Kart DS.nds"))
        assertFalse(Names.isHidden("map.txt.bak"))
    }
    @Test fun a_part_file_from_a_replacing_write_is_hidden() {
        assertTrue(Names.isHidden("map.txt.part"))
        assertTrue(Names.isHidden("systems.txt.part"))
        assertTrue(Names.isHidden("Tetris.gb.part"))
        assertFalse(Names.isHidden("Partners.gb"))
    }
    @Test fun system_folder_tag_is_the_trailing_parenthesised_word() {
        assertEquals("Nintendo DS" to "NDS", Names.parseSystemFolder("Nintendo DS (NDS)"))
        assertEquals("Game Boy Advance" to "MGBA", Names.parseSystemFolder("Game Boy Advance (MGBA)"))
        assertEquals("Sony Playstation Portable" to "PSP", Names.parseSystemFolder("Sony Playstation Portable (PSP) "))
        assertEquals("Crazy Taxi 2 v1.004 (2001)" to "US", Names.parseSystemFolder("Crazy Taxi 2 v1.004 (2001) (US)"))
        assertNull(Names.parseSystemFolder("Ports"))
        assertNull(Names.parseSystemFolder("Weird ()"))
    }
    @Test fun extension_helpers() {
        assertEquals("Mario Kart 64", Names.stripExtension("Mario Kart 64.z64"))
        assertEquals("z64", Names.extensionOf("Mario Kart 64.Z64"))
        assertEquals("", Names.extensionOf("README"))
        assertEquals("", Names.extensionOf(".hidden"))
        assertEquals("hidden", Names.extensionOf(".Hidden", dotFile = true))
        assertEquals("", Names.extensionOf("README", dotFile = true))
        assertEquals("README", Names.stripExtension("README"))
    }
    @Test fun systems_txt_is_hidden_and_skeleton_folder_names_follow_the_console() {
        assertTrue(Names.isHidden("systems.txt"))
        val gba = com.nxredux.mobile.runner.CoreRegistry.consoleOf("GBA")!!
        assertEquals("Game Boy Advance (MGBA)", Names.systemFolderName(gba, "MGBA"))
        assertEquals("Game Boy Advance" to "MGBA", Names.parseSystemFolder(Names.systemFolderName(gba, "MGBA")))
    }
    @Test fun new_skeleton_folder_names_parse_to_their_tags() {
        assertEquals("Pokémon mini" to "PKM", Names.parseSystemFolder("Pokémon mini (PKM)"))
        assertEquals("Sega SG-1000" to "SG1000", Names.parseSystemFolder("Sega SG-1000 (SG1000)"))
        assertEquals("TurboGrafx-16" to "PCE", Names.parseSystemFolder("TurboGrafx-16 (PCE)"))
        val md = com.nxredux.mobile.runner.CoreRegistry.consoleOf("GPGX")!!
        assertEquals("Sega Genesis (GPGX)", Names.systemFolderName(md, "GPGX"))
    }
    @Test fun archives_are_zip_and_7z_whatever_the_case() {
        assertEquals(setOf("zip", "7z"), Names.ARCHIVE_EXTENSIONS)
        assertTrue(Names.isArchive("Game.zip"))
        assertTrue(Names.isArchive("Game.7Z"))
        assertTrue(Names.isArchive("Game (USA).ZIP"))
        assertFalse(Names.isArchive("Game.gba"))
        assertFalse(Names.isArchive("Game.rar"))
        assertFalse(Names.isArchive("zip"))
        assertFalse(Names.isArchive(".zip"))
        assertEquals("gba", Names.extensionOf("GAME.GBA"))   // the inner-file check lowercases the same way
    }

    @Test fun valid_alias_trims_and_rejects_empty_slash_dot_and_control_characters() {
        assertEquals("Super Mario", Names.validAlias("  Super Mario "))
        assertNull(Names.validAlias(""))
        assertNull(Names.validAlias("   "))
        assertNull(Names.validAlias("a/b"))
        assertNull(Names.validAlias(".hidden"))
        assertNull(Names.validAlias("tab\there"))
        assertNull(Names.validAlias("new\nline"))
    }

    @Test fun valid_collection_name_also_rejects_map_and_file_system_characters() {
        assertEquals("RPGs", Names.validCollectionName(" RPGs "))
        assertNull(Names.validCollectionName("map"))
        assertNull(Names.validCollectionName("MAP"))
        for (c in listOf('\\', ':', '*', '?', '"', '<', '>', '|', '/')) assertNull(Names.validCollectionName("a${c}b"), "$c")
        assertNull(Names.validCollectionName(".dot"))
        assertNull(Names.validCollectionName(""))
    }

    @Test fun plain_name_cuts_at_the_first_bracket_as_the_brick() {
        assertEquals("Donkey Kong", Names.plainName("Donkey Kong (1982)"))
        assertEquals("Zelda", Names.plainName("Zelda (USA) (Rev 1)"))
        assertEquals("Tank Wars", Names.plainName("Tank Wars (1983) (Bit Corp) [!]"))
        assertEquals("Tetris", Names.plainName("Tetris [!]"))
        assertEquals("Game", Names.plainName("Game [a] (b)"))
        assertEquals("Mario Kart 64", Names.plainName("Mario Kart 64"))
    }
    @Test fun plain_name_keeps_a_bracket_at_position_zero() {
        assertEquals("(Proto) Game", Names.plainName("(Proto) Game"))
        assertEquals("[BIOS] x", Names.plainName("[BIOS] x"))
        // getDisplayName looks for '(' first: a '(' at 0 stops the cut even with a later '['
        assertEquals("(Proto) Game [!]", Names.plainName("(Proto) Game [!]"))
        assertEquals("[BIOS] x", Names.plainName("[BIOS] x (Rev 1)"))
    }
    @Test fun plain_name_edge_cases() {
        assertEquals("", Names.plainName(""))
        assertEquals("Game", Names.plainName("Game   "))
        assertEquals("Game", Names.plainName("Game \t(USA)"))
        assertEquals("Game", Names.plainName("Game (unbalanced"))
        assertEquals("Game )", Names.plainName("Game ) (x"))   // a stray ')' is not a bracket to cut at
        assertEquals(" ", Names.plainName(" (x)"))   // the first character is never trimmed, as the C loop
    }
    @Test fun plain_title_drops_trailing_bracket_groups_as_gen_arcade_names() {
        assertEquals("1942", Names.plainTitle("1942 (Revision B)"))
        assertEquals("The Three Stooges In Brides Is Brides", Names.plainTitle("The Three Stooges In Brides Is Brides (set 1)"))
        assertEquals("Dragon Spirit", Names.plainTitle("Dragon Spirit (new version (DS3))"))
        assertEquals("Metal Slug - Super Vehicle-001", Names.plainTitle("Metal Slug - Super Vehicle-001"))
        assertEquals("Street Fighter II': Champion Edition", Names.plainTitle("Street Fighter II': Champion Edition (street fighter 2' 920313 etc)"))
        assertEquals("Galaga", Names.plainTitle("Galaga (Namco rev. B) [bootleg]"))
        assertEquals("Galaxian", Names.plainTitle("Galaxian / Moon Alien (set 1)"))
    }
    @Test fun plain_title_keeps_inner_groups_and_stops_on_odd_shapes() {
        assertEquals("Vs. (Super) Mario Bros.", Names.plainTitle("Vs. (Super) Mario Bros."))       // not at the end
        assertEquals("Game (a", Names.plainTitle("Game (a"))                                        // no closing bracket
        assertEquals("Game a)", Names.plainTitle("Game a)"))                                        // unbalanced
        assertEquals("(Whole Title)", Names.plainTitle("(Whole Title)"))                            // the group is the whole title
        assertEquals("Game", Names.plainTitle("Game (x) \t "))                                     // trailing blanks
        assertEquals("Game", Names.plainTitle("Game   "))
    }
    @Test fun plain_title_slash_and_empty_edge_cases() {
        assertEquals(" / Two", Names.plainTitle(" / Two"))       // " / " at position 0 is not cut
        assertEquals("One", Names.plainTitle("One / Two (x)"))
        assertEquals("   (x)", Names.plainTitle("   (x)"))       // would be empty: the original stays
        assertEquals("", Names.plainTitle(""))
    }
}
