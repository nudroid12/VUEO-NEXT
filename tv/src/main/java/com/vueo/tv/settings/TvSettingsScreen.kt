package com.vueo.tv.settings

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.storage.PreferredQuality
import com.vueo.shared.core.storage.SubtitleLanguage
import com.vueo.shared.core.storage.SubtitleSize
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvPrimaryDestinations
import com.vueo.tv.ui.TvTopBar
import kotlinx.coroutines.delay

private const val SETTING_PROFILE = "profile"
private const val SETTING_STARTUP = "startup"
private const val SETTING_RESUME = "resume"
private const val SETTING_QUALITY = "quality"
private const val SETTING_RECOVERY = "recovery"
private const val SETTING_AUTOPLAY = "autoplay"
private const val SETTING_SKIP = "skip"
private const val SETTING_SUBTITLES = "subtitles"
private const val SETTING_SUBTITLE_LANGUAGE = "subtitle_language"
private const val SETTING_SUBTITLE_SIZE = "subtitle_size"
private const val SETTING_SOURCE_DETAILS = "source_details"

private val settingIds = listOf(
    SETTING_PROFILE,
    SETTING_STARTUP,
    SETTING_RESUME,
    SETTING_QUALITY,
    SETTING_RECOVERY,
    SETTING_AUTOPLAY,
    SETTING_SKIP,
    SETTING_SUBTITLES,
    SETTING_SUBTITLE_LANGUAGE,
    SETTING_SUBTITLE_SIZE,
    SETTING_SOURCE_DETAILS,
)

/**
 * TV-native settings surface backed directly by Shared Core stores.
 *
 * Remote grammar:
 * - Up/Down: move through rows.
 * - Left/Right: change an adjustable value.
 * - OK: toggle / advance / open the focused row.
 * - Up from the first row: move into the top navigation plane.
 * - Down from top navigation: restore the last focused setting.
 */
