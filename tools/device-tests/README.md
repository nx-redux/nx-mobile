# Device tests: Brick driver

Mac-side Python 3 (stdlib only) driver for a TrimUI Brick (tg5040) on NX Redux, over adb. Design:
`docs/superpowers/specs/2026-10-16-device-test-automation-design.md`. Safety rules: nx-redux `.dev/TESTING.md`.

- `brick.py`: the `Brick(serial)` class.
- `state_roundtrip_smoke.py`: Brick-only proof of the state recipes (`--serial`, `--out DIR` for screenshots + log).
- `fold.py`: the `Fold(serial)` class for the phone (debug build hooks: RESET, SYNC, SYNC_STOP, GAME, GAME_STOP;
  private files through run-as).
- `sync_test.py`: Device Sync scenarios 1-6 over Wi-Fi (`--skip-roms`, `--only-roms`); report in
  `.local/device-tests/sync-test-<date>.md`, Brick screenshots in `.local/device-tests/sync-shots/`. Sends SYNC_STOP
  before each sync and retries once when the phone pairs but never connects (seen on ROMs-on syncs).
- `state_grid.py`: the save-state grid (`--only core,core`); report in `.local/device-tests/state-grid-<date>.md`.

- `pacing.py`: frame pacing on any phone with the debug build: launches a game through the GAME hook (optionally from
  a state in a slot, the same moment every run), records SurfaceFlinger timestats, and alternates an option
  (`--ab nx_graphics_api=gles,vulkan`) or a system property (`--ab-prop debug.nx.presenttime=0,1`). Prints frames,
  uneven frames, frames held 33 ms+, the core's work per frame (`--work`: Debug log on for the runs) and the temperature.
  Each run ends with the game's Quit (GAME_STOP, so saves a core writes itself finish) before the force-stop; the
  game's auto state is set aside for the runs and put back. `test_pacing.py`: that teardown, no device.
- `slang_shaders.py`: slang presets from the home's `Shaders/shaders_slang` on a phone (`--only SFC,NDS`, `--presets`):
  per console and preset, a launch through the GAME hook, a screenshot (`.local/device-tests/slang-shots/`), frame
  pacing, the time the preset took to prepare and any shader error from the log. Game options, resume points and the
  Debug log setting are put back.
- `quit_loop.py`: boot a game, wait, GAME_STOP, check its :game process ends with no ANR or watchdog stop; repeat.
  GAME_STOP quits like Save & Quit, so the game's resume point moves.
- `dolphin_sav.py`: wraps the libretro Dolphin core's state in standalone Dolphin's .sav headers, for comparing both
  apps from one moment.

### After a Dolphin pin bump

