"""Build androidApp/src/main/assets/bios/known.tsv from retrobios metadata (emulators/*.yml + database.json).

Usage (from the repo root; needs PyYAML):
  python3 tools/bios-list/build_bios_list.py --retrobios <sparse checkout> [--out <tsv>]

Only names, sizes and md5s are read; the retrobios BIOS files themselves are never needed or fetched.
"""
import argparse
import json
import os
import sys

import yaml

# NX Redux core id -> retrobios profile name, for every core CoreRegistry ships. Dolphin is left out: its BIOS
# (GC IPL) sits at nested paths we do not map, and its flat entries are Wii NAND/key files, not Bios/GC files.
CORE_PROFILES = {c: c for c in (
    "a5200 armsx2 azahar fake08 fbneo fceumm flycast gambatte gearcoleco genesis_plus_gx gpsp handy "
    "mednafen_pce_fast mednafen_supafaust mednafen_vb mednafen_wswan mgba mupen64plus_next pcsx_rearmed picodrive "
    "pokemini ppsspp prboom prosystem race snes9x stella2014 swanstation"
).split()}
CORE_PROFILES["melondsds"] = "melonds_ds"

# The console a profile entry without a `system` belongs to.
CORE_DEFAULT = {
    "a5200": "A5200", "armsx2": "PS2", "azahar": "3DS", "dolphin": "GC", "fake08": "P8", "fbneo": "FBN",
    "fceumm": "FC", "flycast": "DC", "gambatte": "GB", "gearcoleco": "COLECO", "genesis_plus_gx": "MD", "gpsp": "GBA",
    "handy": "LYNX", "mednafen_pce_fast": "PCE", "mednafen_supafaust": "SFC", "mednafen_vb": "VB",
    "mednafen_wswan": "WSC", "mgba": "GBA", "mupen64plus_next": "N64", "pcsx_rearmed": "PS", "picodrive": "MD",
    "pokemini": "PKM", "ppsspp": "PSP", "prboom": "PRBOOM", "prosystem": "A7800", "race": "NGP", "snes9x": "SFC",
    "stella2014": "A2600", "swanstation": "PS", "melonds_ds": "NDS",
}

ARCADE = ("snk-neogeo-mvs snk-neogeo-aes snk-neogeo-cd igs-pgm super-kaneko-nova deco-cassette namco-na1 "
          "namco-nb1 konami-bubsys sega-system16b nmk16 taito-cchip midway-mcr ym2608 arcade atari-system1").split()

SYSTEM_TAGS = {
    "nintendo-gb": "GB", "nintendo-gbc": "GBC", "nintendo-gba": "GBA", "nintendo-sgb": "SGB",
    "nintendo-nes": "FC", "nintendo-fds": "FDS", "nintendo-satellaview": "SFC", "nintendo-sufami-turbo": "SFC",
    "nintendo-ds": "NDS", "nintendo-dsi": "NDS", "nintendo-3ds": "3DS", "nintendo-gamecube": "GC",
    "nintendo-pokemon-mini": "PKM", "nintendo-64": "N64", "nintendo-64dd": "N64",
    "sega-megadrive": "MD", "sega-megacd": "SEGACD", "sega-mastersystem": "SMS", "sega-gamegear": "GG",
    "sega-32x": "32X", "sega-dreamcast": "DC", "sega-naomi": "DC", "sega-naomi2": "DC", "sega-atomiswave": "DC",
    "sega-systemsp": "DC", "colecovision": "COLECO", "atari-lynx": "LYNX", "atari-7800": "A7800",
    "atari-5200": "A5200", "doom": "PRBOOM", "heretic": "PRBOOM",
    **{s: "FBN" for s in ARCADE},
}

# Systems a core lists that NX Redux runs on another core, or not at all: their entries are left out.
IGNORED = {
    "*": {"sega-triforce", "coleco-adam"},
    # FBNeo here runs arcade sets only; its console BIOS copies belong to the consoles' own cores.
    # CPS3 sets are games (each lists its own SIMMs), not a shared BIOS.
    "fbneo": {"capcom-cps3", "fairchild-channelf", "coleco-colecovision", "sinclair-zx-spectrum", "msx", "nintendo-fds", "snk-ngp",
              "snes", "nintendo-gba", "bally-astrocade"},
}

# Folders a core reads under the system dir whose files the user keeps flat in Bios/<TAG>/ (BiosFolders.subdir).
FLAT_SUBDIRS = ("dc/", "pcsx2/bios/")

# Not BIOS: configs, fonts, palettes, samples, text.
NON_BIOS_EXT = {"cfg", "ttf", "otf", "pal", "wav", "txt", "xml", "json", "png", "ini", "dat", "md"}

# Optional files whose presence changes how a game boots (boot animation / TMSS screen).
BOOT_CHANGE = {"gb_bios.bin", "gbc_bios.bin", "sgb_bios.bin", "sgb_boot.bin", "bios_MD.bin",
               "bios_E.sms", "bios_U.sms", "bios_J.sms", "bios.gg"}


# Console dumps the profiles do not list by name: every file of [size] and extension under a database folder, by its
# own name (ARMSX2 accepts any .bin), skipping the folder's variants and its DVD player firmware.
DB_FOLDERS = [("armsx2", "PS2", "bios/Sony/PlayStation 2/", 4194304, "bin", (".variants/", "DVD Firmware/"))]