@Composable
fun TvSettingsScreen(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    var askOnStartup by remember { mutableStateOf(runtime.profileStore.askWhoIsWatchingOnStartup()) }
    var resumePlayback by remember { mutableStateOf(runtime.settingsStore.resumePlaybackEnabled()) }
    var quality by remember { mutableStateOf(runtime.settingsStore.preferredQuality()) }
    var autoRecovery by remember { mutableStateOf(runtime.settingsStore.autoSourceRecoveryEnabled()) }
    var autoPlayNext by remember { mutableStateOf(runtime.settingsStore.autoPlayNextEpisodeEnabled()) }
    var skipSegments by remember { mutableStateOf(runtime.settingsStore.skipSegmentsEnabled()) }
    var subtitles by remember { mutableStateOf(runtime.settingsStore.subtitlesOnByDefault()) }
    var subtitleLanguage by remember { mutableStateOf(runtime.settingsStore.preferredSubtitleLanguage()) }
    var subtitleSize by remember { mutableStateOf(runtime.settingsStore.subtitleSize()) }
    var sourceDetails by remember { mutableStateOf(runtime.settingsStore.showSourceTechnicalDetails()) }

    val navRequesters = remember { TvPrimaryDestinations.associateWith { FocusRequester() } }
    val profileRequester = remember { FocusRequester() }
    val rowRequesters = remember { settingIds.associateWith { FocusRequester() } }
    var lastFocusedSetting by remember { mutableStateOf(SETTING_PROFILE) }

    LaunchedEffect(Unit) {
        delay(120)
        runCatching { rowRequesters.getValue(SETTING_PROFILE).requestFocus() }
    }

    fun focusSettingsNav() {
        runCatching { navRequesters.getValue("Settings").requestFocus() }
    }

    fun restoreSettingFocus(): Boolean {
        val requester = rowRequesters[lastFocusedSetting] ?: return false
        return runCatching {
            requester.requestFocus()
            true
        }.getOrDefault(false)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvDesign.Black),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 58.dp, end = 58.dp, top = 108.dp, bottom = 36.dp),
        ) {
            Text(
                text = "Settings",
                color = TvDesign.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Playback, subtitles, sources and profile preferences.",
                color = TvDesign.Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(22.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth(.74f)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "section-profile") {
                    SettingsSection("PROFILE")
                }
                item(key = SETTING_PROFILE) {
                    TvSettingRow(
                        requester = rowRequesters.getValue(SETTING_PROFILE),
                        title = "Profile",
                        subtitle = "Switch or manage who is watching.",
                        value = runtime.profileStore.activeProfile().name,
                        valueMode = SettingValueMode.ACTION,
                        first = true,
                        onUpFromFirst = ::focusSettingsNav,
                        onFocused = { lastFocusedSetting = SETTING_PROFILE },
                        onActivate = onProfile,
                    )
                }
                item(key = SETTING_STARTUP) {
                    TvSettingRow(
                        requester = rowRequesters.getValue(SETTING_STARTUP),
                        title = "Ask who’s watching on startup",
                        subtitle = "Show profile selection before Home opens.",
                        value = if (askOnStartup) "On" else "Off",
                        valueMode = SettingValueMode.ADJUSTABLE,
                        onFocused = { lastFocusedSetting = SETTING_STARTUP },
                        onPrevious = {
                            askOnStartup = false
                            runtime.profileStore.setAskWhoIsWatchingOnStartup(false)
                        },
                        onNext = {
                            askOnStartup = true
                            runtime.profileStore.setAskWhoIsWatchingOnStartup(true)
                        },
                        onActivate = {
                            askOnStartup = !askOnStartup
                            runtime.profileStore.setAskWhoIsWatchingOnStartup(askOnStartup)
                        },
                    )
                }

                item(key = "space-playback") { Spacer(Modifier.height(10.dp)) }
                item(key = "section-playback") { SettingsSection("PLAYBACK") }
                item(key = SETTING_RESUME) {
                    TvSettingRow(
                        requester = rowRequesters.getValue(SETTING_RESUME),
                        title = "Resume playback",
                        subtitle = "Continue from the last saved position.",
                        value = if (resumePlayback) "On" else "Off",
                        valueMode = SettingValueMode.ADJUSTABLE,
                        onFocused = { lastFocusedSetting = SETTING_RESUME },
                        onPrevious = {
                            resumePlayback = false
                            runtime.settingsStore.setResumePlaybackEnabled(false)
                        },
                        onNext = {
                            resumePlayback = true
                            runtime.settingsStore.setResumePlaybackEnabled(true)
                        },
                        onActivate = {
                            resumePlayback = !resumePlayback
                            runtime.settingsStore.setResumePlaybackEnabled(resumePlayback)
                        },
                    )
                }
                item(key = SETTING_QUALITY) {
                    TvSettingRow(
                        requester = rowRequesters.getValue(SETTING_QUALITY),
                        title = "Preferred source quality",
                        subtitle = "Used when ranking discovered sources.",
                        value = quality.label,
                        valueMode = SettingValueMode.ADJUSTABLE,
                        onFocused = { lastFocusedSetting = SETTING_QUALITY },
                        onPrevious = {
                            quality = cycle(PreferredQuality.entries, quality, -1)
                            runtime.settingsStore.setPreferredQuality(quality)
                        },
                        onNext = {
                            quality = cycle(PreferredQuality.entries, quality, 1)
                            runtime.settingsStore.setPreferredQuality(quality)
                        },
                        onActivate = {
                            quality = cycle(PreferredQuality.entries, quality, 1)
                            runtime.settingsStore.setPreferredQuality(quality)
                        },
                    )
                }
                item(key = SETTING_RECOVERY) {
                    TvSettingRow(
                        requester = rowRequesters.getValue(SETTING_RECOVERY),
                        title = "Automatic source recovery",
                        subtitle = "Try another source when playback cannot continue.",
                        value = if (autoRecovery) "On" else "Off",
                        valueMode = SettingValueMode.ADJUSTABLE,
                        onFocused = { lastFocusedSetting = SETTING_RECOVERY },
                        onPrevious = {
                            autoRecovery = false
                            runtime.settingsStore.setAutoSourceRecoveryEnabled(false)
                        },
                        onNext = {
                            autoRecovery = true
                            runtime.settingsStore.setAutoSourceRecoveryEnabled(true)
                        },
                        onActivate = {
                            autoRecovery = !autoRecovery
                            runtime.settingsStore.setAutoSourceRecoveryEnabled(autoRecovery)
                        },
                    )
                }
                item(key = SETTING_AUTOPLAY) {
                    TvSettingRow(
                        requester = rowRequesters.getValue(SETTING_AUTOPLAY),
                        title = "Auto-play next episode",
                        subtitle = "Continue episodic playback automatically.",
                        value = if (autoPlayNext) "On" else "Off",
                        valueMode = SettingValueMode.ADJUSTABLE,
                        onFocused = { lastFocusedSetting = SETTING_AUTOPLAY },
                        onPrevious = {
                            autoPlayNext = false
                            runtime.settingsStore.setAutoPlayNextEpisodeEnabled(false)
                        },
                        onNext = {
                            autoPlayNext = true
                            runtime.settingsStore.setAutoPlayNextEpisodeEnabled(true)
                        },
                        onActivate = {
                            autoPlayNext = !autoPlayNext
                            runtime.settingsStore.setAutoPlayNextEpisodeEnabled(autoPlayNext)
                        },
                    )
                }
                item(key = SETTING_SKIP) {
                    TvSettingRow(
                        requester = rowRequesters.getValue(SETTING_SKIP),
                        title = "Skip detected segments",
                        subtitle = "Enable skip controls when intro or credit markers exist.",
                        value = if (skipSegments) "On" else "Off",
                        valueMode = SettingValueMode.ADJUSTABLE,
                        onFocused = { lastFocusedSetting = SETTING_SKIP },
                        onPrevious = {
                            skipSegments = false
                            runtime.settingsStore.setSkipSegmentsEnabled(false)
                        },
                        onNext = {
                            skipSegments = true
                            runtime.settingsStore.setSkipSegmentsEnabled(true)
                        },
                        onActivate = {
                            skipSegments = !skipSegments
                            runtime.settingsStore.setSkipSegmentsEnabled(skipSegments)
                        },
                    )
                }

                item(key = "space-subtitles") { Spacer(Modifier.height(10.dp)) }
                item(key = "section-subtitles") { SettingsSection("SUBTITLES") }
                item(key = SETTING_SUBTITLES) {
                    TvSettingRow(
                        requester = rowRequesters.getValue(SETTING_SUBTITLES),
                        title = "Subtitles by default",
                        subtitle = "Start playback with subtitles enabled when available.",
                        value = if (subtitles) "On" else "Off",
                        valueMode = SettingValueMode.ADJUSTABLE,
                        onFocused = { lastFocusedSetting = SETTING_SUBTITLES },
                        onPrevious = {
                            subtitles = false
                            runtime.settingsStore.setSubtitlesOnByDefault(false)
                        },
                        onNext = {
                            subtitles = true
                            runtime.settingsStore.setSubtitlesOnByDefault(true)
                        },
                        onActivate = {
                            subtitles = !subtitles
                            runtime.settingsStore.setSubtitlesOnByDefault(subtitles)
                        },
                    )
                }
                item(key = SETTING_SUBTITLE_LANGUAGE) {
                    TvSettingRow(
                        requester = rowRequesters.getValue(SETTING_SUBTITLE_LANGUAGE),
                        title = "Preferred subtitle language",
                        subtitle = "First choice when subtitle tracks are available.",
                        value = subtitleLanguage.label,
                        valueMode = SettingValueMode.ADJUSTABLE,
                        onFocused = { lastFocusedSetting = SETTING_SUBTITLE_LANGUAGE },
                        onPrevious = {
                            subtitleLanguage = cycle(SubtitleLanguage.entries, subtitleLanguage, -1)
                            runtime.settingsStore.setPreferredSubtitleLanguage(subtitleLanguage)
                        },
                        onNext = {
                            subtitleLanguage = cycle(SubtitleLanguage.entries, subtitleLanguage, 1)
                            runtime.settingsStore.setPreferredSubtitleLanguage(subtitleLanguage)
                        },
                        onActivate = {
                            subtitleLanguage = cycle(SubtitleLanguage.entries, subtitleLanguage, 1)
                            runtime.settingsStore.setPreferredSubtitleLanguage(subtitleLanguage)
                        },
                    )
                }
                item(key = SETTING_SUBTITLE_SIZE) {
                    TvSettingRow(
                        requester = rowRequesters.getValue(SETTING_SUBTITLE_SIZE),
                        title = "Subtitle size",
                        subtitle = "Default subtitle text size in the player.",
                        value = subtitleSize.label,
                        valueMode = SettingValueMode.ADJUSTABLE,
                        onFocused = { lastFocusedSetting = SETTING_SUBTITLE_SIZE },
                        onPrevious = {
                            subtitleSize = cycle(SubtitleSize.entries, subtitleSize, -1)
                            runtime.settingsStore.setSubtitleSize(subtitleSize)
                        },
                        onNext = {
                            subtitleSize = cycle(SubtitleSize.entries, subtitleSize, 1)
                            runtime.settingsStore.setSubtitleSize(subtitleSize)
                        },
                        onActivate = {
                            subtitleSize = cycle(SubtitleSize.entries, subtitleSize, 1)
                            runtime.settingsStore.setSubtitleSize(subtitleSize)
                        },
                    )
                }

                item(key = "space-sources") { Spacer(Modifier.height(10.dp)) }
                item(key = "section-sources") { SettingsSection("SOURCES") }
                item(key = SETTING_SOURCE_DETAILS) {
                    TvSettingRow(
                        requester = rowRequesters.getValue(SETTING_SOURCE_DETAILS),
                        title = "Technical source details",
                        subtitle = "Show quality, codec and provider information when available.",
                        value = if (sourceDetails) "On" else "Off",
                        valueMode = SettingValueMode.ADJUSTABLE,
                        onFocused = { lastFocusedSetting = SETTING_SOURCE_DETAILS },
                        onPrevious = {
                            sourceDetails = false
                            runtime.settingsStore.setShowSourceTechnicalDetails(false)
                        },
                        onNext = {
                            sourceDetails = true
                            runtime.settingsStore.setShowSourceTechnicalDetails(true)
                        },
                        onActivate = {
                            sourceDetails = !sourceDetails
                            runtime.settingsStore.setShowSourceTechnicalDetails(sourceDetails)
                        },
                    )
                }

                item(key = "settings-bottom-space") { Spacer(Modifier.height(24.dp)) }
            }
        }

        TvTopBar(
            selected = "Settings",
            expanded = true,
            navRequesters = navRequesters,
            profileRequester = profileRequester,
            onFocused = {},
            onNavigate = onNavigate,
            onProfile = onProfile,
            onDownFromNav = ::restoreSettingFocus,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun SettingsSection(label: String) {
    Text(
        text = label,
        color = TvDesign.Muted.copy(alpha = .82f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.25.sp,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
    )
}

private enum class SettingValueMode {
    ACTION,
    ADJUSTABLE,
}

@Composable
private fun TvSettingRow(
    requester: FocusRequester,
    title: String,
    subtitle: String,
    value: String,
    valueMode: SettingValueMode,
    first: Boolean = false,
    onUpFromFirst: () -> Unit = {},
    onFocused: () -> Unit,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onActivate: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                when {
                    first &&
                        event.type == KeyEventType.KeyDown &&
                        keyCode == KeyEvent.KEYCODE_DPAD_UP -> {
                        onUpFromFirst()
                        true
                    }

                    event.type == KeyEventType.KeyDown &&
                        keyCode == KeyEvent.KEYCODE_DPAD_LEFT &&
                        onPrevious != null -> {
                        onPrevious()
                        true
                    }

                    event.type == KeyEventType.KeyDown &&
                        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT &&
                        onNext != null -> {
                        onNext()
                        true
                    }

                    event.isTvActivationKey() -> {
                        if (event.type == KeyEventType.KeyUp) onActivate()
                        true
                    }

                    else -> false
                }
            }
            .background(
                color = if (focused) TvDesign.White.copy(alpha = .105f)
                else TvDesign.Surface.copy(alpha = .66f),
                shape = RoundedCornerShape(12.dp),
            )
            .border(
                width = 1.dp,
                color = if (focused) TvDesign.White.copy(alpha = .78f)
                else TvDesign.White.copy(alpha = .075f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onActivate)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                color = TvDesign.White,
                fontSize = 14.sp,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = TvDesign.Muted.copy(alpha = if (focused) .92f else .72f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = when (valueMode) {
                SettingValueMode.ACTION -> "$value  ›"
                SettingValueMode.ADJUSTABLE -> "‹  $value  ›"
            },
            color = if (focused) TvDesign.White else TvDesign.Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 24.dp),
        )
    }
}

private fun <T> cycle(values: List<T>, current: T, delta: Int): T {
    if (values.isEmpty()) return current
    val currentIndex = values.indexOf(current).takeIf { it >= 0 } ?: 0
    val nextIndex = (currentIndex + delta).floorMod(values.size)
    return values[nextIndex]
}

private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

private fun androidx.compose.ui.input.key.KeyEvent.isTvActivationKey(): Boolean =
    nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
