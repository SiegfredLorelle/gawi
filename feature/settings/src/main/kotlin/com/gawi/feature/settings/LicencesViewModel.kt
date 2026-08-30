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
import javax.inject.Inject

/**
 * Reads one packaged notice by its file name. The seam a test substitutes, and
 * the place the dispatcher lives — as the archives in `:core:data` own theirs —
 * so the ViewModel stays dispatcher-free and its tests run on the virtual clock.
 */
internal fun interface NoticeSource {
    @Throws(IOException::class)
    suspend fun read(file: String): String
}

/** The assets the module packages from `licenses/` (docs/ux/settings.md §9). */
internal class AssetNoticeSource(private val context: Context) : NoticeSource {
    override suspend fun read(file: String): String = withContext(Dispatchers.IO) {
        context.assets.open(file).use { it.reader().readText() }
    }
}

/**
 * Reads the two notices once and holds them.
 *
 * The primary constructor takes the source so a test can hand it a file that is
 * missing, unreadable or empty; Hilt uses the secondary one. The Robolectric
 * test constructs it that way too, against the real assets — which is the test
 * that proves the packaging, not just the screen.
 *
 * An empty file is a failure, not a notice. `AssetManager.open` succeeds on a
 * zero-byte asset, and a heading with nothing under it would be quietly
 * claiming the licence had been shown.
 */
@HiltViewModel
internal class LicencesViewModel internal constructor(private val source: NoticeSource) : ViewModel() {

    @Inject
    constructor(@ApplicationContext context: Context) : this(AssetNoticeSource(context))

    private val state = MutableStateFlow<LicencesUiState>(LicencesUiState.Loading)
    val uiState: StateFlow<LicencesUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            state.value = try {
                LicencesUiState.Ready(readAll())
            } catch (cause: IOException) {
                Log.e(TAG, "a licence notice did not read", cause)
                LicencesUiState.Unavailable
            }
        }
    }

    private suspend fun readAll(): List<NoticeUi> = LicenceNotice.entries.map { notice ->
        val text = source.read(notice.file)
        if (text.isBlank()) throw IOException("${notice.file} is empty")
        NoticeUi(notice, reflowNotice(text))
    }

    private companion object {
        const val TAG = "LicencesViewModel"
    }
}
