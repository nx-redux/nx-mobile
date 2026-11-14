#!/usr/bin/env python3
"""Slang shader presets on a phone: launches games with presets from the home's Shaders/ folder and reports each run.

Runs on any phone with the debug build (com.nxredux.mobile.dev) over adb, through the GAME hook (no taps). For each
case (console, game, preset) it writes the game's option file, launches from boot (the game's resume point is set
aside), lets the preset load, takes a screenshot, records frame pacing for a few seconds, reads the log (Debug log is
turned on for the runs) and quits. Every game's option file, resume point and the Debug log setting are put back.

    python3 tools/device-tests/slang_shaders.py --serial c2c658b2
    python3 tools/device-tests/slang_shaders.py --serial c2c658b2 --only SFC,NDS --presets crt/crt-geom

The table shows, per run: frames presented and uneven ones (as pacing.py), the core's work per frame, how long the
preset took to prepare (parse, translate or read from the cache, decode LUTs), and any shader error from the log.
Screenshots land in .local/device-tests/slang-shots/<tag>-<preset>.png for a look: a preset that failed shows the
plain picture and an error in the table.
"""

import argparse
import os
import re
import sys
import time
from types import SimpleNamespace

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fold import q  # noqa: E402
from pacing import Pacing, parse_timestats, work_from_log  # noqa: E402

# tag, core id (names the option folder), folder under Roms/, game, presets (relative to the library, no extension).
# None is the run without a shader, the pacing to compare against.
LIGHT = ["crt/crt-geom", "crt/crt-guest-advanced", "crt/newpixie-crt"]
CASES = [
    ("SFC", "snes9x", "Super Nintendo ES (SFC)", "Chrono Trigger (USA).sfc",
     [None] + LIGHT + ["crt/crt-royale", "edge-smoothing/scalefx/scalefx", "motionblur/feedback",
                       "bezel/Mega_Bezel/Presets/MBZ__5__POTATO", "bezel/Mega_Bezel/Presets/MBZ__3__STD",
                       "bezel/Mega_Bezel/Presets/MBZ__0__SMOOTH-ADV"]),
    ("GB", "gambatte", "Game Boy (GB)", "Kirby's Dream Land 2.gb", ["handheld/gameboy", "handheld/lcd-grid-v2"]),
    ("FBN", "fbneo", "Arcade (FBN)", "1942.zip", ["crt/crt-geom", "crt/crt-guest-advanced"]),
    ("N64", "mupen64plus_next", "Nintendo 64 (N64)", "Super Mario 64.z64", [None] + LIGHT),
    ("GC", "dolphin", "Gamecube (GC)", "Mario Kart - Double Dash!! (USA).ciso", [None, "crt/crt-geom", "crt/crt-guest-advanced"]),
    ("PSP", "ppsspp", "Sony Playstation Portable (PSP)", "FIFA Street 2.iso", [None, "crt/crt-geom", "crt/crt-guest-advanced"]),
    ("PS2", "armsx2", "Playstation 2 (PS2)", "Okami (USA).iso", [None, "crt/crt-geom"]),
    ("NDS", "melondsds", "Nintendo DS (NDS)", "Advance Wars - Dual Strike.nds", [None, "crt/crt-geom", "crt/newpixie-crt", "motionblur/feedback"]),
    ("3DS", "azahar", "Nintendo 3DS (3DS)", "Mario Kart 7 (USA) (En,Fr,Es) (Rev 1).cci", [None, "crt/crt-geom", "crt/newpixie-crt"]),
]

ERRORS = re.compile(r"shader .* failed|Slang (shader|program|chain|target)[^\n]*|Preset chain failed[^\n]*|"
                    r"Secondary preset chain failed[^\n]*|FATAL EXCEPTION[^\n]*|Fatal signal[^\n]*")
READY = re.compile(r"slang passes for GLSL ES (\d+) ready in (\d+) ms")


def shot_name(tag, preset):
    return "%s-%s.png" % (tag, (preset or "none").replace("/", "_"))


