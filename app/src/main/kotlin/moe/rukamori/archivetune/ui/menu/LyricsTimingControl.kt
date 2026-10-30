/*
 * Adapted from SimpMusic, LyricsOffsetControl.kt by maxrave-dev and contributors.
 * Source commit: 5397d0af1b2a849d6994822f269b1995d7d5ca23
 * https://github.com/maxrave-dev/SimpMusic
 * GPL-3.0; see the repository LICENSE.
 * Sonora adaptations: Material color roles, accessible touch targets, bounded
 * offsets, and immediate local state so rapid taps accumulate before persistence.
 */
package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R

internal fun adjustedLyricsOffset(offset: Int, delta: Int): Int =
    (offset.toLong() + delta).coerceIn(-5000L, 5000L).toInt()

@Composable
internal fun LyricsTimingControl(offsetMs: Int, onOffsetChange: (Int) -> Unit) {
    var displayedOffset by remember(offsetMs) { mutableIntStateOf(offsetMs.coerceIn(-5000, 5000)) }
    fun update(value: Int) {
        displayedOffset = value
        onOffsetChange(value)
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.lyrics_sync_offset), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                FilledTonalIconButton(
                    onClick = { update(adjustedLyricsOffset(displayedOffset, -100)) },
                    enabled = displayedOffset > -5000,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(painterResource(R.drawable.remove), stringResource(R.string.sonora_lyrics_earlier))
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.sonora_lyrics_signed_offset, if (displayedOffset > 0) "+$displayedOffset" else displayedOffset.toString()),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                FilledTonalIconButton(
                    onClick = { update(adjustedLyricsOffset(displayedOffset, 100)) },
                    enabled = displayedOffset < 5000,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(painterResource(R.drawable.add), stringResource(R.string.sonora_lyrics_later))
                }
                IconButton(onClick = { update(0) }, enabled = displayedOffset != 0, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.replay), stringResource(R.string.reset))
                }
            }
            Text(stringResource(R.string.sonora_lyrics_timing_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
}
