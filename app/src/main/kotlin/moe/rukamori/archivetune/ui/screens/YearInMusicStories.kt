/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.common.collect.ImmutableList
import kotlin.math.abs
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.recap.RecapAppearance
import moe.rukamori.archivetune.recap.RecapChapter
import moe.rukamori.archivetune.recap.RecapData
import moe.rukamori.archivetune.recap.RecapEntry
import moe.rukamori.archivetune.recap.RecapSelection
import moe.rukamori.archivetune.recap.RecapText

@Composable
internal fun RecapStories(
    data: RecapData,
    selection: RecapSelection,
    text: RecapText,
    callbacks: RecapCallbacks,
    shareChooserOpen: Boolean,
) {
    val chapter = selection.chapter
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val index = data.chapters.indexOf(chapter).coerceAtLeast(0)
    var paused by rememberSaveable(data.year) { mutableStateOf(false) }
    var elapsed by rememberSaveable(data.year, chapter) { mutableLongStateOf(0L) }
    var pressing by remember(chapter) { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val context = LocalContext.current
    val accessibility = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    var touchExploration by remember(accessibility) { mutableStateOf(accessibility.isTouchExplorationEnabled) }
    DisposableEffect(lifecycle, accessibility) {
        val observer = LifecycleEventObserver { _, event -> resumed = event.targetState.isAtLeast(Lifecycle.State.RESUMED) }
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { touchExploration = it }
        lifecycle.addObserver(observer)
        accessibility.addTouchExplorationStateChangeListener(listener)
        onDispose {
            lifecycle.removeObserver(observer)
            accessibility.removeTouchExplorationStateChangeListener(listener)
        }
    }
    val scroll = key(chapter) { rememberScrollState() }
    val move = rememberUpdatedState<(Int) -> Unit> { direction ->
        data.chapters.getOrNull(index + direction)?.let(callbacks.chapter)
    }
    val duration = when (chapter) {
        RecapChapter.INTRO -> 2_000L
        RecapChapter.SUMMARY -> 12_000L
        RecapChapter.END -> 0L
        else -> 8_000L
    }
    val running = resumed && !paused && !pressing && !selection.sharing && !shareChooserOpen &&
        !touchExploration && !scroll.isScrollInProgress
    LaunchedEffect(chapter, running) {
        if (!running || duration == 0L) return@LaunchedEffect
        val startedAt = withFrameNanos { it }
        val elapsedBeforeResume = elapsed
        while (elapsed < duration) {
            val now = withFrameNanos { it }
            elapsed = (elapsedBeforeResume + (now - startedAt) / 1_000_000L).coerceAtMost(duration)
        }
        move.value(1)
    }
    val intro = chapter == RecapChapter.INTRO
    val foreground = Color(if (intro) RecapAppearance.Ink else RecapAppearance.Foreground)
    val colors = remember(intro) {
        if (intro) lightColorScheme(primary = Color(RecapAppearance.Ink), onSurface = Color(RecapAppearance.Ink),
            onSurfaceVariant = Color(RecapAppearance.Ink), surface = Color(RecapAppearance.Cream))
        else darkColorScheme(primary = Color.White, onPrimary = Color(RecapAppearance.Background),
            surface = Color(RecapAppearance.Background), onSurface = Color(RecapAppearance.Foreground),
            onSurfaceVariant = Color(RecapAppearance.Secondary), surfaceContainerHigh = Color(RecapAppearance.Surface))
    }
    MaterialTheme(colorScheme = colors) {
        Box(Modifier.fillMaxSize()) {
            RecapStoryBackground(chapter, Modifier.matchParentSize())
            Column(Modifier.align(Alignment.Center).widthIn(max = 1000.dp).fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.small_icon), null, Modifier.size(20.dp), tint = foreground)
                    Text(stringResource(R.string.app_name), Modifier.weight(1f).padding(start = 8.dp),
                        style = MaterialTheme.typography.labelLarge, color = foreground)
                    if (!touchExploration && chapter != RecapChapter.END) {
                        RecapIcon(if (paused) R.drawable.play else R.drawable.pause,
                            if (paused) R.string.recap_resume_highlights else R.string.recap_pause_highlights, { paused = !paused })
                    }
                    FilledTonalIconButton(onClick = { callbacks.story(false) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = foreground.copy(alpha = 0.16f), contentColor = foreground)) {
                        Icon(painterResource(R.drawable.close), stringResource(R.string.close))
                    }
                }
                val progressDescription = stringResource(R.string.recap_progress, index + 1, data.chapters.size)
                Canvas(Modifier.fillMaxWidth().height(3.dp).semantics { contentDescription = progressDescription }) {
                    val gap = 4.dp.toPx()
                    val segmentWidth = ((size.width - gap * (data.chapters.size - 1)) / data.chapters.size).coerceAtLeast(0f)
                    data.chapters.indices.forEach { segment ->
                        val left = (if (rtl) data.chapters.lastIndex - segment else segment) * (segmentWidth + gap)
                        drawRect(foreground.copy(alpha = 0.28f), Offset(left, 0f), androidx.compose.ui.geometry.Size(segmentWidth, size.height))
                        val fill = when {
                            segment < index -> 1f
                            segment == index && duration > 0 -> elapsed.toFloat() / duration
                            segment == index -> 1f
                            else -> 0f
                        }
                        drawRect(foreground, Offset(left + if (rtl) segmentWidth * (1 - fill) else 0f, 0f),
                            androidx.compose.ui.geometry.Size(segmentWidth * fill, size.height))
                    }
                }
                Text(text.period(data), Modifier.padding(top = 12.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.labelMedium, color = foreground)
                val previousLabel = stringResource(R.string.recap_previous)
                val nextLabel = stringResource(R.string.recap_next)
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().semantics {
                    customActions = buildList {
                        if (index > 0) add(CustomAccessibilityAction(previousLabel) { move.value(-1); true })
                        if (index < data.chapters.lastIndex) add(CustomAccessibilityAction(nextLabel) { move.value(1); true })
                    }
                }.pointerInput(chapter, rtl, touchExploration) {
                    if (touchExploration) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        pressing = true
                        var position = down.position
                        var cancelled = false
                        var dragging = false
                        var releasedAt = down.uptimeMillis
                        try {
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) { cancelled = true; break }
                                position = change.position
                                releasedAt = change.uptimeMillis
                                val distance = position - down.position
                                if (change.isConsumed) { cancelled = true; break }
                                if (abs(distance.x) > viewConfiguration.touchSlop && abs(distance.x) > abs(distance.y)) {
                                    dragging = true
                                    change.consume()
                                } else if (abs(distance.y) > viewConfiguration.touchSlop && !dragging) {
                                    cancelled = true
                                    break
                                }
                            } while (event.changes.any { it.id == down.id && it.pressed })
                            if (!cancelled) {
                                val distance = position.x - down.position.x
                                if (dragging && abs(distance) > viewConfiguration.touchSlop * 3) {
                                    move.value(if ((distance < 0) != rtl) 1 else -1)
                                } else if (!dragging && releasedAt - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
                                    val previousSide = if (rtl) down.position.x > size.width * 0.65f else down.position.x < size.width * 0.35f
                                    move.value(if (previousSide) -1 else 1)
                                }
                            }
                        } finally {
                            pressing = false
                        }
                    }
                }) {
                    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                    key(chapter) {
                        val visible = remember { MutableTransitionState(false).apply { targetState = true } }
                        androidx.compose.animation.AnimatedVisibility(
                            visibleState = visible,
                            enter = fadeIn(effects),
                            exit = ExitTransition.None,
                        ) {
                            RecapScene(data, chapter, text, scroll, onReplay = { paused = false; callbacks.replay() },
                                onClose = { callbacks.story(false) })
                        }
                    }
                }
                val soundtrackError = selection.soundtrackFailed || selection.playback.failed ||
                    (!selection.soundtrackStarting && !selection.playback.recapQueue)
                if (soundtrackError) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.recap_soundtrack_error), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                        TextButton(callbacks.retrySoundtrack) { Text(stringResource(R.string.recap_retry_soundtrack)) }
                    }
                } else if (selection.soundtrackStarting || selection.playback.buffering) {
                    Text(stringResource(R.string.recap_soundtrack_loading), Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.labelSmall)
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RecapIcon(if (selection.playback.muted) R.drawable.volume_off else R.drawable.volume_up,
                        if (selection.playback.muted) R.string.recap_unmute else R.string.recap_mute,
                        callbacks.mute, selection.playback.recapQueue)
                    RecapIcon(if (rtl) R.drawable.arrow_forward else R.drawable.arrow_back,
                        R.string.recap_previous, { move.value(-1) }, index > 0)
                    Text("${text.number(index.toLong() + 1)} / ${text.number(data.chapters.size.toLong())}",
                        Modifier.weight(1f).clearAndSetSemantics { }, textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall)
                    RecapIcon(if (rtl) R.drawable.arrow_back else R.drawable.arrow_forward,
                        R.string.recap_next, { move.value(1) }, index < data.chapters.lastIndex)
                    if (chapter != RecapChapter.INTRO && chapter != RecapChapter.END) {
                        if (selection.sharing) ShareProgress() else RecapIcon(R.drawable.share, R.string.share, callbacks.share)
                    } else Spacer(Modifier.size(48.dp))
                }
            }
        }
    }
}

