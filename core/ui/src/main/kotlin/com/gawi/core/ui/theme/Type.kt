package com.gawi.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.gawi.core.ui.R

/**
 * The weights [Outfit] registers, exposed so [GawiTypographyTest] can pin the
 * accessibility half rather than the obvious half.
 *
 * Declared before [Outfit] because it initialises it — top-level properties run
 * in declaration order, and the other way round reads this as null and throws
 * during class init. A small hazard rather than a silent one: an empty family
 * is not constructible either (`FontListFontFamily` throws "At least one font
 * should be passed to FontFamily"), so both orderings fail loudly.
 */
internal val OutfitWeights = listOf(
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.Bold,
    FontWeight.ExtraBold,
)

/**
 * Outfit, bundled, as one variable font.
 *
 * The face docs/ux/visual-identity.md §5 was choosing, and the one the design
 * canvas draws in: geometric, OFL-licensed and on Google Fonts, so the file here
 * is the same one the browser previewed. One file, 110,884 bytes, against the
 * 200-400KB §5 budgeted — a variable font carries the whole `wght` axis in a
 * single asset rather than one file per weight.
 *
 * **`variationSettings` is load-bearing. Do not delete it as redundant, however
 * convincing the argument looks** — and it looks convincing, because a
 * `Font(resId, weight, …)` bridge really does build
 * `FontVariation.Settings(weight, style)` from the declared weight. That bridge
 * belongs to a function this call does not reach. `FontKt` declares *three*
 * `Font()` overloads for a resource id and only the third accepts variation
 * settings; `Font(resId, weight)` binds to the **first**, whose body constructs
 * `ResourceFont` without touching `FontVariation` at all, so the settings end up
 * empty rather than derived. Passing the argument is what selects the overload
 * that builds them.
 *
 * **So the hazard is not "a redundant argument" but "deleting an argument
 * silently changes which function you call".** Without it the whole app renders
 * at this file's `fvar` default of **100** — its name table reads "Outfit Thin"
 * — hairline everywhere, measured on 2026-08-24. [GawiTypographyTest] asserts
 * every entry names `wght`, so the deletion fails a test rather than shipping a
 * thin app.
 *
 * **Four weights, because that is what the app can *request*, not what it
 * writes.** Material's fifteen roles ask for W400 and W500, and no source in
 * `:core:ui`, `:feature:*` or `:app` sets a weight by hand. But Compose installs
 * an `AndroidFontResolveInterceptor` carrying
 * `Configuration.fontWeightAdjustment` and adds it to **every** request, so with
 * the system's *Bold text* accessibility setting on (+300 on API 31+) the same
 * roles ask for W700 and W800. Registered, those resolve to real instances
 * already sitting in this file; unregistered, they fall to the nearest entry plus
 * platform synthesis — fake bold drawn over a genuine bold the app already
 * shipped. Four entries and one asset is the cheaper half of that trade.
 *
 * **This font's `cmap` covers 360 characters, and that is a live constraint
 * even though nothing visibly fails on it.** Five glyphs the app would
 * otherwise draw as *text* are outside it: `☰` (U+2630), `◔` (U+25D4), `⚙`
 * (U+2699), `✎` (U+270E) and `✕` (U+2715). Outside the `cmap` they would fall
 * back to the platform face, so an app bar would mix two faces at one size —
 * `←` in Outfit directly beside `✎` in the system font. No tofu and not a
 * crash, but it is the "looks like a design choice rather than a gap" failure
 * this project keeps naming, and the answer is icons rather than dingbats.
 *
 * **Most characters the app draws as an *icon* are vendored vectors**
 * (`GawiIcons`, docs/ux/visual-identity.md §7.5), but not all: `RetroStrip`'s
 * `✓`, `·` and `•` and the `✓` on `HabitEditorPickers`' selected swatch still
 * draw text from this `cmap`. Each sits inside a control and carries its
 * *state* rather than being an affordance — the swatch is a `Role.RadioButton`
 * always, the day cell a `Role.Checkbox` **only while the day is open**, since
 * the shut branch is `disabled()` and every cell of an archived habit is shut.
 * That is why §7.5 leaves them alone, and why the limit stays written down for
 * them as much as for the next character someone reaches for.
 *
 * **Present in the face**, so nobody re-checks: `←`, `‹`, `›`, `✓`, `•`, `−`
 * (U+2212) and `·` (U+00B7). `−` is the one worth confirming rather than
 * assuming, because `WeeklyTargetStepper` draws it beside an ASCII `+` at one
 * size in one `Row`, where a fallback would be more visible than the app-bar
 * case.
 *
 * **The habit-icon emoji are a different question and not an omission here.**
 * `HabitPalette`'s twelve icons are outside this `cmap` too, and always will be:
 * Android draws colour emoji through its own emoji font, which no text face
 * substitutes for — docs/ux/visual-identity.md §4.2 covers that and its
 * consequence for tint.
 */
internal val Outfit = FontFamily(OutfitWeights.map(::outfitAt))

