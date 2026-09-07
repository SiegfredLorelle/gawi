#!/usr/bin/env bash
#
# A document records decisions and current state; git records history
# (AGENTS.md Documents, docs/prd.md §5's cleanup pass). This is the mechanical
# half of that rule over `docs/`, and it checks three things a reader cannot be
# relied on to notice while writing:
#
#   - a checkbox body longer than the cap
#                     A manual check keeps its instruction and one status line.
#                     What grows instead is a stack of dated run records — a
#                     first hearing, a fix note, a re-hearing — each of which is
#                     the same box's history rather than its state. The cap is
#                     set from the finished document plus a small margin, so it
#                     refuses regrowth without refusing ordinary editing.
#   - three or more dates in one checkbox body
#                     One status line may name when a check ran and when it was
#                     re-run; that is two. A third date is a third record, which
#                     is the shape the cap above is usually catching from the
#                     other side, caught here even when the prose is terse.
#   - a struck passage, `~~…~~`
#                     A struck claim with its correction after it is history
#                     written in place. The surviving half belongs in the
#                     present tense and the struck half belongs in git.
#
# What it deliberately does not check. Whether a status line is *true*, which
# only a device can say. Whether a body's prose is instruction or narrative,
# which is the judgement the rule is actually asking for and which no regex
# reaches — the cap is a volume proxy for it and nothing more. And prose outside
# a checkbox, because a section's argument is not a checklist entry and has no
# comparable shape.
#
# Fenced blocks and tables inside a body do not count toward the cap. A recipe
# is instruction however long it runs, and the two boxes that carry one would
# otherwise be the longest here while holding no narrative at all.
#
# A script rather than a Gradle task, for the reason the other three give in
# their own headers and docs/architecture.md §9 records: it reads `docs/`, which
# no Gradle source set contains, and it has to run in about a second.

set -uo pipefail

cd "$(dirname "$0")/.." || exit 2

if [ ! -f docs/architecture.md ]; then
    echo "check-docs: run from the repo (docs/architecture.md not found)" >&2
    exit 2
fi

# 22 against a longest finished body of 20 prose lines. Raising this is a
# decision about the rule, not a fix for a box that outgrew it.
MAX_BODY_LINES=22
MAX_DATES=2

sources=$(find docs -type f -name '*.md' | sort)

# A scan that found nothing to scan is not a pass. `find` writes to stderr and
# keeps going when a root is missing, its status is swallowed here, and there is
# no `set -e` — so a moved tree would otherwise print the success line below and
# exit 0. Two nets, because a count alone is the weaker one: every root has to
# exist, which is what catches a *renamed* module, and the total has to look
# like this repo, which catches a glob that stopped matching.
if [ ! -d docs/ux ]; then
    echo "check-docs: docs/ux is not a directory — the layout has moved and the" >&2
    echo "scan root needs updating." >&2
    exit 2
fi

found=$(printf '%s' "$sources" | grep -c .)
if [ "$found" -lt 8 ]; then
    echo "check-docs: found $found document(s) under docs/, too few to be this" >&2
    echo "repo — the layout has moved and the scan root needs updating." >&2
    exit 2
fi

failures=$(
    echo "$sources" | while IFS= read -r file; do
        awk -v file="$file" -v cap="$MAX_BODY_LINES" -v maxdates="$MAX_DATES" '
            # A checkbox body is the item line plus its continuation lines,
            # which are indented to sit under the marker. It ends at the first
            # non-blank line back at the item indent or further out.
            function flush() {
                if (start == 0) return
                if (prose > cap)
                    printf "%s:%d: checkbox body is %d prose lines, cap is %d\n",
                        file, start, prose, cap
                if (dates > maxdates)
                    printf "%s:%d: checkbox body carries %d dates, at most %d\n",
                        file, start, dates, maxdates
                start = 0
            }

            {
                line = $0
                trimmed = line
                sub(/^[[:space:]]+/, "", trimmed)

                if (trimmed ~ /^```/) {
                    if (start) infence = !infence
                    next
                }
                if (infence) next

                if (match(line, /^[[:space:]]*- \[[ x]\] /)) {
                    flush()
                    start = FNR
                    indent = index(line, "-") - 1 + 6
                    prose = 0
                    dates = 0
                }

                if (start == 0) next

                # Out of the body: a non-blank line that is not indented under
                # the marker. A blank line stays in, so a body may hold
                # paragraphs.
                if (trimmed != "" && length(line) - length(trimmed) < indent &&
                    !match(line, /^[[:space:]]*- \[[ x]\] /)) {
                    flush()
                    next
                }

                if (trimmed == "") next
                if (trimmed ~ /^\|/) next            # table row
                prose++
                rest = line
                while (match(rest, /[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]/)) {
                    dates++
                    rest = substr(rest, RSTART + RLENGTH)
                }
            }

            END { flush() }
        ' "$file"

        grep -n '~~' "$file" | while IFS= read -r hit; do
            echo "$file:${hit%%:*}: struck passage — the surviving half belongs in the present tense"
        done
    done
)

if [ -n "$failures" ]; then
    echo "$failures"
    count=$(printf '%s\n' "$failures" | grep -c .)
    echo
    echo "check-docs: $count problem(s). A checklist box keeps its instruction" >&2
    echo "and one status line; the run log and the struck claim belong in git." >&2
    exit 1
fi

echo "check-docs: no overgrown checkbox bodies and no struck passages in docs/"
