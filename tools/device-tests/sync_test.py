#!/usr/bin/env python3
"""Device Sync scenarios between a phone (NX Redux mobile, debug build) and a TrimUI Brick, over Wi-Fi.

Design: docs/superpowers/specs/2026-10-16-device-test-automation-design.md, "Device Sync scenarios".

  1. handheld saves of a GBA-folder and an MGBA-folder game reach the phone's Saves/GBA
  2. changed phone saves go back to each game's own handheld folder
  3. a second unchanged sync moves no save (odd-second mtimes vs FAT's 2 s rounding)
  4. DS: a .dsv crosses, the phone plays it in place, a phone-changed .dsv (footer kept) returns
  5. ROMs on: a phone GBA ROM reaches only the Brick's GBA folder; a Brick MGBA-folder ROM lands in the phone's GBA folder
  6. Dreamcast per-game card Saves/DC/nxtest-dc.A1.bin crosses both ways

Every sync: the Brick's Device Sync first (Searching on Wi-Fi), then the phone SYNC hook (link wifi); wait for
sync.json; then B on the Brick. Every file it creates is named nxtest-*; both devices are cleaned at the end.
A Device Sync also moves the user's real saves both ways (newest wins, never deletes): that is the app's normal job.

usage: sync_test.py [--fold SERIAL] [--brick SERIAL] [--out FILE] [--skip-roms | --only-roms]
"""

import argparse
import os
import sys
import tempfile
import time

from brick import SDCARD, Brick, BrickGone, q
from fold import PRIV, ROMS as PHONE_ROMS, TERMINAL_SYNC, TEST_DIR, Fold

B_ROMS = SDCARD + "/Roms"
B_SAVES = SDCARD + "/Saves"
B_GBA = B_ROMS + "/Game Boy Advance (GBA)"
B_MGBA = B_ROMS + "/Game Boy Advance (MGBA)"
B_NDS = B_ROMS + "/Nintendo DS (NDS)"
B_DC = B_ROMS + "/DreamCast (DC)"
P_GBA = PHONE_ROMS + "/Game Boy Advance (GBA)"
DSV_FOOTER_TAIL = b"|-DESMUME SAVE-|"
STALL_S = 240   # paired but no rsync connection on the Brick by then: the phone is stuck


class Results:
    def __init__(self):
        self.rows = []   # (scenario, ok, evidence list)
        self.syncs = []  # (label, sync.json)
        self.notes = []

    def add(self, scen, ok, evidence):
        self.rows.append((scen, ok, evidence))
        print("%s %s\n    %s" % ("PASS" if ok else "FAIL", scen, "\n    ".join(evidence)), flush=True)


def odd(t):
    t = int(t)
    return t if t % 2 else t - 1


def write_local(tmp, name, data):
    p = os.path.join(tmp, name)
    with open(p, "wb") as f:
        f.write(data)
    return p


def md5_bytes(data):
    import hashlib
    return hashlib.md5(data).hexdigest()


