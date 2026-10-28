/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component.shimmer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.constants.GridThumbnailCornerRadius
import moe.rukamori.archivetune.ui.component.GridItem

@Composable
fun GridItemPlaceHolder(
    modifier: Modifier = Modifier,
    thumbnailShape: Shape = RoundedCornerShape(GridThumbnailCornerRadius),
    fillMaxWidth: Boolean = false,
) {
    if (!fillMaxWidth) {
        GridItem(
            modifier = modifier,
            title = {
                Column {
                    TextPlaceholder()
                    TextPlaceholder(modifier = Modifier.fillMaxWidth(0.7f))
                }
            },
            subtitle = {
                Column {
                    TextPlaceholder(height = 8.dp)
                    TextPlaceholder(height = 8.dp, modifier = Modifier.fillMaxWidth(0.6f))
                }
            },
            thumbnailContent = {
                Spacer(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(thumbnailShape)
                            .background(MaterialTheme.colorScheme.onSurface),
                )
            },
        )
        return
    }

    Column(
        modifier =
            modifier
                .padding(12.dp)
                .fillMaxWidth(),
    ) {
        Spacer(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(thumbnailShape)
                    .background(MaterialTheme.colorScheme.onSurface),
        )

        Spacer(modifier = Modifier.height(6.dp))

        TextPlaceholder()

        TextPlaceholder()
    }
}
