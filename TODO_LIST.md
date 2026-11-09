# TODO list

## Review round 3: device checks owed

- [ ] Kids: unlock sheet on the tiles, screen off then on → tiles, not the sheet; same from Open mode's lock icon → menu; sheet left 60 s closes; Home over it, back → gone.
- [ ] Kids: Open mode, MENU on a game shows only play items (no Hide, Rename, art, Game Settings, Emulator, collections); Parent mode shows them all; a Rename prompt open when Parent ends closes.
- [ ] Kids, dual screen (Nova + second screen): Locked, open an allowed app on the top screen, follow a link to a blocked app → bounced back (and the bounce covers the top display).
- [ ] RA: revoke/sign out mid-game, unlock, sign in, sync → the unlock arrives; quit right after an unlock → no toast during the quit.
- [ ] Merge a split console in the home summary → play time follows the games.
- [ ] Device Sync: stray `.tmp` in Saves and a stale `.part` in Roms are not sent; SAF home scrape/sync sidecars still write.
- [ ] gpSP Wireless Adapter phone↔Brick (Union Room trade), phone host then Brick host; patched phone with an older Brick build still connects.
- [ ] Controls editor: d-pad mode on DC/GC/N64, drag the d-pad toward the face buttons, save → lands where the editor showed (in game and from Settings); title shows "Pad skin"/"Overlay"; Reset console and Appearance reset put overlay positions back.
- [ ] Game screen: Second screen toggle in launcher mode with the menu process killed doesn't stall; Vulkan→GLES fallback relaunches cleanly.
- [ ] Home during a slow PS2/Wii quit, tap the same game → starts fresh; Home during a big PS2 state resume → no ANR; then clear it from the launcher icon → SRAM survived.
- [ ] Threaded cores across pause/resume (input lock): Dolphin GC/Wii incl. Wii pointer, Azahar, ARMSX2, PPSSPP; N64 netplay input still arrives; a game from a picked file still opens.

## Controls editor: skin and overlay layouts

Device checks still owed:

- [ ] Landscape: R shoulder moves to the very top edge on a skin; buttons enter each screen up to 10% from its edges, not further.
- [ ] Resize a button very large and very small (floor/ceiling hold); two buttons can overlap visually but their touch areas never stack.
- [ ] Pad skin Off: Adjust buttons edits the overlay; untouched overlay buttons start at the skin positions; moving them doesn't change the skin; Pad skin On again shows the skin layout unchanged.
- [ ] Existing skin tweaks from before the upgrade still land where they were.
- [ ] Frame layouts' touch-shortcut strips may now enter the bottom 10% of the screen above them.
- [ ] The red area in Adjust buttons covers the whole screen frame above the touch-shortcut row; shortcut labels are centred in their pills.
- [ ] A button moved onto a screen's edge draws over the picture (editor and game) and its touches press it, not the game.
- [ ] Editor drag stays smooth on a slow device (AYANEO) with long drags.
- [ ] An owned overlay button stays put after a later skin edit.
- [ ] A shoulder shrunk before the upgrade keeps its size.

## Dolphin (GameCube / Wii) multiplayer: untested

GameCube and Wii pass with 2 controllers (Double Dash, NSMB Wii sideways). Still untested:

- [ ] 3 and 4 controllers at once (GameCube and Wii)
- [ ] Wii Classic Controller in multiplayer (e.g. Mario Kart Wii)
- [ ] Unplugging and replugging a controller keeps its player slot
- [ ] Wii players 2–4: no pointer (touch drives player 1 only) and no motion (phone sensors feed player 1 only). Decide whether to support them.

## Review bug fixes (PR #102): device checks still owed

Already passed on the Nova: game smoke test (GBA, DS, PS), play time across menu and back, Device Sync wake and
Wi-Fi locks with the screen off, the 5-minute search limit, and settings reaching a resumed game.

Fold or Flip:
- [ ] Half-folded launch of a DS/3DS game with a pad skin starts on the hinge layout, with no flat-layout flash first
- [ ] With the dual-screen menu open while a game runs, play time keeps counting: Game Tracker shows the full session
  after quitting
- [ ] Game on an external monitor or DeX: the second-screen panel never covers the game; the phone screen is offered
  instead (or dual screen stays off)
- [ ] Settings → Second screen / Bottom screen changed in the menu while a game is suspended (launcher mode Home)
  applies when the game comes back

Two devices:
- [ ] Device Sync over Wi-Fi with the other device vanishing mid-run (Wi-Fi off, no FAIL): the server gives up after
  about 5 minutes with "The other device stopped answering (5 min)"
- [ ] A long Device Sync (ROMs on, one large file) still finishes: the 5-minute limit must not end a transfer while an
  rsync connection is open
- [ ] Device Sync with the screen turned off mid-run finishes without stalling
- [ ] Wi-Fi netplay connects and plays; `dumpsys wifi` shows the `nxredux:netplay` lock during the session

