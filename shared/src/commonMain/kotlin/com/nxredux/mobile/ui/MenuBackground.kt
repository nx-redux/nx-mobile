package com.nxredux.mobile.ui

import com.nxredux.mobile.settings.ConsoleArt
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.ui.nx.ConsolePad
import com.nxredux.mobile.ui.nx.consoleLogo
import com.nxredux.mobile.ui.nx.consolePad
import org.jetbrains.compose.resources.DrawableResource

/** What the main menu draws behind a tab (List, and a Carousel Consoles tab; Grid and the other Carousels are plain). */
sealed interface MenuBackground {
    /**
     * List Consoles on Background: the console's bg.png ([url]), drawn as a game list's List background.
     */
    data class Picture(val url: String) : MenuBackground
    /** List Home: the selected game ([entry]), its picture as a game list's List shows it (Layouts → List art). */
    data class Game(val entry: Listed) : MenuBackground
    /** Carousel Consoles on Background: the console's bg.png ([url]), drawn as the Backdrop game list's picture. */
    data class Backdrop(val url: String) : MenuBackground
    /**
     * Consoles: the selected console's logo, large and dimmed on the right (a console without controller art).
     */
    data class Logo(val res: DrawableResource) : MenuBackground
    /** Consoles: the selected console's controller alone, zoomed past the right edge and turned (Layouts → Consoles background). */
    data class Pad(val pad: ConsolePad) : MenuBackground
    /** Grid / Carousel Home, Collections, Tools, Grid Consoles, Consoles on None (or Background without a bg.png), a Carousel on Controller: plain black. */
    data object Plain : MenuBackground
}

/**
 * The background behind the highlighted [row] of [category]'s tab drawn in [style]. A List Consoles tab by Layouts →
 * Consoles background ([consoleArt]): CONTROLLER, the selected console's controller ([consolePad]) or its logo;
 * BACKGROUND, the console's own `.media/bg.png` ([SystemFolder.bgRef]: the row's default tag's, else any of its
 * systems'), drawn as a game list's picture, else plain black; NONE, plain black. A Carousel Consoles tab: BACKGROUND,
 * that bg.png as the Backdrop game list's picture ([MenuBackground.Backdrop]); CONTROLLER (the row's tiles carry the
 * controllers) and NONE, plain black.
 * A List Home shows the selected game's picture as a game list's List does ([MenuBackground.Game]: Layouts → List art);
 * a pinned tool and Pick a game are plain. Grid and Carousel Home are plain whatever is selected (the dashboard
 * carries the art), and everything else is plain black.
 */
internal fun menuBackground(
    style: MenuStyle, category: MenuCategory, row: MainRow?, consoleArt: ConsoleArt = ConsoleArt.CONTROLLER, portrait: Boolean = false,
): MenuBackground {
    if (style == MenuStyle.CAROUSEL && category == MenuCategory.CONSOLES) {
        val system = row as? MainRow.System
        return if (system != null && consoleArt == ConsoleArt.BACKGROUND) consoleBg(system)?.let(MenuBackground::Backdrop) ?: MenuBackground.Plain
        else MenuBackground.Plain
    }
    if (style != MenuStyle.LIST) return MenuBackground.Plain
    if (category == MenuCategory.HOME) return listHomeBackground(row)
    val system = row as? MainRow.System ?: return MenuBackground.Plain
    return when (consoleArt) {
        ConsoleArt.NONE -> MenuBackground.Plain
        ConsoleArt.BACKGROUND -> consoleBg(system)?.let(MenuBackground::Picture) ?: MenuBackground.Plain
        // A merged row (SFC + SUPA) falls back to its other tags' art.
        ConsoleArt.CONTROLLER -> system.systems.firstNotNullOfOrNull { consolePad(it.tag) }?.let { MenuBackground.Pad(it) }
            ?: system.systems.firstNotNullOfOrNull { consoleLogo(it.tag) }?.let(MenuBackground::Logo) ?: MenuBackground.Plain
    }
}

/** A console row's own `.media/bg.png`: the row's default tag's, else any of its systems'. */
private fun consoleBg(system: MainRow.System): String? =
    (system.backdrop.bgRef ?: system.systems.firstNotNullOfOrNull { it.bgRef })?.value

/** [menuBackground] for a List Home's [row] (Layouts → Consoles background is the consoles' and does not apply). */
private fun listHomeBackground(row: MainRow?): MenuBackground = when (row) {
    is MainRow.Continue -> MenuBackground.Game(row.entry)
    is MainRow.Pin -> MenuBackground.Game(row.entry)
    is MainRow.Shelf -> MenuBackground.Game(row.entry)
    else -> MenuBackground.Plain   // Pick a game, a pinned tool: plain black
}
