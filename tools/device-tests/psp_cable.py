"""PSP ad hoc over the phone-to-phone USB cable: the checks that need a pull, a plug or an idle phone, hands-free.

Two phones with the debug build, joined by the cable and driven over wireless adb (their USB ports are taken). The
`--switch` phone is the one whose USB data can be switched from the shell (`svc usb enableUsbDataSignal`, Android 14+):
off is a pull, on is a plug. Each phone needs a save state of the game one Cross press away from its ad hoc lobby
(Tekken 6: NETWORK with BATTLE LOBBY selected) in `--slot`; Circle then Cross must leave the lobby (Circle asks, Cross confirms).

    psp_cable.py --host SERIAL:ROM --switch SERIAL:ROM:CROSS_XY:CIRCLE_XY [--only pull,replug] [--slot 7]

A phone's buttons are pressed as gamepad keys, or as taps on its on-screen pad when CROSS_XY and CIRCLE_XY are given
(`958,1634`). Checks: `pull`, `replug`, `idle` (the other phone not in a game, with what it shows), `late` (a cable
plugged into a running lobby), `nocable`. Passes and fails are judged on the link's log lines; the report goes to
`.local/device-tests/psp-cable-<date>.md` with a screenshot per check. The game's resume point and the phones' screen
timeout are put back, and the switch phone's USB data is always left on.
"""

import argparse
import datetime
import os
import re
import sys
import time

from brick import q
from fold import Fold, FoldError

LINES = r"PSP link|NxPspLink|AdhocServer|sceNetAdhocctl(Init|Connect|Term)|NxDsCable|NxUsbLink|FATAL EXCEPTION|Fatal signal"
USB_DIALOGS = ("UsbPermissionActivity", "UsbResolverActivity", "UsbConfirmActivity")
STAMP = re.compile(r"^\d\d-\d\d (\d\d):(\d\d):(\d\d)\.(\d\d\d)")


def seconds(line):
    m = STAMP.match(line)
    return int(m.group(1)) * 3600 + int(m.group(2)) * 60 + int(m.group(3)) + int(m.group(4)) / 1000.0 if m else None


