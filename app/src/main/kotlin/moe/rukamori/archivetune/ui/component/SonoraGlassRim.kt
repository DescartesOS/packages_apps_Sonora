/*
 * Directional rim treatment adapted from BitChord's lightweightLiquidGlass /
 * liquidGlassRim integration, kushagrasinghx/BitChord commit
 * 04a4a83889bb8fa5bd6ee6f6097f0c6608671d68 (GPL-3.0).
 * BitChord credits Echo-Music (GPL-3.0) and Kyant0/backdrop (Apache-2.0).
 * Sonora uses Compose brush borders rather than importing those renderers.
 */
package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** A directional highlight without a second blur, capture layer, or animation. */
@Composable
internal fun Modifier.sonoraGlassRim(shape: Shape): Modifier = border(
    width = 1.dp,
    brush = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.38f),
        0.35f to Color.White.copy(alpha = 0.05f),
        0.65f to Color.White.copy(alpha = 0.02f),
        1f to Color.White.copy(alpha = 0.20f),
    ),
    shape = shape,
)
