#!/usr/bin/env bash
# Put a generated event log onto a device, for docs/running.md §4.
#
#     scripts/avd-seed.sh baseline
#     scripts/avd-seed.sh regenerating --today 2026-09-19
#
# Through the app's own importer, not through sqlite, and that is forced rather
# than chosen: §4 runs against the signed release APK, which is not debuggable,
# so `run-as` is refused; `adb root` is refused on the Play image too. The
# importer is the only writer into a release build's log.
#
# **The app's data is cleared first.** Import is a merge deduped by event id
# (ExportReader), so seeding twice without a clear unions the two logs and the
# screen then disagrees with the scenario for reasons nothing on it explains.
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
    "$here/gen-seed.py" --list >&2
    exit 2
fi

work="$(mktemp -d)"
# set -e does not abort a pipeline, and a cleanup that fires anyway once cost a
# run's evidence. A trap removes the scratch copy whatever happened.
trap 'rm -rf "$work"' EXIT

file="seed-$scenario.json"
"$here/gen-seed.py" "$scenario" --out "$work/$file" "$@"

adb shell pm clear "$app" >/dev/null
adb push "$work/$file" "/sdcard/Download/$file" >/dev/null
echo "pushed $file, app data cleared"

adb shell am start -n "$app/.MainActivity" >/dev/null
"$here/avd-ui.py" wait --desc Settings --timeout 20 >/dev/null
"$here/avd-ui.py" tap --desc Settings >/dev/null

# The import row is below the fold on a 720x1280 screen.
"$here/avd-ui.py" wait --text 'Settings' --timeout 10 >/dev/null
adb shell input swipe 360 1000 360 300 300
sleep 1
adb shell input swipe 360 1000 360 300 300
"$here/avd-ui.py" wait --text 'Import a file' --timeout 10 >/dev/null
"$here/avd-ui.py" tap --text 'Import a file' >/dev/null

"$here/avd-ui.py" wait --text "$file" --id title --timeout 20 >/dev/null
"$here/avd-ui.py" tap --text "$file" --id title >/dev/null

"$here/avd-ui.py" wait --text 'Import a file' --timeout 20 >/dev/null
"$here/avd-ui.py" tap --desc Back >/dev/null
sleep 2
echo "imported $file; Today now reads:"
"$here/avd-ui.py" find --text-contains 'left today'
