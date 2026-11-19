"""Run: python3 tools/bios-list/test_build_bios_list.py (from the repo root; needs PyYAML)."""
import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_bios_list  # noqa: E402

GBA_MD5 = "a860e8c0b6d573d191e4ec7db1b1e4f6"
CD_MD5 = "2efd74e3232ff260e371b99f84024f7f"


def write_fixture(root, profiles, files, extra_paths=()):
    os.makedirs(os.path.join(root, "emulators"))
    for core, text in profiles.items():
        with open(os.path.join(root, "emulators", core + ".yml"), "w") as f:
            f.write(text)
    by_name = {}
    db_files = {}
    for i, (name, size, md5) in enumerate(files):
        sha = "%040d" % i
        db_files[sha] = {"path": "bios/X/" + name, "name": name, "size": size, "md5": md5}
        by_name.setdefault(name, []).append(sha)
    for j, (path, size, md5) in enumerate(extra_paths):
        db_files["p%039d" % j] = {"path": path, "name": path.rsplit("/", 1)[-1], "size": size, "md5": md5}
    with open(os.path.join(root, "database.json"), "w") as f:
        json.dump({"files": db_files, "indexes": {"by_name": by_name}}, f)


PROFILES = {
    "mgba": """
systems: [nintendo-gba, nintendo-gb]
files:
  - name: gba_bios.bin
    system: nintendo-gba
    required: false
  - name: gb_bios.bin
    system: nintendo-gb
    size: 256
    md5: "32fbbd84168d3482956eb3c5051637f5"
""",
    "genesis_plus_gx": """
systems: [sega-megadrive]
files:
  - name: bios_CD_U.bin
    system: sega-megacd
    required: true
    size: 131072
  - name: bios_MD.bin
    system: sega-megadrive
  - name: odd.bin
    system: sega-megacd
    size: [100, 200]
""",
    "picodrive": """
systems: [sega-megadrive]
files:
  - name: bios_CD_U.bin
    aliases: [us_scd1_9210.bin]
    system: sega-megacd
  - name: carthw.cfg
""",
    "flycast": """
systems: [sega-dreamcast]
files:
  - name: dc_boot.bin
    path: dc/dc_boot.bin
    system: sega-dreamcast
""",
    "fbneo": """
systems: [arcade]
files:
  - name: sp-s3.sp1
    archive: neogeo.zip
    system: snk-neogeo-mvs
    size: 131072
    required: true
  - name: sp-s2.sp1
    archive: neogeo.zip
    system: snk-neogeo-mvs
  - name: 136032.107.b2
    system: atari-system1
  - name: coleco.rom
    system: coleco-colecovision
  - name: jojo_simm1.0
    archive: jojo.zip
    system: capcom-cps3
""",
    "armsx2": """
systems: [sony-playstation-2]
files:
  - name: "<module>.irx"
  - name: Wii System Menu
""",
    "prboom": """
systems: [doom]
files:
  - name: doom.wad
    system: doom
    category: game_data
  - name: prboom.wad
""",
}

DB = [
    ("neogeo.zip", 1859335, "4" * 32),
    ("neogeo.zip", 54356, "5" * 32),
    ("gba_bios.bin", 16384, GBA_MD5),
    ("gba_bios.bin", 999, "ffffffffffffffffffffffffffffffff"),
    ("bios_CD_U.bin", 131072, CD_MD5),
    ("dc_boot.bin", 2097152, "e10c53c2f8b90bab96ead2d368858623"),
]


