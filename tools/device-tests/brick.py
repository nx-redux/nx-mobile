"""Mac-side driver for a TrimUI Brick (tg5040) running NX Redux, over adb.

Stdlib only. Every recipe follows nx-redux/.dev/TESTING.md:

- apps are launched through /tmp/next + `kill -9 nextui.elf`, and only while
  nextui.elf itself has a pid (a bare kill -9 of nextui powers the device off);
- a game is ended with `kill -9` on the minarch pid, never SIGTERM (minarch
  turns SDL_QUIT into a power-off);
- input is 48-byte key+SYN_REPORT writes to /dev/input/event3 (pad) or
  /dev/input/event1 (KEY_POWER), ~150 ms between press and release;
- a showing OSD eats injected input: dismissed with `touch /tmp/hide_osdd`;
- hybrid sleep is detected by keymon.elf being SIGSTOPped (PWR_enterSleep).

Test files are always named nxtest-*; cleanup_nxtest() removes them.
"""

import os
import shlex
import struct
import subprocess
import tempfile
import time
import zlib

SDCARD = "/mnt/SDCARD"
EMUS = SDCARD + "/.system/paks/Emus"
TOOLS = SDCARD + "/.system/paks/Tools"
SHARED_USERDATA = SDCARD + "/.userdata/shared"
AUTO_RESUME_PATH = SHARED_USERDATA + "/.minui/auto_resume.txt"
RESUME_SLOT_PATH = "/tmp/resume_slot.txt"
EV_DIR = "/tmp/nxtest-ev"
SHOT_REMOTE = "/tmp/nxtest-shot.bmp"
LIBS = "/mnt/SDCARD/.system/lib:/mnt/SDCARD/.system/shared/lib:/usr/trimui/lib"
DEVICE_SYNC_LAUNCH = TOOLS + "/Device Sync.pak/launch.sh"

PAD_DEV = "/dev/input/event3"
POWER_DEV = "/dev/input/event1"

EV_SYN, EV_KEY, EV_ABS = 0, 1, 3
ABS_HAT0X, ABS_HAT0Y = 0x10, 0x11
KEY_POWER = 116

# Physical button -> evdev code (INPUT_MAPPING.md: physical A is BTN_B, etc.)
KEYS = {
    "A": 305, "B": 304, "X": 308, "Y": 307,
    "L1": 310, "R1": 311,
    "SELECT": 314, "START": 315, "MENU": 316,
}
HATS = {"UP": (ABS_HAT0Y, -1), "DOWN": (ABS_HAT0Y, 1),
        "LEFT": (ABS_HAT0X, -1), "RIGHT": (ABS_HAT0X, 1)}

ERROR_MARKERS = ("Error restoring save state", "Error reading", "Error opening state")

PRESS_SH = """#!/bin/sh
# nxtest press helper: press.sh NAME [NAME...]  (HOLD_US / GAP_US override timing)
D=/tmp/nxtest-ev
for k in "$@"; do
  [ -f "$D/$k.down" ] || { echo "nxtest: no key $k" >&2; exit 2; }
  dev=/dev/input/event3
  [ "$k" = POWER ] && dev=/dev/input/event1
  cat "$D/$k.down" > $dev
  usleep ${HOLD_US:-150000}
  cat "$D/$k.up" > $dev
  usleep ${GAP_US:-300000}
done
"""


class BrickError(RuntimeError):
    pass


class BrickGone(BrickError):
    """The Brick left adb: it may have powered off. Stop, do not retry."""


def q(s):
    """Single-quote for the device's /bin/sh."""
    return "'" + str(s).replace("'", "'\\''") + "'"


def nxtest_targets(files, dirs):
    """What cleanup_nxtest removes, from two `find` outputs: nxtest-* files and nxtest-* directories (a core's
    folder named after the game, Saves/PRBOOM/nxtest-freedoom1/). Only names starting nxtest- count; a directory
    comes back ending in "/" (rm -rf) and nothing inside one is listed again. Directories first, then files."""
    def names(out):
        paths = (l.strip().rstrip("/") for l in out.split("\n"))
        return [p for p in paths if p and os.path.basename(p).startswith("nxtest-")]
    found = sorted(set(names(dirs)))
    top = [d for d in found if not any(d.startswith(o + "/") for o in found)]
    inside = lambda p: any(p.startswith(d + "/") for d in top)
    return [d + "/" for d in top] + [f for f in dict.fromkeys(names(files)) if not inside(f)]


