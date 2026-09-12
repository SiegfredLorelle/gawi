package com.gawi.feature.today

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.gawi.core.ui.theme.GawiRole
import com.gawi.core.ui.theme.contrastRatio
import com.gawi.core.ui.theme.gawiRole
import com.gawi.core.ui.theme.gawiTankFarStop
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The weeds against the water they are drawn on, at both ends of the gradient
 * (docs/ux/momo.md §4).
 *
 * The dark tank used to end on `primaryFixedDim`, which Material specifies as
 * theme-invariant: both schemes declare it as the same light teal, a few units
 * from the dark weed's own `primary`, and the right-hand pair vanished into the
 * corner it was drawn on.
 *
 * **The bar is the light tank, not a WCAG floor.** A weed is a non-text graphic
 * and would take 3:1, but the light tank has never cleared that and nobody has
 * called it a defect — so what is asserted is that the dark tank is no worse
 * than the light one at either end, which is what "reads at both ends" meant. A
 * fixed number here would either fail a drawing that is already accepted or
 * pass the one that measured 1.00:1.
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

    @Test
    fun `the two schemes no longer share one far stop`() {
        // The defect itself, as a property: a `*Fixed` role is theme-invariant,
        // so both tanks used to end on the same light teal.
        assertNotEquals(gawiTankFarStop(darkTheme = false), gawiTankFarStop(darkTheme = true))
    }

    private fun stops(darkTheme: Boolean) = listOf(gawiRole(GawiRole.PrimaryContainer, darkTheme), gawiTankFarStop(darkTheme))

    /** A weed at its drawn alpha over [stop], against that stop. */
    private fun weedOn(stop: Color, darkTheme: Boolean): Float =
        contrastRatio(gawiRole(GawiRole.Primary, darkTheme).copy(alpha = WEED_ALPHA).compositeOver(stop), stop)
}
