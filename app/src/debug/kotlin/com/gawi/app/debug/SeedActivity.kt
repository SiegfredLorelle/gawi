package com.gawi.app.debug

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.gawi.core.data.settings.SettingsSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

/**
 * Edits the two settings there is no screen for yet.
 *
 * It used to put a daily and a weekly habit on the device as well, because
 * before `:feature:habits` there was no other way to get one there. That half is
 * gone: the create form does it now, and does it better, since a seeded habit
 * could only ever be one of two hard-coded samples.
 *
 * What is left is the day cutoff and the reminder time, which
 * `docs/running.md` §5's checklist needs and `:feature:settings` does not yet
 * provide (architecture §2). **This whole directory goes when it does.** The
 * cutoff is also the cheapest way to make a day roll over against a real clock:
 * set it a couple of minutes ahead and watch the rows flip, without `adb root`
 * and without touching the device time.
 *
 * A debug source set rather than a `BuildConfig.DEBUG` branch. The class is not
 * on the release compile path at all, so it cannot ship and a reference to it
 * from `src/main` would not compile — where a runtime branch would leave this
 * code in the release DEX and rely on nobody reaching it. Release does not
 * minify, so there would be nothing to strip it either.
 *
 * Reached only by adb:
 *
 *     adb shell am start -n com.gawi.app/com.gawi.app.debug.SeedActivity \
 *         --es cutoff 03:00 --es reminder 21:00
 *     adb logcat -d -s GawiSeed
 *
 * Passing no extras reads the settings back without changing them, which is the
 * only way to see their values: the stored file holds second-of-day varints, so
 * dumping it shows the keys and not what they are set to.
 */
@AndroidEntryPoint
internal class SeedActivity : ComponentActivity() {

    @Inject
    lateinit var settings: SettingsSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            // The log line is this tool's only feedback channel, so a bad extra
            // has to arrive as a message rather than as a stack trace with no
            // GawiSeed line in it at all.
            runCatching {
                applyExtras()
                // Reading them back is the honest persistence check, and the
                // only way to see the values at all.
                settings.current().toString()
            }
                .onSuccess { report(it) }
                .onFailure { report("failed: $it") }
            finish()
        }
    }

    private suspend fun applyExtras() {
        // LocalTime.parse throws on anything that is not ISO-8601; the caller
        // hears about it through report() above.
        intent.getStringExtra("cutoff")?.let { value ->
            settings.update { it.copy(dayCutoff = LocalTime.parse(value)) }
        }
        intent.getStringExtra("reminder")?.let { value ->
            settings.update { it.copy(reminderTime = LocalTime.parse(value)) }
        }
    }

    private fun report(text: String) {
        Log.i("GawiSeed", text)
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }
}
