#!/usr/bin/env python3
"""Read and drive a device's screen over adb, for docs/running.md §4.

The checklist is a manual pass, and this is what makes it repeatable rather
than automatic: it reports what is on screen and taps where told, so a box's
evidence is a command someone else can run again.

    scripts/avd-ui.py dump                       # every node, one per line
    scripts/avd-ui.py find --desc 'Read'         # the nodes matching, with bounds
    scripts/avd-ui.py tap --text 'Save'
    scripts/avd-ui.py longpress --desc-contains 'chapter four'
    scripts/avd-ui.py wait --text 'Habits' --timeout 10
    scripts/avd-ui.py shot /tmp/today.png
    scripts/avd-ui.py watch --seconds 6          # what appeared and vanished
    scripts/avd-ui.py frames /tmp/out --seconds 3

Why it reads rather than infers, in four rules each bought with a lost run:

- **`screencap` and `uiautomator dump` inject no input**, which is why a box
  watched across a boundary is watched with them. Anything that taps changes
  the thing being measured.
- **A dump lags a tap by a recomposition.** Read a control's own `enabled`
  flag, never the label beside it: `find` prints the flag for that reason.
- **Match a file picker on the full filename.** A partial match ranks the
  clickable preview thumbnail above the filename, because the thumbnail
  carries the name in its own description -- and opens a preview instead of
  choosing the file. `--desc` and `--text` are exact by default; the
  `-contains` forms are the ones to think twice about.
- **A snackbar cannot be caught by racing a dump against it.** `watch` diffs a
  burst of dumps against the settled screen instead, and a duration comes off
  `frames`, not off a stopwatch.

Injected input also bypasses the accessibility layer, so nothing here can be
used to check TalkBack: under TalkBack a tap *toggles* the row it lands on,
which is the direct tap those boxes forbid arriving by another door.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass

DUMP_ON_DEVICE = "/sdcard/window_dump.xml"
BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")


def adb(*args: str, binary: bool = False, check: bool = True) -> bytes | str:
    """One adb invocation. `ANDROID_SERIAL` picks the device, as §2 documents."""
    result = subprocess.run(["adb", *args], capture_output=True, check=False)
    if check and result.returncode != 0:
        raise SystemExit(f"avd-ui: adb {' '.join(args)} failed: {result.stderr.decode(errors='replace').strip()}")
    return result.stdout if binary else result.stdout.decode(errors="replace")


@dataclass
class Node:
    cls: str
    text: str
    desc: str
    rid: str
    enabled: bool
    clickable: bool
    bounds: tuple[int, int, int, int]

    @property
    def centre(self) -> tuple[int, int]:
        left, top, right, bottom = self.bounds
        return (left + right) // 2, (top + bottom) // 2

    def __str__(self) -> str:
        parts = [self.cls.rsplit(".", 1)[-1]]
        if self.text:
            parts.append(f"text={self.text!r}")
        if self.desc:
            parts.append(f"desc={self.desc!r}")
        if self.rid:
            parts.append(f"id={self.rid.rsplit('/', 1)[-1]}")
        parts.append(f"enabled={str(self.enabled).lower()}")
        if self.clickable:
            parts.append("clickable")
        parts.append(f"at={self.centre[0]},{self.centre[1]}")
        return " ".join(parts)


def dump() -> list[Node]:
    """Every node on screen.

    Dumped to a file and read back rather than to stdout: `uiautomator dump`
    writes its own chatter onto the stream, and a stale file from a previous
    dump reads as a live screen -- which is its own trap, so the file is
    removed first and its absence is an error rather than an empty screen.
    """
    adb("shell", "rm", "-f", DUMP_ON_DEVICE, check=False)
    adb("shell", "uiautomator", "dump", DUMP_ON_DEVICE)
    raw = adb("exec-out", "cat", DUMP_ON_DEVICE, binary=True)
    if not raw.strip():
        raise SystemExit("avd-ui: the dump is empty -- the window may be mid-animation; try again")
    nodes: list[Node] = []
    # A dump taken while a list is re-rendering carries the same node twice,
    # identical in every attribute including its bounds. That is an artefact of
    # the dump rather than two controls, and collapsing it is what lets `one`
    # stay strict: two nodes at *different* bounds are still an ambiguity worth
    # refusing, and still are.
    seen: set[tuple] = set()
    for element in ET.fromstring(raw).iter("node"):
        matched = BOUNDS.fullmatch(element.get("bounds", ""))
        if matched is None:
            continue
        key = (
            element.get("class", ""),
            element.get("text", ""),
            element.get("content-desc", ""),
            element.get("resource-id", ""),
            element.get("enabled"),
            element.get("bounds"),
        )
        if key in seen:
            continue
        seen.add(key)
        nodes.append(
            Node(
                cls=element.get("class", ""),
                text=element.get("text", ""),
                desc=element.get("content-desc", ""),
                rid=element.get("resource-id", ""),
                enabled=element.get("enabled") == "true",
                clickable=element.get("clickable") == "true",
                bounds=tuple(int(g) for g in matched.groups()),
            )
        )
    return nodes


def select(nodes: list[Node], args: argparse.Namespace) -> list[Node]:
    """The nodes a command was aimed at, exact match first.

    Exactness is the default because of the file picker: a row's preview
    thumbnail carries the file name in its own description, so a contains-match
    on a name ranks the thumbnail alongside the row and the tap opens a preview
    instead of choosing the file.
    """
    found = nodes
    if args.text is not None:
        found = [n for n in found if n.text == args.text]
    if args.desc is not None:
        found = [n for n in found if n.desc == args.desc]
    if args.text_contains is not None:
        found = [n for n in found if args.text_contains in n.text]
    if args.desc_contains is not None:
        found = [n for n in found if args.desc_contains in n.desc]
    if args.id is not None:
        found = [n for n in found if n.rid.rsplit("/", 1)[-1] == args.id]
    if getattr(args, "clickable", False):
        found = [n for n in found if n.clickable]
    return found


def one(nodes: list[Node], args: argparse.Namespace) -> Node:
    """Exactly one node, or a refusal naming what else matched.

    Never "the first match". A tap aimed at an ambiguous selector is how a blind
    tap chain wanders into another app, and the wandering is only visible much
    later.
    """
    found = select(nodes, args)
    if not found:
        raise SystemExit("avd-ui: nothing matched")
    if len(found) > 1:
        for node in found:
            print(f"  {node}", file=sys.stderr)
        raise SystemExit(f"avd-ui: {len(found)} nodes matched -- narrow it, do not guess")
    return found[0]


def wait_for(args: argparse.Namespace) -> Node:
    deadline = time.monotonic() + args.timeout
    while True:
        found = select(dump(), args)
        if len(found) == 1:
            return found[0]
        if time.monotonic() >= deadline:
            raise SystemExit(f"avd-ui: {len(found)} matches after {args.timeout}s")


def watch(seconds: float, after: str | None) -> None:
    """What appears and vanishes, against the screen as it settles.

    A snackbar outlives a single dump by less than the dump costs, so racing
    one is a coin toss. Diffing a burst against the settled screen is not: the
    line either turns up in the burst or it never existed.

    `after` runs once the baseline has been taken, and the ordering is the
    whole point: an action performed before the baseline has its own result
    dumped as part of the settled screen, and the watch then reports that
    nothing appeared -- a false negative in the one direction these boxes
    read as a pass.
    """
    settled = {(n.cls, n.text, n.desc) for n in dump()}
    if after:
        subprocess.run(after, shell=True, check=True)
    seen: dict[tuple[str, str, str], int] = {}
    passes = 0
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        passes += 1
        for node in dump():
            key = (node.cls, node.text, node.desc)
            if key not in settled:
                seen[key] = seen.get(key, 0) + 1
    print(f"{passes} dumps over {seconds}s; {len(seen)} node(s) not in the settled screen")
    for (cls, text, desc), count in sorted(seen.items(), key=lambda kv: -kv[1]):
        print(f"  x{count:<3} {cls.rsplit('.', 1)[-1]} text={text!r} desc={desc!r}")
    if not seen:
        print("  (nothing appeared -- on its own this proves nothing; pair it with the same")
        print("   action on a control shown to respond, or the absence is just a missed race)")


def frames(out_prefix: str, seconds: float, fps: int) -> None:
    """A recording split into frames, for anything that holds for a beat.

    A `screencap` round trip is most of a two-second window, so a line that
    holds that long is read off frames or not at all.
    """
    adb("shell", "screenrecord", "--time-limit", str(int(seconds) or 1), "/sdcard/avd-ui.mp4")
    adb("pull", "/sdcard/avd-ui.mp4", f"{out_prefix}.mp4")
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-i", f"{out_prefix}.mp4", "-vf", f"fps={fps}", f"{out_prefix}-%03d.png"],
        check=True,
    )
    print(f"{out_prefix}.mp4 and {out_prefix}-NNN.png at {fps} fps")



def settime(spec: str) -> None:
    """Drive an already-open Material time picker to `spec`, e.g. `12:00AM`.

    The **clock face**, never the keyboard mode beside it. Keyboard mode cannot
    be driven by `adb shell input`: its two fields hand focus back and forth,
    `KEYCODE_DEL` on the minute field bounces focus to the hour without
    deleting, and a field already holding two digits silently drops further
    input -- so a time that looks set is often the old one. The dial's ticks
    each carry a description (`3 o'clock`, `45 minutes`), which is a selector,
    and tapping the hour advances the dial to minutes on its own.

    The caller opens the dialog. This leaves it **committed**, because the
    dialog commits on *Set* and Back discards the selection silently
    (docs/running.md §4).
    """
    matched = re.fullmatch(r"(\d{1,2}):(\d{2})\s*([AaPp][Mm])", spec.strip())
    if matched is None:
        raise SystemExit(f"avd-ui: cannot read a time from {spec!r}; want e.g. 12:00AM")
    hour, minute, meridiem = int(matched[1]), int(matched[2]), matched[3].upper()
    if minute % 5:
        raise SystemExit(f"avd-ui: the dial only carries five-minute ticks, not {minute}")

    if any(n.cls.endswith("EditText") for n in dump()):
        # Keyboard mode is showing. Its toggle is the only described switch.
        _tap_described("Switch to text input mode for the time input", fallback_y=None)

    _tap_dial(f"{hour} o'clock")
    _tap_dial(f"{minute} minutes")
    for node in dump():
        if node.text == meridiem:
            adb("shell", "input", "tap", *map(str, node.centre))
            break
    else:
        raise SystemExit(f"avd-ui: no {meridiem} control on this dialog")
    time.sleep(1)
    for node in dump():
        if node.text == "Set":
            adb("shell", "input", "tap", *map(str, node.centre))
            print(f"set {spec} and committed on Set")
            return
    raise SystemExit("avd-ui: no Set button -- nothing was committed")


def _tap_dial(description: str) -> None:
    """Tap the dial tick carrying `description`, never the field above it.

    The selected hour and minute are announced twice: once by the field at the
    top of the dialog and once by the tick on the dial. They are told apart by
    the field carrying text and the tick carrying only a description.
    """
    ticks = [n for n in dump() if n.desc == description and not n.text]
    if len(ticks) != 1:
        raise SystemExit(f"avd-ui: {len(ticks)} dial ticks for {description!r}")
    adb("shell", "input", "tap", *map(str, ticks[0].centre))
    time.sleep(1)


def _tap_described(description: str, fallback_y: int | None) -> None:
    for node in dump():
        if node.desc == description:
            adb("shell", "input", "tap", *map(str, node.centre))
            time.sleep(1)
            return
    raise SystemExit(f"avd-ui: nothing described {description!r}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)

    def selectors(p: argparse.ArgumentParser) -> None:
        p.add_argument("--text", help="exact text")
        p.add_argument("--desc", help="exact content description")
        p.add_argument("--text-contains", dest="text_contains")
        p.add_argument("--desc-contains", dest="desc_contains")
        p.add_argument("--id", help="resource id, without the package")
        p.add_argument("--clickable", action="store_true")

    p_dump = sub.add_parser("dump", help="every node on screen")
    p_dump.add_argument("--all", action="store_true", help="include nodes with no text, description or id")

    selectors(sub.add_parser("find", help="the nodes matching, with their enabled flag"))
    selectors(sub.add_parser("tap", help="tap the one node matching"))
    selectors(sub.add_parser("longpress", help="long-press the one node matching"))

    p_wait = sub.add_parser("wait", help="block until exactly one node matches")
    selectors(p_wait)
    p_wait.add_argument("--timeout", type=float, default=10.0)

    p_shot = sub.add_parser("shot", help="a screenshot")
    p_shot.add_argument("path")

    p_watch = sub.add_parser("watch", help="diff a burst of dumps against the settled screen")
    p_watch.add_argument("--seconds", type=float, default=5.0)
    p_watch.add_argument("--after", help="shell command to run once the baseline is taken")

    p_time = sub.add_parser("settime", help="drive an open time picker via its clock face")
    p_time.add_argument("spec", help="e.g. 12:00AM")

    p_frames = sub.add_parser("frames", help="record and split into frames")
    p_frames.add_argument("prefix")
    p_frames.add_argument("--seconds", type=float, default=3.0)
    p_frames.add_argument("--fps", type=int, default=4)

    args = parser.parse_args()

    if args.command == "dump":
        for node in dump():
            if args.all or node.text or node.desc or node.rid:
                print(node)
    elif args.command == "find":
        found = select(dump(), args)
        for node in found:
            print(node)
        print(f"({len(found)} node(s))")
    elif args.command in ("tap", "longpress"):
        node = one(dump(), args)
        x, y = node.centre
        if args.command == "tap":
            adb("shell", "input", "tap", str(x), str(y))
        else:
            # Long-press is a swipe to itself: `input longpress` does not exist.
            adb("shell", "input", "swipe", str(x), str(y), str(x), str(y), "800")
        print(f"{args.command} at {x},{y}: {node}")
    elif args.command == "wait":
        print(wait_for(args))
    elif args.command == "shot":
        with open(args.path, "wb") as handle:
            handle.write(adb("exec-out", "screencap", "-p", binary=True))
        print(args.path)
    elif args.command == "settime":
        settime(args.spec)
    elif args.command == "watch":
        watch(args.seconds, args.after)
    elif args.command == "frames":
        frames(args.prefix, args.seconds, args.fps)
    return 0


if __name__ == "__main__":
    sys.exit(main())
