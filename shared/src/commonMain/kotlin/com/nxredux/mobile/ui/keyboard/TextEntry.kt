package com.nxredux.mobile.ui.keyboard

import com.nxredux.mobile.ui.nx.NxButton

/** One field of a prompt: its [label], its text and whether it is a [password] (drawn as dots, never printed). */
data class EntryField(val label: String, val value: String, val password: Boolean = false) {
    override fun toString(): String = "EntryField(label=$label, value=${if (password) "***" else value}, password=$password)"
}

/** Where the pad's highlight is: on a key, in the active field (moving its cursor), or on the box's buttons (0 confirm, 1 Cancel). */
sealed interface EntryFocus {
    data class Keys(val at: KeyPos) : EntryFocus
    data object Field : EntryFocus
    data class Button(val index: Int) : EntryFocus
}

/** What a press asks of the prompt's host: an [Edit] of a field's text, [Confirm], [Cancel], the clipboard ([Paste]), or nothing. */
sealed interface EntryOutcome {
    data class Edit(val field: Int, val text: String) : EntryOutcome
    data object Confirm : EntryOutcome
    data object Cancel : EntryOutcome
    data object Paste : EntryOutcome
    data object None : EntryOutcome
}

/** A press's result: the [entry] after it and what it asks of the host. */
data class EntryStep(val entry: TextEntry, val outcome: EntryOutcome)

/**
 * A text prompt as the NX keyboard edits it: its [fields], the [active] one and its [cursor], the pad's [focus], the
 * key it last stood on ([keyAt], where it returns from the field), one-shot [shift] and the key [page]. Pure: every
 * press returns a new entry; the host applies the outcome.
 */
data class TextEntry(
    val fields: List<EntryField>,
    val active: Int = 0,
    val cursor: Int = fields[active].value.length,
    val focus: EntryFocus = EntryFocus.Keys(NxKeys.HOME),
    val shift: Boolean = false,
    val page: KeyPage = KeyPage.LETTERS,
    val keyAt: KeyPos = NxKeys.HOME,
) {
    val twoFields: Boolean get() = fields.size > 1
    val text: String get() = fields[active].value

    override fun toString(): String =
        "TextEntry(fields=$fields, active=$active, cursor=$cursor, focus=$focus, shift=$shift, page=$page)"
}

private fun TextEntry.none() = EntryStep(this, EntryOutcome.None)

private fun TextEntry.edited(text: String, cursor: Int): EntryStep {
    val fields = fields.toMutableList().also { it[active] = it[active].copy(value = text) }
    return EntryStep(copy(fields = fields, cursor = cursor), EntryOutcome.Edit(active, text))
}

/** [s] at the cursor, line breaks and tabs dropped (one-line fields); nothing for nothing. */
private fun TextEntry.insert(s: String): EntryStep {
    val clean = s.replace("\n", "").replace("\r", "").replace("\t", "")
    if (clean.isEmpty()) return none()
    return edited(text.substring(0, cursor) + clean + text.substring(cursor), cursor + clean.length)
}

private fun TextEntry.backspace(): EntryStep = when {
    text.isEmpty() -> EntryStep(this, EntryOutcome.Cancel)
    cursor == 0 -> none()
    else -> edited(text.removeRange(cursor - 1, cursor), cursor - 1)
}

private fun TextEntry.toField(index: Int, focus: EntryFocus = this.focus): TextEntry =
    copy(active = index, cursor = fields[index].value.length, focus = focus)

/** START / Done: the next field while there is one, else confirm. */
private fun TextEntry.done(): EntryStep =
    if (active < fields.lastIndex) EntryStep(toField(active + 1), EntryOutcome.None) else EntryStep(this, EntryOutcome.Confirm)

/** SELECT / Next: the next field, round to the first. */
private fun TextEntry.next(): EntryStep = if (!twoFields) none() else EntryStep(toField((active + 1) % fields.size), EntryOutcome.None)

private fun TextEntry.cursorBy(d: Int) = copy(cursor = (cursor + d).coerceIn(0, text.length))

