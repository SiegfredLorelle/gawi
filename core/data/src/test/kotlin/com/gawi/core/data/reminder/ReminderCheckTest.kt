package com.gawi.core.data.reminder

import com.gawi.core.data.model.TodayHabit
import com.gawi.core.data.model.TodaySnapshot
import com.gawi.core.data.repository.HabitRepository
import com.gawi.core.data.settings.UserSettings
import com.gawi.core.data.settings.settingsDataStore
import com.gawi.core.data.testsupport.FakeDeviceClock
import com.gawi.core.data.testsupport.FakeSettingsSource
import com.gawi.core.data.testsupport.TestStore
import com.gawi.core.domain.command.CommandResult
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.projection.HabitMetadata
import com.gawi.core.domain.projection.HabitState
import com.gawi.core.domain.streak.StreakSnapshot
import com.gawi.core.domain.testing.habitId
import com.gawi.core.domain.testing.metadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * PRD §6.1.5 — *"one reminder max per day; silent when all done"* — as assertions,
 * plus the two things that decide when the wakes happen.
 *
 * On the JVM with a clock the test moves by hand, which is the only way to state
 * any of this: every claim here is about an instant, and none of them is reachable
 * from a device inside a working day. The real repository, the real journal and a
 * real preferences file are all in play — only the clock and the settings are
 * fakes, so a rule that lived in the wrong layer would still be exercised.
 *
 * `ReminderSchedulerTest` in `:app` covers the other half: that these answers
 * reach WorkManager intact.
 */
