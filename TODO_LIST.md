# TODO list

## Review round 3: device checks owed

- [ ] RA: revoke/sign out mid-game, unlock, sign in, sync → the unlock arrives; quit right after an unlock → no toast during the quit.
- [ ] Merge a split console in the home summary → play time follows the games. (Nova 2026-11-22: not run, every game is in the read-only SD extra.)
- [ ] Device Sync: stray `.tmp` in Saves and a stale `.part` in Roms are not sent; SAF home scrape/sync sidecars still write. Saves `.tmp`/`.part` not sent either way (Nova ↔ AYANEO 2026-11-22); Roms part needs ROMs on; SAF sidecars owed. A `.tmp` inside a state folder (`.userdata/shared/<TAG>-<core>/`) is sent: its rules have no `*.tmp`/`*.part` exclude.
- [ ] gpSP Wireless Adapter phone↔Brick (Union Room trade), phone host then Brick host; patched phone with an older Brick build still connects.
- [ ] Controls editor: d-pad mode on DC/GC/N64, drag the d-pad toward the face buttons, save → lands where the editor showed (in game and from Settings). Needs a phone without a built-in controller (the AYANEO's hides the touch pad in game). Title "Pad skin"/"Overlay", Reset console and Appearance reset passed (AYANEO 2026-11-22).
- [ ] Threaded cores across pause/resume (input lock): N64 netplay input still arrives (two devices); a game from a picked file still opens. Dolphin GC/Wii incl. Wii pointer, Azahar (GLES and Vulkan), ARMSX2 and PPSSPP passed (Nova 2026-11-22).

## Vulkan frames through a swapchain on an image reader (malisx2)

A custom driver that cannot import a shared buffer (malisx2 on Mali) runs through a swapchain on an image reader;
checked on the AYANEO (2026-11-16) and the Nova with the reader forced (2026-11-20).

Device checks still owed:

- [ ] Other Mali devices and malisx2 versions.
## Slang shader presets

Nova, 2026-11-14 (`tools/device-tests/slang_shaders.py`, 33 runs, no errors, screenshots looked at): SNES with crt-geom,
crt-guest-advanced, crt-royale, newpixie-crt, scalefx, motionblur/feedback and Mega Bezel POTATO at full speed; Game Boy
(gameboy, lcd-grid-v2); a rotated arcade game; N64 and GameCube (GL cores), PSP, PS2 and 3DS; DS with a feedback preset
on the top screen; all 50 bundled slang presets and the 21 replaced GLSL names on SNES. Mega Bezel STD and SMOOTH-ADV draw
correctly but at about 36 fps there. Still owed:

- [ ] Mega Bezel STD / SMOOTH-ADV speed: find whether the 36 fps on the Nova is the preset's own cost or ours.
- [ ] Heat over a long session with crt-guest-advanced.
- [ ] A pad skin with a slang preset (the Nova's built-in controller hides the skin). DS screens on the second display with a feedback preset passed (Nova 2026-11-22: response-time, top shaded with trails, touch screen plain).
- [ ] The Fold: crt-guest-advanced and a float-framebuffer preset draw; a preset that cannot build toasts and falls back. Mali passed (AYANEO 2026-11-22: crt-guest-advanced ~42 fps, float-framebuffer presets draw, a broken preset toasts and falls back).
- [ ] Shader picker on the Fold inner screen; Personal with a whole `shaders_slang` folder (about 2,600 presets) opens quickly. The Nova two-pane passed (2026-11-22: groups with descriptions, preview strip within its group, B back with the tick moved).
- [ ] Second screen: the Settings tab's Shader row opens the picker on the bottom screen while the picture on the top stays undimmed. (Nova 2026-11-22: the top picture pauses and dims, as from the in-game menu; B walks up the in-game menu, not back to the Settings tab.)
- [ ] Compare the swapped single-file GLSL presets with the handheld's (needs the Brick). A game saved with a removed GLSL preset opens with its slang version and looks the same (Nova 2026-11-22: crt-geom and scanline, pixel diff 0).

## Game info from ScreenScraper

Device checks still owed:

- [ ] "Fetch all" re-asks remembered misses; "Fetch missing" and highlights don't. (Not run 2026-11-22: ScreenScraper quota.)
- [ ] Signed out, signed in, quota exhausted (silent; resumes the next day); offline (retries after a few minutes). (Not run 2026-11-22: needs sign-out and network toggles on the user's account.)
- [ ] Device Sync phone↔phone/handheld carries `.media/info/` for home games; the receiver shows it without re-fetching.
- [ ] Short top screen (Fold cover / skin glass): the synopsis drops first, nothing clips.
- [ ] Browsing art: a game with all its art only gets its info; Artwork running meanwhile → no background fetch; toggle off → nothing. Resting on a game without art passed (Nova 2026-11-22: art on the top screen and tile, no notification).

## Dual screen: bottom acts, top informs

Device checks still owed:

- [ ] Nova: a game with achievements; no RetroAchievements chip on a played homebrew without a set (RA not signed in on the dev app). Fast scrolling, save states, an Android game and a never-played console passed (Nova 2026-11-22).
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
- [ ] Cheats: Remove the cheat database deletes only `Cheats/<TAG>/` files; saves and other folders untouched (Nova 2026-11-22: a cancelled install's clean-up kept Cheats/ root and Saves; a full install then Remove still owed)
- [ ] Setup move / the home summary's "Move them into <home>/Roms" with a PS disc game whose `.cue` names its `.bin` in another letter case (or with
  accented names): the game moves with all its files (Nova 2026-11-22: not run, needs a home move with real disc games)
- [ ] RetroAchievements: Sync now from the menu while a game earns an unlock (dual-screen menu): the unlock is kept (RA not signed in on the dev app)
- [ ] RetroAchievements page with the RA folder unreadable (All files access revoked): shows an error, no crash (RA not signed in on the dev app)

## Review performance fixes (PR #103): device checks still owed

Already passed on the Nova:
- PS2, GC, Wii, PSP, 3DS, DC and N64 run, hold still under the in-game menu, resume, survive Home and return, and
  quit. Home's state serialize and write run off the main thread (serialize 0.1–3 s, 17–109 MB written).
- GBA battery save: three menus and a Home leave an unchanged `.srm` alone; quit and relaunch load it.

Art fetch (real library, a few hundred games):
- [ ] A console with no `.media` folder gets its folders and art on the first fetch (Nova 2026-11-22: every console there has one)
- [ ] Change art pick for a game the running job hasn't reached yet is kept (not run 2026-11-22: ScreenScraper quota)
- [ ] Untagged ROMs still get libretro-thumbnails art through its name list
- [ ] TheGamesDB: a multi-region or multi-disc set uses fewer requests (allowance before/after)
- [ ] ScreenScraper: Fetch missing art again on name-miss ROMs starts the hash lookup at once (no re-read of big ROMs)
- [ ] Fetch all art with Replace existing art: the written-art log only grows by appends; Reset artwork afterwards deletes exactly the app's files (not run 2026-11-22: Reset artwork would delete the user's art)

Game and video:
- [ ] DS (melonDS) with a multi-pass shader preset: Screen Sharpness and screen swap; Large layout with a translucent
  inset needs another device (unavailable on the Nova's split). Top shaded, bottom plain passed (Nova 2026-11-22)
- [ ] Adjust buttons backdrop sharp. Thumbnails for Home, slot save and Quit look sharp, incl. a DS top-screen crop
  (Nova 2026-11-22)
- [ ] Home then force-stop the game process: relaunch resumes from slot 9 and the switcher thumbnail matches (Nova
  2026-11-22: God of War II resumes slot 9; Doom with the pointer on manual slot 1 resumed slot 1 with its picture,
  check whether a Home should move the pointer)
- [ ] Two controllers on a 2-player core: P2 claims with a stick/trigger push, rumble per pad, replug keeps the slot (needs two pads)
- [ ] A System buttons binding works in game, also after changing it in Settings and returning to the running game

Menu:
- [ ] Menu pad shows/hides right on onboarding's Pad step, with a tool on top in two-screen skin mode, and after
  toggling Appearance → Pad skin (the Nova's built-in controller hides the menu pad; needs the Fold)
- [ ] Game Tracker shows box art right after a fetch (Nova 2026-11-22: no fetched game had play sessions)
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
  core messages passed (Nova 2026-11-22)
- [ ] A collection file or map.txt starting with a byte-order mark (from Windows): its first entry still works

## Security fixes (Device Sync confirm, update check): device checks still owed

Needs two devices. No protocol change: an older build on the other side still syncs.

- [ ] Phone ↔ phone (Wi-Fi and cable): each confirm page shows the other's IP, matching the IP that device shows for
  itself ("This device: …"). Wi-Fi passed (Nova ↔ AYANEO 2026-11-22); cable owed.
- [ ] Phone ↔ Brick (Wi-Fi and cable): the same, both ways
- [ ] An old build on the other side still syncs, in both roles (old server: A here, then it runs; old client: A here
  within its 30 s wait for READY)
- [ ] Unattended `sync_test.py` against the Brick presses A on the Brick's confirm screen and finishes
- [ ] In-app update with the dev build's local feed (`-PnxUpdateFeed=http://localhost:<port>/latest.json` + adb
  reverse): offer, download and install; no `digest` → "Update not verified: GitHub sent no checksum"; a LAN or other
  host → "Update not verified: not a GitHub download"; `"draft": true` or a `-rc` tag → up to date; a 302 to another
  host → "Update failed"

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
  passed (Nova 2026-11-22)
- [ ] Wii: Motion recentre, a second pad claiming a port. Controller change, Motion toggle, HOME then Wii Menu, GameCube
  under the WII tag passed (Nova 2026-11-22)
- [ ] DS 3D resolution: Auto follows rotation/fold (needs the Fold), the software-fallback notice. A fixed pick passed
  (Nova 2026-11-22)
- [ ] Touch shortcuts: FF and rewind (hold and toggle), Adjust buttons backdrop. The strip slots passed (Nova 2026-11-22)
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
- [ ] RA hashing (RA not signed in on the dev app). Save / load states on PS2 and Wii (large), PPSSPP; SRAM / RTC; multi-file
  VFS launches passed (Nova 2026-11-22)

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
- [ ] Cheats & Patches: a cheat from Cheats/PS2 and a patch from Cheats/PS2/Patches apply. (No PS2 cheats or patches in the Nova's dev home.)
- [ ] 16 KB pages (nx16k): a PS2 game starts and runs on the new pin.
- [ ] OpenGL ES at 2x on the AYANEO with the optimised build, measured cool (the first passes heat-throttled). (AYANEO 2026-11-22: never under 37.7 °C on USB power; the Budokai Tenkaichi 3 ROM is gone, only its states.)
- [ ] Custom GPU driver on a build with the slang merge (#144): one launch on the Nova with a Turnip package. (Nova 2026-11-22: no driver installed in the dev app.)

Left on the devices (2026-11-16):

- [ ] AYANEO: ARMSX2 Nightly installed (with the battle state and auto-load on); `/sdcard/NXReduxDev-perf-backup`; the battle state in slot 0 of Budokai Tenkaichi 3; malisx2 in the dev app's driver folder; Debug log on.
- [ ] Nova: the temporary build with the PS2 4x override is still installed (install a current build); `/sdcard/NXRedux-gpu-test-backup-2`; Debug log on; `/sdcard/NXRedux/drivers` and 10 installed drivers from the GPU driver tests.

## Codebase review (comments, stale code, repeated code, simplifications, AppState split): device checks owed

Checked on the emulator only (GBA game starts, draws, quits; first setup up to Permissions). On real devices:

- [ ] Custom GPU driver: add a package from a zip on Android 9–12 (the AYANEO) and on the Nova. (AYANEO 2026-11-22: no driver zip at hand.)
- [ ] First setup end to end on a fresh install (text size → permissions → folders → Device Sync step → artwork and
      RetroAchievements sign-in → Done); kill the app mid-setup and reopen. (Needs a fresh install.)
- [ ] Netplay: host advertises over Wi-Fi and over the cable; a second device finds it. Wi-Fi passed (AYANEO found "Mario Kart 64 (192.168.1.13)", 2026-11-22); cable owed.
- [ ] Settings survive the update: second screen, pause when unfocused, screen care, tool apps, tips hidden.

## Found 2026-11-22: open

- [ ] Slow slang presets on Mali-G76 (correct picture): aa-shader-4.0 ~5 fps, authentic_gbc ~6, crt-lottes ~7, crt-mattias ~21.
