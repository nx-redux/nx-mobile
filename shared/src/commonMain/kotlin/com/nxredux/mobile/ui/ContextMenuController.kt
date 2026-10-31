package com.nxredux.mobile.ui

import com.nxredux.mobile.library.CollectionRename
import com.nxredux.mobile.library.CollectionWrite
import com.nxredux.mobile.library.Collections
import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.lines
import com.nxredux.mobile.library.Names
import com.nxredux.mobile.library.path
import com.nxredux.mobile.library.Sidecars
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.settings.AppSettings
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.storage.Recents
import com.nxredux.mobile.storage.Shortcut
import com.nxredux.mobile.storage.Shortcuts
import com.nxredux.mobile.storage.ToolPins
import com.nxredux.mobile.storage.UserDataStore
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The context menu a screen hosts: a game list's, the main menu's over a game or tool row, or the app drawer's; null elsewhere. */
internal val Screen.contextMenu: ContextMenu?
    get() = when (this) { is Screen.Games -> menu; is Screen.Systems -> menu; is Screen.ToolApps -> menu; else -> null }

/**
 * What [ContextMenuController] needs from [AppState] that is AppState's own state or behaviour. Nothing here is a
 * copy: pins, the main-menu rows, the index, the playable cache and the collections flag stay single-sourced there.
 */
internal interface ContextMenuHost {
    /** shortcuts.txt as last read (read only; a pin change is written, then [rebuildMainRows] re-reads it). */
    val pins: List<Shortcut>

    /** tool-pins.txt as last read or written (app-private; never shortcuts.txt). */
    val toolPins: List<String>

    /** Writes [keys] ([pinKey]s) as the tool pins off the main thread, then keeps them in memory; false (nothing changed) when the write fails. */
    suspend fun writeToolPins(keys: List<String>): Boolean

    /** Moves the list highlight to [index] (a long press on a row that was not highlighted). */
    fun select(index: Int)

    /** Shows [text] as the one-line notice (clears itself after a few seconds). */
    fun showNotice(text: String)

    /** Rebuilds the main menu's tabs (re-reading pins, recents and the collection names). */
    suspend fun rebuildMainRows()

    /** [name]'s file was just written: its count on the Collections tab is counted again (after the next rebuild). */
    fun collectionChanged(name: String)

    /** On the main menu (rebuilt): the highlight goes to the collection [name]'s row, wherever the rebuild sorted it. */
    fun selectCollection(name: String)

    /** The main menu's context menu closed: the tabs it held back ([AppState]'s swap waits while one is open) show now. */
    fun menuClosed()

    /** Rebuilds the open list or main menu after an action; [select]: put the row on that game path. */
    suspend fun refreshOpenList(select: String? = null)

    /** A full scan (a stale list after a rename). */
    fun rescan()

    /** Adds the app [id] to the Tools tab (last) or removes it ([on] false); the drawer's marks and the tabs follow. */
    suspend fun setInTools(id: String, on: Boolean) {}

    /** Hides or unhides [entry]'s game (shared with the hidden list's direct unhide). */
    suspend fun applyHidden(entry: Listed, hidden: Boolean, closeMenu: Boolean)

    /**
     * Renames [game] to [name] in the index in memory and on disk and refreshes the playable cache. False when there
     * is no index in memory or the rename changed nothing (a stale list): the caller rescans.
     */
    suspend fun renameInIndex(game: GameEntry, name: String): Boolean

    /** Game Settings: the settings page for [entry]'s own tag, writing its `<rom>.cfg`; B comes back to this list. */
    fun openGameSettings(entry: Listed)

    /** After a tag switch's sidecar write (and copy prompt): moves the game in the index and opens the new tag's list. */
    fun finishSwitch(game: GameEntry, from: SystemFolder, tag: String)

    /** Fetch art is offered for [entry]: a console ScreenScraper knows, in a build that can fetch. */
    fun canFetchArt(entry: Listed): Boolean = false

    /** Fetches [entry]'s art under the loading overlay, then shows the result as a notice. */
    fun fetchArt(entry: Listed) {}

