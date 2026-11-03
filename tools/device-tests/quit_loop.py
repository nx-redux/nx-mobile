#!/usr/bin/env python3
"""Boot a game, let it run, quit it, again and again: catches hangs on quit (the Wii GPU-loop race froze ~1 boot in 6).

Debug build only (GAME and GAME_STOP hooks; GAME_STOP quits as the menu's Quit does, writing the auto state). A run
passes when the game was running, its :game process is gone within --close seconds of the quit, and neither an ANR
nor the core watchdog showed up in the log.

    python3 tools/device-tests/quit_loop.py --serial c2c658b2 --tag WII \\
        --rom "/sdcard/NXRedux/Roms/Nintendo Wii (WII)/New Super Mario Bros. Wii (USA) (En,Fr,Es) (Rev 2).wbfs" --runs 10
"""
import argparse
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fold import Fold, FoldError, q  # noqa: E402

GAME_PROCESS = "com.nxredux.mobile.dev:game"


def one_run(dev, a, n):
    dev.sh("am force-stop com.nxredux.mobile.dev; input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard; "
           "logcat -c; logcat -b events -c")
    try:
        dev.game(a.rom, a.tag, timeout=90)
    except FoldError as e:
        return "run %d: FAIL did not start (%s)" % (n, e), False
    time.sleep(a.boot)
    running = bool(dev.sh("pidof %s" % GAME_PROCESS).strip())
    dev.broadcast("GAME_STOP")
    t0 = time.time()
    while time.time() - t0 < a.close and dev.sh("pidof %s" % GAME_PROCESS).strip():
        time.sleep(0.5)
    closed = time.time() - t0
    alive = bool(dev.sh("pidof %s" % GAME_PROCESS).strip())
    anr = int(dev.sh("logcat -d -b events | grep -c am_anr").strip() or 0)
    watchdog = int(dev.sh("logcat -d | grep -c -E 'stuck in one frame|stopped responding'").strip() or 0)
    ok = running and not alive and anr == 0 and watchdog == 0
    return ("run %d: %s running=%s closed_after=%.1fs still_alive=%s anr=%d watchdog=%d"
            % (n, "ok  " if ok else "FAIL", running, closed, alive, anr, watchdog)), ok


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--serial", required=True)
    p.add_argument("--rom", required=True)
    p.add_argument("--tag", required=True)
    p.add_argument("--runs", type=int, default=10)
    p.add_argument("--boot", type=float, default=15, help="seconds the game runs before the quit")
    p.add_argument("--close", type=float, default=10, help="seconds the process may take to end")
    a = p.parse_args()

    dev = Fold(a.serial, verbose=False)
    dev.require_present()
    passed = 0
    for n in range(1, a.runs + 1):
        line, ok = one_run(dev, a, n)
        passed += ok
        print(line, flush=True)
    print("%d/%d passed" % (passed, a.runs))
    sys.exit(0 if passed == a.runs else 1)


if __name__ == "__main__":
    main()
