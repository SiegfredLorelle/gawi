package com.gawi.feature.settings

/**
 * What the screen needs to know about the phone, as opposed to about the app.
 *
 * Neither of these is a setting and neither belongs in [SettingsUiState]: the
 * store is not their source, and putting them there would mean the ViewModel
 * reading the platform in order to describe it. [SettingsRoute] resolves both and
 * passes them down, which is the rule that KDoc already states about
 * [is24Hour] — the screen stays renderable in every combination without a device
 * to configure.
 *
 * A holder rather than two parameters, for the reason `SettingsActions` is one:
 * `SettingsScreen` would otherwise take six, and detekt's `LongParameterList`
 * fires *at* six for a function. `ignoreDataClasses` is true by default, so a
 * `data class` is exempt however many properties it grows.
 */
internal data class DeviceFacts(
    /** The device's 12- or 24-hour clock convention. */
    val is24Hour: Boolean,
    /**
     * Whether a notification this app posts would actually appear.
     *
     * `NotificationManagerCompat.areNotificationsEnabled()`, not
     * `checkSelfPermission(POST_NOTIFICATIONS)`. The permission only exists on API
     * 33+, while switching notifications off in system settings works on every
     * version — so the permission answers the narrower question and would report
     * "allowed" on API 29 for an app the user had silenced. This is the honest
     * reading of *"will the reminder be seen"*, which is what the row claims.
     *
     * What it does **not** see is a muted *channel*: notifications on, this app's
     * reminder channel set to None. Checking that needs the channel id, which
     * belongs to `:app`, and coupling this module to it for one edge case was
     * declined — docs/ux/settings.md §5 records the gap.
     */
    val notificationsAllowed: Boolean,
)
