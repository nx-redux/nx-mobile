# MSU test sets

`make_tests.py` builds two small sets for checking MSU support on a device (Python 3 and ffmpeg):

- `SFC/`: `MSU1 Test.sfc`, a 32 KB SNES ROM that turns the screen green when MSU-1 is present and its `.msu` data
  reads back, yellow when the data is wrong, red when there is no MSU-1. It then plays track 1 on repeat.
  `MSU1 Test.msu` and `MSU1 Test-1.pcm`/`-2.pcm` go beside it.
- `MD/`: krikzz's MSU-MD sample as `MSU-MD Sample.md`, with `MSU-MD Sample.cue` and three audio tracks. The sample
  shows "load driver...ok / init driver...ok" when it finds the Sega CD; A plays, left/right change the track.

Track N beeps N times, then holds a chord, so each track can be told apart by ear.

    curl -LO https://github.com/krikzz/msu-md/raw/master/msu-md-sample/msu-md-sample.bin
    python3 -I make_tests.py out msu-md-sample.bin

Copy `out/SFC` and `out/MD` into a game folder each (`Roms/Super Nintendo ES (SFC)/MSU1 Test/`,
`Roms/Sega Genesis (MD)/MSU-MD Sample/`), the way MSU packs ship.
