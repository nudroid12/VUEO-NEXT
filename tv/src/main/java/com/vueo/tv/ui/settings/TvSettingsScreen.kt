package com.vueo.tv.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.vueo.shared.core.dna.UserDnaPreferences
import com.vueo.shared.core.dna.UserDnaSnapshot
import com.vueo.shared.core.extensions.CatalogDiscoveryCache
import com.vueo.shared.core.enrichment.MdblistClient
import com.vueo.shared.core.enrichment.TmdbEnhancementClient
import com.vueo.shared.core.plugin.PluginRepositoryDescriptor
import com.vueo.shared.core.plugin.PluginHealthStore
import com.vueo.shared.core.plugin.PluginProviderDescriptor
import com.vueo.shared.core.plugin.ProviderCodeStore
import com.vueo.shared.core.plugin.providerHealthSortKey
import com.vueo.shared.core.profile.ProfileAvatarCatalog
import com.vueo.shared.core.source.SourceDiscoveryCache
import com.vueo.shared.core.storage.AppAccent
import com.vueo.shared.core.storage.AppTheme
import com.vueo.shared.core.storage.PlayerVideoFit
import com.vueo.shared.core.storage.PreferredQuality
import com.vueo.shared.core.storage.SubtitleLanguage
import com.vueo.shared.core.storage.SubtitleSize
import com.vueo.shared.core.storage.SubtitleVisibility
import com.vueo.shared.core.storage.VueoBackupManager
import com.vueo.tv.BuildConfig
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvSidebarPreferences
import com.vueo.tv.ui.TvSidebarStyle
import com.vueo.tv.update.TvUpdateManager
import com.vueo.tv.update.TvUpdateRelease
import kotlinx.coroutines.launch
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class TvSettingsPage {
    PROFILE,
    PROFILE_CHOOSER,
    PERSONALIZATION,
    CONTENT_MANAGER,
    CONTENT_ADDONS,
    CONTENT_PROVIDERS,
    CONTENT_PROVIDER_HEALTH,
    CONTENT_CATALOGS,
    ENHANCEMENTS,
    ENHANCEMENT_TMDB,
    ENHANCEMENT_MDBLIST,
    PLAYBACK,
    SUBTITLES,
    SOURCES,
    APPEARANCE,
    DATA_STORAGE,
    ABOUT,
}

private data class TvSettingsRootDestination(
    val page: TvSettingsPage,
    val id: String,
    val title: String,
    val section: String? = null,
)

private val TvSettingsRootDestinations = listOf(
    TvSettingsRootDestination(TvSettingsPage.PROFILE, "profile", "Profile", "VUEO"),
    TvSettingsRootDestination(TvSettingsPage.PERSONALIZATION, "personalization", "Personalization", "VUEO"),
    TvSettingsRootDestination(TvSettingsPage.CONTENT_MANAGER, "content-manager", "Content Manager", "VUEO"),
    TvSettingsRootDestination(TvSettingsPage.ENHANCEMENTS, "enhancements", "Enhancements", "VUEO"),
    TvSettingsRootDestination(TvSettingsPage.PLAYBACK, "playback", "Playback", "PLAYBACK"),
    TvSettingsRootDestination(TvSettingsPage.SUBTITLES, "subtitles", "Subtitles", "PLAYBACK"),
    TvSettingsRootDestination(TvSettingsPage.SOURCES, "sources", "Sources", "PLAYBACK"),
    TvSettingsRootDestination(TvSettingsPage.APPEARANCE, "appearance", "Appearance", "APP"),
    TvSettingsRootDestination(TvSettingsPage.DATA_STORAGE, "data-storage", "Data & Storage", "APP"),
    TvSettingsRootDestination(TvSettingsPage.ABOUT, "about", "About VUEO", "APP"),
)

private fun TvSettingsPage.rootPage(): TvSettingsPage = when (this) {
    TvSettingsPage.PROFILE_CHOOSER -> TvSettingsPage.PROFILE
    TvSettingsPage.CONTENT_ADDONS,
    TvSettingsPage.CONTENT_PROVIDERS,
    TvSettingsPage.CONTENT_PROVIDER_HEALTH,
    TvSettingsPage.CONTENT_CATALOGS -> TvSettingsPage.CONTENT_MANAGER
    TvSettingsPage.ENHANCEMENT_TMDB,
    TvSettingsPage.ENHANCEMENT_MDBLIST -> TvSettingsPage.ENHANCEMENTS
    else -> this
}

private fun TvSettingsPage.hasPanelParent(): Boolean = when (this) {
    TvSettingsPage.PROFILE_CHOOSER,
    TvSettingsPage.CONTENT_ADDONS,
    TvSettingsPage.CONTENT_PROVIDERS,
    TvSettingsPage.CONTENT_PROVIDER_HEALTH,
    TvSettingsPage.CONTENT_CATALOGS,
    TvSettingsPage.ENHANCEMENT_TMDB,
    TvSettingsPage.ENHANCEMENT_MDBLIST -> true
    else -> false
}

