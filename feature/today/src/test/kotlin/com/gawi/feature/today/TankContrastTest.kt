package com.gawi.feature.today

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.gawi.core.ui.theme.GawiRole
import com.gawi.core.ui.theme.contrastRatio
import com.gawi.core.ui.theme.gawiRole
import com.gawi.core.ui.theme.gawiTankFarStop
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The weeds against the water they are drawn on, at both ends of the gradient
 * (docs/ux/momo.md §4).
 *
 * **A `*Fixed` role cannot carry a per-scheme value.** Material specifies the
 * whole family as theme-invariant, so both schemes declare `primaryFixedDim` as
 * the same light teal — a few units from the dark weed's own `primary`, which
 * puts the dark pair at 1.00:1 in the corner it is drawn on. That is why the
 * dark tank names its own far stop rather than sharing one, and it is what this
 * test reddens on if the two are ever collapsed back together.
 *
 * **The bar is the light tank, not a WCAG floor.** A weed is a non-text graphic
 * and would take 3:1, but the light tank does not clear that and nobody has
 * called it a defect — so what is asserted is that the dark tank is no worse
 * than the light one at either end, which is what "reads at both ends" means. A
 * fixed number here would either fail a drawing that is already accepted or
 * pass one measuring 1.00:1.
 */
class TankContrastTest {

    @Test
    fun `the dark weeds read at least as well as the light ones, at both ends`() {
        val lightFloor = stops(darkTheme = false).minOf { weedOn(it, darkTheme = false) }

        stops(darkTheme = true).forEach { stop ->
            val ratio = weedOn(stop, darkTheme = true)
            assertTrue(
                "dark weeds measure $ratio on one end of the water, worse than the light tank's $lightFloor",
                ratio >= lightFloor,
            )
        }
    }

    private fun stops(darkTheme: Boolean) = listOf(gawiRole(GawiRole.PrimaryContainer, darkTheme), gawiTankFarStop(darkTheme))

    /** A weed at its drawn alpha over [stop], against that stop. */
    private fun weedOn(stop: Color, darkTheme: Boolean): Float =
        contrastRatio(gawiRole(GawiRole.Primary, darkTheme).copy(alpha = WEED_ALPHA).compositeOver(stop), stop)
}
