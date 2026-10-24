package com.nxredux.mobile.ui

import com.nxredux.mobile.library.Names
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.netplay.NetplayCores
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.settings.MenuCategory

enum class ContextKind { PIN, UNPIN, HIDE, UNHIDE, RENAME, ADD_TO_COLLECTION, GAME_SETTINGS, REMOVE_FROM_COLLECTION, REMOVE_RECENT, EMULATOR, CONSOLE, FETCH_ART, PIN_TOOL, UNPIN_TOOL, ADD_TO_TOOLS, REMOVE_FROM_TOOLS, RENAME_COLLECTION, DELETE_COLLECTION, NETPLAY, PLAY_FROM_START, OPEN_CONSOLES, OPEN_COLLECTIONS, OPEN_TOOLS, ACHIEVEMENTS }

data class ContextItem(val kind: ContextKind, val label: String)

/** A list of choices under an item. [values] are tags (Emulator) or console ids (Console); [current] marks the one in effect. */
data class SubMenu(val title: String, val labels: List<String>, val values: List<String>, val index: Int, val current: Int? = null)

sealed interface MenuPrompt {
    /** Rename Rom: [initial] is the current shown name; [draft] the text as typed. */
    data class Rename(val initial: String, val draft: String = initial) : MenuPrompt
    data class NewCollection(val draft: String = "") : MenuPrompt
    /** Rename on a Collections-tab row: [initial] is the collection's name; [draft] the text as typed. */
    data class RenameCollection(val initial: String, val draft: String = initial) : MenuPrompt
    /** Delete on a Collections-tab row: the confirm for [name]. */
    data class DeleteCollection(val name: String) : MenuPrompt
}

/** The prompt is a text field (a rename or a new collection's draft), so the keyboard is in use. */
val MenuPrompt?.isTextEntry: Boolean
    get() = this is MenuPrompt.Rename || this is MenuPrompt.NewCollection || this is MenuPrompt.RenameCollection

/**
 * nx-redux's context menu, opened with MENU (or a long press) over [entry] in a game list, on Home's Continue card, a
 * pinned game or a shelf game, over [tool] on a tool or pinned tool row, or over the [collection] (its name) on a Collections-tab row.
 * One target is set: with [tool] or [collection], [entry] is null and only that target's items are offered.
 * [busy]: a choice is being applied; every input is swallowed until the screen is rebuilt.
 */
data class ContextMenu(
    val entry: Listed?,
    val items: List<ContextItem>,
    val index: Int = 0,
    val sub: SubMenu? = null,
    val prompt: MenuPrompt? = null,
    val busy: Boolean = false,
    val tool: ToolRow? = null,
    val collection: String? = null,
    /** The caption of a menu with no target: the hidden-tab items alone. */
    val title: String? = null,
) {
    /** The modal's caption: the game's shown name, the tool's label, the collection's name, or [title]. */
    val caption: String get() = entry?.shown ?: tool?.label ?: collection ?: title.orEmpty()
}

object ContextMenus {
    private val pin = ContextItem(ContextKind.PIN, "Pin Item")
    private val unpin = ContextItem(ContextKind.UNPIN, "Unpin Item")
    private val hide = ContextItem(ContextKind.HIDE, "Hide Game")
    private val rename = ContextItem(ContextKind.RENAME, "Rename Rom")
    private val addToCollection = ContextItem(ContextKind.ADD_TO_COLLECTION, "Add to Collection")
    private val gameSettings = ContextItem(ContextKind.GAME_SETTINGS, "Game Settings")
    private val fetchArt = ContextItem(ContextKind.FETCH_ART, "Fetch art")
    private val netplayItem = ContextItem(ContextKind.NETPLAY, "Netplay")
    private val fromStartItem = ContextItem(ContextKind.PLAY_FROM_START, "Play from start")

