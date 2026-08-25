package com.gawi.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.gawi.core.ui.R

/**
 * The weights [Outfit] registers, exposed so [GawiTypographyTest] can pin the
 * accessibility half rather than the obvious half.
 *
 * Declared before [Outfit] because it initialises it — top-level properties run
 * in declaration order, and the other way round reads this as null and throws
 * during class init. An earlier revision said it "leaves the family empty",
 * which is wrong twice: the null read comes first, and an empty family is not
 * constructible anyway — `FontListFontFamily` throws "At least one font should
 * be passed to FontFamily". Both failure modes are loud, so the ordering is a
 * smaller hazard than that wording implied.
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
 * convincing the argument looks.** It looks very convincing: decompile
 * `ui-text-android:1.12.0` and `Font(resId, weight, …)`'s `$default` bridge
 * plainly builds `FontVariation.Settings(weight, style)` from the declared
 * weight, so passing the axis by hand reads as duplicated work. A code review
 * made exactly that argument from exactly that bytecode. **It does not survive a
 * device.** Measured 2026-08-24 and reproduced: with the argument removed the
 * entire app renders at this file's `fvar` default, which is **100** — its name
 * table reads "Outfit Thin" — hairline everywhere, 3,799 differing pixels on one
 * screen against the same build with the argument present.
 *
 * **The mechanism is overload resolution, and it is worth naming precisely,
 * because the bytecode that makes the argument look redundant is real — it just
 * belongs to a function this call does not reach.** `FontKt` declares *three*
 * `Font()` overloads for a resource id: `(resId, weight, style)`,
 * `(… , loadingStrategy)`, and `(… , loadingStrategy, variationSettings)`. Only
 * the third accepts variation settings. `Font(resId, weight)` binds to the
 * **first**, whose body constructs `ResourceFont` without touching
 * `FontVariation` at all, so the settings are empty rather than derived. Loading
 * then runs `ResourcesCompat.getFont(context, resId)`, which hands back the
 * variable face at its `fvar` default with nothing instanced, and
 * `setFontVariationSettings` afterwards has nothing to apply. The
 * `Font-…$default` bridge that *does* build `Settings(weight, style)` is the
 * third overload's, and passing the argument is what selects it.
 *
 * That also settles the theory this paragraph used to be unable to rule out —
 * that the derived settings name an `ital` axis this font lacks and an
 * unsupported axis voids the string. `FontVariation.italic(0f)` added explicitly
 * is pixel-identical to weight-only, so axes were never the variable; the
 * overload was. **So the hazard is not "a redundant argument" but "deleting an
 * argument silently changes which function you call".**
 * [GawiTypographyTest] asserts every entry names `wght`, so the deletion fails
 * a test rather than shipping a thin app.
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
 * **This font's `cmap` covers 360 characters, and that is still a live
 * constraint — but no longer a visible one.** Five of the glyphs the app drew as
 * *text* were outside it: `☰` (U+2630), `◔` (U+25D4), `⚙` (U+2699), `✎` (U+270E)
 * and `✕` (U+2715). They fell back to the platform face, so an app bar mixed two
 * faces at one size — habit detail drew `←` in Outfit directly beside `✎` in the
 * system font. No tofu and not a crash, but it was the "looks like a design
 * choice rather than a gap" failure this project keeps naming, and the fix named
 * here was icons rather than dingbats.
 *
 * **That fix shipped on 2026-08-24** (`GawiIcons`, docs/ux/visual-identity.md
 * §7.5): every character the app drew as an *icon* is now a vendored vector.
 * **Not every character, and the difference matters here.** Four things still
 * draw text from this `cmap`: `RetroStrip`'s `✓`, `·` and `•`, and the `✓`
 * `HabitEditorPickers` puts on the selected colour swatch. Each sits inside a
 * control and carries its *state* rather than being an affordance — the swatch
 * is a `Role.RadioButton` always, and the day cell a `Role.Checkbox` **only
 * while the day is open**, since the shut branch is `disabled()` and every cell
 * of an archived habit is shut. So the number stays written down for those four
 * as much as for the next character someone reaches for.
 *
 * Two rounds of review landed here. The first killed "nothing is a control any
 * more", the second killed "the cell is a `Role.Checkbox`" without the
 * condition. Both were absolutes, and an absolute is the shape of sentence that
 * gets this limit deleted — see docs/ux/visual-identity.md §7.5.
 *
 * **What the audit found present**, so nobody re-runs it: `←`, `‹`, `›`, `✓`,
 * `•`, and — added after review pointed out the first list was short — `−`
 * (U+2212) and `·` (U+00B7). `−` was the one worth checking rather than
 * assuming: `WeeklyTargetStepper` drew it beside an ASCII `+`, both
 * `titleLarge`, in one `Row`, and had it been absent that would have been a
 * two-face pair at one size, adjacent, and more visible than the app-bar case.
 * It was present — and that pair is two icons now regardless, which is the
 * difference between an audit that was wrong and one that has been superseded.
 * `✓`, `·` and `•` are still drawn as text, and are still covered here — as is
 * the editor's swatch tick, which the first version of this paragraph forgot.
 * They are state marks rather than pictures of an action, which is why §7.5 left
 * them alone; "not controls" was simply the wrong way to say it.
 *
 * **The habit-icon emoji are a different question and not an omission here.**
 * `HabitPalette`'s twelve icons are outside this `cmap` too, and always will be:
 * Android draws colour emoji through its own emoji font, which no text face
 * substitutes for — docs/ux/visual-identity.md §4.2 already covers that, along
 * with its consequence for tint. A sweep of every non-widget main source finds
 * 36 distinct non-ASCII characters in all; the remainder are in KDoc and
 * comments (`√`, `≡`, `≥`) and are never drawn.
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

private fun TextStyle.inOutfit(): TextStyle = copy(fontFamily = Outfit)

/**
 * The app's type: Material's scale, drawn in [Outfit].
 *
 * **Only the face changes here, and that is deliberate rather than unfinished.**
 * Every size, line height and letter spacing is Material's baseline, untouched.
 * The sizes are the ones all four feature modules have been drawing at since
 * Phase 0, so they are the only part of this already validated on a device;
 * changing the face and the scale in one commit would make any regression
 * unattributable to either. `letterSpacing` is the value most likely to want
 * tuning next — Material's is tuned for Roboto, and Outfit is wider — and that
 * is a change to make while looking at a screen, not while writing this file.
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
 * uppercase letters, so an `OFL.txt` in there is a build error. §5 said
 * otherwise and has been corrected. What is **not** yet true is that a user can
 * read it: nothing packages `licenses/` into the artifact and the app has no
 * about-or-licences surface, so a release currently distributes Outfit with only
 * the notice in the font's own `name` table (IDs 0, 13 and 14).
 *
 * Review split on whether that is already compliant, so here is the clause
 * rather than a judgement. OFL 1.1 §2 allows the notice "as stand-alone text
 * files, human-readable headers or in the appropriate machine-readable metadata
 * fields within text or binary files **as long as those fields can be easily
 * viewed by the user**". The `name` table is such a field; inside an APK it is
 * not easily viewable by anyone without extraction tooling, which is the half
 * that fails. So this is not "probably fine" — it is **owed before a public
 * release**, and it is a release gate rather than a merge gate.
 *
 * **The widget does not get this `Typography`, and draws in the face anyway.**
 * A `RemoteViews` tree resolves only the platform's generic family names,
 * measured on 2026-08-24 (docs/ux/visual-identity.md §2), so `:widget` cannot
 * be handed the font; since 2026-08-25 it rasterises its text in Outfit itself
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
