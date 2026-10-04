package com.vueo.tv.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import com.vueo.tv.player.TvPlayerSubtitleStyleState
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

@Composable
internal fun TvPlaybackSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    val store = runtime.settingsStore
    var resume by remember { mutableStateOf(store.resumePlaybackEnabled()) }
    var quality by remember { mutableStateOf(store.preferredQuality()) }
    var speed by remember { mutableStateOf(store.playerPlaybackSpeed()) }
    var fit by remember { mutableStateOf(store.playerVideoFit()) }
    var warnings by remember { mutableStateOf(store.contentWarningsEnabled()) }
    var skip by remember { mutableStateOf(store.skipSegmentsEnabled()) }
    var autoplay by remember { mutableStateOf(store.autoPlayNextEpisodeEnabled()) }
    var recovery by remember { mutableStateOf(store.autoSourceRecoveryEnabled()) }
    val speeds = listOf(.75f, 1f, 1.25f, 1.5f, 2f)

    val entries = listOf(
        toggleEntry("resume", "Resume Playback", "Ask to continue from a saved position when reopening a title.", resume) { resume = it; store.setResumePlaybackEnabled(it) }
            .copy(section = "PLAYBACK", icon = Icons.Default.PlayArrow),
        choiceEntry("quality", "Preferred Quality", "Prefer this resolution when Smart Source ranks playable streams.", quality.label, { quality = cycle(PreferredQuality.entries, quality, -1); store.setPreferredQuality(quality) }, { quality = cycle(PreferredQuality.entries, quality, 1); store.setPreferredQuality(quality) })
            .copy(section = "PLAYBACK"),
        choiceEntry("speed", "Playback Speed", "Default speed used by the TV player.", "${speed}×", { speed = cycle(speeds, speed, -1); store.setPlayerPlaybackSpeed(speed) }, { speed = cycle(speeds, speed, 1); store.setPlayerPlaybackSpeed(speed) })
            .copy(section = "PLAYER"),
        choiceEntry("fit", "Video Fit", "Choose how video fills the TV canvas.", fit.label, { fit = cycle(PlayerVideoFit.entries, fit, -1); store.setPlayerVideoFit(fit) }, { fit = cycle(PlayerVideoFit.entries, fit, 1); store.setPlayerVideoFit(fit) })
            .copy(section = "PLAYER"),
        toggleEntry("warnings", "Content Warnings", "Show available parental guidance briefly when playback starts.", warnings) { warnings = it; store.setContentWarningsEnabled(it) }
            .copy(section = "BEHAVIOR"),
        toggleEntry("skip", "Skip Intro & Ending", "Show contextual skip controls when verified timestamps are available.", skip) { skip = it; store.setSkipSegmentsEnabled(it) }
            .copy(section = "BEHAVIOR"),
        toggleEntry("autoplay", "Auto-play Next Episode", "Start the next episode after an 8-second countdown when playback ends.", autoplay) { autoplay = it; store.setAutoPlayNextEpisodeEnabled(it) }
            .copy(section = "BEHAVIOR"),
        toggleEntry("recovery", "Auto Source Recovery", "Try up to two ranked alternatives after an error or timeout while keeping the timestamp.", recovery) { recovery = it; store.setAutoSourceRecoveryEnabled(it) }
            .copy(section = "BEHAVIOR"),
    )

    TvSettingsListScreen("Playback", "Player behavior and quality preference.", entries, onNavigate, onProfile, onBack)
}

