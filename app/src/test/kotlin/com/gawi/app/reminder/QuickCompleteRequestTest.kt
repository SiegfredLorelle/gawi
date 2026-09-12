package com.gawi.app.reminder

import android.content.Intent
import com.gawi.core.domain.testing.habitId
import com.gawi.core.testing.FIXED_DATE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What a tapped button's extras are allowed to be.
 *
 * [requestFrom] promises a null for anything missing or malformed, and the
 * promise is load-bearing twice over: the receiver has no other way to refuse,
 * and a throw here would happen while assembling the *re-post*, losing the
 * completion the user asked for along with it.
 */
@RunWith(RobolectricTestRunner::class)
class QuickCompleteRequestTest {

    private fun intent(
        habit: String? = habitId(1).value,
        date: String? = FIXED_DATE.toString(),
        ids: Array<String>? = arrayOf(habitId(1).value, habitId(2).value),
        names: Array<String>? = arrayOf("read", "swim"),
        total: Int = 5,
    ) = Intent().apply {
        habit?.let { putExtra(EXTRA_HABIT_ID, it) }
        date?.let { putExtra(EXTRA_DATE, it) }
        ids?.let { putExtra(EXTRA_IDS, it) }
        names?.let { putExtra(EXTRA_NAMES, it) }
        putExtra(EXTRA_TOTAL, total)
    }

    @Test
    fun `a whole intent parses to what the button carried`() {
        val request = requestFrom(intent())!!

        assertEquals(habitId(1).value, request.habitId)
        assertEquals(FIXED_DATE, request.logicalDate)
        assertEquals(listOf(habitId(1), habitId(2)), request.outstanding.map { it.id })
        assertEquals(5, request.total)
    }

    @Test
    fun `a missing habit is nothing to act on`() {
        assertNull(requestFrom(intent(habit = null)))
    }

    @Test
    fun `a missing date is nothing to act on`() {
        assertNull(requestFrom(intent(date = null)))
    }

    /** A defaulted date would write a completion to the wrong day, silently. */
    @Test
    fun `a date that is not a date is nothing to act on`() {
        assertNull(requestFrom(intent(date = "not-a-date")))
    }

    @Test
    fun `an empty list is nothing to act on`() {
        assertNull(requestFrom(intent(ids = emptyArray(), names = emptyArray())))
    }

    /**
     * `HabitId` rejects anything that is not a canonical UUIDv7 in its `init`, so
     * this is the case that would otherwise **throw** rather than return null.
     */
    @Test
    fun `one malformed id rejects the whole intent rather than throwing`() {
        val request = requestFrom(intent(ids = arrayOf(habitId(1).value, "not-a-uuid")))

        assertNull(request)
    }

    /**
     * A total below the habits carried would re-post *"1 of 0 left today"* — the
     * body and the buttons contradicting each other, which is the state the
     * re-post exists to prevent.
     */
    @Test
    fun `a total smaller than the list is nothing to act on`() {
        assertNull(requestFrom(intent(total = 1)))
        assertNotNull(requestFrom(intent(total = 2)))
    }
}
