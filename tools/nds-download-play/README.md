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
