package com.nxredux.mobile.runner

import com.nxredux.mobile.netplay.NetplaySession

/**
 * A file loaded with the game: one a disc sheet names ([fileName] as the sheet spells it) or an arcade set's parent/BIOS set
 * found beside it ([fileName] `<set>.zip|7z` in lowercase); the core asks for that name. [ref] is the file.
 */
data class Companion(val fileName: String, val ref: ContentRef)

data class GameSession(
    val systemTag: String,
    val displayName: String,
    val core: CoreDescriptor,
    val content: ContentRef,
    /** The folder holding [content] (the library entry's folder): a disc sheet's companions are looked up there. */
    val folder: ContentRef,
    /** The ROM's real file name (not the map.txt display name); keys all on-disk save data. */
    val fileName: String,
    /** Save slot to load right after start (0..7 manual, 9 auto); null = start fresh. */
    val resumeSlot: Int? = null,
    /**
     * Files handed to the core beside [content], from the same folder: the ones a `.cue`/`.m3u`/`.ccd`/`.toc` names, or an
     * arcade set's parent and BIOS sets ([com.nxredux.mobile.library.ArcadeCompanions]).
     */
    val companions: List<Companion> = emptyList(),
    /** Arcade BIOS sets not beside [content] that the core must find in the system dir (Bios/FBN/); a missing parent is not listed. */
    val systemSets: List<String> = emptyList(),
    /** The library path (GamePath, the Recently Played key) — game time's identity; null when unknown. */
    val gamePath: String? = null,
    /** The game's box art as the library resolved it ([com.nxredux.mobile.ui.panelArt]): the game panel's header picture; null, none. */
    val artPath: String? = null,
    /** Set for a Wi-Fi netplay launch: this device's role, the peer and the agreed core; null for every other launch. */
    val netplay: NetplaySession? = null,
    /** The name a Minimal launch spread its black from, for the game screen to hold on the same pixels; null: none (no sequence). */
    val launchName: LaunchName? = null,
    /** Picked in the Game Switcher: the runner keeps a [SwitcherReturnMark] for this launch, so its end reopens the switcher. */
    val fromSwitcher: Boolean = false,
)
