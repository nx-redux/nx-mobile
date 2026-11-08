# TODO list

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
