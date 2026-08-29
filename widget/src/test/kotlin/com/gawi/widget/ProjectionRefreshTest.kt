package com.gawi.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Every provider this module declares is one a committed write redraws.
 *
 * **The failure this rules out is invisible.** [GlanceProjectionListener] is what
 * starts a Glance session when none is alive, which is the common case because
 * sessions are short. A provider missing from [refreshedWidgets] still renders —
 * it just stops following writes made in the app for the life of a session, so a
 * habit ticked on the Today screen leaves the widget showing the old number. That
 * looks exactly like a widget nobody placed, and
 * docs/ux/visual-identity.md §7.4 costed a second widget without naming it.
 *
 * **Why the manifest is the source of truth here rather than a list in this
 * file.** Pinning `refreshedWidgets()` against a hand-written expected set would
 * pass forever: a third provider would be added to the manifest, to a receiver,
 * and to neither list. Reading the receivers the manifest actually declares is
 * what makes forgetting fail — the test grows itself.
 */
@RunWith(RobolectricTestRunner::class)
class ProjectionRefreshTest {

    private val context = RuntimeEnvironment.getApplication()

    /** Every `GlanceAppWidgetReceiver` the module's manifest declares. */
    private fun declaredWidgets(): List<Class<*>> {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
        return context.packageManager
            .queryBroadcastReceivers(intent, PackageManager.MATCH_ALL)
            .map { Class.forName(it.activityInfo.name) }
            .filter { GlanceAppWidgetReceiver::class.java.isAssignableFrom(it) }
    }

    /** If this ever finds nothing, every assertion below is vacuous. */
    @Test
    fun `the manifest declares the providers this module ships`() {
        val declared = declaredWidgets()
        assertTrue("no GlanceAppWidgetReceiver found in the merged manifest", declared.isNotEmpty())
        assertTrue("TodayWidgetReceiver is not declared", declared.contains(TodayWidgetReceiver::class.java))
        assertTrue("StreakWidgetReceiver is not declared", declared.contains(StreakWidgetReceiver::class.java))
    }

    /**
     * The coupling itself: whatever the manifest declares, the push covers.
     *
     * Compared by the `GlanceAppWidget` class each receiver serves, because that
     * is what `updateAll` is called on — a receiver and its widget are different
     * objects and only the second one is refreshable.
     */
    @Test
    fun `every declared provider is refreshed after a committed write`() {
        val served = declaredWidgets()
            .map { (it.getDeclaredConstructor().newInstance() as GlanceAppWidgetReceiver).glanceAppWidget.javaClass }
            .toSet()
        val refreshed = refreshedWidgets().map { it.javaClass }.toSet()

        assertEquals("a declared provider is not in refreshedWidgets(), so it will freeze", served, refreshed)
    }

    /** `updateAll` on the same widget twice is wasted work on every committed write. */
    @Test
    fun `no provider is refreshed twice`() {
        val classes = refreshedWidgets().map { it.javaClass }
        assertEquals(classes.toSet().size, classes.size)
    }

    /**
     * Fresh instances, not shared ones. A `GlanceAppWidget` reaches Glance's
     * session machinery from its own constructor, which is why the tap path
     * constructs one for `update` rather than holding it.
     */
    @Test
    fun `each call hands back new instances`() {
        val first = refreshedWidgets()
        val second = refreshedWidgets()
        for ((a, b) in first.zip(second)) {
            assertTrue("${a.javaClass.simpleName} is shared between calls", a !== b)
        }
    }
}
