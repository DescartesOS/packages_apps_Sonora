/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.settings

import android.content.res.Resources
import androidx.compose.runtime.Immutable
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.constants.LanguageCodeToName
import java.util.Locale
import javax.inject.Inject

@Immutable
data class AppLanguageOption(
    val languageTag: String,
    val name: String,
)

class AppLanguageSettingsUseCases @Inject constructor(
    private val repository: AppLanguageSettingsRepository,
) {
    val languages: ImmutableList<AppLanguageOption> = ImmutableList.copyOf(
        LanguageCodeToName.map { (tag, name) -> AppLanguageOption(tag, name) },
    )

    fun observeSettings(): Flow<AppLanguageSettings> = repository.observeSettings().map { settings ->
        settings.copy(languageTag = settings.languageTag.takeIf { it in LanguageCodeToName } ?: "en")
    }

    fun locale(settings: AppLanguageSettings): Locale =
        if (settings.useSystemLanguage) {
            Resources.getSystem().configuration.locales[0]
        } else {
            Locale.forLanguageTag(settings.languageTag)
        }

    suspend fun setUseSystemLanguage(enabled: Boolean) = repository.setUseSystemLanguage(enabled)

    suspend fun setLanguage(languageTag: String) {
        require(languageTag in LanguageCodeToName)
        repository.setLanguage(languageTag)
    }
}
