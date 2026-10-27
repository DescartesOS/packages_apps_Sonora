/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package moe.rukamori.archivetune.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.google.common.collect.ImmutableList
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.distinctUntilChanged
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.recap.RecapAppearance
import moe.rukamori.archivetune.recap.RecapChapter
import moe.rukamori.archivetune.recap.RecapData
import moe.rukamori.archivetune.recap.RecapEntry
import moe.rukamori.archivetune.recap.RecapEntryKind
import moe.rukamori.archivetune.recap.RecapMenu
import moe.rukamori.archivetune.recap.RecapMonthState
import moe.rukamori.archivetune.recap.RecapPlayback
import moe.rukamori.archivetune.recap.RecapSelection
import moe.rukamori.archivetune.recap.RecapText
import moe.rukamori.archivetune.ui.component.FloatingBarGlassBackground
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.menu.AlbumMenu
import moe.rukamori.archivetune.ui.menu.ArtistMenu
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.player.rememberOfflineArtworkImageRequest
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import moe.rukamori.archivetune.ui.utils.navigateToAlbum
import moe.rukamori.archivetune.ui.utils.navigateToArtist
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.RecapEvent
import moe.rukamori.archivetune.viewmodels.YearInMusicUiState
import moe.rukamori.archivetune.viewmodels.YearInMusicViewModel

@Immutable
private data class RecapCallbacks(
    val back: () -> Unit,
    val yearPicker: (Boolean) -> Unit,
    val year: (Int) -> Unit,
    val story: (Boolean) -> Unit,
    val chapter: (RecapChapter) -> Unit,
    val month: (Int?) -> Unit,
    val entry: (RecapEntry) -> Unit,
    val menu: (RecapEntry) -> Unit,
    val play: () -> Unit,
    val togglePlayback: () -> Unit,
    val share: () -> Unit,
    val retry: () -> Unit,
    val retryMonth: () -> Unit,
)

