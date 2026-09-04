package com.gawi.feature.settings

import app.cash.turbine.test
import com.gawi.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.IOException

/**
 * Under Robolectric so the first test can open the *real* assets: that is the
 * test that proves `licenses/` is packaged, which no screen test can.
 */
@RunWith(RobolectricTestRunner::class)
class LicencesViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    /** The two notices are in the APK, under their upstream names, verbatim. */
    @Test
    fun `the packaged notices read back, both of them, in order`() = runTest {
        val viewModel = LicencesViewModel(AssetNoticeSource(RuntimeEnvironment.getApplication()))

        viewModel.uiState.test {
            var state = awaitItem()
            if (state == LicencesUiState.Loading) state = awaitItem()
            val ready = state as LicencesUiState.Ready

            assertEquals(listOf(LicenceNotice.Outfit, LicenceNotice.Lucide), ready.notices.map { it.notice })
            val (outfit, lucide) = ready.notices
            assertTrue(outfit.text.startsWith("Copyright 2021 The Outfit Project Authors"))
            assertTrue(outfit.text.contains("SIL OPEN FONT LICENSE Version 1.1"))
            assertTrue(lucide.text.startsWith("ISC License"))
            assertTrue(lucide.text.contains("Copyright (c) 2026 Lucide Icons and Contributors"))
            assertTrue(lucide.text.contains("The MIT License (MIT)"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Layout, not editing, on the files that ship: the word sequence, the
     * paragraph count and the rule count are the three things the reflow
     * promises not to touch (docs/ux/settings.md §9). Tokens rather than a
     * whitespace-stripped string, because stripping would let two words merge
     * or a paragraph break vanish unnoticed.
     */
    @Test
    fun `the reflowed notices are the files, word for word`() {
        val assets = RuntimeEnvironment.getApplication().assets
        LicenceNotice.entries.forEach { notice ->
            val raw = assets.open(notice.file).use { it.reader().readText() }
            val reflowed = reflowNotice(raw)

            assertEquals(notice.file, raw.words(), reflowed.words())
            assertEquals(notice.file, raw.trimEnd().split(PARAGRAPH_BREAK).size, reflowed.split("\n\n").size)
            assertEquals(notice.file, raw.rules(), reflowed.rules())
        }
    }

    private fun String.words(): List<String> = split(WHITESPACE).filter { it.isNotEmpty() }

    private fun String.rules(): Int = lines().count { line -> line.length >= 3 && line.all { it == '-' } }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val PARAGRAPH_BREAK = Regex("\\n\\s*\\n")
    }

    /** Both or neither: one unreadable file takes the screen to Unavailable. */
    @Test
    fun `a notice that does not read makes the screen unavailable`() = runTest {
        val viewModel = LicencesViewModel(
            NoticeSource { file -> if (file == LicenceNotice.Lucide.file) throw IOException("gone") else "text" },
        )

        assertEquals(LicencesUiState.Unavailable, viewModel.uiState.value)
    }

    /** `AssetManager.open` succeeds on a zero-byte file; a heading over nothing is not a notice. */
    @Test
    fun `an empty notice makes the screen unavailable too`() = runTest {
        val viewModel = LicencesViewModel(NoticeSource { file -> if (file == LicenceNotice.Outfit.file) "" else "text" })

        assertEquals(LicencesUiState.Unavailable, viewModel.uiState.value)
    }
}
