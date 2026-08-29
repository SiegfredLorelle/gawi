package com.gawi.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.gawi.core.ui.theme.GawiRole
import com.gawi.core.ui.theme.gawiRole
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The widget pickers' preview layouts draw the same palette the widgets do.
 *
 * **The one hand-copy this module still has, and why it cannot be derived.**
 * [WidgetPalette] holds no hex — every value comes from `:core:ui`'s `gawiRole`.
 * `res/layout/streak_widget_preview.xml` and `momo_widget_preview.xml` cannot
 * join in: each is a real Android layout that the launcher's picker inflates,
 * and XML cannot read Kotlin. So `res/values/colors.xml` reproduces six roles by
 * hand, exactly the way `:app`'s `values/colors.xml` reproduces the window
 * background. `StreakPreviewColorsTest` until 2026-08-29, when the Momo
 * widget's preview added two more colours to the same list.
 *
 * That copy is guarded the same way `:app`'s is, by `WindowBackgroundTest`:
 * compare each resource against the role it claims, in both schemes, so retuning
 * a role in `core/ui/theme/Color.kt` fails here instead of leaving the picker
 * showing last month's palette until somebody notices by eye.
 *
 * Both schemes matter and `values-night` is where the second half lives. The
 * picker resolves it in its own theme, which is the one place a widget resource
 * variant does work — the Glance tree cannot rely on one below API 31, which is
 * the whole subject of [WidgetPalette]'s KDoc.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetPreviewColorsTest {

    /** Each preview colour, and the `:core:ui` role its name claims it reproduces. */
    private val copies = listOf(
        Triple("widget_preview_surface", R.color.widget_preview_surface, GawiRole.Surface),
        Triple("widget_preview_on_surface", R.color.widget_preview_on_surface, GawiRole.OnSurface),
        Triple("widget_preview_primary", R.color.widget_preview_primary, GawiRole.Primary),
        Triple("widget_preview_caption", R.color.widget_preview_caption, GawiRole.OnSurfaceVariant),
        Triple("widget_preview_momo_ground", R.color.widget_preview_momo_ground, GawiRole.PrimaryContainer),
        Triple("widget_preview_momo_caption", R.color.widget_preview_momo_caption, GawiRole.OnPrimaryContainer),
    )

    @Test
    fun `every preview colour is the role it names, in both schemes`() {
        for (night in listOf(false, true)) {
            val context = context(night)
            for ((name, id, role) in copies) {
                assertEquals(
                    "@color/$name has drifted from gawiRole($role) with night=$night",
                    gawiRole(role, darkTheme = night).toArgb(),
                    context.getColor(id),
                )
            }
        }
    }

    /**
     * The night file exists and differs. Without this, a missing
     * `values-night/colors.xml` would leave the test above passing in the light
     * scheme and comparing light values against light roles in the night one —
     * which is exactly what it looks like when the qualifier is not picked up.
     */
    @Test
    fun `the preview has a night variant that actually differs`() {
        val light = context(night = false)
        val dark = context(night = true)
        for ((name, id, _) in copies) {
            assertEquals(
                "@color/$name is the same in both schemes, so values-night is not being read",
                false,
                light.getColor(id) == dark.getColor(id),
            )
        }
    }

    /** Sanity on the alpha channel: a preview drawn at 0% would be invisible and pass a hex compare. */
    @Test
    fun `every preview colour is fully opaque`() {
        val context = context(night = false)
        for ((name, id, _) in copies) {
            assertEquals("@color/$name is not opaque", 0xFF, Color(context.getColor(id)).alpha.times(0xFF).toInt())
        }
    }

    /** The same application context, in a configuration with night mode forced either way. */
    private fun context(night: Boolean): Context {
        val application = RuntimeEnvironment.getApplication()
        val configuration = Configuration(application.resources.configuration)
        configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        return application.createConfigurationContext(configuration)
    }
}
