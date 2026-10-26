package com.nxredux.mobile.shortcuts

/** The bindings of one level (base, console or game): action -> button, touch slot -> action. */
data class ShortcutSet(
    val bindings: Map<ShortcutAction, ShortcutBinding>,
    val touch: Map<TouchSlot, ShortcutAction>,
) {
    /**
     * [action] on [binding] (null: None). An action that had [binding] gives it up (one button, one action, as
     * [Shortcuts.effective] treats an override); the map stays in [ShortcutAction] order, as [Shortcuts.encodeBase] writes it.
     */
    fun withBinding(action: ShortcutAction, binding: ShortcutBinding?): ShortcutSet {
        val kept = bindings.filter { (a, b) -> a != action && b != binding } + listOfNotNull(binding?.let { action to it })
        return copy(bindings = ShortcutAction.entries.filter { it in kept }.associateWith { kept.getValue(it) })
    }

    /** Touch button [slot] on [action] (null: none, off the pad), in [TouchSlot] order. Two slots may share an action. */
    fun withTouch(slot: TouchSlot, action: ShortcutAction?): ShortcutSet {
        val kept = touch.filterKeys { it != slot } + listOfNotNull(action?.let { slot to it })
        return copy(touch = TouchSlot.entries.filter { it in kept }.associateWith { kept.getValue(it) })
    }

    companion object {
        val EMPTY = ShortcutSet(emptyMap(), emptyMap())
    }
}

/**
 * What a console or game changes over the base: a present key overrides (a null value is "None here"), an absent key
 * inherits the base, so a later change to Controls -> Shortcuts still reaches every row not touched there.
 */
data class ShortcutOverrides(
    val bindings: Map<ShortcutAction, ShortcutBinding?> = emptyMap(),
    val touch: Map<TouchSlot, ShortcutAction?> = emptyMap(),
) {
    /**
     * [action] overridden to [binding] (null: None here). Another action overridden onto [binding] gives it up to None
     * (one button, one action, as [ShortcutSet.withBinding]); a base action on it is dropped by [Shortcuts.effective].
     */
    fun withBinding(action: ShortcutAction, binding: ShortcutBinding?): ShortcutOverrides {
        val kept = bindings.mapValues { (a, b) -> if (a != action && binding != null && b == binding) null else b } + (action to binding)
        return copy(bindings = ShortcutAction.entries.filter { it in kept }.associateWith { kept[it] })
    }

    /** [action] inherits the base again. */
    fun inheriting(action: ShortcutAction): ShortcutOverrides = copy(bindings = bindings - action)

    /** Touch button [slot] overridden to [action] (null: none here), in [TouchSlot] order. */
    fun withTouch(slot: TouchSlot, action: ShortcutAction?): ShortcutOverrides {
        val kept = touch + (slot to action)
        return copy(touch = TouchSlot.entries.filter { it in kept }.associateWith { kept[it] })
    }

    /** Touch button [slot] inherits the base again. */
    fun inheriting(slot: TouchSlot): ShortcutOverrides = copy(touch = touch - slot)

    companion object {
        val NONE = ShortcutOverrides()
    }
}

/** What fires in game. [blocked]: bound actions a console hotkey disables, with the hotkey that wins. */
data class EffectiveShortcuts(
    val bindings: Map<ShortcutAction, ShortcutBinding>,
    val touch: Map<TouchSlot, ShortcutAction>,
    val blocked: Map<ShortcutAction, ClaimedInput>,
)

object Shortcuts {
    const val PREFIX = "nx_bind_"
    private const val NONE = "none"

    fun keyFor(a: ShortcutAction): String = PREFIX + a.key
    fun keyFor(t: TouchSlot): String = PREFIX + t.key

