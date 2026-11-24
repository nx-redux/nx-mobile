#!/usr/bin/env python3
"""Sega netplay pads, phone <-> phone: both sides play the host's Controller Type, and never drift apart.

Each case runs one Wi-Fi session through the debug NETPLAY hook (the real handshake, then the game) with a Controller
Type offered on each side, and checks:
- pad.json on both phones (written at the launch): the session's pad, the drawn pad and the libretro device on ports
  0-1 match each other and the expected values;
- the drift check (debug.nx.netplay_pair=1 for the run: both sides hash the console's work RAM every 120 frames): no
  "pair: hashes differ", and "pair: verified frame" lines so the checks did run. The first case, control-mismatch, makes
  the client play another pad than the host; it passes only when the drift check catches that.

Needs the debug build on both phones, Debug log on (the engine logs only then), both on the same Wi-Fi, and the same ROM
files (md5 checked first). Every adb call passes -s. The property is cleared at the end.

    python3 tools/device-tests/netplay_pads.py \\
        --host R5GL66XJS2F:"/sdcard/NXRedux/Roms/Sega Genesis (MD)" \\
        --client c2c658b2:"/storage/3933-6330/Roms/Sega Genesis" [--seconds 45] [--only pico-6,old-host]

`--smoke TAG:core:game` (the Roms folders then hold that console's games): one session of another console, drift check
only (any lockstep core, e.g. SFC:snes9x:Contra.sfc).
"""

import argparse
import datetime
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from brick import q  # noqa: E402
from fold import Fold  # noqa: E402

SIX_GAME = "Super Street Fighter II.md"   # header I/O "J6": a 6-button game
THREE_GAME = "Streets of Rage 2.md"       # header I/O "J": 3 buttons
HEADER = {SIX_GAME: "SIX", THREE_GAME: "THREE"}
PICO, GPGX = "picodrive", "genesis_plus_gx"
DEVICE = {"THREE": 257, "SIX": 513}       # RETRO_DEVICE_SUBCLASS(JOYPAD, 0 / 1)
JOYPAD = 1
PROP = "debug.nx.netplay_pair"

# name, core, game, host's pad, client's pad ("none": that side sends no pad=, as the handheld or an older phone)
CASES = [
    # The control: the client plays 3 buttons against the host's 6 (force_pad). The drift check must catch it, or a pass
    # below proves nothing.
    ("control-mismatch", PICO, SIX_GAME, "6", "auto"),
    ("pico-6", PICO, SIX_GAME, "6", "auto"),
    ("pico-3", PICO, SIX_GAME, "3", "6"),
    ("pico-auto-6b", PICO, SIX_GAME, "auto", "3"),
    ("pico-auto-3b", PICO, THREE_GAME, "auto", "auto"),
    ("pico-6-on-3b", PICO, THREE_GAME, "6", "auto"),
    ("gpgx-6", GPGX, THREE_GAME, "6", "auto"),
    ("gpgx-3", GPGX, SIX_GAME, "3", "auto"),
    ("gpgx-auto", GPGX, SIX_GAME, "auto", "auto"),
    ("old-joiner", PICO, SIX_GAME, "6", "none"),
    ("old-host", PICO, SIX_GAME, "none", "6"),
]


def expected(core, game, host_pad, client_pad):
    """(session pad, drawn pad, port device) both phones should report."""
    session = host_pad if host_pad != "none" and client_pad != "none" else None
    if session is None:   # the old fixed pad: picodrive's 3-button pad, genesis_plus_gx's header-picked joypad
        return (None, "THREE", DEVICE["THREE"]) if core == PICO else (None, HEADER[game], JOYPAD)
    if session in ("3", "6"):
        drawn = "THREE" if session == "3" else "SIX"
        return session, drawn, DEVICE[drawn]
    return (session, HEADER[game], DEVICE[HEADER[game]]) if core == PICO else (session, HEADER[game], JOYPAD)