@Composable
fun YearInMusicScreen(
    navController: NavController,
    initialYear: Int? = null,
    viewModel: YearInMusicViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val text = remember(context, configuration) { RecapText(context) }
    val player = LocalPlayerConnection.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val menuState = LocalMenuState.current
    val menuScope = androidx.compose.runtime.rememberCoroutineScope()
    val font = LocalFontFamilyResolver.current.resolve(LocalArchiveTuneFontFamily.current).value as Typeface
    val snackbar = remember { SnackbarHostState() }
    val overview = key(state.selection.year) { rememberLazyListState() }
    val monthSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val yearSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val chapters = (state as? YearInMusicUiState.Success)?.data?.chapters ?: ImmutableList.of<RecapChapter>()
    val pager = rememberPagerState(
        initialPage = chapters.indexOf(state.selection.chapter).coerceAtLeast(0),
        pageCount = { chapters.size },
    )
    val (disableBlur) = rememberPreference(DisableBlurKey, false)
    val callbacks = remember(viewModel, navController, font) {
        RecapCallbacks(
            back = { if (!viewModel.back()) navController.navigateUp() },
            yearPicker = viewModel::showYearPicker, year = viewModel::selectYear,
            story = viewModel::showStory, chapter = viewModel::selectChapter, month = viewModel::selectMonth,
            entry = viewModel::openEntry, menu = viewModel::showMenu, play = { viewModel.play() },
            togglePlayback = viewModel::togglePlayback, share = { viewModel.share(font) },
            retry = viewModel::reload, retryMonth = viewModel::reloadMonth,
        )
    }

    DisposableEffect(viewModel, player, lifecycleOwner) {
        viewModel.attachPlayer(player)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.refreshCalendar()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.attachPlayer(null)
        }
    }
    LaunchedEffect(viewModel, initialYear) { viewModel.initialize(initialYear) }
    LaunchedEffect(state.selection.story) {
        navController.currentBackStackEntry?.savedStateHandle?.set("recap_immersive", state.selection.story)
    }
    LaunchedEffect(state.selection.chapter, state.selection.story, chapters) {
        if (state.selection.story && !pager.isScrollInProgress) {
            val index = chapters.indexOf(state.selection.chapter)
            if (index >= 0 && pager.currentPage != index) pager.scrollToPage(index)
        }
    }
    LaunchedEffect(pager, chapters, state.selection.story) {
        if (state.selection.story) snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
            chapters.getOrNull(page)?.let(viewModel::selectChapter)
        }
    }
    LaunchedEffect(state.selection.actionMessage) {
        state.selection.actionMessage?.let {
            snackbar.showSnackbar(context.getString(it))
            viewModel.dismissMessage()
        }
    }
    LaunchedEffect(viewModel, lifecycleOwner, navController, context, menuState, menuScope) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is RecapEvent.OpenArtist -> navController.navigateToArtist(event.id)
                    is RecapEvent.OpenAlbum -> navController.navigateToAlbum(event.id)
                    is RecapEvent.ShowMenu -> menuState.show {
                        when (val menu = event.menu) {
                            is RecapMenu.SongMenu -> SongMenu(originalSong = menu.song, navController = navController, onDismiss = menuState::dismiss)
                            is RecapMenu.ArtistMenu -> ArtistMenu(originalArtist = menu.artist, coroutineScope = menuScope, onDismiss = menuState::dismiss)
                            is RecapMenu.AlbumMenu -> AlbumMenu(originalAlbum = menu.album, navController = navController, onDismiss = menuState::dismiss)
                        }
                    }
                    is RecapEvent.Share -> try {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, event.uri)
                            clipData = ClipData.newRawUri(null, event.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_summary)))
                    } catch (_: ActivityNotFoundException) {
                        viewModel.shareLaunchFailed()
                    } catch (_: SecurityException) {
                        viewModel.shareLaunchFailed()
                    }
                }
            }
        }
    }
    BackHandler(state.selection.month != null || state.selection.story || state.selection.yearPickerOpen, callbacks.back)

    val colorScheme = MaterialTheme.colorScheme
    val storyColors = remember {
        darkColorScheme(
            primary = Color(RecapAppearance.Foreground), onPrimary = Color(RecapAppearance.Background),
            background = Color(RecapAppearance.Background), surface = Color(RecapAppearance.Background),
            surfaceContainer = Color(RecapAppearance.Surface), surfaceContainerHigh = Color(RecapAppearance.Surface),
            onBackground = Color(RecapAppearance.Foreground), onSurface = Color(RecapAppearance.Foreground),
            onSurfaceVariant = Color(RecapAppearance.Secondary),
        )
    }
    MaterialTheme(colorScheme = if (state.selection.story) storyColors else colorScheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                RecapContent(state, overview, pager, text, disableBlur, callbacks)
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }
        }
        MaterialTheme(colorScheme = colorScheme) {
            if (state.selection.yearPickerOpen) {
                YearPicker(state.selection, yearSheet, callbacks)
            }
            val success = state as? YearInMusicUiState.Success
            if (success != null && success.selection.month != null) {
                MonthDetails(success, monthSheet, text, callbacks)
            }
        }
    }
}

