/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.localmedia.ScanLocalMusicUseCase
import moe.rukamori.archivetune.viewmodels.LocalSongFolderUiModel
import moe.rukamori.archivetune.viewmodels.LocalSongSettingsAction
import moe.rukamori.archivetune.viewmodels.LocalSongSettingsEvent
import moe.rukamori.archivetune.viewmodels.LocalSongSettingsState
import moe.rukamori.archivetune.viewmodels.LocalSongSettingsUiModel
import moe.rukamori.archivetune.viewmodels.LocalSongsViewModel
import kotlin.math.roundToInt

const val LocalSongSettingsRoute = "settings/local_songs"

@Composable
fun LocalSongSettings(navController: NavController, viewModel: LocalSongsViewModel) {
    val state by viewModel.settingsState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val onAction = remember(viewModel) { viewModel::onSettingsAction }
    val permission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionPicker = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onAction(LocalSongSettingsAction.PermissionResult(it))
    }
    val includedPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        onAction(LocalSongSettingsAction.FolderSelected(included = true, uri = it))
    }
    val excludedPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        onAction(LocalSongSettingsAction.FolderSelected(included = false, uri = it))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshSettings() }
    LaunchedEffect(viewModel, context, navController, permission) {
        viewModel.settingsEvents.collect { event ->
            try {
                when (event) {
                    LocalSongSettingsEvent.NavigateBack -> navController.navigateUp()
                    LocalSongSettingsEvent.RequestAudioPermission -> permissionPicker.launch(permission)
                    LocalSongSettingsEvent.OpenAppSettings -> context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                    )
                    is LocalSongSettingsEvent.PickFolder -> {
                        if (event.included) includedPicker.launch(event.initialUri)
                        else excludedPicker.launch(event.initialUri)
                    }
                }
            } catch (_: ActivityNotFoundException) {
                onAction(LocalSongSettingsAction.ExternalActionFailed)
            } catch (_: SecurityException) {
                onAction(LocalSongSettingsAction.ExternalActionFailed)
            }
        }
    }
    BackHandler(onBack = remember(onAction) { { onAction(LocalSongSettingsAction.Back) } })
    LocalSongSettingsContent(state, onAction)
}

