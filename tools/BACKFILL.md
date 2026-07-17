# One-time ride backfill from a Karoo

The product model:

> **Connect once to backfill all your rides — then the extension pulls every
> future ride automatically.**

The Karoo extension in this repo handles the *future* half (it syncs each ride
as you finish it). This tool handles the *backfill* half, because the karoo-ext
SDK exposes **no ride-history API** — the only way to get old rides off the Karoo
is its on-device `.fit` files.

## How it works

Every Karoo activity is saved as a `.fit` file in internal storage under
`FitFiles/`. Quiver already has a FIT receptor (`POST /api/fit/ingest`) that
parses a ride, attributes it to the right bike by power-meter serial, and accrues
distance onto the odometer — **deduping on the FIT file's internal timestamp**, so
re-uploading the same ride is a safe no-op.

`backfill-fit.sh` bridges the two: `adb pull` the `FitFiles/` folder, then POST
each file to the receptor.

## Steps

1. **On the Karoo 3:** Settings → About → tap the build number until Developer
   Options unlocks → enable **USB Debugging**. Plug it into the Mac via USB and
   tap **Allow** on the authorization prompt.
2. **Get a Quiver device token** (Settings → *Connect your phone* on quiver.fyi)
   and save it to `~/.quiver-token` (`chmod 600`).
3. **Run:** `tools/backfill-fit.sh` — it pulls `FitFiles/` and uploads everything,
   printing `ok` / `dup` / `FAIL` and the running odometer per file.

## After a full backfill: reconcile the baseline

Quiver's odometer = `odometer_baseline_miles` + Σ(imported rides). The baseline
represents miles ridden *before* Quiver started tracking. After importing the
Karoo's whole history, confirm the bike's odometer matches the number on the Karoo
screen; if the baseline now double-counts pre-tracking rides, set it so
`odometer_miles` equals the real total.

## Productizing this (future)

For real users, "connect once" shouldn't require a terminal. Options, roughly in
order of effort: a multi-file drag-drop zone in the web FIT uploader; a tiny
desktop helper; or a guided MTP flow ("drag your FitFiles folder here"). The
receptor + dedup already support all of them.
