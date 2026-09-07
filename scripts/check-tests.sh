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
#   - the java.lang.Class lookups, in both spellings
#                     reflection into a library or a constructor shape. What a
#                     library keeps internal is not a behaviour, and a
#                     constructor's parameter list is the Hilt graph's to check.
#                     Kotlin reads a Java getter as a property, so every one of
#                     these has two spellings and the list needs both: the
#                     matcher removed from this module read a mangled accessor
#                     as `javaClass.methods.single { … }`, and banning only that
#                     form would let it back in as `javaClass.getMethods()`.
#                     `kotlin.reflect` is the same hole through another package,
#                     so `declaredMemberFunctions` and `memberProperties` are
#                     listed too.
#
#                     `getDeclaredConstructor()` — singular, no arguments — is
#                     deliberately NOT here: it instantiates a receiver the
#                     merged manifest declares, which is a question about the
#                     app. `Class.forName` is out for the same reason.
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

sources=$(
    find app core feature widget -type f -name '*.kt' \
        \( -path '*/src/test/*' -o -path '*/src/androidTest/*' -o -path '*/src/testFixtures/*' -o -path 'core/testing/src/main/*' \) \
        -not -path '*/build/*' | sort
)

# A scan that found nothing to scan is not a pass. `find` writes to stderr and
# keeps going when a root is missing, its status is swallowed here, and there is
# no `set -e` — so a moved tree would otherwise print the success line below and
# exit 0. Two nets, because a count alone is the weaker one: every root has to
# exist, which is what catches a *renamed* module, and the total has to look
# like this repo, which catches a glob that stopped matching.
for root in app core feature widget; do
    if [ ! -d "$root" ]; then
        echo "check-tests: $root is not a directory — the module layout has" >&2
        echo "moved and the scan roots need updating." >&2
        exit 2
    fi
done

found=$(printf '%s' "$sources" | grep -c .)
if [ "$found" -lt 40 ]; then
    echo "check-tests: found $found test source file(s), too few to be this" >&2
    echo "repo — the module layout has moved and the scan roots need updating." >&2
    exit 2
fi

FORBIDDEN='Thread\.sleep\(|\.getMethod\(|[gG]etDeclaredMethods?\(|[gG]etMethods\(|[gG]etDeclaredFields?\(|[gG]etFields\(|[gG]etDeclaredConstructors\(|declaredConstructors|declaredFields|\.methods\b|\.fields\b|declaredMethods|declaredMemberFunctions|declaredMemberProperties|memberProperties'
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
done <<< "$sources"

if [ "$failures" -gt 0 ]; then
    echo "check-tests: $failures line(s) reach past behaviour into the implementation" >&2
    exit 1
fi

echo "check-tests: no forbidden calls in test sources"
