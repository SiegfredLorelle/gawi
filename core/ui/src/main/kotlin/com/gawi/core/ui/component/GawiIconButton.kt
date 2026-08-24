package com.gawi.core.ui.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * An icon button, named for assistive technology.
 *
 * **This was `GlyphButton`, and it drew a character.** Its KDoc argued that
 * `material-icons-extended` was the wrong trade for four arrows and a pencil,
 * which was true of that dependency and is still true. What it did not survive
 * is the reason the characters had to go: five of the ones the app drew — `☰`,
 * `◔`, `⚙`, `✎`, `✕` — are outside Outfit's `cmap`, so they fell back to the
 * platform face and an app bar rendered two typefaces at one size. The trade
 * that replaced it is neither of those: ten vendored path files, no dependency
 * at all ([GawiIcons], docs/ux/visual-identity.md §7.5).
 *
 * **The label is still required, and for a sharper reason than before.** A `←`
 * was not a word; a vector is not even text. The control has no accessible name
 * of its own either way, so [labelRes] is not optional and the description is
 * set by hand.
 *
 * **The `Icon` is deliberately unnamed.** `contentDescription = null` on it,
 * with the name on the enclosing button's `semantics` instead. Naming both
 * would announce the control twice under TalkBack, and putting it on the button
 * is what keeps every existing `onNodeWithContentDescription` lookup — in
 * `TodayScreenTest`, `AppNavigationTest` and `WriteJourneyTest` — pointing at
 * the same node it always did. The icon swap is invisible to them, which is the
 * property that made it safe.
 *
 * **[enabled] is here because `:feature:habits` had already written it.** The
 * weekly-target stepper needs its `−` and `+` to go dead at the schedule's
 * bounds, and it had a private `StepperButton` that was this composable plus
 * that one parameter. Absorbing it is `core/ui/component/`'s whole purpose —
 * the same rule that moved this file here when a third caller appeared, and
 * that the habit icon badge, written three times, is the cautionary tale for.
 *
 * Takes a resource id rather than a resolved string, unlike [Notice]. A caller
 * hands over its own copy either way; an id keeps the `stringResource` call on
 * this side, which is what every existing call site already does.
 *
 * The default tint is `LocalContentColor`, so an app bar's icons take the app
 * bar's colour without being told. Do not pass `Color.Unspecified` to get
 * around a contrast question — that disables the filter rather than choosing a
 * colour, and this project has already shipped one assertion that passed
 * because of exactly that.
 */
@Composable
fun GawiIconButton(@DrawableRes icon: Int, @StringRes labelRes: Int, enabled: Boolean = true, onClick: () -> Unit) {
    val label = stringResource(labelRes)
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        Icon(painter = painterResource(icon), contentDescription = null)
    }
}
