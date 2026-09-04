#!/usr/bin/env bash
#
# A comment keeps what the code cannot say — the mechanism, the invariant, the
# reason a shape was chosen, the `docs/ §N` it answers to. It does not keep the
# story of how it got there. Git already holds that, holds it better, and holds
# it without going stale when the code moves on. This is the mechanical half of
# the AGENTS.md Comments rule: the phrasings that are always narrative.
#
#   - a YYYY-MM-DD date  a comment that stamps itself is a changelog entry in
#                        the wrong file. See the exemption below.
#   - "used to"          what the code was is the diff's to tell.
#   - "a review"         who found something is the pull request's to record.
#                        Covers "a reviewer" as a substring.
#   - "/code-review"     likewise. The finding is worth keeping; the fact that
#                        a review produced it is not.
#   - "this KDoc"        a comment correcting an earlier version of itself.
#   - "an earlier version"
#   - "was wrong"        the same, in the voice of a confession.
#   - "coderabbit"       a bot's name in a comment dates it to the bot.
#
# **The one exemption: a measurement.** "Contrast measured 1.59:1 on a Nothing
# A059 on 2026-08-22" has to carry its date, because the reader's question is
# how stale the number is and no other line can answer it. So a date passes if
# the same line says `measured` or `seen on`. Same line, not the same block:
# the check reads one line at a time, which is the cost of it being this
# simple, and the wrap has to be arranged around it.
#
# That exemption is why this is a gate at all. The argument against automating
# it was that a regex cannot tell a measurement from a changelog entry, which
# is true and is why the exemption is a token the writer opts into rather than
# a pattern the script infers.
#
# **What it deliberately does not check.** "no longer", "previously" and "the
# fix" all have honest present-tense uses here — a mechanism that no longer
# needs a guard, a value previously computed at the call site — and gating
# them would cost more good sentences than it saved bad ones. The other half
# of the rule, that a comment must not retell the code beneath it, is not
# checkable at all and lives in AGENTS.md as a rule for the writer.
#
# **Why a script and not a Gradle task**: see check-citations.sh. A task
# caches, and a gate that passes by being UP-TO-DATE has verified nothing.

set -uo pipefail

cd "$(dirname "$0")/.." || exit 2

if [ ! -f docs/architecture.md ]; then
    echo "check-history: run from the repo (docs/architecture.md not found)" >&2
    exit 2
fi

# Production sources only. Test sources are check-tests.sh's, and a test that
# names the date it pinned a behaviour on is not the same defect.
sources=$(find app core feature widget -type f -name '*.kt' \
    -path '*/src/main/*' -not -path '*/build/*' | sort)

failures=$(
    echo "$sources" | while IFS= read -r file; do
        [ -n "$file" ] || continue
        awk -v file="$file" '
            # A comment line: the first non-space character opens or continues
            # one. Enough for this codebase, where every block comment is
            # left-aligned with a leading asterisk.
            $0 !~ /^[[:space:]]*(\/\/|\/\*|\*)/ { next }

            {
                line = tolower($0)
                why = ""

                if ($0 ~ /20[0-9][0-9]-[0-9][0-9]-[0-9][0-9]/ &&
                    line !~ /measured/ && line !~ /seen on/) {
                    why = "dated, and the line does not say measured or seen on"
                } else if (line ~ /used to/) {
                    why = "\"used to\""
                } else if (line ~ /a review/) {
                    why = "\"a review\""
                } else if (line ~ /\/code-review/) {
                    why = "\"/code-review\""
                } else if (line ~ /this kdoc/) {
                    why = "\"this KDoc\""
                } else if (line ~ /an earlier version/) {
                    why = "\"an earlier version\""
                } else if (line ~ /was wrong/) {
                    why = "\"was wrong\""
                } else if (line ~ /coderabbit/) {
                    why = "\"CodeRabbit\""
                }

                if (why == "") next

                text = $0
                sub(/^[[:space:]]*/, "", text)
                printf "%s:%d: %s\n", file, FNR, why
                printf "    %s\n", text
            }
        ' "$file"
    done
)

if [ -n "$failures" ]; then
    echo "$failures"
    count=$(echo "$failures" | grep -c '^[^ ]')
    echo
    echo "check-history: $count comment line(s) narrate history instead of" >&2
    echo "stating a mechanism. Keep the finding, drop the story of finding it." >&2
    echo "A date needs 'measured' or 'seen on' on the same line." >&2
    exit 1
fi

echo "check-history: no dated or narrated comments in production sources"
