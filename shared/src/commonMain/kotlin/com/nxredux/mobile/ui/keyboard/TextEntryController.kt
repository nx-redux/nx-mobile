package com.nxredux.mobile.ui.keyboard

import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * An open prompt as the keyboard sees it: its identity ([id]; another one starts a fresh entry), its [fields] as the
 * host holds them, a setter per field and its confirm word ([confirmLabel]: the keyboard's Done key and the START hint read it).
 */
internal class PromptSpec(val id: Any, val fields: List<EntryField>, val setters: List<(String) -> Unit>, val confirmLabel: String)

/**
 * The NX keyboard bound to whichever prompt is open ([spec]; null: none, or the platform's keyboard types). Edits go
 * through the prompt's own setters; confirming and cancelling come back as the A and B the prompt already answers.
 */
internal class TextEntryController(private val spec: () -> PromptSpec?, private val clipboard: () -> String?) {
    private val _entry = MutableStateFlow<TextEntry?>(null)
    val entry: StateFlow<TextEntry?> = _entry.asStateFlow()
    private var current: PromptSpec? = null

    /** The open prompt's confirm word ("OK" with none). */
    val confirmLabel: String get() = current?.confirmLabel ?: "OK"

    /** Follows the open prompt: a new one starts fresh, the same one takes the host's values (a change made elsewhere). */
    fun sync() {
        val s = spec()
        if (s == null) { current = null; _entry.value = null; return }
        val e = _entry.value
        _entry.value = if (current?.id != s.id || e == null || e.fields.size != s.fields.size) TextEntry(s.fields) else e.withValues(s.fields.map { it.value })
        current = s
    }

    /** A pad press: null when the keyboard used it, else the button the prompt hears (A to confirm, B to cancel). */
    fun press(button: NxButton, repeat: Boolean = false): NxButton? {
        sync()
        val e = _entry.value ?: return button
        return apply(e.press(button, repeat))
    }

    /** A tap on the key at [at]; the button the prompt hears, as [press]. */
    fun tapKeyAt(at: KeyPos): NxButton? = _entry.value?.let { apply(it.tapKeyAt(at)) }

    fun tapField(index: Int) { _entry.value?.let { _entry.value = it.tapField(index) } }

    /** The clipboard at the cursor, as the Paste key. */
    fun paste(): NxButton? = _entry.value?.let { apply(it.tapKey(Key.Paste)) }

    fun toggleReveal() { _entry.value?.let { _entry.value = it.toggleReveal() } }

    /** A physical keyboard's printable [text]. */
    fun type(text: String): NxButton? = _entry.value?.let { apply(it.type(text)) }

    private fun apply(step: EntryStep): NxButton? {
        _entry.value = step.entry
        return when (val o = step.outcome) {
            is EntryOutcome.Edit -> { current?.setters?.getOrNull(o.field)?.invoke(o.text); null }
            EntryOutcome.Confirm -> NxButton.A
            EntryOutcome.Cancel -> NxButton.B
            EntryOutcome.Paste -> clipboard()?.let { apply(step.entry.paste(it)) }
            EntryOutcome.None -> null
        }
    }
}
