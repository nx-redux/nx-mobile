# On-demand cores: investigated, parked

**Decision (2026-09-26): keep bundling every core in the APK.** On-demand delivery was designed in
full and then parked on size grounds. This file keeps the numbers and the design so the work can be
picked up without redoing it. Revisit when a Play listing is real, or when the installed size starts
to hurt (FBNeo is the likely trigger).

## Why it was considered

Four goals, from the 2026-09-25 cores expansion decision ("hybrid delivery"):

1. Install size: move heavy bundled cores (PPSSPP, melonDS, mupen64) out of the base.
2. More systems: add Arcade (FBNeo), 32X (picodrive), ColecoVision (gearcoleco) without making
   every install bigger.
3. Distribution: a proper Play build and a side-loaded (GitHub) build.
4. An onboarding picker ("which systems do you play?").

## Size numbers

Measured on 2026-09-26 at e304bcc:

- Debug APK: 115 MB. About 28 MB of that is unshrunk debug dex, so a release APK is about 100 MB.
- Core `.so` files (arm64-v8a): 64 MB. Largest: ppsspp 18.5, genesis_plus_gx 10.6, mupen64plus_next 6.0.
  `useLegacyPackaging = false` stores them uncompressed, so installed size equals raw size.
- Assets: 33 MB (PPSSPP system files 22 MB, overlays 10 MB, shaders 0.7 MB).
- Compressed (proxy for the Play download): the `.so` files about 17 MB, the assets about 21 MB.
- Actual device footprint today is about 122 MB. `SystemFiles.ensure` copies `assets/system`
  into `filesDir/system`, so PPSSPP's 22 MB is stored twice.

Projection. Cores not yet built use tg5040 sizes from the cores research, which were within about
15 % of our NDK builds for most cores. FBNeo was the outlier: 77.9 MB on tg5040 against 63.2 MB for
our stripped NDK build, about 19 % smaller.

| Scenario | APK installed | Play download (est.) |
|---|---|---|
| Today (22 cores, release) | ~100 MB | ~40 MB |
| + picodrive (1.6), gearcoleco (0.7), FBNeo (63.2, + 0.8 system files) | ~166 MB | ~56 MB |
| + VICE (16.3), PUAE (9.8), bluemsx (1.8), cap32 (1.5), DOS (~7), flycast (~18) | ~221 MB | ~74 MB |
| On-demand, PPSSPP moved out | ~60 MB base | ~25 MB base |
| On-demand, also melonDS, mupen64 and FBNeo | ~52 MB base | ~22 MB base |

The picodrive, gearcoleco and FBNeo sizes were measured on 2026-09-26 from our stripped NDK builds. The earlier
77.9 MB for FBNeo was the tg5040 build's size from the cores research; our stripped NDK arm64 build is 63.2 MB.

The Play base-module limit (200 MB compressed) is not at risk in any row. Bundling is a size
choice, not a policy blocker. The computer cores and flycast are blocked by the runner (no
keyboard/mouse device; no Vulkan), not by delivery.

## The parked design

If this comes back, it was decided as five sub-projects, in this order:

| # | Sub-project | Depends on |
|---|---|---|
| 1 | Delivery foundation: flavours, installer, launch gate, Settings → Cores. Pilot core: PPSSPP (largest, carries assets) | none |
| 2 | Slim the base: move melonDS and mupen64, final bundled list, doc wording | 1 |
| 3 | Release pipeline: build packs (`.so` + assets + manifest + sha256), publish to GitHub releases, pin the catalogue per app version | 1 |
| 4 | Onboarding picker | 1 (nicer after 2) |
| 5 | New systems as on-demand only: picodrive, gearcoleco, FBNeo | 1, 3 |

Decisions for sub-project 1:

- **Channels:** both. The `play` build uses Play Feature Delivery, one dynamic feature module per
  core (`:cores:ppsspp`). The `github` build downloads packs over HTTPS. The GitHub path is proven
  first on a real device. The Play path is checked with `bundletool --local-testing` (no store
  account needed).
