#!/usr/bin/env python3
"""Save-state grid: do auto states cross between the Brick (minarch) and the phone (NX Redux mobile)?

For each core both devices pin the same (plus mupen64plus_next, pinned differently, as an expected-fail control):
copy the smallest ROM the Brick has for the core's tag to nxtest-<core>.<ext> on both devices, then
  - phone fresh: GAME ~20 s, GAME_STOP -> the phone's auto state (and its path, the phone's naming);
  - Brick fresh: launch ~20 s, sleep (auto state), kill -> the Brick's auto state;
  - B->P: the Brick state goes to the phone's auto-state path, GAME slot 9: resume.json ok, the game still runs
    ~8 s later, no crash in `logcat -b crash`;
  - P->B: the phone state goes to the Brick's auto-state path, resume slot 9, launch: resume file consumed, no state
    error in the log, minarch still up ~10 s later.
Same tag on both sides. Output: a Markdown table on stdout and in .local/device-tests/state-grid-<date>.md.
Every file it creates is named nxtest-*; both devices are cleaned at the end.

usage: state_grid.py [--fold SERIAL] [--brick SERIAL] [--only core,core] [--out FILE]
"""

import argparse
import os
import sys
import tempfile
import time

from brick import AUTO_RESUME_PATH, ERROR_MARKERS, RESUME_SLOT_PATH, SDCARD, Brick, BrickError, BrickGone, q
from fold import PRIV, ROMS as PHONE_ROMS, TEST_DIR, Fold

B_ROMS = SDCARD + "/Roms"
PLAY_S = 20

# core, tag (same on both devices, same core), Brick ROM folders (first with a ROM wins), single-file extensions,
# file names to skip (BIOS sets, the 4 MB Classic NES Pac-Man that crashes gpSP)
GRID = [
    ("gambatte", "GB", ["Game Boy (GB)"], ["gb", "gbc", "dmg"], []),
    ("fceumm", "FC", ["Nintendo ES (FC)"], ["nes"], []),
    ("snes9x", "SFC", ["Super Nintendo ES (SFC)"], ["sfc", "smc"], []),
    ("genesis_plus_gx", "GPGX", ["Sega Genesis (GPGX)"], ["md", "gen", "smd", "bin"], []),
    ("mgba", "MGBA", ["Game Boy Advance (MGBA)"], ["gba"], ["Pac-Man.gba"]),
    ("gpsp", "GBA", ["Game Boy Advance (GBA)"], ["gba"], ["Pac-Man.gba"]),
    ("pcsx_rearmed", "PS", ["Sony PlayStation (PS)"], ["chd", "pbp", "PBP", "iso"], []),
    ("swanstation", "PSX", ["Sony PlayStation (PSX)"], ["chd", "pbp", "PBP", "iso"], []),
    ("picodrive", "MD", ["Sega Genesis (MD)"], ["md", "gen", "smd", "bin"], []),   # the MD pak and the phone's MD run picodrive
    ("picodrive", "32X", ["Sega 32X (32X)"], ["32x"], []),
    ("mednafen_pce_fast", "PCE", ["TurboGrafx-16 (PCE)"], ["pce"], []),
    ("mednafen_vb", "VB", ["Virtual Boy (VB)"], ["vb", "vboy"], []),
    ("mednafen_wswan", "WSC", ["Wonderswan Color (WSC)"], ["ws", "wsc"], []),
    ("mednafen_supafaust", "SUPA", ["Super Nintendo ES (SUPA)"], ["sfc", "smc"], []),
    ("race", "NGPC", ["Neo Geo Pocket Color (NGPC)"], ["ngc", "ngpc", "npc", "ngp"], []),
    ("handy", "LYNX", ["Atari Lynx (LYNX)"], ["lnx", "lyx"], []),
    ("stella2014", "A2600", ["Atari 2600 (A2600)"], ["a26", "bin"], []),
    ("prosystem", "A7800", ["Atari 7800 (A7800)"], ["a78", "bin"], []),
    ("a5200", "A5200", ["Atari 5200 (A5200)"], ["a52", "bin"], []),
    ("pokemini", "PKM", ["Pokémon mini (PKM)"], ["min"], []),
    ("fake08", "P8", ["Pico-8 (P8)"], ["p8", "png"], []),
    ("prboom", "PRBOOM", ["Doom (PRBOOM)"], ["wad"], []),
    ("gearcoleco", "COLECO", ["Colecovision (COLECO)"], ["col", "cv", "rom"], []),
    ("fbneo", "FBN", ["Arcade (FBN)"], ["zip"], ["neogeo.zip"]),
    ("flycast", "DC", ["DreamCast (DC)"], ["chd", "cdi", "gdi"], []),
    ("ppsspp", "PSP", ["Sony Playstation Portable (PSP)"], ["iso", "cso"], []),
    ("mupen64plus_next", "N64", ["Nintendo 64 (N64)"], ["n64", "z64", "v64"], []),
]


