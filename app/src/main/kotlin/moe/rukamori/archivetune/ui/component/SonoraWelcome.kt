package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R

/** Sonora's responsive listening hub. Actions use the existing navigation graph. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SonoraWelcome(onExplore: () -> Unit, onLibrary: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp, bottomEnd = 40.dp, bottomStart = 16.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.background(
                Brush.linearGradient(listOf(colors.tertiaryContainer.copy(alpha = 0.6f), colors.primaryContainer)),
            ).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.sonora_identity), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(R.string.sonora_welcome),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(R.string.sonora_welcome_detail), style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExplore, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)) {
                    Text(stringResource(R.string.sonora_explore))
                }
                FilledTonalButton(onClick = onLibrary, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)) {
                    Text(stringResource(R.string.sonora_library))
                }
            }
        }
    }
}