def _event(etype, code, value):
    return struct.pack("<QQHHi", 0, 0, etype, code, value)


def _syn():
    return _event(EV_SYN, 0, 0)


def event_files():
    """{name: (down_bytes, up_bytes)}; each is one 48-byte event + SYN_REPORT."""
    out = {}
    for name, code in KEYS.items():
        out[name] = (_event(EV_KEY, code, 1) + _syn(), _event(EV_KEY, code, 0) + _syn())
    for name, (axis, val) in HATS.items():
        out[name] = (_event(EV_ABS, axis, val) + _syn(), _event(EV_ABS, axis, 0) + _syn())
    out["POWER"] = (_event(EV_KEY, KEY_POWER, 1) + _syn(), _event(EV_KEY, KEY_POWER, 0) + _syn())
    return out


def bmp_to_png(bmp_path, png_path):
    """Convert the screenshot.elf BMP (24/32 bpp, either row order) to PNG."""
    with open(bmp_path, "rb") as f:
        data = f.read()
    if data[:2] != b"BM":
        raise BrickError("not a BMP: %s" % bmp_path)
    off = struct.unpack_from("<I", data, 10)[0]
    w, h = struct.unpack_from("<ii", data, 18)
    bpp = struct.unpack_from("<H", data, 28)[0]
    if bpp not in (24, 32):
        raise BrickError("unsupported BMP depth %d" % bpp)
    top_down = h < 0
    h = abs(h)
    bpx = bpp // 8
    stride = (w * bpx + 3) & ~3
    rows = []
    for y in range(h):
        sy = y if top_down else h - 1 - y
        row = data[off + sy * stride: off + sy * stride + w * bpx]
        rgb = bytearray(w * 3)
        rgb[0::3] = row[2::bpx]
        rgb[1::3] = row[1::bpx]
        rgb[2::3] = row[0::bpx]
        rows.append(b"\x00" + bytes(rgb))

    def chunk(tag, body):
        return (struct.pack(">I", len(body)) + tag + body
                + struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF))

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(b"".join(rows), 6))
           + chunk(b"IEND", b""))
    with open(png_path, "wb") as f:
        f.write(png)


