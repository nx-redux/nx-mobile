package com.nxredux.mobile.external

// Each console lists every tag its games can carry: forTag matches the game's own tag.
private val PS = setOf("PS", "PSX")
private val GB = setOf("GB", "GBC")
private val GBA = setOf("GBA", "MGBA")
private val NES = setOf("FC", "FDS")
private val SNES = setOf("SFC", "SUPA")
private val MD = setOf("MD", "GPGX", "SEGACD", "GPGX_SEGACD")
private val SMS = setOf("SMS", "GPGX_SMS")
private val GG = setOf("GG", "GPGX_GG")
private val SG1000 = setOf("SG1000", "GPGX_SG1000")
private val N64 = setOf("N64", "N64DD")

private const val EXPLUS_ACTIVITY = "com.imagine.BaseActivity"
private const val FMS_ACTIVITY = "com.fms.emulib.TVActivity"
private const val MUPEN_ACTIVITY = "paulscode.android.mupen64plusae.SplashActivity"
private const val MELONDS_ACTIVITY = "me.magnum.melonds.ui.emulator.EmulatorActivity"
private const val CITRA_ACTIVITY = "org.citra.citra_emu.activities.EmulationActivity"
private const val YUZU_ACTIVITY = "org.yuzu.yuzu_emu.activities.EmulationActivity"
private const val FLYCAST_ACTIVITY = "com.flycast.emulator.MainActivity"
private val flycastPackages = listOf("com.flycast.emulator")

private fun explus(id: String, name: String, pkg: String, tags: Set<String>) =
    ExternalEmulator(id = id, name = name, packages = listOf(pkg), activity = EXPLUS_ACTIVITY, pass = GamePass.VIEW_DATA, tags = tags)

/** Pizza Boy's free and paid builds name their activity after their own package, so each is an entry. */
private fun pizzaBoy(id: String, name: String, pkg: String, activity: String, tags: Set<String>) = ExternalEmulator(
    id = id, name = name, packages = listOf(pkg), activity = activity, pass = GamePass.URI_EXTRA, extraKey = "rom_uri",
    clearTask = true, tags = tags,
)

private const val MAME_PACKAGE = "com.seleuco.mame4d2024"
private const val MAME_ACTIVITY = "com.seleuco.mame4droid.MAME4droid"

/** MAME4droid as [machine]; the game is found by [media] (its slot option) and its folder is the ROM path for BIOS sets. */
private fun mameMachine(id: String, tag: String, machine: String, media: String) = ExternalEmulator(
    id = id, name = "MAME4droid Current", packages = listOf(MAME_PACKAGE), activity = MAME_ACTIVITY, pass = GamePass.VIEW_DATA,
    data = machine, extras = mapOf("cli_params" to "-rompath '${GameTokens.DIR}' $media '${GameTokens.PATH}'"), tags = setOf(tag),
)

