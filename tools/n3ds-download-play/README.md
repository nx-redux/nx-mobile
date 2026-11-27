# 3DS Download Play client

Our own 3DS homebrew program that stands in for the 3DS's Download Play system app, so a guest without the game can
join a Download Play host in NX Redux's 3DS core (Azahar). It uses only Azahar's emulated services: `dlp:CLNT` to find
hosts and download the game's child version, `ns:s` to hand the host's details to the child and restart into it.

The "3DS Download Play" row launches it; the built `download-play.3dsx` ships in
`androidApp/src/main/assets/n3ds-dlp/`.

Build (Docker, nothing installed on the machine): `./build.sh`.

The command layouts come from Azahar's `src/core/hle/service/dlp/dlp_clnt.cpp` and `dlp_clt_base.cpp`; the host's
details the child reads (`APT GetWirelessRebootInfo`) are 16 bytes: host MAC, the 9-byte passphrase, a zero byte.

## Row art

`art/make_art.py` builds the row's art (`androidApp/src/main/assets/n3ds-dlp/`: 3D box, 2D box, wheel, screenshot,
mix) from the DS Download Play row's (`androidApp/src/main/assets/nds-dlp/`) and a phone capture of this client
(`art/cap_client.png`, an AYANEO Pocket Air Mini screenshot of the list screen). The screenshot is the DS row's as is;
the icon and the handheld are drawn here.

```
.venv/bin/python -I art/make_art.py ../../androidApp/src/main/assets/nds-dlp art/cap_client.png ../../androidApp/src/main/assets/n3ds-dlp
```
