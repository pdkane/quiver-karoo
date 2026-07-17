# Quiver Odometer — Karoo extension (releases)

Distribution repo for the **Quiver Odometer** extension for the
[Hammerhead Karoo](https://www.hammerhead.io/). It connects your Karoo to your
[Quiver](https://quiver.fyi) garage:

- **Pair once**, then every finished ride's mileage syncs to your garage
  automatically — your maintenance, wear, and service clocks stay current with
  zero logging.
- **One-tap backfill** of your entire Karoo ride history.

> This repo hosts the built releases. The source lives in Quiver's main
> repository.

## Install (Karoo 3)

1. Open the [latest release](https://github.com/pdkane/quiver-karoo/releases/latest)
   on your **phone**.
2. **Long-press** the `quiver-odometer.apk` link → **Share → Hammerhead
   Companion**. The Companion app installs it on your Karoo.
3. On the Karoo, open **Quiver Odometer** and pair with the code from
   quiver.fyi → Settings → *Pair a head unit*.

Updates are delivered in-device (the app declares a `MANIFEST_URL`, so the Karoo
offers **Update** when a new release is published).

Karoo 2: sideload the APK with `adb install quiver-odometer.apk`.

## License

Apache-2.0.
