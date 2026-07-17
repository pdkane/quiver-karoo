# Quiver — Karoo extension

A [Karoo](https://www.hammerhead.io/) extension (built on
[`karoo-ext`](https://github.com/hammerheadnav/karoo-ext)) that connects a
Hammerhead head unit to the rider's [Quiver](https://quiver.fyi) garage.

**What it does (v1)**

- **Pair once.** The rider mints a short code on quiver.fyi
  (Settings → *Connect your head unit*) and types it into the extension. The code
  is exchanged for a device token (`POST /api/karoo/pair/claim`) stored on the
  device.
- **Automatic ride sync.** When a ride finishes, its distance is POSTed to
  `POST /api/ride/ingest` and accrued onto the bike's odometer — driving Quiver's
  wear / maintenance / service clocks. This replaces manual FIT-file backfill.
- **Garage seed.** On pairing (and each reconnect) the head unit's `Bikes`,
  `SavedDevices`, and `UserProfile` are sent to `POST /api/karoo/seed` to light up
  the Quiver board.

All network goes through the Karoo System's managed HTTP
(`OnHttpResponse.MakeHttpRequest`); finished rides are held in a durable on-device
outbox and flushed when connectivity returns, so an offline ride-end never drops
mileage.

## Project layout

```
app/src/main/kotlin/fyi/quiver/karoo/
  QuiverApi.kt            HTTP client over the Karoo managed-HTTP effect
  QuiverPrefs.kt          device-token + serial storage
  RideOutbox.kt           durable queue of unsynced finished rides
  SeedModels.kt           JSON payload contracts (mirror the backend types)
  SeedMapper.kt           SDK models -> seed payload (pure)
  extension/QuiverExtension.kt   background service: ride sync + seed
  ui/                     MainActivity + Compose pairing screen + ViewModel
```

## Build prerequisites

1. **JDK 17** and the **Android SDK** (command-line tools are enough; Android
   Studio optional). Set `ANDROID_HOME` / `sdk.dir` in `local.properties`.
2. **A GitHub Personal Access Token with the `read:packages` scope.** `karoo-ext`
   is published to GitHub Packages, which requires authentication even for public
   packages. Provide it one of two ways:
   - `~/.gradle/gradle.properties`:
     ```
     gpr.user=<your-github-username>
     gpr.key=ghp_<token-with-read:packages>
     ```
   - or environment variables `USERNAME` and `TOKEN`.

## Build

```sh
./gradlew assembleRelease      # -> app/build/outputs/apk/release/app-release.apk
# or a debug build for quick device testing:
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
```

## Install on a Karoo 3

1. Attach the built APK to a GitHub release (rename to `quiver-karoo.apk` to match
   `manifest.json`).
2. On the phone, open the release page in a browser, **long-press the APK link**,
   and **Share → Hammerhead Companion**. The Companion app sideloads it to the
   Karoo. The `MANIFEST_URL` meta-data enables in-device *Update* on future
   releases.

(Karoo 2: sideload the APK over USB with `adb install quiver-karoo.apk`.)

## Configuration

- Backend base URL: `QuiverApi.BASE_URL` (`https://quiver.fyi`).
- Extension id: `quiver` (in `extension_info.xml` and `QuiverExtension`).
- Application id / namespace: `fyi.quiver.karoo`.
- The GitHub org/repo in `manifest.json` and `AndroidManifest.xml` (`MANIFEST_URL`)
  is currently `pdkane/quiver-karoo` — update if the repo lives elsewhere.

## Status

v1 **builds green** (`./gradlew assembleDebug`) against `karoo-ext` 1.1.9 on JDK 17
/ Android SDK 34. **Not yet run on hardware** — see `GAMEPLAN.md` for the on-device
verification checklist and the official-store (partner) track.
