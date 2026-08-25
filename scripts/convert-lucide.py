#!/usr/bin/env python3
"""Convert Lucide SVGs into the VectorDrawables :core:ui draws.

Run this rather than hand-editing anything in core/ui/src/main/res/drawable.
It needs python3 and network access; it is a developer tool, not part of the
build, so CI never runs it and `make` does not know about it.

    scripts/convert-lucide.py                 # fetch the pinned version
    scripts/convert-lucide.py --svg-dir DIR   # convert an already-fetched copy

**The version is pinned, and the bytes are checked.** Lucide v1 renamed icons
(`pie-chart` -> `chart-pie`, still aliased), so `@latest` would make the
checked-in files irreproducible and a re-run could silently redraw the app. A
pin alone does not prove a re-run got the same bytes, though, so DIGESTS carries
a SHA-256 per icon and a mismatch aborts before anything is written. Review
pointed out that the transport was the one thing here taken on trust while five
SVG attributes were asserted.

**`--svg-dir` is exempt from that check, deliberately.** It is the flag for
converting something local, which includes the deliberately malformed SVGs used
to test this script — the two-circle file that proved the circle positions and
the `<rect>` file that proved nothing is written on failure. Digesting that path
would reject exactly the inputs the checks depend on. The network path is the
one that needs to be reproducible; the local path is the one that needs to be
freely editable.

**Why a conversion step at all.** Lucide publishes no Android artifact, so the
choice is vendoring or nothing. Ten files of about 300 bytes, converted once
and reviewable in the diff, beats a hand-drawn set nobody can re-derive.

**The guard is the point.** `VectorDrawable` has no element for <circle>,
<rect>, <line>, <polyline> or <polygon>, and an SVG carrying one would
otherwise convert with that part of the drawing silently missing. <circle> is
converted exactly (see below); anything else is a hard failure asking for a
deliberate conversion rather than a quiet drop. This is not hypothetical:
`settings` in 1.34.0 is one path plus one circle.

**Nothing is written unless every icon converts.** The whole output is built
in memory first. An earlier version wrote each file inside the conversion loop
and reported failures afterwards, which defeated the guard above: a source that
had grown a <rect> exited non-zero *and* left a valid, silently truncated
drawable on disk, where it passed every assertion `GawiIconsTest` makes. Found
in review. Partial output is worse than none, because none is obvious.
"""

import argparse
import hashlib
import os
import sys
import urllib.request
import xml.etree.ElementTree as ET

VERSION = "1.34.0"
BASE = "https://unpkg.com/lucide-static@" + VERSION
NS = "{http://www.w3.org/2000/svg}"
OUT = os.path.join("core", "ui", "src", "main", "res", "drawable")

# The icons that must flip under an RTL layout direction. The manifest sets
# `supportsRtl="true"`, and the characters these replaced — `←` (U+2190), `‹`
# (U+2039), `›` (U+203A) — are all Bidi_Mirrored, so the text shaper flipped
# them and a VectorDrawable without this attribute does not. Missing it was an
# RTL regression rather than a gap: the Up arrow would point away from the edge
# it sits on, and the month pager would read inverted. Found in review.
#
# `list-checks` is here for consistency rather than regression — `☰` is
# symmetric, so nothing changed when it was replaced, but the icon leads with
# marks and follows with rules, and Material auto-mirrors its own list icons.
# The rest are non-directional: a gear, a pie, a pencil, a cross, and the
# stepper's pair all mean the same thing mirrored.
AUTO_MIRRORED = {"arrow-left", "chevron-left", "chevron-right", "list-checks"}

