#!/usr/bin/env bash
#
# Every `<doc> §N` citation in Kotlin source names a document and a section that
# exist, and no single file uses one section number for two different documents.
#
# This exists because the comments here cite `docs/` heavily — 631 citations
# across 211 files — and nothing verified any of them. That is not hypothetical:
# the comment above `robolectric` in gradle/libs.versions.toml pointed at a
# `:core:data/robolectric.properties` that has never existed, and was believed
# for a phase. A citation is only worth writing if a stale one is noisy.
#
# **Why a script and not a Gradle task.** `make lint` is otherwise one `gradlew`
# invocation, and a task would be the more idiomatic home (build-logic owns
# build configuration). But a Gradle task caches, and this repo has been bitten
# more than once by a gate that passed by being UP-TO-DATE — the last recorded
# `make test` skipped 70 of its 71 suites and still exited 0. A script cannot go
# UP-TO-DATE. That is worth more here than idiom.
#
# **Dates are not this script's to check: see check-history.sh.** A regex cannot
# tell "measured on 2026-08-22" (legitimate — it stamps how stale a hardware
# measurement is) from "added 2026-08-21" (changelog that git already owns), and
# for a long time that was the argument for not gating them at all. What makes
# the gate possible is that the writer marks the exemption rather than the
# script inferring it: a dated comment line passes only if it also says
# `measured` or `seen on`.
#
# **Known limit.** Only citations with a recognised document token are checked.
# A bare `§4` is unresolvable from text alone and is legitimate prose when the
# enclosing block already anchored it — which is why rule 2 exists instead: it
# catches the case where the anchor is ambiguous because the file used the same
# number for something else. External standards (`RFC 9562 §4`) are skipped;
# they are not ours to resolve.

set -uo pipefail

cd "$(dirname "$0")/.." || exit 2

if [ ! -f docs/architecture.md ]; then
    echo "check-citations: run from the repo (docs/architecture.md not found)" >&2
    exit 2
fi

failures=0

# The document tokens `resolve_doc` below recognises, as one alternation. Used
# to tell an anchored citation from a bare one.
DOC_TOKEN='(architecture|Architecture|PRD|prd|today-view|[A-Za-z0-9_./-]+\.md)'

# A document token as written in a comment -> the file it means. Unknown tokens
# are prose ("the §4", "is §4") and are skipped rather than guessed at.
resolve_doc() {
    case "$1" in
        architecture | Architecture) echo docs/architecture.md ;;
        PRD | prd) echo docs/prd.md ;;
        today-view) echo docs/ux/today-view.md ;;
        RFC) echo "" ;;
        # A token ending in .md is unambiguously meant as a document, so failing
        # to find it is a finding rather than something to skip. That is the
        # exact shape of the bug this script was written for: a comment naming
        # a `:core:data/robolectric.properties` that had never existed.
        *.md)
            if [ -f "$1" ]; then
                echo "$1"
            else
                found=$(find docs -name "$(basename "$1")" -print -quit 2>/dev/null)
                echo "${found:-MISSING}"
            fi
            ;;
        *) echo "" ;;
    esac
}

# `## N.` at the top level of a markdown file.
has_section() {
    grep -qE "^## $2\. " "$1"
}

# `§N.M` is written for two different things in these docs and both are legal:
#
#   - a literal sub-numbered heading — `## 3.5 Identity` in the PRD, which
#     `PRD §3.5` means;
#   - the Mth ordered-list item inside `## N.` — `architecture §1.6` means the
#     sixth principle, "Commands are validated; events are not".
#
# Checking only the second was this script's own first bug: it reported the one
# real `PRD §3.5` citation as dangling. Accept either.
has_subsection() {
    grep -qE "^##+ $2\.$3( |\$)" "$1" && return 0

    awk -v want="$2" -v item="$3" '
        $0 ~ "^## " want "\\. " { inside = 1; next }
        /^## [0-9]+\. / { inside = 0 }
        inside && $0 ~ "^" item "\\. " { found = 1 }
        END { exit !found }
    ' "$1"
}

sources=$(find app core feature widget -name '*.kt' -path '*/src/*' | sort)

# --- Rule 1: every anchored citation resolves ------------------------------
while IFS= read -r file; do
    while IFS= read -r hit; do
        line=${hit%%:*}
        cite=${hit#*:}
        token=${cite% §*}
        section=${cite##*§}

        doc=$(resolve_doc "$token")
        [ -z "$doc" ] && continue

        if [ "$doc" = MISSING ] || [ ! -f "$doc" ]; then
            echo "$file:$line: cites '$token', which is not a file under docs/"
            failures=$((failures + 1))
            continue
        fi

        major=${section%%.*}
        if ! has_section "$doc" "$major"; then
            echo "$file:$line: cites $token §$section but $doc has no section $major"
            failures=$((failures + 1))
            continue
        fi

        if [ "$section" != "$major" ]; then
            minor=${section##*.}
            if ! has_subsection "$doc" "$major" "$minor"; then
                echo "$file:$line: cites $token §$section but $doc §$major has no item $minor"
                failures=$((failures + 1))
            fi
        fi
    done < <(grep -noE '[A-Za-z0-9_./-]+ §[0-9]+(\.[0-9]+)?' "$file")
done <<< "$sources"

# --- Rule 2: no bare `§N` where N means more than one document -------------
#
# The defect this guards: Mascot.kt anchored `docs/ux/today-view.md §4`, then
# used `architecture §4` ten lines later and `PRD §4` ninety lines after that.
# Three documents behind one number, and every unanchored `§4` in between
# ambiguous.
#
# Note what is *not* the defect. A file citing three different `§4`s is fine so
# long as each one names its document — Mascot.kt legitimately needs all three.
# Shorthand after a single anchor is good prose too; repeating
# `docs/ux/today-view.md` nine times would read worse. What cannot stand is
# shorthand after *conflicting* anchors, because then the shorthand has no
# single referent to inherit.
while IFS= read -r file; do
    pairs=$(grep -hoE '[A-Za-z0-9_./-]+ §[0-9]+' "$file" 2>/dev/null |
        while IFS= read -r cite; do
            doc=$(resolve_doc "${cite% §*}")
            # An unresolvable .md is rule 1's finding, not a second document.
            [ -n "$doc" ] && [ "$doc" != MISSING ] && echo "${cite##*§} $doc"
        done | sort -u)

    [ -z "$pairs" ] && continue

    for num in $(echo "$pairs" | awk '{print $1}' | sort | uniq -d); do
        docs=$(echo "$pairs" | awk -v n="$num" '$1 == n { printf "%s ", $2 }')

        # A bare occurrence is one the anchored count does not account for.
        # "Anchored" means preceded by a *document token* — matching any word
        # would count "the way §4" as anchored, which was this rule's own first
        # bug and hid four of Mascot.kt's bare references.
        while IFS=: read -r line text; do
            total=$(grep -oE "§$num([^0-9.]|\$)" <<< "$text" | wc -l)
            anchored=$(grep -oE "$DOC_TOKEN §$num([^0-9.]|\$)" <<< "$text" | wc -l)
            [ "$total" -le "$anchored" ] && continue
            echo "$file:$line: bare §$num, but this file uses §$num for: $docs"
            failures=$((failures + 1))
        done < <(grep -nE "§$num([^0-9.]|\$)" "$file")
    done
done <<< "$sources"

if [ "$failures" -gt 0 ]; then
    echo
    echo "check-citations: $failures problem(s). A section number must name its" >&2
    echo "document when the file uses that number for more than one document." >&2
    exit 1
fi

echo "check-citations: all doc citations resolve, no ambiguous section numbers"
