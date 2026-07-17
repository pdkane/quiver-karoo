#!/usr/bin/env bash
#
# One-time full ride backfill from a Karoo head unit.
#
# The Karoo stores every activity as a .fit file in its internal "FitFiles"
# folder. This pulls them over USB (adb) and bulk-uploads each to Quiver's
# FIT receptor (POST /api/fit/ingest). The endpoint dedups on the FIT file's
# internal timestamp, so re-running is always safe (already-imported rides
# come back as "duplicate"), and it attributes each ride to the right bike by
# the power-meter serial in the file.
#
# Prereqs on the Karoo (3): Settings → About → tap build number to unlock
# Developer Options → enable USB Debugging, connect USB, tap "Allow".
#
# Auth: a Quiver device token. Put it in ~/.quiver-token (chmod 600) so it
# never lands in shell history, or pass it as $QUIVER_TOKEN.
#
# Usage:
#   tools/backfill-fit.sh            # pull from Karoo, then upload
#   tools/backfill-fit.sh ./FitFiles # skip pull, upload an existing folder
#
set -euo pipefail

BASE_URL="${QUIVER_BASE_URL:-https://quiver.fyi}"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
DEST="${1:-$HOME/quiver-karoo-fit}"
TOKEN="${QUIVER_TOKEN:-$(cat "$HOME/.quiver-token" 2>/dev/null || true)}"

if [[ -z "$TOKEN" ]]; then
  echo "No token. Put a Quiver device token in ~/.quiver-token or set \$QUIVER_TOKEN." >&2
  exit 1
fi

# 1) Pull FitFiles from the Karoo unless we were handed an existing folder.
if [[ "${1:-}" == "" ]]; then
  mkdir -p "$DEST"
  echo "== devices =="; "$ADB" devices
  # The Karoo exposes activities under internal storage /sdcard/FitFiles.
  echo "== pulling FitFiles from Karoo =="
  "$ADB" pull /sdcard/FitFiles "$DEST" || {
    echo "adb pull failed — check USB debugging is on and the device is authorized." >&2
    exit 1
  }
fi

SRC="$DEST"
[[ -d "$DEST/FitFiles" ]] && SRC="$DEST/FitFiles"

# 2) Upload every .fit, printing per-file status + the running odometer.
shopt -s nullglob nocaseglob
files=("$SRC"/*.fit)
echo "== uploading ${#files[@]} files from $SRC =="
imported=0; dup=0; failed=0
for f in "${files[@]}"; do
  resp="$(curl -s -o /tmp/quiver_fit_resp.json -w '%{http_code}' -X POST \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/octet-stream" \
    --data-binary "@$f" \
    "$BASE_URL/api/fit/ingest" || echo 000)"
  body="$(cat /tmp/quiver_fit_resp.json 2>/dev/null || true)"
  case "$resp" in
    200) if grep -q '"duplicate":true' <<<"$body"; then dup=$((dup+1)); tag="dup";
         else imported=$((imported+1)); tag="ok"; fi
         odo="$(sed -n 's/.*"odometerMiles":\([0-9.]*\).*/\1/p' <<<"$body")"
         printf '  %-28s %s  odo=%s\n' "$(basename "$f")" "$tag" "${odo:-?}" ;;
    *)   failed=$((failed+1)); printf '  %-28s FAIL(%s) %s\n' "$(basename "$f")" "$resp" "${body:0:80}" ;;
  esac
done
echo "== done: imported=$imported duplicate=$dup failed=$failed =="