class Runner:
    def __init__(self, fold, brick, res, tmp, shots):
        self.f, self.b, self.res, self.tmp, self.shots = fold, brick, res, tmp, shots

    # ---- one sync -----------------------------------------------------

    def _run_sync(self, roms, timeout):
        """SYNC hook; polls sync.json. 'stalled' when the Brick's daemon saw no connection in STALL_S."""
        f, b = self.f, self.b
        f.rm_result("sync")
        f.broadcast("SYNC", "--es link wifi --ez roms %s" % ("true" if roms else "false"))
        t0 = time.time()
        while True:
            time.sleep(3)
            j = f.read_json("sync")
            el = round(time.time() - t0, 1)
            if j and j.get("state") in TERMINAL_SYNC:
                j["elapsed"] = el
                return j
            if el > STALL_S and "connect from" not in b.sh("cat /tmp/rsyncd.log 2>/dev/null"):
                f.sync_stop()
                return {"state": "stalled", "stalled": True, "elapsed": el,
                        "detail": "no rsync connection on the Brick after %d s" % STALL_S}
            if el > timeout:
                f.sync_stop()
                return {"state": "harness-timeout", "elapsed": el}

    def sync(self, label, roms=False, timeout=1800, retry_stall=True):
        b, f = self.b, self.f
        if f.game_running():
            raise RuntimeError("a game is open on the phone: SYNC would be refused")
        if b.minarch_pid() or not b.nextui_alive():
            raise RuntimeError("the Brick is not on its main menu")
        f.sync_stop()   # a leftover controller page from an earlier run goes back to Ready
        time.sleep(2)
        b.start_device_sync(wifi=True)
        time.sleep(1.5)
        j = self._run_sync(roms, timeout)
        if j.get("stalled") and retry_stall:
            self.res.notes.append("sync %s stalled (paired, no rsync connection to the Brick in %d s, phone idle); "
                                  "SYNC_STOP and retried once" % (label, STALL_S))
            b.stop_device_sync()
            self.res.syncs.append((label + " [stalled, retried]", j))
            return self.sync(label, roms, timeout, retry_stall=False)
        time.sleep(3)
        # the Brick's rsync daemon log (rewritten per Device Sync): files it sent / received
        dlog = b.sh("cat /tmp/rsyncd.log 2>/dev/null")
        sends = [l.split("] send ", 1)[1] for l in dlog.split("\n") if "] send " in l]
        recvs = [l.split("] recv ", 1)[1] for l in dlog.split("\n") if "] recv " in l]
        j["brick_daemon"] = "sent %d, received %d file(s)%s" % (
            len(sends), len(recvs), (": " + "; ".join((["send " + s for s in sends] + ["recv " + r for r in recvs])[:8]))
            if sends or recvs else "")
        shot = os.path.join(self.shots, "sync-%s.png" % label.split()[0])
        try:
            b.screenshot(shot)
            j["brick_screen"] = shot
        except Exception as e:
            j["brick_screen"] = "screenshot failed: %r" % e
        b.stop_device_sync()
        self.res.syncs.append((label, j))
        print("[sync %s] %s" % (label, j), flush=True)
        return j

    # ---- helpers ------------------------------------------------------

    def b_put(self, data, remote, mtime):
        local = write_local(self.tmp, "nxtest-up.bin", data)
        self.b.push(local, remote)
        self.b.sh("touch -d @%d %s && sync" % (mtime, q(remote)))
        return md5_bytes(data)

    def b_bytes(self, remote):
        local = os.path.join(self.tmp, "nxtest-down.bin")
        if os.path.exists(local):
            os.remove(local)
        if not self.b.exists(remote):
            return None
        self.b.pull(remote, local)
        with open(local, "rb") as fh:
            return fh.read()

    def p_bytes(self, rel):
        local = os.path.join(self.tmp, "nxtest-pdown.bin")
        if not self.f.pull_private(rel, local):
            return None
        with open(local, "rb") as fh:
            return fh.read()

    def p_put(self, data, rel, mtime):
        local = write_local(self.tmp, "nxtest-pup.bin", data)
        self.f.push_private(local, rel, mtime)
        return md5_bytes(data)


def run(args, res):
    f = Fold(args.fold)
    b = Brick(args.brick)
    f.require_present()
    b.require_present()
    if f.game_running():
        raise SystemExit("FAIL: a game is open on the Fold")
    if b.minarch_pid() or b.pidof("sync.elf") or not b.nextui_alive():
        raise SystemExit("FAIL: the Brick must be on the NextUI menu")
    b.wake()

    tmp = tempfile.mkdtemp(prefix="nxtest-sync-")
    shots = os.path.join(os.path.dirname(os.path.abspath(args.out)), "sync-shots")
    os.makedirs(shots, exist_ok=True)
    r = Runner(f, b, res, tmp, shots)
    print("stale removed: brick %s, fold %s" % (b.cleanup_nxtest(), f.cleanup_nxtest()))
    f.reset()

    if not args.only_roms:
        saves_scenarios(r, f, b, res, tmp)
    if args.skip_roms:
        res.add("5. ROMs on", False, ["skipped (--skip-roms)"])
    else:
        roms_scenario(r, f, b, res)
    return f, b


