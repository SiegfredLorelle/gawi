package com.gawi.app.debug

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.gawi.core.data.repository.HabitRepository
import com.gawi.core.data.settings.SettingsSource
import com.gawi.core.domain.command.CommandResult
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.projection.HabitMetadata
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

/**
 * Puts a daily and a weekly habit on the device, and optionally edits the two
 * settings there is no screen for yet, so step 4b's vertical slice can be
 * exercised before :feature:habits exists (architecture §2).
 *
 * A debug source set rather than a BuildConfig.DEBUG branch. The class is not on
 * the release compile path at all, so it cannot ship and a reference to it from
 * src/main would not compile — where a runtime branch would leave this code and
 * its sample data in the release DEX and rely on nobody reaching it. Release
 * does not minify, so there would be nothing to strip it either.
 *
 * Reached only by adb:
 *
 *     adb shell am start -n com.gawi.app/com.gawi.app.debug.SeedActivity
 *     adb shell am start -n com.gawi.app/com.gawi.app.debug.SeedActivity \
 *         --es cutoff 03:00 --es reminder 21:00
 *     adb logcat -d -s GawiSeed
 *
 * Delete this directory when the create form lands. Nothing references it, so
 * the deletion cannot break a build.
 */
@AndroidEntryPoint
internal class SeedActivity : ComponentActivity() {

    @Inject
    lateinit var repository: HabitRepository

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
                // Reading the settings back is the honest persistence check: the
                // stored file holds second-of-day varints, so dumping it shows
                // the keys but not the values.
                seed() + " | " + settings.current()
            }
                .onSuccess { report(it) }
                .onFailure { report("failed: $it") }
            finish()
        }
    }

    /**
     * The day cutoff and the reminder time, if the caller passed them.
     *
     * Here because they are the only two settings the on-device checklist needs
     * and :feature:settings does not exist. The cutoff also happens to be the
     * cheapest way to make the day roll over on a real clock: set it a couple of
     * minutes ahead and watch the rows flip without touching the device time.
     */
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

    /** Skips names already present, so re-running while iterating is harmless. */
    private suspend fun seed(): String {
        val existing = repository.observeToday().first().habits.map { it.habit.name }.toSet()
        val fresh = SAMPLES.filterNot { it.name in existing }
        if (fresh.isEmpty()) return "seeded nothing, all present"
        // map, not joinToString: the latter takes a nullable function type, so
        // it is not inlined and cannot host a suspend call.
        return fresh
            .map { metadata ->
                when (val result = repository.createHabit(metadata)) {
                    is CommandResult.Accepted -> metadata.name
                    is CommandResult.Rejected -> "${metadata.name} rejected ${result.error}"
                }
            }
            .joinToString(prefix = "seeded ")
    }

    private fun report(text: String) {
        Log.i("GawiSeed", text)
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    private companion object {
        val SAMPLES = listOf(
            HabitMetadata(name = "read", icon = "📖", color = "#7E57C2", schedule = Schedule.Daily, tag = "growth"),
            HabitMetadata(name = "exercise", icon = "🏃", color = "#26A69A", schedule = Schedule.Weekly(3), tag = "health"),
        )
    }
}
