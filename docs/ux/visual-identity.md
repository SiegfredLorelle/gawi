# The visual identity: palette, typography and the habit hues

Companion to [the PRD](../prd.md) §3.5 and §8's OQ-4, and to
[the architecture](../architecture.md) §2. The PRD asks for a mascot and an art
style and, since 2026-08-23, for the app's whole visual identity under the same
question. This document is where the first half of it gets decided — the colour
scheme, the typography and the habit hues.

**Status: the colour half is built, as of 2026-08-23.** Written as a sketch
before anything existed, and revised once the scheme landed in `Theme.kt` — so
§§3, 4 and 6 now record what building decided rather than what was proposed, in
the way [habits.md](habits.md), [widget.md](widget.md),
[settings.md](settings.md) and [reminder.md](reminder.md) do. §5 (typography)
and §7's second half (Momo's art, the icon) are still sketch, and
[insights.md](insights.md) is still the document to read the sketchy way.

**Building it changed two of the published values, and that is recorded rather
than quietly fixed.** §3's `tertiary` failed the requirement §4.1 sets for it,
in both themes and for two different reasons. The measurements and the
replacements are in §3; the short version is that a palette approved from
drawings is not a palette that has been measured, which is exactly what §3's
own "honest limit" paragraph warned about.

---

## 1. What this question grew to cover, and why it is first

OQ-4 was "mascot art style". It was widened on 2026-08-23 to the colour scheme,
the typography and the habit hues, which was less a new decision than the
discovery of an old one: **four separate places in this codebase had already
parked themselves on OQ-4 and nothing recorded that they had.**

| Where | What it said | What it says now |
|---|---|---|
| `GawiTheme` KDoc | Stock Material 3, no `ColorScheme`, no typography, "because Momo's palette is PRD OQ-4 and undesigned" | The designed schemes, and why dynamic colour stays off. Type is the one stock thing left, and says what it waits on |
| `HabitPalette` KDoc | "Not a design system" — mid-tone Material hues, same deferral | Designed, and to the rule in §6, with the two rules that failed |
| `GawiSpacing` KDoc | "Not a design system and not trying to be one — Momo's visual language is PRD OQ-4" | Narrowed, not rewritten: §8 records that dimensions were genuinely not in this brief, so it still defers — but only about spacing |
| `TodayWidget`'s glyph comment | Checkbox glyph left unpinned because pinning needs two literals and "this project does not have a palette yet". Ends: "Revisit with OQ-4." | Still unpinned, for the two reasons that outlived the palette: `:widget` cannot see `:core:ui`, and pinning would still not make the glyph assertable. Now points at §7.4 |

All four were rewritten when the scheme landed. Recorded this way rather than
edited away, because "four places had quietly parked on one open question"
is the finding, and a table showing only the current text would not show it.
A stale disclaimer is the same defect as a stale citation: it is believed.

That is why PRD §5's Phase 1 order was inverted. The palette is not a fifth
workstream that could be scheduled against Insights v1 — Insights *draws* things,
and its one undecided visual (the heatmap's two colours,
[insights.md](insights.md) §7) is the piece most tightly bound to a palette that
does not exist. Building the heatmap first means choosing its colours twice.

**The two halves have different lead times and the split is the point.** Palette,
typography and hues are what Insights needs, and they land first. Momo's own art
and the launcher icon are a longer job — a character sheet, four expressions,
possibly a Rive state machine — and they run behind. **The second half must never
gate Insights.** If it starts to, the split has failed and the answer is to ship
the first half anyway.

## 2. What makes this cheap, and the one place it is not

There is not a single hardcoded colour or type size in any screen. All 98
`MaterialTheme.colorScheme` / `MaterialTheme.typography` call sites across 14
files go through roles. So the Compose side of this is a `:core:ui/theme/`
change, not a per-screen rewrite.

**The widget is the exception, and it is structural rather than an oversight.** A
Glance tree compiles to `RemoteViews` and cannot consume a Compose theme
(architecture §2), so `:widget` takes the palette a *second* time as its own
`GlanceTheme(colors = …)`. Two copies of the same hexes, maintained by hand, is
the sanctioned duplication here — there is no mechanism that would let it be one.

**And it is not only colour.** Checked against Glance 1.1.1:
`TextStyle.fontFamily` takes `FontFamily(String)` — a family *name* — with only
`Serif`, `SansSerif`, `Monospace` and `Cursive` as constants and **no
resource-based API**. So through Glance's typed API a bundled font (§5) cannot
reach a widget, and the widget renders in a system face while the app renders in
ours. "Two surfaces, not one" therefore covers typography too.

