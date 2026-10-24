package com.nxredux.mobile.ui

/**
 * Settings -> System's Licenses group: plain paragraphs (not rows) of what the app ships and under which licences, then a
 * row to [LICENSES_URL]. The full texts ship in the APK's licenses/ assets (androidApp stageLicenses) and on the website.
 */
internal val ABOUT_LICENSES: List<String> = listOf(
    "NX Redux is free software under the GNU General Public License v3.0. It does not host or distribute games: you " +
        "play the ones you own.",
    "Its text is set in MiSans by Xiaomi, embedded under Xiaomi's MiSans font terms, which let apps embed the font " +
        "and ask them to say so. The font files are not distributed on their own.",
    "Games run on libretro emulator cores, among them mGBA, Snes9x, Genesis Plus GX, PCSX ReARMed, Mupen64Plus-Next, " +
        "melonDS DS, Azahar, Dolphin, flycast, PPSSPP, SwanStation and FBNeo. Each core keeps its own licence, most of " +
        "them the GPL; FBNeo's does not allow commercial use.",
    "The game runner is a fork of LibretroDroid (GPL-3.0) with oboe (Apache 2.0), rcheevos for RetroAchievements " +
        "(MIT), libretro-common (MIT), libchdr (BSD-3-Clause) with miniz (MIT), zstd (BSD-3-Clause) and the LZMA SDK " +
        "(public domain). Device Sync and netplay copy saves with rsync (GPL-3.0-or-later).",
    "Kotlin, Compose Multiplatform, kotlinx and AndroidX libraries are under the Apache License 2.0.",
)

/** The website's full licence list (the docs' reference/licenses page). */
internal const val LICENSES_URL = "https://nxredux.com/reference/licenses/"