class Phone:
    def __init__(self, spec, home, slot, name):
        parts = spec.split(":")
        # A wireless serial is IP:PORT, so the ROM is the first part that is a path.
        at = next(i for i, p in enumerate(parts) if p.startswith("/"))
        self.dev = Fold(":".join(parts[:at]), verbose=False, activity_hooks=True)
        self.rom = parts[at]
        taps = parts[at + 1:]
        self.taps = {"cross": taps[0], "circle": taps[1]} if len(taps) == 2 else None
        self.name, self.slot = name, slot
        stem = os.path.splitext(os.path.basename(self.rom))[0]
        states = "%s/.userdata/shared/PSP-ppsspp" % home
        minui = "%s/.userdata/shared/.minui/PSP/%s" % (home, os.path.basename(self.rom))
        self.state = "%s/%s.state%s" % (states, stem, slot if slot else "")
        self.auto = ["%s/%s.state.auto" % (states, stem), minui + ".9.bmp", minui + ".txt"]
        self.timeout = None
        self.dialogs_seen = []

    def sh(self, cmd, timeout=60):
        return self.dev.sh(cmd, timeout=timeout)

    def prepare(self):
        self.dev.require_present()
        if "Awake" not in self.sh("dumpsys power | grep mWakefulness="):
            raise FoldError("%s is asleep: wake and unlock it" % self.name)
        if "No such file" in self.sh("ls %s 2>&1" % q(self.state)):
            raise FoldError("%s has no state in slot %d (%s)" % (self.name, self.slot, self.state))
        self.timeout = self.sh("settings get system screen_off_timeout").strip()
        self.sh("settings put system screen_off_timeout 1800000")
        # A busy phone's default buffer keeps under a minute of lines.
        self.sh("logcat -G 10M")
        self.sh("; ".join("if [ -e %s ]; then mv %s %s; fi" % (q(f), q(f), q(f + ".nxcable")) for f in self.auto))

    def restore(self):
        self.stop()
        self.sh("; ".join("rm -f %s; if [ -e %s ]; then mv %s %s; fi" % (q(f), q(f + ".nxcable"), q(f + ".nxcable"), q(f))
                          for f in self.auto))
        if self.timeout and self.timeout.isdigit():
            self.sh("settings put system screen_off_timeout %s" % self.timeout)

    def launch(self):
        """The GAME hook, twice if need be; a result older than the launch is a previous game's and is waited out."""
        g = None
        for _ in range(2):
            since = self.clock() * 1000
            self.dev.rm_result("game")
            self.dev.broadcast("GAME", "--es path %s --es tag PSP --ei slot %d" % (q(self.rom), self.slot))
            end = time.time() + 40
            while time.time() < end:
                g = self.dev.read_json("game")
                if g and g.get("state") == "running" and g.get("ts", 0) >= since:
                    return
                # The cable starts with the game: Android's USB dialog covers it and the game waits behind it.
                dialog = self.answer_usb_dialog()
                if dialog:
                    self.dialogs_seen.append(dialog)
                time.sleep(1)
        raise FoldError("%s: the game did not start (%s, front %s)" % (self.name, g, self.front()))

    def stop(self):
        if self.dev.game_running():
            self.dev.game_stop()

    def press(self, button):
        if self.taps:
            x, y = self.taps[button].split(",")
            self.sh("input swipe %s %s %s %s 250" % (x, y, x, y))
        else:
            # A plain keyevent is over inside a frame and the game misses it.
            self.sh("input gamepad keyevent --longpress %d" % {"cross": 97, "circle": 96}[button])

    def front(self):
        out = self.sh("dumpsys activity activities | grep -E '  ResumedActivity' | head -1")
        m = re.search(r"/([\w.$]+)[ }]", out)
        return m.group(1).split(".")[-1] if m else out.strip()

    def answer_usb_dialog(self):
        """Android's USB permission or open-with dialog, when it is up: taps its positive button. Returns its name."""
        front = self.front()
        if front not in USB_DIALOGS:
            return None
        self.sh("uiautomator dump /sdcard/nxtest-ui.xml >/dev/null 2>&1")
        xml = self.sh("cat /sdcard/nxtest-ui.xml; rm -f /sdcard/nxtest-ui.xml")
        nodes = re.findall(r"<node [^>]*>", xml)
        pick = [n for n in nodes if 'resource-id="android:id/button1"' in n] or \
               [n for n in nodes if re.search(r'text="(OK|Allow|Just once|Always)"', n, re.I)]
        if pick:
            x1, y1, x2, y2 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', pick[0]).groups())
            self.sh("input tap %d %d" % ((x1 + x2) // 2, (y1 + y2) // 2))
        return front + ("" if pick else " (no button found)")

    def clear(self):
        self.sh("logcat -c")

    def log(self):
        # adbd logs each shell command, this one's pattern included.
        return [l for l in self.sh("logcat -d -v time | grep -E %s" % q(LINES)).splitlines() if STAMP.match(l) and "/adbd" not in l]

    def has(self, pattern, since=0.0):
        return [l for l in self.log() if re.search(pattern, l) and seconds(l) >= since]

    def clock(self):
        """The phone's epoch seconds; wireless adb sometimes answers nothing, so asked up to five times."""
        for _ in range(5):
            out = self.sh("date +%s").strip()
            if out.isdigit():
                return int(out)
            time.sleep(2)
        raise FoldError("%s does not answer on adb" % self.name)

    def now(self):
        """The phone's time of day in seconds, as its log lines stamp it."""
        for _ in range(5):
            parts = self.sh("date +%H:%M:%S").strip().split(":")
            if len(parts) == 3 and all(x.isdigit() for x in parts):
                return int(parts[0]) * 3600 + int(parts[1]) * 60 + int(parts[2])
            time.sleep(2)
        raise FoldError("%s does not answer on adb" % self.name)

    def wait(self, pattern, timeout, since=0.0):
        end = time.time() + timeout
        while True:
            hit = self.has(pattern, since)
            if hit or time.time() >= end:
                return hit[0] if hit else None
            time.sleep(1)

    def shot(self, path):
        self.sh("screencap -p /sdcard/nxtest-shot.png 2>/dev/null")
        self.dev.pull("/sdcard/nxtest-shot.png", path)
        self.sh("rm -f /sdcard/nxtest-shot.png")


class Run:
    def __init__(self, args):
        self.host = Phone(args.host, args.home, args.slot, "host")
        self.switch = Phone(args.switch, args.home, args.slot, "switch")
        self.both = (self.host, self.switch)
        self.out = args.out
        self.rows = []          # (check, step, verdict, detail)
        self.seen = set()
        self.check = None

    def note(self, step, ok, detail=""):
        verdict = ok if isinstance(ok, str) else ("PASS" if ok else "FAIL")
        self.rows.append((self.check, step, verdict, detail))
        print("  %-4s %s%s" % (verdict, step, (": " + detail) if detail else ""), flush=True)

    def cable(self, on):
        self.switch.sh("svc usb enableUsbDataSignal %s" % ("true" if on else "false"))

    def start(self, phones):
        for p in self.both:
            p.stop()
            p.clear()
        for p in phones:
            p.dialogs_seen = []
            p.launch()
            if p.dialogs_seen:
                self.note("USB dialog on %s at game start" % p.name, "INFO", ", ".join(sorted(set(p.dialogs_seen))))

    def cable_up(self, phones, timeout=60):
        """Waits for the link on [phones]; a USB dialog Android leaves up is reported, since nothing here may answer it."""
        end = time.time() + timeout
        while time.time() < end:
            if all(p.has(r"PSP link: cable up") for p in phones):
                return True
            self.dialogs()
            time.sleep(2)
        shown = ["%s shows %s" % (p.name, p.front()) for p in self.both if p.front() in USB_DIALOGS]
        self.note("cable up on both", False, "; ".join(shown) or "no link in %d s" % timeout)
        return False

    def dialogs(self):
        """Answers a USB dialog on either phone and records that it came up."""
        for p in self.both:
            name = p.answer_usb_dialog()
            if name and (p.name, name) not in self.seen:
                self.seen.add((p.name, name))
                self.note("USB dialog on %s" % p.name, "INFO", name)

    def press(self, phone, button, proof, since):
        """A press the game must show in its log: a USB dialog over the game swallows a press, so up to three tries."""
        for _ in range(3):
            self.dialogs()
            phone.press(button)
            if button == "circle":
                # The game asks before leaving its lobby; Cross is its Yes. Pressed only while the question is up:
                # a second Circle would answer No.
                time.sleep(1.5)
                phone.press("cross")
            if phone.wait(proof, 6, since):
                return True
        return False

    def settle(self, secs):
        """A wait that keeps answering USB dialogs."""
        end = time.time() + secs
        while time.time() < end:
            self.dialogs()
            time.sleep(2)

    def enter(self, phones, gap=6):
        marks = {p: p.now() for p in phones}
        for i, p in enumerate(phones):
            if i:
                time.sleep(gap)
            self.press(p, "cross", r"sceNetAdhocctlConnect", marks[p])
        ok = all(p.wait(r"PSP link: game in a group", 25, marks[p]) for p in phones)
        time.sleep(3)   # the lobby takes no press while it opens
        return ok

    def leave(self, phones):
        marks = {p: p.now() for p in phones}
        for p in phones:
            self.press(p, "circle", r"PSP link: game left ad hoc", marks[p])
        ok = all(p.wait(r"PSP link: game left ad hoc", 15, marks[p]) for p in phones)
        time.sleep(3)   # the menu takes no press while it slides back in
        return ok

    def crashes(self):
        bad = [p.name for p in self.both if p.has(r"FATAL EXCEPTION|Fatal signal")]
        self.note("no crash", not bad, ", ".join(bad))

    def finish(self):
        for p in self.both:
            try:
                p.shot(os.path.join(self.out, "psp-cable-%s-%s.png" % (self.check, p.name)))
            except FoldError:
                pass
            with open(os.path.join(self.out, "psp-cable-%s-%s.log" % (self.check, p.name)), "w") as f:
                f.write("\n".join(p.log()) + "\n")
        self.crashes()
        for p in self.both:
            p.stop()

    def lobby_on_cable(self):
        self.cable(True)
        self.start(self.both)
        if not self.cable_up(self.both):
            return False
        ok = self.enter(self.both)
        met = bool(self.host.has(r"on the cable") and self.switch.has(r"on the cable"))
        self.note("both in the lobby over the cable", ok and met)
        return ok and met

    # ---- checks -------------------------------------------------------

    def pull(self):
        if not self.lobby_on_cable():
            return
        marks = {p: p.now() for p in self.both}
        self.cable(False)
        for p in self.both:
            line = p.wait(r"PSP link: cable none", 12, marks[p])
            self.note("%s sees the pull" % p.name, bool(line), "%.1f s" % (seconds(line) - marks[p]) if line else "no 'cable none'")
            self.note("%s back on the Wi-Fi" % p.name, bool(p.wait(r"PSP link: on the Wi-Fi", 8, marks[p])))
            self.note("%s told to log in again" % p.name, bool(p.wait(r"log in again", 8, marks[p])))
        self.leave(self.both)
        marks = {p: p.now() for p in self.both}
        self.enter(self.both)
        wifi = [l for l in self.host.has(r"PSP link: server ", marks[self.host]) if "on the cable" not in l]
        same = self.subnets_match()
        self.note("meets again over the Wi-Fi", bool(wifi) if same else "SKIP",
                  "" if same else "the phones are on different Wi-Fi subnets: broadcasts do not cross")

    def replug(self):
        if not self.lobby_on_cable():
            return
        marks = {p: p.now() for p in self.both}
        self.cable(False)
        self.host.wait(r"PSP link: cable none", 12, marks[self.host])
        time.sleep(8)
        plugged = self.host.now()
        self.cable(True)
        end = time.time() + 90
        while time.time() < end:
            self.dialogs()
            last = [p.has(r"PSP link: cable (up|none|coming)", plugged) for p in self.both]
            if time.time() > end - 55 and all(f and "cable up" in f[-1] for f in last):
                break
            time.sleep(3)
        ups = self.host.has(r"PSP link: cable up", plugged)
        coming = self.host.has(r"PSP link: cable coming", plugged)
        early = bool(ups and coming and seconds(ups[0]) < seconds(coming[0]))
        self.note("host waits for the real link", not early,
                  "'cable up' %.0f s after the plug, then 'cable coming' %.0f s after it: up on a dead cable"
                  % (seconds(ups[0]) - plugged, seconds(coming[0]) - plugged) if early else "")
        final = [p.has(r"PSP link: cable (up|none|coming)", plugged) for p in self.both]
        self.note("link back on both", all(f and "cable up" in f[-1] for f in final),
                  "; ".join("%s: %s" % (p.name, f[-1].split("PSP link: ")[-1] if f else "nothing") for p, f in zip(self.both, final)))

    def idle(self):
        self.cable(True)
        self.switch.stop()
        self.switch.sh("input keyevent KEYCODE_HOME")
        time.sleep(2)
        before = self.switch.front()
        self.start([self.host])
        self.settle(12)
        after = self.switch.front()
        self.note("what the idle phone shows", "INFO", "%s before, %s after the other phone's game started" % (before, after))
        self.enter([self.host])
        held = len(self.host.has(r"held for the cable"))
        self.note("the lobby's network", "INFO", "cable" if self.host.has(r"PSP link: on the cable") else "Wi-Fi")
        self.note("at most one hold at lobby entry", held <= 1, "%d" % held)
        mark = self.host.now()
        self.press(self.host, "circle", r"PSP link: game left ad hoc", mark)
        left = self.host.wait(r"PSP link: game left ad hoc", 5, mark)
        self.note("leaving ad hoc does not stall", bool(left), "%.1f s" % (seconds(left) - mark) if left else "no 'game left ad hoc' in 15 s")

    def late(self):
        self.cable(False)
        self.start(self.both)
        time.sleep(4)
        self.note("both in a Wi-Fi lobby", self.enter(self.both))
        marks = {p: p.now() for p in self.both}
        self.cable(True)
        self.settle(40)
        moved = [p.name for p in self.both if p.has(r"PSP link: on the cable", marks[p])]
        self.note("nothing moves while in the lobby", not moved, ", ".join(moved))
        self.leave(self.both)
        marks = {p: p.now() for p in self.both}
        self.enter(self.both)
        self.note("the next lobby is on the cable", all(p.has(r"PSP link: on the cable", marks[p]) for p in self.both))

    def nocable(self):
        self.cable(False)
        self.start(self.both)
        time.sleep(4)
        self.enter(self.both)
        for p in self.both:
            asked = p.has(r"sceNetAdhocctlConnect")
            answered = p.has(r"PSP link: (serve on|server )")
            took = seconds(answered[0]) - seconds(asked[0]) if asked and answered else None
            self.note("%s: server within 1.5 s" % p.name, took is not None and took <= 1.5, "%.2f s" % took if took is not None else "no answer")
            self.note("%s: no hold, no cable" % p.name, not p.has(r"held for the cable|on the cable"))

    def subnets_match(self):
        nets = []
        for p in self.both:
            m = re.search(r"broadcast (\d+\.\d+\.\d+\.\d+)", "\n".join(p.has(r"PSP link: auto, self")))
            nets.append(m.group(1) if m else None)
        return nets[0] is not None and nets[0] == nets[1]

    def run(self, names):
        os.makedirs(self.out, exist_ok=True)
        try:
            for p in self.both:
                p.prepare()
            for name in names:
                self.check = name
                self.seen = set()
                print("== %s" % name, flush=True)
                try:
                    getattr(self, name)()
                    self.finish()
                except FoldError as e:
                    self.note("ran", False, str(e))
                    self.finish()
        finally:
            self.cable(True)
            for p in self.both:
                try:
                    p.restore()
                except FoldError as e:
                    print("restore %s: %s" % (p.name, e))
        return self.report()

    def report(self):
        path = os.path.join(self.out, "psp-cable-%s.md" % datetime.date.today().isoformat())
        with open(path, "w") as f:
            f.write("# PSP cable checks\n\n| Check | Step | Result | Detail |\n| --- | --- | --- | --- |\n")
            for row in self.rows:
                f.write("| %s | %s | %s | %s |\n" % row)
        print("report: %s" % path)
        return 1 if any(r[2] == "FAIL" for r in self.rows) else 0


CHECKS = ("pull", "replug", "idle", "late", "nocable")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--host", required=True, help="SERIAL:ROM[:CROSS_XY:CIRCLE_XY], the phone without the USB data switch")
    ap.add_argument("--switch", required=True, help="the same for the phone whose USB data the shell can switch")
    ap.add_argument("--slot", type=int, default=7)
    ap.add_argument("--home", default="/sdcard/NXReduxDev")
    ap.add_argument("--only", default=",".join(CHECKS))
    ap.add_argument("--out", default=os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".local", "device-tests"))
    args = ap.parse_args()
    names = [n for n in args.only.split(",") if n]
    unknown = [n for n in names if n not in CHECKS]
    if unknown:
        ap.error("unknown check: %s" % ", ".join(unknown))
    sys.exit(Run(args).run(names))


if __name__ == "__main__":
    main()
