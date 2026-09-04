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
 * **A vendored vector rather than a character**, ten path files against no
 * dependency at all ([GawiIcons], docs/ux/visual-identity.md §7.5). Drawing
 * these as text is what the face cannot take: `☰`, `◔`, `⚙`, `✎` and `✕` are
 * all outside Outfit's `cmap`, so each falls back to the platform face and an
 * app bar renders two typefaces at one size. `material-icons-extended` is the
 * other way to get vectors, and the wrong trade for four arrows and a pencil.
 *
 * **[labelRes] is not optional.** A vector is not text, so the control has no
 * accessible name of its own and the description has to be set by hand.
 *
 * **The `Icon` is deliberately unnamed.** `contentDescription = null` on it,
 * with the name on the enclosing button's `semantics` instead: naming both
 * announces the control twice under TalkBack, and the button is the node an
 * `onNodeWithContentDescription` lookup should find.
 *
 * **[enabled] is for a caller whose button goes dead at a bound**, as the
 * weekly-target stepper's `−` and `+` do at the schedule's.
 *
 * Takes a resource id rather than a resolved string, unlike [Notice]. A caller
 * hands over its own copy either way; an id keeps the `stringResource` call on
 * this side.
 *
 * The default tint is `LocalContentColor`, so an app bar's icons take the app
 * bar's colour without being told. Do not pass `Color.Unspecified` to get
 * around a contrast question: that disables the filter rather than choosing a
 * colour, and an assertion against it passes on nothing.
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
