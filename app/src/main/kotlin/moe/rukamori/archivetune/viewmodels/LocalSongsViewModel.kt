/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.localmedia.LocalSongScanConfig
import moe.rukamori.archivetune.localmedia.LocalSongScanSummary
import moe.rukamori.archivetune.localmedia.ScanLocalMusicUseCase
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

@HiltViewModel
class LocalSongsViewModel @Inject constructor(
    database: MusicDatabase,
    private val scanLocalMusic: ScanLocalMusicUseCase,
) : ViewModel() {
    private val _scanState = MutableStateFlow<LocalSongsScanState>(LocalSongsScanState.Empty)
    val scanState = _scanState.asStateFlow()
    private var scanJob: Job? = null

    val songs = database.localSongs().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun scanDevice(scanConfig: LocalSongScanConfig = LocalSongScanConfig()) {
        if (scanJob?.isActive == true) return
        scanJob = launchScan { scanLocalMusic(scanConfig) }
    }

    fun includeFolder(uri: Uri) {
        scanJob?.cancel()
        scanJob = launchScan { scanLocalMusic(scanLocalMusic.includeFolder(uri)) }
    }

    private fun launchScan(scan: suspend () -> LocalSongScanSummary): Job {
        _scanState.value = LocalSongsScanState.Loading
        return viewModelScope.launch {
            try {
                _scanState.value = LocalSongsScanState.Success(scan())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                coroutineContext.ensureActive()
                reportException(error)
                _scanState.value = LocalSongsScanState.Error(
                    if (error is SecurityException) R.string.local_songs_permission_body else R.string.local_songs_scan_failed,
                )
            }
        }
    }
}

sealed interface LocalSongsScanState {
    data object Loading : LocalSongsScanState

    @Immutable
    data class Success(val summary: LocalSongScanSummary) : LocalSongsScanState

    data object Empty : LocalSongsScanState

    @Immutable
    data class Error(@StringRes val messageRes: Int) : LocalSongsScanState

    val isScanning: Boolean get() = this is Loading
    val lastSummary: LocalSongScanSummary? get() = (this as? Success)?.summary
    val errorMessageRes: Int? get() = (this as? Error)?.messageRes
}