@Composable
private fun RecapContent(
    state: YearInMusicUiState,
    overview: LazyListState,
    pager: PagerState,
    text: RecapText,
    disableBlur: Boolean,
    callbacks: RecapCallbacks,
) {
    val haze = rememberHazeState()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            when (state) {
                is YearInMusicUiState.Loading -> RecapStatus(R.string.recap_loading, loading = true)
                is YearInMusicUiState.Empty -> RecapStatus(R.string.year_in_music_empty_title, detail = stringResource(R.string.year_in_music_empty_subtitle, state.selection.year))
                is YearInMusicUiState.Error -> RecapStatus(state.messageRes, retry = callbacks.retry)
                is YearInMusicUiState.Success -> if (state.selection.story) {
                    RecapStory(state, pager, text, callbacks)
                } else {
                    RecapOverview(state.data, state.selection, overview, text, callbacks)
                }
            }
        }
        RecapHeader(state, haze, disableBlur, callbacks, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun RecapHeader(
    state: YearInMusicUiState,
    haze: HazeState,
    disableBlur: Boolean,
    callbacks: RecapCallbacks,
    modifier: Modifier = Modifier,
) {
    val shape = remember { RoundedCornerShape(20.dp) }
    val selection = state.selection
    val success = state as? YearInMusicUiState.Success
    val shareEnabled = success != null && !selection.sharing
    Box(modifier.padding(horizontal = 20.dp, vertical = 8.dp).widthIn(max = 720.dp).fillMaxWidth()) {
        if (!disableBlur) {
            FloatingBarGlassBackground(haze, shape, pureBlack = selection.story, modifier = Modifier.matchParentSize())
        } else {
            Box(Modifier.matchParentSize().clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh))
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RecapIcon(if (selection.story) R.drawable.close else R.drawable.arrow_back,
                if (selection.story) R.string.close else R.string.recap_back, callbacks.back)
            if (selection.story && success != null) {
                val index = success.data.chapters.indexOf(selection.chapter)
                Text(stringResource(R.string.recap_progress, index + 1, success.data.chapters.size),
                    Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.labelLarge)
            } else {
                TextButton(onClick = { callbacks.yearPicker(true) }, modifier = Modifier.weight(1f)) {
                    Text(selection.year.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Icon(painterResource(R.drawable.expand_more), null, Modifier.size(18.dp))
                }
            }
            if (selection.sharing) {
                val description = stringResource(R.string.recap_preparing_share)
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp).semantics { contentDescription = description }, strokeWidth = 2.dp)
                }
            } else RecapIcon(R.drawable.share, R.string.share, callbacks.share, shareEnabled)
        }
    }
}