@Composable
fun TvSettingsScreen(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
    onDataChanged: () -> Unit = {},
    onResetComplete: () -> Unit = {},
) {
    var page by remember { mutableStateOf(TvSettingsPage.PROFILE) }
    var panelAutoFocusToken by remember { mutableIntStateOf(0) }

    val rootPage = page.rootPage()
    val selectedRoot = TvSettingsRootDestinations.firstOrNull { it.page == rootPage }
        ?: TvSettingsRootDestinations.first()

    fun openPanel(next: TvSettingsPage) {
        page = next
        panelAutoFocusToken += 1
    }

    fun backPanel() {
        page = when (page) {
            TvSettingsPage.PROFILE_CHOOSER -> TvSettingsPage.PROFILE
            TvSettingsPage.CONTENT_ADDONS,
            TvSettingsPage.CONTENT_PROVIDERS,
            TvSettingsPage.CONTENT_PROVIDER_HEALTH,
            TvSettingsPage.CONTENT_CATALOGS -> TvSettingsPage.CONTENT_MANAGER
            TvSettingsPage.ENHANCEMENT_TMDB,
            TvSettingsPage.ENHANCEMENT_MDBLIST -> TvSettingsPage.ENHANCEMENTS
            else -> page
        }
        panelAutoFocusToken += 1
    }

    // Content Manager destinations behave like dedicated pages, matching Mobile:
    // enter the selected section, then Back returns to the Content Manager hub.
    when (page) {
        TvSettingsPage.CONTENT_ADDONS -> {
            TvAddonSettings(
                runtime, onNavigate, onProfile, onDataChanged, ::backPanel
            )
            return
        }
        TvSettingsPage.CONTENT_PROVIDERS -> {
            TvProviderSettings(
                runtime, onNavigate, onProfile, onDataChanged, ::backPanel
            )
            return
        }
        TvSettingsPage.CONTENT_PROVIDER_HEALTH -> {
            TvProviderHealthOverview(
                runtime, onNavigate, onProfile, ::backPanel
            )
            return
        }
        TvSettingsPage.CONTENT_CATALOGS -> {
            TvCatalogSettings(
                runtime, onNavigate, onProfile, onDataChanged, ::backPanel
            )
            return
        }
        else -> Unit
    }

    TvSettingsMasterDetailShell(
        categories = TvSettingsRootDestinations.map { TvSettingsNavItem(it.id, it.title, it.section) },
        selectedCategoryId = selectedRoot.id,
        panelKey = page.name,
        panelAutoFocusToken = panelAutoFocusToken,
        panelHasBack = page.hasPanelParent(),
        onCategorySelected = { id ->
            TvSettingsRootDestinations.firstOrNull { it.id == id }?.let { destination ->
                if (page != destination.page) page = destination.page
            }
        },
        onPanelBack = ::backPanel,
        onNavigate = onNavigate,
        onProfile = onProfile,
        onBack = onBack,
    ) {
        when (page) {
            TvSettingsPage.PROFILE -> TvProfileSettings(
                runtime = runtime,
                onOpenDna = onProfile,
                onOpenProfiles = { openPanel(TvSettingsPage.PROFILE_CHOOSER) },
            )
            TvSettingsPage.PROFILE_CHOOSER -> TvProfileChooserSettings(
                runtime = runtime,
                onNavigate = onNavigate,
                onProfile = onProfile,
                onDataChanged = onDataChanged,
                onProfileSelected = { openPanel(TvSettingsPage.PROFILE) },
                onBack = ::backPanel,
            )
            TvSettingsPage.PERSONALIZATION -> TvPersonalizationSettings(
                runtime, onNavigate, onProfile, onBack
            )
            TvSettingsPage.CONTENT_MANAGER -> TvContentManagerHub(
                runtime, onNavigate, onProfile, ::openPanel, onBack
            )
            TvSettingsPage.CONTENT_ADDONS -> TvAddonSettings(
                runtime, onNavigate, onProfile, onDataChanged, ::backPanel
            )
            TvSettingsPage.CONTENT_PROVIDERS -> TvProviderSettings(
                runtime, onNavigate, onProfile, onDataChanged, ::backPanel
            )
            TvSettingsPage.CONTENT_PROVIDER_HEALTH -> TvProviderHealthOverview(
                runtime, onNavigate, onProfile, ::backPanel
            )
            TvSettingsPage.CONTENT_CATALOGS -> TvCatalogSettings(
                runtime, onNavigate, onProfile, onDataChanged, ::backPanel
            )
            TvSettingsPage.ENHANCEMENTS -> TvEnhancementSettings(
                runtime, onNavigate, onProfile, ::openPanel, onBack
            )
            TvSettingsPage.ENHANCEMENT_TMDB -> TvTmdbEnhancementSettings(
                runtime, onNavigate, onProfile, ::backPanel
            )
            TvSettingsPage.ENHANCEMENT_MDBLIST -> TvMdblistEnhancementSettings(
                runtime, onNavigate, onProfile, ::backPanel
            )
            TvSettingsPage.PLAYBACK -> TvPlaybackSettings(
                runtime, onNavigate, onProfile, onBack
            )
            TvSettingsPage.SUBTITLES -> TvSubtitleSettings(
                runtime, onNavigate, onProfile, onBack
            )
            TvSettingsPage.SOURCES -> TvSourceSettings(
                runtime, onNavigate, onProfile, onBack
            )
            TvSettingsPage.APPEARANCE -> TvAppearanceSettings(
                runtime, onNavigate, onProfile, onBack
            )
            TvSettingsPage.DATA_STORAGE -> TvDataStorageSettings(
                runtime, onNavigate, onProfile, onDataChanged, onResetComplete, onBack
            )
            TvSettingsPage.ABOUT -> TvAboutSettings(
                onNavigate, onProfile, onBack
            )
        }
    }
}
