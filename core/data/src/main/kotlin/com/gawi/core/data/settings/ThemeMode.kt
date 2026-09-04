package com.gawi.core.data.settings

/**
 * Which colour scheme the app draws: the system's choice, or one of the two
 * designed schemes forced regardless of it (docs/ux/settings.md §7).
 *
 * Stored under [code] rather than `ordinal`. The other stored preferences are
 * stable numbers too — a second-of-day, an ISO weekday — and for the same
 * reason: an `ordinal` changes silently when a constant is reordered or one is
 * inserted, and a preferences file written by an older build would then read
 * as a different theme. The codes are part of the file format and never move.
 *
 * Beside [UserSettings] rather than in `:core:ui`, because it is what the
 * settings store reads and writes; how a mode is *resolved* to a scheme is
 * `:app`'s business, since only the Activity knows the system's setting.
 */
enum class ThemeMode(val code: Int) {
    /** Follow the device's dark setting, which is the default. */
    SYSTEM(0),
    LIGHT(1),
    DARK(2),
    ;

    companion object {
        /** The mode stored as [code], or null when it is absent or not a code this build knows. */
        fun fromCode(code: Int?): ThemeMode? = entries.firstOrNull { it.code == code }
    }
}
