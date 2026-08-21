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
 * The end-of-day reminder will use WorkManager on purpose. If a WorkRequest ever
 * takes a network constraint, the `tools:node="remove"` line is what has to go
 * first, and this test is what will say so.
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
     * Three arrive with WorkManager, which Glance requires. The fourth is
     * androidx.core's own signature-level permission for a non-exported dynamic
     * receiver — it predates the widget, which also makes
     * `AndroidManifest.xml`'s old "deliberately no `<uses-permission>`" comment
     * literally false all along; it now says what is true instead.
     */
    @Test
    fun `the requested permission set is exactly the four that are argued for`() {
        assertEquals(
            listOf(
                "android.permission.WAKE_LOCK",
                "android.permission.RECEIVE_BOOT_COMPLETED",
                "android.permission.FOREGROUND_SERVICE",
                "com.gawi.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
            ),
            requestedPermissions(),
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