class BuildBiosListTest(unittest.TestCase):
    def build(self, profiles=PROFILES, db=DB, cores=None, extra_paths=()):
        with tempfile.TemporaryDirectory() as root:
            write_fixture(root, profiles, db, extra_paths)
            return build_bios_list.build(root, cores or list(profiles))

    def row(self, rows, name, console):
        hits = [r for r in rows if r.name == name and r.console == console]
        self.assertEqual(1, len(hits), (name, console, rows))
        return hits[0]

    def test_profile_md5_used(self):
        self.assertEqual({"32fbbd84168d3482956eb3c5051637f5"}, self.row(self.build(), "gb_bios.bin", "GB").md5s)

    def test_database_fills_missing_md5(self):
        r = self.row(self.build(), "gba_bios.bin", "GBA")
        # No profile size: every database variant of that name counts.
        self.assertEqual({GBA_MD5, "ffffffffffffffffffffffffffffffff"}, r.md5s)
        self.assertEqual({16384, 999}, r.sizes)

    def test_profile_size_filters_database(self):
        r = self.row(self.build(), "bios_CD_U.bin", "SEGACD")
        self.assertEqual({131072}, r.sizes)
        self.assertEqual({CD_MD5}, r.md5s)

    def test_size_list_kept(self):
        self.assertEqual({100, 200}, self.row(self.build(), "odd.bin", "SEGACD").sizes)

    def test_system_maps_to_console(self):
        rows = self.build()
        self.row(rows, "bios_CD_U.bin", "SEGACD")
        self.row(rows, "gba_bios.bin", "GBA")

    def test_missing_system_takes_core_default(self):
        self.row(self.build(), "prboom.wad", "PRBOOM")

    def test_unmapped_system_fails(self):
        bad = {"mgba": "systems: [nintendo-gba]\nfiles:\n  - name: x.bin\n    system: martian-console\n"}
        with self.assertRaises(ValueError):
            self.build(profiles=bad)

    def test_nested_path_skipped_dc_kept(self):
        nested = dict(PROFILES, mgba=PROFILES["mgba"] + "  - name: IPL.bin\n    path: Sys/GC/USA/IPL.bin\n")
        rows = self.build(profiles=nested)
        self.assertFalse([r for r in rows if r.name == "IPL.bin"])
        self.row(rows, "dc_boot.bin", "DC")

    def test_game_data_skipped(self):
        self.assertFalse([r for r in self.build() if r.name == "doom.wad"])

    def test_non_bios_extensions_skipped(self):
        self.assertFalse([r for r in self.build() if r.name == "carthw.cfg"])

    def test_arcade_keeps_set_archives_only(self):
        rows = self.build()
        neo = self.row(rows, "neogeo.zip", "FBN")
        self.assertEqual({"4" * 32, "5" * 32}, neo.md5s)
        self.assertTrue(neo.required)
        self.assertFalse([r for r in rows if r.name in ("136032.107.b2", "coleco.rom", "jojo.zip")])

    def test_placeholder_and_extensionless_names_skipped(self):
        self.assertFalse([r for r in self.build() if r.name in ("<module>.irx", "Wii System Menu")])

    def test_ps2_dumps_from_database_folder(self):
        extra = [
            ("bios/Sony/PlayStation 2/SCPH-39001.bin", 4194304, "d5ce2c7d119f563ce04bc04dbc3a323e"),
            ("bios/Sony/PlayStation 2/DVD Firmware/ps2-dvd-300e.bin", 4194304, "1" * 32),
            ("bios/Sony/PlayStation 2/.variants/x.bin.123", 4194304, "2" * 32),
            ("bios/Sony/PlayStation 2/SCPH30004R.nvm", 1024, "3" * 32),
        ]
        rows = [r for r in self.build(extra_paths=extra) if r.console == "PS2"]
        self.assertEqual(["SCPH-39001.bin"], [r.name for r in rows])
        self.assertEqual({"d5ce2c7d119f563ce04bc04dbc3a323e"}, rows[0].md5s)
        self.assertEqual({4194304}, rows[0].sizes)

    def test_dolphin_not_read(self):
        self.assertNotIn("dolphin", build_bios_list.CORE_PROFILES.values())

    def test_archives_have_no_size(self):
        lines = build_bios_list.to_tsv(self.build()).splitlines()
        neo = [l for l in lines if l.startswith("neogeo.zip\t")][0].split("\t")
        self.assertEqual("", neo[3])

    def test_boot_change_flag(self):
        rows = self.build()
        self.assertTrue(self.row(rows, "gb_bios.bin", "GB").boot_change)
        self.assertTrue(self.row(rows, "bios_MD.bin", "MD").boot_change)
        self.assertFalse(self.row(rows, "gba_bios.bin", "GBA").boot_change)

    def test_rows_merged_across_profiles(self):
        r = self.row(self.build(), "bios_CD_U.bin", "SEGACD")
        self.assertEqual(["us_scd1_9210.bin"], r.aliases)
        self.assertTrue(r.required)

    def test_tsv_round_trip_shape(self):
        lines = build_bios_list.to_tsv(self.build()).splitlines()
        self.assertTrue(lines[0].startswith("#"))
        self.assertEqual(lines[1:], sorted(lines[1:], key=lambda l: (l.split("\t")[2], l.split("\t")[0].lower())))
        cd = [l for l in lines if l.startswith("bios_CD_U.bin\t")][0].split("\t")
        self.assertEqual(["bios_CD_U.bin", "us_scd1_9210.bin", "SEGACD", "131072", CD_MD5, "1", "0"], cd)


if __name__ == "__main__":
    unittest.main()