@Composable
internal fun TvSubtitleSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    val store = runtime.settingsStore
    var font by remember { mutableStateOf(store.subtitleFontFamily()) }
    var primary by remember { mutableStateOf(store.preferredSubtitleLanguage()) }
    var secondary by remember { mutableStateOf(store.secondarySubtitleLanguage()) }
    var visibility by remember { mutableStateOf(store.subtitleVisibility()) }
    var defaultOn by remember { mutableStateOf(store.subtitlesOnByDefault()) }
    var autoSelect by remember { mutableStateOf(store.autoSelectPreferredSubtitle()) }
    var embedded by remember { mutableStateOf(store.embeddedSubtitlePriority()) }
    var size by remember { mutableStateOf(store.subtitleSize()) }
    var bold by remember { mutableStateOf(store.subtitleBold()) }
    var outline by remember { mutableStateOf(store.subtitleOutlineEnabled()) }
    var bottomPadding by remember { mutableIntStateOf(store.subtitleBottomPaddingPercent()) }
    var opacity by remember { mutableIntStateOf(store.subtitleTextOpacityPercent()) }
    var backgroundEnabled by remember { mutableStateOf(store.subtitleBackgroundEnabled()) }
    var backgroundColor by remember { mutableIntStateOf(store.subtitleBackgroundColor()) }
    var backgroundOpacity by remember {
        mutableIntStateOf(store.subtitleBackgroundOpacityPercent())
    }
    val backgroundColours = listOf(
        0xFF000000.toInt() to "Black",
        0xFF202124.toInt() to "Charcoal",
        0xFF263238.toInt() to "Blue Black",
        0xFF3B1F2B.toInt() to "Wine Black",
    )
    fun updateBackgroundColour(direction: Int) {
        val colours = backgroundColours.map { it.first }
        backgroundColor = cycle(colours, backgroundColor, direction)
        store.setSubtitleBackgroundColor(backgroundColor)
    }

    val entries = listOf(
        choiceEntry("primary", "Preferred Language", "First language VUEO should prefer when subtitle tracks are available.", primary.label, { primary = cycle(SubtitleLanguage.entries, primary, -1); store.setPreferredSubtitleLanguage(primary) }, { primary = cycle(SubtitleLanguage.entries, primary, 1); store.setPreferredSubtitleLanguage(primary) })
            .copy(section = "LANGUAGE & BEHAVIOR", icon = Icons.Default.VideoLibrary),
        choiceEntry("secondary", "Secondary Language", "Fallback language when the preferred language is unavailable.", secondary.label, { secondary = cycle(SubtitleLanguage.entries, secondary, -1); store.setSecondarySubtitleLanguage(secondary) }, { secondary = cycle(SubtitleLanguage.entries, secondary, 1); store.setSecondarySubtitleLanguage(secondary) })
            .copy(section = "LANGUAGE & BEHAVIOR"),
        choiceEntry("visibility", "Subtitle Visibility", "Choose whether the player lists only your preferred language or every discovered subtitle language.", visibility.label, { visibility = cycle(SubtitleVisibility.entries, visibility, -1); store.setSubtitleVisibility(visibility) }, { visibility = cycle(SubtitleVisibility.entries, visibility, 1); store.setSubtitleVisibility(visibility) })
            .copy(section = "LANGUAGE & BEHAVIOR"),
        toggleEntry("default", "Subtitles On by Default", "Prefer showing subtitles automatically when a suitable track exists.", defaultOn) { defaultOn = it; store.setSubtitlesOnByDefault(it) }
            .copy(section = "LANGUAGE & BEHAVIOR"),
        toggleEntry("auto", "Auto Select Preferred Language", "Prioritize preferred and secondary languages automatically.", autoSelect) { autoSelect = it; store.setAutoSelectPreferredSubtitle(it) }
            .copy(section = "LANGUAGE & BEHAVIOR"),
        toggleEntry("embedded", "Embedded Subtitle Priority", "Prefer subtitle tracks already included in the stream before external tracks when possible.", embedded) { embedded = it; store.setEmbeddedSubtitlePriority(it) }
            .copy(section = "LANGUAGE & BEHAVIOR"),
        choiceEntry("font", "Subtitle Font", "Choose a font. The preview updates immediately.", com.vueo.shared.core.player.SubtitleFonts.label(font),
            { font = com.vueo.shared.core.player.SubtitleFonts.next(font, -1); store.setSubtitleFontFamily(font) },
            { font = com.vueo.shared.core.player.SubtitleFonts.next(font, 1); store.setSubtitleFontFamily(font) })
            .copy(section = "DISPLAY", supportingContent = {
                SubtitleFontPreview(com.vueo.tv.player.TvPlayerSubtitleStyleState(
                    fontFamily = font, fontSizeSp = store.subtitleFontSizeSp(), bold = bold,
                    textColor = store.subtitleTextColor(), outlineEnabled = outline,
                    outlineColor = store.subtitleOutlineColor(), backgroundEnabled = backgroundEnabled,
                    backgroundColor = backgroundColor, backgroundOpacityPercent = backgroundOpacity,
                ))
            }),
        choiceEntry("size", "Subtitle Size", "Saved display size preference for the VUEO player.", size.label, { size = cycle(SubtitleSize.entries, size, -1); store.setSubtitleSize(size) }, { size = cycle(SubtitleSize.entries, size, 1); store.setSubtitleSize(size) })
            .copy(section = "DISPLAY"),
        toggleEntry("bold", "Bold Subtitles", "Use heavier subtitle text.", bold) { bold = it; store.setSubtitleBold(it) }
            .copy(section = "DISPLAY"),
        toggleEntry("outline", "Subtitle Outline", "Draw an outline for contrast over bright video.", outline) { outline = it; store.setSubtitleOutlineEnabled(it) }
            .copy(section = "DISPLAY"),
        choiceEntry("padding", "Bottom Position", "Distance from the bottom edge of the screen.", "$bottomPadding%", { bottomPadding = (bottomPadding - 2).coerceAtLeast(5); store.setSubtitleBottomPaddingPercent(bottomPadding) }, { bottomPadding = (bottomPadding + 2).coerceAtMost(40); store.setSubtitleBottomPaddingPercent(bottomPadding) })
            .copy(section = "DISPLAY"),
        choiceEntry("opacity", "Text Opacity", "Subtitle text opacity.", "$opacity%", { opacity = (opacity - 10).coerceAtLeast(20); store.setSubtitleTextOpacityPercent(opacity) }, { opacity = (opacity + 10).coerceAtMost(100); store.setSubtitleTextOpacityPercent(opacity) })
            .copy(section = "DISPLAY"),
        toggleEntry("background", "Subtitle Background", "Show a background behind subtitle text for stronger contrast.", backgroundEnabled) { backgroundEnabled = it; store.setSubtitleBackgroundEnabled(it) }
            .copy(section = "DISPLAY"),
        choiceEntry(
            "background-color",
            "Background Color",
            "Choose the subtitle background colour.",
            backgroundColours.firstOrNull { it.first == backgroundColor }?.second ?: "Custom",
            { updateBackgroundColour(-1) },
            { updateBackgroundColour(1) },
        ).copy(section = "DISPLAY", enabled = backgroundEnabled),
        choiceEntry(
            "background-opacity",
            "Background Opacity",
            "Control how strongly the video is darkened behind subtitle text.",
            "$backgroundOpacity%",
            {
                backgroundOpacity = (backgroundOpacity - 10).coerceAtLeast(10)
                store.setSubtitleBackgroundOpacityPercent(backgroundOpacity)
            },
            {
                backgroundOpacity = (backgroundOpacity + 10).coerceAtMost(100)
                store.setSubtitleBackgroundOpacityPercent(backgroundOpacity)
            },
        ).copy(section = "DISPLAY", enabled = backgroundEnabled),
    )

    TvSettingsListScreen("Subtitles", "Subtitle behavior is separate from subtitle providers in Content Manager.", entries, onNavigate, onProfile, onBack)
}

