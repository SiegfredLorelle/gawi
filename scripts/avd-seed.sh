#!/usr/bin/env bash
# Put a generated event log onto a device, for docs/running.md §4.
#
#     scripts/avd-seed.sh baseline
#     scripts/avd-seed.sh regenerating --today 2026-09-19
#
# **This clears the app's data, and it refuses anything but an emulator.**
# `adb` with no `-s` and no `ANDROID_SERIAL` takes whatever single device is
# attached, and §3 documents a physical-device path -- so without the guard a
# phone on wireless debugging loses its whole habit log to a command about
# seeding. Point `ANDROID_SERIAL` at an emulator, or set `GAWI_SEED_FORCE=1`
# if you mean it.
#
# Through the app's own importer, not through sqlite, and that is forced rather
# than chosen: §4 runs against the signed release APK, which is not debuggable,
# so `run-as` is refused; `adb root` is refused on the Play image too. The
# importer is the only writer into a release build's log.
#
# The clear is also what makes a scenario mean anything: import is a merge
# deduped by event id (ExportReader), so seeding twice without one unions the
# two logs and the screen then disagrees with the scenario for reasons nothing
# on it explains.
#
# **The file is chosen by its full name.** A partial match ranks the row's
# preview thumbnail alongside the filename -- the thumbnail carries the name in
# its own content description -- and opens a preview instead of importing.
# avd-ui.py refuses an ambiguous selector for this reason; do not loosen it.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
app=com.gawi.app

scenario="${1:-}"
shift || true
if [ -z "$scenario" ]; then
    echo "usage: $0 <scenario> [--today YYYY-MM-DD]" >&2
    echo "clears $app's data, so it refuses a non-emulator target." >&2
    "$here/gen-seed.py" --list >&2
    exit 2
fi

if [ "${GAWI_SEED_FORCE:-}" != "1" ] && [ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" != "1" ]; then
    echo "$0: the target is not an emulator, and this clears $app's data." >&2
    echo "Point ANDROID_SERIAL at an emulator, or set GAWI_SEED_FORCE=1." >&2
    exit 2
fi

work="$(mktemp -d)"
# The scratch copy goes whichever way this exits: every adb call below can end
# the script under `set -e`, and none of them reach a cleanup written inline.
trap 'rm -rf "$work"' EXIT

file="seed-$scenario.json"
"$here/gen-seed.py" "$scenario" --out "$work/$file" "$@"

adb shell pm clear "$app" >/dev/null
adb push "$work/$file" "/sdcard/Download/$file" >/dev/null
echo "pushed $file, app data cleared"

# Read the screen rather than assume it. The swipe is the one thing here that
# cannot be aimed by selector, and a guessed one lands somewhere harmless on
# another device and surfaces later as an unrelated timeout.
size="$(adb shell wm size | sed 's/.*: *//' | tr -d '\r')"
width="${size%%x*}"
height="${size##*x}"
mid_x=$((width / 2))
from_y=$((height * 4 / 5))
to_y=$((height * 3 / 10))

# Scroll until a row is on screen, rather than a fixed number of swipes.
scroll_to() {
    local label="$1"
    local attempts="${2:-8}"
    local n=0
    while [ "$n" -lt "$attempts" ]; do
        if "$here/avd-ui.py" find --text "$label" | grep -q "text='$label'"; then
            return 0
        fi
        adb shell input swipe "$mid_x" "$from_y" "$mid_x" "$to_y" 300
        sleep 1
        n=$((n + 1))
    done
    echo "$0: '$label' never came on screen after $attempts scrolls" >&2
    return 1
}

adb shell am start -n "$app/.MainActivity" >/dev/null
"$here/avd-ui.py" wait --desc Settings --timeout 20 >/dev/null
"$here/avd-ui.py" tap --desc Settings >/dev/null

scroll_to 'Import a file'
"$here/avd-ui.py" tap --text 'Import a file' >/dev/null

"$here/avd-ui.py" wait --text "$file" --id title --timeout 20 >/dev/null
"$here/avd-ui.py" tap --text "$file" --id title >/dev/null

"$here/avd-ui.py" wait --text 'Import a file' --timeout 20 >/dev/null
"$here/avd-ui.py" tap --desc Back >/dev/null
sleep 2
echo "imported $file; Today now reads:"
"$here/avd-ui.py" find --text-contains 'left today'