- **Flavours:** a `distribution` dimension with `play` and `github`. `play` has its own
  `applicationId` (`com.nxredux.mobile.play`): its saves live in `Android/media/<package>/`, not the
  All files home, so the two are separate apps that install side by side.
  `checkCores` checks each flavour's bundled list.
- **Missing core UX:** games stay visible. Launching one opens a modal: "<System> needs the <core>
  core (N MB). Install?". It shows download, verify and install progress, then continues the
  launch. B cancels and removes the partial download. On failure it shows the reason. All launch
  paths (list, Game Switcher, Recently Played) go through the same gate. Only one install runs at a
  time, and a second request for the same core joins it.
- **Catalogue and trust:** the catalogue (id, pack version, url, size, sha256) is baked into each
  APK and pinned to a release tag (`cores-<version>`). No remote catalogue. A hash mismatch
  refuses the pack. Core updates arrive with app updates. On `github`, a pinned version that
  differs from the installed pack's `manifest.json` shows "Update available", and the old core
  keeps working until updated. On Play, installed splits update with the app.
- **Pilot pack:** a `gmake -C cores pack-<core>` target writes `<core>.zip` (the `.so`,
  `system/<Core>/` and `manifest.json`) and prints its sha256. It is uploaded once to a pre-release
  (`cores-0.1.0-pilot`, only with the owner's approval). Debug builds also accept a local override
  (a pack pushed by adb) for offline tests.

Architecture (approach A):

- `shared/commonMain`:
  - `CoreDescriptor.delivery` is `Bundled` or `OnDemand(sizeBytes)`.
  - `CoreInstaller` provides `status(coreId)`, `install(coreId): Flow<InstallProgress>`,
    `remove(coreId)` and `locate(coreId): CoreLocation?`.
  - `status` is one of Bundled, Installed, UpdateAvailable, NotInstalled or Unavailable
    (Unavailable is a Play build installed outside the Play Store).
  - A pure, unit-tested catalogue parser and version comparison, a launch gate in `AppState`, and
    a `CoresScreen` (A install/update, MENU remove; saves and states are never touched).
- The launch intent carries the full library path from `locate` instead of a bare file name. The
  fork already `dlopen`s whatever path `coreFilePath` holds (`core.cpp:39`), so **no fork change**
  is needed.
- `GithubCoreInstaller`:
  - Checks free space first (pack size × 2.5).
  - Downloads to `cacheDir`, checks the sha256, extracts to `filesDir/cores/<id>/<ver>.tmp`, then
    renames it into place in one step. Stale `.tmp` folders are swept at start.
  - Copies the pack's `system/` tree into `filesDir/system` and records the file list so removal
    cleans up.
  - A missing `.so` counts as NotInstalled.
- `PlayCoreInstaller`:
  - `SplitInstallManager`, handling `REQUIRES_USER_CONFIRMATION` through an
    ActivityResultLauncher and calling `SplitCompat.install` in the Activity.
  - `locate` returns `<splitSourceDir>!/lib/arm64-v8a/<lib>.so` (libraries stored uncompressed,
    16 KB aligned).
  - System files are copied from `createContextForSplit("core_<id>").assets`.
- **Approach B (fallback):** if the linker refuses `apk!/` paths inside a split, copy the split's
  `.so` into `filesDir` like the GitHub path. The cost is double storage and executable code in a
  writable directory on the Play build.
- **Rejected:** `SplitInstallHelper.loadLibrary`. It loads the library process-wide, so the
  fork's `releaseCore()`/`dlclose` would never reset core globals between games.

The first task of any plan: on the device, `dlopen` PPSSPP from its split via `apk!/` and run a
PSP homebrew before building anything else.

Doc wording to change if this ships:

- README Decisions: "Common cores bundled; large cores on demand (Play Feature Delivery on Play,
  pinned hash-checked GitHub packs on the `github` build)".
- README Constraints: "Only Play Feature Delivery on the Play build; no other code download".
- CLAUDE.md core rule: "Cores are bundled or delivered as on-demand packs, 16 KB page aligned. The
  Play build never downloads code from anywhere but Play."

Out of scope even then: a remote catalogue, and cores that are blocked in the runner (flycast,
computer cores).