/**
 * One entry, at one point on the `wght` axis.
 *
 * The axis value is read off [weight] rather than written as a literal, so an
 * entry cannot declare one weight and instance another — a mismatch that would
 * render correctly in every test and wrongly on a screen. Keeping the numbers
 * out of the file is also what `MagicNumber` asks for, and here that is the
 * right answer rather than a workaround.
 */
private fun outfitAt(weight: FontWeight): Font = Font(
    resId = R.font.outfit,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val Default = Typography()

/**
 * Material's role in [Outfit], with any positive tracking taken to zero — the
 * one metric this app does not take from Material, and [GawiTypography]'s KDoc
 * has why. The guard is `isSpecified` rather than `coerceAtMost(0.sp)` because
 * `TextUnit.coerceAtMost` requires both units to be the same type and would
 * throw on an unspecified role if Compose ever ships one.
 */
private fun TextStyle.inOutfit(): TextStyle = copy(
    fontFamily = Outfit,
    letterSpacing = if (letterSpacing.isSpecified && letterSpacing.value > 0f) 0.sp else letterSpacing,
)

/**
 * The app's type: Material's scale, drawn in [Outfit].
 *
 * **The face, and one metric: positive tracking goes to zero.** Every size and
 * line height is still Material's baseline, untouched: the sizes are the part
 * all four feature modules have been drawing at since Phase 0, so they are the
 * only part already validated on a device, and moving the face and the scale
 * together would make any regression unattributable to either.
 *
 * `letterSpacing` is the exception. Material's positive tracking sits only on
 * roles at 16sp and under — `bodyLarge`, `labelMedium` and `labelSmall` at 0.5,
 * `bodySmall` 0.4, `titleMedium` and `bodyMedium` 0.2, `titleSmall` and
 * `labelLarge` 0.1 — so those are the only roles this touches. Every role at
 * 22sp and over is already 0 except `displayLarge`, which is −0.2 (not −0.25),
 * and those are left exactly as they are.
 *
 * **Zero rather than halved, and the widget is what decides it.** `BitmapText`
 * never sets `Paint.letterSpacing`, so `:widget` draws at 0em at the same
 * nominal 16sp this module calls `bodyLarge`. The two surfaces claimed to match
 * and did not, so zeroing here closes a divergence rather than opening one.
 *
 * What it buys is horizontal slack and not reflow: measured on 2026-08-24
 * against the same build without it, the affected Settings rows narrow by up to
 * 22px — enough to lift the longest off its container bounds — while no wrapped
 * paragraph loses or gains a line and the ≥22sp app-bar title is unchanged.
 *
 * **The family is set on all fifteen roles, though §5 names ten.** The ten in
 * §5's table are the roles this app actually draws, and that list is what makes
 * the type reviewable — it is not a list of roles allowed to have a face. Left
 * at the default, the other five would render the *next* screen's
 * `headlineMedium` in Roboto, silently, and look like a design choice rather
 * than a gap. Covering all fifteen invents no sizes, which is what §5 was
 * guarding against, and removes the trap. [GawiTypographyTest] pins it.
 *
 * **The licence is in the repo, not in the APK.** `licenses/Outfit-OFL.txt`
 * is the OFL text, and it is deliberately not under `res/` — that directory
 * takes font files and XML families only, and a resource filename cannot carry
 * uppercase letters, so an `OFL.txt` in there is a build error.
 *
 * What is **not** yet true is that a user can read it: nothing packages
 * `licenses/` into the artifact, so a release distributes Outfit with only the
 * notice in the font's own `name` table (IDs 0, 13 and 14). OFL 1.1 §2 allows
 * the notice in machine-readable metadata fields "**as long as those fields can
 * be easily viewed by the user**", and inside an APK that one cannot be, which
 * is the half that fails. So this is **owed before a public release** — a
 * release gate rather than a merge gate.
 *
 * **The widget does not get this `Typography`, and draws in the face anyway.**
 * A `RemoteViews` tree resolves only the platform's generic family names,
 * measured on 2026-08-24 (docs/ux/visual-identity.md §2), so `:widget` cannot
 * be handed the font; it rasterises its text in Outfit itself
 * — `widget/…/BitmapText.kt`, which takes `R.font.outfit` from this module and
 * sets `wght` 400 through `Paint.setFontVariationSettings`, because the Thin
 * default described above is a trap on that side too.
 */
val GawiTypography: Typography = Typography(
    displayLarge = Default.displayLarge.inOutfit(),
    displayMedium = Default.displayMedium.inOutfit(),
    displaySmall = Default.displaySmall.inOutfit(),
    headlineLarge = Default.headlineLarge.inOutfit(),
    headlineMedium = Default.headlineMedium.inOutfit(),
    headlineSmall = Default.headlineSmall.inOutfit(),
    titleLarge = Default.titleLarge.inOutfit(),
    titleMedium = Default.titleMedium.inOutfit(),
    titleSmall = Default.titleSmall.inOutfit(),
    bodyLarge = Default.bodyLarge.inOutfit(),
    bodyMedium = Default.bodyMedium.inOutfit(),
    bodySmall = Default.bodySmall.inOutfit(),
    labelLarge = Default.labelLarge.inOutfit(),
    labelMedium = Default.labelMedium.inOutfit(),
    labelSmall = Default.labelSmall.inOutfit(),
)
