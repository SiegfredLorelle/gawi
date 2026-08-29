# Momo: the character, its moods, and how it moves

Companion to [the PRD](../prd.md) §3.5 and §8's OQ-4, to
[visual-identity.md](visual-identity.md) §7 and §8, and to
[today-view.md](today-view.md) §3–§4, which own the slot and the mood rules.
This document owns the character: what it looks like, how each mood is drawn,
how it moves, and where it appears. It closes the second half of OQ-4 — the art
— that visual-identity.md §8 left open.

**Status: decided and built for the Today screen, 2026-08-25.** The character
is the one on the Gawi Redesign canvas, the motion is the one approved on its
"Momo motion" page, and the code is `core/ui/component/Momo.kt` and
`MomoDrawing.kt`, drawn into the Today panel by `feature/today/MascotPanel.kt`.
The widget, the reminder and the launcher icon followed later the same day —
the still frame on each of their own grounds (§4). **The habitat, the mood
transition and the day-complete celebration followed on 2026-08-26**, designed
on the canvas's "Habitat & motion" page and built in `feature/today/Habitat.kt`
and `Celebration.kt` (§3, §4, §6).

---

## 1. Three decisions, and the one that was reversed

**Style: flat.** The leaning visual-identity.md §7.2 recorded is the decision.
The reasons are the ones that were always there — it holds at 24px where the
launcher mark needs it, it sits beside a monoline icon set (§7.5) without
arguing, and every part is a rigid shape, which is what makes it cheap to move.

**The character is the canvas's, unchanged.** The Gawi Redesign canvas
(`https://claude.ai/code/artifact/f2c92c47-58a4-4547-bff5-695fa3705c17`, Momo &
icon page) drew Momo in all four moods with a CSS motion spec, and the user's
answer to "design the character" was that this *is* the character. §2 and §3
transcribe it; nothing here was redrawn. The same canvas's "Momo motion" page
(a standalone Momo Motion canvas until 2026-08-25, when it was folded in) put
the four moods on the Today tank in both themes with the timings exposed as
sliders and a still-frame board; the user approved it with the defaults, so the
numbers in §3 are the canvas's own.

**It is drawn in code, and Rive was dropped.** PRD §5 recommended Rive, and it
was researched to the point of an integration brief on 2026-08-25 before being
rejected on one fact: since 2025-10-20 Rive's free plan can neither import SVG
nor export a `.riv`; both need a paid seat. The user will not pay for tooling to
ship a personal app, and there is no free path through the editor. Lottie was
weighed as the free alternative and set aside: its runtime is free and
JVM-testable, but the only real editor is After Effects, and for moods it offers
no state machine — four JSON files and a crossfade, which is the `when (mood)`
we would write anyway, plus a dependency and an asset pipeline. What decided it
is what the motion actually is: every movement in §3 is a translate, rotate,
scale or opacity change on a fixed group. No path morphs, no bones. That is
exactly the class of animation a Compose `Canvas` does natively, so
`drawMomo` is the character and `MomoFrame.at(mood, seconds)` is the state
machine. **The one thing this gives up** is a visual timeline for tuning
motion, and the Momo Motion page is that timeline: change a slider there, read
the number into `MomoMotion`. Lottie stays the named fallback if a future
sequence — a milestone celebration, say — needs morphing.

## 2. The character

Drawn in a 260 × 200 design space, scaled to fit whatever box it is given and
centred. Coordinates below are that space's; `MomoDrawing.kt` holds them and
`MomoRenderTest` measures them.

| Part | Shape | Colour |
|---|---|---|
| Body | ellipse at (130, 104), radii 62 × 51 | `#F7C3D1` |
| Belly | ellipse at (130, 122), 42 × 28, 62 % opaque | `#FFE3EA` |
| Tail | `M130,150 q-8,26 4,34 q14,-10 12,-34 Z` | `#E8879F` |
| Limbs | ellipses 15 × 10 at (82, 152) turned −26° and (178, 152) turned 26° | `#F7C3D1` |
| Gills | six: strokes 7.5 wide, round caps, from roots (84, 78), (74, 100), (84, 122) and their mirrors at x 176/186/176, each ending in five beads | stroke `#E8879F`, beads `#F2A0B8` |
| Blush | ellipses 11 × 7 at (92, 114) and (168, 114), 40 % | `#F58AA6` |
| Eyes, mouth | per mood, §3 | ink `#3A2530`, mouth `#C2607A` |