def saves_scenarios(r, f, b, res, tmp):
    """Scenarios 1, 2, 3, 4 and 6 (ROMs off)."""
    now = time.time()
    t_hand = odd(now - 3 * 3600)          # handheld fixtures: 3 h ago, odd second
    fx = {}

    # ---- fixtures on the Brick -------------------------------------------
    b.sh("cp %s %s && cp %s %s && sync" % (q(B_GBA + "/Flappy Bird.gba"), q(B_GBA + "/nxtest-gba.gba"),
                                         q(B_MGBA + "/Flappy Bird.gba"), q(B_MGBA + "/nxtest-mgba.gba")))
    fx["gba"] = r.b_put(os.urandom(131072), B_SAVES + "/GBA/nxtest-gba.srm", t_hand)
    fx["mgba"] = r.b_put(os.urandom(131072), B_SAVES + "/MGBA/nxtest-mgba.srm", t_hand)
    # DS: a 512-byte save with the real DeSmuME footer of the Brick's Contra 4 save (also 512 bytes)
    contra = r.b_bytes(B_SAVES + "/NDS/Contra 4.dsv")
    if not contra or not contra.endswith(DSV_FOOTER_TAIL) or len(contra) != 634:
        raise SystemExit("FAIL: Saves/NDS/Contra 4.dsv is not the 634-byte DeSmuME save expected as the footer source")
    ds = os.urandom(512) + contra[512:]
    fx["ds"] = r.b_put(ds, B_SAVES + "/NDS/nxtest-ds.dsv", t_hand)
    b.sh("cp %s %s && sync" % (q(B_NDS + "/Contra 4.nds"), q(B_NDS + "/nxtest-ds.nds")))
    fx["dc"] = r.b_put(os.urandom(131072), B_SAVES + "/DC/nxtest-dc.A1.bin", t_hand)
    r.b_put(b"\0" * 4096, B_DC + "/nxtest-dc.chd", t_hand)  # a name for the save mapping, never booted
    # the phone's copy of the DS ROM (for scenario 4's in-place play), outside its library
    f.sh("mkdir -p %s" % TEST_DIR)
    src = PHONE_ROMS + "/Nintendo DS (NDS)/Contra 4.nds"
    if f.exists(src):
        f.sh("cp %s %s/nxtest-ds.nds" % (q(src), TEST_DIR), timeout=120)
    else:
        local = os.path.join(tmp, "nxtest-ds.nds")
        b.pull(B_NDS + "/Contra 4.nds", local)
        f.push(local, TEST_DIR + "/nxtest-ds.nds")
        os.remove(local)

    # ---- sync A: handheld -> phone ------------------------------------
    jA = r.sync("A (handheld -> phone)")
    okA = jA.get("state") == "done"
    p_gba, p_mgba = r.p_bytes("Saves/GBA/nxtest-gba.srm"), r.p_bytes("Saves/GBA/nxtest-mgba.srm")
    ev = ["sync A: state=%s received=%s sent=%s %s" % (jA.get("state"), jA.get("received"), jA.get("sent"),
                                                       jA.get("reason") or "")]
    ev.append("phone Saves/GBA/nxtest-gba.srm md5 %s (handheld %s)" % (p_gba and md5_bytes(p_gba), fx["gba"]))
    ev.append("phone Saves/GBA/nxtest-mgba.srm md5 %s (handheld %s)" % (p_mgba and md5_bytes(p_mgba), fx["mgba"]))
    stray = f.run_as("find %s/Saves -name 'nxtest-*gba*'" % PRIV).split()
    ev.append("phone nxtest GBA saves: %s" % [s.replace(PRIV + "/", "") for s in stray])
    s1 = okA and p_gba and md5_bytes(p_gba) == fx["gba"] and p_mgba and md5_bytes(p_mgba) == fx["mgba"] \
        and len(stray) == 2
    res.add("1. handheld GBA + MGBA saves -> phone Saves/GBA", bool(s1), ev)

    # DS part 1: the .dsv crossed; the phone plays it in place
    p_ds = r.p_bytes("Saves/NDS/nxtest-ds.dsv")
    ev4 = ["phone Saves/NDS/nxtest-ds.dsv md5 %s (handheld %s)" % (p_ds and md5_bytes(p_ds), fx["ds"])]
    ds_in = bool(p_ds) and md5_bytes(p_ds) == fx["ds"]
    f.clear_crash_log()
    g = f.game(TEST_DIR + "/nxtest-ds.nds", "NDS")
    time.sleep(12)
    bat = f.read_json("battery")
    ev4.append("phone GAME NDS: game.json=%s battery.json=%s" % (
        g and {k: g.get(k) for k in ("state", "core")}, bat and {k: bat.get(k) for k in ("loadedFrom", "bytes")}))
    played = bool(bat) and str(bat.get("loadedFrom", "")).endswith("nxtest-ds.dsv")
    stop = f.game_stop()
    ev4.append("GAME_STOP: %s" % (stop and stop.get("state")))
    after = r.p_bytes("Saves/NDS/nxtest-ds.dsv")
    other = [s.replace(PRIV + "/", "") for s in f.run_as("find %s/Saves/NDS -name 'nxtest-ds*'" % PRIV).split()]
    ev4.append("after play: phone nxtest-ds files %s; .dsv %s bytes, footer %s" % (
        other, after and len(after), after and after.endswith(DSV_FOOTER_TAIL)))
    crash = f.crash_log()
    if crash:
        ev4.append("crash log: %s" % crash[:300].replace("\n", " | "))

    # DC part 1
    p_dc = r.p_bytes("Saves/DC/nxtest-dc.A1.bin")
    ev6 = ["A: phone Saves/DC/nxtest-dc.A1.bin md5 %s (handheld %s)" % (p_dc and md5_bytes(p_dc), fx["dc"])]
    dc_in = bool(p_dc) and md5_bytes(p_dc) == fx["dc"]

    # ---- phone changes -------------------------------------------------
    t_phone = odd(time.time() - 120)
    fx["gba2"] = r.p_put(os.urandom(131072), "Saves/GBA/nxtest-gba.srm", t_phone)
    fx["mgba2"] = r.p_put(os.urandom(131072), "Saves/GBA/nxtest-mgba.srm", t_phone)
    base = after if after and after.endswith(DSV_FOOTER_TAIL) else ds
    ds2 = bytes([base[0] ^ 0xFF]) + os.urandom(15) + base[16:]   # data changed, footer kept
    fx["ds2"] = r.p_put(ds2, "Saves/NDS/nxtest-ds.dsv", t_phone)
    fx["dc2"] = r.p_put(os.urandom(131072), "Saves/DC/nxtest-dc.A1.bin", t_phone)

    # ---- sync B: phone -> handheld ------------------------------------
    jB = r.sync("B (phone -> handheld)")
    okB = jB.get("state") == "done"
    h_gba = b.md5(B_SAVES + "/GBA/nxtest-gba.srm")
    h_mgba = b.md5(B_SAVES + "/MGBA/nxtest-mgba.srm")
    cross = [p for p in (B_SAVES + "/MGBA/nxtest-gba.srm", B_SAVES + "/GBA/nxtest-mgba.srm") if b.exists(p)]
    ev = ["sync B: state=%s received=%s sent=%s %s" % (jB.get("state"), jB.get("received"), jB.get("sent"),
                                                       jB.get("reason") or "")]
    ev.append("handheld Saves/GBA/nxtest-gba.srm md5 %s (phone %s)" % (h_gba, fx["gba2"]))
    ev.append("handheld Saves/MGBA/nxtest-mgba.srm md5 %s (phone %s)" % (h_mgba, fx["mgba2"]))
    ev.append("copies in the other emulator folder: %s" % (cross or "none"))
    res.add("2. changed phone saves -> each game's own handheld folder",
            okB and h_gba == fx["gba2"] and h_mgba == fx["mgba2"] and not cross, ev)

    h_ds = r.b_bytes(B_SAVES + "/NDS/nxtest-ds.dsv")
    h_extra = b.sh("ls %s 2>/dev/null" % q(B_SAVES + "/NDS")).split("\n")
    h_extra = [x for x in h_extra if x.startswith("nxtest-")]
    ev4.append("B: handheld Saves/NDS/nxtest-ds.dsv md5 %s (phone %s), footer %s; handheld nxtest NDS files %s" % (
        h_ds and md5_bytes(h_ds), fx["ds2"], h_ds and h_ds.endswith(DSV_FOOTER_TAIL), h_extra))
    ds_back = bool(h_ds) and md5_bytes(h_ds) == fx["ds2"] and h_ds.endswith(DSV_FOOTER_TAIL)
    res.add("4. DS .dsv crosses, plays in place on the phone, changed .dsv returns",
            ds_in and played and ds_back and not crash, ev4)

    h_dc = b.md5(B_SAVES + "/DC/nxtest-dc.A1.bin")
    ev6.append("B: handheld Saves/DC/nxtest-dc.A1.bin md5 %s (phone %s)" % (h_dc, fx["dc2"]))
    dcx = b.exists(B_SAVES + "/DCX/nxtest-dc.A1.bin")
    ev6.append("copy in Saves/DCX: %s" % dcx)
    res.add("6. Dreamcast per-game card crosses both ways", dc_in and h_dc == fx["dc2"], ev6)

    # ---- sync C: nothing changed -------------------------------------
    watch_b = [B_SAVES + "/GBA/nxtest-gba.srm", B_SAVES + "/MGBA/nxtest-mgba.srm",
               B_SAVES + "/NDS/nxtest-ds.dsv", B_SAVES + "/DC/nxtest-dc.A1.bin"]
    watch_p = ["Saves/GBA/nxtest-gba.srm", "Saves/GBA/nxtest-mgba.srm", "Saves/NDS/nxtest-ds.dsv",
               "Saves/DC/nxtest-dc.A1.bin"]
    snap = lambda: ([(b.md5(p), b.mtime(p)) for p in watch_b],
                    [(f.md5_private(p), f.mtime_private(p)) for p in watch_p])
    before = snap()
    jC = r.sync("C (unchanged)")
    after_c = snap()
    ev = ["sync C: state=%s received=%s sent=%s notes=%r" % (jC.get("state"), jC.get("received"), jC.get("sent"),
                                                             (jC.get("notes") or "")[:200])]
    ev.append("handheld mtimes (odd-second fixtures) %s -> %s" % ([m for _, m in before[0]], [m for _, m in after_c[0]]))
    ev.append("phone mtimes %s -> %s" % ([m for _, m in before[1]], [m for _, m in after_c[1]]))
    ev.append("nxtest files unchanged: %s" % (before == after_c))
    res.add("3. second unchanged sync moves no save",
            jC.get("state") == "done" and jC.get("received") == 0 and jC.get("sent") == 0 and before == after_c, ev)



