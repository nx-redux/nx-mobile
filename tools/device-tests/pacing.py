#!/usr/bin/env python3
"""Frame pacing on a phone: launch a game on the debug build, record SurfaceFlinger's frame timing, repeat.

Runs on any phone with the debug build (com.nxredux.mobile.dev) over adb, through the Fold class's GAME hook (no taps).
Each run: optional per-game options, an optional save state loaded from a slot (the same moment every run), a warm-up,
then SurfaceFlinger timestats over the measured window. A/B runs alternate one option or one system property.

    python3 tools/device-tests/pacing.py --serial c2c658b2 --tag WII --core dolphin \\
        --rom "/sdcard/NXRedux/Roms/Nintendo Wii (WII)/Super Mario Galaxy 2 (USA) (En,Fr,Es).wbfs" \\
        --state smg2.state --runs 2 --ab-prop debug.nx.presenttime=0,1

Per run it prints: frames shown, uneven frames (any frame not shown for exactly one content frame at the screen's
rate), frames held 33 ms or more, the core's average/worst work per frame (NXHITCH summaries; --work turns the Debug
log on for the run), and the device temperature. Per-game option files and the Debug log are put back afterwards.
"""
import argparse
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fold import Fold, q  # noqa: E402

PREFS = "shared_prefs/nx-mobile.xml"


def rom_stem(rom):
    return os.path.splitext(os.path.basename(rom))[0]


def state_name(rom, slot):
    """The game's state file for a slot in the default Retroarch naming (slot 0: .state, n: .state<n>)."""
    return rom_stem(rom) + (".state" if slot == 0 else ".state%d" % slot)


def parse_timestats(text, frame_ms):
    """Frames, uneven frames and frames held 33 ms+ of the game's SurfaceView layer, from a timestats dump."""
    on = False
    frames = None
    for i, line in enumerate(text.splitlines()):
        if line.startswith("layerName = ") and "SurfaceView[" in line and "GameActivity" in line:
            on = True
        elif on and line.startswith("totalFrames = "):
            frames = int(line.split("=")[1])
        elif on and line.startswith("present2present"):
            hist = text.splitlines()[i + 1]
            uneven = held = 0
            steady = int(frame_ms)   # SurfaceFlinger buckets by whole ms: 16 for a 16.7 ms frame
            for bucket in hist.split():
                ms, count = bucket.split("=")
                ms, count = int(ms.rstrip("ms")), int(count)
                if count and ms != steady:
                    uneven += count
                    if ms >= 33:
                        held += count
            return {"frames": frames, "uneven": uneven, "held33": held}
    return None


def work_from_log(log):
    """Average and worst core work per frame from the NXHITCH summaries in a log."""
    avgs, worst = [], 0.0
    for m in re.finditer(r"NXHITCH summary .*run_avg=([0-9.]+) run_worst=([0-9.]+)", log):
        avgs.append(float(m.group(1)))
        worst = max(worst, float(m.group(2)))
    if not avgs:
        return None
    return {"work_avg": sum(avgs) / len(avgs), "work_worst": worst}