# SHA-256 of each pinned source, so a fetch that returns something else fails
# loudly instead of redrawing the app. Regenerate with:
#
#     sha256sum *.svg
#
# and note that a mismatch prints the digest it actually got, so moving the pin
# is a copy-paste rather than an arithmetic exercise.
DIGESTS = {
    "arrow-left": "e8704135ca5c590e638898fc29ff57eba13e664bf9bc0fee641b2aedb44e86c7",
    "chart-pie": "81bb79ff8218e8effd5a690cb330cff4f00191b00140e266e760c106ed1b10e2",
    "chevron-left": "a8cd01ec4ad145afd6009f4f0e251d5d1e7b24371ddfda0b0e2a7b64926e9fee",
    "chevron-right": "0502b201dcef6e134d30caa2d9ee142d4d4ea687f870ebe2e23b690e5745a4fd",
    "list-checks": "b28b7c88944dbeca67d03d4de3df5a57f97e4c058d7998f7c31d95ff8afb78a3",
    "minus": "eac2eab4c444ebb343ac7244956cbf8369223f06e72142d5376aeaab62f30cd7",
    "pencil": "e00fc1f07701978eeed6980f8cf2e84f5aff3794dbafa4d1a092898352008d2b",
    "plus": "9604e8eb752dca9b045084f28ba963cfce16bb0d035aa023152361ec6ef54507",
    "settings": "2f2fc973e5f104e94e44f845de8e2af4cf61f0d0298055b956f7057227700f3d",
    "x": "cac7f746fa2596dd081dfce44e061dcddb79bdb98a488f08cd8d5874fcb52332",
}

# Lucide slug -> drawable name. `x` becomes ic_close because the drawable is
# named for the job, not the glyph; every other name matches its source.
ICONS = {
    "arrow-left": "ic_arrow_left",
    "chart-pie": "ic_chart_pie",
    "chevron-left": "ic_chevron_left",
    "chevron-right": "ic_chevron_right",
    "list-checks": "ic_list_checks",
    "minus": "ic_minus",
    "pencil": "ic_pencil",
    "plus": "ic_plus",
    "settings": "ic_settings",
    "x": "ic_close",
}

# The icons licenses/Lucide-ISC.txt lists as derived from Feather, which adds
# MIT to their ISC. Six of the ten, so it is worth stating per file.
FEATHER = {"arrow-left", "chevron-left", "chevron-right", "minus", "plus", "x"}

HEADER = """<?xml version="1.0" encoding="utf-8"?>
<!--
    Lucide `{slug}`, converted from lucide-static {version}.
    {licence}

    Generated by scripts/convert-lucide.py, not hand-drawn. Re-run it against
    the pinned version rather than tuning the path data here.{note}
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"{mirrored}
    android:viewportWidth="24"
    android:viewportHeight="24">
"""

MIRRORED_ATTR = '\n    android:autoMirrored="true"'

PATH = """    <path
        android:pathData="{d}"
        android:strokeWidth="2"
        android:strokeColor="#FF000000"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
"""

CIRCLE_NOTE = """

    VectorDrawable has no circle element, so the source's circles are emitted
    as arc paths:

{items}

    Each substitution is exact rather than an approximation: an elliptical arc
    with rx = ry = r and the large-arc flag set traces a true semicircle, so
    two of them close the figure."""

CIRCLE_ITEM = """    <circle cx="{cx}" cy="{cy}" r="{r}"> is path {position} of {total}."""


def trim(value):
    """3.0 -> 3, so generated path data reads like the hand-written kind."""
    return "%g" % value


def circle_to_path(cx, cy, r):
    """A circle as two semicircular arcs, starting at its west point."""
    return "M%s,%s a%s,%s 0 1,0 %s,0 a%s,%s 0 1,0 -%s,0" % (
        trim(cx - r), trim(cy), trim(r), trim(r),
        trim(2 * r), trim(r), trim(r), trim(2 * r),
    )


def load(slug, svg_dir, problems):
    if svg_dir:
        # Unverified on purpose — see the module docstring.
        with open(os.path.join(svg_dir, slug + ".svg"), "rb") as handle:
            return handle.read()

    with urllib.request.urlopen("%s/icons/%s.svg" % (BASE, slug), timeout=30) as response:
        body = response.read()

    actual = hashlib.sha256(body).hexdigest()
    expected = DIGESTS.get(slug)
    if expected is None:
        problems.append("%s: no digest recorded; add one to DIGESTS" % slug)
    elif actual != expected:
        # Reported rather than raised, so one run names every mismatch and the
        # write loop is skipped wholesale.
        problems.append("%s: digest is %s, expected %s" % (slug, actual, expected))
    return body


