/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.graphics.Typeface
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.recap.ObserveRecapUseCase
import moe.rukamori.archivetune.recap.RecapActionsUseCase
import moe.rukamori.archivetune.recap.RecapChapter
import moe.rukamori.archivetune.recap.RecapData
import moe.rukamori.archivetune.recap.RecapEntry
import moe.rukamori.archivetune.recap.RecapEntryKind
import moe.rukamori.archivetune.recap.RecapMenu
import moe.rukamori.archivetune.recap.RecapMonthState
import moe.rukamori.archivetune.recap.RecapSelection
import moe.rukamori.archivetune.utils.reportException
import java.time.LocalDate
import javax.inject.Inject

sealed interface YearInMusicUiState {
    val selection: RecapSelection

    @Immutable data class Loading(override val selection: RecapSelection) : YearInMusicUiState
    @Immutable data class Success(
        override val selection: RecapSelection,
        val data: RecapData,
        val monthState: RecapMonthState = RecapMonthState.Loading,
    ) : YearInMusicUiState
    @Immutable data class Empty(override val selection: RecapSelection) : YearInMusicUiState
    @Immutable data class Error(override val selection: RecapSelection, val messageRes: Int) : YearInMusicUiState
}

sealed interface RecapEvent {
    data class OpenArtist(val id: String) : RecapEvent
    data class OpenAlbum(val id: String) : RecapEvent
    data class ShowMenu(val menu: RecapMenu) : RecapEvent
    data class Share(val uri: Uri) : RecapEvent
}

