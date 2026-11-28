package com.nxredux.mobile.options

import com.nxredux.mobile.gpudriver.GpuDriver
import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.runner.ButtonMap
import com.nxredux.mobile.runner.ButtonMapRows
import com.nxredux.mobile.runner.CoreDescriptor
import com.nxredux.mobile.runner.MapRow
import com.nxredux.mobile.runner.N3dsNewControls
import com.nxredux.mobile.runner.NdsRenderer
import com.nxredux.mobile.runner.WiiController

/** A Console Settings row; [disabledNote] set: the row shows the note, dimmed, and takes no change. */
data class ConsoleRow(val option: FrontendOption, val value: String, val disabledNote: String? = null)

/** One emulator tag's settings as Emulator Settings / Game Settings show them. */
data class EmulatorPage(
    val tag: String, val coreId: String, val emulator: String,
    val console: List<ConsoleRow>,
    /** Null when the core has no bundled table; empty when it has one with nothing to show. */
    val core: List<CoreOption>?,
    val forGame: Boolean, val gameFileExists: Boolean,
    /**
     * The button mapping the page edits ([ButtonMap.resolve]: a game's own lines, else the console's) and the Button Mapping
     * rows it is shown on ([ButtonMapRows.rowsFor]); [consoleMap]: the console file's, which a game's follows until it differs.
     */
    val buttonMap: ButtonMap = ButtonMap.DEFAULT, val mapRows: List<MapRow> = emptyList(),
    val consoleMap: ButtonMap = ButtonMap.DEFAULT,
)

object EmulatorSettings {
    /**
     * The page for [tag] on [descriptor]'s core. Values: the game file when [forGame] and it exists, else the tag's
     * console file ([CoreConfig.resolve]: one file, never both), over [defaults] (the descriptor's and any platform
     * ones), over the table's defaults. A stored value the table doesn't list is kept as an extra choice. The
     * descriptor's [CoreDescriptor.offlineHiddenOptions] and table keys in the frontend namespace never appear; console
     * rows locked in the cfg are hidden, as on the in-game Console Settings page. [secondScreen] false (none connected) hides Bottom screen, its value kept. [vulkanFailed]: Vulkan failed for the
     * core on this phone ([VulkanFallback]), noted on the Graphics API row. [screen]: the window's [ScreenClass] (null:
     * unknown), which shapes the DS and 3DS layout rows ([ConsoleSettings.tableFor]). [displayMax]: the biggest DS or 3DS 3D scale the display reaches (null:
     * unknown), which shapes the 3D Resolution scales. [biosFiles]: the ".bin" names in the PS2 BIOS folder, the PS2 BIOS row's choices.
     * [gpuDrivers]: the installed custom GPU drivers (null: the feature is unavailable, no GPU driver row). [gpuDriverResolved]:
     * a custom driver will load for this page, so the Vulkan-failed note (about the system driver) is left off the Graphics API
     * row. [autoVulkan]: Graphics API's Auto is Vulkan for this core on this phone; [mali]: its GPU is a Mali. [gpuDriverCrashNote]: put on the GPU driver row (its line and before its info, still editable), for a game whose driver crashed.
     */
    fun page(
        tag: String, descriptor: CoreDescriptor, table: CoreOptionTable?, defaults: Map<String, String>,
        console: ConfigFile?, game: ConfigFile?, forGame: Boolean, vulkanFailed: Boolean = false,
        secondScreen: Boolean = true, screen: ScreenClass? = null, displayMax: Int? = null,
        biosFiles: List<String> = emptyList(), gpuDrivers: List<GpuDriver>? = null, gpuDriverResolved: Boolean = false,
        gpuDriverCrashNote: String? = null, autoVulkan: Boolean = false, mali: Boolean = false,
    ): EmulatorPage {
        val tableDefaults = table?.options.orEmpty().associate { it.key to it.defaultValue }
        val frontendDefaults = FrontendOptions.defaults(tag)
        val resolved = CoreConfig.resolve(
            frontendDefaults + tableDefaults + defaults, console, if (forGame) game else null,
            forced = descriptor.lockedOptions, hidden = descriptor.hiddenValues,
        )
        val software = resolved.values[NdsRenderer.RENDER_MODE] == NdsRenderer.SOFTWARE
        // (the Wii U's landscape row: the skin's default where the device's window lets the skin take it, Pad skin's
        // On being the default; the game reads only a stored pick, so the label alone follows)
        val wiiuSkin = screen != null && WiiuOptions.skinTakesLandscape(WiiuOptions.lockedLayoutRows(true, screen))
        val consoleRows = ConsoleSettings.tableFor(tag, screen, displayMax, biosFiles, gpuDrivers, autoVulkan, mali, wiiuSkinLandscape = wiiuSkin).filter { it.key !in resolved.locked && (descriptor.rewind || it.key !in SpeedOptions.REWIND_KEYS) && (secondScreen || it.key != BottomScreenOptions.KEY) }
            .shownFor(resolved.values)
            .map {
                // The DS and 3DS layout rows read their old keys too, as the game does (no pick: the row default).
                val value = (if (it.key in FrontendOptions.NO_PICK_KEYS) FrontendOptions.layoutValue(resolved.values, it.key) else resolved.values[it.key]) ?: it.default
                ConsoleSettings.fitted(VulkanFallback.noted(it, vulkanFailed && !gpuDriverResolved), value).let { (option, shown) ->
                    val noted = if (option.key == GpuDriverOptions.KEY && gpuDriverCrashNote != null) option.copy(brief = gpuDriverCrashNote, info = gpuDriverCrashNote + "\n" + option.info) else option
                    ConsoleRow(noted, shown, NdsOptions.SOFTWARE_NOTE.takeIf { software && option.key in NdsOptions.RESOLUTION_KEYS })
                }
            }
        val core = table?.let { t ->
            val withValues = t.options.filter { it.key !in descriptor.offlineHiddenOptions && !FrontendOptions.isFrontendKey(it.key) }.map { o ->
                val v = resolved.values[o.key] ?: o.defaultValue
                o.copy(value = v, values = if (v in o.values || v.isEmpty()) o.values else o.values + v)
            }
            CoreOptionFilter.offline(CoreOptionFilter.withHiddenValues(withValues, descriptor.hiddenValues), resolved.locked)
        }
        return EmulatorPage(
            tag, descriptor.id, descriptor.name, consoleRows, core, forGame, forGame && game != null,
            ButtonMap.fromFiles(if (forGame) game else null, console, tag).keeping(ButtonMap.locked(tag)), mapRows(tag, resolved.values),
            ButtonMap.fromFiles(null, console, tag).keeping(ButtonMap.locked(tag)),
        )
    }