**But that is Glance's API and not the platform's, which is a correction to an
earlier draft of this file.** `RemoteViews` are inflated by the host against
*your* package's resources, and `res/font` entries are package resources — so a
hand-written layout with `android:fontFamily="@font/…"` should resolve. Glance
1.1.1 exposes `AndroidRemoteViews(remoteViews, modifier)` (verified in the
artifact), which embeds exactly such a layout inside a Glance tree, so the
branded text could drop to a classic layout while the list stays in Glance.

Two routes that do **not** work, so they are not tried twice: `TypefaceSpan` is a
`ParcelableSpan` but its `writeToParcel` writes only the family *name*, so a
custom `Typeface` does not survive the trip to the launcher; and there is no
downloadable-font path for `RemoteViews`. Bitmap text does work and is the
fallback, at the cost of not responding to the system font scale, needing a
`contentDescription`, and `RemoteViews`' hard size limit.

**Unverified on a device**, and this module has form for green builds that are
broken on a launcher (§2's widget bullet, and docs/ux/widget.md §5). Treat the
`AndroidRemoteViews` route as an experiment to run, not a fact to design on.

Two more things follow that are easy to miss:

- **`WidgetTextColourTest` and its light twin must be updated, not merely
  re-run.** Their own KDoc predicted this: `Probe` resolves the background from a
  *second*, default `GlanceTheme { }` because `BackgroundModifier` exposes no
  colour to read back off the emitted tree. The moment `WidgetBody` takes an
  explicit palette, the test measures contrast against the **default** background
  rather than the one drawn. It will not go red. It will keep passing while
  measuring nothing — which is the failure mode this repo keeps finding.
- **The checkbox glyph decision reopens.** It is unpinned today only because
  there was no palette to pin it to, and because every `GlanceTheme` colour is
  resource-backed while `CheckBoxColors` rejects resource-backed providers
  (`IllegalArgumentException` at runtime, not at compile time). With hexes
  decided, `ColorProvider(Color)` literals become available. Note what does *not*
  change: pinning still would not make the glyph assertable, because
  `CheckBoxColors` exposes only an `internal` accessor returning an empty public
  interface. `docs/running.md` §4 keeps its by-hand check either way.

## 3. The colour scheme — the candidates, and what shipped

Three candidates, chosen to span the actual decision rather than three shades of
one idea. The question they disagree about is **where Momo's coral goes**.

### Candidate A — teal habitat, coral reserved

| Role | Light | Dark |
|---|---|---|
| `primary` | `#1F6F78` | `#7FD4DC` |
| `tertiary` | `#C9A227` †  | `#E8C55E` † |
| `surface` | `#F4FBFA` | `#0E1A1C` |
| `onSurface` | `#101C1E` | `#DCEEF0` |
| Momo only | `#E0708F` | `#FFB1C4` |

† Neither `tertiary` survived measurement. Kept here as the record of what was
chosen from the drawings; "What shipped" below has the replacements and why.

The UI is the tank; Momo is the only warm thing in it. The mascot cannot be lost
against chrome in its own colours, and teal is the natural ramp for the heatmap.
Costs: the app chrome is cooler and quieter than the character.

### Candidate B — axolotl coral and water

| Role | Light | Dark |
|---|---|---|
| `primary` | `#E0708F` | `#FFB1C4` |
| `tertiary` | `#3E7F84` | `#8FC4C8` |
| `surface` | `#FFF8F7` | `#1A1113` |
| `onSurface` | `#201417` | `#F3DDE2` |

The strongest tie to the mascot, and the least generic on first launch. Two
costs, both real: Momo stops standing out once everything around it is already
coral, and a pink primary has to work on the long-horizon retrospective screens
(PRD §5 Phase 1.5), which are the most data-dense surfaces planned.

### Candidate C — woven earth

| Role | Light | Dark |
|---|---|---|
| `primary` | `#A9552F` | `#E39B72` |
| `tertiary` | `#2F4858` | `#9BB8C8` |
| `surface` | `#FBF6EE` | `#1C1815` |
| `onSurface` | `#1F1A16` | `#EFE6DA` |

Drawn from PRD §3.5's **weaving** metaphor rather than from the mascot, which is
worth something specific: the metaphor is explicitly "kept regardless of name"
and so survives whatever OQ-6 decides, where a mascot-derived palette is hostage
to the character. Calmest ground for data. Warmest and least aquatic of the
three, and clay next to Momo's coral needs care.

### What the candidates are being judged as

Not as swatches. Each is rendered on mock Today and habit-detail screens, light
and dark, with the heatmap ramp and all eight hues, because a palette approved as
a row of rectangles is a palette approved without its contrast, its hierarchy or
its data density.

**Honest limit of that preview:** it is HTML, so Material 3 role mapping is by
hand and font rasterisation differs from Android's. The preview decides the
palette; the device decides the result, and `make run` in both system themes is
still the check that matters. The widget cannot be previewed faithfully at all.

### What shipped, and the two corrections to candidate A

Candidate A won (§7.2). Two of its four roles went in unchanged; `tertiary` did
not survive being measured, in either theme, for two unrelated reasons.

**Light `#C9A227` → `#665012`.** `tertiary` is drawn as plain text — a week
streak in `StreakBadge` — so WCAG's 4.5:1 applies to it, and `#C9A227` on
`#F4FBFA` is **2.31:1**. It also fails §4.1's own direction: §4.1 requires
`tertiary` to step *darker* than `primary` in light mode, and `#C9A227` is
lighter. `#665012` is 7.36:1 with a 1.32 step. The cost, stated because it is
real: gold at that lightness reads as bronze, and the light theme's accent is
duller than the drawings promised. There is no way around it — a gold light
enough to look like gold cannot clear 4.5:1 on a near-white surface, which is
the same gamut wall §6.1 hit from the other side.

**Dark `#E8C55E` → `#C9A227`.** Against dark `primary` `#7FD4DC` the published
value gives a luminance ratio of **1.02** — identical lightness, distinguished by
hue alone. That is precisely the failure §4.1 says it measured and rejected, and
it would take the day-versus-week distinction out in greyscale and under
deuteranopia. `#C9A227` restores the step at 1.42 and stays at 7.34:1 on the dark
surface. Light's discarded value turns out to be dark's correct one.

**The other 44 roles are derived, and the code is their contract.** §3 fixes four
roles and reserves one colour; a Material 3 `ColorScheme` has 48, and the screens
already draw `outline`, `outlineVariant`, `surfaceVariant`, `onSurfaceVariant`,
`secondaryContainer`, `onSecondaryContainer`, `error` and `background`. Leaving
Material's baseline underneath was not an option — the baseline is a purple
family, so those roles would have shown lavender through a teal app, and §4.1
makes `outline` semantic rather than decorative. They are generated in OKLCH from
the three hue families the four anchors imply — teal at hue 206, gold at 89, a
teal-tinted neutral at 209 — plus a warm red at 27 for `error`. The values live in
`core/ui/src/main/kotlin/com/gawi/core/ui/theme/Color.kt`; reproducing 48 hexes
here would guarantee two copies and one of them stale.

**What holds them honest is a test, not this table.** `GawiColorSchemeTest`
asserts every foreground/background pair the app draws against its floor in both
themes, the `primary`/`tertiary` lightness step, and that `outline` and
`onSurfaceVariant` stay recessive. Tightest surviving margin is `primary` on
`secondaryContainer` at 4.58 — the completed tick on today's cell in the retro
strip. It was mutation-checked by putting §3's two published values back, and it
fails on both with the ratios above.

**A third value moved, and this one the test missed until a device found it.**
Dark `secondaryContainer` was `#344D50`, a straight tonal mirror of the light
value. But `secondaryContainer` is a **ground**, not only an accent: `RetroStrip`
fills today's cell with it and draws the weekday letter on top in
`onSurfaceVariant`, which measured **4.24:1** — a real text failure, on the one
cell every user looks at every day. It is now `#273F42`, which puts that pair at
5.25 and every other role drawn there above 4.5. The cost is a subtler fill:
1.59:1 against `surface` rather than 1.96:1, still more visible than the light
scheme's own 1.21:1.

The lesson is the one worth keeping, because the test was *written* to prevent
exactly this and did not: it enumerated the `surface` family as grounds and
treated the containers purely as accents, checking each only against its own
`onX`. A container that something fills a shape with is a ground, and every
recessive role that lands on it is a pair. `secondaryContainer` is now
enumerated as one. It also fixed a role misuse next door — the strip drew its
not-done marker in `outline`, which is Material's *border* role, at 2.70:1 on
that fill; recessive **content** is `onSurfaceVariant`, and using it leaves
`outline` to the shut day alone, which sharpens the hierarchy §5 asks for.

### The canvas and this table disagreed, and the table was wrong

Recorded 2026-08-23, after the code landed and the two were compared directly —
which had not been done, because §3 was written *from* the canvas and nobody read
it back the other way.

The canvas does not hold four role values per candidate. It holds a **twelve-role
scheme in both themes**, in a `PALETTES` object, and its candidate A differs from
the table above. Most of the difference is nothing: canvas `primary` `#027273`
against the shipped `#1F6F78` is a contrast ratio of **1.01**, and the two
surfaces likewise — different hex, indistinguishable to an eye. Two differences
are not nothing:

- **The canvas's light `tertiary` is `#553F00`**, a dark gold at 9.61:1. The table
  above recorded `#C9A227`, the bright one, which measured 2.31:1 and had to be
  replaced during the build. **The canvas was right and the transcription was
  wrong**, and the replacement arrived at `#665012` — the same decision, made
  twice, the second time the expensive way.
- **The canvas's own scheme has two flaws the shipped one does not.** Its light
  `outline` is 3.77:1, under the text floor, and its dark `primary`-to-`tertiary`
  step is 1.27, the bottom of §4.1's range and the same near-collapse the table
  had. Shipped is 5.18 and 1.42.

**So the shipped values stay, and the canvas is not the contract for them — the
code is** (`core/ui/theme/Color.kt`, asserted by `GawiColorSchemeTest`). Adopting
the canvas wholesale would trade two measured improvements for a 1.01 difference
nobody can see. What the canvas remains authoritative on is everything a number
cannot hold: Momo, the tank, the icon comparison, the widget surfaces, and the
typeface.

The lesson generalises past colour. A drawing and a document drift the moment one
is edited without the other, and the drift is invisible while nothing compares
them. §3 also declared 44 roles undecided that the canvas had already chosen —
harmless here, because they were re-derived to the same place or better, and
wasteful all the same.

One thing it deliberately does not assert: `surfaceVariant` and the container
roles sit at 1.2-2.0:1 against `surface`, and that is correct. They are fills —
the icon-picker swatch, `HabitIcon`'s fallback circle — and what they owe
contrast to is their own contents, not the page. Holding a fill to 3:1 would pin
the wrong property and force every quiet surface to look like a button.

## 4. Four constraints the palette has to satisfy

These come from what the screens already do, and they hold for whichever
candidate wins. Every number below was computed and checked, not estimated.

### 4.1 `primary` and `tertiary` are semantic, not decorative

`StreakBadge` draws a **day** streak in `primary` and a **week** streak in
`tertiary`. Those are the two schedule kinds (PRD §4), and colour is part of how
the badge distinguishes them — the text differs only by a trailing `w` (`5` vs
`5w`). `primary` also marks a completed cell in habit detail's `RetroStrip`,
against `onSurfaceVariant` for an open day not yet done and `outline` for a shut
one — three states, not two, since the correction in §3.

So the requirement is not just that each role is legible on `surface`. **`primary`
and `tertiary` must be clearly distinguishable from each other**, in both themes,
including for the common colour-vision deficiencies.

Hue distance alone is not enough, and this was measured rather than assumed. A
first pass put both roles at the same tonal position, which is the obvious thing
to do: every candidate then came out with a `primary`-to-`tertiary` *luminance*
ratio of about 1.05 — identical lightness, distinguished by hue only, so the
day-versus-week distinction would disappear entirely in greyscale or under
deuteranopia. **`tertiary` therefore takes a deliberate lightness step away from
`primary`**, which should bring the ratio to 1.27-1.74 while keeping `tertiary`
above 4.5:1 on `surface`. The badge text also differs by a trailing `w`, so
colour was never the only channel — but making lightness carry it too costs
nothing.

Two things this originally got wrong, corrected once the scheme was built and
measured rather than drawn. It said the step goes "darker in light mode, lighter
in dark", and in the winning scheme it is darker in *both*: dark `primary` sits
at 10.4:1 on its surface already, so there is no room above it and the step has
to go down. And it read as though the published candidates satisfied the
requirement — candidate A's `tertiary` satisfied neither half, at 2.31:1 in light
and a 1.02 step in dark. §3's "What shipped" carries the replacements. The
requirement stated here was right and worth stating; what was missing was
anything that checked it, which is now `GawiColorSchemeTest`.

`outline` and `onSurfaceVariant` carry the broken-streak and shut-day states and
must read as *recessive* against `onSurface` while still clearing the contrast
floor. Dimmed is a meaning here, not a leftover.

### 4.2 `CONTRAST_PIVOT` was wrong, and it blocked the hue retune

**Fixed on 2026-08-23, before the hues, in `fix(ui): pick the better habit glyph
colour`.** Kept in full rather than deleted, because the defect is the reason the
retune was safe to do at all and because how it survived is worth remembering.

Found while generating the hues. `HabitColor.kt` picked a glyph colour with:

```kotlin
if (tint.compositeOver(background).luminance() > CONTRAST_PIVOT) Color.Black else Color.White
private const val CONTRAST_PIVOT = 0.5f
```

0.5 is the midpoint of the luminance range. It is **not** the crossover between
a black and a white glyph. Black overtakes white where
`(L + 0.05)² = 0.0525`, i.e. at **L = 0.1791**, and either choice gives 4.58:1
exactly there. So for any tint whose luminance falls between 0.1791 and 0.5, the
current code picks the *worse* of its two options.

That band is where most mid-tone hues live, and the consequence is measurable on
what ships today:

| Hue | Hex | Luminance | Picks | Chosen | Other |
|---|---|---|---|---|---|
| Red | `#EF5350` | 0.251 | white | **3.49** | 6.02 |
| Pink | `#EC407A` | 0.229 | white | **3.76** | 5.58 |
| Purple | `#7E57C2` | 0.151 | white | 5.21 | 4.03 |
| Blue | `#42A5F5` | 0.347 | white | **2.65** | 7.93 |
| Teal | `#26A69A` | 0.300 | white | **3.00** | 7.00 |
| Green | `#66BB6A` | 0.394 | white | **2.36** | 8.88 |
| Yellow | `#FFD54F` | 0.694 | black | 14.88 | 1.41 |
| Orange | `#FFA726` | 0.490 | white | **1.94** | 10.81 |

**Six of the eight fall below the 4.5:1 floor, and in every one of those six the
other choice would have passed.** Setting the pivot to the true crossover fixes
all six and cannot regress any input, because the crossover is by definition the
point where the better choice changes.

**Where this is actually visible**, stated honestly rather than overclaimed:

- **The editor's selection tick.** `HabitEditorPickers` draws `✓` (U+2713, a
  monochrome dingbat) tinted by `glyphColorOn`, so on orange it is currently a
  1.94:1 checkmark. `selectableBorder` double-encodes the selection with a ring,
  which is why this was survivable, not why it was fine.
