#!/usr/bin/env python3
"""Wrap a libretro Dolphin save state in standalone Dolphin's .sav headers, so both start from the same moment.

The libretro core's state (retro_serialize) is Dolphin's raw State::DoState stream; a standalone .sav is that stream
behind a 24-byte legacy header (game id, time), a version header (cookie 0xBAADBABE + state version, version string)
and a 16-byte extended header (uncompressed). Both sides must be on the same state version (192 for Dolphin 2609 and
libretro/dolphin b93001ed; check STATE_VERSION in Source/Core/Core/State.cpp after a bump).

    python3 tools/device-tests/dolphin_sav.py smg2.state SB4E01.s01 --game-id SB4E01
    adb push SB4E01.s01 /sdcard/Android/data/org.dolphinemu.dolphinemu/files/StateSaves/

Standalone Dolphin needs [Core] EnableSaveStates = True in Config/Dolphin.ini to show Load State; the file named
<GAMEID>.s01 is its Slot 1.
"""
import argparse
import struct
import time

COOKIE_BASE = 0xBAADBABE


def wrap(stream, game_id, state_version, version_string):
    legacy = game_id.encode("ascii").ljust(6, b"\0")[:6] + b"\0\0" + struct.pack("<I", 0) + b"\0" * 4 + \
        struct.pack("<d", time.time())
    assert len(legacy) == 24
    ver = version_string.encode("ascii")
    version = struct.pack("<II", COOKIE_BASE + state_version, len(ver)) + ver
    extended = struct.pack("<HHIQ", 1, 0, 0, len(stream))   # header version 1, uncompressed, no payload offset
    return legacy + version + extended + stream


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("state", help="the libretro core's state file (e.g. <game>.state from .userdata/shared/WII-dolphin)")
    p.add_argument("out", help="the .sav / .sNN to write")
    p.add_argument("--game-id", required=True, help="the disc's game id, e.g. SB4E01 (Super Mario Galaxy 2, USA)")
    p.add_argument("--state-version", type=int, default=192)
    p.add_argument("--version-string", default="2609")
    a = p.parse_args()
    with open(a.state, "rb") as f:
        stream = f.read()
    if stream[:1] not in (b"\x00", b"\x01"):
        raise SystemExit("%s does not start like a raw Dolphin state (is_wii byte)" % a.state)
    with open(a.out, "wb") as f:
        f.write(wrap(stream, a.game_id, a.state_version, a.version_string))
    print("wrote %s (%d bytes of state)" % (a.out, len(stream)))


if __name__ == "__main__":
    main()