    /**
     * The Button Mapping rows as [values] label them: a Wii controller or New 3DS controls picked outright; Auto, which
     * each game decides, keeps the plain pad's names.
     */
    private fun mapRows(tag: String, values: Map<String, String>): List<MapRow> {
        val wii = values[WiiOptions.CONTROLLER]?.takeIf { it != WiiController.AUTO }?.let { WiiController.resolve(it, null) }
        return ButtonMapRows.rowsFor(tag, wii, N3dsNewControls.resolve(values[N3dsOptions.NEW_CONTROLS], null))
    }

    /** The console file's text after [key] = [value]: that line only; locks, binds and every other line kept. */
    fun consoleWrite(console: ConfigFile?, key: String, value: String): String =
        CoreConfig.render(console, listOf(key to value), FrontendOptions::migrateKey)

    /** The console file's text with [map] as its `nx_map_*` lines: the others and the retired layout key dropped ([ButtonMap.droppedOnSave]). */
    fun consoleMapWrite(console: ConfigFile?, map: ButtonMap): String =
        CoreConfig.render(console, ButtonMap.toConfig(map), FrontendOptions::migrateKey, drop = ButtonMap.droppedOnSave(map))

    /**
     * The game file's text with [map] as its mapping ([ButtonMap.gameConfig]: no lines while it matches [page]'s console
     * mapping), in place or as [gameWrite]'s first snapshot; null when there is no game file and nothing to write.
     */
    fun gameMapWrite(page: EmulatorPage, console: ConfigFile?, game: ConfigFile?, map: ButtonMap): String? {
        val lines = ButtonMap.gameConfig(map, page.consoleMap)
        val drop = ButtonMap.droppedOnSave(lines)
        if (game != null) return CoreConfig.render(game, lines, FrontendOptions::migrateKey, drop = drop)
        if (lines.isEmpty()) return null
        return CoreConfig.render(console, page.core.orEmpty().map { it.key to it.value } + lines, FrontendOptions::migrateKey, drop = drop)
    }

    /**
     * The game file's text after [key] = [value]: in place, or a full snapshot of [console] + [page]'s core values on the
     * first change. The snapshot takes values from [page] (never the resolved map), so keys the page hides are not added;
     * invisible table options are left to their defaults, not written.
     */
    fun gameWrite(page: EmulatorPage, console: ConfigFile?, game: ConfigFile?, key: String, value: String): String {
        if (game != null) return CoreConfig.render(game, listOf(key to value), FrontendOptions::migrateKey)
        // A game file replaces the console file whole when read, so the first one starts as a full copy (in-game "Save for game"),
        // less the console's button mapping, which the game keeps following ([ButtonMap.resolve]).
        val core = page.core.orEmpty().map { it.key to it.value }
        return CoreConfig.render(console, core + (key to value), FrontendOptions::migrateKey, drop = { it.startsWith(ButtonMap.PREFIX) })
    }
}