@RunWith(RobolectricTestRunner::class)
class ReminderCheckTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var store: TestStore

    // Guarded, because one test injects a fake repository and never builds a store.
    @After
    fun tearDown() {
        if (::store.isInitialized) store.close()
    }

    /**
     * A check over a real repository, a real journal and a real preferences file.
     *
     * The store runs on `backgroundScope` so its reader dies with the test, and on
     * a file DataStore creates itself — a pre-created empty one would be testing
     * the corruption path by accident, which is `DataStoreSettingsSourceTest`'s
     * note and applies just as much here.
     */
    private fun TestScope.check(
        clock: FakeDeviceClock = FakeDeviceClock(),
        settings: FakeSettingsSource = FakeSettingsSource(),
        name: String = "reminder",
    ): ReminderCheck {
        store = TestStore.create(clock = clock, settings = settings)
        val dataStore = settingsDataStore(scope = backgroundScope) { File(folder.root, "$name.preferences_pb") }
        return ReminderCheck(store.repository, settings, clock, ReminderJournal(dataStore))
    }

    /**
     * A check over a snapshot this test wrote, rather than one a query produced.
     *
     * Only [HabitRepository.observeToday] is implemented; everything else `error`s,
     * following the shape `DataStoreSettingsSourceTest`'s `throwing` store uses — a
     * fake that answers more than the test needs is a fake that can drift.
     */
    private fun TestScope.checkOver(snapshot: TodaySnapshot): ReminderCheck {
        val settings = FakeSettingsSource(UserSettings(dayCutoff = snapshot.dayCutoff, reminderTime = snapshot.reminderTime))
        val dataStore = settingsDataStore(scope = backgroundScope) { File(folder.root, "injected.preferences_pb") }
        val repository = object : HabitRepository {
            override fun observeToday(): Flow<TodaySnapshot> = flowOf(snapshot)

            override suspend fun createHabit(metadata: HabitMetadata) = error("not used")

            override suspend fun updateHabit(habitId: HabitId, metadata: HabitMetadata) = error("not used")

            override suspend fun archiveHabit(habitId: HabitId) = error("not used")

            override suspend fun unarchiveHabit(habitId: HabitId) = error("not used")

            override suspend fun addCompletion(habitId: HabitId, logicalDate: LocalDate, note: String?) = error("not used")

            override suspend fun undoCompletion(habitId: HabitId, logicalDate: LocalDate) = error("not used")

            override suspend fun updateNote(habitId: HabitId, logicalDate: LocalDate, text: String) = error("not used")

            override fun observeAllHabits() = error("not used")

            override fun observeHabit(habitId: HabitId) = error("not used")

            override fun observeHabitDetail(habitId: HabitId) = error("not used")

            override fun observeCompletedDates(habitId: HabitId, from: LocalDate, to: LocalDate) = error("not used")

            override fun observeCompletionDatesByHabit(from: LocalDate, to: LocalDate) = error("not used")

            override fun observeReadContext() = error("not used")

            override fun observeTagEffort(from: LocalDate, to: LocalDate) = error("not used")

            override suspend fun refreshStreaks() = error("not used")

            override suspend fun rebuildProjections() = error("not used")
        }
        return ReminderCheck(repository, settings, FakeDeviceClock(), ReminderJournal(dataStore))
    }

    /** An incomplete daily habit, archived or not — outstanding unless filtered. */
    private fun row(id: HabitId, archived: Boolean) = TodayHabit(
        habit = HabitState(id, "read", "book", "#aabbcc", Schedule.Daily, tag = null, archived = archived, createdOn = null),
        completedToday = false,
        note = null,
        weekCount = 0,
        streak = StreakSnapshot.NONE,
    )

    private suspend fun createHabit(name: String = "read", schedule: Schedule = Schedule.Daily): HabitId =
        (store.repository.createHabit(metadata(name, schedule)) as CommandResult.Accepted).payload

    /** The default clock sits at 09:00 UTC; the default reminder is 21:00. */
    private fun evening() = FakeDeviceClock(Instant.parse("2026-08-17T21:30:00Z"))

    @Test
    fun `nothing is said before the reminder time, even with a habit outstanding`() = runTest {
        val check = check()
        createHabit()

        assertEquals(ReminderDecision.Silent, check.evaluate())
    }

    @Test
    fun `an outstanding habit is reported once the reminder time has passed`() = runTest {
        val check = check(clock = evening())
        createHabit()

        assertEquals(ReminderDecision.Remind(outstanding = 1, total = 1), check.evaluate())
    }

    /** PRD §6.1.5's *"silent when all done"*. */
    @Test
    fun `nothing is said when every habit is done`() = runTest {
        val clock = evening()
        val check = check(clock = clock)
        val habit = createHabit()
        store.repository.addCompletion(habit, store.today())

        assertEquals(ReminderDecision.Silent, check.evaluate())
    }

    /** PRD §6.1.5's *"one reminder max per day"*, which is the headline criterion. */
    @Test
    fun `a second evaluation on the same day says nothing`() = runTest {
        val check = check(clock = evening())
        createHabit()

        assertEquals(ReminderDecision.Remind(outstanding = 1, total = 1), check.evaluate())
        assertEquals(ReminderDecision.Silent, check.evaluate())
    }

    @Test
    fun `the next day reminds again`() = runTest {
        val clock = evening()
        val check = check(clock = clock)
        createHabit()

        assertEquals(ReminderDecision.Remind(outstanding = 1, total = 1), check.evaluate())
        clock.advanceDays(1)

        assertEquals(ReminderDecision.Remind(outstanding = 1, total = 1), check.evaluate())
    }

    /**
     * Silence must not consume the day.
     *
     * The ordering rule in `evaluate`: a day with nothing outstanding is not a day
     * that has been reminded, so a habit created later that same evening still
     * gets one. Stamping on the silent path would pass every other test here and
     * lose a real reminder in the field.
     */
    @Test
    fun `being silent does not use up the day's one reminder`() = runTest {
        val check = check(clock = evening())

        assertEquals(ReminderDecision.Silent, check.evaluate())
        createHabit()

        assertEquals(ReminderDecision.Remind(outstanding = 1, total = 1), check.evaluate())
    }

    /**
     * The deferred wake, which is the failure this guard exists for.
     *
     * A wake pushed past the day cutoff by Doze or a powered-off device arrives
     * inside the *next* logical day, where every habit is legitimately incomplete.
     * Without the threshold check it would post "1 of 1 left today" at 00:30 and
     * stamp the new day — so the reminder that evening, the real one, would be
     * suppressed by the one that fired by mistake.
     *
     * Measured: stubbing the guard out reddens **three** tests — this one, "nothing
     * is said before the reminder time", and the earlier-than-cutoff case. An
     * earlier version of this comment claimed it reddened only this one, which was
     * written rather than run. The three are worth having separately: this is the
     * *late* direction, and the other two are the early one, which is the same
     * comparison failing for the opposite reason.
     */
    @Test
    fun `a wake deferred past the day boundary says nothing`() = runTest {
        val check = check(clock = FakeDeviceClock(Instant.parse("2026-08-18T00:30:00Z")))
        createHabit()

        assertEquals(ReminderDecision.Silent, check.evaluate())
    }

    /** A minute of earliness is jitter, not a drifted schedule. */
    @Test
    fun `a wake a minute early still reports`() = runTest {
        val check = check(clock = FakeDeviceClock(Instant.parse("2026-08-17T20:59:30Z")))
        createHabit()

        assertEquals(ReminderDecision.Remind(outstanding = 1, total = 1), check.evaluate())
    }

    /**
     * A weekly habit is not outstanding while the week can still absorb it —
     * `Mascot.isOutstanding`'s rule, reached rather than re-implemented. A second
     * copy of it here is how the notification and the Today view's chip would come
     * to disagree about the same evening.
     *
     * 2026-08-17 is a Monday, so a three-times-a-week habit has six days left after
     * today and nothing is owed yet.
     */
    @Test
    fun `a weekly habit the week can still absorb is not outstanding`() = runTest {
        val check = check(clock = evening())
        createHabit("swim", Schedule.Weekly(3))

        assertEquals(ReminderDecision.Silent, check.evaluate())
    }

    @Test
    fun `the total counts every habit, not only the outstanding ones`() = runTest {
        val clock = evening()
        val check = check(clock = clock)
        createHabit("read")
        val done = createHabit("swim")
        store.repository.addCompletion(done, store.today())

        assertEquals(ReminderDecision.Remind(outstanding = 1, total = 2), check.evaluate())
    }

    /**
     * The case a same-date comparison gets backwards, and the reason `reminderOn`
     * is shared rather than copied.
     *
     * With an 03:00 cutoff and a 01:30 reminder, the reminder for the logical 17th
     * falls at 01:30 on the **18th** — 22.5 hours into that logical day. So 01:00
     * on the 18th is before it and 01:45 is after, both of them on the same logical
     * date.
     */
    @Test
    fun `a reminder set earlier than the cutoff falls on the next calendar day`() = runTest {
        val settings = FakeSettingsSource(UserSettings(dayCutoff = LocalTime.of(3, 0), reminderTime = LocalTime.of(1, 30)))
        val clock = FakeDeviceClock(Instant.parse("2026-08-18T01:00:00Z"))
        val check = check(clock = clock, settings = settings)
        createHabit()

        assertEquals(LocalDate.parse("2026-08-17"), store.today())
        assertEquals(ReminderDecision.Silent, check.evaluate())

        clock.instant = Instant.parse("2026-08-18T01:45:00Z")
        assertEquals(ReminderDecision.Remind(outstanding = 1, total = 1), check.evaluate())
    }

    @Test
    fun `the next reminder is today's while it is still ahead`() = runTest {
        val check = check(clock = FakeDeviceClock(Instant.parse("2026-08-17T09:00:00Z")))

        assertEquals(Duration.ofHours(12), check.untilNextReminder())
    }

    /** Strictly ahead, so a worker woken exactly on the threshold looks to tomorrow. */
    @Test
    fun `the next reminder rolls to tomorrow once the threshold is reached`() = runTest {
        val check = check(clock = FakeDeviceClock(Instant.parse("2026-08-17T21:00:00Z")))

        assertEquals(Duration.ofHours(24), check.untilNextReminder())
    }

    @Test
    fun `the next reminder rolls to tomorrow once the threshold has passed`() = runTest {
        val check = check(clock = FakeDeviceClock(Instant.parse("2026-08-17T21:30:00Z")))

        assertEquals(Duration.ofHours(23).plusMinutes(30), check.untilNextReminder())
    }

    @Test
    fun `the next cutoff is the end of the current logical day`() = runTest {
        val check = check(clock = FakeDeviceClock(Instant.parse("2026-08-17T21:30:00Z")))

        assertEquals(Duration.ofHours(2).plusMinutes(30), check.untilNextCutoff())
    }

    /**
     * A non-midnight cutoff, where "the end of today" is not "midnight tonight".
     *
     * With an 03:00 cutoff, 01:00 on the 18th is still the logical 17th, whose
     * boundary is 03:00 on the 18th — two hours away, not twenty-six.
     */
    @Test
    fun `the next cutoff follows the configured boundary rather than midnight`() = runTest {
        val settings = FakeSettingsSource(UserSettings(dayCutoff = LocalTime.of(3, 0)))
        val check = check(clock = FakeDeviceClock(Instant.parse("2026-08-18T01:00:00Z")), settings = settings)

        assertEquals(Duration.ofHours(2), check.untilNextCutoff())
    }

    /**
     * A reminder set equal to the day cutoff is refused, not honoured.
     *
     * `reminderOn` resolves that pair to the logical day's **start**, so without
     * the guard the wake at the top of every logical day finds nothing completed
     * yet, posts "N of N left today", and stamps the day — which silences the
     * evening as well. `:feature:settings` refuses the combination now; this is
     * what protects a value an older build already stored. Found by /code-review.
     */
    @Test
    fun `a reminder equal to the day cutoff says nothing at the day's start`() = runTest {
        val settings = FakeSettingsSource(UserSettings(dayCutoff = LocalTime.of(3, 0), reminderTime = LocalTime.of(3, 0)))
        val check = check(clock = FakeDeviceClock(Instant.parse("2026-08-17T03:00:00Z")), settings = settings)
        createHabit()

        assertEquals(ReminderDecision.Silent, check.evaluate())
    }

    /** And it stays silent later in that day too, rather than merely being early. */
    @Test
    fun `a reminder equal to the day cutoff says nothing all day`() = runTest {
        val settings = FakeSettingsSource(UserSettings(dayCutoff = LocalTime.of(3, 0), reminderTime = LocalTime.of(3, 0)))
        val check = check(clock = FakeDeviceClock(Instant.parse("2026-08-17T21:00:00Z")), settings = settings)
        createHabit()

        assertEquals(ReminderDecision.Silent, check.evaluate())
    }

    /**
     * The DST fall-back hour, where "the boundary of today" is behind us.
     *
     * `logicalDate` documents this anomaly and accepts it: a cutoff strictly
     * inside a repeated hour makes today regress to yesterday for the rewound
     * stretch. Europe/London goes 02:00 -> 01:00 on 2026-10-25, so with a 01:30
     * cutoff the second pass through 01:15 resolves to the 24th, whose boundary is
     * 01:30 on the 25th at the *earlier* offset — already past. An earlier KDoc
     * claimed this could not happen. Found by /code-review.
     */
    @Test
    fun `the next cutoff is never in the past across a DST fall-back`() = runTest {
        val settings = FakeSettingsSource(UserSettings(dayCutoff = LocalTime.of(1, 30)))
        val london = ZoneId.of("Europe/London")
        // 01:15 GMT is the *second* pass through 01:15 local, after the rewind.
        val clock = FakeDeviceClock(Instant.parse("2026-10-25T01:15:00Z"), london)
        val check = check(clock = clock, settings = settings)

        val until = check.untilNextCutoff()

        assert(!until.isNegative) { "armed a wake $until in the past" }
    }

    /**
     * Archived habits count towards neither figure — and this needs a **fake
     * repository**, which is the interesting part.
     *
     * `Mascot.isOutstanding` does not check `archived` the way `Mascot.mood` does,
     * so `ReminderCheck` filters for itself rather than trusting the query, the
     * same call `TodayUiMapper` makes and documents. Found by /code-review.
     *
     * The guard is **unreachable through the real repository**: `observeToday`'s SQL
     * has `WHERE h.archived = 0`, so a real snapshot can never carry an archived
     * row, and a test built on `TestStore` passes identically with the filter
     * deleted — measured, which is how the first version of this test was caught
     * being vacuous. That is exactly what makes the finding latent rather than
     * live: correct today, wrong the day that query changes.
     *
     * So the snapshot is injected instead. This is the only place in the suite that
     * fakes the repository, and it earns it: nothing else can put the class in the
     * state the guard exists for.
     */
    @Test
    fun `an archived habit counts towards neither the outstanding nor the total`() = runTest {
        val today = LocalDate.parse("2026-08-17")
        val snapshot = TodaySnapshot(
            habits = listOf(row(habitId(1), archived = false), row(habitId(2), archived = true)),
            today = today,
            now = today.atTime(21, 30),
            reminderTime = LocalTime.of(21, 0),
            dayCutoff = LocalTime.MIDNIGHT,
            weekStart = DayOfWeek.MONDAY,
        )
        val check = checkOver(snapshot)

        assertEquals(ReminderDecision.Remind(outstanding = 1, total = 1), check.evaluate())
    }

    /** Both wakes are always in the future, which is what keeps a delay non-negative. */
    @Test
    fun `neither wake is ever in the past`() = runTest {
        val clock = FakeDeviceClock(Instant.parse("2026-08-17T00:00:00Z"))
        val check = check(clock = clock)

        repeat(HOURS_IN_A_DAY) {
            assert(!check.untilNextReminder().isNegative) { "reminder went backwards at ${clock.now()}" }
            assert(!check.untilNextCutoff().isNegative) { "cutoff went backwards at ${clock.now()}" }
            clock.instant = clock.instant.plus(Duration.ofHours(1))
        }
    }

    private companion object {
        const val HOURS_IN_A_DAY = 24
    }
}
