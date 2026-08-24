package com.gawi.feature.insights

/**
 * What the history screen reports.
 *
 * Its own file, beside the screen rather than inside it, the way
 * `:feature:habits` keeps its actions. Named after the type rather than after
 * the module, unlike `HabitsActions.kt` — detekt's `MatchingDeclarationName`
 * requires it of a file with one top-level declaration, and that file has
 * three.
 *
 * Three lambdas and no ids: unlike habit detail's actions, nothing here names a
 * habit back to its caller, because nothing here writes. Which month is on
 * screen is the ViewModel's business and not `:app`'s — the history is one
 * destination showing a different month, not a month per destination, so
 * stepping must not push a back-stack entry and Back must leave rather than
 * unwind.
 */
internal data class HistoryActions(val onEarlier: () -> Unit, val onLater: () -> Unit, val onBack: () -> Unit)