Any device:
- [ ] Art: Replace (or a picked image) over a hand-placed `Name.PNG` on the SD card keeps the new image and shows it
- [ ] Cheats: Remove the cheat database deletes only `Cheats/<TAG>/` files; saves and other folders untouched
- [ ] Setup move / Merge folders with a PS disc game whose `.cue` names its `.bin` in another letter case (or with
  accented names): the game moves with all its files
- [ ] RetroAchievements: Sync now from the menu while a game earns an unlock (dual-screen menu): the unlock is kept
- [ ] RetroAchievements page with the RA folder unreadable (All files access revoked): shows an error, no crash
- [ ] Settings → touch button help text reads right (Adjust buttons in the in-game Controls)

## Review performance fixes (PR #103): device checks still owed

Already passed on the Nova:
- PS2, GC, Wii, PSP, 3DS, DC and N64 run, hold still under the in-game menu, resume, survive Home and return, and
  quit. Home's state serialize and write run off the main thread (serialize 0.1–3 s, 17–109 MB written).
- GBA battery save: three menus and a Home leave an unchanged `.srm` alone; quit and relaunch load it.

Art fetch (real library, a few hundred games):
- [ ] Fetch missing advances smoothly; the game being fetched shows Searching / Downloading; Pause, Resume and Cancel
  mid-job keep finished games finished
- [ ] A console with no `.media` folder gets its folders and art on the first fetch
- [ ] Change art pick for a game the running job hasn't reached yet is kept
- [ ] Untagged ROMs still get libretro-thumbnails art through its name list
- [ ] TheGamesDB: a multi-region or multi-disc set uses fewer requests (allowance before/after)
- [ ] ScreenScraper: Fetch again on name-miss ROMs starts the hash lookup at once (no re-read of big ROMs)
- [ ] Replace all: the written-art log only grows by appends; Reset artwork afterwards deletes exactly the app's files

Game and video:
- [ ] DS (melonDS) with a multi-pass shader preset: top shaded, bottom plain; Screen Sharpness, Large layout with a
  translucent inset, screen swap
- [ ] 3DS with a preset under video views: menu pause keeps the picture (no black), resume
- [ ] GC/Wii and Flycast: change the shader preset mid-game; no black frames or broken textures
- [ ] PSP on Vulkan and on GLES: play, pause/resume, fast forward
- [ ] Save/load state on PSP, GC and PS2; a PPSSPP state is the same size as before and loads
- [ ] Thumbnails for Home, slot save and Quit look sharp, incl. a DS/3DS top-screen crop; Adjust buttons backdrop sharp
- [ ] Home then force-stop the game process: relaunch resumes from slot 9 and the switcher thumbnail matches
- [ ] Two controllers on a 2-player core: P2 claims with a stick/trigger push, rumble per pad, replug keeps the slot
- [ ] A System buttons binding works in game, also after changing it in Settings and returning to the running game
- [ ] DS stylus mode: cursor moves smoothly with d-pad and stick, stops at edges, moves again after the menu closes

Menu:
- [ ] Return from a game: Continue's picture updates and the highlight stays put (also returning to the Game Switcher)
- [ ] Menu pad shows/hides right on onboarding's Pad step, with a tool on top in two-screen skin mode, and after
  toggling Appearance → Pad skin
- [ ] Game Tracker shows box art right after a fetch
- [ ] Dual screen: moving through large merged consoles feels smooth

Kids Mode:
- [ ] Locked: a disallowed app bounces about once a second, allowed apps stay, keyboard and Settings while granting
  are exempt, the Locked game screen from Recents still quits

## Repetition extraction (PR #105): device checks still owed

Already passed on the Nova: GBA, DS, PS smoke; GBA, PS2, GC, Wii, PSP, 3DS, DC and N64 run, hold under the menu,
survive Home and return, quit, and load their auto state again (slot 9).

- [ ] Pause when unfocused: with another app focused the game holds and the screen sleeps on Android's timeout; focus
  back plays on and keeps it awake (stays on throughout under live netplay)
- [ ] Held UP/DOWN on a long in-game message and on a setting's info popup keeps scrolling; touch scroll then a key
- [ ] In-game menu portrait (words beside the panel, Save/Load slot card, Options preview), narrow window stacks
  (Flip), split view on the Fold (dimmed parent pane, slot card, Options word list)
- [ ] In-game Achievements page, Locked only toggle; panel Cheats (empty and full) and Players tabs
- [ ] Home's Continue and Pick cards, box-art shadows, Carousel collection and console counts look unchanged
- [ ] DS/3DS hat d-pad and L2/R2 as axes; GC and DC analog triggers; DS/3DS face and L2/R2 keys
- [ ] Save/load SRAM and RTC (a GB/GBA game with a clock, and a core without one: no error log)
- [ ] Multi-disc m3u and CUE+BIN load; RetroAchievements hashing, unlock toasts, core messages, rumble
- [ ] Core options with categories (localized labels); controller type lists
- [ ] A collection file or map.txt starting with a byte-order mark (from Windows): its first entry still works

## Security fixes (Device Sync confirm, update check): device checks still owed

Needs two devices. No protocol change: an older build on the other side still syncs.