class Side:
    def __init__(self, spec, label):
        serial, _, roms = spec.partition(":")
        self.phone = Fold(serial, verbose=False)
        # Android 11 (the AYANEO) drops adb broadcasts to a force-stopped app: the hooks go as activity starts there.
        self.phone.activity_hooks = int(self.phone.sh("getprop ro.build.version.sdk").strip() or 0) <= 30
        self.roms = roms.rstrip("/")
        self.label = label

    def sh(self, cmd):
        return self.phone.sh(cmd)

    def rom(self, game):
        return self.roms + "/" + game

    def ip(self):
        m = re.search(r"inet (\d+\.\d+\.\d+\.\d+)", self.sh("ip -4 addr show wlan0"))
        return m.group(1) if m else None

    def ensure_wifi(self, timeout=60):
        """The AYANEO drops Wi-Fi while its screen sleeps: wake it, swipe the lock screen up and wait for an address."""
        if self.ip():
            return
        w, h = (int(v) for v in re.search(r"(\d+)x(\d+)", self.sh("wm size")).groups())
        self.sh("input keyevent KEYCODE_WAKEUP")
        time.sleep(1)
        self.sh("input swipe %d %d %d %d 300" % (w // 2, h * 9 // 10, w // 2, h // 5))
        if not Fold.wait_for(self.ip, timeout, interval=2):
            raise SystemExit("%s: no Wi-Fi address after waking it" % self.label)

    def md5(self, game):
        return self.sh("md5sum %s" % q(self.rom(game))).split()[0]

    def stop_game(self):
        if self.phone.game_running():
            try:
                self.phone.game_stop(timeout=20)
            except Exception:
                pass
        self.sh("am force-stop com.nxredux.mobile.dev")

    def netplay(self, role, core, game, pad, peer=None, force_pad=None, tag="MD"):
        extras = "--es role %s --es path %s --es tag %s --es core %s --es pad %s" % (role, q(self.rom(game)), tag, core, pad)
        if peer:
            extras += " --es peer %s" % peer
        if force_pad:
            extras += " --es force_pad %s" % force_pad
        self.phone.broadcast("NETPLAY", extras, background_start=True)

    def wait_json(self, name, ok, timeout):
        return Fold.wait_for(lambda: (lambda j: j if j and ok(j) else None)(self.phone.read_json(name)), timeout)

    def engine_log(self):
        return self.sh("logcat -d -s nx-netplay:I")


def run_case(case, host, client, seconds):
    name, core, game, host_pad, client_pad = case
    for s in (host, client):
        s.ensure_wifi()
    control = name == "control-mismatch"
    want = expected(core, game, host_pad, client_pad)
    for s in (host, client):
        s.stop_game()
        for r in ("netplay", "pad", "game"):
            s.phone.rm_result(r)
        s.sh("logcat -c")
    host.netplay("host", core, game, host_pad)
    if not host.wait_json("netplay", lambda j: j.get("state") in ("listening", "refused"), 15):
        return name, False, "host never listened"
    client.netplay("client", core, game, client_pad, peer=host.ip(), force_pad="3" if control else None)
    started = [s.wait_json("netplay", lambda j: j.get("state") in ("started", "refused"), 60) for s in (host, client)]
    if not all(j and j.get("state") == "started" for j in started):
        return name, False, "handshake: host %s, client %s" % tuple(started)
    pads = [s.wait_json("pad", lambda j: True, 60) for s in (host, client)]
    if not all(pads):
        return name, False, "no pad.json (host %s, client %s)" % tuple(pads)
    time.sleep(seconds)
    logs = [s.engine_log() for s in (host, client)]
    for s in (client, host):
        s.stop_game()
    differ = sum(log.count("pair: hashes differ") for log in logs)
    verified = sum(log.count("pair: verified frame") for log in logs)
    if control:
        ok = differ > 0 and pads[1].get("drawn") == "THREE" and pads[0].get("drawn") == "SIX"
        return name, ok, "caught: %d hash mismatch(es)" % differ if ok else "NOT caught (pads %s / %s): the drift check sees no pad mismatch" % tuple(pads)
    problems = []
    for s, p in zip((host, client), pads):
        got = (p.get("session"), p.get("drawn"), p.get("port0"))
        if got != want or p.get("port1") != want[2]:
            problems.append("%s pad %s port1 %s, want %s" % (s.label, got, p.get("port1"), want))
    if differ:
        problems.append("drift: %d hash mismatch(es)" % differ)
    if not verified:
        problems.append("no verified frames (Debug log off, or the check never ran)")
    detail = "; ".join(problems) or "pad %s/%s/%s on both, %d verified checks" % (want + (verified,))
    return name, not problems, detail


def run_smoke(tag, core, game, host, client, seconds):
    for s in (host, client):
        s.ensure_wifi()
    for s in (host, client):
        s.stop_game()
        for r in ("netplay", "game"):
            s.phone.rm_result(r)
        s.sh("logcat -c")
    host.netplay("host", core, game, "none", tag=tag)
    if not host.wait_json("netplay", lambda j: j.get("state") in ("listening", "refused"), 15):
        return False, "host never listened"
    client.netplay("client", core, game, "none", peer=host.ip(), tag=tag)
    started = [s.wait_json("netplay", lambda j: j.get("state") in ("started", "refused"), 60) for s in (host, client)]
    if not all(j and j.get("state") == "started" for j in started):
        return False, "handshake: host %s, client %s" % tuple(started)
    time.sleep(seconds)
    logs = [s.engine_log() for s in (host, client)]
    for s in (client, host):
        s.stop_game()
    differ = sum(log.count("pair: hashes differ") for log in logs)
    verified = sum(log.count("pair: verified frame") for log in logs)
    ended = [l for log in logs for l in log.splitlines() if "session ended" in l and "peer left" not in l and "none" not in l]
    problems = (["drift: %d hash mismatch(es)" % differ] if differ else []) + \
        ([] if verified else ["no verified frames"]) + (["ended early: " + ended[0].split("nx-netplay: ")[-1]] if ended else [])
    return not problems, "; ".join(problems) or "%d verified checks, no drift" % verified


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--host", required=True, help="serial:Roms folder holding the MD games")
    ap.add_argument("--client", required=True, help="serial:Roms folder holding the MD games")
    ap.add_argument("--seconds", type=int, default=45, help="play time per case (default 45)")
    ap.add_argument("--only", help="comma-separated case names")
    ap.add_argument("--smoke", help="TAG:core:game, one session of another console (drift check only)")
    args = ap.parse_args()
    host, client = Side(args.host, "host"), Side(args.client, "client")
    if args.smoke:
        tag, core, game = args.smoke.split(":", 2)
        if host.md5(game) != client.md5(game):
            raise SystemExit("%s differs between the phones" % game)
        try:
            for s in (host, client):
                s.sh("setprop %s 1" % PROP)
            ok, detail = run_smoke(tag, core, game, host, client, args.seconds)
        finally:
            for s in (host, client):
                s.sh("setprop %s 0" % PROP)
                s.stop_game()
        print("%s %s  %s" % (tag, "PASS" if ok else "FAIL", detail))
        sys.exit(0 if ok else 1)
    cases = [c for c in CASES if not args.only or c[0] in args.only.split(",")]
    for game in sorted({c[2] for c in cases}):
        if host.md5(game) != client.md5(game):
            raise SystemExit("%s differs between the phones" % game)
    results = []
    try:
        for s in (host, client):
            s.sh("setprop %s 1" % PROP)
        for case in cases:
            print("== %s ..." % case[0], flush=True)
            r = run_case(case, host, client, args.seconds)
            print("   %s  %s" % ("PASS" if r[1] else "FAIL", r[2]), flush=True)
            results.append(r)
    finally:
        for s in (host, client):
            s.sh("setprop %s 0" % PROP)
            s.stop_game()
    out_dir = ".local/device-tests"
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, "netplay-pads-%s.md" % datetime.date.today().isoformat())
    with open(path, "w") as f:
        f.write("# Sega netplay pads, %s\n\nhost %s, client %s, %d s a case\n\n| Case | Result | Detail |\n| --- | --- | --- |\n"
                % (datetime.datetime.now().strftime("%Y-%m-%d %H:%M"), host.phone.serial, client.phone.serial, args.seconds))
        for name, ok, detail in results:
            f.write("| %s | %s | %s |\n" % (name, "PASS" if ok else "FAIL", detail))
    print("report: " + path)
    sys.exit(0 if all(r[1] for r in results) else 1)


if __name__ == "__main__":
    main()
