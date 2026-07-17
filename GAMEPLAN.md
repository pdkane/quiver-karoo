# Quiver on Karoo — gameplan

**Goal:** get Quiver into the Karoo extension ecosystem so riders can pair their
head unit with their Quiver garage. Scope for this workstream: **pair + automatic
ride-mileage sync** (PK-approved v1). In-ride data fields and recall/maintenance
alerts on the ride screen are a fast-follow, not v1.

## The key reframe: "in the store" is two tracks

| Track | What | Owner | Blocker |
|---|---|---|---|
| **A — Sideloadable extension** | Build the APK; ship via GitHub release + Companion-app sideload. This is how every community Karoo extension ships today, and it's the exact artifact Track B submits. | Eng | none (fully in our control) |
| **B — Official Native Extension Library** | The in-device store on the Karoo main menu. Getting listed is a Hammerhead **partner** process their public docs deliberately don't document — appears curated/invite-based. | PK (founder→partner) | unknown whether self-serve submission exists |

**Decision: build Track A now, run Track B outreach in parallel.** Track A puts a
working, pairable extension in riders' hands immediately and de-risks Track B (we
submit a proven APK). PK chose "build first, outreach later."

## How it fits Quiver (already built on the backend)

The web + backend pairing/sync flow already exists — the only missing piece was the
on-device app, which is what this repo is:

- `mintPairingCode` + Settings → `PairHeadUnit.tsx` (rider gets a code on quiver.fyi)
- `POST /api/karoo/pair/claim` → device token (extension claims the code)
- `POST /api/karoo/seed` (garage snapshot: Bikes / SavedDevices / UserProfile)
- `POST /api/ride/ingest` (per-ride distance → odometer → wear clocks; dedup on
  `externalId`)

The `karoo-ext` data model (`Bikes`, `SavedDevices`, `UserProfile`, `RideState`,
`DataType.Type.DISTANCE`, `OnHttpResponse.MakeHttpRequest`) maps 1:1 onto these
endpoints — the seed route was clearly designed against it.

## What's built in this repo (v1, written, not yet compiled)

- Full Kotlin/Android extension scaffolded from `karoo-ext-template`, `karoo-ext`
  1.1.9.
- **Pairing** UI (Compose) → claim → token in prefs.
- **Auto ride sync**: `QuiverExtension` (a bound `KarooExtension` service) watches
  `RideState`; on Recording→Idle it POSTs ride distance via a durable outbox
  (`RideOutbox`) that survives an offline ride-end.
- **Garage seed** on pair + reconnect.
- `manifest.json` + `MANIFEST_URL` meta for Companion sideload & in-device Update.

## Finish-line checklist (to a live sideloadable APK)

1. **[PK] GitHub PAT with `read:packages`** — required to resolve the `karoo-ext`
   dependency from GitHub Packages. Put in `~/.gradle/gradle.properties`
   (`gpr.user` / `gpr.key`). *Hard blocker for any build.*
2. **[Eng/PK] Android toolchain** — JDK 17 + Android SDK (cmdline-tools). Not
   present in the authoring env. Either install headlessly or open in Android
   Studio.
3. **[Eng] `./gradlew assembleDebug`** — fix any compile errors (code is written
   against the real SDK API but unverified by a compiler).
4. **[PK] On-device test on the Karoo 3** (sideload via Companion):
   - Pair with a real code from quiver.fyi; confirm the board seeds.
   - Do a short real/simulated ride; confirm mileage lands on the Melee odometer.
   - **Top runtime risk to verify:** that the Karoo System keeps `QuiverExtension`
     bound/alive during a ride so it receives `RideState`/distance events. If not,
     fall back to a foreground service or drive sync from an activity. (Established
     community extensions use the onCreate pattern we use, so this is expected to
     work — but it's the #1 thing to confirm.)
5. **[Eng] Create the GitHub repo** (`quiver-fyi/quiver-karoo` assumed — confirm),
   push, cut a release with `quiver-karoo.apk` + `manifest.json` + icon.
6. **[PK] Announce** the sideload link to Quiver riders.

## Track B (official store) — when ready

- Draft + send outreach to Hammerhead's developer platform
  (hammerhead.io/pages/developer-platform) asking for Native Extension Library
  submission requirements + lead time. (Agent can draft; PK sends.)
- Submit the proven APK per whatever process they return.

## Known limitations / fast-follows

- **Ride distance** uses the `DISTANCE` stream captured at ride end (monotonic-max
  guarded). Per-bike attribution relies on the rider's Karoo bike selection / the
  seed; unassigned rides fall to the backend's default handling.
- `movingTimeS` is sent as null in v1 (elapsed ≠ moving); add later if useful.
- Seed device `kind` is a best-effort token from supported data types (backend
  normalizes unknown → 'other').
- Fast-follows: in-ride data fields (chain-wax-due, battery %, next service),
  recall/maintenance in-ride alerts (`InRideAlert`/`SystemNotification` effects).