def pick_rom(b, folders, exts, skip):
    """Smallest ROM with one of exts in the first folder that has one: (folder, name, size) or None."""
    for folder in folders:
        d = B_ROMS + "/" + folder
        out = b.sh("cd %s 2>/dev/null && for f in *; do [ -f \"$f\" ] && echo \"$(wc -c < \"$f\")|$f\"; done" % q(d),
                   timeout=240)
        roms = []
        for line in out.split("\n"):
            size, _, name = line.partition("|")
            ext = name.rsplit(".", 1)[-1] if "." in name else ""
            if size.strip().isdigit() and ext in exts and not name.startswith("nxtest-") \
                    and not name.startswith(".") and name not in skip:
                roms.append((int(size), name))
        if roms:
            size, name = min(roms)
            return folder, name, size
    return None


class Row:
    def __init__(self, core, tag):
        self.core, self.tag = core, tag
        self.rom = ""
        self.b_size = self.p_size = None
        self.bp = self.pb = "-"
        self.notes = []

    def note(self, s):
        self.notes.append(s)
        print("    %s: %s" % (self.core, s), flush=True)


def phone_quit(f):
    """GAME_STOP, then BACK presses if a game (or its error page) is still up."""
    j = f.game_stop(timeout=20)
    if f.game_running():
        for _ in range(3):
            f.sh("input keyevent 4")
            time.sleep(2)
            if not f.game_running():
                break
    return j


def phone_play(f, path, tag, slot=None):
    """GAME, wait for running (90 s budget). Returns (game.json, resume.json); resume.json only with a slot."""
    f.clear_crash_log()
    g = f.game(path, tag, slot=slot, timeout=90)
    resume = None
    if slot is not None and g and g.get("state") == "running":
        resume = f.wait_for(lambda: f.read_json("resume"), 30)
    return g, resume


