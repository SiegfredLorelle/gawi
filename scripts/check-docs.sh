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
#                     that box's history rather than its state. The cap is set
#                     from the finished document plus a margin, so it refuses
#                     regrowth without refusing ordinary editing.
#   - three or more dates in one checkbox body
#                     One status line may name when a check ran and when it was
#                     re-run; that is two. A third date is a third record, which
#                     is the shape the cap above is usually catching from the
#                     other side, caught here even when the prose is terse.
#   - a struck passage
#                     A struck claim with its correction after it is history
#                     written in place. The surviving half belongs in the
#                     present tense and the struck half belongs in git.
#
# What it deliberately does not check. Whether a status line is *true*, which
# only a device can say. Whether the prose in a body is instruction or
# narrative, which is the judgement the rule is actually asking for and which no
# regex reaches — the cap is a volume proxy for it and nothing more. And prose
# outside a checkbox, because the argument in a section is not a checklist entry
# and has no comparable shape.
#
# Three exemptions, each of which a document needs in order to describe this
# rule at all. Fenced blocks and tables inside a body do not count toward the
# cap: a recipe is instruction however long it runs. A fenced block is skipped
# outright, so a document may show the checklist form or the strike markers as
# an example. And an inline code span is stripped before the strike scan, so a
# sentence may name the marker it must not use.
#
# Two traps this check fell into itself, both worth not repeating. Fence state
# belongs to the file scanner and not to the body scanner: gated on being inside
# a body, a fenced example holding a checkbox line opened a phantom body whose
# closing fence then switched the scanner off for the rest of the file, and the
# success line printed anyway. And awk runs over every file in one invocation so
# that its exit status can be checked — run per file inside a pipeline, a
# malformed program failed on all of them and the script still reported a pass.
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

# 26 against a longest finished body of 20 prose lines. The margin is what lets
# a box take another sentence of instruction without a lint failure; going past
# it is a decision about the rule, not a fix for a box that outgrew it. Two
# boxes lost real setup instructions to a tighter cap once, which is the failure
# this margin exists to prevent.
MAX_BODY_LINES=26
MAX_DATES=2

# A scan that found nothing to scan is not a pass. `find` writes to stderr and
# keeps going when a root is missing, its status is swallowed here, and there is
# no `set -e` — so a moved tree would otherwise print the success line below and
# exit 0. Two nets, because a count alone is the weaker one: the roots have to
# exist, which is what catches a rename, and the total has to look like this
# repo, which catches a glob that stopped matching.
for root in docs docs/ux; do
    if [ ! -d "$root" ]; then
        echo "check-docs: $root is not a directory — the layout has moved and" >&2
        echo "the scan roots need updating." >&2
        exit 2
    fi
done

mapfile -t sources < <(find docs -type f -name '*.md' | sort)

if [ "${#sources[@]}" -lt 8 ]; then
    echo "check-docs: found ${#sources[@]} document(s) under docs/, too few to" >&2
    echo "be this repo — the layout has moved and the scan root needs updating." >&2
    exit 2
fi

findings=$(
    awk -v cap="$MAX_BODY_LINES" -v maxdates="$MAX_DATES" '
        # A checkbox body is the item line plus its continuation lines, which
        # are indented to sit under the marker. It ends at the first non-blank
        # line back at the item indent or further out.
        function flush() {
            if (start == 0) return
            if (prose > cap)
                printf "%s:%d: checkbox body is %d prose lines, cap is %d\n",
                    startfile, start, prose, cap
            if (dates > maxdates)
                printf "%s:%d: checkbox body carries %d dates, at most %d\n",
                    startfile, start, dates, maxdates
            start = 0
        }

        FNR == 1 { flush(); infence = 0; fencechar = ""; fencelen = 0 }

        {
            # Tabs are expanded before any indent arithmetic, or a tab-indented
            # continuation counts as one column and the body ends at its first
            # line.
            line = $0
            gsub(/\t/, "        ", line)
            trimmed = line
            sub(/^[[:space:]]+/, "", trimmed)

            # A fence is paired by its own delimiter, per CommonMark: the
            # closing run is the same character, at least as long, and carries
            # nothing but whitespace. Toggling on either marker let a ``` block
            # holding a ~~~ line — or the reverse — close early, which ends the
            # exemption in the middle of an example.
            if (infence) {
                if (match(trimmed, /^(`{3,}|~{3,})/) &&
                    substr(trimmed, 1, 1) == fencechar &&
                    RLENGTH >= fencelen &&
                    substr(trimmed, RLENGTH + 1) ~ /^[[:space:]]*$/) {
                    infence = 0
                }
                next
            }
            if (match(trimmed, /^(`{3,}|~{3,})/)) {
                fencechar = substr(trimmed, 1, 1)
                fencelen = RLENGTH
                infence = 1
                next
            }

            if (match(line, /^[[:space:]]*- \[[ xX]\] /)) {
                flush()
                start = FNR
                startfile = FILENAME
                indent = index(line, "-") - 1 + 6
                prose = 0
                dates = 0
            }

            if (start == 0) next

            # Out of the body: a non-blank line that is not indented under the
            # marker. A blank line stays in, so a body may hold paragraphs.
            if (trimmed != "" && length(line) - length(trimmed) < indent &&
                !match(line, /^[[:space:]]*- \[[ xX]\] /)) {
                flush()
                next
            }

            if (trimmed == "") next
            if (trimmed ~ /^\|/) next
            prose++
            rest = line
            while (match(rest, /[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]/)) {
                dates++
                rest = substr(rest, RSTART + RLENGTH)
            }
        }

        END { flush() }
    ' "${sources[@]}"
) || {
    echo "check-docs: the body scan failed to run" >&2
    exit 2
}

strikes=$(
    awk '
        FNR == 1 { infence = 0; fencechar = ""; fencelen = 0 }

        {
            fence = $0
            sub(/^[[:space:]]+/, "", fence)

            if (infence) {
                if (match(fence, /^(`{3,}|~{3,})/) &&
                    substr(fence, 1, 1) == fencechar &&
                    RLENGTH >= fencelen &&
                    substr(fence, RLENGTH + 1) ~ /^[[:space:]]*$/) {
                    infence = 0
                }
                next
            }
            if (match(fence, /^(`{3,}|~{3,})/)) {
                fencechar = substr(fence, 1, 1)
                fencelen = RLENGTH
                infence = 1
                next
            }

            stripped = $0
            gsub(/`[^`]*`/, "", stripped)
            if (stripped ~ /~~/)
                printf "%s:%d: struck passage — the surviving half belongs in the present tense\n",
                    FILENAME, FNR
        }
    ' "${sources[@]}"
) || {
    echo "check-docs: the struck-passage scan failed to run" >&2
    exit 2
}

failures=$(printf '%s\n%s' "$findings" "$strikes" | grep -c .)

if [ "$failures" -gt 0 ]; then
    [ -n "$findings" ] && echo "$findings"
    [ -n "$strikes" ] && echo "$strikes"
    echo
    echo "check-docs: $failures problem(s). A checklist box keeps its instruction" >&2
    echo "and one status line; the run log and the struck claim belong in git." >&2
    exit 1
fi

echo "check-docs: no overgrown checkbox bodies and no struck passages in docs/"
