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
[settings.md](settings.md) and [reminder.md](reminder.md) do. §7's second half
(Momo's art, the icon) was sketch until 2026-08-25; [momo.md](momo.md) now
records the character as built, and only the icon's drawing is still owed.
[insights.md](insights.md) is still the document to read the sketchy way.

**§5 (typography) is BUILT as of 2026-08-24.** The experiment it waited on ran
(§2 has the result: a widget cannot be handed a bundled font), the trade that
left was taken in favour of identity, and the app now draws in Outfit —
`core/ui/theme/Type.kt`, `GawiTypography`, pinned by `GawiTypographyTest`.
Every text style in the app is Outfit; only glyphs outside its `cmap` — emoji,
other scripts — fall back to the platform face (§5). The widget followed on 2026-08-25,
by a different mechanism: its text is rasterised in Outfit and shipped as
bitmaps, because a font resource still cannot reach a `RemoteViews` tree (§2).

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
| `TodayWidget`'s glyph comment | Checkbox glyph left unpinned because pinning needs two literals and "this project does not have a palette yet". Ends: "Revisit with OQ-4." | Still unpinned, for the two reasons that outlived the palette: `:widget` sees `:core:ui` for one font resource only (2026-08-25), so pinning still means copying hexes, and it would still not make the glyph assertable. Now points at §7.4 |

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
fallback. Its price, as first written here, was "not responding to the system
font scale, needing a `contentDescription`, and `RemoteViews`' hard size limit";
building it on 2026-08-25 corrected all three, and the corrections are below the
measurement.

**Measured on 2026-08-24, and the route is dead.** The paragraph above was
right about the API and wrong about the outcome, which is why it is kept rather
than deleted: `AndroidRemoteViews` composes, the layout inflates, the launcher
draws it — and the bundled font is **silently ignored**.

A probe drew the same string at the same size four times inside one
`AndroidRemoteViews` layout on the Today widget, varying only `fontFamily`.
Rendered widths, read off the launcher's own accessibility tree on an API 37
emulator (Android 17, 720x1280 at 320dpi, host `com.google.android.apps.nexuslauncher`):

| `android:fontFamily` | Rendered | Verdict |
|---|---|---|
| *absent* — the control | 313 x 54 | the system sans |
| `@font/outfit` — a bundled `.ttf` | 313 x 54 | **identical to the control** |
| `@font/outfit_family` — an XML `<font-family>` wrapping it | 313 x 54 | **identical to the control** |
| `serif` — a built-in family *name* | 329 x 52 | honoured |

So the boundary is not "fonts do not reach a widget". It is narrower and more
useful: **`RemoteViews` inflation honours the `fontFamily` attribute, and
resolves only built-in family names from it.** A font resource of ours is
dropped without a warning, in both spellings a font resource can take. And the
four names it *will* resolve are exactly the four Glance's typed API already
offers, so `AndroidRemoteViews` buys nothing at all for typography. It remains
the correction to that earlier draft as an API fact; as a route it is a third
dead one, recorded beside the other two so it is not tried a fourth time.

**Built on 2026-08-25: the widget draws in Outfit after all, as bitmaps.** The
measurement above is untouched and still true — no font *resource* reaches a
`RemoteViews` tree. What changed is the conclusion §5 drew from it, that bitmap
text was "not worth it for a checkbox list"; that was reversed, and
`widget/…/BitmapText.kt` is the result. Each row's name and the two copy
strings are laid out with `StaticLayout` (so bidi and shaping happen before the
pixels exist), in Outfit at `wght` 400 through `Paint.setFontVariationSettings`
— `Typeface.create(base, 400, false)` picks from a font *list* and never
instances a variable axis, so it would have shipped Thin, the same trap Type.kt
records — drawn white and handed to a Glance `Image` with
`ColorFilter.tint(GlanceTheme.colors.onSurface)`. The three costs, corrected:

- **Font scale is honoured, at the next render.** The size is resolved in sp
  against the configuration in force when the bitmap is drawn, so a scale change
  shows at the next update — a write, a rollover, or the 30-minute period.
  Glance recomposes on a locale change and not on a configuration change, so it
  cannot be immediate; that is the latency the widget already accepts for a day
  rollover (docs/ux/widget.md §4), not a new one.
- **`contentDescription` is nullable, and the name is passed.** An `Image` with
  none is decorative; each name is its own description, so TalkBack reads the
  row now that the checkbox beside it carries no text.
- **The size limit is arithmetic, not a wall.** `RemoteViews` bitmaps are capped
  at 1.5 × the screen's pixels × 4 bytes. A row at 16sp on a 440dpi phone is
  about 720 × 56 px in ARGB_8888, ~160 KB, against a budget near 14 MB; width
  is the text's own, clamped to the row, never a fixed canvas.

Two things it does not do. Colour is not baked in: on API 31+ the tint is a
colour *resource* the launcher resolves in its own theme, exactly as the
background is; on API 29–30 Glance resolves it in our process at translation,
which is how it already translates the checkbox glyph and plain text colour
below 31. What that costs there is **not** the "whole widget stale together"
this paragraph used to claim — measured on API 30 on 2026-08-28, the
background follows the host on its own and leaves the name and the glyph
behind, which is a legibility failure rather than a stale one
([widget.md](widget.md), §7.4). And the glyphs of a script
outside Outfit's `cmap` come from the device's fallback fonts, the same as the
app, and take the *layout's* height rather than Outfit's so an emoji is not
clipped. The edge this needed, `widget → core:ui`, carries `R.font.outfit` and
nothing else; architecture §2 says so.

Three things the first cut got wrong, caught by review the same day and worth
keeping because each looked like something else. **A right-to-left name drew
nothing** — not, as the first test claimed, because Robolectric lacks Hebrew
fonts, but because `ALIGN_NORMAL` puts an RTL line at the right edge of the
layout, and a layout as wide as the room drawn into a bitmap as wide as the
text painted every glyph off the canvas; the layout is now built at the text's
own width and the test asserts ink. **The name was described on the image**,
which read to TalkBack as an anonymous checkbox beside a named picture; it is
on the checkbox now, paired with the checked state as `CheckBox(text = …)` was.
**The budget above is per size.** `SizeMode.Exact` composes once per size the
host reports and ships them in one `RemoteViews`, and a bitmap grows with the
square of the font scale, so at 200 % on a launcher reporting two sizes the cap
is nearer twenty rows than dozens. Still far from a real habit list; if it ever
binds, `ALPHA_8` (the ink is coverage only) is a 4× saving that was not needed
yet. One thing it does not do: a name with no strong character — `10,000`,
`3 × 💧` — lays out left-to-right whatever the host's direction, because the
bitmap is drawn with the app's configuration and a per-app locale can differ
from the launcher's.

**Why this negative is trustworthy, given how often a check here has measured
nothing.** The `serif` row is a positive control, and it is the whole reason the
zero-deltas mean something: it proves the ruler works on this surface, in this
process, at this granularity — a 16px difference read cleanly, so the ~8px an
Outfit that had loaded would have produced was well within reach. The bundled
rows did not come back *close*, they came back **exactly equal**. Three further
things were ruled out rather than assumed: the APK carried
`res/font/outfit.ttf` at its unmodified 110,884 bytes; the compiled layout
carried `android:fontFamily=@0x7f080000` on the bundled row and nothing on the
control, confirmed with `aapt2 dump xmltree` against the installed APK; and a
JVM test asserted the file's `sfnt` signature, because a saved error page with a
`.ttf` extension would have failed in a way indistinguishable from the route
failing. A 3x screenshot agrees with the ruler and is the check that does not
depend on it at all — the three bundled rows are pixel-identical Roboto and the
`serif` row is visibly serifed. Letterforms, not widths: Outfit's geometric
`G`, `o` and `0` are nothing like Roboto's, so "the widths happened to match"
is not available as an explanation.

Two limits on the claim, stated because they are the honest edges of it. It was
measured on an emulator and one launcher, not on the Nothing A059 the colour
work used; a launcher is free to differ, though the thing that failed is
framework-level font resolution rather than anything the launcher chooses. And
it says nothing about *multiple weights*: `fontVariationSettings` is not a
`@RemotableViewMethod`, so even a route that worked would have handed a widget
one instance of a variable font. Moot while the route is dead; relevant again if
it is ever reopened.

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
| Momo only | `#E0708F` ‡ | `#FFB1C4` ‡ |

† Neither `tertiary` survived measurement. Kept here as the record of what was
chosen from the drawings; "What shipped" below has the replacements and why.

‡ Wrong value, right reservation — corrected 2026-08-25. The canvas never drew
Momo in this coral: the character is a pastel, `#F7C3D1` with `#E8879F` as its
accent, the same in both themes, and [momo.md](momo.md) §2 is its palette.
What this row still means is what mattered: the pink family belongs to the
mascot and no theme role is drawn from it.

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
crossover every tint clears **4.58:1** whichever glyph it takes, so contrast
stopped being a property of the palette and became a property of the function —
which is what let §6 retune all eight hues without re-deriving their glyphs. That
is the same 4.58 quoted above and it is the *minimum*, attained at the crossover
where the two options are equal; an earlier revision of this paragraph said 4.49,
which was simply wrong.

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
"done" role and a "not done" role, and the "not done" state must not read as
disabled-because-broken.

**Built 2026-08-24: `primary` and `surfaceContainerHighest`**, measured at 4.41
in light and 6.94 in dark against each other, with the day number on each in
`onPrimary` and `onSurfaceVariant`. [insights.md](insights.md) §8 has the working.

One clause of the paragraph above was wrong and is corrected rather than quietly
dropped: it asked for both roles to be **legible against `surface`**, and the
"not done" ground is not — `surfaceContainerHighest` on `surface` is 1.26 in
light and 1.50 in dark. That is the right answer for a month of thirty-one cells.
Holding a quiet fill to 3:1 against the page is the mistake
`GawiColorSchemeTest`'s own KDoc already refused for every other container role,
and a grid that obeyed it would read as a keypad. What owes a floor is the
**pair** — done against not-done, which is the information — and the number drawn
on each, which is text. Both are held; the fill against the page is not.

The other correction is about today. `RetroStrip` marks today with a filled
`secondaryContainer` cell, and reusing that here fails at 1.04 light / 1.05 dark
against the not-done ground — indistinguishable, the same way §3's published
`tertiary` failed at 1.02. The grid uses a ring instead.

## 5. Typography

**Decision: bundle one variable font in `:core:ui` and define a real
`Typography`.** `core/ui/src/main/res/font/` is where it goes. Two corrections
to this line as it was first written, both found on 2026-08-24: `:core:ui` does
have a `res/` directory now — the history screen's shared composables gave it
one (docs/ux/insights.md §8.6) — so the directory the font needs already exists.
And **the OFL text cannot ship beside the file.** `res/font/` accepts font files
and XML families only, and a resource filename cannot carry uppercase letters,
so an `OFL.txt` in there is a build error rather than good citizenship. The
licence needs a home outside `res/`.

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
- ~~**Possibly close to the system sans in character** — conditional on §2's
  experiment. If a widget cannot get the bundled font it renders in the platform
  face, and the app and widget sit next to each other on a home screen: a quiet
  humanist face makes that divergence hard to notice, a strongly geometric one
  (Outfit, which the canvas uses) makes it obvious. If `AndroidRemoteViews` does
  carry `@font/…` through, the constraint disappears and the face can be chosen
  on identity alone.~~ **So the typeface waits on that experiment rather than on
  taste** — decided 2026-08-23.

  **The experiment ran on 2026-08-24 and the constraint is real, so it hardens
  from *possibly* into the live tension in this choice.** §2 has the
  measurement: a widget cannot be handed a bundled font, and the only faces it
  can name are the platform's four generics. The app will render in ours and the
  widget in the system sans, one home screen apart — *permanently*, this said
  until 2026-08-25, and the next paragraph but one records why that word went.

  What that does **not** mean is "pick a humanist face and move on", and it is
  worth saying so before the next session reads it as an instruction. It makes
  the divergence a cost to weigh rather than a veto: a quiet face pays less of
  it and gives up the identity this whole brief exists to buy, and a geometric
  one — Outfit, still the canvas's face — buys the identity and pays the cost in
  full on one surface, which is the smallest surface the app has. Bitmap text is
  the only way to have both, at the price §2 lists, and on 2026-08-24 this said
  it was not worth paying for a checkbox list. **So the typeface no longer waits
  on the experiment. It waits on that trade, which is taste, and it is now the
  only thing between this section and a real `Typography`.**

  **That last judgement was reversed on 2026-08-25, and the price was paid.**
  The widget now draws in Outfit as bitmaps; §2 has what it cost once built,
  which was less than the list above priced it at — font scale is honoured at
  the next render, the description is the name, and the size budget is dozens
  of rows deep. The divergence §5 accepted as permanent lasted one day.

**Decided and BUILT on 2026-08-24: the face is Outfit, and the divergence is
accepted.** The trade above was taken in the direction of identity rather than
of hiding the seam — the canvas's own face, geometric, bundled as one variable
font at 110,884 bytes, which is under half what this section budgeted. The app
draws in it; `:widget` drew in the system sans for a day and, **since
2026-08-25, draws in Outfit as bitmaps** — §2's closing paragraph is the
mechanism and its costs, `widget/…/BitmapText.kt` the code, and
`core/ui/theme/Type.kt` records the rest.

One limit on "the app draws in Outfit", kept because it is what drove the icon
set: the file's `cmap` covers 360 characters, and five of the glyph characters
the screens drew as *text* were outside it — `☰`, `◔`, `⚙`, `✎` and `✕`. Those
fell back to the platform face, so an app bar mixed two faces at one size. No
tofu and no crash, but it was this document's own "looks like a design choice
rather than a gap" failure. **Fixed on 2026-08-24 by §7.5**, which converted
every character-as-icon to a vector, so the audit that follows is history
rather than a live constraint. The `cmap` limit is not history, and it has live
dependents rather than only hypothetical ones: `RetroStrip` still draws `✓`, `·`
and `•` as text inside a clickable cell (§7.5).

**What the audit found present**, kept so nobody re-runs it: `←`, `‹`, `›`, `✓`,
`•`, and — added after review noticed the first list was short — `−` (U+2212)
and `·` (U+00B7). `−` was the one that mattered. `WeeklyTargetStepper` drew it
beside an ASCII `+`, both `titleLarge`, in one `Row`; absent, that would have
been a two-face pair *adjacent at one size*, worse than the app-bar case above.
It was present — and that pair is two icons now regardless, which is the
difference between the audit being wrong and it being superseded. `✓`, `·` and `•`
are still drawn as text and still covered, and so is the editor's selection tick
(§4.3). "Data in a grid rather than controls" is what this paragraph said until
2026-08-24 and it was the wrong way to put it — §7.5 has the accurate version.
What holds either way is that the `cmap` still has live dependents.

**The habit-icon emoji are a separate matter, not a gap in that audit.**
`HabitPalette`'s twelve are outside this `cmap` and always will be — Android
draws colour emoji through its own font, which no text face substitutes for, and
§4.2 already records that along with what it costs the tint. Sweeping every
non-widget main source finds 36 distinct non-ASCII characters; what is left after
the glyphs and the emoji sits in KDoc (`√`, `≡`, `≥`) and is never drawn.

Three things the code decided that this section had not:

- **The family is set on all *fifteen* Material roles, not the ten in the table
  above.** The table is still right about what the app draws, and that is what
  makes the type reviewable — but it is a list of roles *used*, not a list of
  roles permitted a face. Left at the default, the five nobody draws yet would
  render the next screen's `headlineMedium` in Roboto, silently, and it would
  look like a choice rather than a gap. Covering all fifteen invents no sizes,
  which is what the "anything not in that table does not need a value" rule was
  actually guarding against.
- **Only the face changed. Every size, line height and letter spacing is still
  Material's**, and `GawiTypographyTest` asserts that against a fresh
  `Typography()` rather than trusting the claim. The sizes are the one part of
  this already validated on a device, across four feature modules since Phase 0;
  moving the face and the scale in one change would make any regression
  unattributable to either. `letterSpacing` is the likeliest thing to want next —
  Material's is tuned for Roboto and Outfit is wider — and that is a change to
  make while looking at a screen.
- **Four weight entries, all pointing at the same file, and the `wght` axis
  named explicitly on each.** Two of those decisions were corrected by review
  and by measurement after the first version of this bullet, so both are stated
  with what settles them.

  *Explicit `variationSettings` is load-bearing, and looks redundant.* Decompile
  Compose and `Font(resId, weight, …)`'s default plainly derives
  `FontVariation.Settings(weight, style)` from the declared weight; a review made
  exactly that argument and it is a reasonable one. It does not survive a device:
  removing the argument renders the **whole app** at the file's `fvar` default,
  which is 100 — "Outfit Thin" — measured and reproduced, 3,799 differing pixels
  on one screen.

  *The mechanism is overload resolution*, found on 2026-08-24 after the review
  offered a hypothesis worth chasing. `FontKt` declares three `Font()` overloads
  for a resource id, and **only the widest takes variation settings**;
  `Font(resId, weight)` binds to the narrowest, which builds its `ResourceFont`
  without touching `FontVariation` at all. Loading then returns the variable face
  at its `fvar` default with nothing instanced. So the decompiled default that
  *does* derive `Settings(weight, style)` is real — it simply belongs to an
  overload a two-argument call never reaches. That also disposes of the earlier
  unresolved theory, that a derived `ital` axis this font lacks voided the
  string: axes were never the variable, the overload was, which is why naming
  `italic` explicitly is pixel-identical. **The hazard is therefore not a
  redundant argument but an argument whose deletion silently changes which
  function is called.** A test asserts every entry names `wght`, so the tempting
  deletion goes red instead of shipping a uniformly thin app.

  *Four rather than two, because the app can request more than it writes.*
  Material's fifteen roles ask for W400 and W500, and nothing sets a weight by
  hand — but Compose adds `Configuration.fontWeightAdjustment` to every request,
  so with the system *Bold text* setting on the same roles ask for W700 and W800.
  Registered, they hit real instances already in the file; unregistered, they get
  platform synthesis over a genuine bold the app already shipped.

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

Values stay **uppercase and six digits**, as a convention that keeps what the
palette owns canonical. It is no longer load-bearing, and the story of that is
worth a line: it used to be justified by a real defect, because `ColorPicker`
compared hexes as strings, so a stored lowercase palette colour was offered twice
— once as a hue and once as §6.3's "colour you already have", with the wrong one
selected. Review caught it. The picker now compares what two hexes *draw*, which
also covers the eight-digit spelling of the same colour that a case-insensitive
comparison would still have duplicated. The convention stays because canonical
storage is worth having; the editor no longer depends on it.

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
because a decision only counts once it is written here. Since 2026-08-25 the
canvas is also the only design link: the 2026-08-19 low-fi Today sketch with
the same A/C rejects sits on its "Today sketch (archive)" page, and Momo's
approved timings on its "Momo motion" page ([momo.md](momo.md) §3).

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
**Which of the two a phone shows became the user's choice on 2026-08-26**
([settings.md](settings.md) §7): the schemes here are unchanged, and what the
setting adds is that dark mode is no longer only reachable through a system
toggle. The one surface it cannot reach is the widget, for the reason §7.4
already gives about `RemoteViews`.
Dark is where the habitat earns itself: the tank drops to deep teal and coral
Momo has nowhere to hide, which is also where candidate B gets worse rather than
better. The role sets in §3 are the contract; `primary` and `tertiary` keep the
lightness step §4.1 measured, so day-versus-week streaks survive a greyscale
reading.

This is what unblocked `Theme.kt`, the retuned hues, the widget's duplicate hexes
and the Insights heatmap — the whole reason PRD §5's Phase 1 order was inverted.
`Theme.kt` and the hues landed the same day; **the heatmap followed on
2026-08-24** and is what §4.4 now records. The widget's own palette is the one
still to come, and it has values to draw from.

~~**Still a leaning: Momo's style is flat.**~~ **Decided 2026-08-25: flat, and
the character is the canvas's own, built for Today** — [momo.md](momo.md) is the
record. The other two treatments stay on the canvas as the record of the choice.
The launcher mark derives from the character, and was drawn the same day:
`ic_launcher_foreground.xml` is the canvas's artboard transcribed (§8).

**Parked, with an experiment rather than a question: the typeface.** §5 has the
condition and §2 has the experiment.

### 7.2.1 The Insights artboard, and its two wrong captions

**"The screen the palette was blocking"** — four cards: the heatmap, a
completion-rate sparkline, tag-effort bars, and a Month/Quarter/Year picker
labelled *"a proposal, not a decision"*. Recorded here on **2026-08-24**, which
is later than it was drawn, because until then it was a drawing and §7's opening
rule says a decision only counts once it is written down.

**What it settled**, and what got built from it: the picker's fixed set of three,
the bar-and-total shape of the tag list, and the sparkline with its months
labelled underneath. [insights.md](insights.md) §§8.7–8.8 carry the detail.

**What it got wrong, twice, and both were caught by measuring.**

- Its rate card draws the current month as a dash, and its caption justifies that
  by saying `Rates.completionRate` returns null for a part-month. It does not —
  it excludes unfinished units from both sides of the fraction, so a part-month
  is already comparable to a finished one. The shipped card draws the number.
- Its untagged bar is grey where the tagged bars are teal. Against `primary`,
  every candidate for "a quieter bar" measures between **1.07 and 1.97** — the
  same class of failure as §3's `tertiary` at 1.02 and the history grid's
  today-fill at 1.04. The shipped bars are all `primary`, and untagged is set
  apart by a recessive **label** and by sorting last.

**Its heatmap is a rolling twelve-week grid with weekday rows and no day
numbers, and the app draws a calendar month instead.** That divergence is
deliberate and is the app's answer, not a defect: the calendar keeps a date
reference and lets any month be reached, and the month grid was already built and
verified when the artboard was reviewed. Left as it stands rather than reconciled
— which is exactly the case §7's opening paragraph warns about, so it is written
down here rather than left to be found.

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
  ~~it is a static vector drawable per mood: four assets~~ — priced before the
  character was code. Built 2026-08-25 as **zero assets**: the widget
  rasterises `drawMomo` at the resting frame the way it rasterises Outfit
  ([momo.md](momo.md) §4), in the existing Today widget, only when the host
  gives it two cells. The large-widget question this bullet list prices is
  thereby answered without a second provider.
- **The checkbox glyph reopens**, as `TodayWidget`'s comment predicted. It is
  unpinned only because there was no palette and because `CheckBoxColors` rejects
  the resource-backed providers every `GlanceTheme` colour is. With hexes decided,
  `ColorProvider(Color)` literals become available — and pinning still will not
  make the glyph assertable, so `docs/running.md` §4 keeps its by-hand check.

### 7.5 The icon set, and the dingbats it retires

**Decided 2026-08-24: Lucide, vendored.** Ten VectorDrawables in `:core:ui`,
generated from `lucide-static` **1.34.0** by `scripts/convert-lucide.py`, reached
through `GawiIcons` and drawn by `GawiIconButton`. This closes the half of OQ-4
that was never about Momo.

**Why Lucide specifically.** The direction asked for was "something like Lucide
or what's in the Claude design", and those turn out to be the same answer: Claude
Code's own scaffold config pins `"iconLibrary": "lucide"`, and its design
guidance refers to "outline icons (Lucide/Feather)". Anthropic has not published
the claude.ai product set itself, so there is nothing closer to copy — but the
family is the monoline one, and Lucide is its ISC-licensed member. Recorded
because "it looks like the Claude design" is not a licence and would not have
survived the next reader asking where the files came from.

**It is not a dependency, and could not be.** Lucide ships no Android artifact.
Ten files of about 300 bytes each, generated and reviewable in the diff, is what
vendoring costs here; `material-icons-extended` was the alternative that
`GlyphButton`'s KDoc rejected, correctly, for the dependency it named.

**Licensing is not uniform.** Lucide is ISC. Six of these ten —
`arrow-left`, `chevron-left`, `chevron-right`, `minus`, `plus`, `x` — are derived
from Feather and are **additionally MIT** (Cole Bemis). Upstream's notice carries
both texts and the derived-from list, so it is vendored verbatim as
`licenses/Lucide-ISC.txt` and nothing here had to adjudicate which clause
governs. Each drawable's header says which licence is its own. **This does not
close the licences release gate**: nothing packages `licenses/` and there is
still no about screen, exactly as §5 left it for the font.

| Icon | Replaces | Where |
|---|---|---|
| `arrow-left` | `←` | Up, in five app bars |
| `pencil` | `✎` | Habit detail, edit |
| `list-checks` | `☰` | Today, manage habits |
| `chart-pie` | `◔` | Today, insights |
| `settings` | `⚙` | Today, settings |
| `x` | `✕` | Habit editor, cancel |
| `chevron-left`, `chevron-right` | `‹`, `›` | History, month pager |
| `minus`, `plus` | `−`, `+` | Weekly-target stepper, and `plus` again on the habit-list FAB |

**Arrows navigate, chevrons step.** `arrow-left` leaves the screen; a chevron
moves a value inside one. The month pager is the only current chevron caller and
the rule exists so the next control does not reopen the question.

**`list-checks`, not `menu`.** `☰` is literally three lines and `menu` is its
exact vector, but it implied a navigation drawer this app has never had. The
destination is a list of things you tick, so the icon says that. `chart-pie` for
Insights is the opposite case — a direct translation of `◔`, a part-filled
circle, and a match for the rate cards that dominate the screen.

**All fifteen call sites converted, not just the five broken ones.** §5's audit
found five glyphs outside Outfit's `cmap`; `←`, `‹`, `›`, `−` and `+` were fine.
Converting only the broken five would have traded a font mismatch for a
stroke-weight mismatch — a 2px vector `✎` beside a typographic `←` in the same
app bar is the same "looks like a design choice rather than a gap" failure, just
harder to name. This is why §5's glyph audit is now retired rather than narrowed:
the app no longer draws a character as an *icon* anywhere.

**The precise claim, because two looser ones were wrong.** What went is every
character standing in for a picture of an action. What stayed is text that still
carries a control's *state*, and there are four of them, not three:
`RetroStrip`'s `✓`, `·` and `•`, plus the `✓` `HabitEditorPickers` draws on the
selected colour swatch — which §4.3 already recorded, and which §7.5 missed on
its first pass because a line-based grep for short literals drawn as `Text` does
not match a multi-line call.

**And the control is conditional, which the first correction also got wrong.**
The swatch is a `Role.RadioButton` always. The day cell is a
`combinedClickable(role = Role.Checkbox)` **only while the day is open** —
`RetroStrip`'s shut branch has no click, no role and a `disabled()` semantic, and
`HabitsUiMapper` computes `open` as `!day.isBefore(oldestOpen) && !archived`, so
on an archived habit *every* cell is shut and the marks carry no control at all.

Two rounds of review landed on this paragraph: the first killed "no character is
a control anywhere", the second killed "the cell is a `Role.Checkbox`" full stop.
Both were absolutes, and an absolute here is the shape of sentence that gets §5's
`cmap` note deleted — which is the thing that must not happen, because these four
are what depend on it.

Whether those marks should become icons is a real question and a separate one.
The strip's are five-per-row `labelLarge` and sized by the type scale rather than
by a 24dp box; the swatch tick is centred on a coloured ground and takes
`glyphColorOn`, so it is a contrast decision as much as a drawing one.

**Fifteen, because the fourteenth was found by looking and the fifteenth was
not.** The habit list's FAB drew a `+` as text inside a `FloatingActionButton`,
which is not an `IconButton` and so was invisible to a sweep for `GlyphButton`
callers — it turned up only in a grep for short string literals drawn as `Text`.
It matters because the claim in the paragraph above is the kind that is either
true or worthless: one character-as-icon left anywhere, in the same feature as
the stepper it would sit beside, and the set is not a set. `GawiIconButton`
does not fit a FAB, so that one call site drops an `Icon` in by hand.

**Three consequences worth having written down.**

- **Icons do not scale with font scale, and the characters did.** A `titleLarge`
  glyph grew with `fontScale`; a 24dp `Icon` holds 24dp. At 200% the app-bar
  icons now stay put while titles grow. That is Material-correct — touch targets
  are 48dp either way — and it is still a visible change from what shipped, so
  `docs/running.md` §4 checks it rather than assuming it reads as deliberate.
- **`settings` is one path plus a circle, and `VectorDrawable` has no circle.**
  The converter emits it as two exact semicircular arcs. It fails loudly on any
  other primitive rather than converting it, because the failure being avoided is
  a drawing that silently loses a part of itself. The generated files carry a
  "do not hand-tune" header for the same reason.
- **A directional icon must declare `android:autoMirrored="true"`, and the first
  cut of this set did not.** `←` (U+2190), `‹` (U+2039) and `›` (U+203A) are all
  `Bidi_Mirrored`, so the text shaper flipped them and the app had RTL
  correctness *for free*; a `VectorDrawable` has to ask for it. Replacing them
  without the attribute was therefore a regression rather than a gap — the Up
  arrow pointed away from the edge it now sits on, and the month pager read
  inverted. `arrow-left`, `chevron-left`, `chevron-right` and `list-checks` carry
  it (the last for consistency, since `☰` was symmetric); nothing else should,
  because a mirrored gear is wrong for no reason. `GawiIconsTest` asserts the set
  both ways round, and `docs/running.md` §4 has the device recipe — the developer
  option `debug.force_rtl` silently does nothing on an emulator, so a per-app
  locale is what works.

  **This is the one a reader adding an icon will need and would not guess**,
  which is why it is here and not only in the generator: the first cut omitted it
  precisely because no design note asked for it.

**The launcher icon is not a set member, and is now built.** §7.1 designed it
and it waited on the character, not on an icon set; it landed 2026-08-25, the
same day as Momo (§8). What this section gave it is the visual language it has
to agree with, which was the argument for doing icons first.
`app/src/main/res/drawable/ic_reminder.xml` is the other non-member, and
deliberately so: a notification small icon is drawn from its alpha channel only
and must be solid, so it is a different medium — it is now Momo's silhouette
([momo.md](momo.md) §4).

## 8. What this does not decide

- ~~**Momo's art style, expressions, and whether it is static or animated.**~~
  **Decided and built 2026-08-25** — flat, the canvas's character, all four
  expressions, animated in Compose on the Today screen. [momo.md](momo.md) is
  the record, including why PRD §5's Rive recommendation was dropped (its export
  is behind a paid plan) and what that cost. The widget and reminder
  treatments followed the same day, and on 2026-08-26 the tank gained its life
  (the canvas's own weeds and bubbles, at a per-mood tempo), the mood change
  became one interpolated body rather than two crossfaded ones, and finishing
  the day is celebrated — all designed on the canvas's "Habitat & motion" page
  (momo.md §3, §4, §6). Still open there: streak milestones.
- ~~**The launcher icon — its drawing and its wiring, not its design.**~~
  **Built 2026-08-25.** §7.1's decision — Momo as a mark, the woven thread as
  the monochrome layer — is `ic_launcher_foreground.xml` and
  `ic_launcher_monochrome.xml`, transcribed from the canvas's "Launcher icon"
  artboard on its 108 grid — inside a group that scales the mark by 0.85 and
  the thread by 0.9 about the centre, because the artboard mocked its masks
  over the whole 108 canvas while a launcher shows the central 72 and
  guarantees only a 66 dp circle; review measured the fronds at 38.6 from
  centre against a safe radius of 33 — under `mipmap-anydpi/ic_launcher.xml` — one file with
  all three layers, because lint's `MonochromeLauncherIcon` *fails* an adaptive
  icon without `<monochrome>` and does not count the API 33 element as unused;
  a `-v33` split was tried first and measured against. The ground is light `primaryContainer` at
  the value `Color.kt` ships, not the canvas's pre-retune hex — §3's rule that
  the table wins — and `LauncherIconTest` pins the two against each other. The
  manifest no longer names `sym_def_app_icon` anywhere.
- **Spacing.** `GawiSpacing` parks itself on OQ-4 along with the rest, but
  dimensions were genuinely not part of this brief. Its KDoc gets narrowed rather
  than rewritten.
- **Whether Momo appears on the Insights screens.** [insights.md](insights.md) §7
  raises it and defers it to OQ-4; it belongs to the second half, with the art.
- **Dynamic colour.** Off, and staying off — a designed identity is the point of
  this document, and Material You would hand it back to the wallpaper. The theme
  setting built on 2026-08-26 is not a step towards it and does not reopen this:
  it chooses between the two schemes §7.2 designed, and offers no third.
