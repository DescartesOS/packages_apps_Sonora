package moe.rukamori.archivetune.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.key
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.onboarding.*

/** A single setup shell keeps progress and navigation visible on every step. */
@Composable
internal fun SonoraSetup(
    state: OnboardingUiState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onPermission: (OnboardingPermissionAction) -> Unit,
    onCommunity: (OnboardingCommunityActionUiModel) -> Unit,
    padding: PaddingValues,
) {
    BackHandler(enabled = state.currentPage > 0, onBack = onBack)
    val colors = MaterialTheme.colorScheme
    val page = state.pages[state.currentPage]
    val listState = key(page.id) { rememberLazyListState() }
    Column(
        Modifier.fillMaxSize().padding(padding).background(
            Brush.verticalGradient(listOf(colors.tertiaryContainer.copy(alpha = 0.45f), colors.surface)),
        ).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.sonora_setup_brand), style = MaterialTheme.typography.labelLarge)
            Text("${state.currentPage + 1} / ${state.pages.size}", style = MaterialTheme.typography.labelLarge)
        }
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.pages.forEachIndexed { index, _ ->
                Surface(
                    Modifier.weight(1f).height(5.dp),
                    shape = RoundedCornerShape(50),
                    color = if (index <= state.currentPage) colors.primary else colors.outlineVariant,
                ) {}
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).widthIn(max = 720.dp).align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(40.dp, 40.dp, 40.dp, 12.dp),
                    color = colors.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Icon(painterResource(R.drawable.sonora_mark), null, Modifier.size(80.dp), tint = colors.onPrimaryContainer)
                        Text(
                            stringResource(when (page.id) {
                                OnboardingPageId.WELCOME -> R.string.sonora_setup_welcome
                                OnboardingPageId.PERMISSIONS -> R.string.sonora_setup_permissions
                                OnboardingPageId.LOGIN -> R.string.sonora_setup_login
                                OnboardingPageId.COMMUNITY -> R.string.sonora_setup_ready
                            }),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.onPrimaryContainer,
                        )
                        Text(stringResource(page.subtitleResId), style = MaterialTheme.typography.bodyLarge, color = colors.onPrimaryContainer)
                    }
                }
            }
            when (page.id) {
                OnboardingPageId.WELCOME -> {
                    item { SetupNote(stringResource(R.string.sonora_setup_intro)) }
                }
                OnboardingPageId.PERMISSIONS -> {
                    item { SetupNote(stringResource(R.string.sonora_setup_permission_note)) }
                    state.permissions.forEach { permission ->
                        item(key = permission.id.name) {
                            Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerLow) {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(painterResource(permission.iconResId), null, tint = colors.primary)
                                        Text(stringResource(permission.titleResId), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    }
                                    Text(stringResource(permission.descriptionResId), style = MaterialTheme.typography.bodyMedium)
                                    if (permission.action != null) {
                                        FilledTonalButton(onClick = { onPermission(permission.action) }) {
                                            Text(stringResource(R.string.sonora_setup_manage))
                                        }
                                    } else {
                                        Text(stringResource(if (permission.status == OnboardingPermissionStatus.UNAVAILABLE) R.string.sonora_setup_unavailable else R.string.sonora_setup_available), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                                    }
                                }
                            }
                        }
                    }
                }
                OnboardingPageId.LOGIN -> {
                    item { SetupNote(stringResource(R.string.onboarding_login_optional)) }
                    state.loginBenefits.forEach { benefit ->
                        item(key = benefit.id) { SetupNote(stringResource(benefit.descriptionResId)) }
                    }
                    item { Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.login)) } }
                }
                OnboardingPageId.COMMUNITY -> {
                    item { SetupNote(stringResource(R.string.sonora_setup_finish_note)) }
                    state.communityActions.forEach { action ->
                        item(key = action.id) {
                            FilledTonalButton(onClick = { onCommunity(action) }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(action.titleResId))
                            }
                        }
                    }
                }
            }
        }
        Surface(
            color = colors.surfaceContainerHigh.copy(alpha = 0.94f),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.5f)),
        ) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.currentPage > 0) {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back_button_desc)) }
                }
                Button(onClick = onNext, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Text(stringResource(if (state.currentPage == state.pages.lastIndex) R.string.sonora_setup_start else R.string.sonora_setup_continue))
                    Spacer(Modifier.width(12.dp))
                    Icon(painterResource(R.drawable.arrow_forward), null)
                }
            }
        }
    }
}

@Composable
private fun SetupNote(text: String) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(text, Modifier.fillMaxWidth().padding(20.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