    /** Reads the `nx_bind_*` entries of a cfg; blank or garbage values are as absent. */
    fun fromConfig(values: Map<String, String>): ShortcutOverrides {
        val bindings = LinkedHashMap<ShortcutAction, ShortcutBinding?>()
        for (a in ShortcutAction.entries) {
            val v = values[keyFor(a)]?.trim() ?: continue
            if (v == NONE) bindings[a] = null else ShortcutBinding.decode(v)?.let { bindings[a] = it }
        }
        val touch = LinkedHashMap<TouchSlot, ShortcutAction?>()
        for (t in TouchSlot.entries) {
            val v = values[keyFor(t)]?.trim() ?: continue
            if (v == NONE) touch[t] = null else ShortcutAction.fromKey(v)?.let { touch[t] = it }
        }
        return ShortcutOverrides(bindings, touch)
    }

    fun toConfig(o: ShortcutOverrides): List<Pair<String, String>> =
        ShortcutAction.entries.filter { it in o.bindings }.map { keyFor(it) to (o.bindings[it]?.encode() ?: NONE) } +
            TouchSlot.entries.filter { it in o.touch }.map { keyFor(it) to (o.touch[it]?.key ?: NONE) }

    /**
     * For a config save writing [o] ([toConfig]): drops every `nx_bind_*` line it doesn't write, since the cfg render keeps
     * any line it isn't given, and a dropped override must inherit again rather than stay in the file.
     */
    fun droppedOnSave(o: ShortcutOverrides): (String) -> Boolean {
        val written = toConfig(o).map { it.first }.toSet()
        return { key -> key.startsWith(PREFIX) && key !in written }
    }

    /**
     * The bindings that fire. Overrides replace base rows, and an override that takes a button drops any base action on
     * it (L24: one button, one action). Rewind actions go without [canRewind], mic ones without [canMic]. A binding a console hotkey claims moves to
     * [EffectiveShortcuts.blocked]: built-in hotkeys win.
     */
    fun effective(
        base: ShortcutSet,
        overrides: ShortcutOverrides,
        claimed: List<ClaimedInput>,
        canRewind: Boolean,
        canMic: Boolean = false,
    ): EffectiveShortcuts {
        val merged = LinkedHashMap(base.bindings)
        for ((action, binding) in overrides.bindings) {
            if (binding == null) {
                merged.remove(action)
            } else {
                merged.keys.filter { it !in overrides.bindings && merged[it] == binding }.forEach { merged.remove(it) }
                merged[action] = binding
            }
        }
        val touch = LinkedHashMap(base.touch)
        for ((slot, action) in overrides.touch) if (action == null) touch.remove(slot) else touch[slot] = action

        val allowed = { a: ShortcutAction -> (canRewind || !a.rewind) && (canMic || !a.mic) }
        val bindings = LinkedHashMap<ShortcutAction, ShortcutBinding>()
        val blocked = LinkedHashMap<ShortcutAction, ClaimedInput>()
        for (a in ShortcutAction.entries) {
            val b = merged[a] ?: continue
            if (!allowed(a) || b.keyCode in ShortcutBinding.GAME_KEYS) continue
            val hit = claimed.firstOrNull { it.binding == b }
            if (hit != null) blocked[a] = hit else bindings[a] = b
        }
        return EffectiveShortcuts(bindings, touch.filterValues { allowed(it) && it.touch }, blocked)
    }

    /** One line for a cfg or pref: `toggle_ff=110+103;touch_1=reset`. */
    fun encodeBase(s: ShortcutSet): String =
        (ShortcutAction.entries.filter { it in s.bindings }.map { "${it.key}=${s.bindings.getValue(it).encode()}" } +
            TouchSlot.entries.filter { it in s.touch }.map { "${it.key}=${s.touch.getValue(it).key}" }).joinToString(";")

    fun decodeBase(s: String): ShortcutSet {
        val bindings = LinkedHashMap<ShortcutAction, ShortcutBinding>()
        val touch = LinkedHashMap<TouchSlot, ShortcutAction>()
        for (part in s.split(';')) {
            val k = part.substringBefore('=', "").trim()
            val v = part.substringAfter('=', "").trim()
            ShortcutAction.fromKey(k)?.let { a -> ShortcutBinding.decode(v)?.let { bindings[a] = it } }
            TouchSlot.entries.firstOrNull { it.key == k }?.let { t -> ShortcutAction.fromKey(v)?.let { touch[t] = it } }
        }
        return ShortcutSet(bindings, touch)
    }
}
