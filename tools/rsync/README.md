# rsync for netplay

Netplay copies battery saves the way the NX Redux handheld does: the joiner pulls each `nxsave.srm`/`nxsave.sav` from
the host's rsync daemon on TCP 18731 (nx-redux `wizard_sync.c`). This folder cross-builds rsync 3.4.1 for arm64 Android.

    gmake -C tools/rsync install   # download (SHA-256 checked), configure, build, verify, copy into androidApp's jniLibs

Output: `output/librsync_exec.so`, a stripped PIE executable with 16 KB LOAD alignment, and `output/licenses/rsync.txt`
(rsync's GPL-3.0 `COPYING`, popt's and zlib's notices), which androidApp ships as `assets/licenses/rsync.txt`. `src/` and
`output/` are gitignored, like the cores.

The name ends in `.so` because Android extracts only `lib*.so` from an APK, and an app may execute only what was extracted
at install (since Android 10 it cannot execute a file it wrote). androidApp therefore sets
`packaging.jniLibs.useLegacyPackaging = true`, so every native library is compressed in the APK and extracted on install.

Configure flags: `--disable-openssl --disable-xxhash --disable-zstd --disable-lz4 --disable-md2man`, plus
`--with-included-popt --with-included-zlib --disable-iconv --disable-acl-support --disable-xattr-support` for the NDK
sysroot, and cache answers for four run-time checks a cross build cannot run. Build time on an M-series Mac: about 21 s.

When the binary is missing or will not run:
- a joiner never declines the copy: it answers `SYNC-FAIL` and shows "Could not copy the host's saves." (the host then shows
  that the save transfer failed and keeps waiting);
- a host skips `SYNC-READY`; both then play on empty saves and the host's game says "Saves not copied - playing on empty
  saves."
