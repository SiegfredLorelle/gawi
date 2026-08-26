package com.gawi.app.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gawi.core.data.settings.SettingsSource
import com.gawi.core.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Which scheme to draw, for the one Activity there is.
 *
 * A ViewModel rather than a `collectAsState` in [com.gawi.app.MainActivity],
 * for a reason specific to this setting: applying it recreates the Activity on
 * API 31 and up ([ApplicationNightMode]), and a ViewModel survives that. State
 * held in the Activity would re-read the store on every recreation, and the
 * side effect below would run again on each one.
 *
 * `:app`'s own, and the first ViewModel here — every other one is `internal` to
 * a feature module. This one has to be here because the theme is resolved
 * around `GawiNavHost` rather than inside any screen.
 */
@HiltViewModel
internal class ThemeViewModel @Inject constructor(settings: SettingsSource, nightMode: ApplicationNightMode) : ViewModel() {

    /**
     * The stored mode, or `null` until the first read lands.
     *
     * `null` is not [ThemeMode.SYSTEM]: the two would draw the same scheme, but
     * only one of them is an answer. The distinction costs nothing here and
     * keeps a test able to tell "not read yet" from "read, and it says follow
     * the system".
     *
     * [catch] resolves to [ThemeMode.SYSTEM] rather than to nothing, because
     * this flow decides whether the app has a colour scheme at all. `observe()`
     * already absorbs an unreadable file into the defaults, so what reaches
     * here is a bug rather than IO — and a screen the user cannot read is a
     * worse answer to a bug than the scheme they had before this setting
     * existed.
     *
     * **It sits *after* the side effect, and the order is the decision.**
     * Upstream of it, that fallback would be applied: a read bug would write
     * `MODE_NIGHT_AUTO` over a stored Dark, clearing a persisted platform
     * override the user set, while the preferences file still said Dark and the
     * settings row still read Dark. Downstream, the fallback only decides what
     * this process draws, and the disagreement it can cause dies with the
     * process. `catch` then ends the flow — the same property
     * `SettingsSource.observe()` and `TodayViewModel` have, recorded in
     * docs/ux/settings.md §8 — so the theme is frozen for the rest of this
     * process, which is the price of not writing a guess to disk.
     *
     * [SharingStarted.Eagerly], so the read starts with the Activity rather
     * than with the first frame that collects it, and so the side effect below
     * does not wait on a subscriber.
     */
    val theme: StateFlow<ThemeMode?> = settings.observe()
        .map { it.theme }
        .distinctUntilChanged()
        // The platform's copy, kept in step with the preference. Here rather
        // than in the settings write, so a restored preferences file reaches
        // the platform on the next start too — see ApplicationNightMode, which
        // is also why this cannot throw into `viewModelScope`.
        .onEach(nightMode::apply)
        .catch { emit(ThemeMode.SYSTEM) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

/**
 * The scheme to draw, given the stored mode and what the device says.
 *
 * A pure function, because it is the one decision in this file that can be
 * wrong in a way no screenshot would show: `null` and [ThemeMode.SYSTEM] both
 * have to defer to [systemDark], and a `when` that forgot either would still
 * compile and still look right on a device whose system setting matched.
 */
internal fun ThemeMode?.resolvesToDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM, null -> systemDark
}
