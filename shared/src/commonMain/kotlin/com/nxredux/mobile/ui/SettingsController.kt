package com.nxredux.mobile.ui

import com.nxredux.mobile.settings.AccentPreset
import com.nxredux.mobile.settings.BackdropArt
import com.nxredux.mobile.settings.ConsoleArt
import com.nxredux.mobile.settings.ExtraFolder
import com.nxredux.mobile.settings.GameListArt
import com.nxredux.mobile.settings.GameRumble
import com.nxredux.mobile.settings.MenuStyles
import com.nxredux.mobile.settings.OptionTextSize
import com.nxredux.mobile.settings.PadTheme
import com.nxredux.mobile.settings.PadVibration
import com.nxredux.mobile.settings.SecondaryAccent
import com.nxredux.mobile.settings.SwitcherLayout
import com.nxredux.mobile.settings.VerticalListAlignment
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxPadA
import com.nxredux.mobile.ui.nx.NxSettingRow
import com.nxredux.mobile.ui.nx.fullInfo
import com.nxredux.mobile.ui.nx.padA
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What Settings' pages need from [AppState]: each page's rows and values ([settingsRows], [settingValue]: they read and
 * write state all over the app), the menu row to come back to, leaving a page, and what a reset touches beyond
 * the settings.
 */
internal interface SettingsHost {
    fun kidsToolsAllowed(): Boolean
    fun loaderActive(): Boolean
    /** A Minimal open is under way: input waits ([LaunchController.minimalPending]). */
    fun minimalPending(): Boolean
    /** A second display is connected: its category shows. */
    fun secondConnected(): Boolean
    fun select(index: Int)
    fun openSelected()
    fun back(): Boolean
    fun rememberMenuSelection()
    fun refreshInstalled()
    fun settingsRows(section: SettingsSection): List<SettingsRow>
    fun settingValue(row: SettingsRow): SettingValue<*>?
    fun settingsDetails(section: SettingsSection): List<String>
    fun layoutShot(section: SettingsSection): LayoutShot?
    fun settingsBanner(section: SettingsSection): String?
    /** The art layout the screens draw follows the settings again. */
    fun syncArtLayout()
    fun rebuildHomeTab()
    /** Shortcuts' Reset: every binding cleared in the draft (written when the page closes). */
    fun clearShortcuts()
    /** System buttons' Reset: the connected pad model's SELECT / START / MENU back to its defaults. */
    fun resetSystemButtons()
    fun resetButtonLayoutConsole()
}

/**
 * Settings' pages as screens: the section menu and each page (built from [SettingsHost.settingsRows]), the row each
 * remembers, a sub-page and back, a refresh keeping the highlight on its row, the two-pane taps and the row edit, the
 * value chooser and a row's info popup, and Reset to defaults.
 */
