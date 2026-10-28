package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.Names
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.storage.GamePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContextMenusTest {
    private fun system(tag: String, unassigned: Boolean = false) =
        SystemFolder(tag, tag, CoreRegistry.forTag(tag), emptyList(), unassigned = unassigned)

    private fun entry(tag: String, unassigned: Boolean = false, file: String = "Mario.gba"): Listed {
        val s = system(tag, unassigned)
        val g = GameEntry(ContentRef("Home/Roms/X ($tag)/$file"), file, Names.stripExtension(file), folder = ContentRef("Home/Roms/X ($tag)"), sourceId = GamePath.HOME, relativeDir = "Roms/X ($tag)")
        return Listed(s, g)
    }

    private fun kinds(source: ListSource, e: Listed, pinned: Boolean = false) = ContextMenus.itemsFor(source, e, pinned).map { it.kind }

    @Test fun console_lists_offer_pin_hide_rename_collection_then_emulator_for_multi_tag_consoles() {
        val gba = entry("MGBA")
        assertEquals(listOf(ContextKind.PIN, ContextKind.HIDE, ContextKind.RENAME, ContextKind.ADD_TO_COLLECTION, ContextKind.GAME_SETTINGS, ContextKind.EMULATOR), kinds(ListSource.Console(gba.system), gba))
        assertEquals(listOf(ContextKind.UNPIN, ContextKind.HIDE, ContextKind.RENAME, ContextKind.ADD_TO_COLLECTION, ContextKind.GAME_SETTINGS, ContextKind.EMULATOR), kinds(ListSource.Console(gba.system), gba, pinned = true))
        val gb = entry("GB", file = "Tetris.gb")
        assertEquals(listOf(ContextKind.PIN, ContextKind.HIDE, ContextKind.RENAME, ContextKind.ADD_TO_COLLECTION, ContextKind.GAME_SETTINGS), kinds(ListSource.Console(gb.system), gb))
        assertEquals(listOf("Pin Item", "Hide Game", "Rename Rom", "Add to Collection", "Game Settings"), ContextMenus.itemsFor(ListSource.Console(gb.system), gb, false).map { it.label })
        assertEquals("Unpin Item", ContextMenus.itemsFor(ListSource.Console(gb.system), gb, true)[0].label)
    }

    @Test fun unassigned_lists_offer_hide_rename_and_console_only() {
        val u = entry("", unassigned = true, file = "thing.chd")
        assertEquals(listOf(ContextKind.HIDE, ContextKind.RENAME, ContextKind.CONSOLE), kinds(ListSource.Console(u.system), u))
        assertEquals(listOf(ContextKind.HIDE, ContextKind.RENAME, ContextKind.CONSOLE), kinds(ListSource.Console(u.system), u, pinned = true))
    }

    @Test fun recent_collection_hidden_and_pin_rows_have_their_own_tables() {
        val gba = entry("MGBA")
        assertEquals(listOf(ContextKind.REMOVE_RECENT), kinds(ListSource.Recent, gba))
        assertEquals("Remove from Recently Played", ContextMenus.itemsFor(ListSource.Recent, gba, false)[0].label)
        assertEquals(listOf(ContextKind.PIN, ContextKind.HIDE, ContextKind.RENAME, ContextKind.ADD_TO_COLLECTION, ContextKind.GAME_SETTINGS, ContextKind.REMOVE_FROM_COLLECTION), kinds(ListSource.Collection("RPGs"), gba))
        assertEquals("Remove from Collection", ContextMenus.itemsFor(ListSource.Collection("RPGs"), gba, false).last().label)
        assertEquals(emptyList(), kinds(ListSource.Hidden, gba))            // Hidden: no menu, A unhides
        assertEquals(listOf(ContextItem(ContextKind.UNPIN, "Unpin")), ContextMenus.pinRowItems())
    }

    @Test fun the_continue_card_offers_pin_or_unpin_when_its_game_is_pinned() {
        assertEquals(listOf(ContextItem(ContextKind.PIN, "Pin")), ContextMenus.homeGameItems(pinned = false))
        assertEquals(ContextMenus.pinRowItems(), ContextMenus.homeGameItems(pinned = true))   // a pinned game row's own menu
    }

    @Test fun a_tool_row_offers_pin_or_unpin_when_pinned() {
        assertEquals(listOf(ContextItem(ContextKind.PIN_TOOL, "Pin")), ContextMenus.toolItems(pinned = false))
        assertEquals(listOf(ContextItem(ContextKind.UNPIN_TOOL, "Unpin")), ContextMenus.toolItems(pinned = true))
    }

    @Test fun a_collection_row_offers_rename_and_delete_captioned_with_its_name() {
        assertEquals(
            listOf(ContextItem(ContextKind.RENAME_COLLECTION, "Rename"), ContextItem(ContextKind.DELETE_COLLECTION, "Delete")),
            ContextMenus.collectionItems(),
        )
        val m = ContextMenu(null, ContextMenus.collectionItems(), collection = "RPGs")
        assertEquals("RPGs", m.caption)
        assertNull(m.entry)
        assertNull(m.tool)
    }

    @Test fun fetch_art_follows_game_settings_where_offered_and_never_in_unassigned() {
        val gb = entry("GB", file = "Tetris.gb")
        assertEquals(listOf(ContextKind.PIN, ContextKind.HIDE, ContextKind.RENAME, ContextKind.ADD_TO_COLLECTION, ContextKind.GAME_SETTINGS, ContextKind.FETCH_ART),
            ContextMenus.itemsFor(ListSource.Console(gb.system), gb, false, fetchArt = true).map { it.kind })
        assertEquals("Fetch art", ContextMenus.itemsFor(ListSource.Collection("RPGs"), gb, false, fetchArt = true)[5].label)
        val u = entry("", unassigned = true, file = "thing.chd")
        assertTrue(ContextKind.FETCH_ART !in ContextMenus.itemsFor(ListSource.Console(u.system), u, false, fetchArt = true).map { it.kind })
    }

    @Test fun context_menu_has_netplay_only_for_supported_tags() {
        val sfc = entry("SFC", file = "Super Mario Kart (USA).sfc")
        val gba = entry("GBA")
        val gg = entry("GG", file = "Sonic.gg")
        val n64 = entry("N64", file = "Super Mario 64.z64")
        fun netplay(e: Listed, source: ListSource = ListSource.Console(e.system), on: Boolean = true) =
            ContextMenus.itemsFor(source, e, false, netplay = on).filter { it.kind == ContextKind.NETPLAY }
        assertEquals(listOf(ContextItem(ContextKind.NETPLAY, "Netplay")), netplay(sfc))
        assertEquals(listOf(ContextItem(ContextKind.NETPLAY, "Netplay")), netplay(sfc, ListSource.Collection("RPGs")))
        assertEquals(ContextKind.NETPLAY, ContextMenus.itemsFor(ListSource.Console(sfc.system), sfc, false, netplay = true)
            .map { it.kind }.let { it[it.indexOf(ContextKind.GAME_SETTINGS) + 1] })   // right after Game Settings
        assertEquals(listOf(ContextItem(ContextKind.NETPLAY, "Netplay")), netplay(gba))   // GBA link (stage 2c)
        assertEquals(listOf(ContextItem(ContextKind.NETPLAY, "Netplay")), netplay(gg))   // GG lockstep, as the handheld's GG.pak
        assertTrue(netplay(n64).isEmpty())
        assertTrue(netplay(sfc, on = false).isEmpty())   // a platform without netplay
        assertTrue(netplay(sfc, ListSource.Recent).isEmpty())
        val u = entry("SFC", unassigned = true, file = "thing.sfc")
        assertTrue(netplay(u).isEmpty())
    }

    @Test fun the_collection_picker_ends_with_new_collection() {
        val m = ContextMenus.collectionMenu(listOf("Racing", "RPGs"))
        assertEquals("Add to Collection", m.title)
        assertEquals(listOf("Racing", "RPGs", "New Collection…"), m.labels)
        assertEquals(listOf("Racing", "RPGs", ""), m.values)
        assertEquals(0, m.index)
        assertNull(m.current)
        assertEquals(listOf("New Collection…"), ContextMenus.collectionMenu(emptyList()).labels)
    }

    @Test fun list_titles() {
        assertEquals("Recently Played", ListSource.Recent.title)
        assertEquals("RPGs", ListSource.Collection("RPGs").title)
        assertEquals("Hidden games", ListSource.Hidden.title)
        assertEquals("Game Boy Advance", ListSource.Console(SystemFolder("Game Boy Advance", "GBA", CoreRegistry.forTag("GBA"), emptyList())).title)
    }
    @Test fun game_list_titles_are_the_lists_own_names() {
        val sfc = SystemFolder("Super Nintendo ES", "SFC", CoreRegistry.forTag("SFC"), emptyList())
        val supa = SystemFolder("Super Nintendo ES", "SUPA", CoreRegistry.forTag("SUPA"), emptyList())
        assertEquals("ColecoVision", listTitle(ListSource.Console(SystemFolder("ColecoVision", "COLECO", CoreRegistry.forTag("COLECO"), emptyList()))))
        assertEquals("Super Nintendo ES", listTitle(ListSource.Console(listOf(sfc, supa))))
        assertEquals("Android", listTitle(ListSource.Console(SystemFolder("Android", ANDROID_TAG, null, emptyList(), android = true))))
        assertEquals("RPGs", listTitle(ListSource.Collection("RPGs")))
        assertEquals("Unassigned", listTitle(ListSource.Console(SystemFolder("Unassigned", "", null, emptyList(), unassigned = true))))
        assertEquals("Hidden games", listTitle(ListSource.Hidden))
        assertEquals("Recently Played", listTitle(ListSource.Recent))
    }
    @Test fun emulator_menu_lists_the_consoles_tags_with_the_current_one_marked() {
        val m = ContextMenus.emulatorMenu(system("MGBA"))!!
        assertEquals("Emulator", m.title)
        assertEquals(listOf("GBA", "MGBA"), m.values)
        assertEquals(listOf("GBA (gpsp)", "MGBA (mgba)"), m.labels)
        assertEquals(1, m.current)
        assertEquals(1, m.index)
        val md = ContextMenus.emulatorMenu(system("GPGX"))!!   // Sega Genesis: PicoDrive or Genesis Plus GX
        assertEquals(listOf("MD", "GPGX"), md.values)
        assertEquals(listOf("MD (picodrive)", "GPGX (genesis_plus_gx)"), md.labels)
        assertEquals(1, md.current)
        val sms = ContextMenus.emulatorMenu(system("GPGX_SMS"))!!   // Master System: Genesis Plus GX shows as the handheld's GPGX
        assertEquals(listOf("SMS", "GPGX_SMS"), sms.values)
        assertEquals(listOf("SMS (picodrive)", "GPGX (genesis_plus_gx)"), sms.labels)
        assertEquals(1, sms.current)
        assertNull(ContextMenus.emulatorMenu(system("GB")))
    }
    @Test fun console_menu_offers_consoles_whose_core_runs_the_file_and_that_own_its_extension() {
        val m = ContextMenus.consoleMenu("thing.chd")!!
        assertEquals("Console", m.title)
        assertEquals(listOf("psp", "ps2", "ps", "segacd", "pce", "dc"), m.values)   // not md: Sega Genesis does not own .chd
        assertEquals(m.values.map { id -> CoreRegistry.consoles.first { it.id == id }.displayName }, m.labels)
        assertEquals(0, m.index)
        assertNull(m.current)
        assertEquals(listOf("gb", "sgb"), ContextMenus.consoleMenu("x.GB")!!.values)
        assertEquals(listOf("ps"), ContextMenus.consoleMenu("v.img")!!.values)
        val every = CoreRegistry.consoles.filter { c -> c.tags.any { CoreRegistry.forTag(it) != null } }.map { it.id }
        assertEquals(every, ContextMenus.consoleMenu("w.zip")!!.values)   // any core may run what an archive holds
        assertEquals(every, ContextMenus.consoleMenu("w.7Z")!!.values)
        assertTrue(every.containsAll(listOf("a2600", "pce", "gba", "sgb")))
        assertNull(ContextMenus.consoleMenu("cart.png"))   // fake08 runs it, but no console owns png
        assertNull(ContextMenus.consoleMenu("notes.txt"))
    }

    @Test fun android_games_offer_pin_or_unpin_only() {
        val s = SystemFolder("Android", ANDROID_TAG, null, emptyList(), android = true)
        val e = Listed(s, GameEntry(ContentRef("app:a/A"), "a/A", "Alpha", folder = ContentRef("app:"), sourceId = "android", relativeDir = ""))
        assertEquals(listOf(ContextKind.PIN), kinds(ListSource.Console(s), e))
        assertEquals(listOf(ContextKind.UNPIN), kinds(ListSource.Console(s), e, pinned = true))
        assertEquals(listOf(ContextKind.PIN), ContextMenus.itemsFor(ListSource.Console(s), e, pinned = false, fetchArt = true).map { it.kind })
    }

    @Test fun a_search_list_offers_the_collection_items_without_remove() {
        val entry = entry("GB", file = "Tetris.gb")
        val items = ContextMenus.itemsFor(ListSource.Search("mario"), entry, pinned = false, fetchArt = true, netplay = true).map { it.kind }
        val collection = ContextMenus.itemsFor(ListSource.Collection("RPGs"), entry, pinned = false, fetchArt = true, netplay = true).map { it.kind }
        assertEquals(collection - ContextKind.REMOVE_FROM_COLLECTION, items)
    }

    private fun lead(source: ListSource, e: Listed, canResume: Boolean = true, netplay: Boolean = true, fetchArt: Boolean = false) =
        ContextMenus.itemsFor(source, e, pinned = false, fetchArt = fetchArt, netplay = netplay, canResume = canResume, netplayLeads = true)

    // A resumes a game with a save state, so its menu leads with Play from start and has no Resume.
    @Test fun a_game_with_a_save_leads_its_menu_with_play_from_start() {
        val e = entry("SFC", file = "Kart.sfc")
        val src = ListSource.Console(e.system)
        val shown = ContextMenus.itemsFor(src, e, pinned = false, netplay = true, canResume = true)
        assertEquals(listOf(ContextKind.PLAY_FROM_START, ContextKind.PIN, ContextKind.HIDE, ContextKind.RENAME, ContextKind.ADD_TO_COLLECTION, ContextKind.GAME_SETTINGS, ContextKind.NETPLAY, ContextKind.EMULATOR), shown.map { it.kind })
        assertEquals("Play from start", shown[0].label)
        assertEquals(ContextKind.PIN, ContextMenus.itemsFor(src, e, pinned = false, netplay = true).first().kind)   // no save: A plays from the start already
    }

    @Test fun a_hidden_netplay_hint_puts_netplay_after_play_from_start() {
        val e = entry("SFC", file = "Kart.sfc")
        val src = ListSource.Console(e.system)
        assertEquals(listOf(ContextKind.PLAY_FROM_START, ContextKind.NETPLAY, ContextKind.PIN), lead(src, e).map { it.kind }.take(3))
        assertEquals(listOf(ContextKind.NETPLAY, ContextKind.PIN), lead(src, e, canResume = false).map { it.kind }.take(2))
        assertEquals(listOf(ContextKind.PLAY_FROM_START, ContextKind.PIN), lead(src, e, netplay = false).map { it.kind }.take(2))
        val nes = entry("N64", file = "Mario.z64")   // a tag without netplay: never Netplay
        assertTrue(ContextKind.NETPLAY !in lead(ListSource.Console(nes.system), nes).map { it.kind })
    }

    @Test fun collection_and_search_menus_list_netplay_once() {
        val e = entry("SFC", file = "Kart.sfc")
        for (src in listOf(ListSource.Collection("RPGs"), ListSource.Search("kart"))) {
            val kinds = lead(src, e, fetchArt = true).map { it.kind }
            assertEquals(listOf(ContextKind.PLAY_FROM_START, ContextKind.NETPLAY, ContextKind.PIN), kinds.take(3))
            assertEquals(1, kinds.count { it == ContextKind.NETPLAY })
            assertTrue(ContextKind.FETCH_ART in kinds)
        }
        assertEquals(ContextKind.REMOVE_FROM_COLLECTION, lead(ListSource.Collection("RPGs"), e).last().kind)
    }

    @Test fun recently_played_puts_play_from_start_and_netplay_above_remove() {
        val e = entry("SFC", file = "Kart.sfc")
        assertEquals(listOf(ContextKind.PLAY_FROM_START, ContextKind.NETPLAY, ContextKind.REMOVE_RECENT), lead(ListSource.Recent, e).map { it.kind })
        assertEquals(listOf(ContextKind.REMOVE_RECENT), lead(ListSource.Recent, e, canResume = false, netplay = false).map { it.kind })
    }

    @Test fun home_game_menus_lead_with_play_from_start_when_the_game_has_a_save() {
        assertEquals(listOf(ContextKind.PLAY_FROM_START, ContextKind.UNPIN), ContextMenus.pinRowItems(fromStart = true).map { it.kind })
        assertEquals(listOf(ContextKind.PLAY_FROM_START, ContextKind.NETPLAY, ContextKind.PIN), ContextMenus.homeGameItems(pinned = false, netplay = true, fromStart = true).map { it.kind })
        assertEquals(listOf(ContextKind.PLAY_FROM_START, ContextKind.NETPLAY, ContextKind.UNPIN), ContextMenus.homeGameItems(pinned = true, netplay = true, fromStart = true).map { it.kind })
    }

    @Test fun unassigned_and_hidden_lists_get_no_lead_items() {
        val u = entry("", unassigned = true, file = "x.7z")
        assertEquals(ContextMenus.itemsFor(ListSource.Console(u.system), u, false), lead(ListSource.Console(u.system), u))
        assertTrue(lead(ListSource.Hidden, u).isEmpty())
    }

    @Test fun home_game_menus_lead_with_netplay_when_hints_are_hidden() {
        assertEquals(listOf(ContextKind.NETPLAY, ContextKind.PIN), ContextMenus.homeGameItems(pinned = false, netplay = true).map { it.kind })
        assertEquals(listOf(ContextKind.NETPLAY, ContextKind.UNPIN), ContextMenus.homeGameItems(pinned = true, netplay = true).map { it.kind })
        assertEquals(listOf(ContextKind.NETPLAY, ContextKind.UNPIN), ContextMenus.pinRowItems(netplay = true).map { it.kind })
        assertEquals(listOf(ContextKind.PIN), ContextMenus.homeGameItems(pinned = false, netplay = false).map { it.kind })
        assertEquals(listOf(ContextKind.UNPIN), ContextMenus.pinRowItems().map { it.kind })
    }

    @Test fun hiddenTabItemsComeInTheTabsOrder() {
        assertEquals(
            listOf(ContextItem(ContextKind.OPEN_CONSOLES, "Consoles"), ContextItem(ContextKind.OPEN_COLLECTIONS, "Collections"), ContextItem(ContextKind.OPEN_TOOLS, "Tools")),
            ContextMenus.hiddenTabItems(listOf(MenuCategory.TOOLS, MenuCategory.CONSOLES, MenuCategory.COLLECTIONS)),
        )
        assertEquals(emptyList<ContextItem>(), ContextMenus.hiddenTabItems(listOf(MenuCategory.HOME)))
        assertEquals(MenuCategory.TOOLS, ContextMenus.tabOf(ContextKind.OPEN_TOOLS))
        assertEquals(null, ContextMenus.tabOf(ContextKind.PIN))
    }
}