@Composable
private fun LocalSongSettingsContent(state: LocalSongSettingsState, onAction: (LocalSongSettingsAction) -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val onBack = remember(onAction) { { onAction(LocalSongSettingsAction.Back) } }
    val onRetry = remember(onAction) { { onAction(LocalSongSettingsAction.Reload) } }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.local_song_settings_title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                subtitle = { Text(stringResource(R.string.local_songs_scan_title)) },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.back_button_desc))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val insets = LocalPlayerAwareWindowInsets.current
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
            contentAlignment = Alignment.TopCenter,
        ) {
            val model = state.data
            when {
                model != null -> LocalSongSettingsList(state, model, onAction)
                state is LocalSongSettingsState.Error -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(SettingsDimensions.ScreenHorizontalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(state.message), color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.retry)) }
                }
                else -> LoadingIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun LocalSongSettingsList(
    state: LocalSongSettingsState,
    model: LocalSongSettingsUiModel,
    onAction: (LocalSongSettingsAction) -> Unit,
) {
    val onInclude = remember(onAction) { { onAction(LocalSongSettingsAction.PickFolder(included = true)) } }
    val onExclude = remember(onAction) { { onAction(LocalSongSettingsAction.PickFolder(included = false)) } }
    val onAppSettings = remember(onAction) { { onAction(LocalSongSettingsAction.OpenAppSettings) } }
    val motion = MaterialTheme.motionScheme
    val placement = remember(motion) { motion.defaultSpatialSpec<IntOffset>() }
    val fade = remember(motion) { motion.defaultEffectsSpec<Float>() }
    LazyColumn(
        modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
        contentPadding = PaddingValues(
            start = SettingsDimensions.ScreenHorizontalPadding,
            end = SettingsDimensions.ScreenHorizontalPadding,
            top = 16.dp,
            bottom = SettingsDimensions.ScreenBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item(key = "scan_status", contentType = "scan_status") {
            LocalSongScanStatus(state, model, onAction, Modifier.padding(bottom = SettingsDimensions.SectionSpacing))
        }
        item(key = "access_heading", contentType = "section_heading") {
            Text(
                stringResource(R.string.permission_storage_title),
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp).semantics { heading() },
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        item(key = "audio_access", contentType = "access_setting") {
            SegmentedListItem(
                onClick = onAppSettings,
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                verticalAlignment = Alignment.CenterVertically,
                leadingContent = { Icon(painterResource(R.drawable.security), null) },
                supportingContent = {
                    Text(stringResource(if (model.hasMediaPermission) R.string.local_song_settings_permission_granted else R.string.local_song_settings_permission_optional))
                },
                trailingContent = {
                    Text(stringResource(if (model.hasMediaPermission) R.string.permission_status_allowed else R.string.not_allowed), style = MaterialTheme.typography.labelLarge)
                },
                colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) { Text(stringResource(R.string.local_song_settings_audio_permission)) }
        }
        item(key = "scan_scope", contentType = "access_setting") {
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                verticalAlignment = Alignment.CenterVertically,
                leadingContent = { Icon(painterResource(R.drawable.storage), null) },
                supportingContent = {
                    Text(stringResource(if (model.includedFolders.isEmpty()) R.string.local_song_settings_scope_device else R.string.local_song_settings_scope_folders))
                },
                colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) { Text(stringResource(R.string.local_song_settings_scan_scope)) }
        }
        item(key = "duration", contentType = "duration_setting") {
            Column(Modifier.padding(top = SettingsDimensions.SectionSpacing), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.local_songs_scan_filters_title),
                    modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary)
                LocalSongDurationSetting(model.minimumDurationSeconds, model.canEdit, onAction)
            }
        }
        item(key = "included_heading", contentType = "folder_heading") {
            LocalSongFolderHeader(
                title = R.string.local_songs_scan_included_folders_title,
                description = R.string.local_songs_scan_included_folders_desc,
                actionLabel = R.string.local_songs_scan_included_folders_add,
                enabled = model.canEdit,
                onAdd = onInclude,
            )
        }
        if (model.includedFolders.isEmpty()) {
            item(key = "included_empty", contentType = "folder_empty") {
                Text(stringResource(R.string.local_songs_scan_included_folders_empty),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        itemsIndexed(model.includedFolders, key = { _, folder -> "included:${folder.entry}" }, contentType = { _, _ -> "folder" }) { index, folder ->
            LocalSongFolderRow(
                folder = folder,
                index = index,
                count = model.includedFolders.size,
                included = true,
                enabled = model.canEdit,
                onAction = onAction,
                modifier = Modifier.animateItem(fadeInSpec = fade, placementSpec = placement, fadeOutSpec = fade),
            )
        }
        item(key = "excluded_heading", contentType = "folder_heading") {
            LocalSongFolderHeader(
                title = R.string.local_songs_scan_folders_title,
                description = R.string.local_songs_scan_folders_desc,
                actionLabel = R.string.local_songs_scan_folders_add,
                enabled = model.canEdit,
                onAdd = onExclude,
            )
        }
        if (model.excludedFolders.isEmpty()) {
            item(key = "excluded_empty", contentType = "folder_empty") {
                Text(stringResource(R.string.local_songs_scan_folders_empty),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        itemsIndexed(model.excludedFolders, key = { _, folder -> "excluded:${folder.entry}" }, contentType = { _, _ -> "folder" }) { index, folder ->
            LocalSongFolderRow(
                folder = folder,
                index = index,
                count = model.excludedFolders.size,
                included = false,
                enabled = model.canEdit,
                onAction = onAction,
                modifier = Modifier.animateItem(fadeInSpec = fade, placementSpec = placement, fadeOutSpec = fade),
            )
        }
        item(key = "settings_note", contentType = "settings_note") {
            Text(stringResource(R.string.local_songs_scan_filters_note),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LocalSongScanStatus(
    state: LocalSongSettingsState,
    model: LocalSongSettingsUiModel,
    onAction: (LocalSongSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val error = (state as? LocalSongSettingsState.Error)?.message
    val title = stringResource(when {
        model.isScanning -> R.string.scanning_device
        error != null -> R.string.local_song_settings_attention
        state is LocalSongSettingsState.Empty -> R.string.local_songs_empty_title
        model.summary != null -> R.string.local_songs_latest_scan
        else -> R.string.local_song_settings_ready
    })
    val message = when {
        model.isScanning -> stringResource(R.string.local_songs_scan_subtitle)
        error != null -> stringResource(error)
        model.summary?.metadataLookupFailed == true -> stringResource(R.string.local_songs_metadata_unavailable, model.summary.scannedSongs)
        model.summary != null -> stringResource(R.string.local_songs_scan_summary, model.summary.scannedSongs, model.summary.removedSongs)
        else -> stringResource(R.string.local_songs_ready_desc)
    }
    val motion = MaterialTheme.motionScheme
    val resize = remember(motion) { motion.defaultSpatialSpec<IntSize>() }
    val fade = remember(motion) { motion.defaultEffectsSpec<Float>() }
    val onScan = remember(onAction) { { onAction(LocalSongSettingsAction.Scan) } }
    val onCancel = remember(onAction) { { onAction(LocalSongSettingsAction.CancelScan) } }
    Card(
        modifier = modifier.fillMaxWidth().animateContentSize(resize),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (error == null) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(when {
                        error != null -> R.drawable.error
                        model.isScanning -> R.drawable.sync
                        model.summary != null -> R.drawable.done
                        else -> R.drawable.library_music
                    }), null, modifier = Modifier.size(24.dp),
                    tint = if (error == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onErrorContainer,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimatedContent(targetState = title, transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) }, label = "scanStatusTitle") {
                        Text(it, style = MaterialTheme.typography.titleLargeEmphasized)
                    }
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                    if (state is LocalSongSettingsState.Empty) {
                        Text(stringResource(R.string.local_song_settings_empty_hint), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            AnimatedVisibility(
                visible = model.isScanning,
                enter = fadeIn(fade) + expandVertically(resize),
                exit = fadeOut(fade) + shrinkVertically(resize),
            ) { LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            Button(
                onClick = onScan,
                shapes = ButtonDefaults.shapes(),
                enabled = model.canEdit,
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight, hasStartIcon = true),
            ) {
                Icon(painterResource(R.drawable.sync), null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(when {
                    model.isScanning -> R.string.scanning_device
                    model.isSaving -> R.string.local_song_settings_saving
                    model.requiresMediaPermission && !model.hasMediaPermission -> R.string.allow
                    else -> R.string.scan_device
                }))
            }
            if (model.isScanning) {
                TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

@Composable
private fun LocalSongDurationSetting(
    seconds: Int,
    enabled: Boolean,
    onAction: (LocalSongSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val durationLabel = if (seconds == 0) stringResource(R.string.dark_theme_off)
    else pluralStringResource(R.plurals.seconds, seconds, seconds)
    val label = stringResource(R.string.local_songs_scan_duration_title)
    val onValueChange = remember(onAction) { { value: Float -> onAction(LocalSongSettingsAction.DurationChanged(value.roundToInt())) } }
    val onValueFinished = remember(onAction) { { onAction(LocalSongSettingsAction.SaveDuration) } }
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = { Icon(painterResource(R.drawable.timer), null) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.local_songs_scan_duration_desc))
                Slider(
                    value = seconds.toFloat(), onValueChange = onValueChange, onValueChangeFinished = onValueFinished,
                    valueRange = 0f..ScanLocalMusicUseCase.MaximumMinimumDurationSeconds.toFloat(), steps = 11,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = label; stateDescription = durationLabel },
                )
                Text(durationLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        },
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) { Text(label) }
}

@Composable
private fun LocalSongFolderHeader(
    @StringRes title: Int,
    @StringRes description: Int,
    @StringRes actionLabel: Int,
    enabled: Boolean,
    onAdd: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
            style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(description), modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(
            onClick = onAdd, enabled = enabled, shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight, hasStartIcon = true),
        ) {
            Icon(painterResource(R.drawable.add), null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(actionLabel))
        }
    }
}

@Composable
private fun LocalSongFolderRow(
    folder: LocalSongFolderUiModel,
    index: Int,
    count: Int,
    included: Boolean,
    enabled: Boolean,
    onAction: (LocalSongSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onRemove = remember(folder.entry, included, onAction) { { onAction(LocalSongSettingsAction.RemoveFolder(folder.entry, included)) } }
    val onRepair = remember(folder.entry, onAction) {
        { onAction(LocalSongSettingsAction.PickFolder(included = true, initialUri = Uri.parse(folder.entry).takeIf { it.scheme == "content" })) }
    }
    val location = when (folder.volume) {
        "primary" -> stringResource(R.string.local_song_settings_internal_storage)
        null -> stringResource(R.string.local_song_settings_saved_path)
        else -> folder.volume
    }
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count), modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = { Icon(painterResource(R.drawable.snippet_folder), null) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(location)
                if (folder.needsAccess) {
                    Text(stringResource(R.string.local_song_settings_folder_access_needed), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRepair, enabled = enabled) { Text(stringResource(R.string.local_song_settings_choose_again)) }
                } else if (included) {
                    Text(stringResource(R.string.local_song_settings_folder_access_saved))
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(painterResource(R.drawable.close), stringResource(R.string.modules_remove))
            }
        },
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) { Text(folder.path) }
}
