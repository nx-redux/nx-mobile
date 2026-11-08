"""Mac-side driver for the NX Redux mobile app (debug build) on an Android phone, over adb.

Stdlib only, in brick.py's style. The phone side is driven through the debug-only test hooks
(NxTestReceiver, actions com.nxredux.mobile.test.*); their results are JSON under files/nxtest/, read with run-as.

- Private files (saves, states) live in the app's files/sd (run-as only). Files go in and out through
  /data/local/tmp (shell-writable, run-as-readable) and `adb exec-out run-as ... cat`.
- GAME is sent with --allow-background-activity-starts (a receiver may not start an activity otherwise).
- The device stays PIN-locked: the hooks never unlock it, and nothing here changes a setting.

Test files are always named nxtest-*; cleanup_nxtest() removes them.
"""

import json
import os
import shlex
import subprocess
import tempfile
import time

from brick import nxtest_targets

# The debug build's application id (applicationIdSuffix ".dev", installed beside the release app); the receiver's
# class and actions keep the code's package.
PKG = "com.nxredux.mobile.dev"
NS = "com.nxredux.mobile"
RECEIVER = PKG + "/" + NS + ".test.NxTestReceiver"
ACTION = NS + ".test."
DATA = "/data/user/0/" + PKG
FILES = DATA + "/files"
PRIV = FILES + "/sd"
RESULTS = FILES + "/nxtest"
HOME = "/sdcard/NXRedux"
ROMS = HOME + "/Roms"
TEST_DIR = "/sdcard/nxtest-dir"
XFER = "/data/local/tmp/nxtest-xfer"
TERMINAL_SYNC = ("done", "failed", "timeout", "refused", "stopped")


class FoldError(RuntimeError):
    pass


def q(s):
    """Single-quote for the device's /system/bin/sh."""
    return "'" + str(s).replace("'", "'\\''") + "'"


