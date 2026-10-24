package com.nxredux.mobile.ui

import com.nxredux.mobile.launcher.AppIcons
import com.nxredux.mobile.launcher.AppLauncher
import com.nxredux.mobile.launcher.InstalledApp
import com.nxredux.mobile.settings.AppSettings
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.gridStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * What the Android apps picker and app launches need from [AppState]: the installed list and the Android games console
 * (both stay there: setup and the menu's Android games question use them too), the main rows, Settings and Back.
 */
internal interface AppsHost {
    fun showNotice(text: String)
    /** The installed apps listed again, and the Android games console rebuilt from them (an app gone meanwhile). */
    fun refreshInstalled()
    /** The picker listed [apps]: they are the installed list now. */
    fun installedLoaded(apps: List<InstalledApp>)
    /** Opened from a Settings page: its row is remembered for the way back. */
    fun rememberSettingsRow()
    fun back(): Boolean
    /** A on the drawer: opens the selected app, as a tap on it ([AppState.openSelected]). */
    fun openSelected()
    /** MENU on the drawer: the selected app's menu. */
    fun openContextMenu()
    /** The drawer's rows for [apps] (A–Z, each marked when it is in Tools). */
    fun drawerRows(apps: List<InstalledApp>): List<ToolAppRow>
    /** The drawer is a tool: it has the focus, on the top screen while the menu is on the second display. */
    fun toolOnTop()
    suspend fun rebuildMainRows()
    fun swapMainRows()
    /** Apps [ids] joined the Android games console: their Tools pins on Home become Android pins. */
    suspend fun moveToolPinsToAndroid(ids: List<String>)
    /** The Android games console rebuilt from the settings now; the rows and an open Android list follow. */
    suspend fun applyAndroidConsole()
}

/**
 * Android apps in the launcher: the apps grid (Tools -> Apps, the drawer: open an app, its menu puts it in Tools; Settings
 * -> Library -> Android games: which apps make the Android games console) and starting an app (an Android game, a Tools
 * app row).
 */
internal class AppsController(
    private val scope: CoroutineScope,
    private val screen: MutableStateFlow<Screen>,
    private val selection: MutableStateFlow<ListSelection>,
    private val settings: AppSettings,
    private val loader: ScreenLoader,
    private val launcher: AppLauncher?,
    private val appIcons: AppIcons?,
    private val host: AppsHost,
) {
    private var toolAppsColumns = 0   // the Android apps picker's column count; 0 until it lays out (it steps as one column)

    /**
     * An Android game: the platform starts its app; nothing is recorded (no recent, no game time). A failed start (the
     * app gone meanwhile) says so and re-lists the apps, so the console drops it.
     */
    fun launchApp(entry: Listed) {
        if (launcher?.launch(entry.game.fileName) == true) return
        host.showNotice("Could not open ${entry.shown}")
        host.refreshInstalled()
    }

    /** A Tools row for an Android app ([ToolKind.APP]): the platform starts it; a failed start says so and rebuilds the rows. */
    fun openApp(row: ToolRow) {
        row.appId?.let { id ->
            if (launcher?.launch(id) == true) return
            host.showNotice("Could not open ${row.label}")
            scope.launch { host.rebuildMainRows(); host.swapMainRows() }   // likely gone since the menu was built
        }
    }

    /**
     * The apps grid for [pick]. TOOLS: the app drawer, every installed app but the Android games console's, marked when
     * it is in Tools. GAMES: the detected games, then every other app ([androidGamesPickOrder]), checked when it is in
     * the console. [selectId]: the app highlighted first (the second display's preview tapped), else the first.
     */
    fun openToolApps(pick: AppPick, selectId: String? = null) {
        val l = launcher ?: return
        host.rememberSettingsRow()
        loader.start {
            val apps = l.installedApps()
            host.installedLoaded(apps)
            // Icons stay cached between visits, so the drawer opens drawn; only uninstalled apps' go.
            appIcons?.retain(apps.map { it.id })
            val console = settings.androidConsoleApps.toSet()
            val rows = when (pick) {
                AppPick.TOOLS -> host.drawerRows(apps)
                AppPick.GAMES -> androidGamesPickOrder(apps).map { ToolAppRow(it.id, it.label, it.id in console) }
            }
            toolAppsColumns = 0   // a new picker: it reports its own columns on its first layout
            if (pick == AppPick.TOOLS) host.toolOnTop()   // the drawer, a tool: it lands after a read, so [openTool] could not tell
            screen.value = Screen.ToolApps(rows, pick)
            selection.value = ListSelection(rows.indexOfFirst { it.id == selectId }.coerceAtLeast(0), rows.size)
        }
    }

    /** The apps grid laid out [columns] tiles wide: the d-pad then steps by tiles. */
    fun onToolAppsLayout(columns: Int) { toolAppsColumns = columns.coerceAtLeast(0) }

    /**
     * The grid ([gridStep], every row still): the d-pad moves by tiles, UP/DOWN wrapping in the column and LEFT/RIGHT
     * stopping at the first and last tile. The drawer: A opens the selected app, MENU its menu, B back to Tools. The
     * games picker: A puts the app in the console or takes it out, B back to Library on its row.
     */
    fun onToolAppsButton(s: Screen.ToolApps, button: NxButton) {
        when (button) {
            NxButton.UP, NxButton.DOWN, NxButton.LEFT, NxButton.RIGHT -> {
                val sel = selection.value
                gridStep(sel.index, sel.count, toolAppsColumns.coerceAtLeast(1), button, rows = sel.count)?.let { selection.value = sel.copy(index = it) }
            }
            NxButton.A -> host.openSelected()
            NxButton.B -> host.back()
            NxButton.MENU -> host.openContextMenu()
            else -> Unit
        }
    }

    /** The drawer's A: opens the selected app; one gone since the drawer opened says so and leaves the grid. */
    fun launchDrawerApp(s: Screen.ToolApps) {
        val row = s.apps.getOrNull(selection.value.index) ?: return
        if (launcher?.launch(row.id) == true) return
        host.showNotice("Could not open ${row.label}")
        val apps = s.apps - row
        screen.value = s.copy(apps = apps)
        selection.value = ListSelection(selection.value.index, apps.size).clampedTo(apps.size)
        scope.launch { host.rebuildMainRows() }   // gone from Tools and Home too, when it was there
    }

    /**
     * The app [id] into Tools (added last) or out of it ([on] false), from the drawer's menu or an app tile's: its mark
     * in an open drawer and the Tools tab follow. A Home pin is its own: it stays either way.
     */
    suspend fun setInTools(id: String, on: Boolean) {
        val ids = settings.toolApps
        settings.toolApps = if (on) (ids - id) + id else ids - id
        (screen.value as? Screen.ToolApps)?.let { s -> screen.value = s.copy(apps = s.apps.map { if (it.id == id) it.copy(shown = on) else it }) }
        host.rebuildMainRows()
    }

    /**
     * The games picker's A: the selected app in or out of the console ([setInAndroidConsole]). The checks are then read
     * back from the list, and the Consoles rows follow, with the Tools rows (an app in the console leaves them).
     */
    fun toggleAndroidGame(s: Screen.ToolApps) {
        val row = s.apps.getOrNull(selection.value.index) ?: return
        val joining = !row.shown
        setInAndroidConsole(row.id, on = joining)
        val shown = settings.androidConsoleApps.toSet()
        screen.value = s.copy(apps = s.apps.map { it.copy(shown = it.id in shown) })
        scope.launch {
            if (joining) host.moveToolPinsToAndroid(listOf(row.id))
            host.applyAndroidConsole()   // a new console rebuilds the rows, the Tools tab's too
        }
    }

    /** Puts app [id] in the Android games console ([on], appended to the list) or takes it out, as the games picker's A does. */
    private fun setInAndroidConsole(id: String, on: Boolean) {
        val ids = settings.androidConsoleApps
        if (on) { if (id !in ids) settings.androidConsoleApps = ids + id } else settings.androidConsoleApps = ids - id
    }
}
