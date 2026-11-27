# DS Download Play client

Our own DS homebrew program for the "DS Download Play" row, so a guest without the game can join a DS Download Play
host in NX Redux's DS core (melonDS DS) with no BIOS or firmware dumps. The protocol runs in the core (our melonDS DS
patch, `net/wmb*`); this program shows the hosts it hears and says which to join, through a mailbox in the DS's main
RAM (`source/mailbox.h`, the same file as the patch's `net/wmb_mailbox.h`).

Build (Docker, nothing installed on the machine): `./build.sh`. It puts `download-play.nds` in
`androidApp/src/main/assets/nds-dlp/`.

Host tests for the core's protocol and the mailbox: `make -C test` (needs the patched core tree: `gmake -C cores
melondsds` once). `test-data/` holds a real exchange between the DS menu's own client and a Mario Party DS host
(`wmb-capture.txt`) and six of that host's beacons (`beacons.txt`).

## Row art

`art/make_art.py` builds the row's art (`androidApp/src/main/assets/nds-dlp/`: 3D box, 2D box, wheel and mix; no screenshot, so the row shows the generated picture a game without one gets)
the way `tools/n3ds-download-play/art/make_art.py` built the 3DS row's: the DS row's case and paper (from before this
client, `git show <commit>:androidApp/src/main/assets/nds-menu/<file>`), our two-handheld icon, and a phone capture of
this client (`art/cap_client.png`, an AYANEO Pocket Air Mini screenshot of the list in the default DS layout).

```
.venv/bin/python -I art/make_art.py <the old DS art> art/cap_client.png ../../androidApp/src/main/assets/nds-dlp
```
