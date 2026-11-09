package com.nxredux.mobile.ui.keyboard

import com.nxredux.mobile.ui.nx.NxButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TextEntryTest {
    private val signIn = TextEntry(listOf(EntryField("Username", ""), EntryField("Password", "", password = true)))
    private fun one(value: String, cursor: Int = value.length) = TextEntry(listOf(EntryField("Name", value)), cursor = cursor)

    /** Applies each step's edit to the entry's own values, as the host's setter echoes it back. */
    private fun TextEntry.run(vararg buttons: NxButton): EntryStep {
        var e = this
        var last = EntryStep(e, EntryOutcome.None)
        for (b in buttons) {
            last = e.press(b)
            e = last.entry
        }
        return last
    }

    @Test fun aTypesHighlightedKeyAtCursor() {
        val step = one("").press(NxButton.A)
        assertEquals(EntryOutcome.Edit(0, "g"), step.outcome)
        assertEquals(1, step.entry.cursor)
        assertEquals("g", step.entry.fields[0].value)
    }

    @Test fun shiftCapitalisesOnceThenClears() {
        val first = one("").run(NxButton.X, NxButton.A)
        assertEquals(EntryOutcome.Edit(0, "G"), first.outcome)
        assertEquals(EntryOutcome.Edit(0, "Gg"), first.entry.press(NxButton.A).outcome)
    }

    @Test fun bDeletesBeforeCursorThenCancelsWhenEmpty() {
        assertEquals(EntryOutcome.Edit(0, "a"), one("ab").press(NxButton.B).outcome)
        assertEquals(EntryOutcome.Cancel, one("").press(NxButton.B).outcome)
    }

    @Test fun menuCancelsAtOnce() = assertEquals(EntryOutcome.Cancel, one("abc").press(NxButton.MENU).outcome)

    @Test fun yTypesSpaceAndStartIsDone() {
        assertEquals(EntryOutcome.Edit(0, " "), one("").press(NxButton.Y).outcome)
        val onUser = signIn.press(NxButton.START)
        assertEquals(EntryOutcome.None, onUser.outcome)
        assertEquals(1, onUser.entry.active)
        assertEquals(EntryOutcome.Confirm, onUser.entry.press(NxButton.START).outcome)
    }

    @Test fun selectMovesToNextField() {
        val e = signIn.copy(fields = listOf(EntryField("Username", "me"), EntryField("Password", "pw", password = true)))
        val next = e.press(NxButton.SELECT).entry
        assertEquals(1, next.active)
        assertEquals(2, next.cursor)
    }

    @Test fun lAndRMoveCursor() {
        val left = one("abc").press(NxButton.L1).entry
        assertEquals(2, left.cursor)
        assertEquals(3, left.press(NxButton.R1).entry.cursor)
        assertEquals(0, one("abc", cursor = 0).press(NxButton.L1).entry.cursor)
        assertEquals(3, one("abc").press(NxButton.R1).entry.cursor)
    }

    @Test fun upFromTopRowEntersActiveFieldWhereLeftRightMoveCursor() {
        val start = one("ab").copy(focus = EntryFocus.Keys(KeyPos(0, 0)))
        val inField = start.press(NxButton.UP).entry
        assertEquals(EntryFocus.Field, inField.focus)
        assertEquals(1, inField.press(NxButton.LEFT).entry.cursor)
        assertEquals(EntryFocus.Keys(KeyPos(0, 0)), inField.press(NxButton.DOWN).entry.focus)
    }

    @Test fun upFromFieldsReachesButtons() {
        val onPassword = signIn.copy(active = 1, cursor = 0, focus = EntryFocus.Field)
        val onUser = onPassword.press(NxButton.UP).entry
        assertEquals(0, onUser.active)
        assertEquals(EntryFocus.Field, onUser.focus)
        val onConfirm = onUser.press(NxButton.UP).entry
        assertEquals(EntryFocus.Button(0), onConfirm.focus)
        val onCancel = onConfirm.press(NxButton.RIGHT).entry
        assertEquals(EntryFocus.Button(1), onCancel.focus)
        assertEquals(EntryOutcome.Cancel, onCancel.press(NxButton.A).outcome)
        assertEquals(EntryOutcome.Confirm, onConfirm.press(NxButton.A).outcome)
        val back = onConfirm.press(NxButton.DOWN).entry
        assertEquals(EntryFocus.Field, back.focus)
        assertEquals(0, back.active)
    }

    @Test fun doneKeyAndNextKeyMatchStartAndSelect() {
        assertEquals(signIn.press(NxButton.START), signIn.tapKey(Key.Done))
        assertEquals(signIn.press(NxButton.SELECT), signIn.tapKey(Key.Next))
    }

    @Test fun symbolsKeySwitchesPageAndKeepsText() {
        val e = one("abc")
        val step = e.tapKey(Key.Symbols)
        assertEquals(KeyPage.SYMBOLS, step.entry.page)
        assertEquals(e.fields, step.entry.fields)
        assertEquals(KeyPage.LETTERS, step.entry.tapKey(Key.Symbols).entry.page)
    }

    @Test fun pasteStripsLineBreaksAndTabs() {
        val step = one("xy", cursor = 0).paste("ab\n\tc\r")
        assertEquals(EntryOutcome.Edit(0, "abcxy"), step.outcome)
        assertEquals(3, step.entry.cursor)
    }

    @Test fun pasteEmptyDoesNothing() = assertEquals(EntryOutcome.None, one("xy").paste("").outcome)

    @Test fun typeInsertsAtCursor() = assertEquals(EntryOutcome.Edit(0, "aXb"), one("ab", cursor = 1).type("X").outcome)

    @Test fun passwordNeverPrinted() {
        val e = signIn.copy(fields = listOf(EntryField("Username", "me"), EntryField("Password", "hunter2", password = true)))
        assertFalse(e.toString().contains("hunter2"))
        assertFalse(e.fields[1].toString().contains("hunter2"))
    }

    @Test fun outsideChangeMovesCursorToEnd() {
        val moved = signIn.withValues(listOf("abc", ""))
        assertEquals(3, moved.cursor)
        assertEquals("abc", moved.fields[0].value)
    }

    @Test fun tapFieldMakesItActiveAtEnd() {
        val e = signIn.copy(fields = listOf(EntryField("Username", "me"), EntryField("Password", "pw1", password = true))).tapField(1)
        assertEquals(1, e.active)
        assertEquals(3, e.cursor)
        assertEquals(EntryFocus.Field, e.focus)
    }

    @Test fun dpadMovesHighlightOnKeys() =
        assertEquals(EntryFocus.Keys(KeyPos(NxKeys.HOME.row, NxKeys.HOME.col + 1)), one("").press(NxButton.RIGHT).entry.focus)
}