- **Any habit icon that is not a colour emoji** — a letter, a symbol from a
  future import. `HabitIcon`'s KDoc anticipates exactly this: text "is right if
  that turns out to be an emoji and is a visible placeholder if it does not".
- **Probably not the twelve emoji `HabitPalette` offers.** Android renders those
  through a colour emoji font, which ignores the text colour. Worth confirming on
  device rather than asserting either way — and it does not change the fix.

**It landed before the hues, not with them.** A one-constant change with its own
test, independent of whichever scheme won — and §6's retuned set *required* it:
at the chosen lightness all eight new hues sit in the band the old pivot got
wrong and would have drawn at about 4.1:1.

**How it survived a phase of green builds, which is the part worth keeping.** All
seven tests over this function passed either way, because none of them knew what
a ratio was — they asserted which colour came back for a given input, which is
the function restating itself. The two added with the fix assert the property
instead: one sweeps 101 greys checking the better of the two glyphs is always the
one chosen, the other checks every offered hue clears the floor *as drawn*. Both
were mutation-checked against `0.5f`. The useful consequence is that at the true
crossover every tint clears 4.49:1 whichever glyph it takes, so contrast stopped
being a property of the palette and became a property of the function — which is
what let §6 retune all eight hues without re-deriving their glyphs.

