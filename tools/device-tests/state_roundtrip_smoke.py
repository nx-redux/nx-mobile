#!/usr/bin/env python3
"""Brick-only smoke test of the state recipes (no phone involved).

Copies the smallest GBA ROM the Brick has to nxtest-gba.gba (same folder),
boots it with the GBA pak (gpsp), plays ~15 s, sleeps so minarch writes the
slot-9 auto state, kills the game, then resumes slot 9 and checks the state
loaded: resume file consumed, no state error in the log, minarch still up.
Every file it creates is named nxtest-* and removed at the end.

usage: state_roundtrip_smoke.py [--serial SERIAL] [--out DIR]
"""

import argparse
import os
import sys
import time

from brick import (AUTO_RESUME_PATH, DEFAULT_SERIAL, ERROR_MARKERS, RESUME_SLOT_PATH, SDCARD,
                   Brick, BrickGone, q)

GBA_DIR = SDCARD + "/Roms/Game Boy Advance (GBA)"
TAG, CORE = "GBA", "gpsp"
ROM_NAME = "nxtest-gba.gba"


def smallest_rom(b):
    out = b.sh("cd %s && for f in *.gba; do [ -f \"$f\" ] && echo \"$(wc -c < \"$f\")|$f\"; done" % q(GBA_DIR))
    roms = []
    for line in out.split("\n"):
        size, _, name = line.partition("|")
        if size.strip().isdigit() and name and not name.startswith("nxtest-"):
            roms.append((int(size), name))
    if not roms:
        raise SystemExit("FAIL: no .gba ROM in %s" % GBA_DIR)
    return min(roms)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", default=DEFAULT_SERIAL)
    ap.add_argument("--out", default=os.path.join(os.getcwd(), "nxtest-out"))
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)

    b = Brick(args.serial)
    evidence, failures = [], []

    def check(ok, what):
        (evidence if ok else failures).append(("ok   " if ok else "FAIL ") + what)
        print(("  ok   " if ok else "  FAIL ") + what, flush=True)
        return ok

    b.require_present()
    if b.minarch_pid() or not b.nextui_alive():
        raise SystemExit("FAIL: the Brick must be on the NextUI menu (no game running)")
    if b.exists(AUTO_RESUME_PATH):
        raise SystemExit("FAIL: %s exists (a real game's auto-resume); refusing to overwrite it" % AUTO_RESUME_PATH)
    if "raHardcoreMode=1" in b.sh("cat %s/minuisettings.txt" % q(SDCARD + "/.userdata/shared")):
        raise SystemExit("FAIL: RetroAchievements hardcore mode is on: minarch blocks state loads")
    b.wake()

    rom =GBA_DIR + "/" + ROM_NAME
    state = Brick.state_path(TAG, CORE, ROM_NAME)
    try:
        stale = b.cleanup_nxtest()
        if stale:
            print("removed stale test files: %s" % stale)
        b.ensure_events()

        size, src = smallest_rom(b)
        print("source ROM: %s (%d bytes) -> %s" % (src, size, rom))
        b.sh("cp %s %s && sync" % (q(GBA_DIR + "/" + src), q(rom)))
        check(b.md5(rom) == b.md5(GBA_DIR + "/" + src), "ROM copied on-device (md5 match)")

        # 1. fresh boot, play, sleep -> auto state
        pid = b.launch_rom(TAG, rom)
        check(pid is not None, "first boot: minarch pid %s" % pid)
        for _ in range(3):  # ~15 s of play, poking START/A so the game moves on
            time.sleep(4)
            b.press("START")
            b.press("A")
        b.screenshot(os.path.join(args.out, "nxtest-1-before-sleep.png"))
        changed = b.sleep_autosave(state)
        st_size = b.size(state)
        check(changed and st_size, "sleep wrote %s (%s bytes)" % (state, st_size))
        check(not b.exists(AUTO_RESUME_PATH), "wake removed auto_resume.txt")
        check(b.minarch_pid() == pid, "game survived sleep/wake")
        st_md5 = b.md5(state)
        b.kill_game()
        check(b.nextui_alive() and not b.minarch_pid(), "kill -9 minarch: back on the menu")

        # 2. resume slot 9
        b.set_resume_slot(9)
        pid2 = b.launch_rom(TAG, rom)
        check(pid2 is not None, "second boot with resume_slot 9: minarch pid %s" % pid2)
        time.sleep(3)
        b.screenshot(os.path.join(args.out, "nxtest-2-resumed-3s.png"))
        time.sleep(7)
        check(not b.exists(RESUME_SLOT_PATH), "%s consumed" % RESUME_SLOT_PATH)
        log = b.game_log(TAG)
        bad = [l for l in log.split("\n") if any(m in l for m in ERROR_MARKERS)]
        check(not bad, "no state error in %s/%s.txt%s" % (b.logs_path(), TAG,
                                                         "" if not bad else ": " + " | ".join(bad)))
        check("Resume state not accepted yet" not in log, "state accepted on the first try")
        check(b.minarch_pid() == pid2, "minarch still running 10 s after resume")
        check(b.md5(state) == st_md5, "auto state untouched by the resume")
        b.screenshot(os.path.join(args.out, "nxtest-3-resumed-10s.png"))
        with open(os.path.join(args.out, "nxtest-GBA-log.txt"), "w") as f:
            f.write(log)
        b.kill_game()

        # 3. negative control: an empty state must show up as a log error,
        #    so the check above can fail
        b.sh("rm -f %s && touch %s && sync" % (q(state), q(state)))
        b.set_resume_slot(9)
        b.launch_rom(TAG, rom)
        time.sleep(6)
        log = b.game_log(TAG)
        bad = [l for l in log.split("\n") if any(m in l for m in ERROR_MARKERS)]
        check(bool(bad), "negative control: empty state is reported (%s)" % (bad[0].strip() if bad else "no error line"))
        b.kill_game()
    except BrickGone as e:
        print("STOP: %s" % e)
        return 2
    except Exception as e:  # report, then still clean up
        failures.append("FAIL exception: %r" % e)
        print("  FAIL exception: %r" % e)
        try:
            if b.minarch_pid():
                b.kill_game()
        except Exception as e2:
            print("  could not end the game: %r" % e2)
    finally:
        if b.present():
            if b.minarch_pid():
                b.kill_game()
            removed = b.cleanup_nxtest()
            print("cleanup removed %d nxtest file(s): %s" % (len(removed), removed))
            left = b.sh("find %s %s /tmp -xdev -name 'nxtest-*'" % (q(GBA_DIR), q(SDCARD + "/.userdata")))
            print("nxtest left on device: %s" % (left.strip() or "none"))
            print("nextui alive: %s" % b.nextui_alive())

    print("\n%s  (%d checks ok, %d failed; screenshots + log in %s)"
          % ("PASS" if not failures else "FAIL", len(evidence), len(failures), args.out))
    return 0 if not failures else 1


if __name__ == "__main__":
    sys.exit(main())
