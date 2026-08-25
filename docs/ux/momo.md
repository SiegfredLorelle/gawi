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
The widget, the reminder and the launcher icon are designed here and **not yet
built** (§4).

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
98 % of the period, and open otherwise. A mood change crossfades over 0.55 s,
both faces drawing during the fade, so a gill changing length reads as a change
and not a cut.

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
| Today | the tank: a 250 dp panel, water in `primaryContainer → primaryFixedDim`, drained to `surfaceContainerHighest → surfaceContainerHigh` while regenerating | animated | **built** — `MascotPanel.kt` |
| Widget | Glance cannot animate (visual-identity §7.4) | the resting frame, `MomoFrame.rest` | designed, not built |
| Reminder | the notification's small icon is alpha-only, so a silhouette | still | designed, not built |
| Launcher | visual-identity §7.1's mark, derived from this character | still | can be drawn now; not built |

**The tank is Today's alone.** It is drawn in `:feature:today`, not `:core:ui`,
because only Today is a habitat: the widget and the reminder get the character
on their own grounds. The character itself is in `:core:ui` for the reason
architecture §2 gives — the widget's still frame will draw it too — and the
widget → `core:ui` edge that carries one font today will carry Momo's geometry
when that lands.

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
  is the widget's lesson ([widget.md](widget.md) §5) from both sides.
- **Animations off means still.** `Momo` reads the system *Animator duration
  scale* once per composition and draws the resting frame when it is off; the
  same frame the widget will show. `docs/running.md` §4 checks it, because it
  is a Settings read and no JVM test can see it.
- **Every mood must read at rest.** The Momo Motion page's still-frame board
  is the check: if a mood cannot be told from the others with nothing moving,
  the motion was carrying meaning the drawing should carry.

## 6. What this does not decide

- **Milestone celebrations** (PRD §5: 7 / 30 / 100 days; 4 / 12 / 52 weeks).
  No treatment. If one is ever a hand-keyed sequence rather than a loop, that
  is where §1's Lottie fallback becomes relevant.
- **The widget and reminder treatments** beyond "the still frame on their own
  ground" — placement, size, whether the large widget is worth its provider
  (visual-identity §7.4).
- **Momo's real copy**, and the `recentlyBrokenHabits` function the
  regenerating line needs to name a habit (today-view §6).
- **Whether Momo appears on Insights** ([insights.md](insights.md) §7).
- **The collapse into an app-bar chip** on scroll (today-view §1), which the
  250 dp tank makes more pressing, not less.