### 4.3 The hue labels are content descriptions, so they have to stay true

`HabitEditorPickers.kt` holds `COLOR_LABELS`, a **positional** list of string
resources — Red, Pink, Purple, Blue, Teal, Green, Yellow, Orange. TalkBack reads
those instead of the hex, so a swatch announced as "Purple" that renders blue is
an accessibility defect and not a nitpick.

**Nothing in the test suite will catch that.** `HabitsUiMapperTest` asserts only
that the two lists are the same *length* and that the labels are distinct — which
is what its KDoc claims and all it claims, enough to stop a swatch announcing
itself as "number sign E F 5 3 5 0" and no help whatever against a swatch
announcing the wrong colour. A name is not a checkable property of a hex, so this
one is carried by review and by the TalkBack pass in `docs/running.md` §4, not by
CI.

**This is what narrows the hue rule in §6**: the eight *names* are effectively
fixed unless the strings move with the values — and §6.2 is where one of them
does.

### 4.4 The heatmap needs two colours and only two

Completions are idempotent per logical date (architecture §4), so there is
nothing to count and no intensity to encode —
[insights.md](insights.md) §7 settled this as a two-state grid. Whatever the
palette gives it must come from `MaterialTheme` roles rather than literals, so the
grid inherits the palette the same way every other screen does. Practically: a
"done" role and a "not done" role, both legible against `surface`, and the "not
done" state must not read as disabled-because-broken.

