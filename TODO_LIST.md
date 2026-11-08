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
