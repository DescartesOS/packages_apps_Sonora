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
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.localmedia.LocalSongScanConfig
import moe.rukamori.archivetune.localmedia.LocalMusicAccess
import moe.rukamori.archivetune.localmedia.LocalMusicFolder
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
    private var scanJob: Job? = null
    private var settingsJob: Job? = null
    private var accessJob: Job? = null
    private var settingsMutationJob: Job? = null
    private var initialScanRequested = false
    private val settingsValues = MutableStateFlow(SettingsValues())
    private val eventChannel = Channel<LocalSongSettingsEvent>(Channel.BUFFERED)
    val settingsEvents = eventChannel.receiveAsFlow()

    val settingsState = combine(settingsValues, _scanState, ::buildSettingsState).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LocalSongSettingsState.Loading,
    )

    val songs = database.localSongs().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun onLibraryOpened() {
        refreshSettings()
        if (!initialScanRequested) {
            startScan(automatic = true)
        }
    }

    fun refreshSettings() {
        if (settingsJob?.isActive != true) {
            settingsJob = viewModelScope.launch {
                try {
                    scanLocalMusic.configuration.collect { configuration ->
                        settingsValues.update { it.copy(configuration = configuration, configurationError = null) }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    reportException(error)
                    settingsValues.update { it.copy(configurationError = R.string.local_song_settings_load_failed) }
                }
            }
        }
        accessJob?.cancel()
        accessJob = viewModelScope.launch {
            try {
                val access = scanLocalMusic.readAccess()
                settingsValues.update { it.copy(access = access, accessError = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                coroutineContext.ensureActive()
                reportException(error)
                settingsValues.update { it.copy(accessError = R.string.local_song_settings_load_failed) }
            }
        }
    }

    fun onSettingsAction(action: LocalSongSettingsAction) {
        when (action) {
            LocalSongSettingsAction.Back -> eventChannel.trySend(LocalSongSettingsEvent.NavigateBack)
            LocalSongSettingsAction.Reload -> refreshSettings()
            LocalSongSettingsAction.Scan -> scanDevice()
            LocalSongSettingsAction.CancelScan -> {
                scanJob?.cancel()
                _scanState.value = LocalSongsScanState.Empty
            }
            LocalSongSettingsAction.OpenAppSettings -> eventChannel.trySend(LocalSongSettingsEvent.OpenAppSettings)
            is LocalSongSettingsAction.PickFolder -> {
                if (!canEdit()) return
                settingsValues.update { it.copy(pickingFolder = true) }
                eventChannel.trySend(LocalSongSettingsEvent.PickFolder(action.included, action.initialUri))
            }
            is LocalSongSettingsAction.FolderSelected -> {
                settingsValues.update { it.copy(pickingFolder = false) }
                action.uri?.let { uri ->
                    if (action.included) includeFolder(uri)
                    else editSettings { scanLocalMusic.excludeFolder(uri) }
                }
            }
            is LocalSongSettingsAction.RemoveFolder -> editSettings {
                scanLocalMusic.removeFolder(action.entry, action.included)
            }
            is LocalSongSettingsAction.DurationChanged -> {
                if (canEdit()) settingsValues.update { it.copy(durationDraft = action.seconds.coerceIn(0, ScanLocalMusicUseCase.MaximumMinimumDurationSeconds)) }
            }
            LocalSongSettingsAction.SaveDuration -> {
                val seconds = settingsValues.value.durationDraft ?: return
                editSettings { scanLocalMusic.setMinimumDuration(seconds) }
            }
            is LocalSongSettingsAction.PermissionResult -> {
                settingsValues.update { it.copy(requestingPermission = false) }
                if (action.granted) scanDevice() else refreshSettings()
            }
            LocalSongSettingsAction.ExternalActionFailed -> settingsValues.update {
                it.copy(pickingFolder = false, requestingPermission = false, errorMessage = R.string.local_song_settings_open_failed)
            }
        }
    }

    fun scanDevice() {
        refreshSettings()
        startScan(automatic = false)
    }

    private fun canEdit(): Boolean =
        scanJob?.isActive != true && settingsMutationJob?.isActive != true &&
            !settingsValues.value.pickingFolder && !settingsValues.value.requestingPermission

    private fun editSettings(update: suspend () -> LocalSongScanConfig) {
        if (!canEdit()) return
        settingsValues.update { it.copy(saving = true, errorMessage = null) }
        settingsMutationJob = viewModelScope.launch {
            try {
                val configuration = update()
                val access = scanLocalMusic.readAccess()
                settingsValues.update {
                    it.copy(configuration = configuration, access = access, durationDraft = null, saving = false,
                        configurationError = null, accessError = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                coroutineContext.ensureActive()
                reportException(error)
                settingsValues.update {
                    it.copy(saving = false, durationDraft = null, errorMessage = R.string.local_song_settings_save_failed)
                }
            }
        }
    }

    fun includeFolder(uri: Uri) {
        editSettings { scanLocalMusic.includeFolder(uri) }
    }

    private fun startScan(automatic: Boolean) {
        if (!canEdit()) return
        initialScanRequested = true
        _scanState.value = LocalSongsScanState.Loading
        scanJob = viewModelScope.launch {
            try {
                val configuration = scanLocalMusic.currentConfiguration()
                val access = scanLocalMusic.readAccess()
                settingsValues.update {
                    it.copy(configuration = configuration, access = access, errorMessage = null,
                        configurationError = null, accessError = null)
                }
                if (configuration.requiresMediaPermission && !access.hasMediaPermission) {
                    if (!automatic) settingsValues.update { it.copy(requestingPermission = true) }
                    _scanState.value = LocalSongsScanState.Empty
                    if (!automatic) eventChannel.send(LocalSongSettingsEvent.RequestAudioPermission)
                    return@launch
                }
                _scanState.value = LocalSongsScanState.Success(scanLocalMusic(configuration))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                coroutineContext.ensureActive()
                reportException(error)
                _scanState.value = LocalSongsScanState.Error(
                    when {
                        error !is SecurityException -> R.string.local_songs_scan_failed
                        settingsValues.value.configuration?.requiresMediaPermission == false -> R.string.local_song_settings_folder_access_needed
                        else -> R.string.local_songs_permission_body
                    },
                )
            }
        }
    }

    private fun buildSettingsState(values: SettingsValues, scan: LocalSongsScanState): LocalSongSettingsState {
        val configuration = values.configuration
        val access = values.access
        val errorMessage = values.configurationError ?: values.accessError ?: values.errorMessage ?: scan.errorMessageRes
        if (configuration == null || access == null) {
            return errorMessage?.let { LocalSongSettingsState.Error(null, it) } ?: LocalSongSettingsState.Loading
        }
        val model = LocalSongSettingsUiModel(
            minimumDurationSeconds = values.durationDraft ?: configuration.sanitizedMinimumDurationSeconds,
            includedFolders = folderModels(configuration.sanitizedIncludedFolders, access, included = true),
            excludedFolders = folderModels(configuration.sanitizedExcludedFolders, access, included = false),
            hasMediaPermission = access.hasMediaPermission,
            requiresMediaPermission = configuration.requiresMediaPermission,
            isScanning = scan.isScanning,
            isSaving = values.saving,
            canEdit = !scan.isScanning && !values.saving && !values.pickingFolder && !values.requestingPermission,
            summary = scan.lastSummary,
        )
        return when {
            errorMessage != null -> LocalSongSettingsState.Error(model, errorMessage)
            model.summary?.scannedSongs == 0 -> LocalSongSettingsState.Empty(model)
            else -> LocalSongSettingsState.Success(model)
        }
    }

    private fun folderModels(entries: Set<String>, access: LocalMusicAccess, included: Boolean): ImmutableList<LocalSongFolderUiModel> =
        ImmutableList.copyOf(entries.sortedWith(String.CASE_INSENSITIVE_ORDER).map { entry ->
            val folder = LocalMusicFolder.fromEntry(entry)
            LocalSongFolderUiModel(
                entry = entry,
                path = folder.path.ifEmpty { folder.volume.orEmpty() },
                volume = folder.volume,
                needsAccess = included && entry !in access.readableFolders,
            )
        })

    private data class SettingsValues(
        val configuration: LocalSongScanConfig? = null,
        val access: LocalMusicAccess? = null,
        val durationDraft: Int? = null,
        val saving: Boolean = false,
        val pickingFolder: Boolean = false,
        val requestingPermission: Boolean = false,
        @param:StringRes val configurationError: Int? = null,
        @param:StringRes val accessError: Int? = null,
        @param:StringRes val errorMessage: Int? = null,
    )
}

@Immutable
sealed interface LocalSongSettingsState {
    data object Loading : LocalSongSettingsState
    data class Success(val model: LocalSongSettingsUiModel) : LocalSongSettingsState
    data class Empty(val model: LocalSongSettingsUiModel) : LocalSongSettingsState
    data class Error(val model: LocalSongSettingsUiModel?, @param:StringRes val message: Int) : LocalSongSettingsState

    val data: LocalSongSettingsUiModel?
        get() = when (this) {
            Loading -> null
            is Success -> model
            is Empty -> model
            is Error -> model
        }
}

@Immutable
data class LocalSongSettingsUiModel(
    val minimumDurationSeconds: Int,
    val includedFolders: ImmutableList<LocalSongFolderUiModel>,
    val excludedFolders: ImmutableList<LocalSongFolderUiModel>,
    val hasMediaPermission: Boolean,
    val requiresMediaPermission: Boolean,
    val isScanning: Boolean,
    val isSaving: Boolean,
    val canEdit: Boolean,
    val summary: LocalSongScanSummary?,
)

@Immutable
data class LocalSongFolderUiModel(val entry: String, val path: String, val volume: String?, val needsAccess: Boolean)

sealed interface LocalSongSettingsAction {
    data object Back : LocalSongSettingsAction
    data object Reload : LocalSongSettingsAction
    data object Scan : LocalSongSettingsAction
    data object CancelScan : LocalSongSettingsAction
    data object OpenAppSettings : LocalSongSettingsAction
    data class PickFolder(val included: Boolean, val initialUri: Uri? = null) : LocalSongSettingsAction
    data class FolderSelected(val included: Boolean, val uri: Uri?) : LocalSongSettingsAction
    data class RemoveFolder(val entry: String, val included: Boolean) : LocalSongSettingsAction
    data class DurationChanged(val seconds: Int) : LocalSongSettingsAction
    data object SaveDuration : LocalSongSettingsAction
    data class PermissionResult(val granted: Boolean) : LocalSongSettingsAction
    data object ExternalActionFailed : LocalSongSettingsAction
}

sealed interface LocalSongSettingsEvent {
    data object NavigateBack : LocalSongSettingsEvent
    data object RequestAudioPermission : LocalSongSettingsEvent
    data object OpenAppSettings : LocalSongSettingsEvent
    data class PickFolder(val included: Boolean, val initialUri: Uri?) : LocalSongSettingsEvent
}

private sealed interface LocalSongsScanState {
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