ARCHIVES = ("zip", "7z")


class Row:
    def __init__(self, name, console):
        self.name, self.console = name, console
        self.aliases, self.sizes, self.md5s = [], set(), set()
        self.required = False
        self.boot_change = name in BOOT_CHANGE

    def __repr__(self):
        return "Row(%s, %s)" % (self.name, self.console)


def flat_name(entry):
    """The name the user keeps in Bios/<TAG>/, or None when the file sits at a nested path we do not map."""
    name, path = entry["name"], entry.get("path")
    if not path or path == name:
        return name
    for sub in FLAT_SUBDIRS:
        if path == sub + name:
            return name
    return None


def ext_of(name):
    return name.rsplit(".", 1)[-1].lower() if "." in name else ""


def looks_like_bios(name):
    """A real file name: no placeholder (<module>), no spaces-only label, an extension that is not config or text."""
    return "<" not in name and ext_of(name) != "" and ext_of(name) not in NON_BIOS_EXT


def console_of(core, entry):
    system = entry.get("system")
    if system is None:
        return CORE_DEFAULT[core]
    if system in IGNORED["*"] or system in IGNORED.get(core, ()):
        return None
    if system not in SYSTEM_TAGS:
        raise ValueError("unmapped system %r in %s (add it to SYSTEM_TAGS or IGNORED)" % (system, core))
    return SYSTEM_TAGS[system]


def build(root, cores=None):
    with open(os.path.join(root, "database.json")) as f:
        db = json.load(f)
    files, by_name = db["files"], db["indexes"]["by_name"]
    rows = {}
    for core in cores or sorted(set(CORE_PROFILES.values())):
        path = os.path.join(root, "emulators", core + ".yml")
        if not os.path.exists(path):
            raise ValueError("no retrobios profile " + path)
        with open(path) as f:
            profile = yaml.safe_load(f) or {}
        for entry in profile.get("files") or []:
            if entry.get("category") == "game_data":
                continue
            if entry.get("archive"):
                # A file listed inside a set archive: the archive is what the user places.
                entry = {"name": entry["archive"], "system": entry.get("system"), "required": entry.get("required"),
                         "path": entry.get("archive_path")}
            name = flat_name(entry)
            if name is None or not looks_like_bios(name):
                continue
            console = console_of(core, entry)
            if console is None:
                continue
            # Arcade BIOS are whole set archives; the chip files listed inside them are not placed on their own.
            if console == "FBN" and ext_of(name) not in ARCHIVES:
                continue
            row = rows.setdefault((name, console), Row(name, console))
            for a in entry.get("aliases") or []:
                if a != name and a not in row.aliases:
                    row.aliases.append(a)
            row.required = row.required or bool(entry.get("required"))
            raw = entry.get("size")
            sizes = {int(x) for x in (raw if isinstance(raw, list) else [raw] if raw else [])}
            md5 = entry.get("md5")
            for m in (md5 if isinstance(md5, list) else [md5] if md5 else []):
                row.md5s.add(m.lower())
            row.sizes |= sizes
            # Every database variant of the name (and aliases), kept to the profile's sizes when it gives them.
            for n in [name] + list(entry.get("aliases") or []):
                for sha in by_name.get(n, []):
                    v = files[sha]
                    if sizes and int(v["size"]) not in sizes:
                        continue
                    row.md5s.add(v["md5"].lower())
                    row.sizes.add(int(v["size"]))
    chosen = cores or sorted(set(CORE_PROFILES.values()))
    for core, console, folder, size, ext, skip in DB_FOLDERS:
        if core not in chosen:
            continue
        for v in files.values():
            p = v["path"]
            if not p.startswith(folder) or any(p[len(folder):].startswith(x) for x in skip):
                continue
            if int(v["size"]) != size or ext_of(v["name"]) != ext:
                continue
            row = rows.setdefault((v["name"], console), Row(v["name"], console))
            row.md5s.add(v["md5"].lower())
            row.sizes.add(size)
    return sorted(rows.values(), key=lambda r: (r.console, r.name.lower()))


def to_tsv(rows):
    out = ["# name\taliases\tconsole\tsizes\tmd5s\trequired\tboot_change (from retrobios metadata, MIT)"]
    for r in sorted(rows, key=lambda r: (r.console, r.name.lower())):
        # Set archives differ in size between romset versions: no size, so a name match is not refused for it.
        sizes = "" if ext_of(r.name) in ARCHIVES else ",".join(str(s) for s in sorted(r.sizes))
        out.append("\t".join([r.name, ",".join(r.aliases), r.console, sizes,
                              ",".join(sorted(r.md5s)), "1" if r.required else "0", "1" if r.boot_change else "0"]))
    return "\n".join(out) + "\n"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--retrobios", required=True)
    ap.add_argument("--out", default="androidApp/src/main/assets/bios/known.tsv")
    args = ap.parse_args()
    rows = build(args.retrobios)
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w") as f:
        f.write(to_tsv(rows))
    print("%d rows -> %s" % (len(rows), args.out), file=sys.stderr)


if __name__ == "__main__":
    main()