class Brick:
    def __init__(self, serial, verbose=True):
        self.serial = serial
        self.verbose = verbose
        self._events_ready = False
        self._set_resume = False

    # ---- plumbing -----------------------------------------------------

    def log(self, msg):
        if self.verbose:
            print("[brick] " + msg, flush=True)

    def _adb(self, args, timeout=60, check=True):
        cmd = ["adb", "-s", self.serial] + list(args)
        try:
            p = subprocess.run(cmd, capture_output=True, timeout=timeout)
        except subprocess.TimeoutExpired:
            raise BrickError("adb timed out: %s" % " ".join(shlex.quote(a) for a in args))
        err = p.stderr.decode("utf-8", "replace")
        if p.returncode != 0 and ("not found" in err or "no devices" in err or "offline" in err):
            raise BrickGone("Brick %s is not on adb (%s) - it may have powered off" % (self.serial, err.strip()))
        if check and p.returncode != 0:
            raise BrickError("adb %s failed (%d): %s" % (args[0], p.returncode, err.strip()))
        return p

    def present(self):
        p = subprocess.run(["adb", "-s", self.serial, "get-state"], capture_output=True)
        return p.returncode == 0 and p.stdout.strip() == b"device"

    def require_present(self):
        if not self.present():
            raise BrickGone("Brick %s is not on adb - it may have powered off" % self.serial)

    def sh(self, cmd, timeout=60):
        """One adb shell command; stdout with \\r stripped."""
        p = self._adb(["shell", cmd], timeout=timeout, check=False)
        return p.stdout.decode("utf-8", "replace").replace("\r", "")

    def push(self, local, remote):
        self._adb(["push", local, remote], timeout=300)

    def pull(self, remote, local):
        self._adb(["pull", remote, local], timeout=300)

    def md5(self, path):
        out = self.sh("md5sum %s 2>/dev/null" % q(path)).split()
        return out[0] if out else None

    def exists(self, path):
        return self.sh("[ -e %s ] && echo Y" % q(path)).strip() == "Y"

    def mtime(self, path):
        """Seconds since epoch, or None (busybox has no stat: date -r)."""
        out = self.sh("date -r %s +%%s 2>/dev/null" % q(path)).strip()
        return int(out) if out.isdigit() else None

    def size(self, path):
        out = self.sh("wc -c < %s 2>/dev/null" % q(path)).strip()
        return int(out) if out.isdigit() else None

    def pidof(self, name):
        out = self.sh("pidof %s" % q(name)).split()
        return int(out[0]) if out else None

    @staticmethod
    def wait_for(fn, timeout, interval=0.5):
        end = time.time() + timeout
        while True:
            v = fn()
            if v:
                return v
            if time.time() >= end:
                return v
            time.sleep(interval)

    # ---- input --------------------------------------------------------

    def ensure_events(self):
        if self._events_ready:
            return
        files = event_files()
        with tempfile.TemporaryDirectory(prefix="nxtest-ev-") as tmp:
            local = os.path.join(tmp, "nxtest-ev")
            os.mkdir(local)
            for name, (down, up) in files.items():
                assert len(down) == 48 and len(up) == 48
                with open(os.path.join(local, name + ".down"), "wb") as f:
                    f.write(down)
                with open(os.path.join(local, name + ".up"), "wb") as f:
                    f.write(up)
            with open(os.path.join(local, "press.sh"), "w", newline="\n") as f:
                f.write(PRESS_SH)
            # push the dir's contents (a dir push nests when the target exists)
            self.sh("rm -rf %s; mkdir -p %s" % (EV_DIR, EV_DIR))
            for fn in sorted(os.listdir(local)):
                self.push(os.path.join(local, fn), EV_DIR + "/" + fn)
            self.sh("chmod 755 %s/press.sh" % EV_DIR)
            import hashlib
            want = hashlib.md5(files["A"][0]).hexdigest()
        if self.md5(EV_DIR + "/A.down") != want:
            raise BrickError("event files did not land in %s" % EV_DIR)
        self._events_ready = True

    def osd_showing(self):
        return self.exists("/tmp/trimui_osd/osdd_show_up")

    def hide_osd(self):
        if self.osd_showing():
            self.log("OSD showing: touch /tmp/hide_osdd")
            self.sh("touch /tmp/hide_osdd")
            self.wait_for(lambda: not self.osd_showing(), 3)

    def press(self, *names, hold_ms=150, gap_ms=300):
        """Press buttons in order: A B X Y L1 R1 SELECT START MENU UP DOWN LEFT RIGHT POWER.

        MENU alone opens minarch's in-game menu and a >=1 s MENU hold pops the
        OSD; a >=1 s POWER hold powers the device off - keep holds short.
        """
        if not names:
            return
        if "POWER" in names and hold_ms >= 800:
            raise BrickError("refusing a POWER hold >= 800 ms (1 s powers off)")
        self.ensure_events()
        for n in names:
            if n not in KEYS and n not in HATS and n != "POWER":
                raise BrickError("unknown button %s" % n)
        if "POWER" not in names:
            self.hide_osd()
        # <= 4 presses per adb shell (longer commands hit "command too long")
        for i in range(0, len(names), 4):
            chunk = names[i:i + 4]
            out = self.sh("HOLD_US=%d GAP_US=%d %s/press.sh %s; echo RC=$?"
                          % (hold_ms * 1000, gap_ms * 1000, EV_DIR, " ".join(chunk)),
                          timeout=30)
            if not out.strip().endswith("RC=0"):
                raise BrickError("press %s failed: %s" % (chunk, out.strip()))

    # ---- sleep / wake -------------------------------------------------

    def asleep(self):
        """Hybrid sleep SIGSTOPs keymon.elf (PWR_enterSleep); fb0 keeps a stale frame."""
        out = self.sh("p=$(pidof keymon.elf) && grep '^State:' /proc/$p/status")
        return "T (stopped)" in out or "stopped" in out

    def wake(self):
        """Wake from hybrid sleep with KEY_POWER. No-op (no press) when awake:
        a power press on an awake device puts it to sleep."""
        for attempt in range(3):
            if not self.asleep():
                if attempt:
                    time.sleep(1.5)  # power presses within 1 s of resume are ignored
                return attempt > 0
            self.log("asleep: waking with KEY_POWER (try %d)" % (attempt + 1))
            self.press("POWER")
            self.wait_for(lambda: not self.asleep(), 5)
        if self.asleep():
            raise BrickError("still asleep after 3 KEY_POWER presses")
        time.sleep(1.5)
        return True

    # ---- launcher -----------------------------------------------------

    def nextui_alive(self):
        return self.pidof("nextui.elf") is not None

    def minarch_pid(self):
        return self.pidof("minarch.elf")

    def logs_path(self):
        """$LOGS_PATH as MinUI.pak/launch.sh sets it: /tmp/nx-logs with debug
        logging off, $USERDATA_PATH/logs with it on. Read from the running
        launcher's environment, falling back to which dir exists."""
        for name in ("nextui.elf", "minarch.elf", "sync.elf"):
            pid = self.pidof(name)
            if pid:
                env = self.sh("tr '\\0' '\\n' < /proc/%d/environ | grep '^LOGS_PATH='" % pid).strip()
                if env.startswith("LOGS_PATH="):
                    return env.split("=", 1)[1]
        if self.exists("/tmp/nx-logs"):
            return "/tmp/nx-logs"
        return SDCARD + "/.userdata/tg5040/logs"

    def game_log(self, tag):
        """The pak's log, $LOGS_PATH/<TAG>.txt (rewritten on every launch)."""
        return self.sh("cat %s 2>/dev/null" % q(self.logs_path() + "/" + tag + ".txt"))

    def launch(self, pak_launch_sh, rom_path=None, wait_proc=None, timeout=30):
        """Queue `'<pak>/launch.sh' ['<rom>']` in /tmp/next and kill -9 nextui
        so the MinUI.pak loop evals it - only while nextui itself has a pid."""
        self.require_present()
        if not self.nextui_alive():
            raise BrickError("nextui.elf has no pid: an app is running, refusing to inject /tmp/next")
        self.wake()
        self.hide_osd()
        cmd = q(pak_launch_sh) + ("" if rom_path is None else " " + q(rom_path))
        script = ("pid=$(pidof nextui.elf); [ -n \"$pid\" ] || { echo NOPID; exit 0; }; "
                  "printf '%%s' %s > /tmp/next && sync; "
                  "[ -s /tmp/next ] || { echo NONEXT; exit 0; }; "
                  "kill -9 $pid; echo KILLED $pid") % q(cmd)
        out = self.sh(script).strip()
        if not out.startswith("KILLED"):
            raise BrickError("launch not queued: %s" % out)
        old = int(out.split()[1])
        self.log("launched %s %s" % (pak_launch_sh, rom_path or ""))
        self.wait_for(lambda: self.pidof("nextui.elf") != old, 10)
        if wait_proc:
            pid = self.wait_for(lambda: self.pidof(wait_proc), timeout)
            if not pid:
                raise BrickError("%s did not start (log: %s)" % (wait_proc, self.logs_path()))
            return pid
        return None

    def launch_rom(self, tag, rom_path, timeout=30):
        """Launch a ROM with the stock Emus/<TAG>.pak (minarch paks)."""
        return self.launch("%s/%s.pak/launch.sh" % (EMUS, tag), rom_path,
                           wait_proc="minarch.elf", timeout=timeout)

    def kill_game(self, timeout=30):
        """kill -9 the minarch pid (never SIGTERM: it powers off), then wait
        for the launch loop to bring nextui back."""
        pid = self.minarch_pid()
        if pid:
            self.wake()  # else nextui comes back with keymon stopped and the backlight off
            # a sleep wrote auto_resume.txt: nextui would relaunch the game at once
            self.drop_nxtest_auto_resume()
            self.sh("kill -9 %d" % pid)
            self.log("kill -9 minarch %d" % pid)
        if not self.wait_for(self.nextui_alive, timeout):
            raise BrickError("nextui did not come back after killing minarch")
        self.wait_for(lambda: not self.exists("/tmp/next"), 5)
        return pid

    def drop_nxtest_auto_resume(self):
        """Remove auto_resume.txt only when it points at an nxtest-* game."""
        if "/nxtest-" in self.sh("cat %s 2>/dev/null" % AUTO_RESUME_PATH):
            self.sh("rm -f %s && sync" % AUTO_RESUME_PATH)
            return True
        return False

    def set_resume_slot(self, n):
        """minarch's State_resume reads (and unlinks) /tmp/resume_slot.txt at boot."""
        self.sh("echo %d > %s && sync" % (int(n), RESUME_SLOT_PATH))
        self._set_resume = True

    @staticmethod
    def state_path(tag, core, rom_name, slot=9):
        """minarch state path (.userdata/shared/<TAG>-<core>/<stem>.state.auto for slot 9)."""
        stem = rom_name
        dot = stem.rfind(".")
        if dot != -1 and 2 < len(stem) - dot <= 5:
            stem = stem[:dot]
        base = "%s/%s-%s/%s" % (SHARED_USERDATA, tag, core, stem)
        if slot == 9:
            return base + ".state.auto"
        return base + (".state" if slot == 0 else ".state%d" % slot)

    def sleep_autosave(self, state_path, timeout=15):
        """KEY_POWER to sleep: minarch's Menu_beforeSleep writes the slot-9
        auto state (and auto_resume.txt); then wake (Menu_afterSleep unlinks
        auto_resume.txt). Returns True when the state changed."""
        if not self.minarch_pid():
            raise BrickError("sleep_autosave needs a running game")
        self.wake()
        self.hide_osd()
        before = (self.mtime(state_path), self.md5(state_path))
        self.press("POWER")
        slept = self.wait_for(self.asleep, timeout)
        changed = self.wait_for(lambda: (self.mtime(state_path), self.md5(state_path)) != before
                                and self.md5(state_path) is not None, timeout)
        if slept:
            time.sleep(3)  # let PWR_enterSleep finish before the wake press
            self.wake()
        if not slept:
            raise BrickError("device did not enter sleep after KEY_POWER")
        return bool(changed)

    # ---- screenshots --------------------------------------------------

    def screenshot(self, local_path):
        """screenshot.elf --capture (composited panel incl. OSD) -> pull.
        A .png local_path is converted from the BMP on the Mac."""
        out = self.sh("export LD_LIBRARY_PATH=%s; rm -f %s; /mnt/SDCARD/.system/bin/screenshot.elf "
                      "--capture %s; echo RC=$?" % (LIBS, SHOT_REMOTE, SHOT_REMOTE), timeout=30)
        if not out.strip().endswith("RC=0"):
            raise BrickError("screenshot.elf failed: %s" % out.strip())
        os.makedirs(os.path.dirname(os.path.abspath(local_path)), exist_ok=True)
        if local_path.lower().endswith(".png"):
            bmp = local_path[:-4] + ".bmp"
            self.pull(SHOT_REMOTE, bmp)
            bmp_to_png(bmp, local_path)
            os.remove(bmp)
        else:
            self.pull(SHOT_REMOTE, local_path)
        self.sh("rm -f %s" % SHOT_REMOTE)
        return local_path

    # ---- Device Sync --------------------------------------------------

    def start_device_sync(self, wifi=True, settle=2.5):
        """Launch Tools/Device Sync.pak through /tmp/next. On the Ready screen
        the cable is selected; UP/DOWN toggles cable <-> Wi-Fi, A starts
        discovery (Waiting). Returns the sync.elf pid."""
        pid = self.launch(DEVICE_SYNC_LAUNCH, None, wait_proc="sync.elf", timeout=20)
        time.sleep(settle)  # splash -> Ready
        if wifi:
            self.press("DOWN")
        self.press("A")
        return pid

    def stop_device_sync(self, timeout=15):
        """B backs out of Waiting to Ready, B on Ready quits; kill -9 sync.elf
        as a fallback. Waits for nextui to come back."""
        if self.pidof("sync.elf"):
            self.wake()
            self.press("B")
            time.sleep(0.8)
            if self.pidof("sync.elf"):
                self.press("B")
            if not self.wait_for(lambda: self.pidof("sync.elf") is None, 6):
                pid = self.pidof("sync.elf")
                if pid:
                    self.log("sync.elf still up: kill -9 %d" % pid)
                    self.sh("kill -9 %d" % pid)
        if not self.wait_for(self.nextui_alive, timeout):
            raise BrickError("nextui did not come back after Device Sync")

    # ---- cleanup ------------------------------------------------------

    CLEAN_ROOTS = (SDCARD + "/Roms", SDCARD + "/Saves", SDCARD + "/.userdata",
                   SDCARD + "/Bios", SDCARD + "/Collections", "/tmp")

    def cleanup_nxtest(self):
        """Remove every nxtest-* file and directory under CLEAN_ROOTS. Returns the paths removed (dirs end in /)."""
        removed = []
        for root in self.CLEAN_ROOTS:
            find = "[ -d %s ] && find %s -xdev -name 'nxtest-*' " % (q(root), q(root))
            files = self.sh(find + "-type f", timeout=180)
            dirs = self.sh(find + "-type d -prune", timeout=180)
            for path in nxtest_targets(files, dirs):
                self.sh(("rm -rf %s" if path.endswith("/") else "rm -f %s") % q(path.rstrip("/")))
                removed.append(path)
        self.drop_nxtest_auto_resume()
        if self._set_resume:
            self.sh("rm -f %s" % RESUME_SLOT_PATH)
            self._set_resume = False
        self._events_ready = False
        self.sh("sync")
        return removed
