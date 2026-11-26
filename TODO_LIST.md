# TODO list

## PSP ad hoc over the USB cable

To fix (both reproduce with `tools/device-tests/psp_cable.py`, checks `late` and `replug`):

- [ ] A cable plugged into a running Wi-Fi lobby drops the lobby: as the cable starts the link logs `left`, `serve off`, `log in again`, though a running session should be kept. Looks like Android's USB dialog pausing the game; not traced in code.
- [ ] A pull and a re-plug within about 10 s: the host-side phone reports `cable up` 2 s after the plug on a cable that is not linked yet, moves onto it and serves alone, then goes back to the Wi-Fi about 20 s later. For PSP count only a peer in link shape as up (`AndroidUsbCable.peer()`).

Still owed:

- [ ] Quit crash seen once on the Fold: SIGSEGV in PPSSPP's `DrawEngineGLES` constructor under `retro_unload_game`, a quit before the game finished booting behind a USB dialog.
- [ ] After a pull, both phones re-enter the lobby and meet over the Wi-Fi (not run: the phones were on different subnets).
- [ ] From review, not seen on a device: the Wi-Fi lost mid-game is never told to the runner (`LaunchPipeline` `onLost` only clears `pspWifi`; `pspWifiUpdate` sends nothing for no address), so with a cable coming the election may serve alone on the old Wi-Fi address after the hold.

## Review round 3: device checks owed