    /**
     * The handheld's table. Console lists: Pin/Unpin (launchable games only), Hide, Rename, Add to Collection, Game
     * Settings (a game with a core), then Emulator (multi-emulator consoles) or Console (Unassigned: no pin, no collection,
     * no Game Settings, since the game cannot launch). Recently Played: Remove only, after the lead items. A collection:
     * the console items plus Remove from Collection. A search list: a collection's items without Remove from Collection.
     * Hidden: none (A unhides). The Android games console: Pin or Unpin only (Settings › Library › Android games is the
     * only way out of the console; [pinned]: in the Android pins).
     * [netplay]: the platform has Wi-Fi netplay, so Netplay follows Game Settings for a game whose tag has it.
     * [fetchArt]: the game's console is one ScreenScraper knows and the build can fetch, so Fetch art follows them
     * (console, collection and search lists).
     * The lead items: [canResume] (the game has a save state, which A resumes) leads with Play from start;
     * [netplayLeads] (the Netplay hint hidden, alone or with the bar) puts Netplay next, and not again below. Hidden and
     * Android lists and Unassigned games have neither.
     */
    fun itemsFor(
        source: ListSource, entry: Listed, pinned: Boolean, fetchArt: Boolean = false, netplay: Boolean = false,
        canResume: Boolean = false, netplayLeads: Boolean = false,
    ): List<ContextItem> {
        val lead = leadItems(entry, canResume, netplay && netplayLeads)
        val below = netplay && !netplayLeads && hasNetplay(entry)
        return when (source) {
        is ListSource.Console -> when {
            source.system.android -> listOf(if (pinned) unpin else pin)
            source.system.unassigned -> listOf(hide, rename, ContextItem(ContextKind.CONSOLE, "Console"))
            else -> buildList {
                addAll(lead)
                add(if (pinned) unpin else pin); add(hide); add(rename); add(addToCollection)
                if (hasCore(entry)) add(gameSettings)
                if (below) add(netplayItem)
                if (fetchArt) add(this@ContextMenus.fetchArt)
                if ((CoreRegistry.consoleOf(entry.system.tag)?.tags?.size ?: 0) > 1) add(ContextItem(ContextKind.EMULATOR, "Emulator"))   // the game's own tag (a merged console mixes them)
            }
        }
        ListSource.Recent -> lead + ContextItem(ContextKind.REMOVE_RECENT, "Remove from Recently Played")
        is ListSource.Collection -> buildList {
            addAll(lead)
            add(if (pinned) unpin else pin); add(hide); add(rename); add(addToCollection)
            if (hasCore(entry)) add(gameSettings)
            if (below) add(netplayItem)
            if (fetchArt) add(this@ContextMenus.fetchArt)
            add(ContextItem(ContextKind.REMOVE_FROM_COLLECTION, "Remove from Collection"))
        }
        is ListSource.Search -> buildList {
            addAll(lead)
            add(if (pinned) unpin else pin); add(hide); add(rename); add(addToCollection)
            if (hasCore(entry)) add(gameSettings)
            if (below) add(netplayItem)
            if (fetchArt) add(this@ContextMenus.fetchArt)
        }
        is ListSource.Filtered -> itemsFor(source.parent, entry, pinned, fetchArt, netplay, canResume, netplayLeads)   // its list's own
        ListSource.Hidden -> emptyList()   // A unhides directly; UNHIDE stays in the enum for act's exhaustive when
        }
    }

    /**
     * MENU on the main menu: an item for each of [hidden] (the tabs Layouts hides that have content; Home never), in the
     * tabs' fixed order, after the item's own items. Picking one opens that tab over the current one.
     */
    fun hiddenTabItems(hidden: List<MenuCategory>): List<ContextItem> = hidden.sortedBy { it.ordinal }.mapNotNull { c ->
        when (c) {
            MenuCategory.CONSOLES -> ContextItem(ContextKind.OPEN_CONSOLES, "Consoles")
            MenuCategory.COLLECTIONS -> ContextItem(ContextKind.OPEN_COLLECTIONS, "Collections")
            MenuCategory.TOOLS -> ContextItem(ContextKind.OPEN_TOOLS, "Tools")
            MenuCategory.HOME -> null
        }
    }

    /** The tab a hidden-tab item opens; null for every other item. */
    fun tabOf(kind: ContextKind): MenuCategory? = when (kind) {
        ContextKind.OPEN_CONSOLES -> MenuCategory.CONSOLES
        ContextKind.OPEN_COLLECTIONS -> MenuCategory.COLLECTIONS
        ContextKind.OPEN_TOOLS -> MenuCategory.TOOLS
        else -> null
    }

    /** A game menu's first items: Play from start ([canResume]: A resumes instead), then Netplay when [netplay] leads. */
    private fun leadItems(entry: Listed, canResume: Boolean, netplay: Boolean): List<ContextItem> = buildList {
        if (canResume) add(fromStartItem)
        if (netplay && hasNetplay(entry)) add(netplayItem)
    }

    /** Game Settings needs the core whose options it edits. */
    private fun hasCore(entry: Listed) = !entry.system.unassigned && (entry.system.core ?: CoreRegistry.forTag(entry.system.tag)) != null

    /** Netplay (the context menu item, Y on a game list): the game's own tag has netplay cores and the game can launch. */
    fun hasNetplay(entry: Listed): Boolean = hasCore(entry) && !entry.system.android && NetplayCores.supported(entry.system.tag)