/** A pad press: the keyboard's mapping (spec), the d-pad moving the highlight on keys, the field or the box's buttons. */
fun TextEntry.press(button: NxButton): EntryStep {
    when (button) {
        NxButton.MENU -> return EntryStep(this, EntryOutcome.Cancel)
        NxButton.B -> return backspace()
        NxButton.Y -> return insert(" ")
        NxButton.X -> return EntryStep(copy(shift = !shift), EntryOutcome.None)
        NxButton.L1 -> return EntryStep(cursorBy(-1), EntryOutcome.None)
        NxButton.R1 -> return EntryStep(cursorBy(1), EntryOutcome.None)
        NxButton.SELECT -> return next()
        NxButton.START -> return done()
        else -> Unit
    }
    return when (val f = focus) {
        is EntryFocus.Keys -> when (button) {
            NxButton.A -> tapKey(NxKeys.rows(page, twoFields)[f.at.row][f.at.col])
            NxButton.UP, NxButton.DOWN, NxButton.LEFT, NxButton.RIGHT -> {
                val to = NxKeys.move(page, twoFields, f.at, button)
                EntryStep(if (to == null) copy(focus = EntryFocus.Field, keyAt = f.at) else copy(focus = EntryFocus.Keys(to), keyAt = to), EntryOutcome.None)
            }
            else -> none()
        }
        EntryFocus.Field -> when (button) {
            NxButton.LEFT -> EntryStep(cursorBy(-1), EntryOutcome.None)
            NxButton.RIGHT -> EntryStep(cursorBy(1), EntryOutcome.None)
            NxButton.DOWN -> EntryStep(copy(focus = EntryFocus.Keys(keyAt)), EntryOutcome.None)
            NxButton.UP -> EntryStep(if (active > 0) toField(active - 1) else copy(focus = EntryFocus.Button(0)), EntryOutcome.None)
            else -> none()
        }
        is EntryFocus.Button -> when (button) {
            NxButton.LEFT, NxButton.RIGHT -> EntryStep(copy(focus = EntryFocus.Button(1 - f.index)), EntryOutcome.None)
            NxButton.DOWN -> EntryStep(toField(0, EntryFocus.Field), EntryOutcome.None)
            NxButton.A -> EntryStep(this, if (f.index == 0) EntryOutcome.Confirm else EntryOutcome.Cancel)
            else -> none()
        }
    }
}

/** A key pressed (A on it, or a tap), the highlight staying where it is. Shift lasts one letter. */
fun TextEntry.tapKey(key: Key): EntryStep = when (key) {
    is Key.Char -> {
        val step = insert((if (shift) key.c.uppercaseChar() else key.c).toString())
        step.copy(entry = step.entry.copy(shift = false))
    }
    Key.Shift -> EntryStep(copy(shift = !shift), EntryOutcome.None)
    Key.Symbols -> EntryStep(copy(page = if (page == KeyPage.LETTERS) KeyPage.SYMBOLS else KeyPage.LETTERS), EntryOutcome.None)
    Key.Space -> insert(" ")
    Key.Backspace -> backspace()
    Key.Paste -> EntryStep(this, EntryOutcome.Paste)
    Key.Next -> next()
    Key.Done -> done()
}

/** A tap on field [index]: it becomes the active one, the cursor at its end. */
fun TextEntry.tapField(index: Int): TextEntry = toField(index.coerceIn(0, fields.lastIndex), EntryFocus.Field)

/** The clipboard's [text] at the cursor, as one line. */
fun TextEntry.paste(text: String): EntryStep = insert(text)

/** A physical keyboard's printable [text] at the cursor. */
fun TextEntry.type(text: String): EntryStep = insert(text)

/** The host's current [values]: a field changed from outside puts the cursor at its end when it is the active one. */
fun TextEntry.withValues(values: List<String>): TextEntry {
    if (values.size != fields.size) return this
    val changed = values[active] != text
    val fields = fields.mapIndexed { i, f -> f.copy(value = values[i]) }
    val cursor = if (changed) values[active].length else cursor.coerceIn(0, values[active].length)
    return copy(fields = fields, cursor = cursor)
}

/** The box's button the highlight is on, as the modal's footer counts them (0 confirm, 1 Cancel); -1 on a key or a field. */
fun TextEntry.buttonFocus(): Int = (focus as? EntryFocus.Button)?.index ?: -1
