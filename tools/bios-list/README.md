# Known BIOS list

`build_bios_list.py` turns [retrobios](https://github.com/Abdess/retrobios) metadata into
`androidApp/src/main/assets/bios/known.tsv`, the list Settings → Library → Import BIOS identifies files with. Only the
project's MIT-licensed metadata is read (emulator profiles and `database.json`: names, sizes, md5s). Its BIOS files are
never fetched or used.

Each row: `name`, `aliases` (comma), `console` (our console tag, the `Bios/<TAG>/` folder), `sizes` (comma; empty for a
set archive), `md5s` (comma), `required` (0/1), `boot_change` (0/1). One row per (file, console).

What goes in:

- the profiles of the cores CoreRegistry ships (`CORE_PROFILES`); a profile entry's `system` maps to a console tag
  (`SYSTEM_TAGS`), an entry without one takes the core's console (`CORE_DEFAULT`); an unmapped system stops the build;
- flat files only, plus the `dc/` and `pcsx2/bios/` subfolders the app maps (`FLAT_SUBDIRS`);
- arcade entries as their set archive (`neogeo.zip`), CPS3 game sets left out;
- every 4 MB `.bin` under the database's `PlayStation 2/` folder (the console dumps, by their own names).

Left out: `game_data` (Doom IWADs, Stella samples), configs/fonts/palettes, Dolphin (nested GC IPL, Wii NAND files).

## Refreshing

From the repo root, in a new empty directory for the checkout (the sparse set never pulls `bios/`):

```bash
SCR=$(mktemp -d)
git clone -q --filter=blob:none --no-checkout https://github.com/Abdess/retrobios "$SCR/retrobios"
git -C "$SCR/retrobios" sparse-checkout set --no-cone /emulators/ /database.json
git -C "$SCR/retrobios" checkout -q main
python3 tools/bios-list/build_bios_list.py --retrobios "$SCR/retrobios"
python3 tools/bios-list/test_build_bios_list.py
```

Needs PyYAML (`pip3 install --user pyyaml`); profiles are read with `yaml.safe_load`. Then run `KnownBiosTest`: it fails
when a BIOS name a core uses is missing from the list.