@Composable
internal fun TvSourceSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    var details by remember { mutableStateOf(runtime.settingsStore.showSourceTechnicalDetails()) }
    val entries = listOf(
        TvSettingsEntry("ranking", "Smart Source Ranking", "Direct playability, preferred quality and provider health are ranked before selection.", "Active", enabled = false, section = "DISCOVERY", icon = Icons.Default.SettingsInputComponent),
        TvSettingsEntry("progressive", "Progressive Discovery", "Fast providers can return results while slower providers continue searching.", "Active", enabled = false, section = "DISCOVERY"),
        toggleEntry("details", "Technical Source Details", "Show codec, HDR, audio, size and provider information when available.", details) {
            details = it
            runtime.settingsStore.setShowSourceTechnicalDetails(it)
        }.copy(section = "DISPLAY"),
    )
    TvSettingsListScreen("Sources", "Discovery and Smart Source behavior.", entries, onNavigate, onProfile, onBack)
}

@Composable
internal fun TvAppearanceSettings(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = runtime.settingsStore
    var theme by remember { mutableStateOf(store.appTheme()) }
    var accent by remember { mutableStateOf(store.appAccent()) }
    var sidebarStyle by remember { mutableStateOf(TvSidebarPreferences.style(context)) }

    val entries = listOf(
        choiceEntry("theme", "Theme", "Choose the dark cinematic base palette.", theme.label, {
            theme = cycle(AppTheme.entries, theme, -1); store.setAppTheme(theme); TvDesign.applyTheme(theme)
        }, {
            theme = cycle(AppTheme.entries, theme, 1); store.setAppTheme(theme); TvDesign.applyTheme(theme)
        }).copy(section = "LOOK & FEEL", icon = Icons.Default.Settings),
        choiceEntry("accent", "Accent", "Selection, progress and semantic accents.", accent.label, {
            accent = cycle(AppAccent.entries, accent, -1); store.setAppAccent(accent); TvDesign.applyAccent(accent)
        }, {
            accent = cycle(AppAccent.entries, accent, 1); store.setAppAccent(accent); TvDesign.applyAccent(accent)
        }).copy(section = "LOOK & FEEL"),
        choiceEntry("sidebar-style", "Sidebar style", "Choose how the TV navigation rail is presented.", sidebarStyle.label, {
            sidebarStyle = cycle(TvSidebarStyle.entries, sidebarStyle, -1)
            TvSidebarPreferences.setStyle(context, sidebarStyle)
        }, {
            sidebarStyle = cycle(TvSidebarStyle.entries, sidebarStyle, 1)
            TvSidebarPreferences.setStyle(context, sidebarStyle)
        }).copy(section = "NAVIGATION"),
    )
    TvSettingsListScreen("Appearance", "Choose a dark VUEO palette and tune the interactive accent.", entries, onNavigate, onProfile, onBack)
}

