# TODO list

## Review round 3: device checks owed

- [x] Kids: unlock sheet on the tiles, screen off then on → tiles, not the sheet; same from Open mode's lock icon → menu; sheet left 60 s closes; Home over it, back → gone (Nova 2026-11-22: tiles after screen off/on, 63 s close, Home; Open mode too).
- [x] Kids: Open mode, MENU on a game shows only play items (no Hide, Rename, art, Game Settings, Emulator, collections); Parent mode shows them all; a Rename prompt open when Parent ends closes (Nova 2026-11-22: 1942 and Home row; Rename prompt gone after screen off/on).
- [x] Kids, dual screen (Nova + second screen): blocked app opened from a top-screen app is covered and stopped; reopened from Recents → sent back (Nova 2026-11-09).
- [ ] RA: revoke/sign out mid-game, unlock, sign in, sync → the unlock arrives; quit right after an unlock → no toast during the quit.
- [ ] Merge a split console in the home summary → play time follows the games. (Nova 2026-11-22: not run, every game is in the read-only SD extra.)
- [ ] Device Sync: stray `.tmp` in Saves and a stale `.part` in Roms are not sent; SAF home scrape/sync sidecars still write. Saves `.tmp`/`.part` not sent either way (Nova ↔ AYANEO 2026-11-22); Roms part needs ROMs on; SAF sidecars owed. A `.tmp` inside a state folder (`.userdata/shared/<TAG>-<core>/`) is sent: its rules have no `*.tmp`/`*.part` exclude.
- [ ] gpSP Wireless Adapter phone↔Brick (Union Room trade), phone host then Brick host; patched phone with an older Brick build still connects.
- [ ] Controls editor: d-pad mode on DC/GC/N64, drag the d-pad toward the face buttons, save → lands where the editor showed (in game and from Settings). Needs a phone without a built-in controller (the AYANEO's hides the touch pad in game). Title "Pad skin"/"Overlay", Reset console and Appearance reset passed (AYANEO 2026-11-22).
- [x] Game screen: Second screen toggle in launcher mode with the menu process killed doesn't stall; Vulkan→GLES fallback relaunches cleanly (Nova 2026-11-22: Doom both ways; Patapon 2 relaunched on GLES at 60 fps).
- [x] Home during a slow PS2/Wii quit, tap the same game → starts fresh; Home during a big PS2 state resume → no ANR; then clear it from the launcher icon → SRAM survived (Nova 2026-11-22: God of War II, new process, no ANR, memory card unchanged; Wii not run).
- [ ] Threaded cores across pause/resume (input lock): N64 netplay input still arrives (two devices); a game from a picked file still opens. Dolphin GC/Wii incl. Wii pointer, Azahar (GLES and Vulkan), ARMSX2 and PPSSPP passed (Nova 2026-11-22).

## Vulkan frames through a swapchain on an image reader (malisx2)

A custom driver that cannot import a shared buffer (malisx2 on Mali) now runs through a swapchain on an image reader;
the OpenGL ES presenter is unchanged. AYANEO Pocket Air Mini (Mali-G76), Budokai Tenkaichi 3 battle state, 2026-11-16:
malisx2 59/60/60 fps at 1x and 55–57 fps at 2x, against 54–57 and 38–39 on the stock driver. Checked there: picture at
1x and 2x, a slang preset (crt-geom, 43 fps at 2x), video-mode size change, quit ×5, screen off/on, Home and return,
PSP (Patapon 2) on the same driver, and the forced fallbacks (`setprop debug.nx.reader_fail_after <frame>`: failed
start and mid-game both relaunch on the system driver).

Checked on the Nova (Adreno 740, Turnip 27.0.0) on 2026-11-20, the reader hand-off forced on a driver that passes the
import (`setprop debug.nx.reader_force 1`): PS2 (Budokai Tenkaichi 3, God of War II, Ace Combat 5 resumed from their
states, 60 fps), PSP, 3DS, GameCube and N64 run with a correct picture; fast forward at 4x presents about 220 frames a
second with none failed (PSP, 3DS, GameCube with Frame Skip on Auto, N64); Home and return twice, with a memory trim and
screen off/on; a simulated second display (3DS: top screen there, touch screen on the main one); a reader buffer the
OpenGL ES side cannot import (`setprop debug.nx.reader_gl_fail 1`) and a mid-game failure (2 of 2) both relaunch on
the system driver, and the PS2 core quits cleanly after.

Device checks still owed:

- [x] Nova (Adreno): a Turnip package still passes its pre-check as "import" and measures as before (60 fps).
- [x] Fast forward on malisx2 itself (passes through the reader on Turnip) (AYANEO 2026-11-22: GameCube 405 frames/10 s presented, PSP and N64 too; no reader errors).
- [x] EGL context loss, forced with `setprop debug.nx.keepcontext 0` (the context given up at every pause): PS2 on the
  AYANEO, Home and return twice, the picture comes back on malisx2 through the reader (57 fps after), on the stock
  Vulkan driver and on OpenGL ES.
- [ ] Other Mali devices and malisx2 versions.
- [x] PS2 fast forward did not speed the game up (the core's frame count stayed at 60 a second, any hand-off, OpenGL ES
  too). Cause: the core's VM runs on its own thread with its own limiter and never asked whether the frontend
  fast-forwards. Core patch 0009 lifts the limiter while it does and makes each call wait for the VM's next frame.
  After it on the Nova at Max FF 4x: Ace Combat 5 226 fps on Vulkan and 229 on OpenGL ES; God of War II 118 at 2x;
  Budokai Tenkaichi 3 116 (the phone's limit in a battle).
- [x] A real second screen on the Nova with the reader hand-off forced (3DS): top screen there, touch screen on the main
  one, fast forward, Home and return.
- [x] Graphics API Auto on the AYANEO (Mali-G76): PS2 starts on Vulkan (malisx2 through the reader, 56–57 fps), Wii on
  Vulkan, N64 stays on OpenGL ES. In Settings the PS2 page says "Auto is Vulkan" and shows the GPU driver row on Auto.
- [x] PS2 fast forward on the AYANEO (malisx2, Max FF 4x): 118 fps at native resolution; at 2x the phone is already at
  its limit (57 to 63 fps).
- [x] Mario Kart 7 on the Nova's real second screen (same race from a save state, both screens captured): the reader
  forced on Turnip, the import hand-off on Turnip, Vulkan on the system driver and OpenGL ES all draw the top screen
  on the second display and the map on the main one. The darkened kart and driver showed up again on the system
  driver with no reader involved (smoke beside it, right after a hit) and never on the reader, so it is the game's own
  look, not the hand-off. The black top screen seen on a simulated display was the simulation.
- Which consoles follow the GPU on Auto, from the AYANEO on 2026-11-20 (frames in 5 s, stutters counted by NXHITCH):
  Wii (New Super Mario Bros. Wii) 240–279 on OpenGL ES against 291–302 on Vulkan; GameCube (Double Dash race) 285–300
  against 301; so Dolphin follows it, with PS2. N64 (Mario Kart 64 from boot) holds 300 on OpenGL ES with 18 stutters
  against 274–294 with about 950 on Vulkan (ParaLLEl), malisx2 no better. PSP (Tekken 6 fight), PS1 (SwanStation) and
  Dreamcast (Soulcalibur fight) hold full speed on both with fewer stutters on OpenGL ES. 3DS (Mario Kart 7): the
  low-memory killer ends the game seconds into a Vulkan launch (1.2 GB held on a 1.9 GB device).
- [x] N64, PSP, GameCube and Wii run on malisx2 through the reader on the AYANEO with a correct picture (AYANEO 2026-11-22: 595–600 frames/10 s each, clean quits).
- [ ] 3DS on Vulkan on the AYANEO: Mario Kart 7 from boot reached 60 fps, then the core aborted at the first 3D scene
  (`vk::DeviceLostError` in `waitForFences`), stock driver and malisx2 alike, 23 MB free at the abort. Cause: Azahar's
  fixed 512 MiB Vulkan upload buffer is resident GPU memory on Mali. Fixed in #194 (sized
  RAM/32, 64–512 MiB): Pokemon Ultra Sun on the AYANEO, GPU mapping 788 → about 280 MB, swap never empty, no lmkd kills
  during play, 60 fps (2026-11-22). Saving a state also got the game killed at any resolution (the whole state uncompressed in memory, then copied): #194
  streams states through ZSTD both ways; Ultra Sun saves, quits and resumes on the AYANEO. Still owed: Mario Kart 7 again;
  a 3DS Max Scale for modest GPUs (Auto starts at 3x on Mali).
- [x] PS2 quit on the AYANEO aborted when the core library was closed (`pthread_mutex_lock called on a destroyed
  mutex`): 6 of 13 quits on malisx2, also on the stock driver and on main. Cause: the settings interfaces kept for the
  process (core patch 0004) saved themselves at unload, after the save lock was destroyed. Core patch 0007 saves them in
  `retro_deinit` and keeps the lock. After it: 0 of 12 quits (8 malisx2, 4 stock).
- [x] PS2 on the driver fallback relaunch (core reloaded in the same process) after core patch 0007: 3 of 3 forced
  fallbacks relaunched and quit cleanly on the AYANEO, 3 of 3 on the Nova.
- [x] PS2 resume booted from the disc although the state load reported success (5 of 13 launches on the AYANEO, either driver). Cause: the state loads during the BIOS boot, and the core swapped TLB entries one index at a time, so unmapping a stale entry could unmap pages a new entry had just mapped (a burst of `TLB Miss` in the log). Core patch 0008 unmaps all stale entries first. After it: 12 of 12 launches resumed.

## Slang shader presets

Nova, 2026-11-14 (`tools/device-tests/slang_shaders.py`, 33 runs, no errors, screenshots looked at): SNES with crt-geom,
crt-guest-advanced, crt-royale, newpixie-crt, scalefx, motionblur/feedback and Mega Bezel POTATO at full speed; Game Boy
(gameboy, lcd-grid-v2); a rotated arcade game; N64 and GameCube (GL cores), PSP, PS2 and 3DS; DS with a feedback preset
on the top screen; all 50 bundled slang presets and the 21 replaced GLSL names on SNES. Mega Bezel STD and SMOOTH-ADV draw
correctly but at about 36 fps there. Still owed:

- [ ] Mega Bezel STD / SMOOTH-ADV speed: find whether the 36 fps on the Nova is the preset's own cost or ours.
- [ ] Heat over a long session with crt-guest-advanced.
- [x] A GL core: the core keeps drawing after the in-game menu opens and closes on a slang preset (Nova 2026-11-22: Super Mario 64 with crt-geom, 3 menu round trips).
- [ ] A pad skin with a slang preset (the Nova's built-in controller hides the skin). DS screens on the second display with a feedback preset passed (Nova 2026-11-22: response-time, top shaded with trails, touch screen plain).
- [ ] The Fold: crt-guest-advanced and a float-framebuffer preset draw; a preset that cannot build toasts and falls back. Mali passed (AYANEO 2026-11-22: crt-guest-advanced ~42 fps, float-framebuffer presets draw, a broken preset toasts and falls back).
- [x] Shader Parameters on a slang preset from the in-game menu: live change, Reset Parameters, Save for game, relaunch (Nova 2026-11-22: Super Mario 64, Color Temperature 80 kept after relaunch).
- [ ] Shader picker on the Fold inner screen; Personal with a whole `shaders_slang` folder (about 2,600 presets) opens quickly. The Nova two-pane passed (2026-11-22: groups with descriptions, preview strip within its group, B back with the tick moved).
- [ ] Second screen: the Settings tab's Shader row opens the picker on the bottom screen while the picture on the top stays undimmed. (Nova 2026-11-22: the top picture pauses and dims, as from the in-game menu; B walks up the in-game menu, not back to the Settings tab.)
- [ ] Compare the swapped single-file GLSL presets with the handheld's (needs the Brick). A game saved with a removed GLSL preset opens with its slang version and looks the same (Nova 2026-11-22: crt-geom and scanline, pixel diff 0).
- [x] Every bundled preset once on a GL core and on a Mali GPU (only SNES on the Nova so far) (AYANEO 2026-11-22: 71 presets on snes9x and Mupen64Plus, 142 runs, 0 errors).

## Game info from ScreenScraper

Device checks still owed:

- [x] Nova: highlighting a game without info fills genre · year · publisher and a 5-line synopsis after a moment (Astro Boy, 2026-11-10).
- [x] Nova: scroll fast through a list — no request burst (Debug log); only the game you stop on is looked up (Nova 2026-11-22: 25 fast moves, only the game rested on got files).
- [x] Artwork "Fetch missing" on a console with missing art stores info from the same lookup; games with all their art are not looked up by the run (Nova 2026-11-22: Game Boy run wrote info beside the art).
- [ ] "Fetch all" re-asks remembered misses; "Fetch missing" and highlights don't. (Not run 2026-11-22: ScreenScraper quota.)
- [ ] Signed out, signed in, quota exhausted (silent; resumes the next day); offline (retries after a few minutes). (Not run 2026-11-22: needs sign-out and network toggles on the user's account.)
- [ ] Device Sync phone↔phone/handheld carries `.media/info/` for home games; the receiver shows it without re-fetching.
- [ ] Short top screen (Fold cover / skin glass): the synopsis drops first, nothing clips.
- [ ] Browsing art: a game with all its art only gets its info; Artwork running meanwhile → no background fetch; toggle off → nothing. Resting on a game without art passed (Nova 2026-11-22: art on the top screen and tile, no notification).

## Dual screen: bottom acts, top informs

Device checks still owed:

- [x] Nova: every menu screen — d-pad and buttons never reach the top; tools, Settings, Apps drawer and game lists open on the bottom; the top shows details / stats / backdrop as the spec table says (Nova 2026-11-22: focus stayed on display 0 throughout).
- [x] Nova: text prompts (search, rename, sign-in) show box and keyboard together on the bottom; the top keeps details/backdrop (Nova 2026-11-22; the page's hint row shows behind the sign-in box).
- [x] Nova: a tap on a Most played / New cover launches; quitting lands on that console/collection row; a drag, a long press, or a tap while a context menu / prompt is open doesn't launch (Nova 2026-11-22: Mario Kart 64 on the N64 row).
- [ ] Nova: a game with achievements; no RetroAchievements chip on a played homebrew without a set (RA not signed in on the dev app). Fast scrolling, save states, an Android game and a never-played console passed (Nova 2026-11-22).
- [ ] Fold cover display and the menu skin's two glasses (small top): details and stats fit without clipping; the stats shelf hides cleanly when too short. (AYANEO 2026-11-22: not offered, the built-in controller hides the menu skin; needs the Fold.)
- [x] Menu on the second display (Bottom screen set to the other display): prompts and keyboard follow the bottom (Nova 2026-11-22: Search box and keyboard on the DP screen).
- [ ] Thor / Duo (both screens touch): top-screen taps act once, never move focus.

## Controls editor: skin and overlay layouts

Device checks still owed:

- [ ] Landscape: R shoulder moves to the very top edge on a skin (the AYANEO's 4:3 landscape has no skin; its overlay R reaches the top edge). Buttons enter each screen up to 10% from its edges passed (AYANEO 2026-11-22: NDS portrait skin, L stops about 10.6% in).
- [x] Resize a button very large and very small (floor/ceiling hold); two buttons can overlap visually but their touch areas never stack (AYANEO 2026-11-22: START 79–297%, never stacked).
- [ ] Pad skin Off: untouched overlay buttons start at the skin positions (not checkable on the AYANEO: overlay and skin are different layouts). Adjust buttons edits the overlay, and Pad skin On again shows the skin unchanged (AYANEO 2026-11-22).
- [ ] Existing skin tweaks from before the upgrade still land where they were. (AYANEO 2026-11-22: no pre-upgrade tweaks there.)
- [x] Frame layouts' touch-shortcut strips may now enter the bottom 10% of the screen above them (AYANEO 2026-11-22: DC portrait).
- [x] The red area in Adjust buttons covers the whole screen frame above the touch-shortcut row; shortcut labels are centred in their pills (AYANEO 2026-11-22: DC and NDS portrait).
- [ ] A button moved onto a screen's edge draws over the picture (editor and game) and its touches press it, not the game. Editor part passed (AYANEO 2026-11-22); in game needs a phone without a built-in controller.
- [x] Editor drag stays smooth on a slow device (AYANEO) with long drags (AYANEO 2026-11-22: 4 s drag, 3% janky, p99 20 ms).
- [ ] An owned overlay button stays put after a later skin edit. (AYANEO 2026-11-22: not reachable, overlay and skin are different layouts there.)
- [ ] A shoulder shrunk before the upgrade keeps its size. (AYANEO 2026-11-22: no pre-upgrade tweaks there.)

## Dolphin (GameCube / Wii) multiplayer: untested

GameCube and Wii pass with 2 controllers (Double Dash, NSMB Wii sideways). Still untested:

- [ ] 3 and 4 controllers at once (GameCube and Wii)
- [ ] Wii Classic Controller in multiplayer (e.g. Mario Kart Wii)
- [ ] Unplugging and replugging a controller keeps its player slot
- [x] Wii players 2–4: no pointer (touch drives player 1 only) and no motion (phone sensors feed player 1 only). Left as is (decided 2026-11-22).

## Review bug fixes (PR #102): device checks still owed

Already passed on the Nova: game smoke test (GBA, DS, PS), play time across menu and back, Device Sync wake and
Wi-Fi locks with the screen off, the 5-minute search limit, and settings reaching a resumed game.

Fold or Flip:
- [ ] Half-folded launch of a DS/3DS game with a pad skin starts on the hinge layout, with no flat-layout flash first
- [x] With the dual-screen menu open while a game runs, play time keeps counting: Game Tracker shows the full session
  after quitting (Nova 2026-11-22: 4 min with the menu on the second display, session 265 s)
- [ ] Game on an external monitor or DeX: the second-screen panel never covers the game; the phone screen is offered
  instead (or dual screen stays off)
- [x] Settings → Second screen / Bottom screen changed in the menu while a game is suspended (launcher mode Home)
  applies when the game comes back (Nova 2026-11-22: Doom from Recents, not launcher mode)

Two devices:
- [x] Device Sync over Wi-Fi with the other device vanishing mid-run (Wi-Fi off, no FAIL): the server gives up after
  about 5 minutes with "The other device stopped answering (5 min)". FAIL on main (client app force-stopped mid-run: the
  server still waited after 6 min): its own 127.0.0.1 listening check counted as an open peer connection;
  fixed in #201 (AYANEO server gave up 5 min 0.7 s after the client went, 2026-11-22).
- [ ] A long Device Sync (ROMs on, one large file) still finishes: the 5-minute limit must not end a transfer while an
  rsync connection is open
- [x] Device Sync with the screen turned off mid-run finishes without stalling (Nova asleep for the last ~45 s of a 1.6 GB sync, 2026-11-22)
- [x] Wi-Fi netplay connects and plays; `dumpsys wifi` shows the `nxredux:netplay` lock during the session (Mario Kart 64, Nova host, AYANEO joined, both held the lock, 2026-11-22)

Any device:
- [ ] Art: Replace (or a picked image) over a hand-placed `Name.PNG` on the SD card keeps the new image and shows it (Nova 2026-11-22: not run, home is internal and extras are read-only)
- [ ] Cheats: Remove the cheat database deletes only `Cheats/<TAG>/` files; saves and other folders untouched (Nova 2026-11-22: a cancelled install's clean-up kept Cheats/ root and Saves; a full install then Remove still owed)
- [ ] Setup move / the home summary's "Move them into <home>/Roms" with a PS disc game whose `.cue` names its `.bin` in another letter case (or with
  accented names): the game moves with all its files (Nova 2026-11-22: not run, needs a home move with real disc games)
- [ ] RetroAchievements: Sync now from the menu while a game earns an unlock (dual-screen menu): the unlock is kept (RA not signed in on the dev app)
- [ ] RetroAchievements page with the RA folder unreadable (All files access revoked): shows an error, no crash (RA not signed in on the dev app)
- [x] Settings → touch button help text reads right (Adjust buttons in the in-game Controls) (Nova 2026-11-22)

## Review performance fixes (PR #103): device checks still owed

Already passed on the Nova:
- PS2, GC, Wii, PSP, 3DS, DC and N64 run, hold still under the in-game menu, resume, survive Home and return, and
  quit. Home's state serialize and write run off the main thread (serialize 0.1–3 s, 17–109 MB written).
- GBA battery save: three menus and a Home leave an unchanged `.srm` alone; quit and relaunch load it.

Art fetch (real library, a few hundred games):
- [x] Fetch missing advances smoothly; the game being fetched shows Searching / Downloading; Pause, Resume and Cancel
  mid-job keep finished games finished (Nova 2026-11-22: stopped at 6/213, finished games kept their art)
- [ ] A console with no `.media` folder gets its folders and art on the first fetch (Nova 2026-11-22: every console there has one)
- [ ] Change art pick for a game the running job hasn't reached yet is kept (not run 2026-11-22: ScreenScraper quota)
- [ ] Untagged ROMs still get libretro-thumbnails art through its name list
- [ ] TheGamesDB: a multi-region or multi-disc set uses fewer requests (allowance before/after)
- [ ] ScreenScraper: Fetch missing art again on name-miss ROMs starts the hash lookup at once (no re-read of big ROMs)
- [ ] Fetch all art with Replace existing art: the written-art log only grows by appends; Reset artwork afterwards deletes exactly the app's files (not run 2026-11-22: Reset artwork would delete the user's art)

Game and video:
- [ ] DS (melonDS) with a multi-pass shader preset: Screen Sharpness and screen swap; Large layout with a translucent
  inset needs another device (unavailable on the Nova's split). Top shaded, bottom plain passed (Nova 2026-11-22)
- [x] 3DS with a preset under video views: menu pause keeps the picture (no black), resume (Nova 2026-11-22: Mario Kart 7, CRT Geom)
- [x] GC/Wii and Flycast: change the shader preset mid-game; no black frames or broken textures (Nova 2026-11-22: Sunshine, SMG2, Soulcalibur)
- [x] PSP on Vulkan and on GLES: play, pause/resume, fast forward (Nova 2026-11-22: GTA Vice City Stories, FF on both)
- [x] Save/load state on PSP, GC and PS2; a PPSSPP state is the same size as before and loads (Nova 2026-11-22: PPSSPP 40 MB as before)
- [x] GameCube: a state saved in session crashed the game on a cold launch (Sunshine, SIGTRAP in the Dolphin CPU thread).
  Cause: the launch's 3D resolution ignored the second display, so Auto changed right after a resume's load and Dolphin
  on OpenGL ES corrupted memory with a 1x state. Fixed in #200 (Nova 2026-11-22, 3/3)
- [ ] Adjust buttons backdrop sharp. Thumbnails for Home, slot save and Quit look sharp, incl. a DS top-screen crop
  (Nova 2026-11-22)
- [ ] Home then force-stop the game process: relaunch resumes from slot 9 and the switcher thumbnail matches (Nova
  2026-11-22: God of War II resumes slot 9; Doom with the pointer on manual slot 1 resumed slot 1 with its picture,
  check whether a Home should move the pointer)
- [ ] Two controllers on a 2-player core: P2 claims with a stick/trigger push, rumble per pad, replug keeps the slot (needs two pads)
- [ ] A System buttons binding works in game, also after changing it in Settings and returning to the running game
- [x] DS stylus mode: cursor moves smoothly with d-pad and stick, stops at edges, moves again after the menu closes (Nova 2026-11-22: d-pad; stick not tried)

Menu:
- [x] Return from a game: Continue's picture updates and the highlight stays put (also returning to the Game Switcher) (Nova 2026-11-22)
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

- [x] Pause when unfocused: with another app focused the game holds and the screen sleeps on Android's timeout; focus
  back plays on and keeps it awake (stays on throughout under live netplay) (Nova 2026-11-22: frames stop, keep-awake
  dropped and back; netplay not run)
- [ ] Held UP/DOWN on a long in-game message and on a setting's info popup keeps scrolling; touch scroll then a key (Nova 2026-11-22: no popup long enough found)
- [ ] In-game menu portrait (words beside the panel, Save/Load slot card, Options preview), narrow window stacks
  (Flip), split view on the Fold (dimmed parent pane, slot card, Options word list) (the Nova is landscape only)
- [ ] In-game Achievements page, Locked only toggle (RA not signed in on the dev app). Panel Cheats (empty and full) and
  Players tabs open (Nova 2026-11-22)
- [ ] Home's Continue and Pick cards, box-art shadows, Carousel collection and console counts look unchanged
- [ ] DS/3DS hat d-pad and L2/R2 as axes; GC and DC analog triggers; DS/3DS face and L2/R2 keys (axes need a pad with a
  hat and analog triggers; DS R2 and 3DS A keys reach the cores, Nova 2026-11-22)
- [x] Save/load SRAM and RTC (a GB/GBA game with a clock, and a core without one: no error log) (Nova 2026-11-22: Pokemon Gold .rtc, Kirby's Dream Land 2, Emerald)
- [ ] RetroAchievements hashing and unlock toasts (RA not signed in on the dev app); rumble. Multi-disc m3u, CUE+BIN and
  core messages passed (Nova 2026-11-22)
- [x] Core options with categories (localized labels); controller type lists (Nova 2026-11-22: melonDS DS, Genesis, Wii)
- [ ] A collection file or map.txt starting with a byte-order mark (from Windows): its first entry still works

## Security fixes (Device Sync confirm, update check): device checks still owed

Needs two devices. No protocol change: an older build on the other side still syncs.

- [ ] Phone ↔ phone (Wi-Fi and cable): each confirm page shows the other's IP, matching the IP that device shows for
  itself ("This device: …"). Wi-Fi passed (Nova ↔ AYANEO 2026-11-22); cable owed.
- [ ] Phone ↔ Brick (Wi-Fi and cable): the same, both ways
- [x] A on both, in either order, syncs; B on either side cancels both (the other says "The other device cancelled the
  sync." while its confirm page shows, "The other device's sync failed…" once it pressed A) (Nova ↔ AYANEO 2026-11-22)
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
- [x] Game Switcher: resume from it and come back to it; it reopens after the menu process restarts; Y Remove updates
  Continue (Nova 2026-11-22)
- [x] Kids Mode: lock/unlock with the PIN, setup from onboarding incl. the home-role step, the day editor's From/To
  (Nova 2026-11-22: setup from Settings; from onboarding not run)
- [x] Settings: chooser and info popup (pad and Back), row edit in two-pane, Reset on Appearance, Main menu, Shortcuts,
  System buttons (Nova 2026-11-22)
- [ ] First setup: the home page move with a stop mid-move, the copy offer after a home change (not run: moves the user's home)
- [ ] A large Fetch art run, then the menu and the open list refresh once after results stop (not run 2026-11-22: ScreenScraper quota)

Game side (GameActivity split):
- [ ] Launches: a zipped game from the menu (unpack toast), N64 with a hi-res pack. PS1 .cue/.chd, an arcade set with a
  missing BIOS (refusal and Back), Genesis passed (Nova 2026-11-22)
- [x] Swipe-kill while backgrounded, reopen: resumes from slot 9; launch with a resume slot (cover shows, then fades) (Nova 2026-11-22: God of War II; cover fade not judged)
- [ ] Quit: the Save & Quit shortcut, Back before the first frame. Quit from the menu and a quit during a manual save
  passed (Nova 2026-11-22)
- [x] Slot save/load from the menu and from the skin/touch SAVE/LOAD; "Still loading" before the first frame (Nova 2026-11-22: Emerald, Mario Kart DS strip, God of War II)
- [ ] Wii: Motion recentre, a second pad claiming a port. Controller change, Motion toggle, HOME then Wii Menu, GameCube
  under the WII tag passed (Nova 2026-11-22)
- [ ] DS 3D resolution: Auto follows rotation/fold (needs the Fold), the software-fallback notice. A fixed pick passed
  (Nova 2026-11-22)
- [ ] Touch shortcuts: FF and rewind (hold and toggle), Adjust buttons backdrop. The strip slots passed (Nova 2026-11-22)
- [x] Mic: first toggle asks permission (denied = noise); off under the menu and in the background, back on resume (Nova 2026-11-22: background not checked)
- [x] Quit right after a manual Save/Load or a Wii HOME press on each early path (netplay or no-states game, quit while
  the resume loads, Wii Menu exit): clean exit, the slot is written. A quit whose screen the system destroyed before its
  finish never ended the game process; fixed in #198 (AYANEO and Nova 2026-11-22)
- [ ] A SAF cue/bin with a missing track still refuses with the error (the Nova has no SAF home or folder)
- [ ] 3DS then Wii in a row (and back): motion works in the second game; the 3DS tilt stops after quitting (needs phone motion)
- [ ] Mic button and touch shortcut buttons in a netplay game (speed shortcuts hidden in netplay). DS/3DS passed (Nova 2026-11-22)
- [ ] Sega pad Auto picks the cart's pad type; 3DS New Controls Auto reads the title (needs the drawn pad, hidden by the Nova's controller)
- [x] Save/Load state shortcut or skin SAVE/LOAD right after launch uses the last-used slot (Nova 2026-11-22: Emerald slot 3)

## Review round 2 fixes: device checks still owed

Already passed on the Nova (wireless adb): GBA, DS, PS smoke; GBA, PS2, GC, Wii, PSP, 3DS, DC, N64 run, hold under the
menu, survive Home and return, quit and reload slot 9 (8/8); save then quit at once ×3; Wii quit ×3 in 5–6 s.

- [ ] Art fetch: Pause, Cancel and Reset during a long run of "has art" skips stop at once (no hang); Resume continues;
  a single-game fetch started during a Pause waits for it (Nova 2026-11-22: Pause, Resume and Cancel stop at once on a
  missing-art run; Reset, single-game fetch and a "has art" stretch still owed)
- [ ] Remove an extra on a large library, then rename / hide a game during the re-index: no stall (the Nova's SD extra is its only ROM folder)
- [ ] Art fetch on a big library: the menu stays smooth on each art flush; art results during a rescan are kept (not run 2026-11-22: ScreenScraper quota)
- [x] A Notepad-saved .cue (UTF-8 BOM) in an extra or loose folder is identified and placed (Nova 2026-11-22: loose in the SD extra, one game)
- [ ] Update dialog: holding DOWN on long notes stops on release; the Scroll hint steps from the current target (no local update feed set up)
- [ ] Button layout editor toolbar: drag, unfold / rotate, drag again — it reaches the new edges (needs the Fold; the
  bottom edge holds on the Nova)
- [x] Device Sync (two devices): a client left on its confirm page > 5 min still syncs; B on either side after the
  other's A shows "The other device cancelled the sync or couldn't start it." Confirm page left 5 min 44 s still synced
  (2026-11-22). B on the client after the server's A showed "…sync failed…" on main: the server counted its own 127.0.0.1
  listening check as the peer's connection; fixed in #201 (AYANEO server: "…cancelled the sync or couldn't start it.", 2026-11-22).
- [ ] RetroAchievements: Stop a sync while a game journals an unlock; the next sync works (no OverlappingFileLock)
- [x] Kill a game mid-play, wait > 3 min in the menu: Game Time and Home agree. Home stayed stale until the menu
  restarted; fixed in #199 (the menu closes dead sessions on return; Nova 2026-11-22)
- [ ] Save & Quit, then tap LOAD during the save: the next launch resumes at the quit point; Wii HOME during a quit is ignored (Nova 2026-11-22: resumes at the quit point, Wii HOME ignored; the quit dialog covers the panel there, so LOAD was not reachable)
- [ ] Launcher mode: change Pause when unfocused and return — it applies (both directions in multi-window). Home out of
  a game, kill the menu process, return: no stall; Second screen applies both ways (Nova 2026-11-22)
- [ ] Kids Mode: lock / unlock / lock quickly — at most one bounce a second (Nova 2026-11-22: no bounce storm, but only
  tried during a timed unlock; fast Locked ↔ unlocked cycling not reached)
- [x] Wi-Fi netplay: after the peer ends, `dumpsys wifi` shows no nxredux:netplay lock while the game plays on (AYANEO quit, Nova played on, no lock on either, 2026-11-22)
- [x] Vulkan PSP / Azahar with a shader preset and while the picture is held; GL Dolphin / Flycast with a preset (Nova 2026-11-22: CRT Geom on each)
- [ ] RA hashing (RA not signed in on the dev app). Save / load states on PS2 and Wii (large), PPSSPP; SRAM / RTC; multi-file
  VFS launches passed (Nova 2026-11-22)

## MSU-1 / MSU-MD (PR #147)

Device checks still owed:

- [x] Genesis Plus GX with an MSU-MD set and a Sega CD BIOS: music plays (the cart mounts its cue through the VFS). MSU-MD Sample on the Nova, 2026-11-16.
- [ ] Play build (no All files access): an MSU-1 pack streams.
- [x] Play build (no All files access): an MSU-MD set and a Sega CD disc on PicoDrive stream from a SAF folder (no cache copy) and play. MSU-MD Sample and Sonic CD (USA) on the Nova, 2026-11-16.
- [ ] Device Sync of an MSU pack in an extra folder: the folder arrives whole; a loose set's `.msu`/`.pcm` or cue and tracks travel with its ROM.

Follow-ups, after #147:

- [x] Region-aware BIOS check (feat/region-bios): a Sega CD disc's header decides which region's BIOS it needs; the popup names the file and where the other regions' files are. Sonic CD (USA) on the Nova, 2026-11-15.
- [x] PicoDrive on the libretro VFS (feat/picodrive-vfs, core patch picodrive/0003): it streams from descriptors like the other cores, so Sega CD discs and MSU-MD sets are no longer copied to the cache; only PrBoom keeps LoadMode.REAL_PATH. Emulator: MSU-MD Sample, Sonic CD (USA), a plain cartridge; Sonic CD on the Nova, 2026-11-15.
- [x] PC Engine CD: the BIOS check asks for the System Card the CD BIOS option picks (#181). Was: check whether a region-specific System Card matters (mednafen pce_fast takes syscard3.pce for both); add it to the region check if so.
- [ ] BIOS checksums: check each BIOS file against the known good dumps, so a bad or misnamed dump gets its own message. Merged #195; phone check owed (PS1 BIOS as disksys.rom, European BIOS as bios_CD_U.bin, PS2 rehash).
- [x] MSU-MD on Genesis Plus GX: the launch checks the Sega CD BIOS of the cartridge's region (its header's country codes, or the forced region), as the core loads it. Refused without bios_CD_U.bin and played with it, MSU-MD Sample on the Nova, 2026-11-16.
- [x] BIOS copies: the app copied BIOS files into its own system folder and never removed one whose original was gone, so a deleted or renamed BIOS still loaded and passed the check. It now records each copy by the Bios folder it came from and removes it once no folder has it (a bundled file is restored). Refused with bios_CD_U.bin renamed, re-copied and played with it back, MSU-MD Sample on the Nova, 2026-11-16.
- [ ] More badges ([IndexedGame.badge] is general): from bracketed file-name tags ((Hack), [h], (Homebrew), (Translated), (Beta)/(Proto)) via a small table, a per-game choice in Options (wins over detected ones), or a DAT/ScreenScraper match. One badge per game today. Tag table + per-game Badge merged #196; DAT/ScreenScraper source, arcade titles and phone check owed.
- [x] Docs: unzip MSU packs (a zip cannot be streamed); a pack in its own folder is one game. nx-redux-docs PR #81 (open).

## PS2: ARMSX2 nightly-20261114 with LTO and upstream's profile (PR #152)

Measured 2026-11-15, Budokai Tenkaichi 3 battle state: AYANEO (Mali-G76) 22–28 fps on OpenGL ES with the old pin →
55–58 fps on Vulkan at native resolution, 38 fps at 2x; Nova (God of War II) unchanged. The official ARMSX2 app on the
AYANEO holds 60 fps at 1x and 2x on Vulkan with the malisx2 driver, 30–34 fps on OpenGL.

Device checks still owed (the areas core patches 0002–0006 touch):

- [ ] Memory cards: save in game, quit, relaunch → the save is there; a card from the old pin still loads. (Nova 2026-11-22: card unchanged across quits and a kill; an in-game save needs play to a save point.)
- [ ] Two players: second controller claims port 2 and plays.
- [ ] Cheats & Patches: a cheat from Cheats/PS2 and a patch from Cheats/PS2/Patches apply. (No PS2 cheats or patches in the Nova's dev home.)
- [x] Resume: Home and return, quit and relaunch from slot 9, a slot save/load; a state from the old pin loads (Nova 2026-11-22: God of War II; the older state's pin not confirmed).
- [ ] 16 KB pages (nx16k): a PS2 game starts and runs on the new pin.
- [ ] OpenGL ES at 2x on the AYANEO with the optimised build, measured cool (the first passes heat-throttled). (AYANEO 2026-11-22: never under 37.7 °C on USB power; the Budokai Tenkaichi 3 ROM is gone, only its states.)
- [ ] Custom GPU driver on a build with the slang merge (#144): one launch on the Nova with a Turnip package. (Nova 2026-11-22: no driver installed in the dev app.)

Follow-ups:

- [x] Graphics API Auto on Mali: PS2 Auto is Vulkan on Mali (autoOnMali, #159). Was: Auto means OpenGL ES, so a Mali user must pick Vulkan to get the gain. Decide whether PS2 picks Vulkan there.
- [x] malisx2 runs through the image-reader hand-off (#159). Was: malisx2 through Settings → Emulation → GPU driver: it loads but fails the pre-check (no AHardwareBuffer import) and falls back. Needs the Vulkan path to present straight to the screen (a swapchain) instead of through the OpenGL presenter; overlays, shaders and the second screen sit on that presenter today.
- [x] Debug test hooks are activities now (#184). Was: AYANEO firmware drops broadcasts to the debug test hook (manifest receiver skipped); a run-time-registered receiver worked. Decide whether the debug build should register it that way.
- [x] Thread pinning is off on chips with two fast cores ("Performance Cores unavailable"); check whether pinning two of the four threads helps. Investigated 2026-11-22: the libretro build has no pinning switch; on the G90T the scheduler already keeps VU and GS on the two A76 cores; upstream notes a 30–45% loss from ordered pinning on two fast cores. No gain expected, not built.

Left on the devices (2026-11-16):

- [ ] AYANEO: ARMSX2 Nightly installed (with the battle state and auto-load on); `/sdcard/NXReduxDev-perf-backup`; the battle state in slot 0 of Budokai Tenkaichi 3; malisx2 in the dev app's driver folder; Debug log on.
- [ ] Nova: the temporary build with the PS2 4x override is still installed (install a current build); `/sdcard/NXRedux-gpu-test-backup-2`; Debug log on; `/sdcard/NXRedux/drivers` and 10 installed drivers from the GPU driver tests.

## Codebase review (comments, stale code, repeated code, simplifications, AppState split): device checks owed

Checked on the emulator only (GBA game starts, draws, quits; first setup up to Permissions). On real devices:

- [x] A hardware-rendered game on OpenGL ES and one on Vulkan start, draw and quit (the native start-up call lost five
      options and the ES2 path; Dolphin, PPSSPP, ARMSX2 on the Nova; one on the AYANEO) (Nova and AYANEO 2026-11-22:
      Sunshine, Patapon 2, Okami on both APIs; AYANEO GLES and Vulkan).
- [x] Shaders: Sharp and a slang preset still apply; Default after either. Sharp picked live never reached the second
      display (Video::drawFrame built shader programs only after its pictureHeld return); fixed in
      #197 (Nova 2026-11-22).
- [ ] Custom GPU driver: add a package from a zip on Android 9–12 (the AYANEO) and on the Nova. (AYANEO 2026-11-22: no driver zip at hand.)
- [ ] First setup end to end on a fresh install (text size → permissions → folders → Device Sync step → artwork and
      RetroAchievements sign-in → Done); kill the app mid-setup and reopen. (Needs a fresh install.)
- [x] Controls → Shortcuts press-to-bind and System buttons bind and cancel with a controller, leaving the page mid-wait (Nova 2026-11-22: R2 and physical L3 bind, B cancels).
- [x] Settings: Button layout editor round trip; each Layouts page; Reset to defaults on Appearance and Main menu (Nova 2026-11-22).
- [x] Dual screen (Nova): the top screen's details and stats while moving through lists; the menu's top screen (Nova 2026-11-22).
- [x] A zip and a 7z game launch; a password-protected 7z says so (Nova 2026-11-22).
- [ ] Netplay: host advertises over Wi-Fi and over the cable; a second device finds it. Wi-Fi passed (AYANEO found "Mario Kart 64 (192.168.1.13)", 2026-11-22); cable owed.
- [ ] Settings survive the update: second screen, pause when unfocused, screen care, tool apps, tips hidden.

## Found 2026-11-22: open

- [ ] Second screen Settings tab Shader row: the picker list dims the top picture (only the preset strip lifts it), as the menu's own path; decide whether the list should leave the picture undimmed.
- [ ] In-game menu: a deeper page's dimmed parent column keeps a fixed share, so long words overflow and the selected one scrolls ("Shader Parameters ›"); decide whether it should size to its words.
- [ ] AYANEO: Button layout editor Portrait is black (the firmware keeps landscape); Back does nothing there.
- [ ] Pad skin Off portrait overlay showed the landscape grip layout letterboxed (AYANEO, forced rotation; may be the rotation).
- [ ] A slot-9 thumbnail was all black after a Home with Bottom screen = Second screen (Nova, 1942; once).
- [ ] Players tab shows Player 1–4 Empty while the built-in controller plays as P1 (Nova, Sunshine).
- [ ] In launcher mode a Game Switcher return relaunched the live game instead of resuming it (Nova, Doom; check whether expected).
- [ ] After a tap on the bottom panel, d-pad keys stopped moving the in-game menu (Nova; B and touch still worked).
- [ ] Pokemon Ultra Sun benchmark state (nx-benchmark states/3DS-azahar) is the first battle, not "the first walk outside"; on the AYANEO its Pokémon models draw striped (GLES and Vulkan).
- [ ] GAME test hook loads a custom GPU driver only with --es game_path (pacing/slang runs without it measured the system driver).
- [ ] Slow slang presets on Mali-G76 (correct picture): aa-shader-4.0 ~5 fps, authentic_gbc ~6, crt-lottes ~7, crt-mattias ~21.
- [ ] Cheats & Patches: a cancelled cheat database install shows "Not installed" while about 20k files are still being deleted (about 2.5 min).
- [ ] Test hooks: GAME_FF brings the menu task over the running game on Android 11; GAME's `game_path` must be the library path (`/Extra/Roms-…/…`), a file path records play time under a separate game.
- [ ] Device Sync carries device-specific emulator config: Dolphin's `GFX.ini`, `Dolphin.ini`, `WiimoteNew.ini` and Azahar's `mac.txt` crossed Nova ↔ AYANEO; decide which stay per device.
- [ ] N64 netplay input from the joining side: adb keys don't reach the N64 game; press START on the guest's pad and watch it act on the host.
