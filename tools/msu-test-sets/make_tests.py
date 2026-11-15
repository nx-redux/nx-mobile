"""Builds MSU-1 (SNES) and MSU-MD (Mega Drive) test sets.

usage: python3 -I make_tests.py <out_dir> <msu-md-sample.bin>
"""
import os, shutil, struct, subprocess, sys

out, md_sample = sys.argv[1], sys.argv[2]
RATE = 44100


def tone(seconds, expr):
    """Raw s16le stereo 44.1 kHz from an ffmpeg aevalsrc expression."""
    return subprocess.run(
        ["ffmpeg", "-v", "error", "-f", "lavfi", "-i",
         f"aevalsrc='{expr}|{expr}':s={RATE}:d={seconds}",
         "-f", "s16le", "-ac", "2", "-ar", str(RATE), "-"],
        check=True, capture_output=True).stdout


# Rising notes so each track is told apart by ear: track N beeps N times, then holds a chord.
def track_audio(n, seconds=12):
    beeps = "+".join(f"between(t,{0.4*i},{0.4*i+0.2})" for i in range(n))
    f = 220 * (2 ** ((n - 1) * 4 / 12))
    expr = (f"0.35*sin(2*PI*880*t)*({beeps})"
            f"+0.25*gte(t,{0.4*n+0.3})*(sin(2*PI*{f}*t)+0.6*sin(2*PI*{f*1.25}*t)+0.5*sin(2*PI*{f*1.5}*t))/2.1")
    return tone(seconds, expr)


# ---------------------------------------------------------------- MSU-1 (SNES)
snes = os.path.join(out, "SFC")
os.makedirs(snes, exist_ok=True)
NAME = "MSU1 Test"

code = bytes.fromhex(
    "78 18 FB"              # sei; clc; xce (native, 8-bit A/X)
    "E2 30"                 # sep #$30
    "A9 80 8D 00 21"        # lda #$80; sta $2100   forced blank
    "9C 21 21"              # stz $2121             CGRAM address 0
    "AD 02 20 C9 53 D0 4B"  # lda $2002; cmp #'S'; bne nomsu
    "9C 00 20 9C 01 20 9C 02 20 9C 03 20"  # data seek to 0 (write $2003 starts it)
    "AD 00 20 29 80 D0 F9"  # wait: data busy
    "AD 01 20 C9 4E D0 25"  # lda $2001; cmp #'N'; bne bad
    "A9 E0 8D 22 21 A9 03 8D 22 21"  # green 0x03E0
    # play: track 1, wait audio busy, volume, play+repeat
    "A9 01 8D 04 20 9C 05 20"
    "AD 00 20 29 40 D0 F9"
    "A9 FF 8D 06 20 A9 03 8D 07 20"
    "80 14"                 # bra on
    # bad: yellow 0x03FF, then play anyway
    "A9 FF 8D 22 21 A9 03 8D 22 21 80 D9"
    # nomsu: red 0x001F
    "A9 1F 8D 22 21 9C 22 21"
    # on: screen on, spin
    "A9 0F 8D 00 21 80 FE"
)
# Branch targets above are hand-assembled; check them before trusting the ROM.
rom = bytearray(b"\xff" * 0x8000)
rom[0:len(code)] = code
rom[0x7F00] = 0x40                      # rti for every other vector
title = NAME.upper().encode().ljust(21, b" ")
rom[0x7FC0:0x7FD5] = title
rom[0x7FD5] = 0x20                      # LoROM, slow
rom[0x7FD6] = 0x00                      # ROM only
rom[0x7FD7] = 0x05                      # 32 KB
rom[0x7FD8] = 0x00
rom[0x7FD9] = 0x01                      # North America
rom[0x7FDA] = 0x00
rom[0x7FDB] = 0x00
for off in range(0x7FE4, 0x8000, 2):    # vectors -> rti at $FF00
    rom[off:off + 2] = struct.pack("<H", 0xFF00)
rom[0x7FFC:0x7FFE] = struct.pack("<H", 0x8000)   # emulation RESET
rom[0x7FDC:0x7FE0] = b"\xff\xff\x00\x00"
cs = sum(rom) & 0xFFFF
rom[0x7FDC:0x7FE0] = struct.pack("<HH", cs ^ 0xFFFF, cs)
open(os.path.join(snes, f"{NAME}.sfc"), "wb").write(rom)
open(os.path.join(snes, f"{NAME}.msu"), "wb").write(b"NX-REDUX MSU-1 DATA OK".ljust(4096, b"\0"))
for n in (1, 2):
    with open(os.path.join(snes, f"{NAME}-{n}.pcm"), "wb") as f:
        f.write(b"MSU1" + struct.pack("<I", 0) + track_audio(n))

# ---------------------------------------------------------------- MSU-MD
md = os.path.join(out, "MD")
os.makedirs(md, exist_ok=True)
MDNAME = "MSU-MD Sample"
shutil.copy(md_sample, os.path.join(md, f"{MDNAME}.md"))   # PicoDrive's find_msu: gen/smd/md/32x only
cue = []
for n in (1, 2, 3):
    fn = f"{MDNAME} (Track {n:02d}).bin"
    open(os.path.join(md, fn), "wb").write(track_audio(n))
    cue += [f'FILE "{fn}" BINARY', f"  TRACK {n:02d} AUDIO", "    INDEX 01 00:00:00"]
open(os.path.join(md, f"{MDNAME}.cue"), "w").write("\r\n".join(cue) + "\r\n")
print("ok")
