package com.gawi.core.ui.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gawi.core.ui.R

/**
 * What a weekly habit's progress *says*, in place of the "1/3 this week" both
 * surfaces draw.
 *
 * A screen reader says the drawn ratio as written: TalkBack 17 read a Today
 * row's as *"1/3 this week"* (docs/running.md §4), which is the slash spoken
 * rather than the relation it stands for. The spoken form names the relation —
 * *"1 of 3 this week"* — and it is here rather than in either feature because
 * a Today row and habit detail both draw it (AGENTS.md's `:core:ui` rule), the
 * same reason [com.gawi.core.ui.streak.spokenStreak] lives beside it.
 *
 * The string is reached through this function rather than at each call site
 * because a feature module reaching a `:core:ui` resource has to
 * `import com.gawi.core.ui.R`, which collides with its own
 * ([com.gawi.core.ui.component.GawiIcons] gives the argument in full).
 *
 * A caller puts this on a node with `clearAndSetSemantics`, not `semantics`: on
 * that TalkBack a node carrying both text and a description is read twice.
 */
@Composable
fun spokenWeekProgress(done: Int, target: Int): String = stringResource(R.string.ui_week_progress_spoken, done, target)