1. `gmake -C cores dolphin`: the patches apply in order (`dolphin.patch`, `dolphin/0001…`, `dolphin/0002…`); on a
   clash regenerate them on the new head with `git apply -3`, and drop a backport the pin now has (0002 once the
   libretro fork includes upstream PR #14928). Keep each patch's licence header.
2. Pacing, SMG2 from a state (Nova): `pacing.py --tag WII --core dolphin --rom <SMG2> --state <state> --runs 2 --work`;
   expect about 5-10 uneven frames a minute, none held 33 ms, about 14 ms work a frame.
3. Quit: `quit_loop.py --tag WII --rom <NSMB Wii> --runs 10`, then again with `nx_graphics_api = vulkan` set for it.
4. Paper Mario TTYD: Mario and the crowd drawn; the log's `nx: active hacks for G8ME01` shows `BBox=true`.

Run from the repo root; every adb call passes `-s`.

Every file a test creates is named `nxtest-*`; `cleanup_nxtest()` removes them from `Roms`, `Saves`, `.userdata`,
`Bios`, `Collections` and `/tmp`.

## Verified on the Brick (2026-10-16)

| What | Value |
| --- | --- |
| Pad / power nodes | `/dev/input/event3` (TRIMUI Player1), `/dev/input/event1` (axp2202-pek, KEY_POWER 116) |
| Buttons | physical A=305, B=304, X=308, Y=307, L1=310, R1=311, SELECT=314, START=315, MENU=316; d-pad `ABS_HAT0X/Y` ±1 then 0 |
| Emu pak | `/mnt/SDCARD/.system/paks/Emus/<TAG>.pak/launch.sh` (GBA = gpsp); no user override in `/mnt/SDCARD/Emus` |
| Device Sync pak | `/mnt/SDCARD/.system/paks/Tools/Device Sync.pak/launch.sh` (runs `sync.elf`) |
| `$LOGS_PATH` | read from the running launcher's `/proc/<pid>/environ`; `/mnt/SDCARD/.userdata/tg5040/logs` with Debug log on, `/tmp/nx-logs` with it off |
| Game log | `$LOGS_PATH/<TAG>.txt`, rewritten on every launch |
| Auto state | `/mnt/SDCARD/.userdata/shared/<TAG>-<core>/<rom without ext>.state.auto` (manual: `.state`, `.state1`..`.state8`) |
| Battery save | `/mnt/SDCARD/Saves/<TAG>/<rom without ext>.srm` (written on sleep too) |
| Screenshot | `screenshot.elf --capture /tmp/nxtest-shot.bmp` (with the launcher `LD_LIBRARY_PATH`), 1024x768 BMP, converted to PNG on the Mac |

## Recipes

- **Launch**: `/tmp/next` gets `'<pak>/launch.sh' '<rom>'`, then `kill -9` nextui, in one adb shell, and only while
  nextui has a pid (`launch()` refuses otherwise).
- **End a game**: `kill_game()` = `kill -9` the minarch pid (never SIGTERM: minarch powers the device off), then wait for
  nextui. It wakes the device first and drops an `auto_resume.txt` that points at an `nxtest-*` ROM; otherwise nextui
  comes back asleep (keymon stopped, backlight off) and relaunches the game at once.
- **Sleep autosave**: a short KEY_POWER press (hold < 1 s; 1 s powers off) makes minarch write the `.state.auto`, the
  `.srm` and `.userdata/shared/.minui/auto_resume.txt`; the wake press (KEY_POWER again) removes `auto_resume.txt`.
  Sleep is detected by `keymon.elf` being SIGSTOPped. A wake press sent ~1.5 s after sleep starts was missed once:
  `sleep_autosave()` waits 3 s and `wake()` retries up to 3 times, pressing only while still asleep (a press on an
  awake device puts it to sleep).
- **Resume**: `set_resume_slot(9)` writes `/tmp/resume_slot.txt` before the launch; minarch reads and unlinks it at
  boot. A load passes when the file is gone, the log has no `Error restoring save state` / `Error reading` /
  `Error opening state` line and minarch is still running. The smoke test's negative control (an empty state) logs
  `[ERROR] Error opening state file: ...`, so the check can fail. RetroAchievements hardcore mode blocks state loads
  without a log line: the smoke test refuses to run if `raHardcoreMode=1`.

## Device Sync, headless

`start_device_sync(wifi=True)` / `stop_device_sync()`:

1. `launch('/mnt/SDCARD/.system/paks/Tools/Device Sync.pak/launch.sh')` (no ROM argument); `sync.elf` shows a splash,
   then the **Ready** screen ~2 s later with "Sync via cable" selected.
2. **DOWN** (or UP) toggles cable and Wi-Fi ("Sync via Wi-Fi" highlighted). **X** toggles "ROMS: OFF/ON" (not
   driven by the driver).
3. **A** starts discovery: "Searching for device... IP: 192.168.1.17 (Wi-Fi)". If Wi-Fi is down, the No Wi-Fi screen
   shows instead (B returns to Ready).
4. **B** on Searching goes back to Ready; **B** on Ready exits `sync.elf`, and nextui comes back by itself (the first
   frame is black for a moment). `stop_device_sync()` falls back to `kill -9 sync.elf`.

Not verified: a full sync (no peer was set up), and the Done/Error screens (A goes back to Ready, B exits).

## Side effects that are not `nxtest-*` files

- `$LOGS_PATH/GBA.txt` and `sync.txt` are rewritten by every launch, and `nextui.txt` by every nextui restart.
- With RetroAchievements on, minarch logs in and loads the game on RA (Flappy Bird is a homebrew RA entry).
- Launching through `/tmp/next` skips nextui's `gametimectl start`, so no play time is recorded for test games.