def roms_scenario(r, f, b, res):
    """Scenario 5 (ROMs on), run last: it syncs the whole ROM library."""
    # ---- sync D: ROMs on (last) ----------------------------------------
    f.sh("cp %s %s" % (q(P_GBA + "/Flappy Bird.gba"), q(P_GBA + "/nxtest-rp.gba")))
    b.sh("cp %s %s && sync" % (q(B_MGBA + "/Flappy Bird.gba"), q(B_MGBA + "/nxtest-rb.gba")))
    # make the two differ from each other and from Flappy Bird so md5s prove the copy (pad bytes past the ROM end)
    f.sh("printf nxtest-rp >> %s" % q(P_GBA + "/nxtest-rp.gba"))
    b.sh("printf nxtest-rb >> %s && sync" % q(B_MGBA + "/nxtest-rb.gba"))
    m_rp, m_rb = f.md5(P_GBA + "/nxtest-rp.gba"), b.md5(B_MGBA + "/nxtest-rb.gba")
    jD = r.sync("D (ROMs on)", roms=True, timeout=1800)
    rp_b = b.sh("find %s -name 'nxtest-rp.gba'" % q(B_ROMS), timeout=180).split("\n")
    rp_b = [x for x in rp_b if x.strip()]
    rb_p = f.sh("find %s -name 'nxtest-rb.gba'" % q(PHONE_ROMS), timeout=180).split("\n")
    rb_p = [x for x in rb_p if x.strip()]
    ev = ["sync D: state=%s received=%s sent=%s roms=%s elapsed=%ss %s" % (
        jD.get("state"), jD.get("received"), jD.get("sent"), jD.get("roms"), jD.get("elapsed"),
        (jD.get("reason") or "") + " " + (jD.get("detail") or ""))]
    ev.append("phone nxtest-rp.gba on the Brick: %s (md5 %s, phone %s)" % (
        rp_b, b.md5(B_GBA + "/nxtest-rp.gba"), m_rp))
    ev.append("Brick nxtest-rb.gba on the phone: %s (md5 %s, Brick %s)" % (
        rb_p, f.md5(P_GBA + "/nxtest-rb.gba"), m_rb))
    ok5 = (jD.get("state") == "done" and rp_b == [B_GBA + "/nxtest-rp.gba"] and b.md5(B_GBA + "/nxtest-rp.gba") == m_rp
           and rb_p == [P_GBA + "/nxtest-rb.gba"] and f.md5(P_GBA + "/nxtest-rb.gba") == m_rb)
    res.add("5. ROMs on: phone GBA ROM -> Brick GBA only; Brick MGBA ROM -> phone GBA", ok5, ev)