    /** [entry] can be played over netplay here (the platform has netplay, and the game's tag has it now). */
    fun netplayOffered(entry: Listed): Boolean = false

    /** Netplay: opens the Netplay sheet over the list for [entry]. */
    fun openNetplay(entry: Listed) {}

    /** Y Netplay is not in the hint bar (hidden alone or with the bar): a game's menu leads with Netplay. */
    val netplayInMenu: Boolean get() = false

    /** Layouts hides the game lists' achievements panel icon and hint: a game's menu offers the panel. */
    val achievementsInMenu: Boolean get() = false

    /** Opens the achievements panel on the game list's highlighted game. */
    fun openAchievements() {}

    /** [entry] has a save state to resume (what A does on it); read off the main thread. */
    suspend fun canResume(entry: Listed): Boolean = false

    /** The menu's Play from start: launches [entry] without its save state. */
    fun playFromStart(entry: Listed) {}

    /** The tabs Layouts hides that have content, for MENU's hidden-tab items; empty while a hidden tab is pushed. */
    fun hiddenTabs(): List<MenuCategory> = emptyList()

    /** Opens [category], a hidden tab, over the main menu. */
    fun openHiddenTab(category: MenuCategory) {}
}

/**
 * The context menu, hosted by a game list or the main menu (a game or tool row): its navigation, prompts and actions
 * (pin, hide, rename, collections, Game Settings, the Emulator / Console switch). The menu itself lives in the host screen
 * ([Screen.Games.menu], [Screen.Systems.menu]) of [AppState]'s screen flow; this class keeps no state of its own.
 *
 * Handed in: AppState's [_screen] (read and written), [_selection] (read), [_error] (written), its [scope], the
 * [userData], [settings] (default tags, Android pins), [sidecars] and [collections] stores, the process-wide
 * [recentsLock], and the [host] for everything else.
 */
