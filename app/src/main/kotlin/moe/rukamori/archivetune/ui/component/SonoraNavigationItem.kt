package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import moe.rukamori.archivetune.LocalAnimationsDisabled
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.ui.screens.Screens

@Composable
internal fun SonoraNavigationItem(screen: Screens, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val disableAnimations = LocalAnimationsDisabled.current
    val label = stringResource(screen.titleId)
    val corner by animateDpAsState(if (selected) 22.dp else 32.dp, animationSpec = if (disableAnimations) snap() else spring(), label = "destinationCorner")
    val fill by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent, animationSpec = if (disableAnimations) snap() else spring(), label = "destinationFill")
    Surface(
        modifier = modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(corner))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = label },
        color = fill,
        shape = RoundedCornerShape(corner),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(if (selected) screen.iconIdActive else screen.iconIdInactive), null)
            if (disableAnimations) {
                if (selected) Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            } else AnimatedVisibility(selected) {
                Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}