class Pacing:
    def __init__(self, args):
        self.a = args
        self.dev = Fold(args.serial, verbose=args.verbose)
        self.home = args.home
        self.cfg_dir = "%s/.userdata/android/%s-%s" % (self.home, args.tag, args.core)
        self.cfg = "%s/%s.cfg" % (self.cfg_dir, os.path.basename(args.rom))
        self.states_dir = "%s/.userdata/shared/%s-%s" % (self.home, args.tag, args.core)
        self.saved_cfg = None
        self.saved_prefs = None

    def sh(self, cmd, timeout=120):
        return self.dev.sh(cmd, timeout=timeout)

    # ---- setup and restore --------------------------------------------

    def prepare(self):
        self.dev.require_present()
        self.saved_cfg = self.sh("cat %s 2>/dev/null" % q(self.cfg)) or None
        if self.a.work:
            self.sh("am force-stop com.nxredux.mobile.dev")
            self.saved_prefs = self.dev.run_as("cat %s" % PREFS)
            prefs = self.saved_prefs
            if 'name="debug_log"' in prefs:
                prefs = re.sub(r'<boolean name="debug_log" value="\w+" />', '<boolean name="debug_log" value="true" />', prefs)
            else:
                prefs = prefs.replace("</map>", '    <boolean name="debug_log" value="true" />\n</map>')
            self.write_private(PREFS, prefs)
        if self.a.state:
            self.dev.push(self.a.state, "%s/%s" % (self.states_dir, state_name(self.a.rom, self.a.slot)))

    def write_private(self, rel, text):
        tmp = "/data/local/tmp/nxtest-pacing.txt"
        local = "/tmp/nxtest-pacing-%d.txt" % os.getpid()
        with open(local, "w") as f:
            f.write(text)
        self.dev.push(local, tmp)
        os.remove(local)
        self.dev.run_as("cp %s %s" % (tmp, rel))
        self.sh("rm -f %s" % tmp)

    def restore(self):
        self.sh("am force-stop com.nxredux.mobile.dev")
        if self.saved_cfg is None:
            self.sh("rm -f %s; rmdir %s 2>/dev/null; true" % (q(self.cfg), q(self.cfg_dir)))
        else:
            self.write_cfg(self.saved_cfg)
        if self.saved_prefs is not None:
            self.write_private(PREFS, self.saved_prefs)
        for prop, _ in self.props():
            self.sh("setprop %s ''" % prop)   # cleared: the debug.nx.* switches read unset as on

    def write_cfg(self, text):
        self.sh("mkdir -p %s && printf %%s %s > %s" % (q(self.cfg_dir), q(text), q(self.cfg)))

    def props(self):
        if not self.a.ab_prop:
            return []
        name, values = self.a.ab_prop.split("=", 1)
        return [(name, values.split(","))]

    # ---- one run ------------------------------------------------------

    def run(self, label, option=None, prop=None):
        lines = list(self.a.opt)
        if option:
            lines.append(option)
        if lines:
            self.write_cfg("".join("%s = %s\n" % tuple(x.split("=", 1)) for x in lines))
        elif self.saved_cfg is None:
            self.sh("rm -f %s" % q(self.cfg))
        if prop:
            self.sh("setprop %s %s" % prop)
        self.sh("am force-stop com.nxredux.mobile.dev; input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard; "
                "settings put global stay_on_while_plugged_in 3")
        try:
            self.dev.game(self.a.rom, self.a.tag, slot=self.a.slot if self.a.state else None, timeout=90)
            time.sleep(self.a.warmup)
            self.sh("logcat -c; dumpsys SurfaceFlinger --timestats -disable; dumpsys SurfaceFlinger --timestats -clear;"
                    " dumpsys SurfaceFlinger --timestats -enable")
            time.sleep(self.a.seconds)
            stats = self.sh("dumpsys SurfaceFlinger --timestats -dump; dumpsys SurfaceFlinger --timestats -disable",
                            timeout=60)
            log = self.sh("logcat -d | grep -E 'NXHITCH summary|SET_HW_RENDER: Vulkan accepted'", timeout=60)
            temp = self.sh("cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null").strip()
        finally:
            self.sh("am force-stop com.nxredux.mobile.dev; settings put global stay_on_while_plugged_in 0")
        frame_ms = 1000.0 / self.a.content_fps
        result = parse_timestats(stats, frame_ms) or {"frames": None, "uneven": None, "held33": None}
        result.update(work_from_log(log) or {})
        result["vulkan"] = "Vulkan accepted" in log
        result["temp"] = "%.1f" % (int(temp) / 1000) if temp.isdigit() else "?"
        result["label"] = label
        print(format_run(result), flush=True)
        return result

    def all_runs(self):
        results = []
        variants = [(None, None)]
        if self.a.ab:
            key, values = self.a.ab.split("=", 1)
            variants = [("%s=%s" % (key, v), None) for v in values.split(",")]
        elif self.a.ab_prop:
            name, values = self.a.ab_prop.split("=", 1)
            variants = [(None, (name, v)) for v in values.split(",")]
        for n in range(1, self.a.runs + 1):
            for option, prop in variants:   # alternated, so warming affects every variant alike
                label = option or ("%s=%s" % prop if prop else "run")
                results.append(self.run("%s #%d" % (label, n), option, prop))
        return results


def format_run(r):
    work = (" work %.2f ms avg, %.1f worst" % (r["work_avg"], r["work_worst"])) if "work_avg" in r else ""
    return "%-32s frames %-5s uneven %-4s held33+ %-3s%s%s temp %s" % (
        r["label"], r["frames"], r["uneven"], r["held33"], work, " vulkan" if r["vulkan"] else "", r["temp"])


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--serial", required=True)
    p.add_argument("--rom", required=True, help="the ROM's path on the device")
    p.add_argument("--tag", required=True, help="console tag, e.g. WII, GC, GBA")
    p.add_argument("--core", required=True, help="core id, e.g. dolphin (names the per-game option folder)")
    p.add_argument("--home", default="/sdcard/NXRedux", help="the debug build's home (the Fold's is /sdcard/NXReduxDev)")
    p.add_argument("--state", help="a local state file to load each run (pushed to --slot)")
    p.add_argument("--slot", type=int, default=0)
    p.add_argument("--warmup", type=float, default=25)
    p.add_argument("--seconds", type=float, default=60)
    p.add_argument("--runs", type=int, default=1, help="rounds; with --ab/--ab-prop each round runs every variant")
    p.add_argument("--opt", action="append", default=[], help="per-game option key=value for every run (repeatable)")
    p.add_argument("--ab", help="alternate one option: key=valueA,valueB")
    p.add_argument("--ab-prop", help="alternate one system property: debug.nx.presenttime=0,1")
    p.add_argument("--content-fps", type=float, default=60.0, help="the game's frame rate (steady frame length)")
    p.add_argument("--work", action="store_true", help="turn the Debug log on for the runs (core work per frame)")
    p.add_argument("--verbose", action="store_true")
    a = p.parse_args()

    pacing = Pacing(a)
    pacing.prepare()
    try:
        pacing.all_runs()
    finally:
        pacing.restore()


if __name__ == "__main__":
    main()