internal class ContextMenuController(
    private val scope: CoroutineScope,
    private val _screen: MutableStateFlow<Screen>,
    private val _selection: StateFlow<ListSelection>,
    private val _error: MutableStateFlow<String?>,
    private val userData: UserDataStore,
    private val settings: AppSettings,
    private val sidecars: Sidecars,
    private val collections: Collections,
    private val recentsLock: Mutex,
    private val host: ContextMenuHost,
) {
    private fun setMenu(m: ContextMenu?) {
        val s = _screen.value
        _screen.value = when (s) {
            is Screen.Games -> s.copy(menu = m)
            is Screen.Systems -> s.copy(menu = m)
            is Screen.ToolApps -> s.copy(menu = m)
            else -> s
        }
        if (m == null && s is Screen.Systems && s.menu != null) host.menuClosed()
    }

    /** The menu's game; every game item is offered only on a menu with an entry ([act] closes a tool menu otherwise). */
    private val ContextMenu.game: Listed get() = checkNotNull(entry) { "a game action on a tool menu" }

    /** The system [m]'s game is listed under, for Emulator / Console: its own tag, also in a merged console; null in a mixed list. */
    private fun consoleOf(m: ContextMenu): SystemFolder? = m.game.system.takeIf { (_screen.value as? Screen.Games)?.source?.base is ListSource.Console }

    /**
     * MENU or a long press on row [index] (null = the highlighted row): a game list's menu; on the main menu, the row's
     * own items ([rowMenu]), then the hidden tabs ([openTabMenu]'s items).
     */
    fun openContextMenu(index: Int? = null) {
        when (val s = _screen.value) {
            is Screen.Games -> {
                if (s.menu?.busy == true) return
                val i = index ?: _selection.value.index
                val e = s.entries.getOrNull(i) ?: return
                if (s.source == ListSource.Hidden) return
                if (index != null) host.select(i)
                if (!e.system.unassigned && !e.system.android) {
                    // The save is read off the main thread, so the menu opens when it is known (the list must not have moved on).
                    scope.launch {
                        val resume = host.canResume(e)
                        if (_screen.value !== s || s.menu != null) return@launch
                        gameMenu(s, e, canResume = resume)
                    }
                } else {
                    gameMenu(s, e, canResume = false)
                }
            }
            is Screen.Systems -> {
                if (s.menu?.busy == true) return
                val i = index ?: _selection.value.index
                val row = s.rows.getOrNull(i)
                // The hidden tabs after the item's own items; a row without a menu (a console, Pick a game, "+N", an empty
                // tab) gets one holding just these. A pushed tab never offers them again.
                val tabs = if (s.pushed) emptyList() else ContextMenus.hiddenTabItems(host.hiddenTabs())
                fun show(fromStart: Boolean) {
                    val own = rowMenu(row, fromStart)
                    val menu = when {
                        own != null -> own.copy(items = own.items + tabs)
                        tabs.isNotEmpty() -> ContextMenu(null, tabs, title = APP_NAME)
                        else -> return
                    }
                    if (index != null && row != null) host.select(i)
                    _screen.value = s.copy(menu = menu)
                }
                // A Home game's save state is read off the main thread, as a game list's: Play from start leads when it has one.
                val game = row.listed()?.takeIf { !it.system.unassigned && !it.system.android }
                if (game == null) show(fromStart = false)
                else scope.launch {
                    val resume = host.canResume(game)
                    if (_screen.value !== s) return@launch   // the main menu moved on (or a menu opened) meanwhile
                    show(resume)
                }
            }
            // The app drawer: the app's Tools and Home items (the Android games picker has no menu).
            is Screen.ToolApps -> {
                if (s.pick != AppPick.TOOLS || s.menu?.busy == true) return
                val i = index ?: _selection.value.index
                val app = s.apps.getOrNull(i) ?: return
                if (index != null) host.select(i)
                val tool = ToolRow(ToolKind.APP, app.label, null, appId = app.id)
                _screen.value = s.copy(menu = ContextMenu(null, ContextMenus.drawerItems(inTools = app.shown, pinned = tool.pinKey in host.toolPins), tool = tool))
            }
            else -> Unit
        }
    }

    /**
     * A long press on the tab row or the single-tab title: the hidden-tab items alone (nothing when none is hidden, or
     * over a pushed tab). True when the menu opened.
     */
    fun openTabMenu(): Boolean {
        val s = _screen.value as? Screen.Systems ?: return false
        if (s.menu != null || s.pushed || s.search != null) return false
        val items = ContextMenus.hiddenTabItems(host.hiddenTabs())
        if (items.isEmpty()) return false
        _screen.value = s.copy(menu = ContextMenu(null, items, title = APP_NAME))
        return true
    }

    /** Opens [s]'s menu on [e]; [canResume]: it has a save state, so Play from start leads. */
    private fun gameMenu(s: Screen.Games, e: Listed, canResume: Boolean) {
        val items = ContextMenus.itemsFor(
            s.source, e, pinned = pinned(e), fetchArt = host.canFetchArt(e), netplay = host.netplayOffered(e),
            canResume = canResume, netplayLeads = host.netplayInMenu,
        )
        // The achievements panel, last, while Layouts hides its icon and hint.
        val all = if (host.achievementsInMenu) items + ContextItem(ContextKind.ACHIEVEMENTS, "Achievements") else items
        if (all.isEmpty()) return
        _screen.value = s.copy(menu = ContextMenu(e, all))
    }

    /** [e] is pinned: an Android game in [AppSettings.androidPins], any other in shortcuts.txt. */
    private fun pinned(e: Listed): Boolean =
        if (e.system.android) e.game.fileName in settings.androidPins else host.pins.any { it.path in e.game.lines }

    /** A hidden Netplay hint (alone or with the bar) puts Y's Netplay in a Home game's menu. */
    private fun homeNetplay(e: Listed): Boolean = host.netplayInMenu && host.netplayOffered(e) && ContextMenus.hasNetplay(e)

    /** A main-menu row's menu; null for a row without one (a console, Pick a game, "+N"). */
    private fun rowMenu(row: MainRow?, fromStart: Boolean): ContextMenu? = when (row) {
        is MainRow.Collection -> ContextMenu(null, ContextMenus.collectionItems(), collection = row.name)
        is MainRow.Pin -> ContextMenu(row.entry, ContextMenus.pinRowItems(netplay = homeNetplay(row.entry), fromStart = fromStart))
        is MainRow.Shelf -> ContextMenu(row.entry, ContextMenus.homeGameItems(pinned = false, netplay = homeNetplay(row.entry), fromStart = fromStart))   // never a pinned game
        is MainRow.Continue -> ContextMenu(row.entry, ContextMenus.homeGameItems(pinned = host.pins.any { it.path in row.entry.game.lines }, netplay = homeNetplay(row.entry), fromStart = fromStart))
        is MainRow.Tool -> ContextMenu(null, ContextMenus.toolItems(pinned = row.row.pinKey in host.toolPins, app = row.row.kind == ToolKind.APP), tool = row.row)
        is MainRow.PinnedTool -> ContextMenu(null, ContextMenus.toolItems(pinned = true), tool = row.row)
        is MainRow.MoreTools -> null   // Home's "+N": no menu
        else -> null
    }

    fun closeContextMenu() = setMenu(null)

    /** Columns the open menu (or sub-menu) is drawn in, reported by the overlay; 1 until it lays out. */
    var menuColumns = 1
        private set

    fun onMenuLayout(columns: Int) { menuColumns = columns.coerceIn(1, 2) }

    /** Tap on a menu row: highlight it (a second tap on the highlighted row is [menuOpen]). */
    fun menuSelect(i: Int) {
        val m = _screen.value.contextMenu?.takeIf { !it.busy } ?: return
        setMenu(if (m.sub != null) m.copy(sub = m.sub.copy(index = i)) else m.copy(index = i))
    }

    fun menuOpen() { _screen.value.contextMenu?.let { onMenuButton(it, NxButton.A) } }

    fun onMenuButton(m: ContextMenu, button: NxButton) {
        if (m.busy) return   // a choice is being applied; rebuilding the screen ends this
        m.prompt?.let { p -> onPromptButton(m, p, button); return }
        m.sub?.let { sub ->
            when (button) {
                NxButton.UP, NxButton.DOWN, NxButton.LEFT, NxButton.RIGHT ->
                    setMenu(m.copy(sub = sub.copy(index = menuStep(sub.index, sub.values.size, menuColumns, button))))
                NxButton.A -> chooseSub(m, sub)
                NxButton.B -> setMenu(m.copy(sub = null))
                NxButton.MENU -> closeContextMenu()
                else -> Unit
            }
            return
        }
        when (button) {
            NxButton.UP, NxButton.DOWN, NxButton.LEFT, NxButton.RIGHT ->
                setMenu(m.copy(index = menuStep(m.index, m.items.size, menuColumns, button)))
            NxButton.A -> act(m, m.items[m.index].kind)
            NxButton.B, NxButton.MENU -> closeContextMenu()
            else -> Unit
        }
    }

    /** The text prompt's live text (the composable reports every change), so A can confirm with it. */
    fun menuDraft(text: String) {
        val m = _screen.value.contextMenu?.takeIf { !it.busy } ?: return
        when (val p = m.prompt) {
            is MenuPrompt.Rename -> setMenu(m.copy(prompt = p.copy(draft = text)))
            is MenuPrompt.NewCollection -> setMenu(m.copy(prompt = p.copy(draft = text)))
            is MenuPrompt.RenameCollection -> setMenu(m.copy(prompt = p.copy(draft = text)))
            else -> Unit
        }
    }

    private fun onPromptButton(m: ContextMenu, p: MenuPrompt, button: NxButton) {
        // A text prompt: the d-pad moves between the field and the buttons ([promptFocusStep]); A on Cancel cancels. A
        // confirm (Delete) is lit by the host's dialog focus ([DialogFocus]) instead.
        if (p.isTextEntry) {
            promptFocusStep(m.promptFocus, button)?.let { setMenu(m.copy(promptFocus = it)); return }
            if (button == NxButton.A && m.promptFocus == 1) { closeContextMenu(); return }
        }
        when (p) {
            is MenuPrompt.Rename -> when (button) {
                NxButton.A -> confirmRename(m, p.draft)
                NxButton.B, NxButton.MENU -> closeContextMenu()
                else -> Unit
            }
            is MenuPrompt.NewCollection -> when (button) {
                NxButton.A -> confirmNewCollection(m, p.draft)
                NxButton.B, NxButton.MENU -> closeContextMenu()
                else -> Unit
            }
            is MenuPrompt.RenameCollection -> when (button) {
                NxButton.A -> confirmRenameCollection(m, p)
                NxButton.B, NxButton.MENU -> closeContextMenu()
                else -> Unit
            }
            is MenuPrompt.DeleteCollection -> when (button) {
                NxButton.A -> deleteCollection(m, p.name)
                NxButton.B, NxButton.MENU -> closeContextMenu()
                else -> Unit
            }
        }
    }

    /** A on a menu item. Emulator, Console and Add to Collection open their submenus; Rename opens the text prompt. */
    private fun act(m: ContextMenu, kind: ContextKind) {
        when (kind) {
            ContextKind.PIN_TOOL -> { m.tool?.let { setToolPinned(m, it.pinKey, true) } ?: closeContextMenu(); return }
            ContextKind.UNPIN_TOOL -> { m.tool?.let { setToolPinned(m, it.pinKey, false) } ?: closeContextMenu(); return }
            ContextKind.ADD_TO_TOOLS, ContextKind.REMOVE_FROM_TOOLS -> { m.tool?.appId?.let { setInTools(m, it, kind == ContextKind.ADD_TO_TOOLS) } ?: closeContextMenu(); return }
            ContextKind.RENAME_COLLECTION -> { m.collection?.let { setMenu(m.copy(prompt = MenuPrompt.RenameCollection(it), promptFocus = -1)) } ?: closeContextMenu(); return }
            // A confirm has no field: its focus starts on Delete.
            ContextKind.DELETE_COLLECTION -> { m.collection?.let { setMenu(m.copy(prompt = MenuPrompt.DeleteCollection(it), promptFocus = -1)) } ?: closeContextMenu(); return }
            ContextKind.OPEN_CONSOLES, ContextKind.OPEN_COLLECTIONS, ContextKind.OPEN_TOOLS -> {
                closeContextMenu()
                ContextMenus.tabOf(kind)?.let(host::openHiddenTab)
                return
            }
            ContextKind.ACHIEVEMENTS -> { closeContextMenu(); host.openAchievements(); return }
            else -> if (m.entry == null) { closeContextMenu(); return }
        }
        when (kind) {
            ContextKind.PIN_TOOL, ContextKind.UNPIN_TOOL, ContextKind.ADD_TO_TOOLS, ContextKind.REMOVE_FROM_TOOLS,
            ContextKind.RENAME_COLLECTION, ContextKind.DELETE_COLLECTION, ContextKind.OPEN_CONSOLES, ContextKind.OPEN_COLLECTIONS, ContextKind.OPEN_TOOLS, ContextKind.ACHIEVEMENTS -> Unit   // handled above
            ContextKind.EMULATOR -> consoleOf(m)?.let { ContextMenus.emulatorMenu(it) }?.let { setMenu(m.copy(sub = it)) } ?: closeContextMenu()
            ContextKind.CONSOLE -> ContextMenus.consoleMenu(m.game.game.fileName)?.let { setMenu(m.copy(sub = it)) } ?: run {
                closeContextMenu()
                _error.value = "No bundled emulator runs .${m.game.game.fileName.substringAfterLast('.', "").lowercase()} files"
            }
            ContextKind.PIN -> pin(m)
            ContextKind.UNPIN -> unpin(m)
            ContextKind.HIDE -> setHidden(m, true)
            ContextKind.UNHIDE -> setHidden(m, false)
            ContextKind.REMOVE_RECENT -> removeRecent(m)
            ContextKind.REMOVE_FROM_COLLECTION -> ((_screen.value as? Screen.Games)?.source?.base as? ListSource.Collection)?.let { removeFromCollection(m, it.name) } ?: closeContextMenu()
            ContextKind.RENAME -> setMenu(m.copy(prompt = MenuPrompt.Rename(m.game.game.shownName), promptFocus = -1))
            ContextKind.ADD_TO_COLLECTION -> openCollectionPicker(m)
            ContextKind.GAME_SETTINGS -> { closeContextMenu(); host.openGameSettings(m.game) }
            ContextKind.FETCH_ART -> { closeContextMenu(); host.fetchArt(m.game) }
            ContextKind.NETPLAY -> { closeContextMenu(); host.openNetplay(m.game) }
            ContextKind.PLAY_FROM_START -> { closeContextMenu(); host.playFromStart(m.game) }
        }
    }

    /** Runs a menu choice with the menu marked busy; a provider throw becomes the error dialog (the scope has no handler). */
    private fun menuAction(m: ContextMenu, block: suspend () -> Unit) {
        setMenu(m.copy(busy = true))
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                closeContextMenu()
                _error.value = "Something went wrong: ${e.message ?: e::class.simpleName}"
            }
        }
    }

    /**
     * Pins [m]'s game: an Android game's app id in [AppSettings.androidPins], any other game in shortcuts.txt. The Home
     * cap ([Shortcuts.MAX]) counts both lists.
     */
    private fun pin(m: ContextMenu) = menuAction(m) {
        val game = m.game.game
        val android = m.game.system.android
        val known = if (android) game.fileName in settings.androidPins else host.pins.any { it.path in game.lines }
        if (!known && host.pins.size + settings.androidPins.size >= Shortcuts.MAX) {
            closeContextMenu(); host.showNotice("Pins are full (${Shortcuts.MAX}). Unpin one first."); return@menuAction
        }
        if (android) {
            if (!known) settings.androidPins = settings.androidPins + game.fileName
        } else {
            val added = Shortcuts.add(host.pins, Shortcut(game.path, game.shownName))
            if (added == null) { closeContextMenu(); host.showNotice("Pins are full (${Shortcuts.MAX}). Unpin one first."); return@menuAction }
            if (!userData.writePins(added)) { closeContextMenu(); _error.value = "Could not save the pin"; return@menuAction }
        }
        host.rebuildMainRows()
        closeContextMenu()
        host.refreshOpenList()
        host.showNotice("Pinned")
    }

    /** Unpins [m]'s game from its list ([AppSettings.androidPins] for an Android game, else shortcuts.txt). */
    private fun unpin(m: ContextMenu) = menuAction(m) {
        if (m.game.system.android) {
            settings.androidPins = settings.androidPins - m.game.game.fileName
        } else {
            // Every line for the game: a pin written for a copy the scan hides behind it is this game's pin too.
            val left = m.game.game.lines.fold(host.pins) { pins, line -> Shortcuts.remove(pins, line) }
            if (!userData.writePins(left)) { closeContextMenu(); _error.value = "Could not save the pin"; return@menuAction }
        }
        host.rebuildMainRows()
        closeContextMenu()
        host.refreshOpenList()
        host.showNotice("Unpinned")
    }

    /** Pins or unpins the tool [key] (its [pinKey]) in the app-private tool-pins.txt (shortcuts.txt is never touched), then rebuilds the main menu. */
    private fun setToolPinned(m: ContextMenu, key: String, pinned: Boolean) = menuAction(m) {
        val next = if (pinned) ToolPins.add(host.toolPins, key) else ToolPins.remove(host.toolPins, key)
        if (!host.writeToolPins(next)) { closeContextMenu(); _error.value = "Could not save the pin"; return@menuAction }
        host.rebuildMainRows()
        closeContextMenu()
        host.refreshOpenList()
        host.showNotice(if (pinned) "Pinned" else "Unpinned")
    }

    /** Adds the app [id] to Tools or removes it ([on]), then says so. */
    private fun setInTools(m: ContextMenu, id: String, on: Boolean) = menuAction(m) {
        host.setInTools(id, on)
        closeContextMenu()
        host.refreshOpenList()
        host.showNotice(if (on) "Added to Tools" else "Removed from Tools")
    }

    /** Hides or unhides [m]'s game: hidden.txt, then the playable cache, the main menu and the open list are rebuilt. */
    private fun setHidden(m: ContextMenu, hidden: Boolean) = menuAction(m) { host.applyHidden(m.game, hidden, closeMenu = true) }

    private fun removeRecent(m: ContextMenu) = menuAction(m) {
        recentsLock.withLock { userData.writeRecents(m.game.game.lines.fold(userData.readRecents()) { r, line -> Recents.remove(r, line) }) }
        host.rebuildMainRows()
        closeContextMenu()
        host.refreshOpenList()
    }

    private fun removeFromCollection(m: ContextMenu, name: String) = menuAction(m) {
        if (!m.game.game.lines.all { collections.remove(name, it) }) { closeContextMenu(); _error.value = "Could not save the collection"; return@menuAction }
        host.collectionChanged(name)
        host.rebuildMainRows()   // the collection's count on the Collections tab
        closeContextMenu()
        host.refreshOpenList()
        host.showNotice("Removed from $name")
    }

    /** Rename Rom: a blank, invalid or unchanged name closes the menu with no write. */
    private fun confirmRename(m: ContextMenu, draft: String) {
        val g = m.game.game
        val name = Names.validAlias(draft)
        if (name == null || name == g.shownName) { closeContextMenu(); return }
        menuAction(m.copy(prompt = null)) {
            val key = if (g.lookupDir != g.relativeDir) g.relativeDir.substringAfterLast('/') else g.fileName   // a disc folder is keyed by its folder name
            val ok = runCatching { sidecars.setName(g.sourceId, g.lookupDir, key, name) }.getOrElse { if (it is CancellationException) throw it; false }
            if (!ok) { closeContextMenu(); _error.value = "Could not save the name"; return@menuAction }
            if (!host.renameInIndex(g, name)) { closeContextMenu(); host.rescan(); return@menuAction }   // stale list: the alias is saved, the scan shows it
            val path = g.path
            recentsLock.withLock {
                val recents = userData.readRecents()
                if (recents.any { it.path == path }) userData.writeRecents(Recents.updateAlias(recents, path, name))
            }
            if (host.pins.any { it.path == path }) userData.writePins(Shortcuts.rename(host.pins, path, name))
            host.rebuildMainRows()
            closeContextMenu()
            host.refreshOpenList(select = path)
            host.showNotice("Renamed")
        }
    }

    /** Add to Collection: loads the names, then shows the picker. */
    private fun openCollectionPicker(m: ContextMenu) = menuAction(m) {
        setMenu(m.copy(busy = false, sub = ContextMenus.collectionMenu(collections.names())))
    }

    private fun addToCollection(m: ContextMenu, name: String) = menuAction(m.copy(sub = null, prompt = null)) {
        when (collections.add(name, m.game.game.path)) {
            CollectionWrite.ADDED -> host.showNotice("Added to $name")
            CollectionWrite.ALREADY -> host.showNotice("Already in $name")
            CollectionWrite.FAILED -> _error.value = "Could not save the collection"
        }
        host.collectionChanged(name)
        host.rebuildMainRows()
        closeContextMenu()
    }

    private fun confirmNewCollection(m: ContextMenu, draft: String) {
        val name = Names.validCollectionName(draft)
        if (name == null) {
            closeContextMenu()
            _error.value = INVALID_NAME
            return
        }
        addToCollection(m, name)
    }

    private fun chooseSub(m: ContextMenu, sub: SubMenu) {
        val value = sub.values.getOrNull(sub.index) ?: return
        when (m.items[m.index].kind) {
            ContextKind.EMULATOR -> { val from = consoleOf(m) ?: return closeContextMenu(); if (value == from.tag) closeContextMenu() else applyTag(m, value) }
            ContextKind.CONSOLE -> CoreRegistry.consoles.firstOrNull { it.id == value }?.let { c -> applyTag(m, c.defaultTagFor(settings.defaultTags[c.id])) }
            ContextKind.ADD_TO_COLLECTION -> if (value.isEmpty()) setMenu(m.copy(sub = null, prompt = MenuPrompt.NewCollection(), promptFocus = -1)) else addToCollection(m, value)
            ContextKind.PIN, ContextKind.UNPIN, ContextKind.HIDE, ContextKind.UNHIDE, ContextKind.RENAME,
            ContextKind.REMOVE_FROM_COLLECTION, ContextKind.REMOVE_RECENT, ContextKind.GAME_SETTINGS, ContextKind.FETCH_ART,
            ContextKind.PIN_TOOL, ContextKind.UNPIN_TOOL, ContextKind.ADD_TO_TOOLS, ContextKind.REMOVE_FROM_TOOLS,
            ContextKind.RENAME_COLLECTION, ContextKind.DELETE_COLLECTION, ContextKind.NETPLAY, ContextKind.PLAY_FROM_START,
            ContextKind.OPEN_CONSOLES, ContextKind.OPEN_COLLECTIONS, ContextKind.OPEN_TOOLS, ContextKind.ACHIEVEMENTS -> closeContextMenu()
        }
    }

    /**
     * A collection's Rename: the trimmed name must be a valid collection name ([Names.validCollectionName]) and not
     * another collection's in any case (the file system may ignore case). The same name is a no-op; a case-only change
     * renames. The file (and its app-private copy) is renamed with its lines; the main menu is rebuilt on the new name.
     */
    private fun confirmRenameCollection(m: ContextMenu, p: MenuPrompt.RenameCollection) {
        val name = Names.validCollectionName(p.draft)
        if (name == null) { closeContextMenu(); _error.value = INVALID_NAME; return }
        val old = p.initial
        if (name == old) { closeContextMenu(); return }
        menuAction(m.copy(prompt = null)) {
            when (collections.rename(old, name)) {
                CollectionRename.EXISTS -> { closeContextMenu(); _error.value = "There is already a collection named $name"; return@menuAction }
                CollectionRename.FAILED -> { closeContextMenu(); _error.value = "Could not rename the collection"; return@menuAction }
                CollectionRename.RENAMED -> Unit
            }
            host.collectionChanged(old)
            host.collectionChanged(name)
            host.rebuildMainRows()
            closeContextMenu()
            host.refreshOpenList()
            host.selectCollection(name)
            host.showNotice("Renamed")
        }
    }

    /** A collection's Delete, confirmed: only its file goes (never a game); the highlight lands on the row now in its place. */
    private fun deleteCollection(m: ContextMenu, name: String) = menuAction(m.copy(prompt = null)) {
        if (!collections.delete(name)) { closeContextMenu(); _error.value = "Could not delete the collection"; return@menuAction }
        host.collectionChanged(name)
        host.rebuildMainRows()   // the last one gone drops the tab
        closeContextMenu()
        host.refreshOpenList()
        host.showNotice("Deleted $name")
    }

    /** Writes the game's sidecar entry, then moves the game in the index and opens the new tag's list. */
    private fun applyTag(m: ContextMenu, tag: String) {
        val from = consoleOf(m) ?: return closeContextMenu()
        val game = m.game.game
        setMenu(m.copy(busy = true))
        scope.launch {
            // A provider can throw here (a revoked grant on a non-external-storage provider); the scope has no handler.
            val ok = runCatching { sidecars.set(game.sourceId, game.folder, game.relativeDir, game.fileName, tag) }
                .getOrElse { if (it is CancellationException) throw it; false }
            if (!ok) { _error.value = "Could not save the emulator choice"; closeContextMenu(); return@launch }
            host.finishSwitch(game, from, tag)
        }
    }

    private companion object {
        const val INVALID_NAME = "Names cannot be empty, start with a dot or contain / \\ : * ? \" < > |"
    }
}