## 5. Typography

**Decision: bundle one variable font in `:core:ui` and define a real
`Typography`.** `:core:ui` has no `res/` directory at all today, so
`core/ui/src/main/res/font/` is new, and the OFL text ships beside the file.

The rejected alternative is worth writing down because it looks cheaper and is
not. Downloadable fonts through the Google Fonts provider keep the APK smaller,
but they need Play Services **and** a network on first use, which contradicts PRD
§2 goal 4 — *"fully functional with no network, no account"*. And because a
downloadable font needs a designed fallback path for the offline case anyway, it
is strictly more work than bundling rather than less. Cost of bundling, stated
plainly: roughly 200-400KB of APK, and one asset to license-check.

Keeping the system font and customising only the scale was the third option. It
is honest and free, but it leaves the app's type device-dependent, and "generic
Android" is the exact complaint this whole exercise exists to answer.

The scale names only the roles the app actually draws, in `GawiSpacing`'s idiom —
named values with a KDoc saying why, rather than a full Material scale most of
which nothing references:

| Role | Uses | Where |
|---|---|---|
| `labelLarge` | 8 | Streak badges, retro-strip glyphs |
| `titleMedium` | 7 | Mascot mood line, section titles |
| `titleLarge` | 6 | Screen titles |
| `titleSmall` | 6 | Sub-section headers ("Recent days") |
| `bodyLarge` | 6 | Habit names, retro-strip day numbers |
| `bodySmall` | 6 | Week progress, remaining-today count |
| `labelSmall` | 4 | Day letters, broken-streak captions |
| `bodyMedium` | 4 | Notice bodies, empty-state copy |
| `headlineSmall` | 2 | Habit detail's large streak number |
| `displaySmall` | 2 | The largest numerals |

