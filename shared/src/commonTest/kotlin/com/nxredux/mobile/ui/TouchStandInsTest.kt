package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.scraper.JobPhase
import com.nxredux.mobile.shared.res.Res
import com.nxredux.mobile.shared.res.nav_icon_delete
import com.nxredux.mobile.shared.res.nav_icon_filter
import com.nxredux.mobile.shared.res.nav_icon_new_folder
import com.nxredux.mobile.storage.ResumeInfo
import com.nxredux.mobile.ui.nx.FooterAction
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.HintTouch
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.touchButtons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The hints with no tap equivalent carry their touch stand-in.
class TouchStandInsTest {
    @Test fun switcher_trash_only_with_a_game() {
        assertTrue(switcherHints(null).none { it.touch is HintTouch.Icon })
        val entry = switcherEntryForTest()
        assertEquals(HintTouch.Icon(Res.drawable.nav_icon_delete), switcherHints(entry).single { it.button == NxButton.Y }.touch)
    }

    @Test fun ra_filter_icon_is_lit_while_locked_only() {
        assertEquals(HintTouch.Icon(Res.drawable.nav_icon_filter, lit = true), raGameFilterHint(lockedOnly = true).touch)
        assertEquals(HintTouch.Icon(Res.drawable.nav_icon_filter, lit = false), raGameFilterHint(lockedOnly = false).touch)
    }


    @Test fun artwork_progress_pause_or_resume_then_cancel_as_buttons() {
        assertEquals(
            listOf(FooterAction(NxButton.Y, "Pause", true), FooterAction(NxButton.X, "Cancel", false)),
            artworkProgressHints(JobPhase.RUNNING).touchButtons(),
        )
        assertEquals("Resume", artworkProgressHints(JobPhase.PAUSED).touchButtons().first().label)
        assertEquals(listOf(FooterAction(NxButton.A, "Done", true)), artworkProgressHints(JobPhase.DONE).touchButtons())   // ended: Done, in the page
    }

    // New folder is a row now (Create new folder): no title icon for it.
    @Test fun file_picker_new_folder_has_no_title_icon() {
        val hint = filePickerHintsForTest(insideFolder = true).single { it.button == NxButton.X }
        assertEquals(HintTouch.None, hint.touch)
    }
}

internal fun switcherEntryForTest() = SwitcherEntry(
    SystemFolder("GB", "GB", null, emptyList()),
    GameEntry(ContentRef("Tetris.gb"), "Tetris.gb", "Tetris", folder = ContentRef("GB"), sourceId = "home", relativeDir = "", subtitle = null),
    ResumeInfo(1),
)

private fun filePickerHintsForTest(insideFolder: Boolean): List<Hint> = filePickerHints(
    Screen.FilePicker(FilePickMode.ImportFolder, if (insideFolder) "/storage/emulated/0/Roms" else null, "Roms", emptyList()),
)
