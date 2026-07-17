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

## What's built in this repo (v1 — builds green; not yet run on hardware)

- Full Kotlin/Android extension scaffolded from `karoo-ext-template`, `karoo-ext`
  1.1.9. `./gradlew assembleDebug` produces `app-debug.apk` on JDK 17 / Android
  SDK 34.
- **Pairing** UI (Compose) → claim → token in prefs.
- **Auto ride sync**: `QuiverExtension` (a bound `KarooExtension` service) watches
  `RideState`; on Recording→Idle it POSTs ride distance via a durable outbox
  (`RideOutbox`) that survives an offline ride-end.
- **Garage seed** on pair + reconnect.
- `manifest.json` + `MANIFEST_URL` meta for Companion sideload & in-device Update.

## Finish-line checklist (to a live sideloadable APK)

1. ✅ **GitHub `read:packages`** — added to the `gh` login; creds in
   `~/.gradle/gradle.properties`.
2. ✅ **Android toolchain** — JDK 17 + Android SDK 34 + Gradle 8.6 installed.
3. ✅ **`./gradlew assembleDebug`** — builds green (fixed a `continue`-in-inline-
   lambda and a serialization opt-in).
4. **[PK] On-device test on the Karoo 3** (sideload via Companion):
   - Pair with a real code from quiver.fyi; confirm the board seeds.
   - Do a short real/simulated ride; confirm mileage lands on the **Melee** odometer
     (not a stray/default bike — see attribution note below).
   - **Verify service liveness across a full ride.** We promote `QuiverExtension` to
     a started **foreground `dataSync` service** (persistent "Syncing your rides"
     notification) so the Karoo unbinding us can't kill it mid-ride. Confirm the
     notification stays up for the whole ride and that `RideState`/distance events
     arrive. (Evidence: veloVigil — the one real background-sync extension — runs the
     same onCreate pattern; it keeps its service alive partly via declared data
     types. Our foreground promotion is the belt-and-suspenders alternative. If FGS
     misbehaves on the Karoo, the fallback is to declare a trivial data type like
     veloVigil.)
   - **Verify ride-end fires.** We end a ride on `RideState.Recording → Idle`.
     veloVigil found the Karoo doesn't always emit `Idle` and added an inactivity
     watchdog. We deliberately didn't (our single-POST model makes a false end
     lossy). If `Idle` proves unreliable on-device, add a non-lossy end-detector
     (e.g. checkpoint distance to the outbox, finalize on next Recording).
5. **[Eng] Create the GitHub repo** (`pdkane/quiver-karoo` assumed — confirm),
   push, cut a release with `quiver-karoo.apk` + `manifest.json` + icon.
6. **[PK] Announce** the sideload link to Quiver riders.

## Track B (official store) — when ready

- Draft + send outreach to Hammerhead's developer platform
  (hammerhead.io/pages/developer-platform) asking for Native Extension Library
  submission requirements + lead time. (Agent can draft; PK sends.)
- Submit the proven APK per whatever process they return.

## Known limitations / fast-follows

- **Ride distance** uses the `DISTANCE` stream captured at ride end (monotonic-max
  guarded).
- **Per-bike attribution** rides on the ride *fingerprint*: each finished ride POSTs
  its active sensor serials (`devices: [{serial,name,kind}]`, e.g. the Quarq PM),
  which `recordRide` matches to the bike. bikeId is null (the Karoo bike id isn't
  Quiver's), so **the fingerprint is what lands mileage on the right bike** — verify
  the Melee's power-meter serial is present in `SavedDevices` on-device.
- `movingTimeS` is sent as null in v1 (elapsed ≠ moving); add later if useful.
- Seed device `kind` is a best-effort token from supported data types (backend
  normalizes unknown → 'other').
- Fast-follows: in-ride data fields (chain-wax-due, battery %, next service),
  recall/maintenance in-ride alerts (`InRideAlert`/`SystemNotification` effects).
