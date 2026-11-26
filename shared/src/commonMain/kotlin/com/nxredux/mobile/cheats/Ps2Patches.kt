package com.nxredux.mobile.cheats

import com.nxredux.mobile.options.ConfigSource
import com.nxredux.mobile.options.FrontendOptions

/** A named group in the running PS2 game's patch files. [bootOnly]: some of its lines only apply when the game boots. */
data class Ps2PatchGroup(val name: String, val bootOnly: Boolean)

/**
 * The PS2 Patches tab's data. The core lists the game's named patch groups in [LIST_FILE] beside the game's memory card
 * once it knows the game's CRC (cores/patches/armsx2/0012) and takes `patch:<name>` codes through the cheat calls.
 */
object Ps2Patches {
    const val LIST_FILE = "nx-patches.txt"
    const val WIDESCREEN = "Widescreen 16:9"
    const val NO_INTERLACING = "No-Interlacing"
    const val CODE_PREFIX = "patch:"
    const val WIDESCREEN_OPTION = "armsx2_widescreen_patches"
    const val NO_INTERLACING_OPTION = "armsx2_no_interlacing_patches"
    /** The core options the Patches tab replaced: locked off, their stored values kept ([initialEnabled]). */
    val RETIRED_OPTIONS = setOf(WIDESCREEN_OPTION, NO_INTERLACING_OPTION)

    /** The core's card folder is `<saves>/<title of the file it opens>`: an archive's unpacked [innerName], else [romFileName]. */
    fun listPath(savesDir: String, romFileName: String, innerName: String? = null): String =
        "$savesDir/${(innerName ?: romFileName).substringBeforeLast('.')}/$LIST_FILE"

    /** A save's running values without the retired options: their stored lines stay as written ([initialEnabled]'s default). */
    fun withoutRetired(running: List<Pair<String, String>>): List<Pair<String, String>> = running.filter { it.first !in RETIRED_OPTIONS }

    /** The groups in a list file, or null when it is not one (the core writes the `crc=` line first, groups or not). */
    fun parse(text: String): List<Ps2PatchGroup>? {
        val lines = text.lines()
        if (lines.firstOrNull()?.startsWith("crc=") != true) return null
        return lines.drop(1).mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab < 0) return@mapNotNull null
            val name = line.substring(tab + 1)
            if (name.isEmpty()) null else Ps2PatchGroup(name, bootOnly = line.substring(0, tab) == "boot")
        }
    }

    /**
     * The game's saved choice; without one, the retired Widescreen / No-Interlacing options' stored values
     * ([rawCoreValues], read before the resolve drops the now-locked keys) stay a default.
     */
    fun initialEnabled(saved: List<String>?, rawCoreValues: Map<String, String>?): Set<String> {
        if (saved != null) return saved.toSet()
        return buildSet {
            if (rawCoreValues?.get(WIDESCREEN_OPTION) == "enabled") add(WIDESCREEN)
            if (rawCoreValues?.get(NO_INTERLACING_OPTION) == "enabled") add(NO_INTERLACING)
        }
    }

    /** The saved set, only from the game file (a console file never carries it). */
    fun savedFrom(values: Map<String, String>, source: ConfigSource): List<String>? =
        if (source == ConfigSource.GAME) values[FrontendOptions.PATCHES]?.let(CheatSet::decode) else null
}

/**
 * The running game's patch switches. [groups]: null until the core has listed them. [sessionOn]: on at some point since
 * the last boot, so its writes may still be in memory. [bootPending]: boot-only groups turned on since then.
 */
data class Ps2PatchSet(
    val groups: List<Ps2PatchGroup>?,
    val enabled: Set<String>,
    val sessionOn: Set<String>,
    val bootPending: Set<String>,
) {
    /** Saved names come back normalised ([CheatSet.normalise]): they are matched to the listed names that way. */
    fun withGroups(groups: List<Ps2PatchGroup>): Ps2PatchSet {
        val listed = groups.associateBy { CheatSet.normalise(it.name) }
        fun canon(names: Set<String>) = names.map { listed[CheatSet.normalise(it)]?.name ?: it }.toSet()
        return copy(groups = groups, enabled = canon(enabled), sessionOn = canon(sessionOn))
    }

    fun isOn(name: String): Boolean = name in enabled

    fun toggle(name: String, on: Boolean): Ps2PatchSet =
        if (on) {
            val boot = groups?.any { it.name == name && it.bootOnly } == true
            copy(enabled = enabled + name, sessionOn = sessionOn + name, bootPending = if (boot) bootPending + name else bootPending)
        } else {
            copy(enabled = enabled - name, bootPending = bootPending - name)
        }

    fun afterReset(): Ps2PatchSet = copy(sessionOn = enabled, bootPending = emptySet())

    fun note(name: String): String = when {
        name in bootPending -> TAKES_EFFECT
        name !in enabled && name in sessionOn -> FULLY_OFF
        else -> ""
    }

    fun needsReset(): Boolean = groups.orEmpty().any { note(it.name).isNotEmpty() }

    /** What the core receives; before the list is known, every enabled name (the core ignores names it does not have). */
    fun codes(): List<String> {
        val listed = groups?.map { it.name }?.toSet()
        return enabled.filter { listed == null || it in listed }.sorted().map { Ps2Patches.CODE_PREFIX + it }
    }

    fun savedKeys(): List<String> = enabled.map(CheatSet::normalise).sorted()

    companion object {
        const val TAKES_EFFECT = "Takes effect after Reset"
        const val FULLY_OFF = "Fully off after Reset"
        val EMPTY = Ps2PatchSet(null, emptySet(), emptySet(), emptySet())

        fun start(enabled: Set<String>): Ps2PatchSet = Ps2PatchSet(null, enabled, enabled, emptySet())

        /** The core's list arrived: the codes change when it renamed a saved name (normalised), so they go again. */
        fun resendAfterList(before: Ps2PatchSet, after: Ps2PatchSet): Boolean = before.codes() != after.codes()
    }
}
