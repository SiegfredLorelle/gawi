package com.gawi.feature.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

/**
 * The settings screen, wired up.
 *
 * Takes no modifier and puts no Compose or `:core:data` type in its signature —
 * the same rule the other Routes follow, and what keeps `:core:ui` and
 * `:core:data` on implementation scope here. One plain lambda goes out, so
 * navigation stays entirely in `:app`.
 *
 * The one thing read off the platform here is the device's 12- or 24-hour
 * preference, which is a fact about the phone rather than about the app. It is
 * resolved here and passed down so the screen stays renderable, in both
 * conventions, without a device to set it on.
 */
@Composable
fun SettingsRoute(onBack: () -> Unit, onOpenLicences: () -> Unit) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // LocalResources rather than LocalContext: reading strings off the context
    // is not configuration-aware, so the copy would go stale on a locale change.
    val resources = LocalResources.current
    // Read on each composition rather than remembered. Deliberately NOT keyed on
    // LocalConfiguration, which would look like it refreshed and would not:
    // Configuration carries no 12/24-hour field, so flipping the system clock
    // format broadcasts ACTION_TIME_CHANGED and never triggers a configuration
    // change. The call is a settings lookup the framework caches, so reading it
    // is cheap; what it costs is that the format only catches up when something
    // else recomposes this. docs/ux/settings.md §5 records the gap.
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)

    val context = LocalContext.current
    // Re-read on every resume, not once. The fix for this is in system settings,
    // which means leaving the app and coming back — so a value read once would
    // show the old answer for exactly as long as the user was looking to see
    // whether it had worked. LifecycleResumeEffect keyed on the context, which is
    // the only thing the read depends on.
    var notificationsAllowed by remember { mutableStateOf(context.notificationsAllowed()) }
    LifecycleResumeEffect(context) {
        notificationsAllowed = context.notificationsAllowed()
        onPauseOrDispose { }
    }

    // Created unconditionally, like the three pickers below, and for the reason
    // stated there: a launcher registered only in one state is a callback that
    // never fires after process death.
    //
    // The result is not trusted as the new state — `notificationsAllowed` is
    // re-read instead, because the permission is not the whole answer (see
    // DeviceFacts) and because the system dialog is not the only way this can
    // change while the app is alive.
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        notificationsAllowed = context.notificationsAllowed()
        // The one case that would otherwise dead-end: `RequestPermission` returns
        // instantly and silently once the user has refused for good, so a row
        // reading "tap to allow" would do literally nothing when tapped. Handing
        // them the system's own page is the only remaining route.
        //
        // This has to be decided *here* and not before the launch, because
        // `canAskAgain` cannot tell "never asked" from "refused for good" — see
        // its KDoc. By the time this callback runs, only the second reading is
        // possible.
        //
        // A user who simply declined the dialog is not escalated: declining once
        // makes the rationale flag true, so this does nothing and the row goes on
        // saying the reminder will not arrive. Declining a second time does reach
        // the settings page, which is honouring a tap on "tap to allow" rather
        // than nagging — the row is the only thing that ever triggers this.
        if (!notificationsAllowed && !context.canAskAgain()) context.openNotificationSettings()
    }

    // Both launchers are created unconditionally, never inside a branch.
    // rememberLauncherForActivityResult registers with the activity's result
    // registry and re-registers after process death — and the file picker is
    // exactly where a low-memory kill happens, so a launcher created only in
    // one state is a callback that never fires on the way back.
    //
    // A null Uri is a cancelled picker. Nothing happens and nothing is said:
    // docs/ux/settings.md §3's rule is that Cancel means nothing changed, and
    // the system's own dialog is this section's version of that dialog.
    val exportTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(EXPORT_MIME_TYPE)) { target ->
        if (target != null) viewModel.onExportTo(target)
    }
    val exportCompletionsTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV_MIME_TYPE)) { target ->
        if (target != null) viewModel.onExportCompletionsTo(target)
    }
    val importFrom = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { source ->
        if (source != null) viewModel.onImportFrom(source)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(resources.format(message))
        }
    }

    SettingsScreen(
        state = state,
        actions = SettingsActions(
            onDayCutoffChange = viewModel::onDayCutoffChange,
            onWeekStartChange = viewModel::onWeekStartChange,
            onReminderTimeChange = viewModel::onReminderTimeChange,
            onThemeChange = viewModel::onThemeChange,
            // The wall-clock date, not the logical one — see exportFileName.
            onExport = { exportTo.launch(exportFileName(LocalDate.now())) },
            onExportCompletions = { exportCompletionsTo.launch(csvFileName(LocalDate.now())) },
            onImport = { importFrom.launch(IMPORT_MIME_TYPES) },
            onEnableNotifications = {
                // Below API 33 there is no runtime permission to ask for at all:
                // notifications were switched off in system settings, and that is
                // the only place they go back on. On 33+ the dialog is always the
                // right *first* move — deciding beforehand whether it will
                // actually appear is not something the platform can be asked, and
                // the callback is where that is resolved instead.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestNotifications.launch(POST_NOTIFICATIONS)
                } else {
                    context.openNotificationSettings()
                }
            },
            onOpenLicences = onOpenLicences,
            onBack = onBack,
        ),
        snackbarHostState = snackbarHostState,
        device = DeviceFacts(is24Hour = is24Hour, notificationsAllowed = notificationsAllowed),
    )
}