def convert(slug, svg_dir, problems):
    root = ET.fromstring(load(slug, svg_dir, problems))

    # Asserted rather than assumed: every one of these is a property the
    # generated <path> attributes below hard-code, so a source that stopped
    # matching would be converted into a lie.
    for attribute, expected in (
        ("fill", "none"),
        ("stroke-width", "2"),
        ("stroke-linecap", "round"),
        ("stroke-linejoin", "round"),
        ("viewBox", "0 0 24 24"),
    ):
        actual = root.get(attribute)
        if actual != expected:
            problems.append("%s: %s is %r, expected %r" % (slug, attribute, actual, expected))

    datas, circles = [], []
    for element in root:  # document order, so the drawing stacks as authored
        tag = element.tag.replace(NS, "")
        if tag == "path":
            # `d` is optional in the SVG grammar and `get` returns None for it,
            # which used to reach the template and emit the literal string
            # `android:pathData="None"` — a file that inflates, passes every
            # assertion, and draws one path fewer than its source. The failure
            # this script exists to prevent, arriving through the one element it
            # supports rather than the ones it rejects. Found in review.
            d = element.get("d")
            if d:
                datas.append(d)
            else:
                problems.append("%s: a <path> carries no d attribute" % slug)
        elif tag == "circle":
            cx, cy, r = (float(element.get(key)) for key in ("cx", "cy", "r"))
            datas.append(circle_to_path(cx, cy, r))
            # Position recorded per circle rather than described once. These
            # headers are the only record of what was substituted, and an
            # earlier version documented the last circle only and hardcoded
            # "the second path", which was true of `settings` and of nothing
            # else. Found in review.
            circles.append((len(datas), cx, cy, r))
        else:
            problems.append(
                "%s: <%s> has no conversion here. Add one deliberately — "
                "dropping it would lose part of the drawing silently." % (slug, tag)
            )

    if not datas:
        problems.append("%s: nothing to draw" % slug)

    note = ""
    if circles:
        items = "\n".join(
            CIRCLE_ITEM.format(cx=trim(cx), cy=trim(cy), r=trim(r), position=position, total=len(datas))
            for position, cx, cy, r in circles
        )
        note = CIRCLE_NOTE.format(items=items)

    licence = "ISC; notice in licenses/Lucide-ISC.txt."
    if slug in FEATHER:
        licence = ("ISC, and MIT as well — this one derives from Feather.\n"
                   "    Both notices are in licenses/Lucide-ISC.txt.")

    body = HEADER.format(
        slug=slug,
        version=VERSION,
        licence=licence,
        note=note,
        mirrored=MIRRORED_ATTR if slug in AUTO_MIRRORED else "",
    )
    for d in datas:
        body += PATH.format(d=d)
    return body + "</vector>\n", len(datas), bool(circles)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--svg-dir", help="convert a local copy instead of fetching")
    parser.add_argument("--out", default=OUT, help="drawable directory to write")
    args = parser.parse_args()

    if not os.path.isdir(args.out):
        sys.exit("no drawable directory at %s — run this from the repo root" % args.out)

    problems, pending, report = [], [], []

    # Asserted rather than assumed, like everything else in this file, and
    # because the untested direction is the one that bit: an AUTO_MIRRORED entry
    # that matches no ICONS slug — a typo, or an upstream rename of the kind the
    # docstring already records — silently stops mirroring that icon and exits 0.
    # GawiIconsTest does catch it, but in another module and with a message about
    # XML rather than about the typo.
    for name, table in (("AUTO_MIRRORED", AUTO_MIRRORED), ("DIGESTS", DIGESTS)):
        unknown = sorted(set(table) - set(ICONS))
        if unknown:
            problems.append("%s names %s, which ICONS does not" % (name, ", ".join(unknown)))

    for slug, drawable in sorted(ICONS.items()):
        body, count, circled = convert(slug, args.svg_dir, problems)
        pending.append((os.path.join(args.out, drawable + ".xml"), body))
        report.append("%-14s -> %-22s %d path(s)%s%s" % (
            slug, drawable + ".xml", count,
            "  [circle converted]" if circled else "",
            "  [autoMirrored]" if slug in AUTO_MIRRORED else ""))

    # Nothing has touched the working tree yet, and nothing will unless the
    # whole set converted. See the module docstring: writing as it went is what
    # let a truncated drawable survive a non-zero exit.
    if problems:
        print("FAILED, and nothing was written:", file=sys.stderr)
        for problem in problems:
            print("  " + problem, file=sys.stderr)
        sys.exit(1)

    for path, body in pending:
        with open(path, "w") as handle:
            handle.write(body)
    print("\n".join(report))
    print("\n%d drawables from lucide-static %s" % (len(ICONS), VERSION))


if __name__ == "__main__":
    main()