@Composable
internal fun SubtitleFontPreview(style: TvPlayerSubtitleStyleState) {
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier.fillMaxWidth().height(64.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xFF101318))
            .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(9.dp)),
        factory = { context -> androidx.media3.ui.SubtitleView(context).apply {
            isFocusable = false
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setApplyEmbeddedStyles(false)
            setApplyEmbeddedFontSizes(false)
            setBottomPaddingFraction(0f)
            setCues(listOf(androidx.media3.common.text.Cue.Builder()
                .setText("Contoh sari kata.")
                .setLine(.5f, androidx.media3.common.text.Cue.LINE_TYPE_FRACTION)
                .setLineAnchor(androidx.media3.common.text.Cue.ANCHOR_TYPE_MIDDLE)
                .setPosition(.5f).setPositionAnchor(androidx.media3.common.text.Cue.ANCHOR_TYPE_MIDDLE)
                .setSize(.9f).build()))
        } },
        update = { view ->
            view.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, style.fontSizeSp.coerceIn(16, 26).toFloat())
            val background = if (style.backgroundEnabled) {
                (style.backgroundColor and 0x00FFFFFF) or
                    ((style.backgroundOpacityPercent * 255 / 100) shl 24)
            } else android.graphics.Color.TRANSPARENT
            view.setStyle(androidx.media3.ui.CaptionStyleCompat(
                style.textColor, background, android.graphics.Color.TRANSPARENT,
                if (style.outlineEnabled) androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE
                else androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE,
                style.outlineColor,
                com.vueo.shared.core.player.SubtitleFonts.resolve(view.context, style.fontFamily, style.bold),
            ))
        },
    )
}
