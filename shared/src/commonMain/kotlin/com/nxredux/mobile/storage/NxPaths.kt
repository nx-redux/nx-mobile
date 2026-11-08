package com.nxredux.mobile.storage

import com.nxredux.mobile.runner.CoreRegistry

/**
 * nx-redux / minarch on-disk layout, relative to the data root (on Android: `DataRoot.of`, the home folder, or the
 * app-private root until the data has moved there).
 * Mirrors minarch exactly so a Device Sync can map files 1:1: `<rom>` is the ROM file name with its last
 * extension stripped only when that extension is 2..4 characters long (minarch's `stripExtension`), and
 * `<rom.ext>` is the full name; names are never sanitized. Slots are 0-based on disk. The battery-save, clock and
 * state names follow the handheld's save settings ([SaveNaming]).
 */
class NxPaths(
    private val tag: String,
    private val coreId: String,
    private val romFileName: String,
    val naming: SaveNaming = SaveNaming.DEFAULT,
    val innerFileName: String? = null,
) {
    /** `<rom>` above: the launched file name with its short extension stripped (core-owned save folders, netplay). */
    val stem: String = minarchStem(romFileName)

    /**
     * minarch's `alt_name`: the file inside an unpacked archive when Use extracted file name is on, else the launched
     * file name. Battery saves, clock files and states are named after it; markers keep [romFileName].
     */
    val saveName: String = innerFileName?.takeIf { naming.useExtractedFileName } ?: romFileName
    /** [saveName] minus a short extension: the stem battery saves are named after (the DS `.dsv` lookup, [NdsDsvSave], uses it). */
    val saveStem: String = minarchStem(saveName)

    /** One save folder per console, whichever emulator runs the game (`MGBA` → `Saves/GBA`); states stay per emulator. */
    val savesDir = "Saves/${CoreRegistry.consoleTag(tag)}"
    val saveRam: String get() = "$savesDir/${batteryName(naming.saveFormat, saveName)}"
    val saveCompressed: Boolean get() = naming.saveFormat.compressed
    /**
     * The configured battery-save path first, then [saveName] under every other format, then (for an unpacked archive)
     * the other name of the Use extracted file name setting under every format; once each.
     */
    fun saveRamCandidates(): List<String> =
        altNames.flatMap { alt -> (listOf(naming.saveFormat) + SaveFormat.entries).map { "$savesDir/${batteryName(it, alt)}" } }.distinct()
    /** minarch `RTC_getPath`: the extension is kept, whatever the format. No fallback name: the handheld has none. */
    val clock: String get() = "$savesDir/$saveName.rtc"

    /** Under the handheld's tag ([CoreRegistry.handheldTag]: `GPGX_SMS` → `GPGX-genesis_plus_gx`), as Device Sync mirrors it 1:1. */
    val statesDir = ".userdata/shared/${CoreRegistry.handheldTag(tag)}-$coreId"
    fun state(slot: Int): String = "$statesDir/${stateName(naming.stateFormat, slot)}"
    val stateCompressed: Boolean get() = naming.stateFormat.compressed
    /**
     * The configured state path for [slot] first, then the slot under every other format, then (for an unpacked archive)
     * under the other name of the Use extracted file name setting in every format; once each.
     */
    fun stateCandidates(slot: Int): List<String> =
        altNames.flatMap { alt -> (listOf(naming.stateFormat) + StateFormat.entries).map { "$statesDir/${stateName(it, slot, alt)}" } }.distinct()

    /** The same game with [innerFileName] known (the runner learns it from the unpack). */
    fun withInner(innerFileName: String?): NxPaths = NxPaths(tag, coreId, romFileName, naming, innerFileName)

    /** [saveName], then the name the other Use extracted file name setting gives (only for an unpacked archive). */
    private val altNames: List<String>
        get() = listOfNotNull(saveName, innerFileName?.let { if (saveName == it) romFileName else it }).distinct()

    private fun batteryName(format: SaveFormat, alt: String): String = SaveKeys.batteryName(format, alt)

    /** minarch `SavePaths_state` (ma_save_paths.c:14-41); disk slots 0..7, auto = [AUTO_SLOT]. */
    private fun stateName(format: StateFormat, slot: Int, alt: String = saveName): String {
        require(slot in 0 until SLOT_COUNT || slot == AUTO_SLOT) { "slot $slot" }
        val stem = minarchStem(alt)
        return when (format) {
            StateFormat.MINUI -> "$alt.st$slot"
            StateFormat.RETROARCH_ISH, StateFormat.RETROARCH_ISH_COMPRESSED ->
                if (slot == AUTO_SLOT) "$stem.state.auto" else "$stem.state.$slot"
            StateFormat.RETROARCH, StateFormat.RETROARCH_COMPRESSED -> when (slot) {
                AUTO_SLOT -> "$stem.state.auto"
                0 -> "$stem.state"
                else -> "$stem.state$slot"
            }
        }
    }

    val minuiDir = ".userdata/shared/.minui/${CoreRegistry.handheldTag(tag)}"
    fun thumb(slot: Int): String {
        require(slot in 0 until SLOT_COUNT || slot == AUTO_SLOT) { "slot $slot" }
        return "$minuiDir/$romFileName.$slot.bmp"
    }
    val lastSlot = "$minuiDir/$romFileName.txt"

    /** This phone's own (never synced): per phone tag, so each console keeps its Console Settings on Genesis Plus GX. */
    val configDir = ".userdata/$PLATFORM/$tag-$coreId"
    val consoleConfig = "$configDir/minarch.cfg"
    val gameConfig = "$configDir/$romFileName.cfg"

    companion object {
        const val SLOT_COUNT = 8
        const val AUTO_SLOT = 9
        const val PLATFORM = "android"
        const val RECENTS = ".userdata/shared/.minui/recent.txt"
        const val GAME_TIME_DB = ".userdata/shared/game_time.db"
        const val SHORTCUTS = ".userdata/shared/.minui/shortcuts.txt"
        const val HIDDEN = ".userdata/$PLATFORM/hidden.txt"
        fun romPath(systemFolder: String, fileName: String) = "/Roms/$systemFolder/$fileName"
    }
}

/** minarch `stripExtension`: drop the last `.xxx` only when the suffix, dot included, is 3..5 chars. */
internal fun minarchStem(name: String): String {
    val dot = name.lastIndexOf('.')
    return if (dot >= 0 && name.length - dot in 3..5) name.substring(0, dot) else name
}

private val RZIP_MAGIC = "#RZIPv".encodeToByteArray()

/** True for RetroArch/minarch RZIP-compressed files. */
fun isRzipCompressed(bytes: ByteArray): Boolean =
    bytes.size >= RZIP_MAGIC.size && RZIP_MAGIC.indices.all { bytes[it] == RZIP_MAGIC[it] }
