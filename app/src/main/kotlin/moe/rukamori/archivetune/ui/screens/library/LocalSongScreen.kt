/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.CONTENT_TYPE_HEADER
import moe.rukamori.archivetune.constants.CONTENT_TYPE_SONG
import moe.rukamori.archivetune.constants.LocalSongsSortDescendingKey
import moe.rukamori.archivetune.constants.LocalSongsSortTypeKey
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.component.SortHeader
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.screens.settings.LocalSongSettingsRoute
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LocalSongsViewModel
import java.text.Collator
import java.time.LocalDateTime
import java.util.Locale

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun LocalSongScreen(
    navController: NavController,
    viewModel: LocalSongsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var query by rememberSaveable { mutableStateOf("") }
    val (sortDescending, onSortDescendingChange) = rememberPreference(LocalSongsSortDescendingKey, true)
    val (sortTypeName, onSortTypeNameChange) = rememberPreference(LocalSongsSortTypeKey, LocalSongSortType.MODIFIED.name)
    val sortType = remember(sortTypeName) { LocalSongSortType.valueOf(sortTypeName) }
    val openScanSettings = remember(navController) {
        { navController.navigate(LocalSongSettingsRoute) { launchSingleTop = true } }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onLibraryOpened() }

    val collator =
        remember {
            Collator.getInstance(Locale.getDefault()).apply {
                strength = Collator.PRIMARY
            }
        }
    val bottomContentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding() + 20.dp

    val visibleSongs by remember(songs, query, sortType, sortDescending, collator) {
        derivedStateOf {
            val normalizedQuery = query.trim()
            val filteredSongs =
                if (normalizedQuery.isBlank()) {
                    songs
                } else {
                    songs.filter { song ->
                        song.song.title.contains(normalizedQuery, ignoreCase = true) ||
                            song.song.albumName
                                .orEmpty()
                                .contains(normalizedQuery, ignoreCase = true) ||
                            song.artists.any { artist -> artist.name.contains(normalizedQuery, ignoreCase = true) }
                    }
                }

            val sortedSongs =
                when (sortType) {
                    LocalSongSortType.MODIFIED -> {
                        filteredSongs.sortedBy { song ->
                            song.song.dateModified ?: LocalDateTime.MIN
                        }
                    }

                    LocalSongSortType.NAME -> {
                        filteredSongs.sortedWith(compareBy(collator) { song -> song.song.title })
                    }

                    LocalSongSortType.ARTIST -> {
                        filteredSongs.sortedWith(
                            compareBy(collator) { song ->
                                song.artists.joinToString(separator = "") { artist -> artist.name }
                            },
                        )
                    }

                    LocalSongSortType.ALBUM -> {
                        filteredSongs.sortedWith(
                            compareBy(collator) { song -> song.song.albumName.orEmpty() },
                        )
                    }
                }

            if (sortDescending) sortedSongs.asReversed() else sortedSongs
        }
    }

    val queueItems = remember(visibleSongs) { visibleSongs.map { it.toMediaItem() } }

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = {
                    fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                        fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
                },
                label = "localSongTopBar",
            ) { searching ->
                if (searching) {
                    SearchBar(
                        inputField = {
                            SearchBarDefaults.InputField(
                                query = query,
                                onQueryChange = { query = it },
                                onSearch = { isSearchActive = false },
                                expanded = false,
                                onExpandedChange = {},
                                placeholder = {
                                    Text(text = stringResource(R.string.search_library))
                                },
                                leadingIcon = {
                                    IconButton(
                                        onClick = {
                                            query = ""
                                            isSearchActive = false
                                        },
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.arrow_back),
                                            contentDescription = stringResource(R.string.back_button_desc),
                                        )
                                    }
                                },
                                trailingIcon =
                                    if (query.isNotEmpty()) {
                                        {
                                            IconButton(onClick = { query = "" }) {
                                                Icon(
                                                    painter = painterResource(R.drawable.close),
                                                    contentDescription = stringResource(R.string.close),
                                                )
                                            }
                                        }
                                    } else {
                                        null
                                    },
                            )
                        },
                        expanded = false,
                        onExpandedChange = {},
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 8.dp, bottom = 4.dp),
                    ) {}
                } else {
                    LargeFlexibleTopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.local_history),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = navController::navigateUp) {
                                Icon(
                                    painter = painterResource(R.drawable.arrow_back),
                                    contentDescription = null,
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.search),
                                    contentDescription = stringResource(R.string.search),
                                )
                            }
                            IconButton(onClick = openScanSettings) {
                                Icon(
                                    painter = painterResource(R.drawable.settings),
                                    contentDescription = stringResource(R.string.settings),
                                )
                            }
                        },
                        colors =
                            TopAppBarDefaults.largeTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                            ),
                        scrollBehavior = scrollBehavior,
                    )
                }
            }
        },
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding =
                PaddingValues(
                    top = 12.dp,
                    bottom = bottomContentPadding,
                ),
        ) {
            item(
                key = "controls",
                contentType = CONTENT_TYPE_HEADER,
            ) {
                LocalSongControlsCard(
                    sortType = sortType,
                    sortDescending = sortDescending,
                    visibleSongCount = visibleSongs.size,
                    shuffleEnabled = queueItems.isNotEmpty(),
                    onSortTypeChange = { onSortTypeNameChange(it.name) },
                    onSortDescendingChange = onSortDescendingChange,
                    onShuffleClick = {
                        if (queueItems.isNotEmpty()) {
                            playerConnection.playQueue(
                                ListQueue(
                                    title =
                                        if (query.isBlank()) {
                                            context.getString(R.string.local_history)
                                        } else {
                                            context.getString(R.string.queue_searched_songs)
                                        },
                                    items = queueItems.shuffled(),
                                ),
                            )
                        }
                    },
                )
            }

            if (visibleSongs.isEmpty()) {
                item(
                    key = "empty",
                    contentType = CONTENT_TYPE_HEADER,
                ) {
                    LocalSongEmptyState(query = query)
                }
            } else {
                item(
                    key = "divider",
                    contentType = CONTENT_TYPE_HEADER,
                ) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }

                itemsIndexed(
                    items = visibleSongs,
                    key = { _, item -> item.id },
                    contentType = { _, _ -> CONTENT_TYPE_SONG },
                ) { index, song ->
                    SongListItem(
                        song = song,
                        showInLibraryIcon = false,
                        showDownloadIcon = false,
                        showSongIconPlaceholder = true,
                        isActive = song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    menuState.show {
                                        SongMenu(
                                            originalSong = song,
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.more_vert),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (song.id == mediaMetadata?.id) {
                                            playerConnection.player.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title =
                                                        if (query.isBlank()) {
                                                            context.getString(R.string.local_history)
                                                        } else {
                                                            context.getString(R.string.queue_searched_songs)
                                                        },
                                                    items = queueItems,
                                                    startIndex = index,
                                                ),
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show {
                                            SongMenu(
                                                originalSong = song,
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                ).padding(horizontal = 16.dp)
                                .animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalSongControlsCard(
    sortType: LocalSongSortType,
    sortDescending: Boolean,
    visibleSongCount: Int,
    shuffleEnabled: Boolean,
    onSortTypeChange: (LocalSongSortType) -> Unit,
    onSortDescendingChange: (Boolean) -> Unit,
    onShuffleClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 8.dp),
    ) {
        SortHeader(
            sortType = sortType,
            sortDescending = sortDescending,
            onSortTypeChange = onSortTypeChange,
            onSortDescendingChange = onSortDescendingChange,
            sortTypeText = { selectedSort ->
                when (selectedSort) {
                    LocalSongSortType.MODIFIED -> R.string.sort_by_last_updated
                    LocalSongSortType.NAME -> R.string.sort_by_name
                    LocalSongSortType.ARTIST -> R.string.sort_by_artist
                    LocalSongSortType.ALBUM -> R.string.sort_by_album
                }
            },
        )

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
            onClick = onShuffleClick,
            enabled = shuffleEnabled,
        ) {
            Icon(
                painter = painterResource(R.drawable.shuffle),
                contentDescription = stringResource(R.string.shuffle),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = pluralStringResource(R.plurals.n_song, visibleSongCount, visibleSongCount),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun LocalSongEmptyState(query: String) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Icon(
                painter = painterResource(if (query.isBlank()) R.drawable.music_note else R.drawable.search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text =
                    if (query.isBlank()) {
                        stringResource(R.string.local_songs_empty_title)
                    } else {
                        stringResource(R.string.local_songs_no_matches_title)
                    },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text =
                    if (query.isBlank()) {
                        stringResource(R.string.local_songs_empty_desc)
                    } else {
                        stringResource(R.string.local_songs_no_matches_desc)
                    },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class LocalSongSortType {
    MODIFIED,
    NAME,
    ARTIST,
    ALBUM,
}