@Composable
private fun RecapOverview(
    data: RecapData,
    selection: RecapSelection,
    listState: LazyListState,
    text: RecapText,
    callbacks: RecapCallbacks,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 96.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        item("hero", "hero") {
            Column(Modifier.widthIn(max = 680.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(if (data.year == data.throughDate.year) R.string.recap_year_so_far else R.string.recap_recorded_listening),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.recap_year_title, data.year), style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                }
                ArtworkHero(data.songs)
                RecapTotals(data, text)
                Button(onClick = { callbacks.story(true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface)) {
                    Text(stringResource(R.string.recap_highlights), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(10.dp))
                    Icon(painterResource(R.drawable.arrow_forward), null, Modifier.size(20.dp))
                }
            }
        }
        rankings(data, selection.playback, text, callbacks)
        item("months", "months") {
            Column(Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
                SectionTitle(stringResource(R.string.recap_months))
                MonthlyListening(data, text, callbacks.month)
            }
        }
        item("habits", "habits") {
            Column(Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
                SectionTitle(stringResource(R.string.recap_habits))
                ListeningHabits(data, text)
            }
        }
        item("playback", "playback") { PlaybackStatus(selection.playback, callbacks.togglePlayback) }
        item("attribution", "attribution") {
            Text(stringResource(R.string.recap_recorded_listening), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

private fun LazyListScope.rankings(data: RecapData, playback: RecapPlayback, text: RecapText, callbacks: RecapCallbacks) {
    if (data.songs.isNotEmpty()) {
        item("songs_heading", "heading") {
            Row(Modifier.widthIn(max = 680.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(stringResource(R.string.top_songs), Modifier.weight(1f))
                TextButton(callbacks.play, enabled = playback.available) { Text(stringResource(R.string.play_all)) }
            }
        }
        itemsIndexed(data.songs, key = { _, entry -> "song_${entry.id}" }, contentType = { _, _ -> "ranking" }) { index, entry ->
            RankedEntry(entry, index + 1, playback, text, callbacks)
        }
    }
    if (data.artists.isNotEmpty()) {
        item("artists_heading", "heading") { SectionTitle(stringResource(R.string.top_artists), Modifier.widthIn(max = 680.dp).fillMaxWidth()) }
        itemsIndexed(data.artists, key = { _, entry -> "artist_${entry.id}" }, contentType = { _, _ -> "ranking" }) { index, entry ->
            RankedEntry(entry, index + 1, playback, text, callbacks)
        }
    }
    if (data.albums.isNotEmpty()) {
        item("albums_heading", "heading") { SectionTitle(stringResource(R.string.albums), Modifier.widthIn(max = 680.dp).fillMaxWidth()) }
        itemsIndexed(data.albums, key = { _, entry -> "album_${entry.id}" }, contentType = { _, _ -> "ranking" }) { index, entry ->
            RankedEntry(entry, index + 1, playback, text, callbacks)
        }
    }
}

@Composable
private fun RankedEntry(entry: RecapEntry, rank: Int, playback: RecapPlayback, text: RecapText, callbacks: RecapCallbacks) {
    val active = entry.kind == RecapEntryKind.SONG && playback.currentId == entry.id
    val onClick = remember(entry, callbacks) { { callbacks.entry(entry) } }
    val onLongClick = remember(entry, callbacks) { { callbacks.menu(entry) } }
    Row(
        Modifier.widthIn(max = 680.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .combinedClickable(enabled = entry.kind != RecapEntryKind.SONG || playback.available,
                onClick = onClick, onLongClick = onLongClick).padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rank.toString(), Modifier.width(20.dp), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            RecapArtwork(entry, Modifier.size(56.dp))
            if (active) Box(Modifier.matchParentSize().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center) {
                Icon(painterResource(if (playback.playing) R.drawable.pause else R.drawable.play),
                    stringResource(if (playback.playing) R.string.recap_pause else R.string.play), Modifier.size(24.dp), tint = Color.White)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (entry.subtitle.isNotBlank()) Text(entry.subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text.minutes(entry.milliseconds) + " · " + text.plays(entry.plays), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        if (playback.available) RecapIcon(R.drawable.more_horiz, R.string.more, onLongClick)
    }
}

@Composable
private fun ArtworkHero(entries: ImmutableList<RecapEntry>, modifier: Modifier = Modifier) {
    val artwork = remember(entries) { entries.take(3) }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(Modifier.widthIn(max = if (artwork.size == 1) 260.dp else 480.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            artwork.forEachIndexed { index, entry ->
                RecapArtwork(entry, Modifier.weight(if (index == 0) 1.25f else 1f).aspectRatio(1f), radius = 18)
            }
        }
    }
}

@Composable
private fun RecapArtwork(entry: RecapEntry, modifier: Modifier = Modifier, radius: Int = 12) {
    val request = rememberOfflineArtworkImageRequest(entry.artwork)
    val shape = remember(entry.kind, radius) { if (entry.kind == RecapEntryKind.ARTIST) CircleShape else RoundedCornerShape(radius.dp) }
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.small_icon), null, Modifier.fillMaxSize(0.38f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        AsyncImage(request, null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun RecapTotals(data: RecapData, text: RecapText) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            Text(text.number(data.milliseconds / 60_000), style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold, letterSpacing = (-2).sp)
            Text(stringResource(R.string.year_in_music_minutes_unit), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f)) {
                Text(text.number(data.plays), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.year_in_music_plays_label), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text(text.number(data.distinctTracks), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.recap_distinct_tracks), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier.padding(top = 24.dp, bottom = 12.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun MonthlyListening(data: RecapData, text: RecapText, onMonth: (Int?) -> Unit) {
    val peak = remember(data.months) { data.months.maxOf { it.milliseconds }.coerceAtLeast(1L) }
    val selectedColor = MaterialTheme.colorScheme.onSurface
    val secondaryColor = MaterialTheme.colorScheme.surfaceContainerHigh
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        items(data.months, key = { it.month }, contentType = { "month" }) { month ->
            val description = stringResource(R.string.recap_month_accessibility, text.month(month.month),
                if (month.available) text.minutes(month.milliseconds) else stringResource(R.string.recap_future_month))
            val click = remember(month.month, onMonth) { { onMonth(month.month) } }
            Column(Modifier.width(60.dp).clip(RoundedCornerShape(10.dp)).combinedClickable(
                enabled = month.available, onClick = click).padding(vertical = 8.dp).semantics(mergeDescendants = true) {
                contentDescription = description
            }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(22.dp).height(112.dp).clip(RoundedCornerShape(5.dp)).background(secondaryColor),
                    contentAlignment = Alignment.BottomCenter) {
                    if (month.available && month.milliseconds > 0) {
                        Box(Modifier.fillMaxWidth().height((112f * month.milliseconds / peak).coerceAtLeast(3f).dp)
                            .background(selectedColor))
                    }
                }
                Text(text.month(month.month, short = true), modifier = Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.labelSmall,
                    color = if (month.available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ListeningHabits(data: RecapData, text: RecapText) {
    val facts = remember(data.habits, data.month, text) { text.facts(data) }
    Column {
        facts.forEachIndexed { index, fact ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(fact.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fact.value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PlaybackStatus(playback: RecapPlayback, onToggle: () -> Unit) {
    if (!playback.available || playback.currentId == null) return
    Row(Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.equalizer), null, Modifier.padding(end = 12.dp).size(20.dp))
        Text(playback.title, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
        RecapIcon(if (playback.playing) R.drawable.pause else R.drawable.play,
            if (playback.playing) R.string.recap_pause else R.string.play, onToggle)
    }
}

@Composable
private fun RecapStory(state: YearInMusicUiState.Success, pager: PagerState, text: RecapText, callbacks: RecapCallbacks) {
    Column(Modifier.fillMaxSize().padding(top = 80.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            state.data.chapters.forEach { chapter ->
                val active = state.data.chapters.indexOf(chapter) <= state.data.chapters.indexOf(state.selection.chapter)
                Box(Modifier.weight(1f).height(3.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (active) 0.9f else 0.2f)))
            }
        }
        HorizontalPager(state = pager, key = { state.data.chapters[it].name }, modifier = Modifier.weight(1f)) { page ->
            state.data.chapters.getOrNull(page)?.let { chapter ->
                StoryChapter(state.data, chapter, state.selection.playback, text, callbacks)
            }
        }
        PlaybackStatus(state.selection.playback, callbacks.togglePlayback)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            val index = state.data.chapters.indexOf(state.selection.chapter)
            RecapIcon(R.drawable.arrow_back, R.string.recap_previous,
                { callbacks.chapter(state.data.chapters[index - 1]) }, enabled = index > 0)
            Text(text.chapterTitle(state.selection.chapter), Modifier.weight(1f).padding(horizontal = 12.dp),
                style = MaterialTheme.typography.labelLarge, maxLines = 2)
            RecapIcon(R.drawable.arrow_forward, R.string.recap_next,
                { callbacks.chapter(state.data.chapters[index + 1]) }, enabled = index < state.data.chapters.lastIndex)
        }
    }
}

@Composable
private fun StoryChapter(data: RecapData, chapter: RecapChapter, playback: RecapPlayback, text: RecapText, callbacks: RecapCallbacks) {
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
        item("title", "heading") {
            Column(Modifier.widthIn(max = 680.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text.period(data), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text.chapterTitle(chapter), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            }
        }
        when (chapter) {
            RecapChapter.TIME -> item("time", "hero") {
                Column(Modifier.widthIn(max = 680.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(32.dp)) {
                    ArtworkHero(data.songs)
                    RecapTotals(data, text)
                }
            }
            RecapChapter.TRACK -> data.songs.firstOrNull()?.let { entry ->
                item("track", "hero") {
                    Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        RecapArtwork(entry, Modifier.fillMaxWidth().aspectRatio(1f), radius = 20)
                        Text(entry.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(entry.subtitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text.minutes(entry.milliseconds) + " · " + text.plays(entry.plays), style = MaterialTheme.typography.bodyMedium)
                        val isPlaying = playback.currentId == entry.id && playback.playing
                        Button(onClick = { callbacks.entry(entry) }, enabled = playback.available,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                            Text(stringResource(if (isPlaying) R.string.recap_pause else R.string.play))
                        }
                    }
                }
            }
            RecapChapter.SONGS, RecapChapter.ARTISTS, RecapChapter.ALBUMS -> {
                val entries = when (chapter) {
                    RecapChapter.ARTISTS -> data.artists
                    RecapChapter.ALBUMS -> data.albums
                    else -> data.songs
                }
                item("artwork", "artwork") { entries.firstOrNull()?.let { RecapArtwork(it, Modifier.size(180.dp), radius = 20) } }
                itemsIndexed(entries, key = { _, entry -> entry.id }, contentType = { _, _ -> "ranking" }) { index, entry ->
                    RankedEntry(entry, index + 1, playback, text, callbacks)
                }
                item("ranking_basis", "label") {
                    Text(stringResource(R.string.recap_ranked_by_time), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            RecapChapter.MONTHS -> item("months", "months") { MonthlyListening(data, text, callbacks.month) }
            RecapChapter.HABITS -> item("habits", "habits") { ListeningHabits(data, text) }
            RecapChapter.SUMMARY -> {
                item("summary", "hero") {
                    Column(Modifier.widthIn(max = 680.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        ArtworkHero(data.songs)
                        RecapTotals(data, text)
                    }
                }
                rankings(data, playback, text, callbacks)
            }
        }
    }
}

@Composable
private fun YearPicker(selection: RecapSelection, sheetState: SheetState, callbacks: RecapCallbacks) {
    ModalBottomSheet(onDismissRequest = { callbacks.yearPicker(false) }, sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            item("title", "heading") { SectionTitle(stringResource(R.string.recap_choose_year)) }
            items(selection.years, key = { it }, contentType = { "year" }) { year ->
                TextButton(onClick = { callbacks.year(year) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(year.toString(), style = MaterialTheme.typography.titleLarge,
                        fontWeight = if (year == selection.year) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun MonthDetails(state: YearInMusicUiState.Success, sheetState: SheetState, text: RecapText, callbacks: RecapCallbacks) {
    val month = state.selection.month ?: return
    ModalBottomSheet(onDismissRequest = { callbacks.month(null) }, sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.9f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text.period(state.selection.year, month), Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (state.selection.sharing) {
                    val description = stringResource(R.string.recap_preparing_share)
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp).semantics { contentDescription = description }, strokeWidth = 2.dp)
                    }
                } else RecapIcon(R.drawable.share, R.string.share, callbacks.share,
                    state.monthState is RecapMonthState.Success)
                RecapIcon(R.drawable.close, R.string.close, { callbacks.month(null) })
            }
            when (val monthState = state.monthState) {
                RecapMonthState.Loading -> RecapStatus(R.string.recap_loading, loading = true, modifier = Modifier.height(220.dp))
                RecapMonthState.Empty -> RecapStatus(R.string.recap_month_empty, modifier = Modifier.heightIn(min = 180.dp))
                is RecapMonthState.Error -> RecapStatus(monthState.messageRes, retry = callbacks.retryMonth,
                    modifier = Modifier.heightIn(min = 180.dp))
                is RecapMonthState.Success -> LazyColumn(contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    item("totals", "totals") { RecapTotals(monthState.data, text) }
                    rankings(monthState.data, state.selection.playback, text, callbacks)
                    item("habits", "habits") {
                        Column(Modifier.fillMaxWidth()) {
                            SectionTitle(stringResource(R.string.recap_habits))
                            ListeningHabits(monthState.data, text)
                        }
                    }
                    item("playback", "playback") { PlaybackStatus(state.selection.playback, callbacks.togglePlayback) }
                }
            }
        }
    }
}

@Composable
private fun RecapStatus(message: Int, modifier: Modifier = Modifier.fillMaxSize(), detail: String? = null,
                        loading: Boolean = false, retry: (() -> Unit)? = null) {
    Box(modifier.padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Text(stringResource(message), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (retry != null) TextButton(retry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
private fun RecapIcon(icon: Int, label: Int, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(painterResource(icon), stringResource(label), Modifier.size(22.dp))
    }
}
