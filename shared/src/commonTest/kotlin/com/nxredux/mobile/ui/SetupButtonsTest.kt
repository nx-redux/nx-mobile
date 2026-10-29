package com.nxredux.mobile.ui

import com.nxredux.mobile.ui.nx.NxSettingRow
import kotlin.test.Test
import kotlin.test.assertEquals

/** Which of a setup page's last rows are its buttons ([setupButtons]), the rest its rows. */
class SetupButtonsTest {
    private fun action(label: String) = NxSettingRow(label)
    private fun value(label: String) = NxSettingRow(label, value = "Set")
    private fun choice(label: String) = NxSettingRow(label, choices = listOf("Large", "Small"), index = 0)

    // A lone row only moves on (Pick folder, Continue): the button.
    @Test fun aLoneRowIsTheButton() = assertEquals(1, setupButtons(listOf(action("Pick folder")), button = true))

    // Two plain actions and nothing else are a real choice: both buttons, side by side.
    @Test fun twoPlainActionsAreBothButtons() =
        assertEquals(2, setupButtons(listOf(action("Yes, I have one"), action("No")), button = true))

    // Under rows with a value (the artwork step's accounts), two plain actions are both buttons too.
    @Test fun twoActionsUnderValueRowsAreBothButtons() =
        assertEquals(2, setupButtons(listOf(value("ScreenScraper"), value("SteamGridDB key"), action("Fetch now"), action("Skip")), button = true))

    // Value rows and a Continue: Continue alone.
    @Test fun valueRowsThenContinueIsOneButton() =
        assertEquals(1, setupButtons(listOf(choice("Size"), action("Continue")), button = true))

    // A list of plain choices (Bring your saves' imports): only its last (Skip) is the button.
    @Test fun aListOfActionsKeepsItsRowsAndOneButton() =
        assertEquals(1, setupButtons(listOf(action("From a folder"), action("From a zip file"), action("From RetroArch"), action("Skip")), button = true))

    // A dimmed line (a skipped file) is no action: with Continue it is not a pair.
    @Test fun aDimmedLineIsNoAction() =
        assertEquals(1, setupButtons(listOf(NxSettingRow("a.srm: no game", enabled = false), action("Continue")), button = true))

    @Test fun noButtonWhenNotAsked() = assertEquals(0, setupButtons(listOf(action("Continue")), button = false))
}
