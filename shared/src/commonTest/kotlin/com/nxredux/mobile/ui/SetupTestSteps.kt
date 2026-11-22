package com.nxredux.mobile.ui

import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.Recents
import kotlin.test.assertIs

/**
 * [AppState.setHome], then past first setup's home page when it shows (a home with games): Continue, with no Move
 * row's games to move (a test home's games are in its Roms). For tests about the steps after it.
 */
internal fun AppState.setHomePast(ref: ContentRef) {
    setHome(ref)
    val s = screen.value as? Screen.FolderSummary ?: return
    if (s.extraId != Recents.HOME) return
    select(folderSummaryRows(s).lastIndex)
    onButton(com.nxredux.mobile.ui.nx.NxButton.A)
}

/** Opens the main menu's console tagged [tag] (a merged row's too). */
internal fun AppState.openConsole(tag: String) = openSystem(
    assertIs<Screen.Systems>(screen.value).tabs.flatMap { it.rows }.firstNotNullOf { (it as? MainRow.System)?.systems?.firstOrNull { s -> s.tag == tag } },
)
