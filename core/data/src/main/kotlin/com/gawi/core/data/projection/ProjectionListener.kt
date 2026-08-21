package com.gawi.core.data.projection

/**
 * Told that the derived tables have changed, once per committed write.
 *
 * This exists for one reason: **Glance widgets do not observe Room**
 * (architecture §4). An open screen re-reads by itself, because its `Flow`
 * query is invalidated by Room's own `InvalidationTracker` — which is the only
 * data-change notifier in this app. A widget is not a subscriber, so the push
 * has to be explicit.
 *
 * **Why it is an interface here rather than a Glance call in the repository.**
 * Architecture §4 used to say the repository triggers the widget update, which
 * read literally puts Glance in `:core:data` and inverts the module rule
 * (`widget → core`, §2). So the knowledge is split: this layer knows *when* the
 * read model moved, and `:widget` knows what to do about it. `:core:data`
 * declares no binding, `:widget` provides one, and `:app` depends on `:widget`
 * so the graph closes — the same route `DataModule` already takes to reach
 * `:app` while staying `internal` here.
 *
 * **One required binding, not a `@Multibinds` set.** An empty set is a legal
 * graph, so a missing contribution would be a widget that quietly stops
 * updating; a missing single binding is a compile-time graph failure, which
 * `:app`'s navigation test catches by being the only test of the production
 * Hilt graph (architecture §8). The cost is real and accepted: `:core:data`
 * can no longer be assembled into an application that has no listener.
 *
 * **Two call sites, and the other three are deliberate omissions.** It is called
 * from `appendLocked` (every command) and `mergeLocked` (an import), which are
 * the two paths that put new events in the log. Not from `rebuildProjections`,
 * which replays the same log into the same tables and so cannot change what is
 * drawn; not from `sweepStreaks`, because a streak is the one thing the widget
 * deliberately does not show (PRD OQ-5, docs/ux/widget.md §2) — if that decision
 * is ever reversed, this is the omission to revisit first.
 *
 * **What it cannot cover: a day rollover is not an event.** Nothing commits at
 * the cutoff, so no push can be made for one. That gap is answered by the
 * provider's update period and, for correctness rather than appearance, by the
 * widget's tap re-reading instead of trusting the date it drew.
 *
 * **Implementations must be main-safe and quick.** This is called from inside
 * the command mutex and inside a `NonCancellable` region, so blocking here
 * delays the next command and cannot be cancelled out of. The caller's
 * dispatcher is whatever tapped — `viewModelScope` is `Main.immediate` — so an
 * implementation that touches the platform switches dispatcher itself, the way
 * `ContentResolverEventArchive` does rather than making every call site
 * remember.
 */
interface ProjectionListener {

    suspend fun onProjectionChanged()
}