class Fold:
    def __init__(self, serial, verbose=True):
        self.serial = serial
        self.verbose = verbose
        self._n = 0

    # ---- plumbing -----------------------------------------------------

    def log(self, msg):
        if self.verbose:
            print("[fold] " + msg, flush=True)

    def _adb(self, args, timeout=60, check=True, binary=False):
        cmd = ["adb", "-s", self.serial] + list(args)
        try:
            p = subprocess.run(cmd, capture_output=True, timeout=timeout)
        except subprocess.TimeoutExpired:
            raise FoldError("adb timed out: %s" % " ".join(shlex.quote(a) for a in args))
        if check and p.returncode != 0:
            raise FoldError("adb %s failed (%d): %s" % (args[0], p.returncode,
                                                        p.stderr.decode("utf-8", "replace").strip()))
        return p

    def present(self):
        p = subprocess.run(["adb", "-s", self.serial, "get-state"], capture_output=True)
        return p.returncode == 0 and p.stdout.strip() == b"device"

    def require_present(self):
        if not self.present():
            raise FoldError("Fold %s is not on adb" % self.serial)

    def sh(self, cmd, timeout=60):
        """One adb shell command (as the shell user); stdout with \\r stripped."""
        p = self._adb(["shell", cmd], timeout=timeout, check=False)
        return p.stdout.decode("utf-8", "replace").replace("\r", "")

    def run_as(self, cmd, timeout=60):
        """A shell command as the app (cwd = its data dir), through a pushed script
        so paths with spaces or quotes need no second layer of quoting."""
        self._n += 1
        with tempfile.NamedTemporaryFile("w", suffix=".sh", delete=False, newline="\n") as f:
            f.write(cmd + "\n")
            local = f.name
        remote = "/data/local/tmp/nxtest-runas-%d.sh" % (self._n % 4)
        try:
            self._adb(["push", local, remote], timeout=30)
        finally:
            os.remove(local)
        self.sh("chmod 644 %s" % remote)
        return self.sh("run-as %s sh %s" % (PKG, remote), timeout=timeout)

    def push(self, local, remote):
        self._adb(["push", local, remote], timeout=900)

    def pull(self, remote, local):
        self._adb(["pull", remote, local], timeout=900)

    def push_private(self, local, rel, mtime=None):
        """Copy a Mac file to files/sd/<rel> (app-owned); optional mtime (epoch s)."""
        self.push(local, XFER)
        self.sh("chmod 644 %s" % XFER)
        dest = PRIV + "/" + rel
        out = self.run_as("mkdir -p %s && cp %s %s && %s echo OK"
                          % (q(os.path.dirname(dest)), XFER, q(dest),
                             "" if mtime is None else "touch -d @%d %s &&" % (int(mtime), q(dest))))
        self.sh("rm -f %s" % XFER)
        if "OK" not in out:
            raise FoldError("push_private %s failed: %s" % (rel, out.strip()))

    def pull_private(self, rel, local):
        """Copy files/sd/<rel> to the Mac. Returns False when it is missing."""
        if not self.exists_private(rel):
            return False
        p = self._adb(["exec-out", "run-as %s cat %s" % (PKG, q(PRIV + "/" + rel))], timeout=300)
        with open(local, "wb") as f:
            f.write(p.stdout)
        return True

    def exists_private(self, rel):
        return self.run_as("[ -e %s ] && echo Y" % q(PRIV + "/" + rel)).strip() == "Y"

    def md5_private(self, rel):
        out = self.run_as("md5sum %s 2>/dev/null" % q(PRIV + "/" + rel)).split()
        return out[0] if out else None

    def mtime_private(self, rel):
        out = self.run_as("stat -c %%Y %s 2>/dev/null" % q(PRIV + "/" + rel)).strip()
        return int(out) if out.isdigit() else None

    def size_private(self, rel):
        out = self.run_as("stat -c %%s %s 2>/dev/null" % q(PRIV + "/" + rel)).strip()
        return int(out) if out.isdigit() else None

    def rm_private(self, rel):
        if "nxtest-" not in os.path.basename(rel):
            raise FoldError("refusing to remove a non-nxtest file: %s" % rel)
        self.run_as("rm -f %s" % q(PRIV + "/" + rel))

    def md5(self, path):
        out = self.sh("md5sum %s 2>/dev/null" % q(path)).split()
        return out[0] if out else None

    def mtime(self, path):
        out = self.sh("stat -c %%Y %s 2>/dev/null" % q(path)).strip()
        return int(out) if out.isdigit() else None

    def exists(self, path):
        return self.sh("[ -e %s ] && echo Y" % q(path)).strip() == "Y"

    @staticmethod
    def wait_for(fn, timeout, interval=1.0):
        end = time.time() + timeout
        while True:
            v = fn()
            if v or time.time() >= end:
                return v
            time.sleep(interval)

    # ---- hooks --------------------------------------------------------

    def broadcast(self, action, extras="", background_start=False):
        cmd = "am broadcast %s-n %s -a %s%s %s" % (
            "--allow-background-activity-starts " if background_start else "", RECEIVER, ACTION, action, extras)
        out = self.sh(cmd)
        if "Broadcast completed" not in out:
            raise FoldError("broadcast %s: %s" % (action, out.strip()))
        return out

    def read_json(self, name):
        out = self.sh("run-as %s cat files/nxtest/%s.json 2>/dev/null" % (PKG, name)).strip()
        if not out:
            return None
        try:
            return json.loads(out)
        except ValueError:
            return None

    def reset(self):
        self.broadcast("RESET")

    def rm_result(self, name):
        self.sh("run-as %s rm -f files/nxtest/%s.json" % (PKG, name))

    def game_running(self):
        """True while GameActivity is the resumed activity."""
        out = self.sh("dumpsys activity activities | grep -E 'ResumedActivity'")
        return "GameActivity" in out

    def sync(self, link="wifi", roms=False, timeout=600):
        """SYNC hook; waits for a terminal sync.json and returns it (state 'timeout' added on our side)."""
        self.rm_result("sync")
        self.broadcast("SYNC", "--es link %s --ez roms %s" % (link, "true" if roms else "false"))
        t0 = time.time()
        last = None
        while time.time() - t0 < timeout:
            j = self.read_json("sync")
            if j and j.get("state") in TERMINAL_SYNC:
                j["elapsed"] = round(time.time() - t0, 1)
                return j
            if j != last:
                last = j
            time.sleep(2)
        self.log("sync: no terminal state after %d s, SYNC_STOP" % timeout)
        self.sync_stop()
        return {"state": "harness-timeout", "elapsed": round(time.time() - t0, 1)}

    def sync_stop(self):
        self.broadcast("SYNC_STOP")

    def game(self, path, tag, slot=None, timeout=60):
        """GAME hook; waits for game.json state running (or refused). Returns game.json."""
        for name in ("game", "battery", "resume"):
            self.rm_result(name)
        extras = "--es path %s --es tag %s" % (q(path), tag)
        if slot is not None:
            extras += " --ei slot %d" % int(slot)
        self.broadcast("GAME", extras, background_start=True)
        j = self.wait_for(lambda: (lambda g: g if g and g.get("state") in ("running", "refused", "stopped") else None)(
            self.read_json("game")), timeout)
        return j

    def game_stop(self, timeout=20):
        """GAME_STOP (quits like the menu's Quit); waits for game.json state stopped."""
        self.broadcast("GAME_STOP")
        j = self.wait_for(lambda: (lambda g: g if g and g.get("state") == "stopped" else None)(
            self.read_json("game")), timeout)
        time.sleep(1)
        return j

    def clear_crash_log(self):
        self.sh("logcat -b crash -c")

    def crash_log(self):
        return self.sh("logcat -b crash -d").strip()

    # ---- cleanup ------------------------------------------------------

    def cleanup_nxtest(self):
        """Remove every nxtest-* file and directory: home Roms (incl. .media/.retroachievements), /sdcard/nxtest-dir,
        files/sd (Saves incl. a core's per-game folders, .userdata/shared incl. .minui), result JSONs, /data/local/tmp.
        Returns paths removed (dirs end in /)."""
        removed = []
        find = "find %s -name 'nxtest-*' " % q(HOME)
        targets = nxtest_targets(self.sh(find + "-type f 2>/dev/null", timeout=300),
                                 self.sh(find + "-type d -prune 2>/dev/null", timeout=300))
        for p in targets:
            self.sh(("rm -rf %s" if p.endswith("/") else "rm -f %s") % q(p.rstrip("/")))
            removed.append(p)
        if self.exists(TEST_DIR):
            self.sh("rm -rf %s" % TEST_DIR)
            removed.append(TEST_DIR + "/")
        find = "find %s -name 'nxtest-*' " % q(PRIV)
        targets = nxtest_targets(self.run_as(find + "-type f 2>/dev/null", timeout=120),
                                 self.run_as(find + "-type d -prune 2>/dev/null", timeout=120))
        for p in targets:
            self.run_as(("rm -rf %s" if p.endswith("/") else "rm -f %s") % q(p.rstrip("/")))
            removed.append(p)
        self.run_as("rm -f %s/*.json" % RESULTS)
        self.sh("rm -f /data/local/tmp/nxtest-*")
        return removed
