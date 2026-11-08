package com.nxredux.mobile.storage

import com.nxredux.mobile.library.Names
import com.nxredux.mobile.runner.CoreRegistry

enum class SaveKind { BATTERY, CLOCK }

/** One game's battery save or clock file, whatever Save format named it. */
data class SaveKey(val kind: SaveKind, val game: String)

/** Save file names ↔ the game they belong to, across the four Save formats (MinUI, Generic, Retroarch ±compressed). */
object SaveKeys {
    /** Every extension a game file of any console can have, plus the archives a game may be launched from. */
    private val ROM_EXTENSIONS: Set<String> by lazy {
        (CoreRegistry.consoles.flatMap { it.extensions } +
            CoreRegistry.consoles.flatMap { it.tags }.mapNotNull { CoreRegistry.forTag(it) }.flatMap { it.extensions } +
            listOf("zip", "7z")).mapTo(HashSet()) { it.lowercase() }
    }

    /**
     * The game a save or clock file belongs to, or null when [fileName] is neither a `.sav`, `.srm` (any case) nor
     * `.rtc` file. The game is the name with its ROM extension dropped (see [romKey]).
     */
    fun of(fileName: String): SaveKey? {
        val dot = fileName.lastIndexOf('.')
        if (dot <= 0) return null
        val kind = when (fileName.substring(dot + 1).lowercase()) {
            "sav", "srm" -> SaveKind.BATTERY
            "rtc" -> SaveKind.CLOCK
            else -> return null
        }
        return SaveKey(kind, romKey(fileName.substring(0, dot)))
    }

    internal val ARCHIVES = setOf("zip", "7z")
    private val BATTERY = setOf("sav", "srm")
    private val CARRIED = BATTERY + "rtc"

    /**
     * The game a ROM entry is named after: its extension dropped only when that extension is a known ROM or archive
     * extension, so a dot inside a title (`Super Mario Bros. 3`) is kept. A disc folder has no extension to drop. After
     * an archive extension a ROM extension goes too (`X.gb.zip` → `X`): an archive named after its ROM saves as
     * `X.gb.zip.sav` (MinUI) but `X.gb.sav` / `X.gb.srm` (Generic, Retroarch: minarch strips only the last extension),
     * whose key drops `.gb`, so keying the ROM `X.gb` would split one game in two.
     */
    fun romKey(name: String): String {
        val base = dropRomExtension(name)
        if (base == name || name.substringAfterLast('.').lowercase() !in ARCHIVES) return base
        val inner = dropRomExtension(base)
        return if (base.substringAfterLast('.').lowercase() in ARCHIVES) base else inner
    }

    private fun dropRomExtension(name: String): String =
        if (Names.extensionOf(name) in ROM_EXTENSIONS) Names.stripExtension(name) else name

    /**
     * True for a battery save named `<name>.<rom ext>.sav` / `.srm` whose ROM extension is no archive (`X.gb.sav`): ROM
     * `X.gb`'s MinUI name and ROM `X.gb.zip`'s (or `.7z`'s) Generic or Retroarch name alike, so it does not tell the ROM.
     */
    fun romAmbiguous(fileName: String): Boolean {
        if (Names.extensionOf(fileName) !in BATTERY) return false
        val stem = Names.stripExtension(fileName)
        return dropRomExtension(stem) != stem && stem.substringAfterLast('.').lowercase() !in ARCHIVES
    }

    /**
     * The ROM file name to name a game's save after, from save or clock file names of that game: [fileName]'s own (its
     * extension dropped, `X.gb.zip.sav` → `X.gb.zip`, `X.gb.rtc` → `X.gb`) when it carries one that is not
     * [romAmbiguous], else the first of [others] (newest first) that does. An ambiguous `X.gb.sav` among them counts as
     * ROM `X.gb` only when another name is that ROM's Generic or Retroarch name (`X.sav`, `X.srm`), which ROM `X.gb.zip`
     * never gives (taking `X.gb.sav` as ROM `X.gb` would rename ROM `X.gb.zip`'s Generic save to `X.sav`, which minarch
     * misses). Null when the names do not tell.
     */
    fun romOf(fileName: String, others: List<String> = emptyList()): String? {
        val names = listOf(fileName) + others
        names.firstNotNullOfOrNull { n -> carriedRom(n)?.takeIf { !romAmbiguous(n) } }?.let { return it }
        return names.firstNotNullOfOrNull { n ->
            carriedRom(n)?.takeIf { rom ->
                romAmbiguous(n) && names.any { it == batteryName(SaveFormat.GENERIC, rom) || it == batteryName(SaveFormat.RETROARCH, rom) }
            }
        }
    }

    /** The ROM file name a save or clock file name ends in (`Emerald.gba.sav` → `Emerald.gba`), else null. */
    private fun carriedRom(fileName: String): String? {
        if (Names.extensionOf(fileName) !in CARRIED) return null
        return Names.stripExtension(fileName).takeIf { romKey(it) != it }
    }

    /** The battery-save file name [format] gives the ROM [romFileName] (minarch's `saveRam` name, no folder). */
    fun batteryName(format: SaveFormat, romFileName: String): String = when (format) {
        SaveFormat.MINUI -> "$romFileName.sav"
        SaveFormat.GENERIC -> "${minarchStem(romFileName)}.sav"
        SaveFormat.RETROARCH, SaveFormat.RETROARCH_COMPRESSED -> "${minarchStem(romFileName)}.srm"
    }

    /**
     * The file name [key] takes under [format]. MinUI and clock files are named after the full ROM file name, so they
     * return null when [romFileName] is unknown; the other formats use [batteryName] of [romFileName] when it is known
     * and is [key]'s game ([romKey]), else the game name alone (`X.gb.zip` → `X.gb.sav`, not `X.sav`).
     */
    fun fileName(key: SaveKey, format: SaveFormat, romFileName: String?): String? = when (key.kind) {
        SaveKind.CLOCK -> romFileName?.let { "$it.rtc" }
        SaveKind.BATTERY -> when (format) {
            SaveFormat.MINUI -> romFileName?.let { "$it.sav" }
            SaveFormat.GENERIC, SaveFormat.RETROARCH, SaveFormat.RETROARCH_COMPRESSED ->
                romFileName?.takeIf { romKey(it) == key.game }?.let { batteryName(format, it) }?.takeIf { of(it) == key }
                    ?: if (format == SaveFormat.GENERIC) "${key.game}.sav" else "${key.game}.srm"
        }
    }
}
