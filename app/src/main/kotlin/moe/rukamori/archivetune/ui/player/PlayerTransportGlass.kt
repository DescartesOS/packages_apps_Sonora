package moe.rukamori.archivetune.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import moe.rukamori.archivetune.ui.component.FloatingBarGlassBackground
import moe.rukamori.archivetune.ui.component.sonoraGlassRim

/** Samples only the player's artwork backdrop; controls never feed back into their own blur. */
@Composable
internal fun PlayerTransportGlass(hazeState: HazeState?, content: @Composable () -> Unit) {
    if (hazeState == null) {
        content()
        return
    }
    val shape = RoundedCornerShape(32.dp)
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(shape).sonoraGlassRim(shape)) {
        FloatingBarGlassBackground(
            hazeState = hazeState,
            shape = shape,
            pureBlack = false,
            artworkGlass = true,
            modifier = Modifier.matchParentSize(),
        )
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) { content() }
    }
}
