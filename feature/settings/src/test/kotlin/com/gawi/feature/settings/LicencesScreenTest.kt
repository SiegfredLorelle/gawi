package com.gawi.feature.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.gawi.core.ui.theme.GawiTheme
import com.gawi.feature.settings.testsupport.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LicencesScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(state: LicencesUiState, onBack: () -> Unit = {}) {
        compose.setContent { GawiTheme { LicencesScreen(state, onBack) } }
    }

    /** Each notice: a heading a screen reader can jump to, its role, its text. */
    @Test
    fun ready_drawsEachNoticeUnderAHeading() {
        render(LicencesUiState.Ready(NOTICES))

        compose.onNodeWithText(string(R.string.settings_notice_outfit))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText(string(R.string.settings_notice_outfit_role)).assertIsDisplayed()
        compose.onNodeWithText("Copyright 2021 The Outfit Project Authors").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.settings_notice_lucide))
            .performScrollTo()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText("Copyright (c) 2026 Lucide Icons and Contributors").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun unavailable_saysSo() {
        render(LicencesUiState.Unavailable)

        compose.onNodeWithText(string(R.string.settings_licences_unavailable_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.settings_notice_outfit)).assertDoesNotExist()
    }

    @Test
    fun loading_drawsNothingYet() {
        render(LicencesUiState.Loading)

        compose.onNodeWithText(string(R.string.settings_notice_outfit)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.settings_licences_unavailable_title)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.settings_licences_title)).assertIsDisplayed()
    }

    @Test
    fun back_isNamedAndReports() {
        val backs = mutableListOf<Unit>()
        render(LicencesUiState.Ready(NOTICES)) { backs += Unit }

        compose.onNodeWithContentDescription(string(R.string.settings_back)).performClick()

        assertEquals(1, backs.size)
    }

    private companion object {
        val NOTICES = listOf(
            NoticeUi(LicenceNotice.Outfit, "Copyright 2021 The Outfit Project Authors"),
            NoticeUi(LicenceNotice.Lucide, "Copyright (c) 2026 Lucide Icons and Contributors"),
        )
    }
}