- [ ] Phone ↔ phone (Wi-Fi and cable): each confirm page shows the other's IP, matching the IP that device shows for
  itself ("This device: …")
- [ ] Phone ↔ Brick (Wi-Fi and cable): the same, both ways
- [ ] A on both, in either order, syncs; B on either side cancels both (the other says "The other device cancelled the
  sync." while its confirm page shows, "The other device's sync failed…" once it pressed A)
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
- [ ] Minimal launch sequence: the spread, hand-off and return, a refused launch retreating with its error, Home while
  handed off
- [ ] Game Switcher: resume from it and come back to it; it reopens after the menu process restarts; Y Remove updates
  Continue
- [ ] Kids Mode: lock/unlock with the PIN, setup from onboarding incl. the home-role step, the day editor's From/To
- [ ] Settings: chooser and info popup (pad and Back), row edit in two-pane, Reset on Appearance, Main menu, Shortcuts,
  System buttons
- [ ] First setup: the home page move with a stop mid-move, the ROMs folder summary move, the copy offer after a home
  change
- [ ] A large Fetch art run, then the menu and the open list refresh once after results stop

Game side (GameActivity split):
- [ ] Launches: PS1 from .cue/.chd, a zipped game (unpack toast), an arcade set with a missing BIOS (refusal), N64 with
  a hi-res pack, Genesis (Sega pad type), one launch that fails (dialog and Back)
- [ ] Swipe-kill while backgrounded, reopen: resumes from slot 9; launch with a resume slot (cover shows, then fades)
- [ ] Quit from the menu, the Save & Quit shortcut, a quit during a manual save, Back before the first frame
- [ ] Slot save/load from the menu and from the skin/touch SAVE/LOAD; "Still loading" before the first frame
- [ ] Wii: controller change, Motion toggle and recentre, HOME then Wii Menu, a second pad claiming a port; GameCube
  under the WII tag
- [ ] DS 3D resolution: Auto follows rotation/fold, a fixed pick, the software-fallback notice
- [ ] Touch shortcuts: FF and rewind (hold and toggle), the strip slots, Adjust buttons backdrop
- [ ] Mic: first toggle asks permission (denied = noise); off under the menu and in the background, back on resume
- [ ] Quit right after a manual Save/Load or a Wii HOME press on each early path (netplay or no-states game, quit while
  the resume loads, Wii Menu exit): clean exit, the slot is written
- [ ] An NDS game with a stale resume: the first menu shows no deleted slot and no last-slot pointer on it
- [ ] A SAF cue/bin with a missing track still refuses with the error
- [ ] 3DS then Wii in a row (and back): motion works in the second game; the 3DS tilt stops after quitting
- [ ] Mic button and touch shortcut buttons on DS/3DS and in a netplay game (speed shortcuts hidden in netplay)
- [ ] Sega pad Auto picks the cart's pad type; 3DS New Controls Auto reads the title
- [ ] Save/Load state shortcut or skin SAVE/LOAD right after launch uses the last-used slot

## Review round 2 fixes: device checks still owed

Already passed on the Nova (wireless adb): GBA, DS, PS smoke; GBA, PS2, GC, Wii, PSP, 3DS, DC, N64 run, hold under the
menu, survive Home and return, quit and reload slot 9 (8/8); save then quit at once ×3; Wii quit ×3 in 5–6 s.

- [ ] Art fetch: Pause, Cancel and Reset during a long run of "has art" skips stop at once (no hang); Resume continues;
  a single-game fetch started during a Pause waits for it
- [ ] Remove an extra on a large library, then rename / hide a game during the re-index: no stall
- [ ] Art fetch on a big library: the menu stays smooth on each art flush; art results during a rescan are kept
- [ ] A Notepad-saved .cue (UTF-8 BOM) in an extra or loose folder is identified and placed
- [ ] Update dialog: holding DOWN on long notes stops on release; the Scroll hint steps from the current target
- [ ] Skin layout editor toolbar: drag, unfold / rotate, drag again — it reaches the new edges
- [ ] Device Sync (two devices): a client left on its confirm page > 5 min still syncs; B on either side after the
  other's A shows "The other device cancelled the sync or couldn't start it."
- [ ] RetroAchievements: Stop a sync while a game journals an unlock; the next sync works (no OverlappingFileLock)
- [ ] Kill a game mid-play, wait > 3 min in the menu: Game Time and Home agree
- [ ] Save & Quit, then tap LOAD during the save: the next launch resumes at the quit point; Wii HOME during a quit is ignored
- [ ] Launcher mode: Home out of a game, kill the menu process, return — no stall; then change Second screen / Pause when
  unfocused and return — it applies (both directions in multi-window)
- [ ] Kids Mode: lock / unlock / lock quickly — at most one bounce a second
- [ ] Wi-Fi netplay: after the peer ends, `dumpsys wifi` shows no nxredux:netplay lock while the game plays on
- [ ] Vulkan PSP / Azahar with a shader preset and while the picture is held; GL Dolphin / Flycast with a preset
- [ ] Save / load states on PS2 and Wii (large), PPSSPP; SRAM / RTC with a clock game; multi-file VFS launches and RA hashing