@Composable
private fun RecapStoryBackground(chapter: RecapChapter, modifier: Modifier = Modifier) {
    val colors = when (chapter) {
        RecapChapter.INTRO -> listOf(Color(RecapAppearance.Cream), Color(RecapAppearance.Mint))
        RecapChapter.TIME, RecapChapter.HABITS -> listOf(Color(RecapAppearance.Surface), Color(RecapAppearance.Background))
        else -> listOf(Color(RecapAppearance.Blue), Color(RecapAppearance.Background))
    }
    Canvas(modifier.background(Brush.verticalGradient(colors))) {
        drawCircle(Brush.radialGradient(listOf(Color(RecapAppearance.Mint).copy(alpha = 0.35f), Color.Transparent),
            center = Offset(size.width * 0.85f, size.height * 0.8f), radius = size.maxDimension * 0.65f),
            radius = size.maxDimension * 0.65f, center = Offset(size.width * 0.85f, size.height * 0.8f))
        drawCircle(Brush.radialGradient(listOf(Color(RecapAppearance.Cream).copy(alpha = 0.25f), Color.Transparent),
            center = Offset(0f, size.height * 0.25f), radius = size.maxDimension * 0.5f),
            radius = size.maxDimension * 0.5f, center = Offset(0f, size.height * 0.25f))
        if (chapter != RecapChapter.INTRO) drawRect(Color.Black.copy(alpha = 0.25f))
    }
}

