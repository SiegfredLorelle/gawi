package com.gawi.core.data.projection

/**
 * Told that the derived tables have changed, once per committed write.
 *
 * This exists because **Glance widgets do not observe Room** (architecture §4).
 * An open screen re-reads by itself, because its `Flow` query is invalidated by
 * Room's `InvalidationTracker` — the only data-change notifier in this app. A
 * widget is not a subscriber, so the push has to be explicit.
 *
 * **An interface here rather than a Glance call in the repository**, because
 * calling Glance from `:core:data` would invert the module rule
 * (`widget → core`, §2). So the knowledge is split: this layer knows *when* the
 * read model moved, `:widget` knows what to do about it. `:core:data` declares no
 * binding, `:widget` provides one, and `:app` depends on `:widget` so the graph
 * closes — the route `DataModule` already takes to reach `:app` while staying
 * `internal` here.
 *
 * **One required binding, not a `@Multibinds` set.** An empty set is a legal
 * graph, so a missing contribution would be a widget that quietly stops
 * updating; a missing single binding is a graph failure, which `:app`'s
 * navigation test catches by being the only test of the production Hilt graph
 * (architecture §8). Accepted cost: `:core:data` can no longer be assembled into
 * an application that has no listener.
 *
 * **Called from `appendLocked` (every command) and `mergeLocked` (an import)**,
 * the two paths that put new events in the log. Not from `rebuildProjections`,
 * which replays the same log into the same tables; not from `sweepStreaks`,
 * because a streak is the one thing the widget deliberately does not show (PRD
 * OQ-5) — if that is ever reversed, this is the omission to revisit first.
 *
 * **What it cannot cover, because neither is an event.** A day rollover commits
 * nothing at the cutoff, and neither does a settings edit — the second matters
 * more, because the day cutoff is what the logical date derives from, so moving
 * it changes every `completedToday` without writing anything. A consumer that
 * needs to follow either must observe for itself rather than wait to be told,
 * which is what the widget does by collecting `observeToday()` inside its
 * content; this push is what starts it when nothing is listening.
 *
 * **Implementations must be main-safe, and are called under the command mutex.**
 * The caller's dispatcher is whatever tapped — `viewModelScope` is
 * `Main.immediate` — so an implementation that touches the platform switches
 * dispatcher itself, the way `ContentResolverEventArchive` does rather than
 * making every call site remember. Being inside the mutex and inside a
 * `NonCancellable` region, work here delays the next command and cannot be
 * cancelled out of; the Glance implementation awaits a DataStore read and a
 * WorkManager enqueue, which is not free. That is accepted rather than
 * overlooked — see the call site in `OfflineFirstHabitRepository` for why moving
 * it outside the lock costs more than it saves.
 */
interface ProjectionListener {

    suspend fun onProjectionChanged()
}
