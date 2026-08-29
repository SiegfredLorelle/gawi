package com.gawi.app

import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gawi.app.reminder.WorkerEntryPoint
import com.gawi.app.testsupport.WidgetHostBinding
import com.gawi.core.data.repository.HabitRepository
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.projection.HabitMetadata
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The streak widget bound to a real host, rendered by real Glance.
 *
 * **What only this can prove, and why the module's 127 JVM tests cannot.** Those
 * compose the Glance tree in-process; nothing there translates it to
 * `RemoteViews`. This widget's tree is the first in the app to put a `LazyColumn`
 * carrying `defaultWeight` under a `Column` with a sibling after it, and a
 * translation failure there would be a shipping bug that composes perfectly and
 * renders nothing — exactly the shape no JVM test here can see. What it reaches
 * is the *structure*: the collection, and the footer outside it. The lazy items'
 * own content it cannot reach, and
 * [theRowsBodyTranslatesToRemoteViewsOnARealHost] records why.
 *
 * **It is also the only place the `res/xml-v31` attributes can be read back.**
 * `dumpsys appwidget` does not print `targetCellWidth`, `description` or
 * `previewLayout`, so a `-v31` variant that failed to resolve looks identical to
 * one that worked. `AppWidgetProviderInfo` is where the framework's parse
 * surfaces.
 *
 * **Not a substitute for placing it on a launcher.** Pinning needs the user, so
 * `docs/running.md` §4's checks stay owed — in particular anything about how an
 * OEM launcher's cells, theme or process behave.
 */