**The palette is the character's, not the theme's**, and this corrects
visual-identity.md §3's table, which reserved a single "Momo only" coral
(`#E0708F` light / `#FFB1C4` dark). The canvas never drew that colour: Momo is
a pastel — the body above with `#E8879F` as its accent — and is the same in
both themes, the way a plush toy is. The reservation §3 made stands in the
form that matters: the pink family belongs to the mascot, and no theme role is
drawn from it (`Color.kt` says so). What §3 got wrong was the value, and the
row now says so.

Momo is an illustration, so contrast against `surface` is not a floor here —
the same reasoning §3 gives for fills. What carries the silhouette is the ink
of the eyes and the deeper coral of the gills and tail, both of which read on
either tank. The pastel body on the light theme's tank is the softest edge, and
`docs/running.md` §4 looks at it rather than a number asserting it.

## 3. The four moods

One drawing per mood for the face, one set of timings for the motion. Periods
are seconds; a "float" is the whole character rising and settling, a "breathe"
the body scaling about (130, 118), a "sway" each gill rotating about its root
between minus and plus the angle, with the six gills offset from each other by
0.2 / 0.7 / 1.2 s on the left and 0.45 / 0.95 / 1.5 s on the right so they never
move as one. Every curve is the canvas's `ease-in-out` keyframe pair, which a
half-cosine is.

| | THRIVING | CONTENT | WORRIED | REGENERATING |
|---|---|---|---|---|
| Eyes | happy arcs, `M95,99 Q104,87 113,99` | happy arcs | round, 9.7 × 12 with a highlight | sad arcs, `M95,95 Q104,103 113,95` |
| Mouth | open smile, filled `M113,118 Q130,138 147,118 Z` | open smile | wavy line, 3.6 stroke | small line, `M119,121 Q130,130 141,121` |
| Extras | two gold `#FFCE5C` eight-point stars, 2.1 s pulse | — | one sweat bead `#8FD3E8` at (186, 66), 2.6 s fall; gills hang 7 px lower | the right upper gill is **short** — reaches (191.6, 65.8) instead of (199.6, 59.5), stroke 5.5, smaller beads — inside a pulsing halo (r 20, 10–30 %), and it grows and settles on a 2.7 s cycle |
| Float | 2.5 s, 7 px | 4.2 s, 7 px, tilting −1.1° | a 1.7 s fidget: ±2.5 px sideways, ±0.8°, no rise | 6 s, 7 px |
| Breathe | 1.5 s | 3.4 s | 3.4 s | 5 s |
| Gill sway | 1.5 s, ±4.5° | 2.9 s, ±4.5° | 4.4 s, ±4.5° | 4.6 s, ±4.5° |
| Blink | never | every 5.4 s | every 5.4 s | every 5.4 s |
| Colour | full | full | full | 34 % saturation, and the tank drains |

A blink is the canvas's `steps(1,end)`: the eyes are 12 % tall between 96 % and
98 % of the period, and open otherwise.

