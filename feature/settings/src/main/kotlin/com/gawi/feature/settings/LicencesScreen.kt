package com.gawi.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.gawi.core.ui.component.GawiIconButton
import com.gawi.core.ui.component.GawiIcons
import com.gawi.core.ui.component.Notice
import com.gawi.core.ui.theme.GawiSpacing

/**
 * The notices, one after another, in one scrolling column.
 *
 * Each is a heading, a one-line role, and the licence text at `bodySmall`. The
 * text is there to be present rather than read like a settings row — OFL §2
 * and ISC both require the notice itself, not a description of it — so the
 * heading and the role are what make the two findable while scrolling, and the
 * heading carries the semantics so a screen reader can jump between them.
 * No links and no buttons: the URLs are in the texts, and nothing else in this
 * app hands off to a browser (docs/ux/settings.md §9).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LicencesScreen(state: LicencesUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_licences_title)) },
                navigationIcon = { GawiIconButton(GawiIcons.ArrowLeft, R.string.settings_back, onClick = onBack) },
            )
        },
    ) { insets ->
        when (state) {
            LicencesUiState.Loading -> Box(Modifier.fillMaxSize().padding(insets))

            LicencesUiState.Unavailable -> Notice(
                title = stringResource(R.string.settings_licences_unavailable_title),
                body = stringResource(R.string.settings_licences_unavailable_body),
                modifier = Modifier.fillMaxSize().padding(insets),
            )

            is LicencesUiState.Ready -> Column(
                modifier = Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(GawiSpacing.Gap),
            ) {
                state.notices.forEach { NoticeBlock(it) }
            }
        }
    }
}

@Composable
private fun NoticeBlock(notice: NoticeUi) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = GawiSpacing.Row, vertical = GawiSpacing.Gap),
        verticalArrangement = Arrangement.spacedBy(GawiSpacing.Line),
    ) {
        Text(
            text = stringResource(notice.notice.title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(notice.notice.role),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = notice.text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = GawiSpacing.Gap),
        )
    }
}