// Order is the default menu order for a console with several apps.
internal val externalEmulatorTable: List<ExternalEmulator> = listOf(
    // Nintendo Switch
    ExternalEmulator(
        id = "eden", name = "Eden", packages = listOf("dev.eden.eden_emulator", "dev.legacy.eden_emulator"),
        activity = YUZU_ACTIVITY, pass = GamePass.VIEW_DATA, action = "android.nfc.action.TECH_DISCOVERED", tags = setOf("SWITCH"),
    ),
    ExternalEmulator(
        id = "eden-nightly", name = "Eden Nightly",
        packages = listOf("dev.eden.eden_emulator.nightly", "dev.legacy.eden_emulator.nightly"),
        activity = YUZU_ACTIVITY, pass = GamePass.VIEW_DATA, action = "android.nfc.action.TECH_DISCOVERED", tags = setOf("SWITCH"),
    ),
    ExternalEmulator(
        id = "kenji-nx", name = "Kenji-NX", packages = listOf("org.kenjinx.android"), activity = "org.kenjinx.android.MainActivity",
        pass = GamePass.URI_EXTRA, extraKey = "bootPath", action = "org.kenjinx.android.LAUNCH_GAME", tags = setOf("SWITCH"),
    ),
    // Nintendo Wii U
    ExternalEmulator(
        id = "cemu", name = "Cemu", packages = listOf("info.cemu.cemu"), activity = "info.cemu.cemu.emulation.EmulationActivity",
        pass = GamePass.VIEW_DATA, tags = setOf("WIIU"),
    ),
    // Sony PlayStation 3
    ExternalEmulator(
        id = "aps3e", name = "aPS3e", packages = listOf("aenu.aps3e", "aenu.aps3e.premium"), activity = "aenu.aps3e.EmulatorActivity",
        pass = GamePass.URI_EXTRA, extraKey = "iso_uri", action = "aenu.intent.action.APS3E", tags = setOf("PS3"),
    ),
    ExternalEmulator(
        id = "armsx3", name = "ARMSX3", packages = listOf("com.armsx3"), activity = "com.armsx2.Main",
        pass = GamePass.VIEW_DATA, tags = setOf("PS3"),
    ),
    ExternalEmulator(
        id = "emucorec", name = "EmuCoreC", packages = listOf("com.sbro.emucorec"), activity = "com.sbro.emucorec.core.ps3.Emulator",
        pass = GamePass.PATH_EXTRA, extraKey = "gamePath", tags = setOf("PS3"),
    ),
    // PlayStation Vita and PlayStation 4 boot installed titles by id: the game is a small text file holding it.
    ExternalEmulator(
        id = "vita3k", name = "Vita3K", packages = listOf("org.vita3k.emulator"), activity = "org.vita3k.emulator.Emulator",
        pass = GamePass.VIEW_DATA, extras = mapOf("title_id" to GameTokens.TEXT), tags = setOf("PSVITA"),
    ),
    ExternalEmulator(
        id = "emucorev", name = "EmuCoreV", packages = listOf("com.sbro.emucorev"), activity = "com.sbro.emucorev.core.vita.Emulator",
        pass = GamePass.VIEW_DATA, arrayExtras = mapOf("AppStartParameters" to listOf("-r", GameTokens.TEXT)), tags = setOf("PSVITA"),
    ),
    ExternalEmulator(
        id = "bachatas4", name = "BachataS4", packages = listOf("com.bachatas4.android", "com.bachatas4.android.github"),
        activity = "com.bachatas4.android.DirectLaunchActivity", pass = GamePass.VIEW_DATA,
        extras = mapOf("game_id" to GameTokens.TEXT), tags = setOf("PS4"),
    ),
    // Sega Saturn
    ExternalEmulator(
        id = "yaba-sanshiro", name = "Yaba Sanshiro 2",
        packages = listOf("org.devmiyax.yabasanshioro2", "org.devmiyax.yabasanshioro2.pro"), activity = "org.uoyabause.android.Yabause",
        pass = GamePass.URI_EXTRA, extraKey = "org.uoyabause.android.FileNameUri", clearTask = true, tags = setOf("SATURN"),
    ),
    explus("saturn-emu", "Saturn.emu", "com.explusalpha.SaturnEmu", setOf("SATURN")),
    // Microsoft Xbox and Xbox 360
    ExternalEmulator(
        id = "x1-box", name = "X1 BOX", packages = listOf("com.izzy2lost.x1box"), activity = "com.izzy2lost.x1box.LauncherActivity",
        pass = GamePass.VIEW_DATA, tags = setOf("XBOX"),
    ),
    ExternalEmulator(
        id = "hakux", name = "hakuX", packages = listOf("com.rfandango.haku_x"), activity = "com.rfandango.haku_x.LauncherActivity",
        pass = GamePass.VIEW_DATA, tags = setOf("XBOX"),
    ),
    ExternalEmulator(
        id = "ax360e", name = "aX360e", packages = listOf("aenu.ax360e.free", "aenu.ax360e"), activity = "aenu.ax360e.EmulatorActivity",
        pass = GamePass.URI_EXTRA, extraKey = "game_uri", action = "aenu.intent.action.AX360E", tags = setOf("XBOX360"),
    ),
    ExternalEmulator(
        id = "xendroid", name = "XenDroid", packages = listOf("xendroid.compose"), activity = "xendroid.compose.EmulatorHostActivity",
        pass = GamePass.VIEW_DATA, tags = setOf("XBOX360"),
    ),
    // MAME4droid runs a console as a machine named in the data, with the game's path on its command line.
    mameMachine("mame4droid-jaguar", "JAGUAR", "jaguar", "-cart"),
    mameMachine("mame4droid-neocd", "NEOCD", "neocdz", "-cdrm"),
    mameMachine("mame4droid-cdi", "CDI", "cdimono1", "-cdrom"),
    // Nintendo 64 and 64DD
    ExternalEmulator(
        id = "m64plus-fz", name = "M64Plus FZ",
        packages = listOf("org.mupen64plusae.v3.fzurita", "org.mupen64plusae.v3.fzurita.amazon", "org.mupen64plusae.v3.fzurita.pro"),
        activity = MUPEN_ACTIVITY, pass = GamePass.VIEW_DATA, tags = N64,
    ),
    ExternalEmulator(
        id = "mupen64plus-ae", name = "Mupen64Plus AE", packages = listOf("org.mupen64plusae.v3.alpha"), activity = MUPEN_ACTIVITY,
        pass = GamePass.VIEW_DATA, tags = N64,
    ),
    // PC Engine, its CD games and the SuperGrafx
    ExternalEmulator(
        id = "pce-emu", name = "PCE.emu", packages = listOf("com.PceEmu"), activity = EXPLUS_ACTIVITY, pass = GamePass.VIEW_DATA,
        tags = setOf("PCE", "SGX"),
    ),
    // DS, Game Boy and GBA: one list, ordered so each console keeps its own default order.
    ExternalEmulator(
        id = "melonds", name = "melonDS", packages = listOf("me.magnum.melonds"), activity = MELONDS_ACTIVITY,
        pass = GamePass.URI_EXTRA, extraKey = "uri", action = "me.magnum.melonds.LAUNCH_ROM", tags = setOf("NDS"),
    ),
    ExternalEmulator(
        id = "melonds-nightly", name = "melonDS Nightly", packages = listOf("me.magnum.melonds.nightly"), activity = MELONDS_ACTIVITY,
        pass = GamePass.URI_EXTRA, extraKey = "uri", action = "me.magnum.melonds.nightly.LAUNCH_ROM", tags = setOf("NDS"),
    ),
    explus("gbc-emu", "GBC.emu", "com.explusalpha.GbcEmu", GB),
    ExternalEmulator(
        id = "my-oldboy", name = "My OldBoy!", packages = listOf("com.fastemulator.gbc"), activity = "com.fastemulator.gbc.EmulatorActivity",
        pass = GamePass.VIEW_DATA, tags = GB,
    ),
    explus("gba-emu", "GBA.emu", "com.explusalpha.GbaEmu", GBA),
    ExternalEmulator(
        id = "my-boy", name = "My Boy!", packages = listOf("com.fastemulator.gba"), activity = "com.fastemulator.gba.EmulatorActivity",
        pass = GamePass.VIEW_DATA, tags = GBA,
    ),
    ExternalEmulator(
        id = "linkboy", name = "Linkboy", packages = listOf("com.pixelrespawn.linkboy"), activity = "com.pixelrespawn.linkboy.EmulatorActivity",
        pass = GamePass.VIEW_DATA, tags = GB + GBA,
    ),
    pizzaBoy("pizza-boy-c", "Pizza Boy C", "it.dbtecno.pizzaboy", "it.dbtecno.pizzaboy.MainActivity", GB),
    pizzaBoy("pizza-boy-c-pro", "Pizza Boy C Pro", "it.dbtecno.pizzaboypro", "it.dbtecno.pizzaboypro.MainActivity", GB),
    pizzaBoy("pizza-boy-a", "Pizza Boy A", "it.dbtecno.pizzaboygba", "it.dbtecno.pizzaboygba.MainActivity", GBA),
    pizzaBoy("pizza-boy-a-pro", "Pizza Boy A Pro", "it.dbtecno.pizzaboygbapro", "it.dbtecno.pizzaboygbapro.MainActivity", GBA),
    ExternalEmulator(
        id = "noods", name = "NooDS", packages = listOf("com.hydra.noods"), activity = "com.hydra.noods.FileBrowser",
        pass = GamePass.PATH_EXTRA, extraKey = "LaunchPath", clearTask = true, tags = setOf("NDS") + GBA,
    ),
    ExternalEmulator(
        id = "skyemu", name = "SkyEmu", packages = listOf("com.sky.SkyEmu"), activity = "com.sky.SkyEmu.EnhancedNativeActivity",
        pass = GamePass.VIEW_DATA, clearTask = true, tags = setOf("NDS") + GB + GBA,
    ),
    ExternalEmulator(
        id = "watermelonds", name = "WatermelonDS", packages = listOf("me.magnum.melondualds"), activity = MELONDS_ACTIVITY,
        pass = GamePass.URI_EXTRA, extraKey = "uri", action = "me.magnum.melonds.LAUNCH_ROM", tags = setOf("NDS"),
    ),
    ExternalEmulator(
        id = "seedlessds", name = "SeedlessDS", packages = listOf("com.seedlessds.app"), activity = "com.seedlessds.app.LaunchGame",
        pass = GamePass.PATH_EXTRA, extraKey = "rom_path", tags = setOf("NDS"),
    ),
    // Nintendo 3DS
    ExternalEmulator(
        id = "azahar", name = "Azahar", packages = listOf("io.github.lime3ds.android", "org.azahar_emu.azahar"), activity = CITRA_ACTIVITY,
        pass = GamePass.VIEW_DATA, clearTask = true, tags = setOf("3DS"),
    ),
    ExternalEmulator(
        id = "azaharplus", name = "AzaharPlus", packages = listOf("io.github.azaharplus.android"), activity = CITRA_ACTIVITY,
        pass = GamePass.VIEW_DATA, clearTask = true, tags = setOf("3DS"),
    ),
    // Its source opens games only from its own list (GameLauncher takes a pandroid-game id); the data may only open the app.
    ExternalEmulator(
        id = "panda3ds", name = "Panda3DS", packages = listOf("com.panda3ds.pandroid"), activity = "com.panda3ds.pandroid.app.MainActivity",
        pass = GamePass.VIEW_DATA, tags = setOf("3DS"),
    ),
    // GameCube and Wii
    ExternalEmulator(
        id = "dolphin", name = "Dolphin", packages = listOf("org.dolphinemu.dolphinemu"),
        activity = "org.dolphinemu.dolphinemu.ui.main.TvMainActivity", pass = GamePass.URI_EXTRA, extraKey = "AutoStartFile",
        action = "android.intent.action.MAIN", categories = listOf("android.intent.category.LEANBACK_LAUNCHER"), tags = setOf("GC", "WII"),
    ),
    // NES and Famicom Disk System
    explus("nes-emu", "NES.emu", "com.explusalpha.NesEmu", NES),
    ExternalEmulator(
        id = "ines", name = "iNES", packages = listOf("com.fms.ines.free"), activity = FMS_ACTIVITY, pass = GamePass.VIEW_DATA,
        clearTask = true, tags = NES,
    ),
    // SNES
    explus("snes9x-explus", "Snes9x EX+", "com.explusalpha.Snes9xPlus", SNES),
    // PlayStation
    ExternalEmulator(
        id = "duckstation", name = "DuckStation", packages = listOf("com.github.stenzek.duckstation"),
        activity = "com.github.stenzek.duckstation.EmulationActivity", pass = GamePass.URI_EXTRA, extraKey = "bootPath",
        clearTask = true, tags = PS,
    ),
    ExternalEmulator(
        id = "armsx1", name = "ARMSX1", packages = listOf("com.nanodata.armsx"), activity = "com.armsx2.Main",
        pass = GamePass.VIEW_DATA, tags = PS,
    ),
    ExternalEmulator(
        id = "epsxe", name = "ePSXe", packages = listOf("com.epsxe.ePSXe"), activity = "com.epsxe.ePSXe.ePSXe",
        pass = GamePass.PATH_EXTRA, extraKey = "com.epsxe.ePSXe.isoName", action = "android.intent.action.MAIN", tags = PS,
    ),
    // PlayStation 2
    ExternalEmulator(
        id = "armsx2", name = "ARMSX2", packages = listOf("com.armsx2", "com.armsx2.nightly"), activity = "com.armsx2.MainActivity",
        pass = GamePass.VIEW_DATA, tags = setOf("PS2"),
    ),
    ExternalEmulator(
        id = "emucorex", name = "EmuCoreX", packages = listOf("com.sbro.emucorex"), activity = "com.sbro.emucorex.MainActivity",
        pass = GamePass.VIEW_DATA, clearTask = true, tags = setOf("PS2"),
    ),
    ExternalEmulator(
        id = "play", name = "Play!", packages = listOf("com.virtualapplications.play"), activity = "com.virtualapplications.play.MainActivity",
        pass = GamePass.VIEW_DATA, tags = setOf("PS2"),
    ),
    // PSP
    ExternalEmulator(
        id = "ppsspp", name = "PPSSPP", packages = listOf("org.ppsspp.ppsspp", "org.ppsspp.ppssppgold"), activity = "org.ppsspp.ppsspp.PpssppActivity",
        pass = GamePass.VIEW_DATA, categories = listOf("android.intent.category.DEFAULT"), tags = setOf("PSP"),
    ),
    // Dreamcast
    ExternalEmulator(id = "flycast-dc", name = "Flycast", packages = flycastPackages, activity = FLYCAST_ACTIVITY, pass = GamePass.VIEW_DATA, tags = setOf("DC")),
    ExternalEmulator(
        id = "redream", name = "Redream", packages = listOf("io.recompiled.redream"), activity = "io.recompiled.redream.MainActivity",
        pass = GamePass.VIEW_DATA, tags = setOf("DC"),
    ),
    // Genesis, Sega CD, Master System, Game Gear, SG-1000
    explus("md-emu", "MD.emu", "com.explusalpha.MdEmu", MD + SMS),
    pizzaBoy("pizza-boy-sc", "Pizza Boy SC", "it.dbtecno.pizzaboyscbasic", "it.dbtecno.pizzaboyscbasic.MainActivity", MD + SMS + GG),
    pizzaBoy("pizza-boy-sc-pro", "Pizza Boy SC Pro", "it.dbtecno.pizzaboyscpro", "it.dbtecno.pizzaboyscpro.MainActivity", MD + SMS + GG),
    ExternalEmulator(
        id = "mastergear", name = "MasterGear", packages = listOf("com.fms.mg"), activity = FMS_ACTIVITY, pass = GamePass.VIEW_DATA,
        clearTask = true, tags = SMS + GG + SG1000,
    ),
    // Handhelds and the older consoles
    explus("ngp-emu", "NGP.emu", "com.explusalpha.NgpEmu", setOf("NGP", "NGPC")),
    explus("swan-emu", "Swan.emu", "com.explusalpha.SwanEmu", setOf("WSC")),
    explus("lynx-emu", "Lynx.emu", "com.explusalpha.LynxEmu", setOf("LYNX")),
    explus("a2600-emu", "2600.emu", "com.explusalpha.A2600Emu", setOf("A2600")),
    ExternalEmulator(
        id = "colem", name = "ColEm", packages = listOf("com.fms.colem", "com.fms.colem.deluxe"), activity = FMS_ACTIVITY,
        pass = GamePass.VIEW_DATA, clearTask = true, tags = setOf("COLECO"),
    ),
    explus("msx-emu", "MSX.emu", "com.explusalpha.MsxEmu", setOf("COLECO")),
    // Arcade. MAME4droid copies a content URI into its own roms folder and runs the set by file name, so a parent or
    // BIOS set has to be in that folder too.
    ExternalEmulator(
        id = "mame4droid", name = "MAME4droid Current", packages = listOf(MAME_PACKAGE),
        activity = MAME_ACTIVITY, pass = GamePass.VIEW_DATA, tags = setOf("FBN"),
    ),
    explus("neo-emu", "NEO.emu", "com.explusalpha.NeoEmu", setOf("FBN")),
    mameMachine("mame4droid-a7800", "A7800", "a7800", "-cart"),
    // Flycast runs Naomi sets from the same app; a separate entry keeps it off the other FBN games.
    ExternalEmulator(
        id = "flycast-arcade", name = "Flycast", packages = flycastPackages, activity = FLYCAST_ACTIVITY, pass = GamePass.VIEW_DATA,
        tags = setOf("FBN"), sets = SetRule.NAOMI,
    ),
    ExternalEmulator(
        id = "super3", name = "SUPER3", packages = listOf("com.izzy2lost.super3"), activity = "com.izzy2lost.super3.MainActivity",
        pass = GamePass.VIEW_DATA, tags = setOf("FBN"), sets = SetRule.MODEL3,
    ),
    // PICO-8
    ExternalEmulator(
        id = "pixl8", name = "Pixl8", packages = listOf("be.codedreams.pixl8"), activity = "be.codedreams.pixl8.MainActivity",
        pass = GamePass.VIEW_DATA, tags = setOf("P8"),
    ),
    ExternalEmulator(
        id = "infinity", name = "Infinity", packages = listOf("me.dt2dev.infinity"), activity = "me.dt2dev.infinity.SchemeActivity",
        pass = GamePass.VIEW_DATA, tags = setOf("P8"),
    ),
    ExternalEmulator(
        id = "pico8-android", name = "PICO-8", packages = listOf("io.wip.pico8"), activity = "com.godot.game.GodotAppLauncher",
        pass = GamePass.VIEW_DATA, tags = setOf("P8"),
    ),
    // Doom: "game" picks the app's GZDoom engine (a path there would start Doom 3); the WAD is named by the player's
    // command line in the sidecar.
    ExternalEmulator(
        id = "idtech4a", name = "idTech4A++", packages = listOf("com.karin.idTech4Amm"), activity = "com.n0n3m4.q3e.Q3EMain",
        pass = GamePass.VIEW_DATA, extras = mapOf("game" to "zdoom"), clearTask = true, commandsSidecar = true, tags = setOf("PRBOOM"),
    ),
)

/** Sega Model 3 set names (Supermodel's game list), lower case: the sets SUPER3 runs. */
internal val model3Sets: Set<String> = setOf(
    "bassdx", "dayto2pe", "daytona2", "dirtdvls", "dirtdvlsau", "dirtdvlsg", "dirtdvlsj", "dirtdvlsu", "eca", "ecaj", "ecap",
    "ecau", "fvipers2", "fvipers2o", "getbass", "getbassdx", "getbassur", "harley", "harleya", "lamachin", "lemans24", "lostwsga",
    "lostwsgp", "magtruck", "mgtrkbad", "oceanhun", "oceanhuna", "scud", "scudau", "scuddx", "scuddxo", "scudplus", "scudplusa",
    "skichamp", "spikeofe", "spikeout", "srally2", "srally2dx", "srally2p", "srally2pa", "swtrilgy", "swtrilgya", "swtrilgyp",
    "vf3", "vf3a", "vf3c", "vf3tb", "von2", "von254g", "von2a", "von2o", "vs2", "vs215", "vs215o", "vs298", "vs29815", "vs299",
    "vs29915", "vs29915a", "vs29915j", "vs299a", "vs299j", "vs2v991",
)
