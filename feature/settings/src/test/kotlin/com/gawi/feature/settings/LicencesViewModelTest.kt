package com.gawi.feature.settings

import app.cash.turbine.test
import com.gawi.feature.settings.testsupport.MainDispatcherRule
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