@HiltViewModel
class YearInMusicViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val observeRecap: ObserveRecapUseCase,
    private val actions: RecapActionsUseCase,
) : ViewModel() {
    private val currentYear: Int
        get() = LocalDate.now().year
    private var calendarDate = LocalDate.now()
    private val initialYear = (savedState.get<Int>("recap_year") ?: savedState.get<Int>("year"))
        ?.takeIf { it in 1..currentYear } ?: currentYear
    private val initialSelection = RecapSelection(
        year = initialYear,
        years = ImmutableList.of(initialYear),
        story = savedState["recap_story"] ?: false,
        chapter = savedState.get<String>("recap_chapter")?.let { name -> RecapChapter.entries.find { it.name == name } }
            ?: RecapChapter.TIME,
        month = savedState.get<Int>("recap_month")?.takeIf { it in 1..12 },
    )
    private val state = MutableStateFlow<YearInMusicUiState>(YearInMusicUiState.Loading(initialSelection))
    val uiState: StateFlow<YearInMusicUiState> = state.asStateFlow()
    private val eventChannel = Channel<RecapEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var yearJob: Job? = null
    private var monthJob: Job? = null
    private var actionJob: Job? = null
    private var shareJob: Job? = null
    private var shareRequest = 0L

    init {
        reload()
        viewModelScope.launch {
            actions.playback().collect { playback -> updateSelection { it.copy(playback = playback) } }
        }
    }

    fun attachPlayer(connection: PlayerConnection?) = actions.attach(connection)

    fun initialize(year: Int?) {
        if (savedState.get<Boolean>("recap_initialized") == true) return
        savedState["recap_initialized"] = true
        if (year != null && year in 1..currentYear && year != state.value.selection.year) selectYear(year)
    }

    fun refreshCalendar() {
        val today = LocalDate.now()
        if (calendarDate == today) return
        calendarDate = today
        reload()
    }

    fun back(): Boolean {
        val selection = state.value.selection
        when {
            selection.yearPickerOpen -> showYearPicker(false)
            selection.month != null -> selectMonth(null)
            selection.story -> showStory(false)
            else -> return false
        }
        return true
    }

    fun reload() {
        yearJob?.cancel()
        monthJob?.cancel()
        monthJob = null
        val year = state.value.selection.year
        state.value = YearInMusicUiState.Loading(state.value.selection.copy(actionMessage = null))
        yearJob = viewModelScope.launch {
            observeRecap(year).catch { failure ->
                if (failure is CancellationException) throw failure
                reportException(failure)
                state.value = YearInMusicUiState.Error(state.value.selection, R.string.recap_load_error)
            }.collect { result ->
                if (year !in result.years) {
                    selectYear(result.years.first())
                    return@collect
                }
                val selection = state.value.selection.copy(
                    years = result.years,
                    chapter = state.value.selection.chapter.takeIf { it in result.data.chapters } ?: RecapChapter.TIME,
                    month = state.value.selection.month?.takeIf { requested ->
                        result.data.months.any { it.month == requested && it.available }
                    },
                )
                state.value = if (result.data.milliseconds == 0L) {
                    cancelShare()
                    monthJob?.cancel()
                    monthJob = null
                    savedState["recap_story"] = false
                    savedState["recap_month"] = null
                    YearInMusicUiState.Empty(selection.copy(story = false, month = null, sharing = false))
                } else {
                    YearInMusicUiState.Success(selection, result.data,
                        (state.value as? YearInMusicUiState.Success)?.monthState ?: RecapMonthState.Loading)
                }
                if (selection.month != null && monthJob == null && state.value is YearInMusicUiState.Success) loadMonth(selection.month)
            }
        }
    }

    fun selectYear(year: Int) {
        if (year !in 1..currentYear) return
        if (year == state.value.selection.year) {
            showYearPicker(false)
            return
        }
        cancelShare()
        actionJob?.cancel()
        monthJob?.cancel()
        monthJob = null
        savedState["recap_year"] = year
        savedState["recap_month"] = null
        savedState["recap_story"] = false
        savedState["recap_chapter"] = RecapChapter.TIME.name
        updateSelection { it.copy(year = year, story = false, month = null, chapter = RecapChapter.TIME, yearPickerOpen = false) }
        reload()
    }

    fun showYearPicker(show: Boolean) = updateSelection { it.copy(yearPickerOpen = show) }

    fun showStory(show: Boolean) {
        if (show && state.value !is YearInMusicUiState.Success) return
        cancelShare()
        savedState["recap_story"] = show
        updateSelection { it.copy(story = show) }
    }

    fun selectChapter(chapter: RecapChapter) {
        val success = state.value as? YearInMusicUiState.Success ?: return
        if (chapter !in success.data.chapters) return
        if (chapter == state.value.selection.chapter) return
        cancelShare()
        savedState["recap_chapter"] = chapter.name
        updateSelection { it.copy(chapter = chapter) }
    }

    fun selectMonth(month: Int?) {
        val success = state.value as? YearInMusicUiState.Success ?: return
        if (month != null && success.data.months.none { it.month == month && it.available }) return
        cancelShare()
        actionJob?.cancel()
        monthJob?.cancel()
        monthJob = null
        savedState["recap_month"] = month
        updateSelection { it.copy(month = month) }
        if (month != null) loadMonth(month)
    }

    fun reloadMonth() { state.value.selection.month?.let { selectMonth(it) } }

    private fun loadMonth(month: Int) {
        val year = state.value.selection.year
        state.update { current -> (current as? YearInMusicUiState.Success)?.copy(monthState = RecapMonthState.Loading) ?: current }
        monthJob = viewModelScope.launch {
            observeRecap(year, month).catch { failure ->
                if (failure is CancellationException) throw failure
                reportException(failure)
                updateMonth(RecapMonthState.Error(R.string.recap_load_error))
            }.collect { result ->
                updateMonth(if (result.data.milliseconds == 0L) RecapMonthState.Empty else RecapMonthState.Success(result.data))
            }
        }
    }

    private fun updateMonth(month: RecapMonthState) {
        state.update { current -> (current as? YearInMusicUiState.Success)?.copy(monthState = month) ?: current }
    }

    fun openEntry(entry: RecapEntry) {
        when (entry.kind) {
            RecapEntryKind.SONG -> play(entry.id)
            RecapEntryKind.ARTIST -> runAction { eventChannel.send(RecapEvent.OpenArtist(entry.id)) }
            RecapEntryKind.ALBUM -> runAction { eventChannel.send(RecapEvent.OpenAlbum(entry.id)) }
        }
    }

    fun showMenu(entry: RecapEntry) = runAction {
        eventChannel.send(RecapEvent.ShowMenu(actions.menu(entry)))
    }

    fun play(songId: String? = null) {
        val data = currentData() ?: return
        runAction { actions.play(data, songId, data.year.toString()) }
    }

    fun togglePlayback() = runAction { actions.togglePlayback() }

    private fun runAction(block: suspend () -> Unit) {
        if (actionJob?.isActive == true) return
        actionJob = viewModelScope.launch {
            try { block() } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                reportException(failure)
                updateSelection { it.copy(actionMessage = R.string.recap_action_error) }
            }
        }
    }

    fun share(font: Typeface) {
        if (shareJob?.isActive == true) return
        val data = currentData() ?: return
        val selection = state.value.selection
        val chapter = if (selection.month == null && selection.story) selection.chapter else RecapChapter.SUMMARY
        val request = ++shareRequest
        updateSelection { it.copy(sharing = true, actionMessage = null) }
        shareJob = viewModelScope.launch {
            try {
                val uri = actions.share(data, chapter, font)
                if (request == shareRequest) eventChannel.send(RecapEvent.Share(uri))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                reportException(failure)
                if (request == shareRequest) updateSelection { it.copy(actionMessage = R.string.recap_share_error) }
            } finally {
                if (request == shareRequest) updateSelection { it.copy(sharing = false) }
            }
        }
    }

    fun shareLaunchFailed() = updateSelection { it.copy(actionMessage = R.string.recap_share_error) }
    fun dismissMessage() = updateSelection { it.copy(actionMessage = null) }

    private fun cancelShare() {
        shareRequest++
        shareJob?.cancel()
        updateSelection { it.copy(sharing = false) }
    }

    private fun currentData(): RecapData? {
        val success = state.value as? YearInMusicUiState.Success ?: return null
        return if (success.selection.month == null) success.data else (success.monthState as? RecapMonthState.Success)?.data
    }

    private inline fun updateSelection(transform: (RecapSelection) -> RecapSelection) {
        state.update { current ->
            val selection = transform(current.selection)
            when (current) {
                is YearInMusicUiState.Loading -> current.copy(selection = selection)
                is YearInMusicUiState.Success -> current.copy(selection = selection)
                is YearInMusicUiState.Empty -> current.copy(selection = selection)
                is YearInMusicUiState.Error -> current.copy(selection = selection)
            }
        }
    }
}