def run_case(args, tag, core, folder, game, presets, out_dir):
    rom = "%s/Roms/%s/%s" % (args.home, folder, game)
    a = SimpleNamespace(serial=args.serial, verbose=False, home=args.home, tag=tag, core=core, rom=rom, work=True,
                        state=None, slot=0, opt=[], ab=None, ab_prop=None, warmup=args.warmup, seconds=args.seconds,
                        content_fps=60.0, runs=1)
    p = Pacing(a)
    if not p.sh("ls %s 2>/dev/null" % q(rom)).strip():
        print("%-4s skipped: %s is not on the device" % (tag, rom), flush=True)
        return []
    rows = []
    p.prepare()
    try:
        for preset in presets:
            value = "User/%s/%s.slangp" % (args.library, preset) if preset else "None"
            # The game's own options stay as they are; only its shader is replaced.
            kept = [x for x in (p.saved_cfg or "").splitlines() if x.strip() and not x.strip().startswith("nx_shader")]
            p.write_cfg("".join(x + "\n" for x in kept) + "nx_shader = %s\n" % value)
            p.sh("am force-stop com.nxredux.mobile.dev; input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard; "
                 "settings put global stay_on_while_plugged_in 3; logcat -c; logcat -b crash -c")
            row = {"tag": tag, "preset": preset or "(none)", "frames": None, "uneven": None, "held33": None,
                   "ready": "", "error": ""}
            try:
                started = p.dev.game(rom, tag, timeout=120)
                if not started or started.get("state") != "running":
                    row["error"] = "did not start: %s" % (started or {}).get("state", "no answer")
                    rows.append(row)
                    print(format_row(row), flush=True)
                    continue
                # A large preset is translated the first time: wait for it, up to a limit, before measuring.
                deadline = time.time() + args.load_timeout
                log = ""
                while preset and time.time() < deadline:
                    log = p.sh("logcat -d -s NXAPP:* libretrodroid:* AndroidRuntime:* DEBUG:* 2>/dev/null", timeout=60)
                    if READY.search(log) or ERRORS.search(log):
                        break
                    time.sleep(1)
                time.sleep(args.warmup)
                p.sh("dumpsys SurfaceFlinger --timestats -disable; dumpsys SurfaceFlinger --timestats -clear;"
                     " dumpsys SurfaceFlinger --timestats -enable")
                time.sleep(args.seconds)
                stats = p.sh("dumpsys SurfaceFlinger --timestats -dump; dumpsys SurfaceFlinger --timestats -disable",
                             timeout=60)
                remote = "/data/local/tmp/nxtest-slang.png"
                p.sh("screencap -p %s" % remote, timeout=60)
                p.dev._adb(["pull", remote, os.path.join(out_dir, shot_name(tag, preset))], timeout=120, check=False)
                p.sh("rm -f %s" % remote)
                log = p.sh("logcat -d 2>/dev/null | grep -E 'NXAPP|libretrodroid|NXHITCH|FATAL|Fatal signal'", timeout=60)
                row.update(parse_timestats(stats, 1000.0 / 60.0) or {})
                row.update(work_from_log(log) or {})
                ready = READY.search(log)
                if ready:
                    row["ready"] = "%s ms (ES %s)" % (ready.group(2), ready.group(1))
                errors = sorted(set(m.group(0).strip()[:140] for m in ERRORS.finditer(log)))
                crash = p.dev.crash_log()
                if crash:
                    errors.append("crash: " + crash.splitlines()[0][:120])
                row["error"] = "; ".join(errors)
                if not p.dev.game_running():
                    row["error"] = (row["error"] + "; " if row["error"] else "") + "the game is no longer in front"
            finally:
                try:
                    stopped = p.dev.game_stop(timeout=60)
                except Exception:
                    stopped = None
                if not stopped:
                    row["error"] = (row["error"] + "; " if row["error"] else "") + "did not quit: force-stopped"
                p.sh("am force-stop com.nxredux.mobile.dev; settings put global stay_on_while_plugged_in 0")
            rows.append(row)
            print(format_row(row), flush=True)
    finally:
        p.restore()
    return rows


def format_row(r):
    work = "%.2f/%.1f" % (r["work_avg"], r["work_worst"]) if "work_avg" in r else "-"
    return "%-4s %-44s frames %-5s uneven %-4s held33+ %-4s work %-11s ready %-18s %s" % (
        r["tag"], r["preset"][-44:], r["frames"], r["uneven"], r["held33"], work, r["ready"], r["error"])


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--serial", required=True)
    ap.add_argument("--home", default="/sdcard/NXRedux", help="the debug build's home (the Fold's is /sdcard/NXReduxDev)")
    ap.add_argument("--library", default="shaders_slang", help="the slang library's folder under <home>/Shaders")
    ap.add_argument("--only", help="console tags to run, comma separated")
    ap.add_argument("--presets", help="run only these presets (comma separated) on every chosen console")
    ap.add_argument("--warmup", type=float, default=6.0)
    ap.add_argument("--seconds", type=float, default=8.0)
    ap.add_argument("--load-timeout", type=float, default=90.0, help="how long a preset may take to prepare")
    args = ap.parse_args()

    out_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".local", "device-tests", "slang-shots")
    os.makedirs(out_dir, exist_ok=True)
    only = set(args.only.split(",")) if args.only else None
    rows = []
    for tag, core, folder, game, presets in CASES:
        if only and tag not in only:
            continue
        if args.presets:
            presets = [None if x == "none" else x for x in args.presets.split(",")]
        rows += run_case(args, tag, core, folder, game, presets, out_dir)
    print("\n%d runs, %d with errors; screenshots in %s" % (
        len(rows), sum(1 for r in rows if r["error"]), os.path.normpath(out_dir)))
    return 1 if any(r["error"] for r in rows) else 0


if __name__ == "__main__":
    sys.exit(main())