@Composable
private fun RecapScene(
    data: RecapData,
    chapter: RecapChapter,
    text: RecapText,
    scroll: androidx.compose.foundation.ScrollState,
    onReplay: () -> Unit,
    onClose: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight && maxWidth >= 600.dp
        val hasArt = chapter !in listOf(RecapChapter.INTRO, RecapChapter.END, RecapChapter.MONTHS, RecapChapter.SUMMARY)
        if (landscape && hasArt) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                SceneArtwork(data, chapter, Modifier.weight(1f))
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(scroll).padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    SceneText(data, chapter, text, onReplay, onClose)
                }
            }
        } else {
            val artFirst = chapter in listOf(RecapChapter.TRACK, RecapChapter.SONGS, RecapChapter.ARTISTS, RecapChapter.ALBUMS)
            Column(Modifier.fillMaxWidth().verticalScroll(scroll).heightIn(min = maxHeight).padding(vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)) {
                if (hasArt && artFirst) SceneArtwork(data, chapter, Modifier.fillMaxWidth())
                SceneText(data, chapter, text, onReplay, onClose)
                if (hasArt && !artFirst) SceneArtwork(data, chapter, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SceneArtwork(data: RecapData, chapter: RecapChapter, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (chapter) {
            RecapChapter.TIME, RecapChapter.HABITS -> RecapMosaic(data.songs, Modifier.widthIn(max = 520.dp).fillMaxWidth())
            RecapChapter.TRACK -> data.songs.firstOrNull()?.let {
                RecapArtwork(it, Modifier.widthIn(max = 280.dp).fillMaxWidth().aspectRatio(1f), radius = 20)
            }
            RecapChapter.SONGS, RecapChapter.ARTISTS, RecapChapter.ALBUMS -> {
                val entry = when (chapter) {
                    RecapChapter.ARTISTS -> data.artists.firstOrNull()
                    RecapChapter.ALBUMS -> data.albums.firstOrNull()
                    else -> data.songs.firstOrNull()
                }
                entry?.let { RecapArtwork(it, Modifier.size(132.dp), radius = 16) }
            }
            else -> Unit
        }
    }
}

@Composable
private fun SceneText(data: RecapData, chapter: RecapChapter, text: RecapText, onReplay: () -> Unit, onClose: () -> Unit) {
    when (chapter) {
        RecapChapter.INTRO -> Text(stringResource(R.string.recap_story_intro), Modifier.fillMaxWidth().semantics { heading() },
            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        RecapChapter.TIME -> {
            SceneHeading(stringResource(R.string.recap_time_statement))
            Text(text.number(data.milliseconds / 60_000), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.year_in_music_minutes_unit), style = MaterialTheme.typography.titleLarge)
            Text(text.plays(data.plays) + " · " + stringResource(R.string.recap_distinct_tracks) + " " + text.number(data.distinctTracks),
                style = MaterialTheme.typography.bodyMedium)
        }
        RecapChapter.TRACK -> data.songs.firstOrNull()?.let {
            Text(text.chapterTitle(chapter), style = MaterialTheme.typography.labelLarge)
            SceneHeading(it.title)
            Text(it.subtitle, style = MaterialTheme.typography.titleMedium)
            Text(text.minutes(it.milliseconds) + " · " + text.plays(it.plays), style = MaterialTheme.typography.bodyLarge)
        }
        RecapChapter.SONGS, RecapChapter.ARTISTS, RecapChapter.ALBUMS -> {
            SceneHeading(text.chapterTitle(chapter))
            val entries = when (chapter) {
                RecapChapter.ARTISTS -> data.artists
                RecapChapter.ALBUMS -> data.albums
                else -> data.songs
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                entries.forEachIndexed { index, entry ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text.number(index.toLong() + 1), Modifier.widthIn(min = 24.dp), style = MaterialTheme.typography.titleMedium)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (entry.subtitle.isNotBlank()) Text(entry.subtitle, style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text.minutes(entry.milliseconds) + " · " + text.plays(entry.plays), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        RecapChapter.MONTHS -> {
            SceneHeading(text.chapterTitle(chapter))
            val columns = if (LocalDensity.current.fontScale > 1.3f) 2 else 3
            data.months.filter { it.available && it.milliseconds > 0 }.chunked(columns).forEach { months ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    months.forEach { month ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.fillMaxWidth().heightIn(min = 100.dp)) {
                                month.artist?.let { RecapArtwork(it, Modifier.size(76.dp).align(Alignment.TopEnd)) }
                                Text(text.month(month.month, short = true), Modifier.align(Alignment.BottomStart)
                                    .background(Brush.horizontalGradient(listOf(Color(RecapAppearance.Background).copy(alpha = 0.8f), Color.Transparent))),
                                    style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                            }
                            month.artist?.let { Text(it.title, style = MaterialTheme.typography.labelSmall,
                                maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            Text(text.minutes(month.milliseconds), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    repeat(columns - months.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        RecapChapter.HABITS -> SceneHeading(stringResource(R.string.recap_streak_statement, text.days(data.habits.longestStreak)))
        RecapChapter.SUMMARY -> {
            RecapMosaic(data.songs, Modifier.fillMaxWidth().heightIn(max = 150.dp))
            Text(data.year.toString(), style = if (LocalDensity.current.fontScale > 1.3f) MaterialTheme.typography.displayLarge
                else MaterialTheme.typography.displayLarge.copy(fontSize = 80.sp, lineHeight = 88.sp))
            val facts = buildList {
                data.albums.firstOrNull()?.let { add(Triple(stringResource(R.string.recap_top_album), it.title, it.subtitle)) }
                data.songs.firstOrNull()?.let { add(Triple(stringResource(R.string.recap_top_song), it.title, it.subtitle)) }
                add(Triple(stringResource(R.string.year_in_music_minutes_unit), text.number(data.milliseconds / 60_000), null))
                data.artists.firstOrNull()?.let { add(Triple(stringResource(R.string.recap_top_artist), it.title, null)) }
            }
            facts.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach { (label, value, subtitle) -> SummaryFact(label, value, subtitle, Modifier.weight(1f)) }
                }
            }
        }
        RecapChapter.END -> {
            SceneHeading(stringResource(R.string.recap_story_end))
            Button(onReplay, colors = ButtonDefaults.buttonColors(containerColor = Color.White,
                contentColor = Color(RecapAppearance.Background)), modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.replay), null, Modifier.size(20.dp))
                Text(stringResource(R.string.recap_replay), Modifier.padding(start = 8.dp))
            }
            TextButton(onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.recap_return_overview)) }
        }
    }
}

@Composable
private fun SceneHeading(value: String) {
    Text(value, Modifier.fillMaxWidth().semantics { heading() }, style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold)
}

@Composable
private fun SummaryFact(label: String, value: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun RecapMosaic(entries: ImmutableList<RecapEntry>, modifier: Modifier = Modifier) {
    val groups = remember(entries) { entries.take(5).chunked(3) }
    if (groups.isEmpty()) return
    Column(modifier.aspectRatio(1.75f).clip(MaterialTheme.shapes.medium).clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        groups.forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { entry -> RecapArtwork(entry, Modifier.weight(1f).fillMaxHeight(), radius = 8) }
            }
        }
    }
}