internal class SettingsController(
    private val screen: MutableStateFlow<Screen>,
    private val selection: MutableStateFlow<ListSelection>,
    /** The row being changed on a two-pane page ([AppState.rowEdit]); AppState clears it when the screen changes. */
    private val rowEdit: MutableStateFlow<RowEdit?>,
    private val deps: AppDeps,
    /** Kids Mode can run here: its category shows. */
    private val kidsAvailable: Boolean,
    private val host: SettingsHost,
) {
    private val sectionSelections = HashMap<SettingsSection, ListSelection>()   // each section's row, remembered across visits
    private var settingsMenuSelection = ListSelection(0, 0)

    /** [sel] is [section]'s row on its next visit. */
    fun rememberSection(section: SettingsSection, sel: ListSelection) { sectionSelections[section] = sel }

    /** [section]'s next visit starts on its first row. */
    fun forgetSection(section: SettingsSection) { sectionSelections.remove(section) }

    /** [sel] is the section menu's row on its next show. */
    fun rememberSettingsMenu(sel: ListSelection) { settingsMenuSelection = sel }
    var settingsSplit = false   // the UI draws Settings as two panes ([onSettingsSplit])
        private set

    /** A pick made from Library returns there after its rescan: keeps the row and names the page. */
    fun rememberLibraryPage(): SettingsSection? {
        val s = screen.value as? Screen.Settings ?: return null
        if (!s.section.library) return null
        sectionSelections[s.section] = selection.value
        return s.section
    }
    private val _chooser = MutableStateFlow<ValueChooser?>(null)

    /**
     * A value row's picker ([com.nxredux.mobile.ui.nx.NxSettingPicker]): every value, the current one marked; a pick
     * applies it ([chooseValue]), B or a tap outside closes it ([closeChooser]).
     */
    val chooser: StateFlow<ValueChooser?> = _chooser.asStateFlow()
    private var chooserApply: ((Int) -> Unit)? = null

    /** The chooser's value [index] picked: it closes, then the value applies (unchanged when it was already the one). */
    fun chooseValue(index: Int) {
        val c = _chooser.value ?: return
        val apply = chooserApply
        closeChooser()
        if (index in c.labels.indices) apply?.invoke(index)
    }

    fun closeChooser() { _chooser.value = null; chooserApply = null }

    /** The chooser takes the pad while open: UP / DOWN move (wrapping), A picks, B closes. */
    private fun onChooserButton(c: ValueChooser, button: NxButton) {
        when (button) {
            NxButton.UP, NxButton.DOWN -> _chooser.value = c.copy(selected = (c.selected + if (button == NxButton.UP) -1 else 1).mod(c.labels.size))
            NxButton.A -> chooseValue(c.selected)
            NxButton.B, NxButton.MENU -> closeChooser()
            else -> Unit
        }
    }

    /** A value row's picker ([ValueChooser]) titled [title]: [labels], [current] under the cursor (-1: none); [apply] takes a pick. */
    fun openPicker(title: String, labels: List<String>, current: Int, apply: (Int) -> Unit) {
        _chooser.value = ValueChooser(title, labels, current.coerceAtLeast(0), current = current)
        chooserApply = apply
    }

    /**
     * Settings row [row] set to its choice [index] ([Screen.Settings.choices]), selecting it: the value as LEFT / RIGHT
     * would step it there, with the same side effects ([SettingValue.changeTo]); nothing when it is already the one or
     * the row is dimmed. Use as home screen asks Android, as A does.
     */
    fun chooseSetting(row: Int, index: Int) {
        val s = screen.value as? Screen.Settings ?: return
        val r = s.rows.getOrNull(row)?.takeIf { it.enabled } ?: return
        host.select(row)
        if (r.kind == SettingsKind.MIC_ACCESS) { deps.micAccess?.request(); return }   // the answer follows on resume
        if (r.kind == SettingsKind.ALL_FILES) { deps.allFilesAccess?.openSettings(); return }   // Android's page; the switch follows on resume
        if (r.kind == SettingsKind.HOME_SCREEN) {
            deps.launcher?.takeIf { (index == 1) != it.isHome() }?.let { it.setHome(index == 1); refreshSettings() }
            return
        }
        host.settingValue(r)?.changeTo(index)
    }

    /** Settings row [row]'s picker ([ValueChooser]): every value, the current one under the cursor; a pick sets it ([chooseSetting]). */
    fun openSettingPicker(row: Int) {
        val s = screen.value as? Screen.Settings ?: return
        if (s.rows.getOrNull(row)?.enabled != true) return
        val c = s.choices.getOrNull(row)?.takeIf { it.labels.size >= 2 } ?: return
        host.select(row)
        _chooser.value = ValueChooser(s.rows[row].label, c.labels, c.index.coerceAtLeast(0), current = c.index)
        val label = s.rows[row].label
        // By its label: a refresh while the picker is open can move the rows (a dependent row shown or hidden).
        chooserApply = { i ->
            ((screen.value as? Screen.Settings)?.rows?.let { rows -> row.takeIf { rows.getOrNull(it)?.label == label } ?: rows.indexOfFirst { it.label == label } })
                ?.takeIf { it >= 0 }?.let { chooseSetting(it, i) }
        }
    }
    private val _settingInfo = MutableStateFlow<SettingInfoPopup?>(null)

    /** A Settings row's full description over the page (its info icon, or X); A, B, X or a tap close it, UP / DOWN scroll it. */
    val settingInfo: StateFlow<SettingInfoPopup?> = _settingInfo.asStateFlow()
    private val _settingInfoTruncated = MutableStateFlow<String?>(null)

    /** The selected Settings row's description the UI reported cut off on its row (keyed by its text): X and its hint read it. */
    val settingInfoTruncated: StateFlow<String?> = _settingInfoTruncated.asStateFlow()

    /** The UI laid out the selected row's description [info] and it did ([truncated]) or did not fit. */
    fun onSettingInfoTruncated(info: String, truncated: Boolean) {
        _settingInfoTruncated.value = if (truncated) info else _settingInfoTruncated.value.takeIf { it != info }
    }

    /** Row [row]'s touch row as the page draws it ([settingsTouchRow], [emulatorTouchRow]); null off those pages. */
    fun settingTouchRow(row: Int): NxSettingRow? = when (val s = screen.value) {
        is Screen.Settings -> s.rows.getOrNull(row)?.let { settingsTouchRow(it, s.choices.getOrNull(row), s.section) }
        is Screen.EmulatorOptions -> s.rows.getOrNull(row)?.takeIf { it.selectable }?.let(::emulatorTouchRow)
        is Screen.RaSettings -> s.rows.getOrNull(row)?.takeIf { it.selectable }?.let(::raSettingsTouchRow)
        is Screen.ArtworkSettings -> s.rows.getOrNull(row)?.takeIf { it.selectable }?.let(::artworkSettingTouchRow)
        else -> null
    }

    /**
     * The pad on a page of setting rows ([rowEditKey]) drawn beside another ([rowEditTwoPane]); one page keeps LEFT /
     * RIGHT for its values (the page's own handling). Two panes: LEFT and RIGHT go between pages (LEFT as B; RIGHT opens a `›`
     * row, as A) and never change a value. A on a row of segments, swatches or a stepped value ([NxPadA.EDIT]) starts
     * changing it: then LEFT / RIGHT step it (the page's own LEFT / RIGHT), A or B stop, UP / DOWN stop and move. A
     * switch's A flips it and a picker's opens it, as before (the page's own A). Returns the press the page hears
     * ([button] itself, or LEFT's B and RIGHT's A); null when it is taken here.
     */
    fun onRowEditButton(s: Screen, button: NxButton): NxButton? {
        val key = rowEditKey(s) ?: return button
        if (rowEditBlocked(s)) return button
        if (!rowEditTwoPane(s, settingsSplit)) { rowEdit.value = null; return button }
        val i = selection.value.index
        if (rowEdit.value == RowEdit(key, i)) return when (button) {
            NxButton.A, NxButton.B -> { rowEdit.value = null; null }
            NxButton.UP, NxButton.DOWN -> { rowEdit.value = null; button }
            else -> button   // LEFT / RIGHT: the page steps the value; X: its info
        }
        rowEdit.value = null
        val row = settingTouchRow(i)
        return when (button) {
            NxButton.A -> if (row?.padA == NxPadA.EDIT) { rowEdit.value = RowEdit(key, i); null } else button
            NxButton.LEFT -> NxButton.B
            // A `›` row has no values (its A opens), so its A never starts an edit here.
            NxButton.RIGHT -> if (row?.opens == true) NxButton.A else null
            else -> button
        }
    }

    /** Whether X opens the selected Settings row's info: its line is cut off on the row, or a fuller text is behind it. */
    fun selectedSettingShowsInfo(): Boolean {
        val r = settingTouchRow(selection.value.index) ?: return false
        return nxShowsInfo(r, _settingInfoTruncated.value)
    }

    /** Shows Settings row [row]'s full description ([fullInfo]), selecting it; nothing for a row without one. */
    fun showSettingInfo(row: Int) {
        val r = settingTouchRow(row) ?: return
        if (r.fullInfo.isBlank()) return
        host.select(row)
        _settingInfo.value = SettingInfoPopup(r.label, r.fullInfo)
    }

    fun closeSettingInfo() { _settingInfo.value = null }

    /** The info popup takes the pad while open: A, B and X close it, UP / DOWN scroll it. */
    private fun onSettingInfoButton(p: SettingInfoPopup, button: NxButton) {
        when (button) {
            NxButton.A, NxButton.B, NxButton.X, NxButton.MENU -> closeSettingInfo()
            NxButton.UP, NxButton.DOWN -> _settingInfo.value = p.copy(scrollSeq = p.scrollSeq + 1, scrollDir = if (button == NxButton.UP) -1 else 1)
            else -> Unit
        }
    }

    /** Tools -> Settings: the section menu, on the section last opened. */
    fun openSettingsMenu() {
        if (!host.kidsToolsAllowed()) return   // Kids Mode: Settings only for a parent
        showSettingsMenu()
    }

    /** The Settings root's categories on this platform ([settingsCategories]); the two-pane left pane lists the same. */
    internal fun settingsSections(): List<SettingsSection> = settingsCategories(secondScreen = host.secondConnected(), kids = kidsAvailable)

    fun showSettingsMenu() {
        val sections = settingsSections()   // sub-pages open from their section
        screen.value = Screen.SettingsMenu(sections)
        selection.value = settingsMenuSelection.clampedTo(sections.size)
    }

    fun openSettings(section: SettingsSection) {
        if (!host.kidsToolsAllowed()) return   // Kids Mode: Settings only for a parent
        when (screen.value) {
            is Screen.Systems, is Screen.Games -> host.rememberMenuSelection()   // the main menu's tab (a game list's: nothing)
            is Screen.SettingsMenu -> settingsMenuSelection = selection.value
            else -> Unit
        }
        val page = settingsPage(section)
        screen.value = page
        selection.value = selectableOn(page, sectionSelections[section] ?: ListSelection(0, 0))
        if (section == SettingsSection.LIBRARY) host.refreshInstalled()   // the Android games count: the cached one until it lands
    }

    /** Opens [to] from [from], keeping [from]'s highlighted row for the return. */
    fun openSubPage(from: Screen.Settings, to: SettingsSection) {
        sectionSelections[from.section] = selection.value
        openSettings(to)
    }

    /**
     * The UI reports whether Settings is drawn as two panes ([settingsSplit] of the window). The screen and selection do
     * not change: the Settings root is the left pane and its pages the right, so a rotation keeps the page and row.
     */
    fun onSettingsSplit(split: Boolean) { settingsSplit = split }

    /** [section]'s page as the unfocused right pane shows it while its category is highlighted on the left. */
    fun settingsPreview(section: SettingsSection): Screen.Settings = settingsPage(section)

    /**
     * A tap on the section list's [index]: one tap opens that section. From a right-pane page the pane's own back stack
     * unwinds as B would (each page keeping its row; a Library reload or rescan lands on the root by itself), then the
     * tapped section opens.
     */
    fun settingsCategoryTap(index: Int) {
        if (host.loaderActive()) return
        if ((screen.value as? Screen.Settings)?.let { it.confirmRemove != null || it.confirmReset || it.nameDraft != null || it.confirmDriver != null } == true) return   // a dialog answers first
        // One tap opens the section, from the root or from another section's page.
        if (screen.value is Screen.SettingsMenu) {
            host.select(index)
            if (!host.minimalPending()) openHighlightedSection()
            return
        }
        if (settingsPane(screen.value) != SettingsPane.RIGHT) return   // a stale tap from a page drawn whole: the root's row stays
        val count = settingsSections().size
        settingsMenuSelection = ListSelection(index, count).clampedTo(count)
        repeat(SETTINGS_MAX_DEPTH) {
            if (host.loaderActive()) return
            if (settingsPane(screen.value) != SettingsPane.RIGHT) {
                if (screen.value is Screen.SettingsMenu) { host.select(index); if (!host.minimalPending()) openHighlightedSection() }
                return
            }
            host.back()
        }
    }

    /** The section menu's highlighted section opens; the caller has checked that input is not waiting. */
    fun openHighlightedSection() {
        (screen.value as? Screen.SettingsMenu)?.sections?.getOrNull(selection.value.index)?.let(::openSettings)
    }

    /**
     * A tap on [row] of the unfocused right pane: opens the highlighted category, focused on that row. A one-tap list
     * ([com.nxredux.mobile.ui.nx.nxTap]) calls this twice: the second call, on the page it opened, acts on that row (A).
     */
    fun settingsPreviewTap(row: Int) {
        if (host.loaderActive()) return
        if (screen.value is Screen.Settings && row == selection.value.index) { host.openSelected(); return }
        val s = screen.value as? Screen.SettingsMenu ?: return
        val section = s.sections.getOrNull(selection.value.index) ?: return
        openSettings(section)
        host.select(row)
    }

    /** [section]'s page as it stands now; [confirm]: the remove dialog over it. */
    fun settingsPage(section: SettingsSection, confirm: ExtraFolder? = null): Screen.Settings {
        val rows = host.settingsRows(section)
        return Screen.Settings(section, rows, confirm, host.settingsDetails(section), host.layoutShot(section), host.settingsBanner(section), rows.map(::settingChoices))
    }

    /** [row]'s values for its touch control: its [settingValue]'s labels and current one; Use as home screen's and All files access's Off / On. */
    fun settingChoices(row: SettingsRow): SettingChoices? {
        if (row.kind == SettingsKind.HOME_SCREEN) return deps.launcher?.let { SettingChoices(ON_OFF, if (it.isHome()) 1 else 0) }
        if (row.kind == SettingsKind.ALL_FILES || row.kind == SettingsKind.MIC_ACCESS) return SettingChoices(ON_OFF, if (row.secondary == "On") 1 else 0)   // as granted when the row was built
        val v = host.settingValue(row) ?: return null
        return v.labels?.let { SettingChoices(it, v.index) }
    }

    /** Shows [section]'s page on its first row of [kind] (the first row when there is none). */
    fun showSettingsOn(section: SettingsSection, kind: SettingsKind) {
        val page = settingsPage(section)
        screen.value = page
        selection.value = selectableOn(page, ListSelection(page.rows.indexOfFirst { it.kind == kind }.coerceAtLeast(0), page.rows.size))
    }

    /** [sel] on [page], clamped and moved off a group label or gap to the next row that takes it (else the one before). */
    fun selectableOn(page: Screen.Settings, sel: ListSelection): ListSelection {
        val rows = page.rows
        val at = sel.clampedTo(rows.size)
        if (rows.getOrNull(at.index)?.selectable != false) return at
        val i = (at.index until rows.size).firstOrNull { rows[it].selectable } ?: (at.index downTo 0).firstOrNull { rows[it].selectable } ?: at.index
        return at.copy(index = i)
    }

    /** One selectable row up ([delta] -1) or down from [sel] on [rows], wrapping past group labels and gaps. */
    fun settingsStep(rows: List<SettingsRow>, sel: ListSelection, delta: Int): ListSelection =
        sel.step(delta) { rows.getOrNull(it)?.selectable != false }

    /**
     * Appearance's, Layouts' or Controls' "Reset to defaults" (NX Redux settings.c): the page's own options back to their
     * defaults. Appearance: Pad theme, Pad buttons, Accent, Secondary accent, Text size, Button layout. Layouts: every
     * tab's style and orientation unset (so [MenuStyles.styleOf] and [MenuStyles.orientationOf] give theirs), the game lists', the tabs', shelves'
     * and hints' Show/Hide, Consoles background, List art, Backdrop art, Page title, Button hints, Extra info, Home stats,
     * Layout options, Achievements panel and Game Switcher layout. Controls: Pad vibration, Game rumble and every pad
     * layout. At once, no confirm, as NX Redux. Through the settings' own setters, then the page is republished like any
     * cycled row's (App re-reads the theme, accent and scale with it), the highlight kept on the reset row, which the
     * orientation rows coming or going may have moved.
     */
    private fun resetToDefaults(section: SettingsSection) {
        val s = deps.settings
        if (section == SettingsSection.SHORTCUTS) {
            host.clearShortcuts()   // Clear all: written with the rest when the page closes
        } else if (section == SettingsSection.SYSTEM_BUTTONS) {
            host.resetSystemButtons()
        } else if (section == SettingsSection.APPEARANCE) {
            s.padSkin = true
            s.padTheme = PadTheme.DEFAULT
            s.accent = AccentPreset.DEFAULT
            s.secondaryAccent = SecondaryAccent.DEFAULT
            s.optionTextSize = OptionTextSize.DEFAULT
            s.skinTweaks = ""
            s.overlayTweaks = ""
        } else if (section == SettingsSection.MAIN_MENU) {
            s.menuStyles = emptyMap()
            s.menuOrientations = emptyMap()
            s.gameListStyle = MenuStyles.DEFAULT_GAME_LIST_STYLE
            s.gameListOrientation = MenuStyles.DEFAULT_ORIENTATION
            s.gameListVerticalAlignment = VerticalListAlignment.DEFAULT
            s.showHomeTab = MenuStyles.DEFAULT_TAB_SHOWN; s.showConsolesTab = MenuStyles.DEFAULT_TAB_SHOWN; s.showGamesTab = MenuStyles.DEFAULT_TAB_SHOWN
            s.showCollectionsTab = MenuStyles.DEFAULT_TAB_SHOWN; s.showToolsTab = MenuStyles.DEFAULT_TAB_SHOWN
            s.showNewShelf = true; s.showNotStartedShelf = true; s.showMostPlayedShelf = true
            s.showNetplayHint = true
            s.consoleArt = ConsoleArt.DEFAULT; s.gameListArt = GameListArt.DEFAULT; s.backdropArt = BackdropArt.DEFAULT
            host.syncArtLayout()
            s.menuPageTitle = MenuStyles.DEFAULT_PAGE_TITLE
            s.menuButtonHints = MenuStyles.DEFAULT_BUTTON_HINTS
            s.menuExtraInfo = MenuStyles.DEFAULT_EXTRA_INFO
            s.menuInfoAchievements = MenuStyles.DEFAULT_INFO_ACHIEVEMENTS
            s.menuHomeStats = MenuStyles.DEFAULT_HOME_STATS
            s.gameListLayoutOptions = true
            s.gameListAchievementsPanel = true
            s.switcherLayout = SwitcherLayout.DEFAULT
            // As the single rows do: Home's rows follow its style and shelves.
            host.rebuildHomeTab()
        } else if (section == SettingsSection.CONTROLS) {
            s.padVibration = PadVibration.DEFAULT
            s.gameRumble = GameRumble.DEFAULT
        }
        showSettingsOn(section, SettingsKind.RESET_DEFAULTS)
    }

    /** Rebuilds the open page's rows in place (selection clamped). */
    fun refreshSettings(confirm: ExtraFolder? = null) {
        host.syncArtLayout()
        val old = screen.value as? Screen.Settings
        val section = old?.section ?: SettingsSection.LIBRARY
        val page = settingsPage(section, confirm)
        screen.value = page
        // The selected row moved (a row above it came or went, as Game lists' art row as its layout steps): the highlight follows it.
        val sel = selection.value
        val moved = old?.rows?.getOrNull(sel.index)?.takeIf { old.section == section }?.let { was ->
            page.rows.indices.filter { sameSettingsRow(page.rows[it], was) }.singleOrNull()
        }
        selection.value = selectableOn(page, if (moved != null && moved != sel.index) sel.copy(index = moved) else sel)
    }

    /** Whether [a] and [b] are the same Settings row across a refresh: its kind and what it is for, not its value or label. */
    private fun sameSettingsRow(a: SettingsRow, b: SettingsRow): Boolean =
        a.kind == b.kind && a.category == b.category && a.shelf == b.shelf && a.hint == b.hint && a.consoleId == b.consoleId &&
            a.shortcut == b.shortcut && a.touchSlot == b.touchSlot && a.systemRole == b.systemRole && a.landscape == b.landscape && a.extra?.id == b.extra?.id && a.gpuDriver == b.gpuDriver

    /** The confirmed reset of [page]'s selected row: Reset to defaults (Shortcuts' Clear all) or Button layout's Reset console. */
    fun runSettingsReset(page: Screen.Settings) {
        when (page.rows.getOrNull(selection.value.index)?.kind) {
            SettingsKind.RESET_DEFAULTS -> resetToDefaults(page.section)
            SettingsKind.BUTTON_LAYOUT_RESET -> host.resetButtonLayoutConsole()
            else -> Unit
        }
    }

    /** A press while the chooser or the info popup is open: theirs, and true. False with neither open. */
    fun onPopupButton(button: NxButton): Boolean {
        _chooser.value?.let { onChooserButton(it, button); return true }
        _settingInfo.value?.let { onSettingInfoButton(it, button); return true }
        return false
    }

    /** Back with the chooser or the info popup open: it closes, and true. False with neither open. */
    fun closePopup(): Boolean {
        if (_chooser.value != null) { closeChooser(); return true }
        if (_settingInfo.value != null) { closeSettingInfo(); return true }
        return false
    }
}

/** The deepest a Settings right pane goes below its category (Library -> Emulator Settings -> consoles -> options), and some. */
private const val SETTINGS_MAX_DEPTH = 6
