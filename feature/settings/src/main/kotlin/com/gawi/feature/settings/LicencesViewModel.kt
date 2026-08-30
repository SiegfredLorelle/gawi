package com.gawi.feature.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

/** Opens one packaged notice by its file name. The seam a test substitutes. */
internal fun interface NoticeOpener {
    @Throws(IOException::class)
    fun open(file: String): InputStream
}

/**
 * Reads the two notices once and holds them.
 *
 * The primary constructor takes the opener so a test can hand it a file that is
 * missing or unreadable; Hilt uses the secondary one, which reads the assets
 * the module packages from `licenses/` (docs/ux/settings.md §9). The
 * Robolectric test constructs it that way too, against the real assets — which
 * is the test that proves the packaging, not just the screen.
 */
@HiltViewModel
internal class LicencesViewModel internal constructor(private val opener: NoticeOpener) : ViewModel() {

    @Inject
    constructor(@ApplicationContext context: Context) : this(NoticeOpener(context.assets::open))

    private val state = MutableStateFlow<LicencesUiState>(LicencesUiState.Loading)
    val uiState: StateFlow<LicencesUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            state.value = try {
                LicencesUiState.Ready(withContext(Dispatchers.IO) { readAll() })
            } catch (cause: IOException) {
                Log.e(TAG, "a licence notice did not read", cause)
                LicencesUiState.Unavailable
            }
        }
    }

    private fun readAll(): List<NoticeUi> = LicenceNotice.entries.map { notice ->
        NoticeUi(notice, reflowNotice(opener.open(notice.file).use { it.reader().readText() }))
    }

    private companion object {
        const val TAG = "LicencesViewModel"
    }
}