- [ ] RA: revoke/sign out mid-game, unlock, sign in, sync → the unlock arrives; quit right after an unlock → no toast during the quit.
- [ ] Merge a split console in the home summary → play time follows the games. (Nova 2026-11-22: not run, every game is in the read-only SD extra.)
- [ ] Device Sync: stray `.tmp` in Saves and a stale `.part` in Roms are not sent; SAF home scrape/sync sidecars still write. Saves `.tmp`/`.part` not sent either way (Nova ↔ AYANEO 2026-11-22); Roms part needs ROMs on; SAF sidecars owed. A `.tmp` inside a state folder (`.userdata/shared/<TAG>-<core>/`) is sent: its rules have no `*.tmp`/`*.part` exclude.
- [ ] gpSP Wireless Adapter phone↔Brick (Union Room trade), phone host then Brick host; patched phone with an older Brick build still connects.
- [ ] Controls editor: d-pad mode on DC/GC/N64, drag the d-pad toward the face buttons, save → lands where the editor showed (in game and from Settings). Needs a phone without a built-in controller (the AYANEO's hides the touch pad in game). Title "Pad skin"/"Overlay", Reset console and Appearance reset passed (AYANEO 2026-11-22).
- [ ] Threaded cores across pause/resume (input lock): N64 netplay input still arrives (two devices); a game from a picked file still opens. Dolphin GC/Wii incl. Wii pointer, Azahar (GLES and Vulkan), ARMSX2 and PPSSPP passed (Nova 2026-11-22).

## Game info from ScreenScraper

Device checks still owed:

- [ ] "Fetch all" re-asks remembered misses; "Fetch missing" and highlights don't. (Not run 2026-11-22: ScreenScraper quota.)
- [ ] Signed out, signed in, quota exhausted (silent; resumes the next day); offline (retries after a few minutes). (Not run 2026-11-22: needs sign-out and network toggles on the user's account.)
- [ ] Device Sync phone↔phone/handheld carries `.media/info/` for home games; the receiver shows it without re-fetching.
- [ ] Short top screen (Fold cover / skin glass): the synopsis drops first, nothing clips.
- [ ] Browsing art: a game with all its art only gets its info; Artwork running meanwhile → no background fetch; toggle off → nothing. Resting on a game without art passed (Nova 2026-11-22: art on the top screen and tile, no notification).

## Dual screen: bottom acts, top informs

Device checks still owed:

- [ ] Fold cover display and the menu skin's two glasses (small top): details and stats fit without clipping; the stats shelf hides cleanly when too short. (AYANEO 2026-11-22: not offered, the built-in controller hides the menu skin; needs the Fold.)
- [ ] Thor / Duo (both screens touch): top-screen taps act once, never move focus.

## Controls editor: skin and overlay layouts

Device checks still owed:

- [ ] Landscape: R shoulder moves to the very top edge on a skin (the AYANEO's 4:3 landscape has no skin; its overlay R reaches the top edge). Buttons enter each screen up to 10% from its edges passed (AYANEO 2026-11-22: NDS portrait skin, L stops about 10.6% in).
- [ ] Pad skin Off: untouched overlay buttons start at the skin positions (not checkable on the AYANEO: overlay and skin are different layouts). Adjust buttons edits the overlay, and Pad skin On again shows the skin unchanged (AYANEO 2026-11-22).
- [ ] Existing skin tweaks from before the upgrade still land where they were. (AYANEO 2026-11-22: no pre-upgrade tweaks there.)
- [ ] A button moved onto a screen's edge draws over the picture (editor and game) and its touches press it, not the game. Editor part passed (AYANEO 2026-11-22); in game needs a phone without a built-in controller.
- [ ] An owned overlay button stays put after a later skin edit. (AYANEO 2026-11-22: not reachable, overlay and skin are different layouts there.)
- [ ] A shoulder shrunk before the upgrade keeps its size. (AYANEO 2026-11-22: no pre-upgrade tweaks there.)

## Dolphin (GameCube / Wii) multiplayer: untested

GameCube and Wii pass with 2 controllers (Double Dash, NSMB Wii sideways). Still untested:

- [ ] 3 and 4 controllers at once (GameCube and Wii)
- [ ] Wii Classic Controller in multiplayer (e.g. Mario Kart Wii)
- [ ] Unplugging and replugging a controller keeps its player slot

## Review bug fixes (PR #102): device checks still owed

Already passed on the Nova: game smoke test (GBA, DS, PS), play time across menu and back, Device Sync wake and
Wi-Fi locks with the screen off, the 5-minute search limit, and settings reaching a resumed game.

Fold or Flip:
- [ ] Half-folded launch of a DS/3DS game with a pad skin starts on the hinge layout, with no flat-layout flash first
- [ ] Game on an external monitor or DeX: the second-screen panel never covers the game; the phone screen is offered
  instead (or dual screen stays off)

Two devices:
- [ ] A long Device Sync (ROMs on, one large file) still finishes: the 5-minute limit must not end a transfer while an
  rsync connection is open

Any device:
- [ ] Art: Replace (or a picked image) over a hand-placed `Name.PNG` on the SD card keeps the new image and shows it (Nova 2026-11-22: not run, home is internal and extras are read-only)
- [ ] Setup move / the home summary's "Move them into <home>/Roms" with a PS disc game whose `.cue` names its `.bin` in another letter case (or with
  accented names): the game moves with all its files (Nova 2026-11-22: not run, needs a home move with real disc games)
- [ ] RetroAchievements: Sync now from the menu while a game earns an unlock (dual-screen menu): the unlock is kept (RA not signed in on the dev app)

## Review performance fixes (PR #103): device checks still owed

Already passed on the Nova:
- PS2, GC, Wii, PSP, 3DS, DC and N64 run, hold still under the in-game menu, resume, survive Home and return, and
  quit. Home's state serialize and write run off the main thread (serialize 0.1–3 s, 17–109 MB written).
- GBA battery save: three menus and a Home leave an unchanged `.srm` alone; quit and relaunch load it.

Art fetch (real library, a few hundred games):
- [ ] Change art pick for a game the running job hasn't reached yet is kept (not run 2026-11-22: ScreenScraper quota)
- [ ] Untagged ROMs still get libretro-thumbnails art through its name list
- [ ] TheGamesDB: a multi-region or multi-disc set uses fewer requests (allowance before/after)
- [ ] ScreenScraper: Fetch missing art again on name-miss ROMs starts the hash lookup at once (no re-read of big ROMs)
- [ ] Fetch all art with Replace existing art: the written-art log only grows by appends; Reset artwork afterwards deletes exactly the app's files (not run 2026-11-22: Reset artwork would delete the user's art)

Game and video:
- [ ] Adjust buttons backdrop sharp. Thumbnails for Home, slot save and Quit look sharp, incl. a DS top-screen crop
  (Nova 2026-11-22) Thumbnails passed (AYANEO 2026-11-23); the backdrop needs a phone without a built-in controller.
- [ ] Two controllers on a 2-player core: P2 claims with a stick/trigger push, rumble per pad, replug keeps the slot (needs two pads)
- [ ] A System buttons binding works in game, also after changing it in Settings and returning to the running game

Menu:
- [ ] Menu pad shows/hides right on onboarding's Pad step, with a tool on top in two-screen skin mode, and after
  toggling Appearance → Pad skin (the Nova's built-in controller hides the menu pad; needs the Fold)
- [ ] Dual screen: moving through large merged consoles feels smooth (Nova 2026-11-22: no merged consoles there)

Kids Mode:
- [ ] Locked: keyboard and Settings while granting are exempt, the Locked game screen from Recents still quits. A
  disallowed app bounces within a second and allowed apps stay (Nova 2026-11-22)

## Repetition extraction (PR #105): device checks still owed

Already passed on the Nova: GBA, DS, PS smoke; GBA, PS2, GC, Wii, PSP, 3DS, DC and N64 run, hold under the menu,
survive Home and return, quit, and load their auto state again (slot 9).

- [ ] Held UP/DOWN on a long in-game message and on a setting's info popup keeps scrolling; touch scroll then a key (Nova 2026-11-22: no popup long enough found)
- [ ] In-game menu portrait (words beside the panel, Save/Load slot card, Options preview), narrow window stacks
  (Flip), split view on the Fold (dimmed parent pane, slot card, Options word list) (the Nova is landscape only)
- [ ] In-game Achievements page, Locked only toggle (RA not signed in on the dev app). Panel Cheats (empty and full) and
  Players tabs open (Nova 2026-11-22)
- [ ] Home's Continue and Pick cards, box-art shadows, Carousel collection and console counts look unchanged
- [ ] DS/3DS hat d-pad and L2/R2 as axes; GC and DC analog triggers; DS/3DS face and L2/R2 keys (axes need a pad with a
  hat and analog triggers; DS R2 and 3DS A keys reach the cores, Nova 2026-11-22)
- [ ] RetroAchievements hashing and unlock toasts (RA not signed in on the dev app); rumble. Multi-disc m3u, CUE+BIN and
  core messages passed (Nova 2026-11-22) Hashing passed on GB/GBA/NES/SNES/N64/NDS/PS1/PSP/GC (Nova 2026-11-23), PS2 and Wii from RVZ (Nova 2026-11-24); toasts need an unlock.
- [ ] A collection file or map.txt starting with a byte-order mark (from Windows): its first entry still works Collection passed (Nova 2026-11-23); map.txt needs a writable ROM folder.

## Security fixes (Device Sync confirm, update check): device checks still owed

Needs two devices. No protocol change: an older build on the other side still syncs.

- [ ] Phone ↔ phone (Wi-Fi and cable): each confirm page shows the other's IP, matching the IP that device shows for
  itself ("This device: …"). Wi-Fi passed (Nova ↔ AYANEO 2026-11-22); cable owed.
- [ ] Phone ↔ Brick (Wi-Fi and cable): the same, both ways
- [ ] An old build on the other side still syncs, in both roles (old server: A here, then it runs; old client: A here
  within its 30 s wait for READY)
- [ ] Unattended `sync_test.py` against the Brick presses A on the Brick's confirm screen and finishes

## AppState and GameActivity splits: device checks still owed

Already passed on the Nova (AppState split): the menu starts with Home and Continue; Continue launches the game; the
in-game menu opens; quitting returns to Home with play time refreshed; the Tools tab opens the Apps drawer.

Menu side (AppState split):
- [ ] First setup: the home page move with a stop mid-move, the copy offer after a home change (not run: moves the user's home)
- [ ] A large Fetch art run, then the menu and the open list refresh once after results stop (not run 2026-11-22: ScreenScraper quota)

Game side (GameActivity split):
- [ ] Launches: a zipped game from the menu (unpack toast), N64 with a hi-res pack. PS1 .cue/.chd, an arcade set with a
  missing BIOS (refusal and Back), Genesis passed (Nova 2026-11-22)
- [ ] Quit: the Save & Quit shortcut, Back before the first frame. Quit from the menu and a quit during a manual save
  passed (Nova 2026-11-22) Save & Quit shortcut passed (AYANEO 2026-11-23); Back before the first frame on PS2: see the teardown crash below.
- [ ] Wii: Motion recentre, a second pad claiming a port. Controller change, Motion toggle, HOME then Wii Menu, GameCube
  under the WII tag passed (Nova 2026-11-22)
- [ ] DS 3D resolution: Auto follows rotation/fold (needs the Fold), the software-fallback notice. A fixed pick passed
  (Nova 2026-11-22)
- [ ] Touch shortcuts: FF and rewind (hold and toggle), Adjust buttons backdrop. The strip slots passed (Nova 2026-11-22) FF and rewind hold/toggle passed (AYANEO 2026-11-23); the backdrop needs a phone without a built-in controller.
- [ ] A SAF cue/bin with a missing track still refuses with the error (the Nova has no SAF home or folder)
- [ ] 3DS then Wii in a row (and back): motion works in the second game; the 3DS tilt stops after quitting (needs phone motion)
- [ ] Mic button and touch shortcut buttons in a netplay game (speed shortcuts hidden in netplay). DS/3DS passed (Nova 2026-11-22)
- [ ] Sega pad Auto picks the cart's pad type; 3DS New Controls Auto reads the title (needs the drawn pad, hidden by the Nova's controller)

## Review round 2 fixes: device checks still owed

Already passed on the Nova (wireless adb): GBA, DS, PS smoke; GBA, PS2, GC, Wii, PSP, 3DS, DC, N64 run, hold under the
menu, survive Home and return, quit and reload slot 9 (8/8); save then quit at once ×3; Wii quit ×3 in 5–6 s.

- [ ] Art fetch: Pause, Cancel and Reset during a long run of "has art" skips stop at once (no hang); Resume continues;
  a single-game fetch started during a Pause waits for it (Nova 2026-11-22: Pause, Resume and Cancel stop at once on a
  missing-art run; Reset, single-game fetch and a "has art" stretch still owed)
- [ ] Remove an extra on a large library, then rename / hide a game during the re-index: no stall (the Nova's SD extra is its only ROM folder)
- [ ] Art fetch on a big library: the menu stays smooth on each art flush; art results during a rescan are kept (not run 2026-11-22: ScreenScraper quota)
- [ ] Update dialog: holding DOWN on long notes stops on release; the Scroll hint steps from the current target (no local update feed set up)
- [ ] Button layout editor toolbar: drag, unfold / rotate, drag again — it reaches the new edges (needs the Fold; the
  bottom edge holds on the Nova)
- [ ] RetroAchievements: Stop a sync while a game journals an unlock; the next sync works (no OverlappingFileLock)
- [ ] Save & Quit, then tap LOAD during the save: the next launch resumes at the quit point; Wii HOME during a quit is ignored (Nova 2026-11-22: resumes at the quit point, Wii HOME ignored; the quit dialog covers the panel there, so LOAD was not reachable)
- [ ] Launcher mode: change Pause when unfocused and return — it applies (both directions in multi-window). Home out of
  a game, kill the menu process, return: no stall; Second screen applies both ways (Nova 2026-11-22)
- [ ] Kids Mode: lock / unlock / lock quickly — at most one bounce a second (Nova 2026-11-22: no bounce storm, but only
  tried during a timed unlock; fast Locked ↔ unlocked cycling not reached)
  VFS launches passed (Nova 2026-11-22) Passed on PSP, GC, PS1 chd + cue/bin, PS2 (Okami, with core patch 0011) (Nova 2026-11-23); PS1 .pbp unsupported; WBFS without its update partition cannot match RA's Redump hashes (use ISO/RVZ).

## MSU-1 / MSU-MD (PR #147)

Device checks still owed:

- [ ] Play build (no All files access): an MSU-1 pack streams.
- [ ] Device Sync of an MSU pack in an extra folder: the folder arrives whole; a loose set's `.msu`/`.pcm` or cue and tracks travel with its ROM.

Follow-ups, after #147:

- [ ] More badges ([IndexedGame.badge] is general): from bracketed file-name tags ((Hack), [h], (Homebrew), (Translated), (Beta)/(Proto)) via a small table, a per-game choice in Options (wins over detected ones), or a DAT/ScreenScraper match. One badge per game today. Tag table + per-game Badge merged #196 (phone-checked on the AYANEO 2026-11-23); DAT/ScreenScraper source and arcade titles owed.

## PS2: ARMSX2 nightly-20261114 with LTO and upstream's profile (PR #152)

Measured 2026-11-15, Budokai Tenkaichi 3 battle state: AYANEO (Mali-G76) 22–28 fps on OpenGL ES with the old pin →
55–58 fps on Vulkan at native resolution, 38 fps at 2x; Nova (God of War II) unchanged. The official ARMSX2 app on the
AYANEO holds 60 fps at 1x and 2x on Vulkan with the malisx2 driver, 30–34 fps on OpenGL.

Device checks still owed (the areas core patches 0002–0006 touch):

- [ ] Memory cards: save in game, quit, relaunch → the save is there; a card from the old pin still loads. (Nova 2026-11-22: card unchanged across quits and a kill; an in-game save needs play to a save point.)
- [ ] Two players: second controller claims port 2 and plays.
- [ ] 16 KB pages (nx16k): a PS2 game starts and runs on the new pin.
- [ ] OpenGL ES at 2x on the AYANEO with the optimised build, measured cool (the first passes heat-throttled). (AYANEO 2026-11-22: never under 37.7 °C on USB power; the Budokai Tenkaichi 3 ROM is gone, only its states.)
- [ ] Custom GPU driver on a build with the slang merge (#144): one launch on the Nova with a Turnip package. (Nova 2026-11-22: no driver installed in the dev app.)

Left on the devices (2026-11-16):

- [ ] AYANEO: ARMSX2 Nightly installed (with the battle state and auto-load on); the battle state in slot 0 of Budokai Tenkaichi 3; malisx2 in the dev app's driver folder; Debug log on.
- [ ] Nova: Debug log on; `/sdcard/NXRedux/drivers` and the installed drivers from the GPU driver tests.

## Codebase review (comments, stale code, repeated code, simplifications, AppState split): device checks owed

Checked on the emulator only (GBA game starts, draws, quits; first setup up to Permissions). On real devices:

- [ ] Custom GPU driver: add a package from a zip on Android 9–12 (the AYANEO) and on the Nova. (AYANEO 2026-11-22: no driver zip at hand.) Nova passed with Turnip v26.0.0 R8 (2026-11-23).
- [ ] First setup end to end on a fresh install (text size → permissions → folders → Device Sync step → artwork and
      RetroAchievements sign-in → Done); kill the app mid-setup and reopen. (Needs a fresh install.)
- [ ] Netplay: host advertises over Wi-Fi and over the cable; a second device finds it. Wi-Fi passed (AYANEO found "Mario Kart 64 (192.168.1.13)", 2026-11-22); cable owed.

## Found 2026-11-23: open

- [ ] DS Large layout with a translucent inset (Pad skin off): a dark rectangle ~40 px bigger than the inset darkens the big screen around it, also at 100% opacity (AYANEO). Check whether intended.
- [ ] DS Layout row with a controller (built-in or plugged in, so no skin): it applies, not "Unavailable — The pad skin places the screens". Fixed; AYANEO check owed. Under a shown skin the DS rows stay hidden (Fold cover 2026-11-24).
- [ ] Update row: after "Update failed" (a redirect), A retries the same release; checking again needs an app restart (Nova, local feed). Check whether intended.

## Sega netplay: both players on the host's Controller Type
Automated: `tools/device-tests/netplay_pads.py` (Fold host, Nova client, then AYANEO host, Nova client, 2026-11-25: all pass).
- [x] PicoDrive: host on 6, 3, Auto (6-button and 3-button game) and 6 on a 3-button game: both phones play the host's pad, drift check clean.
- [x] Old peer either side (no `pad=`): both on the old 3-button pad, drift check clean.
- [x] The drift check catches a forced 6-vs-3 mismatch (the control case).
- [x] Genesis Plus GX: host on 6, 3 and Auto, both phones on the host's pad, drift check clean. It drifted before this branch (Auto too): its state leaves out the 68k refresh phase and VDP FIFO timing; the host now loads back the state it sends. 60 s without resyncs stayed in step.
- [x] Other consoles with the reload (`--smoke`, 45 s each): SNES on Snes9x (AYANEO ↔ Nova) and Supafaust, NES and FDS on FCEUmm, arcade on FBNeo (1942), Master System and Sega CD (Sonic CD) on PicoDrive and Genesis Plus GX (Fold ↔ Nova): no drift. No Game Gear, SG-1000 or 32X games on the phones.
- [x] PS1: PCSX ReARMed drifted (its dynarec compiles on a second thread, so interrupts landed at other instructions per device); sessions now force `pcsx_rearmed_drc_thread` off. PCSX ReARMed and SwanStation, SF EX Plus Alpha: no drift.
- [x] N64 (Mario Kart 64): in step. Every RAM difference is in the framebuffer area (0x310000-0x38FFFF, drawn by each GPU); the rest of RDRAM matched at every check for 48 s, so the RAM hash can't pass for N64.
- [ ] Phone ↔ Brick (no `pad=` yet): still starts, both on the old fixed pad, no desync.

## Dreamcast: HLE BIOS when dc_boot.bin is missing
- [ ] No `dc_boot.bin`: a disc boots on the HLE BIOS, the notice shows on the first launch only.
- [ ] With `dc_boot.bin` back: the real BIOS boots, no notice.
- [ ] Save for game / Save for console during an HLE launch, then add the BIOS and relaunch: the real BIOS boots.
- [ ] An auto state made on HLE resumes with the BIOS present (and the reverse).
- [ ] Netplay with no BIOS on one phone: the session starts, both sides on HLE, no drift.
- [ ] Docs: the BIOS page says the Dreamcast BIOS is recommended, not required.

## Wii U on Cemu

Device checks owed:
- [ ] Wind Waker HD name screen: the Keyboard box opens; touch typing + OK names the file; A sends the account's name; B then "Type" brings the box back; START still answers.
- [ ] Fold: Performance 2x on Mario Kart 8 draws right; fold/unfold and a second display mid-game keep the launch scale.
- [ ] Fold inner portrait and half-folded skin (round 13 layout); phone landscape with a skin starts Stacked.
- [ ] In-game menu after SWAP hides the lower-glass screen (DS, 3DS, Wii U).
- [ ] Core message toasts: no noisy core floods (a long shader compile, the FDS disk swap, Azahar wireless on a second display).
- [ ] AYANEO (Mali, 2 GB): the RAM warning shows once; whether a light game starts.
- [ ] Release build: the Cemu core from CI loads (no local jniLibs).

Follow-ups:
- [ ] Install a game's update and DLC separately (today the update must be inside the .wua).
- [ ] Load Cemu's shader/pipeline cache without blocking the first frame (the start watchdog waits 120 s).
- [ ] Refine the Wii U skin layouts per device, with DS/3DS SWAP moved into the touch strip (Save · SWAP · Load).
- [ ] Bindable SWAP shortcut; the swapped-screen backdrop under the menu.
- [ ] Docs: Wii U page (keys.txt in Bios/WIIU, .wua with updates inside, Vulkan-only, 4 GB RAM advice, Native by default) and release text.
- [ ] Deferred simplifications: WiiuLayout pip/centred shared with NdsLayout; the strip's even-share maths in one place; one layout-tip getter.

## Merged features: checks owed

Change art (#71):
- [ ] Upgrade scan of a library with old `grid/` and `hero/` art folders: they move once, art still shows.
- [ ] Game Switcher wheel art at 60%; the "Show this picture?" card's position; Change art on an Android game.
- [ ] Artwork page on a wide screen; Fold cover fit while a fetch runs.

DS / 3DS microphone (#36):
- [ ] Mic permission denied: the blow-noise fallback passes a real blow prompt.
- [ ] A 3DS game that uses the mic.
- [ ] Toggle mic from a controller.

Save on quit / A resumes (#158):
- [ ] Save on quit Off: start from a manual slot, kill the app → the game starts fresh.
- [ ] A resumes Off: A on a suspended game starts fresh.
- [ ] Wii HOME / Wii Menu exit with Save on quit Off.
- [ ] Game Switcher falls back to screenshot art when there is no state picture.

Import BIOS (#163):
- [ ] Import into a SAF-picked home (Play build path).
- [ ] Import real BIOS dumps (folder, zip, RetroArch system folder).

PC Engine CD BIOS (#181):
- [ ] CD BIOS = System Card 3 US with only `syscard3u.pce` in `Bios/PCE/`: a PCE CD game boots.

3DS local wireless and Player name (#129, #130):
- [ ] The core writes the personal Mii on its own (Fold), and it shows in Mario Kart 7.
- [ ] A full Mario Kart 7 race; a sheet-hosted room with an automatic joiner.
- [ ] Prompt box above the keyboard on the Fold, a portrait skin and the Android keyboard.

Home captions and second-screen panel (#128):
- [ ] Landscape Home with a d-pad: DOWN from an inline game can skip the tools row, UP from the first pin row returns to Continue. Check whether intended.
- [ ] The small old-style Panel pill still shows under a pad skin and when the touch screen is on the other display.
- [ ] Minimal Home strip, 3-line caption names, Flip / wide-phone landscape Home, shader stepping, DS/3DS menu flip across Reset and state loads.

RetroAchievements user agent (#226, handheld #352):
- [ ] Phone: sign in and unlock an achievement.
- [ ] Brick: ratools and in-game RA.
- [ ] When applying for hardcore compliance, disclose the old NextUI user agent.

A before LEFT / RIGHT (#160):
- [ ] Fold: value rows change only after A on Settings, the in-game menu, onboarding and Kids Mode.

N64 hi-res packs (#45):
- [ ] Play build (no All files access): a pack in `Textures/N64/` loads.
- [ ] A phone with more than 3 GB RAM: Auto VRAM limit picks the right size.

## Sega Saturn

- [x] Saturn menu art: the Saturn logo in the Consoles tab and the six-button Sega pad behind it, both orientations
- [x] With a BIOS in `Bios/SATURN/`: a disc boots and plays (Daytona USA, Nova, full speed).
- [x] An in-game save creates `<game>.bkr` in `Saves/SATURN/`, and it survives a killed app (Daytona's own file, process killed right after; Fold).
- [x] Save state and load state work (Daytona USA, Mega Man X4, Nova).
- [x] An Action Replay cheat takes effect (Daytona Infinite Time: the clock holds; Fold).
- [x] A multi-part database cheat (e.g. `+`-joined) works (the libretro file's codes, Master Code's pairs logged as not core writes; Fold).
- [ ] An achievement unlocks (if not: Saturn is show-only).
- [x] No BIOS: a disc boots on the HLE BIOS (Daytona USA, Nova, full speed, its save created). Notice once: the toast shows on the Fold (two lines, the folder cut off as on the Dreamcast).
- [x] Mega Man X4 on HLE (no BIOS): intro and in-game demo at full speed (Fold).
- [x] A multi-disc game by its `.m3u`: swap to disc 2, on the real BIOS (disc 2 boots on reset) and on HLE (loads at once); debug GAME_DISC hook, Fold.
- [x] An `.m3u` game's `.bkr` is named after the playlist (Fold).
- [x] A zipped cue+bin is refused with a clear message (only one entry unpacks; it booted with no disc). A zipped .chd/.iso still plays.
- [x] 3D Resolution 1x, then 2x, then 4x mid-game: each change shows at once (Nova).
- [x] 3D Resolution Auto across the Fold opening and closing (device-state override: relaid out, stays at the 2x cap).
- [x] Frame rate at the default cap on the Nova (full speed).
- [x] AYANEO (2 GB): both games full speed at Auto, 1x and 2x, 400+ MB free; its 1280×960 screen caps 4x at 2x.
- [x] A slang shader (CRT) at 2x looks right (crt-geom, full speed; Fold).
- [x] Saturn skin: the on-screen layout (L and R on the trigger slots) checked on the Fold.
- [ ] Saturn on a physical pad: C and Z are reachable.
- [ ] Device Sync carries a Saturn `.bkr` to another phone.
