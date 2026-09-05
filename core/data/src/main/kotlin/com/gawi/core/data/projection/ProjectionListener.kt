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
 * **Fired on the paths that put new events in the log**, and deliberately not
 * on two others: not on `rebuildProjections`, which replays the same log into
 * the same tables; not on `sweepStreaks`, because a streak is the one thing the
 * widget deliberately does not show (PRD OQ-5) — if that is ever reversed, this
 * is the omission to revisit first.
 *
 * **Neither a day rollover nor a settings edit is an event, so the log alone
 * cannot push for either.** A rollover writes nothing at the cutoff yet every
 * `completedToday` has just changed answer, and moving the day cutoff does the
 * same without writing anything. A consumer that needs to follow either must
 * observe for itself rather than wait to be told, which is what the widget does
 * by collecting `observeToday()` inside its content; this push is what starts it
 * when nothing is listening. A *scheduled* wake turns both back into a push:
 * `:app`'s `RolloverWorker` sweeps the streaks and calls this by hand at the
 * boundary, and `ReminderScheduler` re-arms from a `SettingsSource` collector so
 * a cutoff edit moves the wake with it.
 *
 * **Implementations must be main-safe, and are called under the command mutex
 * inside a `NonCancellable` region.** The caller's dispatcher is whatever tapped
 * — `viewModelScope` is `Main.immediate` — so an implementation that touches the
 * platform switches dispatcher itself, the way `ContentResolverEventArchive`
 * does rather than making every call site remember. Work here delays the next
 * command and cannot be cancelled out of.
 */
interface ProjectionListener {

    suspend fun onProjectionChanged()
}
