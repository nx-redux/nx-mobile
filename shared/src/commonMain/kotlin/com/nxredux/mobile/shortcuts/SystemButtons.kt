package com.nxredux.mobile.shortcuts

/** The three buttons NX Redux needs from every pad, with the key code each sends on a standard one. */
enum class SystemRole(val label: String, val keyCode: Int) {
    SELECT("SELECT", KeyCodes.BUTTON_SELECT), START("START", KeyCodes.BUTTON_START), MENU("MENU", KeyCodes.BUTTON_MODE),
}

/** A controller model: bindings are kept per [key] (vendor + product, so two pads of one model share them); [name] is for the page. */
data class PadModel(val vendor: Int, val product: Int, val name: String) {
    val key: String get() = "${hex4(vendor)}:${hex4(product)}"

    private fun hex4(v: Int) = (v and 0xffff).toString(16).padStart(4, '0')
}

/**
 * A physical button: its key code, or its scan code when Android has no key code for it (KEYCODE_UNKNOWN, 0), as the
 * AYANEO Pocket Air Mini's button below the logo (scan 0x2f1). Build it with [of], so a key with a code never carries
 * its scan code. [device]: the input device that sends it when that is not the pad itself (a handheld whose SELECT and
 * START sit on a device beside its pad), as the platform names it; empty for the pad's own button.
 */
data class PhysicalKey(val keyCode: Int, val scanCode: Int, val device: String = "") {
    companion object {
        fun of(keyCode: Int, scanCode: Int, device: String = ""): PhysicalKey =
            if (keyCode == 0) PhysicalKey(0, scanCode, device) else PhysicalKey(keyCode, 0, device)
    }
}

/** What a pad says of its own buttons: whether it has a SELECT and a START key ([SystemButtons.standIn] fills in for one it lacks). */
data class PadKeys(val hasSelect: Boolean, val hasStart: Boolean)

/** Every model's overrides ([bound]: a role on a key other than its standard one). */
data class SystemButtonMap(val bound: Map<String, Map<SystemRole, PhysicalKey>> = emptyMap())

/**
 * Controls → System buttons: which physical button is SELECT, START and MENU on a pad model. A bound button acts only as its role ([translate]); a role with no override is its standard code.
 */
object SystemButtons {
    // An entry earlier versions wrote (the models their setup page had asked about): read past, never written.
    private const val ASKED = "asked"

    fun keyOf(map: SystemButtonMap, model: String, role: SystemRole): PhysicalKey =
        map.bound[model]?.get(role) ?: PhysicalKey(role.keyCode, 0)

    fun roleOf(map: SystemButtonMap, model: String, key: PhysicalKey): SystemRole? =
        SystemRole.entries.firstOrNull { keyOf(map, model, it) == key }

    /**
     * [role] on [key]. A key that is another role's now swaps: that role takes [role]'s old key, so SELECT and START
     * always keep a button. A role back on its standard key has no override.
     */
    fun bind(map: SystemButtonMap, model: String, role: SystemRole, key: PhysicalKey): SystemButtonMap {
        val keys = SystemRole.entries.associateWith { keyOf(map, model, it) }.toMutableMap()
        val other = SystemRole.entries.firstOrNull { it != role && keys[it] == key }
        if (other != null) keys[other] = keys.getValue(role)
        keys[role] = key
        val overrides = keys.filter { (r, k) -> k != PhysicalKey(r.keyCode, 0) }
        return map.copy(bound = if (overrides.isEmpty()) map.bound - model else map.bound + (model to overrides))
    }

    fun reset(map: SystemButtonMap, model: String): SystemButtonMap = map.copy(bound = map.bound - model)

    /**
     * The role a pad's key stands in for while nothing is bound: many pads send SELECT as Back and have no SELECT key,
     * a few send START as the Menu key; on a pad with its own START the Menu key is MENU. A pad with its own SELECT
     * keeps its Back as Back (a handheld's separate Back button). An AYANEO handheld's logo button is MENU: its pad
     * has no mode button.
     */
    fun standIn(keyCode: Int, keys: PadKeys): SystemRole? = when (keyCode) {
        KeyCodes.BACK -> SystemRole.SELECT.takeIf { !keys.hasSelect }
        KeyCodes.MENU -> if (keys.hasStart) SystemRole.MENU else SystemRole.START
        KeyCodes.AYANEO_AYA -> SystemRole.MENU
        else -> null
    }

    /**
     * The key code this press should become, null to leave it alone: its role's standard code when it's bound on
     * [model]; else, with the pad's [keys], the role it stands in for ([standIn]) unless that role is bound to
     * another button there (a bound role keeps to its button).
     */
    fun translate(map: SystemButtonMap, model: String, keyCode: Int, scanCode: Int, keys: PadKeys? = null): Int? {
        val bound = map.bound[model]
        if (bound != null) {
            val role = roleOf(map, model, PhysicalKey.of(keyCode, scanCode))
            if (role != null) return role.keyCode.takeIf { it != keyCode }
        }
        val role = keys?.let { standIn(keyCode, it) } ?: return null
        return role.keyCode.takeIf { bound?.containsKey(role) != true }
    }

    /** [translate] for a press from [device], which is no pad: the role some pad model keeps on that device's button. */
    fun translateFrom(map: SystemButtonMap, device: String, keyCode: Int, scanCode: Int): Int? {
        val key = PhysicalKey.of(keyCode, scanCode, device)
        val role = map.bound.values.firstNotNullOfOrNull { roles -> roles.entries.firstOrNull { it.value == key }?.key } ?: return null
        return role.keyCode.takeIf { it != keyCode }
    }

    /** `4001:0428=MENU:0:753,SELECT:106:0:00010001` (a fourth part: the other device that sends the button); [decode] reads it back. */
    fun encode(map: SystemButtonMap): String =
        map.bound.entries.sortedBy { it.key }.joinToString(";") { (model, roles) ->
            "$model=" + roles.entries.sortedBy { it.key }.joinToString(",") { (r, k) ->
                "${r.name}:${k.keyCode}:${k.scanCode}" + if (k.device.isEmpty()) "" else ":${k.device}"
            }
        }

    /** [encode]'s text; empty or unreadable text is no bindings. */
    fun decode(stored: String): SystemButtonMap {
        if (stored.isBlank()) return SystemButtonMap()
        return runCatching {
            val bound = mutableMapOf<String, Map<SystemRole, PhysicalKey>>()
            for (entry in stored.split(';')) {
                val (name, value) = entry.split('=', limit = 2).also { require(it.size == 2) }
                if (name == ASKED) continue
                bound[name] = value.split(',').associate { part ->
                    val parts = part.split(':').also { require(it.size == 3 || it.size == 4) }
                    SystemRole.valueOf(parts[0]) to PhysicalKey(parts[1].toInt(), parts[2].toInt(), parts.getOrElse(3) { "" })
                }
            }
            SystemButtonMap(bound)
        }.getOrElse { SystemButtonMap() }
    }
}