Ten roles, which is the whole list and not a selection — every Material role this
app references, counted rather than guessed. Anything not in that table does not
need a value, and giving it one would be inventing type nothing draws.

The typeface itself is chosen out of the same preview as the palette, so the pair
is judged together. Constraints on the choice:

- **OFL-licensed and available on Google Fonts**, so the face previewed in the
  browser is the same file that gets bundled as a `.ttf`.
- **Possibly close to the system sans in character** — conditional on §2's
  experiment. If a widget cannot get the bundled font it renders in the platform
  face, and the app and widget sit next to each other on a home screen: a quiet
  humanist face makes that divergence hard to notice, a strongly geometric one
  (Outfit, which the canvas uses) makes it obvious. If `AndroidRemoteViews` does
  carry `@font/…` through, the constraint disappears and the face can be chosen
  on identity alone. **So the typeface waits on that experiment rather than on
  taste** — decided 2026-08-23.

## 6. The habit hues

**Decision: keep eight slots; retune all eight to one tonal rule.**

Eight rather than fewer, because the count is load-bearing in two places —
`COLOR_LABELS` and the test that pins the two lists parallel. (An earlier
revision counted a third, the debug seeder's expectations. There is no debug
seeder in this repo and there is no `debug` source set; the claim came from
`HabitPalette`'s own KDoc, which said the same and has been corrected. A
citation to code that does not exist is the defect `scripts/check-citations.sh`
was written for, in the one direction it cannot check.) Retuned rather than left alone, because eight saturated stock
Material hues sitting next to a designed scheme is the single most visible
remaining tell.

### 6.1 The rule, and the two rules that do not work

**The rule: the same eight hue families, all at a fixed OKLCH lightness of 0.62,
each taking the highest chroma sRGB allows it at that lightness.** Uniform
*perceived* lightness, so the eight read as one set; per-hue chroma, so each
family stays recognisable.

Two more obvious rules were tried first and both fail. Recording them because
each looks correct written down:

- **Fixed OKLCH lightness *and* chroma.** Chroma then has to be capped by the
  worst family in gamut, which lands at about 0.11 — and at that chroma "Blue"
  is a dusty slate and "Yellow" is `#A2933B`, a khaki. The labels stop being
  true (§4.3) and the set looks washed out.
- **Fixed WCAG luminance.** Tempting, because it makes the glyph choice uniform
  by construction. But yellow is intrinsically light and blue intrinsically
  dark, so forcing them to equal luminance drags yellow down to `#846E00` — an
  olive-bronze. The same failure from the other direction.

The lesson is worth stating plainly: **you cannot have eight recognisable hue
families at one luminance.** So the uniformity to insist on is *contrast*, not
lightness — and §4.2's pivot fix is what delivers it, because at the true
crossover every tint clears 4.5:1 whichever glyph it takes. That is why the pivot
is a prerequisite here rather than a tidy-up.

Generated values, checked against every surface in §3:

| | Red | Pink | Purple | Blue | Teal | Green | Gold | Orange |
|---|---|---|---|---|---|---|---|---|
| hex | `#F22935` | `#E92786` | `#A94FF6` | `#427FF6` | `#249899` | `#24A047` | `#9C851F` | `#C26E1F` |

Luminance spread 0.205-0.260, so all eight take a **black** glyph — uniform, at
5.10:1 in the worst case. Badge against surface is 3.23-3.93:1 in light mode and
4.31-5.24:1 in dark, clearing the 3:1 non-text floor throughout.

### 6.2 One label has to move: Yellow becomes Gold

**Done.** `habits_color_gold` is the string and "Gold" is what TalkBack reads.

At a uniform lightness the yellow family lands at `#9C851F`, which is a gold or
olive and is not yellow. Per §4.3 the string is a content description, so the
honest fix is to move the label with the value: `habits_color_yellow` becomes
`habits_color_gold`, "Gold". A `values/` string with no translations today, so the
cost is one line and its positional slot is unchanged.

The alternative — exempting yellow from the rule so it can stay bright — was
rejected because a bright yellow fails the 3:1 badge floor against every light
surface in §3, which is a worse defect than a renamed swatch.

Values stay **uppercase and six digits**, for the reason `HabitPalette`'s KDoc
gives: `ColorPicker` matches on the exact string, so a lowercase palette hex
would offer the same colour twice — once as a hue and once as §6.3's "colour you
already have".

