#!/usr/bin/env bash
#
# A test asserts a behaviour, never an artefact of the implementation
# (AGENTS.md Testing, docs/architecture.md §8). This is the mechanical half of
# that rule: the constructs a test source may not contain because each one
# reaches past the behaviour into the implementation.
#
#   - Thread.sleep    a poll against the wall clock, which passes or fails with
#                     the machine's load. The two bounded polls that must sleep
#                     (a CoroutineWorker finishing on its own dispatcher, a
#                     launcher host rendering) mark the line `// bounded poll`
#                     and carry a loud timeout; nothing else may.
#   - getMethod, getDeclaredMethod, getDeclaredField, declaredConstructors,
#     .methods, declaredMethods
#                     reflection into a library or a constructor shape. What a
#                     library keeps internal is not a behaviour, and a
#                     constructor's parameter list is the Hilt graph's to check.
#                     The `.methods` pair is listed because it is the way round
#                     a banned `getMethod`: the matcher removed from this module
#                     read a mangled accessor as `javaClass.methods.single { … }`.
#                     Class.forName and getDeclaredConstructor() are not listed:
#                     probing whether a class is on the classpath, or
#                     instantiating a receiver the merged manifest declares, is
#                     how the manifest tests ask a real question about the app.
#
# A script and not a detekt rule, for two reasons. detekt's ForbiddenMethodCall
# needs type resolution, which this repo's single `detekt` task does not run, so
# the rule reported nothing on the two sleeps it was meant to catch — a gate
# that passes on nothing (measured 2026-09-04). And a script cannot go
# UP-TO-DATE, which is why check-citations.sh is a script too.
#
# Comment lines are skipped so a KDoc may name the thing it is not doing.

set -uo pipefail

cd "$(dirname "$0")/.." || exit 2

if [ ! -f docs/architecture.md ]; then
    echo "check-tests: run from the repo (docs/architecture.md not found)" >&2
    exit 2
fi

FORBIDDEN='Thread\.sleep\(|\.getMethod\(|getDeclaredMethod\(|getDeclaredField\(|declaredConstructors|\.methods\b|declaredMethods'
failures=0

while IFS= read -r file; do
    while IFS= read -r hit; do
        line="${hit#*:}"
        # Skip comment lines: the header above is exactly the kind of prose that
        # would otherwise trip its own check.
        if printf '%s' "$line" | grep -qE '^[[:space:]]*(//|\*|/\*)'; then
            continue
        fi
        if printf '%s' "$line" | grep -q 'Thread\.sleep(' && printf '%s' "$line" | grep -q '// bounded poll'; then
            continue
        fi
        echo "check-tests: $file:${hit%%:*}: $(printf '%s' "$line" | sed 's/^[[:space:]]*//')"
        failures=$((failures + 1))
    done < <(grep -nE "$FORBIDDEN" "$file" || true)
# :core:testing keeps its helpers in `main`, because only test source sets
# consume it — so the rule has to reach that directory by name or the module
# that exists to hold shared test code would be the one place exempt from it.
done < <(
    find app core feature widget -type f -name '*.kt' \
        \( -path '*/src/test/*' -o -path '*/src/androidTest/*' -o -path '*/src/testFixtures/*' -o -path 'core/testing/src/main/*' \) \
        -not -path '*/build/*' | sort
)

if [ "$failures" -gt 0 ]; then
    echo "check-tests: $failures line(s) reach past behaviour into the implementation" >&2
    exit 1
fi

echo "check-tests: no forbidden calls in test sources"