def run_core(f, b, row, folders, exts, skip, tmp):
    core, tag = row.core, row.tag
    picked = pick_rom(b, folders, exts, skip)
    if not picked:
        row.bp = row.pb = "no ROM"
        row.note("no ROM in %s" % ", ".join(folders))
        return
    folder, name, size = picked
    ext = name.rsplit(".", 1)[-1]
    row.rom = "%s (%d KB)" % (name, size // 1024)
    stem = "nxtest-" + core
    b_rom = "%s/%s/%s.%s" % (B_ROMS, folder, stem, ext)
    p_rom = "%s/%s.%s" % (TEST_DIR, stem, ext)
    b_state = Brick.state_path(tag, core, stem + "." + ext)
    print("== %s (%s): %s" % (core, tag, row.rom), flush=True)

    # ROM copies
    b.sh("cp %s %s && sync" % (q(B_ROMS + "/" + folder + "/" + name), q(b_rom)), timeout=600)
    if b.size(b_rom) != size:
        row.bp = row.pb = "setup"
        row.note("Brick ROM copy failed")
        return
    f.sh("mkdir -p %s" % TEST_DIR)
    hits = [h for h in f.sh("find %s -name %s -type f" % (q(PHONE_ROMS), q(name)), timeout=120).split("\n") if h]
    same = [h for h in hits if f.sh("stat -c %%s %s" % q(h)).strip() == str(size)]
    if same:
        f.sh("cp %s %s" % (q(same[0]), q(p_rom)), timeout=600)
    else:
        local = os.path.join(tmp, stem + "." + ext)
        b.pull(B_ROMS + "/" + folder + "/" + name, local)
        f.push(local, p_rom)
        os.remove(local)
    if f.sh("stat -c %%s %s" % q(p_rom)).strip() != str(size):
        row.bp = row.pb = "setup"
        row.note("phone ROM copy failed")
        return

    # phone fresh run -> the phone's auto state and its path
    p_local = os.path.join(tmp, "p.state")
    p_rel = None
    g, _ = phone_play(f, p_rom, tag)
    if not g or g.get("state") != "running":
        row.note("phone fresh launch: %s" % (g or "no game.json in 90 s"))
        phone_quit(f)
    else:
        if g.get("core") != core:
            row.note("phone ran core %s, not %s" % (g.get("core"), core))
        time.sleep(PLAY_S)
        alive = f.game_running()
        crash = f.crash_log()
        stop = phone_quit(f)
        auto = stop and stop.get("autoState")
        if not alive or crash:
            row.note("phone fresh run died%s" % (": " + crash[:200].replace("\n", " | ") if crash else ""))
        if auto and isinstance(auto, str) and auto.startswith(PRIV + "/"):
            p_rel = auto[len(PRIV) + 1:]
            if f.pull_private(p_rel, p_local):
                row.p_size = os.path.getsize(p_local)
            else:
                row.note("phone autoState %s missing" % p_rel)
        else:
            row.note("phone GAME_STOP wrote no auto state: %s" % stop)

    # Brick fresh run -> the Brick's auto state
    b_local = os.path.join(tmp, "b.state")
    try:
        pid = b.launch_rom(tag, b_rom, timeout=45)
        for _ in range(PLAY_S // 5):
            time.sleep(4)
            b.press("START")
        if b.minarch_pid() != pid:
            row.note("Brick fresh run died (log: %s)" % b.game_log(tag)[-200:].replace("\n", " | "))
        else:
            changed = b.sleep_autosave(b_state, timeout=45)
            b.kill_game()
            if changed and b.size(b_state):
                b.pull(b_state, b_local)
                row.b_size = os.path.getsize(b_local)
            else:
                row.note("Brick sleep wrote no auto state")
    except BrickGone:
        raise
    except BrickError as e:
        row.note("Brick fresh run: %s" % e)
    if b.minarch_pid():
        b.kill_game()

    # B -> P
    if row.b_size is None:
        row.bp = "n/a"
    elif p_rel is None:
        row.bp = "n/a"
        row.note("B->P skipped: no phone auto-state path")
    else:
        f.push_private(b_local, p_rel)
        g, resume = phone_play(f, p_rom, tag, slot=9)
        if not g or g.get("state") != "running":
            row.bp = "FAIL"
            row.note("B->P: phone launch %s" % g)
        else:
            time.sleep(8)
            alive = f.game_running() and (f.read_json("game") or {}).get("state") == "running"
            crash = f.crash_log()
            ok = bool(resume and resume.get("ok"))
            row.bp = "PASS" if ok and alive and not crash else "FAIL"
            if not ok:
                row.note("B->P: resume.json %s" % resume)
            if not alive:
                row.note("B->P: game not running 8 s after the resume")
            if crash:
                row.note("B->P crash: %s" % crash[:300].replace("\n", " | "))
        phone_quit(f)

    # P -> B
    if row.p_size is None:
        row.pb = "n/a"
    else:
        try:
            b.push(p_local, b_state)
            b.sh("sync")
            b.set_resume_slot(9)
            pid = b.launch_rom(tag, b_rom, timeout=45)
            time.sleep(10)
            consumed = not b.exists(RESUME_SLOT_PATH)
            log = b.game_log(tag)
            bad = [l.strip() for l in log.split("\n") if any(m in l for m in ERROR_MARKERS)]
            alive = b.minarch_pid() == pid
            row.pb = "PASS" if consumed and not bad and alive else "FAIL"
            if not consumed:
                row.note("P->B: resume_slot.txt not consumed")
            if bad:
                row.note("P->B: %s" % " | ".join(bad)[:300])
            if not alive:
                row.note("P->B: minarch gone 10 s after the resume (log tail: %s)" % log[-200:].replace("\n", " | "))
            if "Resume state not accepted yet" in log:
                row.note("P->B: state retried ('Resume state not accepted yet')")
        except BrickGone:
            raise
        except BrickError as e:
            row.pb = "FAIL"
            row.note("P->B: %s" % e)
        if b.minarch_pid():
            b.kill_game()
        b.sh("rm -f %s" % RESUME_SLOT_PATH)

    # per-core cleanup (ROM copies are big)
    b.sh("rm -f %s" % q(b_rom))
    f.sh("rm -f %s" % q(p_rom))


def table(rows):
    out = ["| core | tag | ROM | Brick state | phone state | B->P | P->B | note |",
           "| --- | --- | --- | --- | --- | --- | --- | --- |"]
    for r in rows:
        out.append("| %s | %s | %s | %s | %s | %s | %s | %s |" % (
            r.core, r.tag, r.rom.replace("|", "/") or "-",
            "-" if r.b_size is None else r.b_size, "-" if r.p_size is None else r.p_size,
            r.bp, r.pb, "; ".join(r.notes).replace("|", "/") or ""))
    return "\n".join(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--fold", default="adb-R5GL66XJS2F-uwbsP9._adb-tls-connect._tcp")
    ap.add_argument("--brick", default="5c000c8997414781d1d")
    ap.add_argument("--only", default="")
    ap.add_argument("--out", default=os.path.join(os.getcwd(), ".local/device-tests/state-grid-%s.md"
                                                  % time.strftime("%Y-%m-%d")))
    args = ap.parse_args()
    f, b = Fold(args.fold), Brick(args.brick)
    f.require_present()
    b.require_present()
    if f.game_running():
        raise SystemExit("FAIL: a game is open on the Fold")
    if b.minarch_pid() or b.pidof("sync.elf") or not b.nextui_alive():
        raise SystemExit("FAIL: the Brick must be on the NextUI menu")
    if b.exists(AUTO_RESUME_PATH) and "/nxtest-" not in b.sh("cat %s" % AUTO_RESUME_PATH):
        raise SystemExit("FAIL: %s points at a real game; refusing" % AUTO_RESUME_PATH)
    if "raHardcoreMode=1" in b.sh("cat %s" % q(SDCARD + "/.userdata/shared/minuisettings.txt")):
        raise SystemExit("FAIL: RetroAchievements hardcore mode is on: minarch blocks state loads")
    b.wake()
    only = set(x for x in args.only.split(",") if x)
    tmp = tempfile.mkdtemp(prefix="nxtest-grid-")
    print("stale removed: brick %s, fold %s" % (b.cleanup_nxtest(), f.cleanup_nxtest()))
    f.reset()
    rows = []
    stopped = None
    try:
        for core, tag, folders, exts, skip in GRID:
            if only and core not in only:
                continue
            row = Row(core, tag)
            rows.append(row)
            try:
                run_core(f, b, row, folders, exts, skip, tmp)
            except BrickGone:
                raise
            except Exception as e:
                import traceback
                traceback.print_exc()
                row.note("harness exception: %r" % e)
                try:
                    if f.game_running():
                        phone_quit(f)
                    if b.minarch_pid():
                        b.kill_game()
                except Exception as e2:
                    row.note("recovery failed: %r" % e2)
            print("   -> B->P %s, P->B %s" % (row.bp, row.pb), flush=True)
    except BrickGone as e:
        stopped = "STOP: %s" % e
        print(stopped)
    parts = []
    try:
        if f.game_running():
            phone_quit(f)
        parts.append("Fold removed %d nxtest file(s)" % len(f.cleanup_nxtest()))
    except Exception as e:
        parts.append("Fold cleanup error %r" % e)
    try:
        if b.present():
            if b.minarch_pid():
                b.kill_game()
            parts.append("Brick removed %d nxtest file(s); nextui alive %s" % (len(b.cleanup_nxtest()),
                                                                            b.nextui_alive()))
    except Exception as e:
        parts.append("Brick cleanup error %r" % e)
    md = ["# Save-state grid (Brick <-> Fold) - %s" % time.strftime("%Y-%m-%d %H:%M"), "",
          "B->P: the Brick's auto state resumed on the phone (resume.json ok, still running 8 s later, no crash). "
          "P->B: the phone's auto state resumed on the Brick (resume file consumed, no state error, minarch up 10 s "
          "later). States in bytes. mupen64plus_next is pinned differently on the two devices (expected-fail control).",
          "", table(rows), ""]
    if stopped:
        md.append(stopped)
    md.append("Cleanup: " + "; ".join(parts))
    text = "\n".join(md) + "\n"
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w") as fh:
        fh.write(text)
    print("\n" + text)
    print("report: %s" % args.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