def write_report(path, res, cleanup):
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    lines = ["# Device Sync scenarios (Fold <-> Brick, Wi-Fi) - %s" % time.strftime("%Y-%m-%d %H:%M"), "",
             "| Scenario | Result |", "| --- | --- |"]
    for scen, ok, _ in sorted(res.rows):
        lines.append("| %s | %s |" % (scen, "PASS" if ok else "FAIL"))
    lines += ["", "## Evidence", ""]
    for scen, ok, ev in sorted(res.rows):
        lines.append("### %s - %s" % (scen, "PASS" if ok else "FAIL"))
        lines += ["- " + e.replace("|", "\\|") for e in ev]
        lines.append("")
    lines += ["## Syncs (phone sync.json)", ""]
    for label, j in res.syncs:
        lines.append("- **%s**: %s" % (label, j))
    lines += ["", "## Notes", ""] + ["- " + n for n in res.notes]
    lines += ["", "## Cleanup", "", cleanup]
    with open(path, "w") as fh:
        fh.write("\n".join(lines) + "\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--fold", default="adb-R5GL66XJS2F-uwbsP9._adb-tls-connect._tcp")
    ap.add_argument("--brick", default="5c000c8997414781d1d")
    ap.add_argument("--out", default=os.path.join(os.getcwd(), ".local/device-tests/sync-test-%s.md"
                                                  % time.strftime("%Y-%m-%d")))
    ap.add_argument("--skip-roms", action="store_true")
    ap.add_argument("--only-roms", action="store_true", help="scenario 5 alone")
    ap.add_argument("--no-cleanup", action="store_true")
    args = ap.parse_args()
    res = Results()
    f, b = Fold(args.fold), Brick(args.brick)
    try:
        run(args, res)
    except BrickGone as e:
        res.notes.append("STOP: %s" % e)
        print("STOP: %s" % e)
    except Exception as e:
        import traceback
        traceback.print_exc()
        res.notes.append("harness exception: %r" % e)
    cleanup = "skipped (--no-cleanup)"
    if not args.no_cleanup:
        parts = []
        try:
            if f.game_running():
                f.game_stop()
            parts.append("Fold removed %d nxtest file(s)" % len(f.cleanup_nxtest()))
            left = f.sh("find /sdcard/NXRedux -name 'nxtest-*'").strip() + f.run_as(
                "find %s -name 'nxtest-*'" % PRIV).strip()
            parts.append("Fold nxtest left: %s" % (left or "none"))
        except Exception as e:
            parts.append("Fold cleanup error %r" % e)
        try:
            if b.present():
                if b.pidof("sync.elf"):
                    b.stop_device_sync()
                if b.minarch_pid():
                    b.kill_game()
                parts.append("Brick removed %d nxtest file(s)" % len(b.cleanup_nxtest()))
                parts.append("Brick nextui alive: %s" % b.nextui_alive())
        except Exception as e:
            parts.append("Brick cleanup error %r" % e)
        cleanup = "; ".join(parts)
    print(cleanup)
    write_report(args.out, res, cleanup)
    print("report: %s" % args.out)
    fails = [s for s, ok, _ in res.rows if not ok]
    print("\n%d scenario(s), %d failed: %s" % (len(res.rows), len(fails), fails))
    return 0 if not fails else 1


if __name__ == "__main__":
    sys.exit(main())