**That rule binds the palette and nothing else.** `parseHabitColor` accepts six
digits or eight, upper case or lower, deliberately, and an import replays
whatever an export held; asking it to normalise would rewrite a stored colour on
read, which is what §6.3 exists so we never have to do. (The convention used to
be justified by what a debug seeder wrote; there is no seeder — see §6.1.)

### 6.3 The orphaned hexes, and what to do about them

A habit's colour is raw hex in an append-only event log. Retuning
`HabitPalette.Colors` **migrates nothing** — and it should not, because rewriting
history to change a colour is exactly what an event log is for not doing. So a
habit created before the restyle keeps its old hex, and reopening its editor shows
a form with nothing selected: the precise failure the uppercase-six-digit
convention exists to avoid, arriving by a different door.

**Decision, and now the behaviour: when `form.color` is not in
`HabitPalette.Colors`, render it as a leading "current" swatch.** The machinery is already there —
`parseHabitColor` survives arbitrary hex by design ("these are what the editor
offers, not a guarantee about what is in the log") and `glyphColorOn` already
picks its glyph. What the picker needs is one extra entry, not a new mechanism.

One wrinkle: `COLOR_LABELS` is positional, so the extra swatch cannot index into
it and needs its own label string. Something naming it as the habit's current
colour rather than naming a hue, since the hue is unknown by definition. That is
`habits_color_current`, "Current colour".

**As built, the wrinkle turned out to be the whole risk.** `ColorPicker` looked
its labels up by list index, so prepending an entry with a synthetic index would
have shifted every name one place along the palette — eight swatches each
announcing the wrong colour, which §4.3 calls an accessibility defect and which
nothing in the suite can detect. The fix was to stop indexing: each swatch now
carries its own label. `HabitsUiMapperTest`'s length assertion cannot see this
swatch at all, so `HabitEditorScreenTest` gained the two cases that can — one
that an orphaned hex is offered and starts selected, one that nothing extra
appears when the colour is still on the palette.

The alternative — offering nothing and letting the form open unselected — was
rejected because saving from that state would silently change the habit's colour,
which is a data change the user did not ask for.

## 7. Round three: what the drawings settled

**Reading the canvas: it holds rejected work on purpose.** Anything on it is a
drawing, not a decision, and two of its Today artboards are options
[today-view.md](today-view.md) §2 explicitly **rejected** — "A — ambient tank",
the whole screen as habitat with cards floating over water, and "C — bottom
dock". They stay on the canvas so the tradeoff that was accepted stays legible,
which is worth having and is also a trap: the built app does not look like the
tank artboard, and it is not meant to. When the app and the canvas disagree,
check which artboard before treating it as a defect — and check this document,
because a decision only counts once it is written here.

Recorded 2026-08-23, after the redesign canvas gained dark mode, an icon
comparison, four widget surfaces and the launcher mark.

### 7.1 Decided

- **The launcher icon is Momo, drawn as a mark rather than scaled down.** Two
  fronds a side instead of three, no blush, eyes and mouth oversized past their
  in-app proportions, everything inside the adaptive icon's 72dp safe zone. The
  full character measured as mush at 40px; the mark holds at 24. **The monochrome
  layer is the woven thread, not the face** — an Android 13+ themed icon is a
  single flat silhouette tinted by the system, and a face that loses its colours
  becomes an inkblot. Two marks, one metaphor each, saying the same thing in
  different registers. Held loosely: the mark derives from the character, so a
  change of Momo's style redraws it rather than reopening it.
- **A streak widget must date its number.** Whatever else it shows, it carries an
  "as of" line. This follows directly from the reasoning that settled OQ-5: a
  streak reaches zero with *no new event*, so it is the one value whose staleness
  is not bounded by the user doing nothing, on the one surface with no live
  query. `RolloverWorker` improves the odds and bounds nothing (widget.md §6).
  Dating it converts a possible lie into a stale-but-true reading, which is the
  difference between this and the "demotivating lie" widget.md §2 refused.

### 7.2 The scheme is decided

**Candidate A, teal habitat — settled 2026-08-23**, after dark mode was drawn.
Dark is where the habitat earns itself: the tank drops to deep teal and coral
Momo has nowhere to hide, which is also where candidate B gets worse rather than
better. The role sets in §3 are the contract; `primary` and `tertiary` keep the
lightness step §4.1 measured, so day-versus-week streaks survive a greyscale
reading.

This is what unblocked `Theme.kt`, the retuned hues, the widget's duplicate hexes
and the Insights heatmap — the whole reason PRD §5's Phase 1 order was inverted.
`Theme.kt` and the hues landed the same day; the widget's own palette and the
heatmap are the two still to come, and both now have values to draw from.

**Still a leaning: Momo's style is flat.** All three treatments stay on the
canvas so it can move; the launcher mark derives from the character, so a change
there redraws the mark rather than reopening §7.1.

**Parked, with an experiment rather than a question: the typeface.** §5 has the
condition and §2 has the experiment.

### 7.3 The habit icon vocabulary is wire-neutral, and that is the finding

`HabitMetadata.icon` is **already an opaque `String`** in the domain, in
`HabitCreated`/`HabitUpdated`, and in `WireV1`. So changing what the editor
*offers* costs nothing in the log: store a stable *name*, render a bundled vector
for it, and let an unrecognised string fall through to the text branch `HabitIcon`
already has — which is exactly how today's emoji keep rendering. **No schema bump,
no upcast-on-read, no migration.** This is the opposite of OQ-1's multi-tag change,
which is a genuine event-payload bump.

`HabitPalette`'s KDoc objects that "drawable ids are not portable through an event
log that has to survive an export and an import". That objection is correct and
does **not** reach a name. Names also decouple the vocabulary from the artwork, so
a licensed set now can become custom art later without the log noticing.

Three candidates are drawn on the canvas — the current emoji, Lucide (ISC,
stroke-based; verify the licence at bundle time), and a custom rounded-fill set in
Momo's language — at 42px and at 24px in both modes, because the small size is
where a stroke set and a fill set diverge and it is the size that should decide.

**One asymmetry worth stating**: Android renders colour emoji through its own font
and **ignores the text colour**, so for the twelve emoji the badge hue is the only
colour decision available and §4.2's whole pivot fix is inert. For a vector set the
glyph colour is chosen by the corrected pivot, which is what keeps it legible on
all eight hues rather than on some of them.

**On generating icons on-device**, asked and answered: it fails on determinism
before it fails on size. PRD §5 Phase 2 is LAN sync — devices union their event
logs and must agree on what the data *is*. Artwork generated per-device from an
opaque string means one habit rendering differently on each phone, and a
regenerated icon differing from the one the user picked. Using AI to *draw* a fixed
set at design time is a different activity, and is already PRD §5's plan for Momo.

### 7.4 What the widget set costs

Four surfaces are drawn: Today small (unchanged in content — no streak, so OQ-5
stands), Today large with Momo and the woven band, a streak widget, and a Momo
widget. Before any of it is built, the price:

- **Each new widget is its own provider** — a `GlanceAppWidget`, a
  `GlanceAppWidgetReceiver`, an `appwidget-provider` xml and a manifest entry,
  three times over. The streak and Momo widgets also each need a read
  `observeToday()` does not currently serve.
- **Two sizes of one widget** means `SizeMode.Single` → `SizeMode.Responsive`, and
  the attributes that make a widget resize properly — `targetCellWidth/Height`,
  `previewLayout`, `description` — need a **`res/xml-v31` variant**. They are
  absent today on purpose: minSdk is 29 and `warningsAsErrors` is on, so lint's
  `UnusedAttribute` is a failed build.
- **Momo on a widget does not move.** `RemoteViews` cannot run the animation, so
  it is a static vector drawable per mood: four assets.
- **The checkbox glyph reopens**, as `TodayWidget`'s comment predicted. It is
  unpinned only because there was no palette and because `CheckBoxColors` rejects
  the resource-backed providers every `GlanceTheme` colour is. With hexes decided,
  `ColorProvider(Color)` literals become available — and pinning still will not
  make the glyph assertable, so `docs/running.md` §4 keeps its by-hand check.

## 8. What this does not decide

- **Momo's art style, expressions, and whether it is static or animated.** OQ-4's
  second half, still open. PRD §5 has the tooling plan (Rive recommended, Lottie
  and static-first as fallbacks).
- **The launcher icon — its drawing and its wiring, not its design.** §7.1 does
  decide the icon: Momo as a mark, the composition, and the woven thread as the
  monochrome layer. What is open is everything downstream of that, and for the
  reason §7.1 gives itself — the mark derives from the character, so it cannot be
  drawn until Momo's style is. Nor is any of it implemented: there is no
  `mipmap/ic_launcher` at all today and the manifest points `android:icon` at
  `@android:drawable/sym_def_app_icon`, which is Android's generic default and
  *not* public API. Replacing it is the fix; depending on it further is not.
  Listed here rather than under §7.1 because a decision nothing has drawn is
  still a thing this document has not delivered.
- **Spacing.** `GawiSpacing` parks itself on OQ-4 along with the rest, but
  dimensions were genuinely not part of this brief. Its KDoc gets narrowed rather
  than rewritten.
- **Whether Momo appears on the Insights screens.** [insights.md](insights.md) §7
  raises it and defers it to OQ-4; it belongs to the second half, with the art.
- **Dynamic colour.** Off, and staying off — a designed identity is the point of
  this document, and Material You would hand it back to the wallpaper.
