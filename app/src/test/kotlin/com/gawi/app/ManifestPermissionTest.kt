package com.gawi.app

import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The PRD's central privacy claim, as a test.
 *
 * PRD §5 and §7 say **"no network permission at MVP (verifiable privacy
 * claim)"** and architecture §1 says the MVP APK declares none. Until now that
 * was verified by reading the hand-written manifest — which is not where the
 * answer lives. A permission can arrive from any library's manifest through the
 * merge, and this reads what the *merged* manifest requests, which is what ships.
 *
 * It is not hypothetical. Jetpack Glance has a **hard runtime dependency on
 * WorkManager** — it runs its composition session in a `CoroutineWorker`, so
 * excluding `androidx.work` compiles and then dies with `NoClassDefFoundError`
 * the first time a widget object is constructed (measured on a device; every
 * Glance version through 1.3.0-alpha02 declares it). WorkManager's manifest
 * brings four permissions, and one of them is `ACCESS_NETWORK_STATE`.
 *
 * `:app`'s manifest removes that one with `tools:node="remove"`, because
 * WorkManager wants it only to evaluate network *constraints* on work requests
 * and nothing here has one. The other three are kept, because WorkManager
 * genuinely wakes and reschedules and a manifest that hid that would be lying.
 * None of the three can move data off the device, and `INTERNET` is absent, so
 * the app cannot open a socket at all — which is the property PRD §5 is really
 * claiming.
 *
 * The end-of-day reminder uses WorkManager on purpose, and disturbs none of
 * that: the two `WorkRequest`s it arms carry no constraints at all, which
 * `ReminderScheduler.enqueue` records as a rule. If one
 * ever takes a network constraint, the `tools:node="remove"` line is what has to
 * go first, and this test is what will say so.
 *
 * **Two measurements were taken separately when the reminder landed, and the
 * order mattered.** WorkManager was pinned first, on its own — `:widget` takes
 * Glance on `implementation`, so `:app` had to declare `work-runtime` to compile a
 * worker at all, which meant choosing a version where Glance's transitive 2.7.1
 * had been the silent default. 2.11.2 was measured *before* `POST_NOTIFICATIONS`
 * was added, and changed this set by nothing. Then the permission went in and this
 * assertion **failed**, naming exactly one addition. That failure is the reason
 * the first result is worth anything: a check that has only ever returned "clean"
 * has not been shown able to return "dirty", which is the mistake the widget step
 * made with a broken grep and is recorded in docs/ux/widget.md §5.
 */
@RunWith(RobolectricTestRunner::class)
class ManifestPermissionTest {

    private fun requestedPermissions(): List<String> {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        return info.requestedPermissions?.toList() ?: emptyList()
    }

    @Test
    fun `no network permission is requested`() {
        val network = setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_NETWORK_STATE",
            "android.permission.CHANGE_WIFI_STATE",
        )

        assertEquals(emptyList<String>(), requestedPermissions().filter { it in network })
    }

    /**
     * The narrower claim, stated separately because it is the one that cannot be
     * traded away. Without `INTERNET` the process cannot open a socket, whatever
     * else it is permitted to read.
     */
    @Test
    fun `the app cannot open a socket`() {
        assertTrue(!requestedPermissions().contains("android.permission.INTERNET"))
    }

    /**
     * The exact set, so *any* new permission fails this and has to be argued for
     * rather than noticed later.
     *
     * Compared **unordered**, deliberately. The order of `requestedPermissions`
     * comes out of the manifest merger and is a function of dependency ordering,
     * which nothing in this repo states — so a Glance, WorkManager or AGP bump
     * that reordered the merge without adding or removing anything would fail
     * this and read as "the permission set changed" when it had not. The KDoc's
     * intent survives comparison as a set, and docs/ux/widget.md §5 nominates
     * this assertion as the decision point for the reminder step, so it has to
     * fail only for real reasons.
     *
     * Three arrive with WorkManager, which Glance requires. The fourth is
     * androidx.core's own signature-level permission for a non-exported dynamic
     * receiver — it predates the widget, which also makes
     * `AndroidManifest.xml`'s old "deliberately no `<uses-permission>`" comment
     * literally false all along; it now says what is true instead.
     *
     * The fifth, `POST_NOTIFICATIONS`, is the **only one the app declares by
     * hand**, and the first runtime permission it has ever asked for. The
     * end-of-day reminder is a notification (PRD §4), so there is no version of
     * that feature without it. It grants nothing but a row in the user's own
     * shade, and it cannot move data off the device — which is the property this
     * whole class is really about, and which `INTERNET`'s absence still decides.
     */
    @Test
    fun `the requested permission set is exactly the five that are argued for`() {
        assertEquals(
            setOf(
                "android.permission.WAKE_LOCK",
                "android.permission.RECEIVE_BOOT_COMPLETED",
                "android.permission.FOREGROUND_SERVICE",
                "com.gawi.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
                "android.permission.POST_NOTIFICATIONS",
            ),
            requestedPermissions().toSet(),
        )
    }

    /**
     * Exactly one requested permission is outside the set the libraries contribute.
     *
     * **This used to be called "the app declares one permission of its own", and
     * that name claimed more than it can check.** `requestedPermissions` reads the
     * *merged* manifest, which records what is requested and not which file asked
     * for it — so a permission hand-declared in
     * `app/src/main/AndroidManifest.xml` that happened to be one of the four below
     * would pass, and the name would have been a lie. Provenance is not available
     * to a unit test; the manifest merger's blame report is a build artefact, not a
     * runtime one. Raised in PR review.
     *
     * Renamed rather than deleted, because the weaker claim is still worth pinning
     * and is not the same as the exact-set assertion above: it says the *shape* of
     * the set is four-plus-one, so a fifth library permission arriving would fail
     * here with a name that points at the cause, rather than only failing the
     * exact-set test with a diff to read.
     */
    @Test
    fun `exactly one requested permission is not contributed by a library`() {
        val fromLibraries = setOf(
            "android.permission.WAKE_LOCK",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.FOREGROUND_SERVICE",
            "com.gawi.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        )

        assertEquals(
            listOf("android.permission.POST_NOTIFICATIONS"),
            requestedPermissions().filterNot { it in fromLibraries },
        )
    }

    /**
     * WorkManager **is** on the classpath, and that is the point of the test.
     *
     * It reads as a nit alone and is not: it names the mechanism behind the set
     * above. Glance cannot run without it, so the permissions are not an
     * accident to be cleaned up — they are a consequence to be argued with, and
     * the argument is the `tools:node="remove"` line plus the three that stay.
     */
    @Test
    fun `WorkManager is on the classpath, because Glance requires it`() {
        val present = runCatching { Class.forName("androidx.work.CoroutineWorker") }.isSuccess

        assertTrue("Glance's SessionWorker extends CoroutineWorker; excluding androidx.work crashes at runtime", present)
    }
}
