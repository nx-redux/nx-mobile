# NX Redux Mobile

NX Redux for Android phones, tablets and Android handhelds. The same pick-up-and-play retro launcher as the [NX Redux](https://github.com/nx-redux/nx-handheld) handheld firmware: pick up, pick a game, play. The app uses the handheld's `Roms/`, `Bios/` and `Collections/` folders, so one library works on both, and [Device Sync](https://nxredux.com/mobile/device-sync/) carries games, saves and save states between a phone and an NX Redux handheld.

📖 **User documentation: [nxredux.com/mobile](https://nxredux.com/mobile/getting-started/)**: setup, guides for every feature, each emulator, and the settings, illustrated with screenshots from the app.


## Requirements

- Android 8.0 or later, on a 64-bit ARM (arm64) device.
- Your own games and, for the consoles that need them, BIOS files. The app ships none.

Download the APK from [Releases](https://github.com/nx-redux/nx-mobile/releases) and see [Getting Started](https://nxredux.com/mobile/getting-started/).

## What's in it

Every feature has its own page on [nxredux.com](https://nxredux.com/mobile/getting-started/);
this list is the map, not the manual.

- A tabbed [main menu](https://nxredux.com/mobile/main-menu/) (Home, Consoles, Collections, Tools) in List, Grid, Carousel or Backdrop [layouts](https://nxredux.com/mobile/layouts/), with [search](https://nxredux.com/mobile/main-menu/#search), an on-screen pad, and any Android controller.
- [Emulators](https://nxredux.com/mobile/emulators/) from the Game Boy to the Dreamcast, Nintendo 3DS and Wii & GameCube, with an [in-game menu](https://nxredux.com/mobile/in-game-menu/) for save states, options, shaders and [cheats](https://nxredux.com/mobile/cheats/).
- [Netplay](https://nxredux.com/mobile/netplay/) with another phone or a handheld, and [local multiplayer](https://nxredux.com/mobile/multiplayer/) for up to four controllers.
- [RetroAchievements](https://nxredux.com/mobile/retroachievements/) (offline unlocks, synced later), [artwork](https://nxredux.com/mobile/artwork/), a [Game Switcher](https://nxredux.com/mobile/game-switcher/) and a [Game Tracker](https://nxredux.com/mobile/game-tracker/).
- Use it as your phone's [home screen](https://nxredux.com/mobile/launcher/), with your Android games beside the rest, and [foldables](https://nxredux.com/mobile/foldables/) get Flex mode.

## Supported consoles

Every emulator is built from source with our patches (`cores/`). Where a console has two, the first is the default and the
other is chosen per console or per game. BIOS files go in `Bios/`; [each emulator's page](https://nxredux.com/mobile/emulators/)
lists the file names.

| Console | Emulator | BIOS |
| :--- | :--- | :--- |
| **Nintendo 3DS** | [Azahar](https://github.com/azahar-emu/azahar) | None (decrypted games) |
| **Nintendo Wii** | [Dolphin](https://github.com/libretro/dolphin) | None |
| **Nintendo GameCube** | [Dolphin](https://github.com/libretro/dolphin) | None |
| **Nintendo DS** | [melonDS DS](https://github.com/JesseTG/melonds-ds) | Optional (free one built in) |
| **Nintendo 64** | [Mupen64Plus-Next](https://github.com/libretro/mupen64plus-libretro-nx) | None |
| **Game Boy Advance** | [gpSP](https://github.com/libretro/gpsp), [mGBA](https://github.com/libretro/mgba) | Optional (open one built in) |
| **Game Boy / Game Boy Color** | [Gambatte](https://github.com/libretro/gambatte-libretro) | None |
| **Super Game Boy** | [mGBA](https://github.com/libretro/mgba) | None |
| **Super Nintendo** | [Snes9x](https://github.com/libretro/snes9x), [Supafaust](https://github.com/libretro/supafaust) | None |
| **NES / Famicom** | [FCEUmm](https://github.com/libretro/libretro-fceumm) | None |
| **Famicom Disk System** | [FCEUmm](https://github.com/libretro/libretro-fceumm) | Required |
| **Virtual Boy** | [Beetle VB](https://github.com/libretro/beetle-vb-libretro) | None |
| **Pokémon mini** | [PokeMini](https://github.com/libretro/PokeMini) | Optional (free one built in) |
| **PlayStation 2** | [ARMSX2](https://github.com/ARMSX2/ARMSX2) | Required |
| **PlayStation Portable** | [PPSSPP](https://github.com/hrydgard/ppsspp) | None |
| **PlayStation** | [PCSX-ReARMed](https://github.com/libretro/pcsx_rearmed), [SwanStation](https://github.com/libretro/swanstation) | Optional on PCSX-ReARMed, required on SwanStation |
| **Sega Dreamcast** | [Flycast](https://github.com/flyinghead/flycast) | Required |
| **Arcade (NAOMI / Atomiswave)** | [Flycast](https://github.com/flyinghead/flycast) | In the set |
| **Arcade** | [FinalBurn Neo](https://github.com/libretro/FBNeo) | In the set |
| **Sega Genesis / Mega Drive** | [PicoDrive](https://github.com/irixxxx/picodrive), [Genesis Plus GX](https://github.com/libretro/Genesis-Plus-GX) | None |
| **Sega CD / Mega CD** | [PicoDrive](https://github.com/irixxxx/picodrive), [Genesis Plus GX](https://github.com/libretro/Genesis-Plus-GX) | Required |
| **Sega 32X** | [PicoDrive](https://github.com/irixxxx/picodrive) | None |
| **Sega Master System / Game Gear / SG-1000** | [PicoDrive](https://github.com/irixxxx/picodrive), [Genesis Plus GX](https://github.com/libretro/Genesis-Plus-GX) | None |
| **TurboGrafx-16 / PC Engine** | [Beetle PCE Fast](https://github.com/libretro/beetle-pce-fast-libretro) | CD games only |
| **Neo Geo Pocket / Color** | [RACE](https://github.com/libretro/race) | None |
| **WonderSwan / Color** | [Beetle WonderSwan](https://github.com/libretro/beetle-wswan-libretro) | None |
| **Atari 2600** | [Stella 2014](https://github.com/libretro/stella2014-libretro) | None |
| **Atari 5200** | [a5200](https://github.com/libretro/a5200) | Optional (Altirra OS built in) |
| **Atari 7800** | [ProSystem](https://github.com/libretro/prosystem-libretro) | Optional |
| **Atari Lynx** | [Handy](https://github.com/libretro/libretro-handy) | Required |
| **ColecoVision** | [Gearcoleco](https://github.com/drhelius/Gearcoleco) | Required |
| **PICO-8** | [fake-08](https://github.com/jtothebell/fake-08) | None |
| **Doom** | [PrBoom](https://github.com/libretro/libretro-prboom) | Bundled (your own IWADs) |

## AI-assisted development

NX Redux Mobile is developed with the help of [Claude](https://claude.ai), Anthropic's AI model. The maintainer's own contributions are written by Claude from the maintainer's requirements and feedback, then tested on real devices and reviewed before shipping. This does not cover the bundled emulators, the LibretroDroid code the runner is built on, or other people's work. Full details on the [disclaimer page](https://nxredux.com/reference/disclaimer/).

## Credits

- [Filippo Scognamiglio](https://github.com/Swordfish90) for [LibretroDroid](https://github.com/Swordfish90/LibretroDroid), the libretro frontend the runner in `libretrodroid/` is forked from.
- The authors of every emulator core the app runs (licences on [nxredux.com/reference/licenses](https://nxredux.com/reference/licenses/)), and [RetroAchievements](https://retroachievements.org/) / [rcheevos](https://github.com/RetroAchievements/rcheevos) for the achievements ecosystem.
- [Kenney](https://kenney.nl/assets/input-prompts) for the PlayStation button symbols, and [GameTDB](https://www.gametdb.com) for the Wii controller database.
- Full list: [nxredux.com/reference/credits](https://nxredux.com/reference/credits/).

## License

Licensed under **GNU GPL v3.0**. See the [LICENSE](LICENSE) file, and [LICENSES/](LICENSES) and [THIRD_PARTY_ART.md](THIRD_PARTY_ART.md) for third-party terms.
