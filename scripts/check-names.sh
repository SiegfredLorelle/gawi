#!/usr/bin/env bash
#
# Resource file names and android:id values, which detekt cannot see.
#
# The last piece of prd.md §5's step-4 naming sweep. detekt reads Kotlin, and
# after that sweep it reads it thoroughly — the test-source exclusion is lifted
# and FunctionNaming gates every function in the repo. What it never reads is
# `res/`: a drawable called `IcLauncher.xml` or an id spelled `@+id/myButton`
# is invisible to it, and to ktlint, and to Android Lint's default checks.
#
# What it catches:
#
#   - a file under any `src/main/res/` whose name is not lower_snake_case,
#     which for a resource is not a style preference: aapt derives an R field
#     from the basename, so an uppercase letter or a hyphen is a build error
#     rather than untidiness, and a *trailing* difference like `Ic_reminder`
#     compiles on a case-insensitive checkout and fails on CI.
#   - an `android:id` that is not `@+id/lower_snake_case` or `@id/…`.
#
# What it deliberately does not check:
#
#   - resource *contents*. A string's wording is docs/ux's business and no
#     script's.
#   - qualifier directories. `values-night`, `xml-v31` and `mipmap-anydpi` are
#     the platform's spelling and the hyphen in them is required, so only the
#     file basename is measured, never the directory.
#   - Kotlin identifiers of any kind. detekt owns those, and a second opinion
#     here would be a rule in two places disagreeing by drift.
#
# Why a script and not a detekt rule or an Android Lint check: detekt's
# analysis is Kotlin-only and does not visit `res/` at all, and the Lint checks
# that come near this need type resolution the build does not run
# (architecture §8). A Gradle task would also go UP-TO-DATE and pass on
# nothing, which is the failure mode the other four gates were written against.
#
# Both halves report zero today, and that is the point of writing it once: the
# repo is clean, so this is the fence rather than the clean-up. The id half in
# particular guards an empty set — this is a Compose app whose only layouts are
# two widget previews — and it is kept because the day someone adds a layout is
# exactly the day nobody is thinking about this.

set -uo pipefail

cd "$(dirname "$0")/.." || exit 2

if [ ! -f docs/architecture.md ]; then
    echo "check-names: run from the repo (docs/architecture.md not found)" >&2
    exit 2
fi

resources=$(
    find app core feature widget -path '*/build' -prune \
        -o -path '*/src/main/res/*' -type f -print | sort
)

# A scan that found nothing to scan is not a pass — the reasoning the other
# gates give, and the same two nets. Every root has to exist, which catches a
# renamed module; and the total has to look like this repo, which catches a
# glob that stopped matching.
for root in app core feature widget; do
    if [ ! -d "$root" ]; then
        echo "check-names: $root is not a directory — the module layout has" >&2
        echo "moved and the scan roots need updating." >&2
        exit 2
    fi
done

found=$(printf '%s' "$resources" | grep -c .)
if [ "$found" -lt 25 ]; then
    echo "check-names: found $found resource file(s), too few to be this repo" >&2
    echo "— the module layout has moved and the scan roots need updating." >&2
    exit 2
fi

failures=0

while IFS= read -r file; do
    [ -n "$file" ] || continue
    base=${file##*/}
    stem=${base%%.*}
    if ! printf '%s' "$stem" | grep -qE '^[a-z][a-z0-9_]*$'; then
        echo "check-names: $file: '$stem' is not lower_snake_case, so aapt cannot name its R field"
        failures=$((failures + 1))
    fi
done <<< "$resources"

while IFS= read -r hit; do
    [ -n "$hit" ] || continue
    file=${hit%%:*}
    rest=${hit#*:}
    line=${rest%%:*}
    value=$(printf '%s' "$rest" | sed -E 's/^[0-9]+://; s/.*android:id="([^"]*)".*/\1/')
    if ! printf '%s' "$value" | grep -qE '^@\+?id/[a-z][a-z0-9_]*$'; then
        echo "check-names: $file:$line: android:id \"$value\" is not @+id/lower_snake_case"
        failures=$((failures + 1))
    fi
done < <(grep -rnoE 'android:id="[^"]*"' --include='*.xml' app core feature widget 2>/dev/null | grep -v '/build/' || true)

if [ "$failures" -gt 0 ]; then
    echo "check-names: $failures resource name(s) a build or a review would trip over" >&2
    exit 1
fi

echo "check-names: resource file names and ids are lower_snake_case"