/**
 * Whether a notification this app posted would be seen.
 *
 * `NotificationManagerCompat` rather than a permission check — see [DeviceFacts]
 * for why the permission answers a narrower question than the row asks.
 */
private fun Context.notificationsAllowed(): Boolean = NotificationManagerCompat.from(this).areNotificationsEnabled()

/**
 * Whether the runtime dialog could be shown again — **asked only after a request
 * has come back**, and wrong if asked before one.
 *
 * `shouldShowRequestPermissionRationale` is `false` in **two** unrelated states:
 * before the permission has ever been requested, and after it has been refused
 * for good. It is `true` only in between. So it cannot answer "will the dialog
 * appear?" on a fresh install, and an earlier version of this file used it that
 * way — which would have sent every first-time user straight to system settings
 * instead of showing them the dialog, for the single most common path through
 * this row.
 *
 * Used where the ambiguity is already resolved: in the request callback, the
 * "never asked" reading is impossible, so `false` there means exactly one thing.
 * That is the whole reason the decision lives after the launch rather than
 * before it.
 *
 * A `Context` with no `Activity` behind it degrades to `true`, which means "do
 * not escalate": the state cannot be read, and silently opening a system settings
 * page is the more surprising of the two wrong answers.
 */
private fun Context.canAskAgain(): Boolean {
    val activity = findActivity() ?: return true
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, POST_NOTIFICATIONS)
}

/**
 * The `Activity` behind this context, unwrapping whatever wraps it.
 *
 * `LocalContext.current` is usually the activity and is not guaranteed to be —
 * a `ContextThemeWrapper` is enough to break a plain cast, and the cast is the
 * kind of thing that works in the test harness and fails on one OEM.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * The app's own page in system notification settings.
 *
 * The route that works on every version and in every state, which is why it is
 * the fallback rather than a second-best. `FLAG_ACTIVITY_NEW_TASK` because this
 * may be reached from a context that is not an `Activity`.
 */
private fun Context.openNotificationSettings() {
    val appNotifications = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

    // Neither action is guaranteed to resolve. Both are implicit intents into an
    // app this app does not ship, and a device or work profile without a Settings
    // activity for one of them is a bare ActivityNotFoundException out of a click
    // handler — a crash on tapping a row, which is the failure every other path in
    // this module absorbs. Found by /code-review.
    //
    // The fallback is the app's own details page, which carries a notifications
    // entry on every version this app supports. If neither resolves there is
    // nothing left to offer, and the row goes on saying the reminder will not
    // arrive, which is at least true.
    for (intent in listOf(appNotifications, appDetails)) {
        val started = runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (started) return
    }
    Log.w(TAG, "no activity could show this app's notification settings")
}

private const val TAG = "SettingsRoute"

private const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

/**
 * Resolves a message and whatever counts it carries.
 *
 * The spread is over a list that is either empty or holds two ints, resolved
 * once when a snackbar is shown. detekt's warning is about copying large arrays
 * on a hot path, and `getString`'s vararg leaves no other way to pass them.
 */
@Suppress("SpreadOperator")
private fun Resources.format(message: SettingsMessage): String = getString(message.text, *message.args.toTypedArray())

/** What an export is written as. `CreateDocument` requires one. */
private const val EXPORT_MIME_TYPE = "application/json"

/**
 * What the CSV is written as.
 *
 * `text/csv` is the registered type (RFC 4180) and is what makes a spreadsheet
 * the default handler for the file afterwards, which is the whole point of the
 * row. Note this is only the *created* document's type; the import picker's
 * generous filter is a separate decision and is about reading.
 */
private const val CSV_MIME_TYPE = "text/csv"

/**
 * What the import picker offers to show.
 *
 * Three types rather than one, and the two extras are not sloppiness. An export
 * that has been round-tripped through a cloud drive, a messaging app or a USB
 * copy very often comes back typed `application/octet-stream`, and some
 * providers report any `.json` as `text/plain`. A filter that hides a user's
 * own backup from them is a worse failure than one that also shows a few text
 * files — and this is the only recovery path there is.
 *
 * The filter is a convenience and never the check. What makes a file an export
 * is decided by reading it.
 */
private val IMPORT_MIME_TYPES = arrayOf("application/json", "application/octet-stream", "text/plain")