@RunWith(AndroidJUnit4::class)
class StreakWidgetHostTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private var widget: WidgetHostBinding? = null

    /**
     * The real repository, off `:app`'s own entry point.
     *
     * `internal` to `:app` and reachable here because AGP makes an androidTest a
     * friend of the variant it tests. Using the app's entry point rather than
     * adding Hilt to this source set: the graph is the real singleton one either
     * way, and the alternative is annotation processing in a source set that has
     * none.
     */
    private val habits: HabitRepository
        get() = EntryPointAccessors.fromApplication(context, WorkerEntryPoint::class.java).habitRepository()

    /**
     * Seed a habit **before** binding, so the widget's rows body is what renders.
     *
     * Not optional, and the first device run is why. With no habits the widget
     * draws the empty *copy* — a single string — so every assertion here passed
     * while the rows body, the one tree shape this test exists to exercise, was
     * never translated at all. A widget's content also depends on whatever the
     * device happened to hold, which makes an ambient-data test a coin toss.
     */
    @Before
    fun seedAndBind() {
        runBlocking {
            habits.createHabit(
                HabitMetadata(
                    name = SEEDED_HABIT,
                    icon = "book",
                    color = "#1F6F78",
                    schedule = Schedule.Daily,
                    tag = null,
                ),
            )
        }
        widget = WidgetHostBinding.bind(context, WidgetHostBinding.STREAK_RECEIVER)
    }

    @After
    fun releaseWidget() {
        widget?.release()
    }

    @Test
    fun glanceRendersTheStreakWidgetForARealHost() {
        val bound = widget
        assertNotNull(
            "StreakWidgetReceiver is missing from the merged manifest, or `appwidget grantbind` was refused",
            bound,
        )

        val rendered = bound!!.awaitGlanceContent()

        assertTrue(
            "Glance produced none of this widget's own strings within ${TIMEOUT_SECONDS}s. " +
                "Rendered: $rendered",
            rendered.any(::isGlanceContent),
        )

        // "It drew something" is not enough — the read-failure copy is text too,
        // and a throwing observeToday() would turn this green. Same argument as
        // WidgetHostTest, and the same reason strings.xml keeps the two apart.
        assertFalse(
            "Glance rendered the read-failure state: $rendered",
            stringByName("widget_unavailable") in rendered,
        )

        println("GAWI_STREAK rendered=$rendered")
    }

    /**
     * The rows body reaches a real host as `RemoteViews`: a collection, with the
     * footer pinned outside it.
     *
     * **The one thing no JVM test in this repo can see.** `:widget`'s unit tests
     * compose the Glance tree; none of them translates it. This widget's rows are
     * the first tree in the app to put a `LazyColumn` carrying `defaultWeight`
     * under a `Column` with a sibling after it, and a translation failure there
     * composes perfectly and renders nothing.
     *
     * Two assertions, and between them they pin the structure the JVM tests
     * cannot: the dated line proves the rows body was selected at all (a `Copy`
     * body draws no date) *and* that the footer outside the collection survived,
     * and the collection view proves the `LazyColumn` became one rather than
     * being dropped.
     *
     * **What this cannot reach, measured rather than assumed.** The row *content*
     * is not observable here. A `LazyColumn` becomes a `RemoteViews` collection,
     * whose item views the host materialises through an adapter when it lays out
     * — and `createView` is never attached to a window, so it never lays out and
     * the adapter is never asked. The first device run made that concrete: with a
     * habit seeded, the tree came back
     * `[AppWidgetHostView, FrameLayout, LinearLayout, ListView, ImageView]` — the
     * `ListView` present and childless, the `ImageView` being the date. So the
     * habit name a row would draw stays a by-hand check in docs/running.md §4.
     *
     * One consequence worth knowing before reading a failure here: the rendered
     * text always contains the provider's `android:label`, because
     * `AppWidgetHostView` carries it as its own content description. That is why
     * [awaitGlanceContent] waits on this widget's own strings instead of on the
     * tree being non-empty — waiting on non-empty returns before Glance has run.
     */
    @Test
    fun theRowsBodyTranslatesToRemoteViewsOnARealHost() {
        val rendered = widget!!.awaitGlanceContent()
        val classes = widget!!.viewClasses()
        println("GAWI_STREAK rows=$rendered classes=$classes")

        assertTrue(
            "the rows body did not reach the host, or its pinned \"$AS_OF_PREFIX\" footer did not: $rendered",
            rendered.any { it.startsWith(AS_OF_PREFIX) },
        )
        assertTrue(
            "the LazyColumn did not translate to a RemoteViews collection: $classes",
            classes.any { it in COLLECTION_VIEWS },
        )
    }

    /**
     * The provenance line docs/ux/visual-identity.md §7.1 makes mandatory,
     * asserted on a real host rather than on a composed tree.
     *
     * Only when there is something to date: with no habits the widget draws the
     * empty copy and no date, which is deliberate (there is no number to date),
     * so this asserts the pair rather than the line alone.
     */
    @Test
    fun theRenderedWidgetEitherDatesItsNumbersOrHasNone() {
        val rendered = widget!!.awaitGlanceContent()
        val empty = stringByName("widget_no_habits")
        val dated = rendered.any { it.startsWith(AS_OF_PREFIX) }

        assertTrue(
            "no \"$AS_OF_PREFIX\" line and not the empty state either: $rendered",
            dated || empty in rendered,
        )
        println("GAWI_STREAK dated=$dated rendered=$rendered")
    }

    /**
     * The API 31 attributes actually resolved from `res/xml-v31`.
     *
     * **Below 31 there is nothing here to assert, and that is the finding rather
     * than a gap.** `targetCellWidth`, `descriptionRes` and `previewLayout` were
     * added to `AppWidgetProviderInfo` in API 31 — they are not zero on an older
     * platform, they *do not exist*, and touching one throws
     * `NoSuchFieldError`. Measured: an earlier version of this test asserted they
     * were `0` in the `else` branch and died exactly that way on API 30. Which is
     * the whole reason the provider xml is split in the first place — the base
     * file cannot mention attributes the platform has never heard of.
     *
     * So below 31 this asserts what the base file *can* be checked for: that it
     * parsed and carries the dimensions it declares. `lint` would normally catch
     * a bare API 31 field access, but AGP does not check androidTest sources by
     * default, so this one is on the author.
     */
    @Test
    fun theApi31AttributesResolveFromTheV31Variant() {
        val info = widget!!.info
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assertEquals("targetCellWidth did not resolve from res/xml-v31", 3, info.targetCellWidth)
            assertEquals("targetCellHeight did not resolve from res/xml-v31", 2, info.targetCellHeight)
            assertNotEquals("description did not resolve from res/xml-v31", 0, info.descriptionRes)
            assertNotEquals("previewLayout did not resolve from res/xml-v31", 0, info.previewLayout)
            // The description is what the picker shows under the name, so an id
            // that resolves to nothing is the same defect one step later.
            assertTrue(
                "the description resource is empty",
                context.getString(info.descriptionRes).isNotBlank(),
            )
            println(
                "GAWI_STREAK sdk=${Build.VERSION.SDK_INT} " +
                    "cells=${info.targetCellWidth}x${info.targetCellHeight} preview=${info.previewLayout}",
            )
        } else {
            assertTrue("the base provider xml declared no minWidth", info.minWidth > 0)
            assertTrue("the base provider xml declared no minHeight", info.minHeight > 0)
            assertNotEquals("the base provider xml declared no initialLayout", 0, info.initialLayout)
            println("GAWI_STREAK sdk=${Build.VERSION.SDK_INT} min=${info.minWidth}x${info.minHeight}")
        }
    }

    /**
     * Wait for a string only **this widget's own composition** can have produced.
     *
     * **Not `isNotEmpty()`, and the difference is a false pass this test already
     * had.** `AppWidgetHostView` carries a content description of its own, taken
     * from the provider's `android:label` — so the rendered set contains
     * `"Streaks"` from the instant `createView` returns, before Glance has run at
     * all. Waiting on "not empty" therefore returned immediately and asserted
     * against the label, which would have stayed green with the composition
     * completely broken. Found on the first device run, by the sibling test
     * failing where this one passed.
     *
     * So the predicate names the three things the widget can actually draw: the
     * dated line, the empty copy, or the failure copy. Anything else means Glance
     * has not finished, and a timeout here is a real finding rather than a flake.
     */
    private fun WidgetHostBinding.awaitGlanceContent(): List<String> = awaitText(TIMEOUT_SECONDS) { text -> text.any(::isGlanceContent) }

    /** One of the three strings this widget's composition can emit. */
    private fun isGlanceContent(line: String): Boolean = line.startsWith(AS_OF_PREFIX) ||
        line == stringByName("widget_no_habits") ||
        line == stringByName("widget_unavailable")

    /** `:app` cannot name `:widget`'s R class — non-transitive R classes — but names resolve at runtime. */
    private fun stringByName(name: String): String = context.getString(context.resources.getIdentifier(name, "string", context.packageName))

    private companion object {
        const val TIMEOUT_SECONDS = 20L

        /** Seeded so the rows body is what renders; see [seedAndBind]. */
        const val SEEDED_HABIT = "Widget host probe"

        /** What Glance translates a `LazyColumn` into, by platform version. */
        val COLLECTION_VIEWS = setOf("ListView", "AdapterView", "StackView")

        /** Matches `widget_streak_as_of`'s literal prefix, which is not a format argument. */
        const val AS_OF_PREFIX = "as of "
    }
}
