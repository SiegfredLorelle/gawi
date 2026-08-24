package com.gawi.core.ui.component

import androidx.annotation.StringRes
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * An icon button with no icon font behind it, named for assistive technology.
 *
 * The glyph is a character rather than a vector because
 * `material-icons-extended` is not a dependency and adding one for four arrows
 * and a pencil would be the wrong trade. What that costs is a control with no
 * accessible name of its own — a "←" is not a word — so [labelRes] is required
 * rather than optional and the description is set by hand.
 *
 * Here rather than in a feature module because three of them draw one. It was
 * written twice with identical bodies, in `:feature:habits` and
 * `:feature:settings`, each explaining that feature modules cannot see each
 * other; the third caller is what makes that explanation a reason to move it
 * instead. This is `core/ui/component/`'s whole purpose, and the habit icon
 * badge — written three times, with three hand-copied contrast decisions — is
 * why the rule exists.
 *
 * Takes a resource id rather than a resolved string, unlike [Notice]. A caller
 * hands over its own copy either way; an id keeps the `stringResource` call on
 * this side, which is what every existing call site already does.
 */
@Composable
fun GlyphButton(glyph: String, @StringRes labelRes: Int, onClick: () -> Unit) {
    val label = stringResource(labelRes)
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        Text(text = glyph, style = MaterialTheme.typography.titleLarge)
    }
}
