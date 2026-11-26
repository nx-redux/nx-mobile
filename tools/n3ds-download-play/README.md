# 3DS Download Play client

Our own 3DS homebrew program that stands in for the 3DS's Download Play system app, so a guest without the game can
join a Download Play host in NX Redux's 3DS core (Azahar). It uses only Azahar's emulated services: `dlp:CLNT` to find
hosts and download the game's child version, `ns:s` to hand the host's details to the child and restart into it.

The "3DS Download Play" row launches it; the built `download-play.3dsx` ships in
`androidApp/src/main/assets/n3ds-dlp/`.

Build (Docker, nothing installed on the machine): `./build.sh`.

The command layouts come from Azahar's `src/core/hle/service/dlp/dlp_clnt.cpp` and `dlp_clt_base.cpp`; the host's
details the child reads (`APT GetWirelessRebootInfo`) are 16 bytes: host MAC, the 9-byte passphrase, a zero byte.