**A mood change is one Momo, not two.** The first build crossfaded two whole
drawings over 0.55 s, and because each mood floats at its own tempo the two
bodies sat at different heights while both were visible. Since 2026-08-26 the
body, tail and five ordinary gills are drawn once from the two moods' frames at
the same instant, interpolated by a progress that runs over 550 ms
(`MomoMotion.TRANSITION_MILLIS`, Compose's fast-out-slow-in), and only what
differs between two drawings crossfades: the eyes and mouth, the sparkles, the
sweat bead, and the right upper gill, which is short while it regrows. The
water and the tank life (§4) drain and refill on the same progress. The Habitat
& motion page's "Mood transition" board runs the same frame maths in the
browser, which is where the duration was approved. With animations off the
change is a cut: a fade is an animation too.

**REGENERATING is the face this whole vocabulary exists for**, and its rules
are in the drawing rather than the copy: the character is dimmer, slower and
visibly regrowing, and it is *not* sad — the mouth is small, not turned down,
and the sad arcs are half the depth of the happy ones. PRD §3.5's "pick the
thread back up" is what the halo says. Its copy line is the fourth face's first
words and a placeholder like the other three (today-view §6): it should name
the habit, and nothing tells the panel which habit yet.

## 4. Where Momo appears

| Surface | Ground | Motion | Status |
|---|---|---|---|
| Today | the tank: a 250 dp panel, water in `primaryContainer → primaryFixedDim`, drained to `surfaceContainerHighest → surfaceContainerHigh` while regenerating, with four weeds and four bubbles keeping the mood's tempo behind the character | animated | **built** — `MascotPanel.kt`, `Habitat.kt` |
| Widget | the Today widget's own background, above the rows, only when the host gives it two cells (≥ 170 dp); from 220 dp wide as well, on a flat `primaryContainer` pill beside the mood line and the woven day band ([widget.md](widget.md) §7) | the resting frame, `MomoFrame.rest`, rasterised by `drawMomo` at 72 dp, or 48 dp inside the pill | **built** — `widget/MomoBitmap.kt`, `TodayWidget.kt` |
| Momo widget | her own 2×2 tile: flat `primaryContainer`, the tank colour without the gradient, and one word beneath her — no rows, no number ([widget.md](widget.md) §7) | the resting frame at 72 dp | **built 2026-08-29** — `widget/MomoWidget.kt` |
| Reminder | the notification's small icon is alpha-only, so a silhouette — of the launcher mark, which is what holds at 24 dp | still | **built** — `app/res/drawable/ic_reminder.xml` |
| Launcher | visual-identity §7.1's mark, derived from this character, on light `primaryContainer`; the woven thread as the monochrome layer | still | **built** — `app/res/mipmap-anydpi/ic_launcher.xml` |

**The tank is Today's alone.** It is drawn in `:feature:today`, not `:core:ui`,
because only Today is a habitat: the widget and the reminder get the character
on their own grounds. The character itself is in `:core:ui` for the reason
architecture §2 gives — the widget's still frame draws it too — and the
widget → `core:ui` edge that carried one font now carries Momo's geometry as
well, and nothing else.

**The habitat has life in it, and the life keeps time with the mood.** The
four weeds and four bubbles were on the canvas's Momo motion boards from the
start, unchanged in shape and colour; the Today screen simply did not draw
them until 2026-08-26. `Habitat.kt` transcribes them — `HabitatFrame.at` is a
pure function of the mood and the clock, like `MomoFrame.at`, and
`drawHabitat` paints it behind Momo, in dp, the left pair placed from the left
edge and the right pair from the right so the tank keeps its shape at any
width. What changes per mood is one **tempo**: a multiplier that scales the
canvas's 5.2 s weed sway and its 7.4–10 s bubble rises together, so the tank
cannot drift out of step with itself — 0.6 thriving, 1.0 content, 1.3
worried, 1.7 regenerating — and a sway of ±5° / 5° / 3° / 1.5°. Regenerating
leans the weeds 22° outward, greys them and stops the bubbles. The numbers are
the Habitat & motion page's defaults, approved 2026-08-26. Colours are roles
and a highlight: weeds `primary` at 55 % (light `#1F6F78` is the canvas's
`#027273`, dark `#7FD4DC` its `#6CE0E1`), drained weeds `outline`, bubbles the
same white as Momo's eye highlight so both themes read as the canvas did — and
so the drain is a role swap, not a second `saturated()`. The habitat is
`:feature:today`'s, like the tank, and nothing about it crosses the widget
edge.

**The widget draws `drawMomo`, not four drawables.** visual-identity §7.4
priced Momo-on-a-widget as "a static vector drawable per mood: four assets"
before the character was code. With `drawMomo` public, the widget rasterises
the resting frame the way it already rasterises Outfit (`MomoBitmap.kt` beside
`BitmapText.kt`): zero assets, no fifth copy of the geometry, and the same
pixels a viewer with animations off sees on Today. It is gated on the size the
host reports — one cell tall stays the name-and-checkbox widget
[widget.md](widget.md) §2 settled; from 170 dp the face sits above the rows, 72
dp tall, described once by TalkBack in the Today panel's words. Beside the
no-habits copy the face is decorative, so the copy is still read once.

**The reminder and the launcher use the mark, not the character.** The
canvas's "Launcher icon" artboard measured the full character as mush at 40 px
and the mark — two fronds a side, no blush, oversized eyes and mouth — as
holding at 24. A notification small icon *is* 24 dp, so its silhouette is the
mark's, eyes cut out with `evenOdd` so the face survives one colour.

**The panel is 250 dp, not 96.** today-view §3 promised a box that does not
move when the character replaces the placeholder, and the placeholder's box
was a 96 dp floor for two lines of copy. The redesign drew the tank at 250 and
the Momo Motion page showed it at that size on a phone; the user approved it
that way. So the promise is kept in the direction that matters — the slot is
still one box in one place — and its height is the tank's, fixed rather than a
floor: the character has a size, and the copy beneath it is what grows with
the font scale.

**Colour follows the theme, the character does not.** The water is theme
roles so it is right in both schemes with no second palette; Momo is §2's own
colours in both, and the only thing that varies them is the regenerating
desaturation, done arithmetically in `drawMomo` so a test can measure it.

## 5. Accessibility

- **One node, one reading.** The panel merges its descendants, the drawing
  carries no description of its own, and the copy line is the description of
  the face — so TalkBack reads "Momo is getting worried." once, not a nameless
  image and then a sentence naming it, and not the same sentence twice. That
  is the widget's lesson from both sides — the one `TodayWidget.kt`'s
  `HabitRows` note records, where review caught a row describing its image
  and its checkbox separately.
- **Animations off means still.** `rememberAnimationsEnabled` reads the
  system *Animator duration scale* once per composition, and everything that
  loops — Momo, the tank life, a celebration — answers to that one reading, so
  a viewer who turned animations off gets a still tank, a still Momo, a cut on
  a mood change and no celebration; the same frame the widget shows.
  `docs/running.md` §4 checks it, because it is a Settings read and no JVM test
  can see it. One reading rather than one per loop is also what keeps the
  screen tests alive: a frame loop is a permanent awaiter, and one gated on
  anything else would hang every composition of Today under Robolectric.
- **Every mood must read at rest — the tank included.** The Momo Motion page's
  still-frame board is the check, and the Habitat & motion page's "still
  frame" board extends it to the habitat: upright weeds and bubbles, or drooped
  weeds in drained water. If a mood cannot be told from the others with nothing
  moving, the motion was carrying meaning the drawing should carry.
- **A celebration is motion only, and says nothing.** Finishing the day (§6)
  hops Momo and bursts bubbles; with animations off it never plays, and the
  resting thriving frame already says thriving, so nothing is lost. It adds no
  announcement: the copy line changing to the thriving line *is* the
  announcement, and a second one would be the double reading this section
  exists to prevent.

## 6. What this does not decide

- ~~**Celebrations.**~~ **Finishing the day is celebrated since 2026-08-26**
  (`Celebration.kt`): when the mood the tank was showing gives way to thriving,
  Momo hops 14 dp, fourteen bubbles rush up from under the tail on staggered
  lanes and the water brightens for a beat, over 1.4 s — the Habitat & motion
  page's "Celebration" board, with its defaults. The edge is detected in
  composition and nowhere else, because the state flow re-emits the same mood
  on every return to the screen and every reminder tick, and a detector there
  would celebrate a finished day again each time the app came back; the
  composition survives those and sees one change. So a cold start on a
  finished day, a rotation and animations off never fire it — by design, and
  `docs/running.md` §4 checks the first two. One part of the board was not
  transcribed: it also popped the sparkles in, but the face crossfade already
  brings them in over the transition, and a knob on `Momo`'s public API for one
  caller was not worth it. **Streak milestones** (PRD §5: 7 / 30 / 100 days;
  4 / 12 / 52 weeks) still have no treatment — this is a different trigger,
  not that one closed. If a milestone is ever a hand-keyed sequence rather than
  a loop, that is where §1's Lottie fallback becomes relevant.
- ~~**The widget and reminder treatments** beyond "the still frame on their own
  ground"~~ — placement and size were decided with the build (§4): in the
  Today widget, above the rows, size-gated. ~~Still open from visual-identity
  §7.4: whether a Momo-only widget or a streak widget is worth its provider.~~
  **Both built 2026-08-29** ([widget.md](widget.md) §6 and §7): the streak
  widget as one row per habit, the Momo widget as her face on the tank colour
  with one word, and the large Today body puts her on a pill beside the woven
  day band. The habitat stays here: none of the three draws the weeds.
- **Momo's real copy**, and the `recentlyBrokenHabits` function the
  regenerating line needs to name a habit (today-view §6).
- **Whether Momo appears on Insights** ([insights.md](insights.md) §7).
- **The collapse into an app-bar chip** on scroll (today-view §1), which the
  250 dp tank makes more pressing, not less.