    /**
     * MENU on a pinned game row (Home's Pinned row, the All pinned list); [fromStart]: the game has a save state (A
     * resumes it), so Play from start leads; [netplay]: hidden hints put Netplay next.
     */
    fun pinRowItems(netplay: Boolean = false, fromStart: Boolean = false): List<ContextItem> =
        homeLead(netplay, fromStart) + ContextItem(ContextKind.UNPIN, "Unpin")

    /** MENU on Home's Continue card or a shelf game: a pinned game row's menu (Unpin), or Pin when its game is not pinned. */
    fun homeGameItems(pinned: Boolean, netplay: Boolean = false, fromStart: Boolean = false): List<ContextItem> =
        if (pinned) pinRowItems(netplay, fromStart) else homeLead(netplay, fromStart) + ContextItem(ContextKind.PIN, "Pin")

    private fun homeLead(netplay: Boolean, fromStart: Boolean): List<ContextItem> = buildList {
        if (fromStart) add(fromStartItem)
        if (netplay) add(netplayItem)
    }

    /**
     * MENU on a tool row: Pin, or Unpin when [pinned] (tool pins live in the app-private `tool-pins.txt`); an app's
     * ([app]) also Remove from Tools (it stays in the app drawer).
     */
    fun toolItems(pinned: Boolean, app: Boolean = false): List<ContextItem> =
        listOf(if (pinned) ContextItem(ContextKind.UNPIN_TOOL, "Unpin") else ContextItem(ContextKind.PIN_TOOL, "Pin")) +
            listOfNotNull(ContextItem(ContextKind.REMOVE_FROM_TOOLS, "Remove from Tools").takeIf { app })

    /** MENU on an app in the drawer: in or out of Tools ([inTools]), then on or off Home ([pinned]); each on its own. */
    fun drawerItems(inTools: Boolean, pinned: Boolean): List<ContextItem> = listOf(
        if (inTools) ContextItem(ContextKind.REMOVE_FROM_TOOLS, "Remove from Tools") else ContextItem(ContextKind.ADD_TO_TOOLS, "Add to Tools"),
        if (pinned) ContextItem(ContextKind.UNPIN_TOOL, "Unpin from Home") else ContextItem(ContextKind.PIN_TOOL, "Pin to Home"),
    )

    /** MENU on a Collections-tab row: Rename (the name prompt) and Delete (a confirm; the games are never touched). */
    fun collectionItems(): List<ContextItem> =
        listOf(ContextItem(ContextKind.RENAME_COLLECTION, "Rename"), ContextItem(ContextKind.DELETE_COLLECTION, "Delete"))

    /** The Add to Collection picker: the existing names, then "New Collection…" whose value is "". */
    fun collectionMenu(names: List<String>): SubMenu = SubMenu("Add to Collection", names + "New Collection…", names + "", 0)

    /**
     * The console's tags, "TAG (core id)" with the handheld's tag (GPGX_SMS shows as GPGX, [CoreRegistry.handheldTag]),
     * [system]'s (the game's own) tag highlighted. Null for single-emulator consoles.
     */
    fun emulatorMenu(system: SystemFolder): SubMenu? {
        val console = CoreRegistry.consoleOf(system.tag)?.takeIf { it.tags.size > 1 } ?: return null
        val current = console.tags.indexOf(system.tag).coerceAtLeast(0)
        val labels = console.tags.map { "${CoreRegistry.handheldTag(it)} (${CoreRegistry.forTag(it)?.id ?: "?"})" }
        return SubMenu("Emulator", labels, console.tags, current, current)
    }

    /**
     * The consoles that can take [fileName]: a member tag's core runs its lowercase extension, and the console owns that
     * extension (or the console owns none, as SGB). `.chd` offers PSP, PlayStation, Sega CD and TurboGrafx-16, not Sega
     * Genesis. An archive is unpacked at launch, so every console with a core is offered. Null when none can, since
     * assigning it would leave the file unassigned.
     */
    fun consoleMenu(fileName: String): SubMenu? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val fits = if (ext in Names.ARCHIVE_EXTENSIONS) {
            CoreRegistry.consoles.filter { c -> c.tags.any { CoreRegistry.forTag(it) != null } }
        } else {
            CoreRegistry.consoles.filter { c ->
                c.tags.any { ext in (CoreRegistry.forTag(it)?.extensions ?: emptySet()) } && (c.extensions.isEmpty() || ext in c.extensions)
            }
        }
        if (fits.isEmpty()) return null
        return SubMenu("Console", fits.map { it.displayName }, fits.map { it.id }, 0)
    }
}
