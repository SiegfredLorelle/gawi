#!/usr/bin/env python3
"""Build Gawi event-log exports to seed a device for docs/running.md §4.

Writes the app's own export envelope, which is then imported through Settings
-> Import. That route rather than sqlite is not a preference: §4 runs against
the **signed release APK**, which is not debuggable, so `run-as` is refused and
`adb root` is refused on the Play image `Small_Phone` runs. The app's importer
is the only writer into a release build's log.

    scripts/gen-seed.py --list
    scripts/gen-seed.py baseline --out /tmp/seed-baseline.json
    scripts/gen-seed.py regenerating --today 2026-09-19 --out /tmp/seed-regen.json

**Import is a merge, deduped by event id** (ExportReader). Two scenarios pushed
one after the other therefore union rather than replace, so a scenario swap is
`adb shell pm clear com.gawi.app` first -- which §4 wants anyway, and which
`make itest` needs and did not get once: `row()` waits for a node before
`performScrollTo()` can run and a `LazyColumn` never composes an off-screen
row, so a seeded device fails two WriteJourneyTest cases.

Two invariants this enforces rather than leaving to the caller, both bought
with a lost run:

- **Every instant is clamped behind the clock.** Habit identity is
  last-write-wins on `occurred_at`; an event dated ahead of now beats a real
  edit made afterwards, and the app then looks broken while the log is right.
- **Each scenario owns a disjoint id range.** Ids are minted by the repo's own
  `uuid(n)` fixture scheme, so two scenarios built from overlapping ranges
  would dedupe into each other on import rather than sit side by side.

The envelope is checked against ExportReader's ladder before it is written: one
bad event refuses the whole file, and the refusal arrives on the device with no
way back to the line that caused it.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta, timezone

# The app's own markers. Mismatch either and the importer answers "not an
# export" before it reads a single event.
EXPORT_FORMAT = "gawi.event-log"
EXPORT_FORMAT_VERSION = 1
SCHEMA_VERSION = 1

# What EventId accepts: lowercase, version nibble 7, RFC 9562 variant nibble.
# A v4 id satisfies a shape-only check and carries no time ordering, which is
# why the app pins the nibbles and why this does too.
CANONICAL_UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")

# ZoneOffset runs to +-18:00 and ExportReader refuses anything outside it.
MAX_OFFSET_MINUTES = 18 * 60

# A spare life per this many clean days, at most this many held (Streaks).
# Both are needed to seed a *break*: a run with a spare in hand does not break
# on a miss, it spends the spare, and the habit is then never named as broken.
CLEAN_PER_SPARE = 7
MAX_SPARE = 3

# Mascot.REGENERATING_WINDOW_DAYS. A break older than this stops being a mood.
REGENERATING_WINDOW_DAYS = 3

# HabitMetadata's defaults, so a seeded habit is indistinguishable from one the
# form made. The rename box turns on exactly this: on a habit carrying the
# defaults, carrying them forward and re-defaulting write identical bytes.
DEFAULT_ICON = "\U0001f4d6"
DEFAULT_COLOR = "#F22935"


def uuid(n: int) -> str:
    """The repo's own deterministic canonical id, ordered like `n`.

    Mirrors `com.gawi.core.domain.testing.uuid` so a seeded log is readable
    beside a test's, and so scenario ranges can be kept apart by arithmetic.
    """
    return "00000000-0000-7000-8000-" + format(n, "x").rjust(12, "0")


@dataclass
class Habit:
    """One habit and the days it was completed, in scenario-local terms."""

    name: str
    # Days before the run date, so a scenario is written once and stays true
    # whenever it is generated.
    done_days_ago: list[int] = field(default_factory=list)
    weekly: int | None = None
    tag: str | None = None
    created_days_ago: int = 200
    notes: dict[int, str] = field(default_factory=dict)


@dataclass
class Scenario:
    """A named log, its id range, and why it exists."""

    purpose: str
    id_base: int
    habits: list[Habit]


def _every(start: int, stop: int, step: int = 1) -> list[int]:
    """Days-ago from `start` down to `stop` inclusive, newest first."""
    return list(range(start, stop - 1, -step))


def _span(oldest_days_ago: int, newest_days_ago: int, step: int = 1) -> list[int]:
    return _every(oldest_days_ago, newest_days_ago, step)


def baseline_habits(break_inside_window: bool) -> list[Habit]:
    """The ten habits every screen block in this pass is read against.

    Ten rather than a handful because the app-bar chip cannot appear below
    nine on this AVD: its trigger is `firstVisibleItemIndex > 0`, so the panel
    has to leave the viewport entirely, and at 720x1280 / 320 dpi that is
    1056 px of viewport against a 628 px panel and 128 px rows.

    `break_inside_window` moves Stretch's last completion from five days back
    to two. That is the whole difference between the content tank and the
    regenerating one, and the arithmetic has an off-by-one in it worth keeping:
    Streaks dates a break `next(unit)` where `unit` is the *missed* day, so a
    run whose last hit is `d` is dated broken on **`d + 2`**, not `d + 1`. With
    the window at REGENERATING_WINDOW_DAYS a last hit four days back is still a
    face; five is the first that is only history.
    """
    stretch_last = 2 if break_inside_window else 5
    return [
        # A long clean run, unticked today. Carries the detail strip's
        # completed cells (the note boxes long-press one) and the only habit
        # with history in this month and the previous one but none in the one
        # before -- which is what "step back into a month you do not have"
        # needs, and why July is left empty across the whole log.
        Habit("Read", _span(12, 1), tag="career", notes={3: "chapter four"}),
        # The open past cells. Its run ended before the mood window, so the
        # tank stays content while three of the strip's cells are tappable.
        Habit("Stretch", _span(stretch_last + 2, stretch_last), tag="career"),
        # Six days standing, so one tick today crosses the seven-day rung and
        # plays the milestone.
        Habit("Meditate", _span(6, 1), tag="career"),
        # The weekly one: a streak counted in weeks and drawn with a `w`, and
        # the only habit that can be outstanding without being un-ticked today.
        Habit("Yoga", _span(90, 1, step=2), weekly=3, tag="health"),
        # Health's weight in the previous quarter. Daily April through June and
        # nothing since, so the focus sentence has something to shift *from*.
        Habit("Water", _span(171, 81), tag="health"),
        Habit("Walk", _span(171, 81, step=2), tag="health"),
        # Career's weight in this quarter, against health's in the last.
        Habit("Sleep log", _span(49, 1), tag="career"),
        Habit("Tidy", _span(49, 1, step=2), tag="career"),
        Habit("Journal", _span(40, 20), tag="health"),
        # No completions and no tag: the untagged case the focus sentence has
        # to ignore rather than call "Untagged".
        Habit("Draw"),
    ]


def focus_habits() -> list[Habit]:
    """Two habits whose tags lead in different, *complete* periods.

    The focus sentence is only claimed for a period that is over, against the
    one before it, and only when both had a tagged completion (`Focus.kt`). So
    the shifted and held cases each need two consecutive finished months with
    the right leaders, which `baseline` cannot give: it leaves July empty on
    purpose, for the history grid's "a month you do not have history in", and
    an empty previous period produces no sentence at all.

    June is health and July and August are career, so June to July shifts and
    July to August holds. The current month is never either: it is partial, so
    it reads "so far" and names its leader.
    """
    return [
        Habit("Run", _span(110, 81), tag="health"),
        Habit("Study", _span(80, 19), tag="career"),
    ]


SCENARIOS: dict[str, Scenario] = {
    "baseline": Scenario(
        purpose="habit detail, the history grid, Insights, and the content tank",
        id_base=0x100000,
        habits=baseline_habits(break_inside_window=False),
    ),
    "focus": Scenario(
        purpose="the Insights focus sentence: a shift, then a hold",
        id_base=0x300000,
        habits=focus_habits(),
    ),
    "regenerating": Scenario(
        purpose="the regenerating tank: one run broken inside the mood window",
        id_base=0x200000,
        habits=baseline_habits(break_inside_window=True),
    ),
}

# Thriving and the celebration are reached by ticking from `baseline`, which is
# what those boxes ask for. Worried needs no log of its own either: it is
# `nearBoundary`, so baseline plus a reminder time moved behind the clock in
# Settings, with one habit left open.


def build(scenario: Scenario, run_on: date, now: datetime, app_version: str) -> dict:
    """Turn a scenario into the envelope the importer reads.

    Events are emitted in `occurred_at` order and given ids in that same order,
    because `uuid(n)` sorts like `n` and the log's primary key is TEXT under a
    binary collation: ids ascending with time is the property the last-write-
    wins tiebreak reads.
    """
    dated: list[tuple[datetime, str, dict]] = []

    for index, habit in enumerate(scenario.habits):
        habit_id = uuid(scenario.id_base + 0x1000 + index)
        created = _instant(run_on - timedelta(days=habit.created_days_ago), now)
        schedule = {"kind": "daily"} if habit.weekly is None else {"kind": "weekly", "times_per_week": habit.weekly}
        payload = {
            "habit_id": habit_id,
            "name": habit.name,
            "icon": DEFAULT_ICON,
            "color": DEFAULT_COLOR,
            "schedule": schedule,
        }
        # Omitted rather than written null: the wire DTO defaults it, so the
        # app's own encoder leaves the key out and a seeded log should be
        # byte-comparable with an exported one.
        if habit.tag is not None:
            payload["tag"] = habit.tag
        dated.append((created, "HabitCreated", payload))

        for days_ago in habit.done_days_ago:
            on = run_on - timedelta(days=days_ago)
            completion = {"habit_id": habit_id, "logical_date": on.isoformat()}
            note = habit.notes.get(days_ago)
            if note is not None:
                completion["note"] = note
            dated.append((_instant(on, now), "CompletionAdded", completion))

    dated.sort(key=lambda entry: entry[0])

    events = []
    for offset, (at, type_name, payload) in enumerate(dated):
        events.append(
            {
                "id": uuid(scenario.id_base + offset),
                "occurred_at": at.strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z",
                "tz_offset_min": 0,
                "type": type_name,
                "schema_version": SCHEMA_VERSION,
                "payload": payload,
            }
        )

    return {
        "format": EXPORT_FORMAT,
        "format_version": EXPORT_FORMAT_VERSION,
        "exported_at": now.strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z",
        "app_version": app_version,
        "event_count": len(events),
        "events": events,
    }


def _instant(on: date, now: datetime) -> datetime:
    """Mid-evening on `on`, and never at or after `now`.

    The clamp is the invariant, not a tidy-up. A seeded event dated ahead of
    the clock wins the last-write-wins compare against an edit the user makes
    afterwards, so the screen stops agreeing with the form while the log is
    exactly right -- which reads as a broken app and costs a run to diagnose.
    """
    at = datetime(on.year, on.month, on.day, 20, 0, tzinfo=timezone.utc)
    return min(at, now - timedelta(minutes=1))


def check(envelope: dict) -> list[str]:
    """ExportReader's ladder, run here where a failure names a line.

    One bad event refuses the whole file, and the refusal arrives on the device
    as a sentence in a snackbar with no way back to what caused it.
    """
    problems: list[str] = []
    if envelope["event_count"] != len(envelope["events"]):
        problems.append(f"event_count {envelope['event_count']} but {len(envelope['events'])} events")

    seen: set[str] = set()
    required = {
        "HabitCreated": {"habit_id", "name", "icon", "color", "schedule"},
        "CompletionAdded": {"habit_id", "logical_date"},
    }
    for index, event in enumerate(envelope["events"]):
        where = f"event {index} ({event['id']})"
        if not CANONICAL_UUID.fullmatch(event["id"]):
            problems.append(f"{where}: id is not a lowercase canonical UUIDv7")
        if event["id"] in seen:
            problems.append(f"{where}: duplicate id -- a merge would dedupe it away")
        seen.add(event["id"])
        if abs(event["tz_offset_min"]) > MAX_OFFSET_MINUTES:
            problems.append(f"{where}: tz_offset_min outside +-{MAX_OFFSET_MINUTES}")
        missing = required.get(event["type"], set()) - set(event["payload"])
        if missing:
            problems.append(f"{where}: payload is missing {sorted(missing)}")

    ids = [event["id"] for event in envelope["events"]]
    if ids != sorted(ids):
        problems.append("ids are not ascending, so id order no longer equals log order")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("scenario", nargs="?", help="which log to build")
    parser.add_argument("--list", action="store_true", help="name the scenarios and what each is for")
    parser.add_argument("--out", help="where to write the envelope (default: stdout)")
    parser.add_argument("--today", help="the run date the scenario is written against (default: today)")
    parser.add_argument("--app-version", default="1.0.0", help="recorded in the envelope, read by nothing")
    args = parser.parse_args()

    if args.list:
        for name, scenario in SCENARIOS.items():
            print(f"{name:14} id base {scenario.id_base:#x}  {scenario.purpose}")
        return 0

    if args.scenario not in SCENARIOS:
        parser.error(f"unknown scenario {args.scenario!r}; --list names them")

    run_on = date.fromisoformat(args.today) if args.today else date.today()
    now = datetime.now(timezone.utc)
    envelope = build(SCENARIOS[args.scenario], run_on, now, args.app_version)

    problems = check(envelope)
    if problems:
        for problem in problems:
            print(f"gen-seed: {problem}", file=sys.stderr)
        return 1

    text = json.dumps(envelope, indent=2, ensure_ascii=False) + "\n"
    if args.out:
        with open(args.out, "w", encoding="utf-8") as handle:
            handle.write(text)
        counts: dict[str, int] = {}
        for event in envelope["events"]:
            counts[event["type"]] = counts.get(event["type"], 0) + 1
        print(f"{args.out}: {envelope['event_count']} events " + ", ".join(f"{n} {t}" for t, n in sorted(counts.items())))
    else:
        sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
